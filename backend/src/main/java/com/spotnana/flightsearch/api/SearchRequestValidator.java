package com.spotnana.flightsearch.api;

import com.spotnana.flightsearch.application.FlightRepository;
import com.spotnana.flightsearch.application.SearchQuery;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Normalizes and validates search parameters, producing the query the search runs on.
 *
 * <p>Format constraints are declared on the controller method with Bean Validation. What is
 * left is the part that needs the dataset: whether a well-formed code actually exists, and
 * whether the two codes differ.
 */
@Component
public class SearchRequestValidator {

    private final FlightRepository flights;

    public SearchRequestValidator(FlightRepository flights) {
        this.flights = flights;
    }

    public SearchQuery toQuery(String origin, String destination, LocalDate date) {
        String normalizedOrigin = normalize(origin);
        String normalizedDestination = normalize(destination);

        // Existence is checked first: for a request such as XXX to XXX, "that airport does
        // not exist" is more useful than "your two airports match".
        requireKnownAirport(normalizedOrigin, "origin");
        requireKnownAirport(normalizedDestination, "destination");

        if (normalizedOrigin.equals(normalizedDestination)) {
            throw new InvalidSearchException(
                    ErrorCode.SAME_ORIGIN_AND_DESTINATION,
                    "destination",
                    "Origin and destination must be different airports, but both are %s."
                            .formatted(normalizedOrigin));
        }

        return new SearchQuery(normalizedOrigin, normalizedDestination, date);
    }

    private void requireKnownAirport(String code, String field) {
        if (flights.findAirport(code).isEmpty()) {
            throw new InvalidSearchException(
                    ErrorCode.UNKNOWN_AIRPORT,
                    field,
                    "Unknown airport code '%s'.".formatted(code));
        }
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
