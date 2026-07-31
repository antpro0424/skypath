package com.spotnana.flightsearch.domain;

import java.time.Duration;

/**
 * Whether a connection stays inside one country, which is what decides the minimum layover.
 */
public enum ConnectionType {
    DOMESTIC(ConnectionRules.MINIMUM_DOMESTIC_LAYOVER),
    INTERNATIONAL(ConnectionRules.MINIMUM_INTERNATIONAL_LAYOVER);

    private final Duration minimumLayover;

    ConnectionType(Duration minimumLayover) {
        this.minimumLayover = minimumLayover;
    }

    /** The shortest layover a connection of this type may have, inclusive. */
    public Duration minimumLayover() {
        return minimumLayover;
    }
}
