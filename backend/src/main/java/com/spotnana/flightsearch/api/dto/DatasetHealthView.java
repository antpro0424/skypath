package com.spotnana.flightsearch.api.dto;

import java.util.List;

/**
 * Service status together with what the loader made of the dataset.
 *
 * <p>The supplied file contains deliberate defects, so reporting only "UP" would hide the
 * more useful fact: which records were dropped and which values were repaired. Exposing
 * that here makes the data-quality handling observable at runtime rather than only in the
 * startup log.
 */
public record DatasetHealthView(String status, Dataset dataset) {

    public record Dataset(
            int airports,
            int flightsLoaded,
            int quarantinedCount,
            int coercedCount,
            List<QuarantinedFlightView> quarantined,
            List<CoercedValueView> coercions) {}

    public record QuarantinedFlightView(String flightNumber, String reason, String detail) {}

    public record CoercedValueView(
            String flightNumber, String field, String reason, String detail) {}
}
