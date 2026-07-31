package com.spotnana.flightsearch.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spotnana.flightsearch.application.DatasetLoadReport.CoercionReason;
import com.spotnana.flightsearch.application.DatasetLoadReport.QuarantineReason;
import com.spotnana.flightsearch.domain.Flight;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Loader behaviour against purpose-built datasets. Each case declares exactly the records
 * it needs, so the expected instants are verifiable by hand rather than depending on the
 * shipped file.
 */
class FlightDatasetLoaderTest {

    private static final String JFK =
            """
            {"code":"JFK","name":"John F. Kennedy International","city":"New York",
             "country":"US","timezone":"America/New_York"}""";
    private static final String LAX =
            """
            {"code":"LAX","name":"Los Angeles International","city":"Los Angeles",
             "country":"US","timezone":"America/Los_Angeles"}""";
    private static final String SYD =
            """
            {"code":"SYD","name":"Sydney Kingsford Smith","city":"Sydney",
             "country":"AU","timezone":"Australia/Sydney"}""";

    private final FlightDatasetLoader loader = new FlightDatasetLoader();

    // ------------------------------------------------------------ time zones

    @Nested
    @DisplayName("time zone resolution")
    class TimeZoneResolution {

        @Test
        @DisplayName("zones departure at the origin airport and arrival at the destination")
        void resolvesEachEndpointInItsOwnZone() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                            "2024-03-15T11:45:00", "299.00"))));

            Flight flight = onlyFlightFrom(schedule, "JFK");

            // 08:30 in New York (UTC-4 on this date) and 11:45 in Los Angeles (UTC-7).
            assertThat(flight.departureInstant()).isEqualTo(Instant.parse("2024-03-15T12:30:00Z"));
            assertThat(flight.arrivalInstant()).isEqualTo(Instant.parse("2024-03-15T18:45:00Z"));
            assertThat(flight.duration()).isEqualTo(Duration.ofMinutes(375));
        }

        @Test
        @DisplayName("crossing the date line yields a positive duration")
        void resolvesDateLineCrossing() {
            // SYD 09:00 to LAX 06:00 on the same calendar day: the arrival clock reads
            // earlier than the departure clock, yet the flight takes fifteen hours.
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(SYD, LAX),
                                    List.of(flight("SP540", "SYD", "LAX", "2024-03-15T09:00:00",
                                            "2024-03-15T06:00:00", "1099.00"))));

            Flight flight = onlyFlightFrom(schedule, "SYD");

            assertThat(flight.arrival().toLocalTime()).isBefore(flight.departure().toLocalTime());
            assertThat(flight.departureInstant()).isEqualTo(Instant.parse("2024-03-14T22:00:00Z"));
            assertThat(flight.arrivalInstant()).isEqualTo(Instant.parse("2024-03-15T13:00:00Z"));
            assertThat(flight.duration()).isEqualTo(Duration.ofHours(15));
            assertThat(flight.duration()).isPositive();
        }

        @Test
        @DisplayName("departure date is the local date at the origin, not the UTC date")
        void departureLocalDateUsesOriginZone() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(SYD, LAX),
                                    List.of(flight("SP540", "SYD", "LAX", "2024-03-15T09:00:00",
                                            "2024-03-15T06:00:00", "1099.00"))));

            Flight flight = onlyFlightFrom(schedule, "SYD");

            // The absolute instant falls on 14 March in UTC; the search date is the local one.
            assertThat(flight.departureInstant().toString()).startsWith("2024-03-14");
            assertThat(flight.departureLocalDate()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThat(flight.departure().toLocalTime()).isEqualTo(LocalTime.of(9, 0));
        }
    }

    // ------------------------------------------------------------ quarantine

    @Nested
    @DisplayName("record quarantine")
    class Quarantine {

        @Test
        @DisplayName("drops a flight referencing an airport that does not exist")
        void quarantinesUnknownAirport() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            flight("SP995", "JKF", "LAX", "2024-03-15T10:00:00",
                                                    "2024-03-15T13:15:00", "289.00"))));

            assertThat(schedule.report().flightsLoaded()).isEqualTo(1);
            assertThat(schedule.report().quarantinedFlights())
                    .singleElement()
                    .satisfies(
                            q -> {
                                assertThat(q.flightNumber()).isEqualTo("SP995");
                                assertThat(q.reason()).isEqualTo(QuarantineReason.UNKNOWN_AIRPORT);
                                assertThat(q.detail()).contains("JKF");
                            });
            assertThat(schedule.outgoingFlightsByOrigin()).doesNotContainKey("JKF");
        }

        @Test
        @DisplayName("drops a flight that arrives at or before it departs")
        void quarantinesNonPositiveDuration() {
            // 08:30 in New York is 12:30Z; 04:00 in Los Angeles is 11:00Z, which is earlier.
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            flight("SP666", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T04:00:00", "199.00"))));

            assertThat(schedule.report().quarantinedFlights())
                    .singleElement()
                    .satisfies(
                            q -> {
                                assertThat(q.flightNumber()).isEqualTo("SP666");
                                assertThat(q.reason())
                                        .isEqualTo(QuarantineReason.NON_POSITIVE_DURATION);
                            });
        }

        @Test
        @DisplayName("drops a flight whose origin and destination are the same airport")
        void quarantinesSelfLoop() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            flight("SP777", "JFK", "JFK", "2024-03-15T08:30:00",
                                                    "2024-03-15T09:45:00", "99.00"))));

            assertThat(schedule.report().quarantinedFlights())
                    .singleElement()
                    .satisfies(q -> assertThat(q.reason()).isEqualTo(QuarantineReason.SELF_LOOP));
        }

        @Test
        @DisplayName("keeps the first record and drops a later reuse of the same flight number")
        void quarantinesDuplicateFlightNumber() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            flight("SP101", "JFK", "LAX", "2024-03-15T14:00:00",
                                                    "2024-03-15T17:15:00", "349.00"))));

            assertThat(schedule.report().flightsLoaded()).isEqualTo(1);
            assertThat(schedule.report().quarantinedFlights())
                    .singleElement()
                    .satisfies(
                            q ->
                                    assertThat(q.reason())
                                            .isEqualTo(QuarantineReason.DUPLICATE_FLIGHT_NUMBER));
            assertThat(onlyFlightFrom(schedule, "JFK").price())
                    .isEqualByComparingTo(new BigDecimal("299.00"));
        }

        @Test
        @DisplayName("drops a flight with an unparseable timestamp")
        void quarantinesInvalidTimestamp() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            flight("SP888", "JFK", "LAX", "15/03/2024 08:30",
                                                    "2024-03-15T11:45:00", "299.00"))));

            assertThat(schedule.report().quarantinedFlights())
                    .singleElement()
                    .satisfies(
                            q ->
                                    assertThat(q.reason())
                                            .isEqualTo(QuarantineReason.INVALID_TIMESTAMP));
        }

        @Test
        @DisplayName("drops a flight with a negative or non-numeric price")
        void quarantinesInvalidPrice() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            rawFlight("SP889", "JFK", "LAX", "2024-03-15T09:00:00",
                                                    "2024-03-15T12:15:00", "-10.00"),
                                            rawFlight("SP890", "JFK", "LAX", "2024-03-15T10:00:00",
                                                    "2024-03-15T13:15:00", "\"free\""))));

            assertThat(schedule.report().quarantinedFlights())
                    .hasSize(2)
                    .allSatisfy(
                            q -> assertThat(q.reason()).isEqualTo(QuarantineReason.INVALID_PRICE));
        }
    }

    // ------------------------------------------------------------ price handling

    @Nested
    @DisplayName("price handling")
    class Prices {

        @Test
        @DisplayName("accepts a string price and records the coercion")
        void coercesStringPrice() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(rawFlight("SP996", "JFK", "LAX", "2024-03-15T08:30:00",
                                            "2024-03-15T11:45:00", "\"289.00\""))));

            assertThat(onlyFlightFrom(schedule, "JFK").price())
                    .isEqualByComparingTo(new BigDecimal("289.00"));
            assertThat(schedule.report().coercedValues())
                    .singleElement()
                    .satisfies(
                            c -> {
                                assertThat(c.flightNumber()).isEqualTo("SP996");
                                assertThat(c.field()).isEqualTo("price");
                                assertThat(c.reason())
                                        .isEqualTo(CoercionReason.PRICE_STRING_TO_DECIMAL);
                            });
        }

        @Test
        @DisplayName("reads numeric prices as exact decimals, never through a double")
        void preservesDecimalPrecision() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                            "2024-03-15T11:45:00", "299.00"))));

            BigDecimal price = onlyFlightFrom(schedule, "JFK").price();

            assertThat(price).isEqualByComparingTo(new BigDecimal("299.00"));
            assertThat(price.scale()).isEqualTo(2);
            assertThat(price.toPlainString()).isEqualTo("299.00");
        }
    }

    // ------------------------------------------------------------ indexing

    @Nested
    @DisplayName("indexing")
    class Indexing {

        @Test
        @DisplayName("orders outgoing flights by departure instant, then flight number")
        void sortsOutgoingFlightsDeterministically() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(
                                            flight("SP300", "JFK", "LAX", "2024-03-15T18:00:00",
                                                    "2024-03-15T21:15:00", "299.00"),
                                            flight("SP200", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"),
                                            // Same departure as SP200: flight number breaks the tie.
                                            flight("SP100", "JFK", "LAX", "2024-03-15T08:30:00",
                                                    "2024-03-15T11:45:00", "299.00"))));

            assertThat(schedule.outgoingFlightsByOrigin().get("JFK"))
                    .extracting(Flight::flightNumber)
                    .containsExactly("SP100", "SP200", "SP300");
        }

        @Test
        @DisplayName("normalizes airport codes so lookups are case-insensitive")
        void normalizesAirportCodes() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(flight("SP101", " jfk ", "lax", "2024-03-15T08:30:00",
                                            "2024-03-15T11:45:00", "299.00"))));

            assertThat(schedule.outgoingFlightsByOrigin()).containsOnlyKeys("JFK");
            assertThat(onlyFlightFrom(schedule, "JFK").destination().code()).isEqualTo("LAX");
        }

        @Test
        @DisplayName("exposes indexes that cannot be mutated after startup")
        void indexesAreImmutable() {
            LoadedFlightSchedule schedule =
                    loader.load(
                            dataset(
                                    List.of(JFK, LAX),
                                    List.of(flight("SP101", "JFK", "LAX", "2024-03-15T08:30:00",
                                            "2024-03-15T11:45:00", "299.00"))));

            Flight existing = onlyFlightFrom(schedule, "JFK");

            assertThatThrownBy(() -> schedule.airportsByCode().remove("JFK"))
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> schedule.outgoingFlightsByOrigin().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(
                            () -> schedule.outgoingFlightsByOrigin().get("JFK").add(existing))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ------------------------------------------------------------ fail fast

    @Nested
    @DisplayName("dataset-level failures")
    class FailFast {

        @Test
        @DisplayName("refuses to start on a duplicate airport code")
        void rejectsDuplicateAirportCode() {
            assertThatThrownBy(
                            () ->
                                    loader.load(
                                            dataset(
                                                    List.of(JFK, JFK),
                                                    List.of(flight("SP101", "JFK", "JFK",
                                                            "2024-03-15T08:30:00",
                                                            "2024-03-15T11:45:00", "299.00")))))
                    .isInstanceOf(DatasetLoadException.class)
                    .hasMessageContaining("Duplicate airport code JFK");
        }

        @Test
        @DisplayName("refuses to start on an unrecognized time zone")
        void rejectsUnknownTimeZone() {
            String broken =
                    """
                    {"code":"XXX","name":"Nowhere","city":"Nowhere",
                     "country":"US","timezone":"Mars/Olympus_Mons"}""";

            assertThatThrownBy(
                            () ->
                                    loader.load(
                                            dataset(
                                                    List.of(JFK, broken),
                                                    List.of(flight("SP101", "JFK", "XXX",
                                                            "2024-03-15T08:30:00",
                                                            "2024-03-15T11:45:00", "299.00")))))
                    .isInstanceOf(DatasetLoadException.class)
                    .hasMessageContaining("unrecognized time zone");
        }

        @Test
        @DisplayName("refuses to start on malformed JSON")
        void rejectsMalformedJson() {
            Resource broken = new ByteArrayResource("{ not json".getBytes(StandardCharsets.UTF_8));

            assertThatThrownBy(() -> loader.load(broken))
                    .isInstanceOf(DatasetLoadException.class)
                    .hasMessageContaining("Unable to read dataset");
        }

        @Test
        @DisplayName("refuses to start when no flight survives validation")
        void rejectsDatasetWithNoUsableFlights() {
            assertThatThrownBy(
                            () ->
                                    loader.load(
                                            dataset(
                                                    List.of(JFK, LAX),
                                                    List.of(flight("SP995", "JKF", "LAX",
                                                            "2024-03-15T10:00:00",
                                                            "2024-03-15T13:15:00", "289.00")))))
                    .isInstanceOf(DatasetLoadException.class)
                    .hasMessageContaining("no usable flights");
        }
    }

    // ------------------------------------------------------------ helpers

    private static Flight onlyFlightFrom(LoadedFlightSchedule schedule, String origin) {
        List<Flight> flights = schedule.outgoingFlightsByOrigin().get(origin);
        assertThat(flights).hasSize(1);
        return flights.get(0);
    }

    /** Builds a flight whose price is a JSON number. */
    private static String flight(
            String number, String origin, String destination, String departure, String arrival,
            String price) {
        return rawFlight(number, origin, destination, departure, arrival, price);
    }

    /** Builds a flight with the price literal inserted verbatim, so tests can supply a string. */
    private static String rawFlight(
            String number, String origin, String destination, String departure, String arrival,
            String priceLiteral) {
        return """
               {"flightNumber":"%s","airline":"SkyPath Airways","origin":"%s","destination":"%s",
                "departureTime":"%s","arrivalTime":"%s","price":%s,"aircraft":"A320"}"""
                .formatted(number, origin, destination, departure, arrival, priceLiteral);
    }

    private static Resource dataset(List<String> airports, List<String> flights) {
        String json =
                """
                {"airports":[%s],"flights":[%s]}"""
                        .formatted(String.join(",", airports), String.join(",", flights));
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
    }
}
