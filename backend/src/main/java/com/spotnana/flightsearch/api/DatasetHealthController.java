package com.spotnana.flightsearch.api;

import com.spotnana.flightsearch.api.dto.DatasetHealthView;
import com.spotnana.flightsearch.application.DatasetLoadReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness plus an account of what the loader made of the dataset.
 *
 * <p>Reaching this endpoint at all proves the schedule loaded, since an unusable dataset
 * fails context startup. Compose uses it as the backend health check, which is what lets the
 * frontend wait for a service that is genuinely ready rather than merely listening.
 */
@RestController
@RequestMapping("/api/v1/health")
public class DatasetHealthController {

    private final DatasetLoadReport report;

    public DatasetHealthController(DatasetLoadReport report) {
        this.report = report;
    }

    @GetMapping
    public DatasetHealthView health() {
        return new DatasetHealthView(
                "UP",
                new DatasetHealthView.Dataset(
                        report.airportCount(),
                        report.flightsLoaded(),
                        report.quarantinedCount(),
                        report.coercedCount(),
                        report.quarantinedFlights().stream()
                                .map(
                                        q ->
                                                new DatasetHealthView.QuarantinedFlightView(
                                                        q.flightNumber(),
                                                        q.reason().name(),
                                                        q.detail()))
                                .toList(),
                        report.coercedValues().stream()
                                .map(
                                        c ->
                                                new DatasetHealthView.CoercedValueView(
                                                        c.flightNumber(),
                                                        c.field(),
                                                        c.reason().name(),
                                                        c.detail()))
                                .toList()));
    }
}
