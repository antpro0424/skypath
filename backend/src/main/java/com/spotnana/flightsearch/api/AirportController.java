package com.spotnana.flightsearch.api;

import com.spotnana.flightsearch.api.dto.AirportView;
import com.spotnana.flightsearch.application.FlightRepository;
import com.spotnana.flightsearch.domain.Airport;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The airports the schedule covers.
 *
 * <p>Lets the interface offer autocomplete and show "JFK — New York" instead of a bare code,
 * without hardcoding a copy of the airport list into the frontend.
 */
@RestController
@RequestMapping("/api/v1/airports")
public class AirportController {

    private final FlightRepository flights;

    public AirportController(FlightRepository flights) {
        this.flights = flights;
    }

    @GetMapping
    public List<AirportView> airports() {
        return flights.airports().stream().map(AirportController::toView).toList();
    }

    private static AirportView toView(Airport airport) {
        return new AirportView(
                airport.code(),
                airport.name(),
                airport.city(),
                airport.country(),
                airport.zone().getId());
    }
}
