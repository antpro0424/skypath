package com.spotnana.flightsearch.domain;

import java.time.Duration;

/**
 * The connection thresholds from the assignment, defined once.
 *
 * <p>Every rule that references a duration reads it from here, so there is no minute count
 * written into traversal code where it could drift out of step with the documented policy.
 */
public final class ConnectionRules {

    /** Minimum layover when both flights stay inside one country. */
    public static final Duration MINIMUM_DOMESTIC_LAYOVER = Duration.ofMinutes(45);

    /** Minimum layover when either flight crosses a border. */
    public static final Duration MINIMUM_INTERNATIONAL_LAYOVER = Duration.ofMinutes(90);

    /** Upper bound on any single layover, regardless of connection type. */
    public static final Duration MAXIMUM_LAYOVER = Duration.ofHours(6);

    private ConnectionRules() {}
}
