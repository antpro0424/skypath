package com.spotnana.flightsearch.api.dto;

/**
 * A connection between two segments.
 *
 * <p>{@code minimumRequiredMinutes} and {@code connectionType} are reported alongside the
 * actual duration so the rule that admitted this connection can be checked from the
 * response alone, without reading the source.
 */
public record LayoverView(
        String airport, long durationMinutes, long minimumRequiredMinutes, String connectionType) {}
