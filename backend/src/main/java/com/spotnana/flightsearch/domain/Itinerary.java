package com.spotnana.flightsearch.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A complete journey: one to three flights, plus the validated layovers between them.
 *
 * <p>Totals are computed rather than stored, so they cannot drift out of step with the
 * segments. With at most three flights the arithmetic is trivial and the invariant is worth
 * more than the saved cycles.
 *
 * @param segments flights in travel order
 * @param layovers one fewer entry than segments; empty for a direct flight
 */
public record Itinerary(List<Flight> segments, List<Layover> layovers) {

    public Itinerary {
        segments = List.copyOf(segments);
        layovers = List.copyOf(layovers);

        if (segments.isEmpty()) {
            throw new IllegalArgumentException("An itinerary needs at least one segment");
        }
        if (segments.size() > ConnectionRules.MAXIMUM_SEGMENTS) {
            throw new IllegalArgumentException(
                    "An itinerary may not exceed %d segments but had %d"
                            .formatted(ConnectionRules.MAXIMUM_SEGMENTS, segments.size()));
        }
        if (layovers.size() != segments.size() - 1) {
            throw new IllegalArgumentException(
                    "%d segments require %d layovers but %d were given"
                            .formatted(segments.size(), segments.size() - 1, layovers.size()));
        }
    }

    public Airport origin() {
        return firstSegment().origin();
    }

    public Airport destination() {
        return lastSegment().destination();
    }

    /**
     * Elapsed travel time from first departure to final arrival, layovers included.
     * Measured between absolute instants, so it is correct across any number of zones.
     */
    public Duration totalDuration() {
        return Duration.between(firstSegment().departureInstant(), lastSegment().arrivalInstant());
    }

    /** Sum of segment fares. {@link BigDecimal} throughout; never a floating-point total. */
    public BigDecimal totalPrice() {
        return segments.stream().map(Flight::price).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Zero for a direct flight, one for a single connection, two for two connections. */
    public int stops() {
        return segments.size() - 1;
    }

    /**
     * Flight numbers in order, joined. Identifies an itinerary uniquely, which makes it
     * both the final sort tie-break and the key used to detect duplicates.
     */
    public String flightNumberSequence() {
        return segments.stream().map(Flight::flightNumber).collect(Collectors.joining("-"));
    }

    private Flight firstSegment() {
        return segments.get(0);
    }

    private Flight lastSegment() {
        return segments.get(segments.size() - 1);
    }
}
