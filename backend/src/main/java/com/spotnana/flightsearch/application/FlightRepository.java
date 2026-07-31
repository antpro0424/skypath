package com.spotnana.flightsearch.application;

import com.spotnana.flightsearch.domain.Airport;
import com.spotnana.flightsearch.domain.Flight;
import java.util.List;
import java.util.Optional;

/**
 * Read access to the loaded schedule.
 *
 * <p>Declared here rather than in the infrastructure package so the dependency arrow
 * points inward: the search use case will depend on this interface, and the in-memory
 * implementation depends on the use case's contract. It also lets search logic be tested
 * against a two-flight fixture with no JSON parsing and no Spring context.
 */
public interface FlightRepository {

    /** @param code an exact, already-normalized airport code */
    Optional<Airport> findAirport(String code);

    /** Every known airport, ordered by code so the listing is stable between calls. */
    List<Airport> airports();

    /**
     * Flights leaving the given airport, ordered by departure instant and then flight
     * number. Never null; an unknown airport yields an empty list.
     */
    List<Flight> flightsDepartingFrom(String airportCode);
}
