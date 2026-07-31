package com.spotnana.flightsearch.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One flight in an itinerary.
 *
 * <p>Times are offset-aware, so {@code 2024-03-15T08:30:00-04:00} states both the local
 * clock at the airport and the absolute moment, making time-zone behaviour visible in the
 * raw payload instead of something a reader has to trust.
 *
 * <p>The IANA zone identifiers are carried alongside deliberately. A client that formats an
 * offset timestamp with the browser's own locale would render a New York departure in the
 * viewer's zone; with the zone id it can format in the airport's zone, which is what a
 * traveller expects to read.
 */
public record SegmentView(
        String flightNumber,
        String airline,
        String origin,
        String destination,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
                OffsetDateTime departureTime,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
                OffsetDateTime arrivalTime,
        String departureTimezone,
        String arrivalTimezone,
        BigDecimal price,
        String aircraft) {}
