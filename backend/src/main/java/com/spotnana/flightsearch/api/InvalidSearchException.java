package com.spotnana.flightsearch.api;

/**
 * A search request that is syntactically fine but cannot be answered, such as an airport
 * the dataset has never heard of.
 *
 * <p>Always maps to 400. A client mistake must never surface as a 500.
 */
public class InvalidSearchException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public InvalidSearchException(ErrorCode code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode code() {
        return code;
    }

    /** The query parameter at fault, or null when the problem spans more than one. */
    public String field() {
        return field;
    }
}
