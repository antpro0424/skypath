package com.spotnana.flightsearch.api;

/**
 * Stable, machine-readable error identifiers.
 *
 * <p>Carried alongside the human-readable detail so a client can distinguish "your input was
 * wrong" from "the service failed" without matching on prose that may be reworded.
 */
public enum ErrorCode {
    /** A required query parameter was absent. */
    MISSING_PARAMETER,
    /** An airport code was not exactly three letters. */
    INVALID_AIRPORT_CODE,
    /** A well-formed code that is not in the dataset. */
    UNKNOWN_AIRPORT,
    /** Origin and destination are the same airport. */
    SAME_ORIGIN_AND_DESTINATION,
    /** The date was absent or not an ISO-8601 calendar date. */
    INVALID_DATE,
    /** Anything unexpected; the only code that accompanies a 5xx. */
    INTERNAL_ERROR
}
