package com.spotnana.flightsearch.application;

import com.spotnana.flightsearch.domain.Itinerary;
import java.util.Comparator;

/**
 * The order results are returned in.
 *
 * <p>Shortest total travel time first, as the assignment requires. The remaining criteria
 * exist only to make the order total: without them, two itineraries of equal duration could
 * come back in either order, which would make tests flaky and the interface unstable
 * between identical searches.
 */
public final class ItineraryOrdering {

    public static final Comparator<Itinerary> SHORTEST_FIRST =
            Comparator.comparing(Itinerary::totalDuration)
                    .thenComparing(Itinerary::totalPrice)
                    .thenComparingInt(itinerary -> itinerary.segments().size())
                    .thenComparing(Itinerary::flightNumberSequence);

    private ItineraryOrdering() {}
}
