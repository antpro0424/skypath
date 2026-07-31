package com.spotnana.flightsearch.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The search response.
 *
 * <p>Echoing the normalized query back means a client can tell what was actually searched,
 * which matters because codes are upper-cased and trimmed on the way in.
 */
public record ItinerarySearchResponse(Query query, List<ItineraryView> itineraries) {

    public record Query(String origin, String destination, LocalDate date) {}
}
