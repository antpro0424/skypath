package com.spotnana.flightsearch.application;

import java.util.List;

/**
 * What happened when the schedule was loaded.
 *
 * <p>The supplied dataset contains deliberate defects, so "load succeeded" is not a
 * useful answer on its own. Every record that was dropped, and every value that was
 * accepted only after coercion, is named here and surfaced rather than logged once and
 * forgotten. Silently skipping a malformed record would hide a data-quality problem.
 */
public record DatasetLoadReport(
        int airportCount,
        int flightsLoaded,
        List<QuarantinedFlight> quarantinedFlights,
        List<CoercedValue> coercedValues) {

    public DatasetLoadReport {
        quarantinedFlights = List.copyOf(quarantinedFlights);
        coercedValues = List.copyOf(coercedValues);
    }

    public int quarantinedCount() {
        return quarantinedFlights.size();
    }

    public int coercedCount() {
        return coercedValues.size();
    }

    public boolean isClean() {
        return quarantinedFlights.isEmpty() && coercedValues.isEmpty();
    }

    /** A record excluded from the index because it could not be resolved. */
    public record QuarantinedFlight(String flightNumber, QuarantineReason reason, String detail) {}

    /** A record admitted to the index after a value was repaired into the expected type. */
    public record CoercedValue(String flightNumber, String field, CoercionReason reason, String detail) {}

    public enum QuarantineReason {
        /** The record carries no flight number, so it cannot be identified or deduplicated. */
        MISSING_FLIGHT_NUMBER,
        /** Origin or destination is not present in the airports list. */
        UNKNOWN_AIRPORT,
        /** A timestamp is absent or not ISO-8601 local date-time. */
        INVALID_TIMESTAMP,
        /** Price is absent, non-numeric, or negative. */
        INVALID_PRICE,
        /** Arrival is at or before departure once both are resolved to instants. */
        NON_POSITIVE_DURATION,
        /** Origin and destination are the same airport. */
        SELF_LOOP,
        /** A later record reuses a flight number already seen. */
        DUPLICATE_FLIGHT_NUMBER
    }

    public enum CoercionReason {
        /** Price arrived as a JSON string rather than a JSON number. */
        PRICE_STRING_TO_DECIMAL
    }
}
