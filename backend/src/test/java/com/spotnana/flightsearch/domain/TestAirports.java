package com.spotnana.flightsearch.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Airports and flight builders shared by tests.
 *
 * <p>Two ways to build a flight. {@link #arrivingAt} and {@link #departingAt} pin an
 * absolute instant, which lets a rule test place a layover at exactly 45 minutes without
 * doing time-zone arithmetic in its own body. {@link #local} takes naive local times, which
 * is how the dataset expresses them and what search-date behaviour needs to exercise.
 */
public final class TestAirports {

    public static final Airport JFK =
            new Airport("JFK", "John F. Kennedy International", "New York", "US",
                    ZoneId.of("America/New_York"));
    public static final Airport LGA =
            new Airport("LGA", "LaGuardia", "New York", "US", ZoneId.of("America/New_York"));
    public static final Airport ORD =
            new Airport("ORD", "O'Hare International", "Chicago", "US", ZoneId.of("America/Chicago"));
    public static final Airport DEN =
            new Airport("DEN", "Denver International", "Denver", "US", ZoneId.of("America/Denver"));
    public static final Airport SFO =
            new Airport("SFO", "San Francisco International", "San Francisco", "US",
                    ZoneId.of("America/Los_Angeles"));
    public static final Airport LAX =
            new Airport("LAX", "Los Angeles International", "Los Angeles", "US",
                    ZoneId.of("America/Los_Angeles"));
    public static final Airport LHR =
            new Airport("LHR", "Heathrow", "London", "GB", ZoneId.of("Europe/London"));
    public static final Airport CDG =
            new Airport("CDG", "Charles de Gaulle", "Paris", "FR", ZoneId.of("Europe/Paris"));
    public static final Airport YYZ =
            new Airport("YYZ", "Toronto Pearson", "Toronto", "CA", ZoneId.of("America/Toronto"));
    public static final Airport NRT =
            new Airport("NRT", "Narita International", "Tokyo", "JP", ZoneId.of("Asia/Tokyo"));

    private static final Duration NOMINAL_FLIGHT_TIME = Duration.ofHours(3);
    private static final BigDecimal NOMINAL_PRICE = new BigDecimal("100.00");

    /** A flight that lands at {@code destination} at exactly the given instant. */
    public static Flight arrivingAt(
            String flightNumber, Airport origin, Airport destination, Instant arrival) {
        return flight(flightNumber, origin, destination, arrival.minus(NOMINAL_FLIGHT_TIME), arrival);
    }

    /** A flight that leaves {@code origin} at exactly the given instant. */
    public static Flight departingAt(
            String flightNumber, Airport origin, Airport destination, Instant departure) {
        return flight(
                flightNumber, origin, destination, departure, departure.plus(NOMINAL_FLIGHT_TIME));
    }

    public static Flight flight(
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

    /** A flight expressed the way the dataset does: naive local times at each endpoint. */
    public static Flight local(
            String flightNumber,
            Airport origin,
            Airport destination,
            String departureLocal,
            String arrivalLocal) {
        return local(flightNumber, origin, destination, departureLocal, arrivalLocal, "100.00");
    }

    public static Flight local(
            String flightNumber,
            Airport origin,
            Airport destination,
            String departureLocal,
            String arrivalLocal,
            String price) {
        return new Flight(
                flightNumber,
                "SkyPath Airways",
                origin,
                destination,
                LocalDateTime.parse(departureLocal).atZone(origin.zone()),
                LocalDateTime.parse(arrivalLocal).atZone(destination.zone()),
                new BigDecimal(price),
                "A320");
    }

    private TestAirports() {}
}
