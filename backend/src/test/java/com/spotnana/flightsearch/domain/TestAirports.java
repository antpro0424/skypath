package com.spotnana.flightsearch.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Airports and flight builders shared by domain tests.
 *
 * <p>Flights are built from explicit instants rather than local strings so a test can place
 * a layover at exactly 45 minutes, or one minute either side of a boundary, without doing
 * time-zone arithmetic in its own body.
 */
final class TestAirports {

    static final Airport JFK =
            new Airport("JFK", "John F. Kennedy International", "New York", "US",
                    ZoneId.of("America/New_York"));
    static final Airport LGA =
            new Airport("LGA", "LaGuardia", "New York", "US", ZoneId.of("America/New_York"));
    static final Airport ORD =
            new Airport("ORD", "O'Hare International", "Chicago", "US", ZoneId.of("America/Chicago"));
    static final Airport LAX =
            new Airport("LAX", "Los Angeles International", "Los Angeles", "US",
                    ZoneId.of("America/Los_Angeles"));
    static final Airport LHR =
            new Airport("LHR", "Heathrow", "London", "GB", ZoneId.of("Europe/London"));
    static final Airport CDG =
            new Airport("CDG", "Charles de Gaulle", "Paris", "FR", ZoneId.of("Europe/Paris"));
    static final Airport YYZ =
            new Airport("YYZ", "Toronto Pearson", "Toronto", "CA", ZoneId.of("America/Toronto"));
    static final Airport NRT =
            new Airport("NRT", "Narita International", "Tokyo", "JP", ZoneId.of("Asia/Tokyo"));

    private static final Duration NOMINAL_FLIGHT_TIME = Duration.ofHours(3);
    private static final BigDecimal NOMINAL_PRICE = new BigDecimal("100.00");

    /** A flight that lands at {@code destination} at exactly the given instant. */
    static Flight arrivingAt(
            String flightNumber, Airport origin, Airport destination, Instant arrival) {
        return flight(flightNumber, origin, destination, arrival.minus(NOMINAL_FLIGHT_TIME), arrival);
    }

    /** A flight that leaves {@code origin} at exactly the given instant. */
    static Flight departingAt(
            String flightNumber, Airport origin, Airport destination, Instant departure) {
        return flight(flightNumber, origin, destination, departure, departure.plus(NOMINAL_FLIGHT_TIME));
    }

    static Flight flight(
            String flightNumber,
            Airport origin,
            Airport destination,
            Instant departure,
            Instant arrival) {
        return new Flight(
                flightNumber,
                "SkyPath Airways",
                origin,
                destination,
                departure.atZone(origin.zone()),
                arrival.atZone(destination.zone()),
                NOMINAL_PRICE,
                "A320");
    }

    private TestAirports() {}
}
