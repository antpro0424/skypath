package com.spotnana.flightsearch.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotnana.flightsearch.domain.ConnectionRules;
import com.spotnana.flightsearch.domain.Flight;
import com.spotnana.flightsearch.domain.Itinerary;
import com.spotnana.flightsearch.domain.Layover;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Search behaviour against the dataset that ships with the assignment.
 *
 * <p>These assert invariants rather than itinerary counts. A count would encode today's
 * dataset into the test suite and say nothing about whether the rules are right; an
 * invariant fails only when a rule is actually broken.
 */
@SpringBootTest
class ItinerarySearchIntegrationTest {

    private static final LocalDate SEARCH_DATE = LocalDate.of(2024, 3, 15);

    /** Routes chosen to span domestic, international, date-line and connection-only cases. */
    private static final List<String[]> SWEPT_ROUTES =
            List.of(
                    new String[] {"JFK", "LAX"},
                    new String[] {"SFO", "NRT"},
                    new String[] {"BOS", "SEA"},
                    new String[] {"SYD", "LAX"},
                    new String[] {"SYD", "DFW"},
                    new String[] {"LHR", "SFO"},
                    new String[] {"DXB", "ATL"},
                    new String[] {"YYZ", "ORD"},
                    new String[] {"JFK", "NRT"},
                    new String[] {"LAX", "LHR"});

    @Autowired private ItinerarySearchService searchService;
    @Autowired private FlightRepository flightRepository;

    // ------------------------------------------------------- supplied test cases

    @Test
    @DisplayName("case 1: JFK to LAX returns direct flights and multi-stop options")
    void jfkToLax() {
        List<Itinerary> results = search("JFK", "LAX");

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(i -> assertThat(i.segments()).hasSize(1));
        assertThat(results).anySatisfy(i -> assertThat(i.segments()).hasSizeGreaterThan(1));
    }

    @Test
    @DisplayName("case 2: SFO to NRT applies the 90 minute minimum to every connection")
    void sfoToNrt() {
        List<Itinerary> results = search("SFO", "NRT");

        assertThat(results).isNotEmpty();
        assertThat(results)
                .allSatisfy(
                        itinerary ->
                                assertThat(itinerary.layovers())
                                        .allSatisfy(
                                                layover ->
                                                        assertThat(layover.duration())
                                                                .isGreaterThanOrEqualTo(
                                                                        layover.minimumRequired())));
    }

    @Test
    @DisplayName("case 3: BOS to SEA has no direct flight and must connect")
    void bosToSea() {
        boolean directExists =
                flightRepository.flightsDepartingFrom("BOS").stream()
                        .anyMatch(f -> f.destination().code().equals("SEA"));
        assertThat(directExists).isFalse();

        List<Itinerary> results = search("BOS", "SEA");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(i -> assertThat(i.segments()).hasSizeGreaterThan(1));
    }

    @Test
    @DisplayName("case 6: SYD to LAX crosses the date line with a positive duration")
    void sydToLax() {
        List<Itinerary> results = search("SYD", "LAX");

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(i -> assertThat(i.totalDuration()).isPositive());
        assertThat(results)
                .anySatisfy(
                        itinerary -> {
                            Flight first = itinerary.segments().get(0);
                            assertThat(first.origin().code()).isEqualTo("SYD");
                            // Local arrival clock reads earlier than the local departure clock.
                            assertThat(first.arrival().toLocalTime())
                                    .isBefore(first.departure().toLocalTime());
                            assertThat(first.duration()).isEqualTo(Duration.ofHours(15));
                        });
    }

    // ------------------------------------------------------- rule enforcement

    @Test
    @DisplayName("no itinerary connects in under 90 minutes after an international arrival")
    void enforcesInternationalMinimumAfterAnInternationalArrival() {
        List<Itinerary> corpus = sweep();
        int internationalArrivalConnections = 0;

        for (Itinerary itinerary : corpus) {
            for (int i = 0; i < itinerary.layovers().size(); i++) {
                Flight arriving = itinerary.segments().get(i);
                Flight departing = itinerary.segments().get(i + 1);
                Layover layover = itinerary.layovers().get(i);

                if (!crossesBorder(arriving)) {
                    continue;
                }
                internationalArrivalConnections++;

                assertThat(layover.duration())
                        .as(
                                "layover at %s after international arrival %s, before %s",
                                layover.airport().code(),
                                arriving.flightNumber(),
                                departing.flightNumber())
                        .isGreaterThanOrEqualTo(ConnectionRules.MINIMUM_INTERNATIONAL_LAYOVER);
            }
        }

        // The dataset contains 60 minute connections after international arrivals, which a
        // rule that inspected only the departing flight would wrongly accept. Assert the
        // case was actually reached, so this test cannot pass vacuously.
        assertThat(internationalArrivalConnections)
                .as("itineraries exercising an international arrival")
                .isPositive();
    }

