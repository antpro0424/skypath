package com.spotnana.flightsearch.infrastructure;

/**
 * Thrown when the dataset as a whole cannot be used, as opposed to an individual record
 * being unusable.
 *
 * <p>A missing file, unparseable JSON, a duplicate airport code, an unrecognized time zone
 * or a dataset with no usable flights all indicate a broken deployment, so startup fails
 * loudly. A single malformed flight is quarantined instead, because one bad row should not
 * take down a service that can still answer almost every query.
 */
public class DatasetLoadException extends RuntimeException {

    public DatasetLoadException(String message) {
        super(message);
    }

    public DatasetLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
