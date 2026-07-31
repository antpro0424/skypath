package com.spotnana.flightsearch.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A single scheduled flight with both endpoints already resolved to zoned times.
 *
 * <p>The dataset stores naive local timestamps. By the time a {@code Flight} exists, the
 * departure has been zoned at the origin airport and the arrival at the destination
 * airport, so {@link #departureInstant()} and {@link #arrivalInstant()} are directly
 * comparable across time zones. Elapsed time is always computed from those instants;
 * subtracting the raw local values would be wrong for any flight that changes zone.
 *
 * <p>The record holds resolved {@link Airport} objects rather than airport codes. The
 * loader has already validated every reference, so rule evaluation downstream needs no
 * lookup and cannot encounter an unknown airport.
 */
public record Flight(
        String flightNumber,
        String airline,
        Airport origin,
        Airport destination,
        ZonedDateTime departure,
        ZonedDateTime arrival,
        BigDecimal price,
        String aircraft) {

    public Flight {
        Objects.requireNonNull(flightNumber, "flightNumber");
        Objects.requireNonNull(airline, "airline");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(aircraft, "aircraft");

        // These mirror checks the loader performs so it can report a typed reason per
        // record. Keeping them here too means the invariant holds for any Flight built
        // directly, such as in a test fixture.
        if (origin.code().equals(destination.code())) {
            throw new IllegalArgumentException(
                    "Flight %s departs and arrives at %s".formatted(flightNumber, origin.code()));
        }
        if (!arrival.toInstant().isAfter(departure.toInstant())) {
            throw new IllegalArgumentException(
                    "Flight %s arrives at or before it departs".formatted(flightNumber));
        }
    }

    public Instant departureInstant() {
        return departure.toInstant();
    }

    public Instant arrivalInstant() {
        return arrival.toInstant();
    }

    /** Elapsed travel time, always positive. */
    public Duration duration() {
        return Duration.between(departureInstant(), arrivalInstant());
    }

    /**
     * The calendar date of departure <em>at the origin airport</em>. This is the date a
     * search request refers to, not a UTC date.
     */
    public LocalDate departureLocalDate() {
        return departure.toLocalDate();
    }
}
