package com.spotnana.flightsearch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotnana.flightsearch.application.DatasetLoadReport;
import com.spotnana.flightsearch.application.DatasetLoadReport.CoercionReason;
import com.spotnana.flightsearch.application.DatasetLoadReport.QuarantineReason;
import com.spotnana.flightsearch.domain.Flight;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Behaviour against the dataset that actually ships, which contains deliberate defects.
 *
 * <p>The counts asserted here were established by inspecting the file, not assumed. They
 * are worth pinning because they are the specific quirks the loader exists to handle.
 */
class ShippedDatasetTest {

    private static LoadedFlightSchedule schedule;

    @BeforeAll
    static void loadShippedDataset() {
        schedule = new FlightDatasetLoader().load(new ClassPathResource("flights.json"));
    }

    @Test
    @DisplayName("loads every airport and every resolvable flight")
    void loadsExpectedVolumes() {
        DatasetLoadReport report = schedule.report();

        assertThat(report.airportCount()).isEqualTo(25);
        assertThat(report.flightsLoaded()).isEqualTo(302);
        assertThat(schedule.airportsByCode()).containsKeys("JFK", "LAX", "SYD", "NRT", "LHR");
    }

    @Test
    @DisplayName("quarantines SP995, whose origin 'JKF' is a typo for JFK")
    void quarantinesTheDanglingAirportReference() {
        assertThat(schedule.report().quarantinedFlights())
                .singleElement()
                .satisfies(
                        q -> {
                            assertThat(q.flightNumber()).isEqualTo("SP995");
                            assertThat(q.reason()).isEqualTo(QuarantineReason.UNKNOWN_AIRPORT);
                            assertThat(q.detail()).contains("JKF");
                        });

        assertThat(schedule.outgoingFlightsByOrigin()).doesNotContainKey("JKF");
        assertThat(allFlights()).noneMatch(f -> f.flightNumber().equals("SP995"));
    }

    @Test
    @DisplayName("admits the two string-priced records and reports the coercion")
    void reportsStringPriceCoercions() {
        assertThat(schedule.report().coercedValues())
                .hasSize(2)
                .allSatisfy(
                        c -> {
                            assertThat(c.field()).isEqualTo("price");
                            assertThat(c.reason()).isEqualTo(CoercionReason.PRICE_STRING_TO_DECIMAL);
                        })
                .extracting(DatasetLoadReport.CoercedValue::flightNumber)
                .containsExactlyInAnyOrder("SP996", "SP998");
    }

    @Test
    @DisplayName("SP995 is reported once as quarantined, not also as coerced")
    void quarantineTakesPrecedenceOverCoercion() {
        // SP995 carries both defects. It never reaches price resolution, so it must not be
        // double-counted in the report.
        assertThat(schedule.report().coercedValues())
                .extracting(DatasetLoadReport.CoercedValue::flightNumber)
                .doesNotContain("SP995");
    }

    @Test
    @DisplayName("every loaded flight arrives strictly after it departs")
    void everyFlightHasPositiveDuration() {
        assertThat(allFlights())
                .isNotEmpty()
                .allSatisfy(f -> assertThat(f.duration()).isPositive());
    }

    @Test
    @DisplayName("SP540 crosses the date line and still takes fifteen hours")
    void resolvesTheDateLineFlight() {
        Flight sp540 =
                schedule.outgoingFlightsByOrigin().get("SYD").stream()
                        .filter(f -> f.flightNumber().equals("SP540"))
                        .findFirst()
                        .orElseThrow();

        assertThat(sp540.destination().code()).isEqualTo("LAX");
        // Departs 09:00 in Sydney, arrives 06:00 in Los Angeles on the same calendar day.
        assertThat(sp540.arrival().toLocalTime()).isBefore(sp540.departure().toLocalTime());
        assertThat(sp540.duration()).isEqualTo(Duration.ofHours(15));
    }

    @Test
    @DisplayName("outgoing flight lists are ordered by departure instant, then flight number")
    void outgoingFlightsAreSorted() {
        Comparator<Flight> expected =
                Comparator.comparing(Flight::departureInstant).thenComparing(Flight::flightNumber);

        assertThat(schedule.outgoingFlightsByOrigin())
                .allSatisfy((origin, flights) -> assertThat(flights).isSortedAccordingTo(expected));
    }

    @Test
    @DisplayName("every indexed flight departs from the airport it is filed under")
    void indexKeysMatchFlightOrigins() {
        assertThat(schedule.outgoingFlightsByOrigin())
                .allSatisfy(
                        (origin, flights) ->
                                assertThat(flights)
                                        .allSatisfy(
                                                f ->
                                                        assertThat(f.origin().code())
                                                                .isEqualTo(origin)));
    }

    private static List<Flight> allFlights() {
        return schedule.outgoingFlightsByOrigin().values().stream().flatMap(List::stream).toList();
    }
}
