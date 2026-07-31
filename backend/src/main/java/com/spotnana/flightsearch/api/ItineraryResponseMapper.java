package com.spotnana.flightsearch.api;

import com.spotnana.flightsearch.api.dto.ItinerarySearchResponse;
import com.spotnana.flightsearch.api.dto.ItineraryView;
import com.spotnana.flightsearch.api.dto.LayoverView;
import com.spotnana.flightsearch.api.dto.SegmentView;
import com.spotnana.flightsearch.application.SearchQuery;
import com.spotnana.flightsearch.domain.Flight;
import com.spotnana.flightsearch.domain.Itinerary;
import com.spotnana.flightsearch.domain.Layover;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns domain objects into the API's own types, so no internal model leaks into the
 * contract and the response shape can change without touching the search.
 */
@Component
public class ItineraryResponseMapper {

    /**
     * Money is presented at two decimal places. The loader keeps prices exactly as the
     * dataset wrote them, which leaves a mix of scales behind: the string-priced record
     * {@code SP996} carries {@code 99} while its neighbours carry {@code 95.00}. Presenting
     * a single scale is a display concern and belongs here rather than in the loader.
     *
     * <p>No dataset price has more than two decimals, so this normalizes without rounding.
     */
    private static final int PRICE_SCALE = 2;

    public ItinerarySearchResponse toResponse(SearchQuery query, List<Itinerary> itineraries) {
        return new ItinerarySearchResponse(
                new ItinerarySearchResponse.Query(query.origin(), query.destination(), query.date()),
                itineraries.stream().map(this::toView).toList());
    }

    private ItineraryView toView(Itinerary itinerary) {
        return new ItineraryView(
                itinerary.segments().stream().map(this::toView).toList(),
                itinerary.layovers().stream().map(this::toView).toList(),
                itinerary.stops(),
                itinerary.totalDuration().toMinutes(),
                money(itinerary.totalPrice()));
    }

    private SegmentView toView(Flight flight) {
        return new SegmentView(
                flight.flightNumber(),
                flight.airline(),
                flight.origin().code(),
                flight.destination().code(),
                flight.departure().toOffsetDateTime(),
                flight.arrival().toOffsetDateTime(),
                flight.origin().zone().getId(),
                flight.destination().zone().getId(),
                money(flight.price()),
                flight.aircraft());
    }

    private LayoverView toView(Layover layover) {
        return new LayoverView(
                layover.airport().code(),
                layover.duration().toMinutes(),
                layover.minimumRequired().toMinutes(),
                layover.connectionType().name());
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
