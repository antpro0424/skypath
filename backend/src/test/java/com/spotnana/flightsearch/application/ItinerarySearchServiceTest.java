package com.spotnana.flightsearch.application;

import static com.spotnana.flightsearch.domain.TestAirports.DEN;
import static com.spotnana.flightsearch.domain.TestAirports.JFK;
import static com.spotnana.flightsearch.domain.TestAirports.LAX;
import static com.spotnana.flightsearch.domain.TestAirports.LGA;
import static com.spotnana.flightsearch.domain.TestAirports.LHR;
import static com.spotnana.flightsearch.domain.TestAirports.ORD;
import static com.spotnana.flightsearch.domain.TestAirports.SFO;
import static com.spotnana.flightsearch.domain.TestAirports.local;
import static org.assertj.core.api.Assertions.assertThat;

import com.spotnana.flightsearch.domain.ConnectionPolicy;
import com.spotnana.flightsearch.domain.Flight;
import com.spotnana.flightsearch.domain.Itinerary;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ItinerarySearchServiceTest {

    private static final LocalDate SEARCH_DATE = LocalDate.of(2024, 3, 15);

    // JFK 08:00 EDT = 12:00Z, arriving ORD 09:30 CDT = 14:30Z.
    private static final Flight JFK_ORD =
            local("SP110", JFK, ORD, "2024-03-15T08:00:00", "2024-03-15T09:30:00");
    // Departs ORD 11:00 CDT = 16:00Z, so the layover is 90 minutes.
    private static final Flight ORD_LAX =
            local("SP120", ORD, LAX, "2024-03-15T11:00:00", "2024-03-15T13:00:00");
    private static final Flight JFK_LAX_DIRECT =
            local("SP100", JFK, LAX, "2024-03-15T08:30:00", "2024-03-15T11:45:00");

    private List<Itinerary> search(String origin, String destination, Flight... network) {
        ItinerarySearchService service =
                new ItinerarySearchService(new FixtureFlightRepository(network), new ConnectionPolicy());
        return service.search(new SearchQuery(origin, destination, SEARCH_DATE));
    }

    // ------------------------------------------------------------ shapes

    @Nested
    @DisplayName("itinerary shapes")
    class Shapes {

        @Test
        @DisplayName("finds a direct flight")
        void findsDirect() {
            List<Itinerary> results = search("JFK", "LAX", JFK_LAX_DIRECT);

            assertThat(results).singleElement().satisfies(itinerary -> {
                assertThat(itinerary.segments()).extracting(Flight::flightNumber).containsExactly("SP100");
                assertThat(itinerary.layovers()).isEmpty();
                assertThat(itinerary.stops()).isZero();
            });
        }

        @Test
        @DisplayName("finds a one-stop connection")
        void findsOneStop() {
            List<Itinerary> results = search("JFK", "LAX", JFK_ORD, ORD_LAX);

            assertThat(results).singleElement().satisfies(itinerary -> {
                assertThat(itinerary.segments())
                        .extracting(Flight::flightNumber)
                        .containsExactly("SP110", "SP120");
                assertThat(itinerary.stops()).isEqualTo(1);
                assertThat(itinerary.layovers())
                        .singleElement()
                        .satisfies(layover -> {
                            assertThat(layover.airport()).isEqualTo(ORD);
                            assertThat(layover.duration()).isEqualTo(Duration.ofMinutes(90));
                        });
            });
        }

        @Test
        @DisplayName("finds a two-stop connection, recorded on the third segment")
        void findsTwoStop() {
            // Only the three-segment path reaches LAX. This also pins the ordering of the
            // destination check against the depth bound inside the traversal.
            List<Itinerary> results =
                    search(
                            "JFK",
                            "LAX",
                            JFK_ORD,
                            local("SP130", ORD, DEN, "2024-03-15T11:00:00", "2024-03-15T12:00:00"),
                            local("SP140", DEN, LAX, "2024-03-15T13:30:00", "2024-03-15T15:00:00"));

            assertThat(results).singleElement().satisfies(itinerary -> {
                assertThat(itinerary.segments()).hasSize(3);
                assertThat(itinerary.stops()).isEqualTo(2);
                assertThat(itinerary.layovers()).hasSize(2);
            });
        }

        @Test
        @DisplayName("never returns an itinerary needing four flights")
        void rejectsFourSegments() {
            List<Itinerary> results =
                    search(
                            "JFK",
                            "LAX",
                            JFK_ORD,
                            local("SP130", ORD, DEN, "2024-03-15T11:00:00", "2024-03-15T12:00:00"),
                            local("SP140", DEN, SFO, "2024-03-15T13:30:00", "2024-03-15T15:00:00"),
                            local("SP150", SFO, LAX, "2024-03-15T16:30:00", "2024-03-15T17:30:00"));

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("returns nothing when no route exists")
        void returnsEmptyWhenUnreachable() {
            assertThat(search("JFK", "LHR", JFK_ORD, ORD_LAX)).isEmpty();
        }
    }

    // ------------------------------------------------------------ path constraints

    @Nested
    @DisplayName("path constraints")
    class PathConstraints {

        @Test
        @DisplayName("never routes through an airport twice")
        void preventsCycles() {
            // JFK -> ORD -> JFK -> LAX: three segments, every layover legal, but it returns
            // to JFK. The final leg departs on 16 March so it cannot stand in as a direct
            // flight for this search, leaving the cycle as the only way to reach LAX.
            Flight outbound = local("SP110", JFK, ORD, "2024-03-15T18:00:00", "2024-03-15T19:30:00");
            Flight back = local("SP111", ORD, JFK, "2024-03-15T21:00:00", "2024-03-16T00:15:00");
            Flight onward = local("SP112", JFK, LAX, "2024-03-16T01:30:00", "2024-03-16T04:45:00");

            assertThat(search("JFK", "LAX", outbound, back, onward)).isEmpty();

            // Control: the same three flights, timed identically. Starting from ORD the path
            // visits each airport once, so it is legal and must be found. This proves the
            // search above was blocked by the revisit and not by the timings.
            assertThat(search("ORD", "LAX", outbound, back, onward))
                    .singleElement()
                    .satisfies(itinerary ->
                            assertThat(itinerary.segments())
                                    .extracting(Flight::flightNumber)
                                    .containsExactly("SP111", "SP112"));
        }

        @Test
        @DisplayName("stops at the destination instead of flying onward and back")
        void doesNotExpandPastDestination() {
            List<Itinerary> results =
                    search(
                            "JFK",
                            "LAX",
                            JFK_LAX_DIRECT,
                            local("SP160", LAX, SFO, "2024-03-15T13:00:00", "2024-03-15T14:15:00"),
                            local("SP161", SFO, LAX, "2024-03-15T15:30:00", "2024-03-15T16:45:00"));

            assertThat(results).singleElement().satisfies(itinerary ->
                    assertThat(itinerary.segments()).hasSize(1));
        }

        @Test
        @DisplayName("a passenger cannot change airports between segments")
        void rejectsAirportChange() {
            List<Itinerary> results =
                    search(
                            "ORD",
                            "LAX",
                            local("SP170", ORD, JFK, "2024-03-15T08:00:00", "2024-03-15T11:00:00"),
                            local("SP171", LGA, LAX, "2024-03-15T13:00:00", "2024-03-15T16:15:00"));

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("a connection outside the layover window is not used")
        void rejectsIllegalLayover() {
            // Lands ORD 14:30Z; this departs 15:00Z, a 30 minute layover.
            Flight tooSoon = local("SP121", ORD, LAX, "2024-03-15T10:00:00", "2024-03-15T12:00:00");

            assertThat(search("JFK", "LAX", JFK_ORD, tooSoon)).isEmpty();
        }

        @Test
        @DisplayName("every returned itinerary has a distinct flight-number sequence")
        void producesNoDuplicates() {
            List<Itinerary> results =
                    search("JFK", "LAX", JFK_LAX_DIRECT, JFK_ORD, ORD_LAX);

            assertThat(results).extracting(Itinerary::flightNumberSequence).doesNotHaveDuplicates();
        }
    }

    // ------------------------------------------------------------ date semantics

    @Nested
    @DisplayName("search date semantics")
    class DateSemantics {

        @Test
        @DisplayName("only first segments departing on the requested local date qualify")
        void filtersFirstSegmentByLocalDate() {
            Flight nextDay = local("SP200", JFK, LAX, "2024-03-16T08:30:00", "2024-03-16T11:45:00");

            List<Itinerary> results = search("JFK", "LAX", JFK_LAX_DIRECT, nextDay);

            assertThat(results).singleElement().satisfies(itinerary ->
                    assertThat(itinerary.segments())
                            .extracting(Flight::flightNumber)
                            .containsExactly("SP100"));
        }

        @Test
        @DisplayName("a later segment may depart on the following calendar day")
        void allowsLaterSegmentsToRollOverMidnight() {
            // Lands ORD 23:30 local; the onward flight leaves at 00:15 the next morning.
            Flight lateArrival =
                    local("SP210", JFK, ORD, "2024-03-15T21:00:00", "2024-03-15T23:30:00");
            Flight afterMidnight =
                    local("SP211", ORD, LAX, "2024-03-16T00:15:00", "2024-03-16T02:15:00");

            List<Itinerary> results = search("JFK", "LAX", lateArrival, afterMidnight);

            assertThat(results).singleElement().satisfies(itinerary -> {
                assertThat(itinerary.segments().get(0).departureLocalDate()).isEqualTo(SEARCH_DATE);
                assertThat(itinerary.segments().get(1).departureLocalDate())
                        .isEqualTo(LocalDate.of(2024, 3, 16));
                assertThat(itinerary.layovers())
                        .singleElement()
                        .satisfies(l -> assertThat(l.duration()).isEqualTo(Duration.ofMinutes(45)));
            });
        }
    }

    // ------------------------------------------------------------ totals and order

    @Nested
    @DisplayName("totals and ordering")
    class TotalsAndOrdering {

        @Test
        @DisplayName("total duration spans first departure to last arrival, layovers included")
        void totalDurationIncludesLayovers() {
            List<Itinerary> results = search("JFK", "LAX", JFK_ORD, ORD_LAX);

            // 12:00Z to 20:00Z: 3.5h flying, 1.5h waiting, 3h flying.
            assertThat(results.get(0).totalDuration()).isEqualTo(Duration.ofHours(8));
        }

        @Test
        @DisplayName("total price sums segment fares exactly")
        void totalPriceSumsSegments() {
            List<Itinerary> results =
                    search(
                            "JFK",
                            "LAX",
                            local("SP110", JFK, ORD, "2024-03-15T08:00:00", "2024-03-15T09:30:00", "199.99"),
                            local("SP120", ORD, LAX, "2024-03-15T11:00:00", "2024-03-15T13:00:00", "150.51"));

            assertThat(results.get(0).totalPrice()).isEqualByComparingTo(new BigDecimal("350.50"));
        }

        @Test
        @DisplayName("orders by shortest total travel time")
        void ordersByDuration() {
            List<Itinerary> results = search("JFK", "LAX", JFK_LAX_DIRECT, JFK_ORD, ORD_LAX);

            assertThat(results).hasSize(2);
            assertThat(results)
                    .isSortedAccordingTo(java.util.Comparator.comparing(Itinerary::totalDuration));
            assertThat(results.get(0).segments()).hasSize(1);
        }

        @Test
        @DisplayName("breaks a duration tie on price")
        void breaksTiesOnPrice() {
            Flight expensive =
                    local("SP301", JFK, LAX, "2024-03-15T08:30:00", "2024-03-15T11:45:00", "500.00");
            Flight cheap =
                    local("SP302", JFK, LAX, "2024-03-15T08:30:00", "2024-03-15T11:45:00", "300.00");

            List<Itinerary> results = search("JFK", "LAX", expensive, cheap);

            assertThat(results)
                    .extracting(Itinerary::flightNumberSequence)
                    .containsExactly("SP302", "SP301");
        }

        @Test
        @DisplayName("returns identical output for identical searches")
        void isDeterministic() {
            Flight[] network = {JFK_LAX_DIRECT, JFK_ORD, ORD_LAX};

            List<String> first =
                    search("JFK", "LAX", network).stream().map(Itinerary::flightNumberSequence).toList();
            List<String> second =
                    search("JFK", "LAX", network).stream().map(Itinerary::flightNumberSequence).toList();

            assertThat(first).isEqualTo(second);
        }
    }
}