    @Test
    @DisplayName("every connection satisfies its own minimum and the six hour maximum")
    void enforcesLayoverBounds() {
        assertThat(sweep())
                .isNotEmpty()
                .allSatisfy(
                        itinerary ->
                                assertThat(itinerary.layovers())
                                        .allSatisfy(
                                                layover -> {
                                                    assertThat(layover.duration())
                                                            .isGreaterThanOrEqualTo(
                                                                    layover.minimumRequired());
                                                    assertThat(layover.duration())
                                                            .isLessThanOrEqualTo(
                                                                    ConnectionRules.MAXIMUM_LAYOVER);
                                                }));
    }

    // ------------------------------------------------------- universal invariants

    @ParameterizedTest(name = "{0} to {1}")
    @CsvSource({
        "JFK,LAX", "SFO,NRT", "BOS,SEA", "SYD,LAX", "SYD,DFW",
        "LHR,SFO", "DXB,ATL", "YYZ,ORD", "JFK,NRT", "LAX,LHR"
    })
    @DisplayName("results are well formed for every swept route")
    void resultsAreWellFormed(String origin, String destination) {
        List<Itinerary> results = search(origin, destination);

        assertThat(results)
                .allSatisfy(
                        itinerary -> {
                            assertThat(itinerary.origin().code()).isEqualTo(origin);
                            assertThat(itinerary.destination().code()).isEqualTo(destination);
                            assertThat(itinerary.segments())
                                    .hasSizeBetween(1, ConnectionRules.MAXIMUM_SEGMENTS);
                            assertThat(itinerary.layovers())
                                    .hasSize(itinerary.segments().size() - 1);
                            assertThat(itinerary.totalDuration()).isPositive();
                            assertThat(itinerary.totalPrice()).isPositive();
                            assertSegmentsJoinUp(itinerary);
                            assertNoAirportRepeats(itinerary);
                            assertFirstSegmentDepartsOnSearchDate(itinerary);
                        });

        assertThat(results).isSortedAccordingTo(ItineraryOrdering.SHORTEST_FIRST);
        assertThat(results).extracting(Itinerary::flightNumberSequence).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("identical searches return identical results")
    void isDeterministic() {
        List<String> first = sequences(search("JFK", "LAX"));
        List<String> second = sequences(search("JFK", "LAX"));

        assertThat(first).isEqualTo(second).isNotEmpty();
    }

    @Test
    @DisplayName("results are ordered shortest first across every swept route")
    void ordersByTotalTravelTime() {
        for (String[] route : SWEPT_ROUTES) {
            List<Itinerary> results = search(route[0], route[1]);

            assertThat(results)
                    .as("%s to %s", route[0], route[1])
                    .isSortedAccordingTo(Comparator.comparing(Itinerary::totalDuration));
        }
    }

    // ------------------------------------------------------- helpers

    private List<Itinerary> search(String origin, String destination) {
        return searchService.search(new SearchQuery(origin, destination, SEARCH_DATE));
    }

    private List<Itinerary> sweep() {
        List<Itinerary> all = new ArrayList<>();
        for (String[] route : SWEPT_ROUTES) {
            all.addAll(search(route[0], route[1]));
        }
        return all;
    }

    private static List<String> sequences(List<Itinerary> itineraries) {
        return itineraries.stream().map(Itinerary::flightNumberSequence).toList();
    }

    private static boolean crossesBorder(Flight flight) {
        return !flight.origin().country().equals(flight.destination().country());
    }

    private static void assertSegmentsJoinUp(Itinerary itinerary) {
        List<Flight> segments = itinerary.segments();
        for (int i = 0; i < segments.size() - 1; i++) {
            assertThat(segments.get(i).destination().code())
                    .as("segment %d lands where segment %d departs", i, i + 1)
                    .isEqualTo(segments.get(i + 1).origin().code());
        }
    }

    private static void assertNoAirportRepeats(Itinerary itinerary) {
        List<String> visited = new ArrayList<>();
        visited.add(itinerary.segments().get(0).origin().code());
        itinerary.segments().forEach(segment -> visited.add(segment.destination().code()));

        assertThat(visited).doesNotHaveDuplicates();
    }

    private static void assertFirstSegmentDepartsOnSearchDate(Itinerary itinerary) {
        assertThat(itinerary.segments().get(0).departureLocalDate()).isEqualTo(SEARCH_DATE);
    }
}
