package com.spotnana.flightsearch.application;

import com.spotnana.flightsearch.domain.Airport;
import com.spotnana.flightsearch.domain.Flight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A repository built from a handful of flights, so search tests can declare exactly the
 * network they need without parsing JSON or starting Spring.
 *
 * <p>It reproduces the one guarantee the real index makes and the search depends on:
 * outgoing lists sorted by departure instant, then flight number.
 */
final class FixtureFlightRepository implements FlightRepository {

    private static final Comparator<Flight> DEPARTURE_ORDER =
            Comparator.comparing(Flight::departureInstant).thenComparing(Flight::flightNumber);

    private final Map<String, Airport> airportsByCode = new LinkedHashMap<>();
    private final Map<String, List<Flight>> outgoingByOrigin = new HashMap<>();

    FixtureFlightRepository(Flight... flights) {
        this(List.of(flights));
    }

    FixtureFlightRepository(List<Flight> flights) {
        for (Flight flight : flights) {
            airportsByCode.putIfAbsent(flight.origin().code(), flight.origin());
            airportsByCode.putIfAbsent(flight.destination().code(), flight.destination());
            outgoingByOrigin
                    .computeIfAbsent(flight.origin().code(), code -> new ArrayList<>())
                    .add(flight);
        }
        outgoingByOrigin.replaceAll((code, list) -> list.stream().sorted(DEPARTURE_ORDER).toList());
    }

    @Override
    public Optional<Airport> findAirport(String code) {
        return Optional.ofNullable(airportsByCode.get(code));
    }

    @Override
    public List<Flight> flightsDepartingFrom(String airportCode) {
        return outgoingByOrigin.getOrDefault(airportCode, List.of());
    }
}
