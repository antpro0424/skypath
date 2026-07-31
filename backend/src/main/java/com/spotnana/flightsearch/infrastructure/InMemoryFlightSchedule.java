package com.spotnana.flightsearch.infrastructure;

import com.spotnana.flightsearch.application.FlightRepository;
import com.spotnana.flightsearch.domain.Airport;
import com.spotnana.flightsearch.domain.Flight;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Serves the schedule from the indexes built at startup.
 *
 * <p>The dataset is a static snapshot of roughly three hundred flights, so it lives
 * entirely in memory. Both maps are unmodifiable and the flight lists inside them are
 * unmodifiable, so nothing can mutate the schedule after startup and no synchronization is
 * needed for concurrent reads.
 */
public class InMemoryFlightSchedule implements FlightRepository {

    private final Map<String, Airport> airportsByCode;
    private final Map<String, List<Flight>> outgoingFlightsByOrigin;

    public InMemoryFlightSchedule(LoadedFlightSchedule schedule) {
        this.airportsByCode = schedule.airportsByCode();
        this.outgoingFlightsByOrigin = schedule.outgoingFlightsByOrigin();
    }

    @Override
    public Optional<Airport> findAirport(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(airportsByCode.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    @Override
    public List<Flight> flightsDepartingFrom(String airportCode) {
        if (airportCode == null || airportCode.isBlank()) {
            return List.of();
        }
        return outgoingFlightsByOrigin.getOrDefault(
                airportCode.trim().toUpperCase(Locale.ROOT), List.of());
    }
}
