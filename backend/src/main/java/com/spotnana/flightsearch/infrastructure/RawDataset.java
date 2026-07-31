package com.spotnana.flightsearch.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * The dataset exactly as it appears on disk, before any validation.
 *
 * <p>Fields are deliberately weakly typed. Timestamps stay {@code String} and price stays
 * {@link JsonNode} so the loader can decide what to do with a bad value and report it,
 * rather than letting Jackson either throw or quietly repair it. In particular, declaring
 * price as {@code BigDecimal} would let Jackson coerce the string {@code "289.00"} without
 * a trace, and the fact that the dataset is internally inconsistent would be lost.
 */
public record RawDataset(List<RawAirport> airports, List<RawFlight> flights) {

    public record RawAirport(
            String code, String name, String city, String country, String timezone) {}

    public record RawFlight(
            String flightNumber,
            String airline,
            String origin,
            String destination,
            String departureTime,
            String arrivalTime,
            JsonNode price,
            String aircraft) {}
}
