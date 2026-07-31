package com.spotnana.flightsearch.application;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A search request, with airport codes already normalized by the caller.
 *
 * @param origin where the journey starts
 * @param destination where it ends
 * @param date the local calendar date of the <em>first</em> segment's departure, read at
 *     the origin airport. Later segments may fall on the following day.
 */
public record SearchQuery(String origin, String destination, LocalDate date) {

    public SearchQuery {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(date, "date");
    }
}
