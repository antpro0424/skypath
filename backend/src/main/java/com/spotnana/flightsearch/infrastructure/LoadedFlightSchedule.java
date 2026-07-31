package com.spotnana.flightsearch.infrastructure;

import com.spotnana.flightsearch.application.DatasetLoadReport;
import com.spotnana.flightsearch.domain.Airport;
import com.spotnana.flightsearch.domain.Flight;
import java.util.List;
import java.util.Map;

/**
 * The immutable result of loading the schedule: the two lookup indexes plus an account of
 * what was rejected or repaired along the way.
 *
 * @param airportsByCode airports keyed by normalized code
 * @param outgoingFlightsByOrigin flights keyed by origin code, each list ordered by
 *     departure instant and then flight number
 */
public record LoadedFlightSchedule(
        Map<String, Airport> airportsByCode,
        Map<String, List<Flight>> outgoingFlightsByOrigin,
        DatasetLoadReport report) {

    public LoadedFlightSchedule {
        airportsByCode = Map.copyOf(airportsByCode);
        outgoingFlightsByOrigin = Map.copyOf(outgoingFlightsByOrigin);
    }
}
