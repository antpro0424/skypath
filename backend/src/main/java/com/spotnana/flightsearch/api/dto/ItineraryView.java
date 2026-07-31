package com.spotnana.flightsearch.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One itinerary as the API presents it.
 *
 * @param stops derived from the segment count, included so the payload is self-describing
 *     rather than making every client compute it
 * @param totalDurationMinutes elapsed time from first departure to final arrival, layovers
 *     included
 */
public record ItineraryView(
        List<SegmentView> segments,
        List<LayoverView> layovers,
        int stops,
        long totalDurationMinutes,
        BigDecimal totalPrice) {}
