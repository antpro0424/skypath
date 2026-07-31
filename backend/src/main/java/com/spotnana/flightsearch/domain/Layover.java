package com.spotnana.flightsearch.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * A validated stop between two consecutive segments.
 *
 * <p>A {@code Layover} only exists for a connection that already satisfies the rules, so
 * its presence is itself the evidence that the minimum and maximum were met.
 *
 * @param airport where the passenger waits; both the arriving and departing flight touch it
 * @param duration wall-clock wait, measured between absolute instants
 * @param connectionType which minimum applied
 */
public record Layover(Airport airport, Duration duration, ConnectionType connectionType) {

    public Layover {
        Objects.requireNonNull(airport, "airport");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(connectionType, "connectionType");
    }

    /**
     * The threshold this layover had to clear. Derived rather than stored so it can never
     * disagree with the connection type.
     */
    public Duration minimumRequired() {
        return connectionType.minimumLayover();
    }
}
