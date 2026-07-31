package com.spotnana.flightsearch.application;

import com.spotnana.flightsearch.domain.ConnectionPolicy;
import com.spotnana.flightsearch.domain.ConnectionRules;
import com.spotnana.flightsearch.domain.Flight;
import com.spotnana.flightsearch.domain.Itinerary;
import com.spotnana.flightsearch.domain.Layover;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds every legal itinerary between two airports on a given date.
 *
 * <p>The schedule is a directed temporal graph: airports are nodes and flights are edges
 * constrained by time. The search is a depth-first traversal bounded at
 * {@link ConnectionRules#MAXIMUM_SEGMENTS} flights, which is a direct expression of
 * "direct, one stop, or two stops" rather than a general shortest-path algorithm.
 *
 * <p>Complexity is {@code O(f · b²)} policy evaluations, where {@code f} is the number of
 * eligible first flights and {@code b} the branching factor at a connecting airport. The
 * depth bound of three is what keeps the exponent at two. On the supplied dataset the worst
 * case is roughly 31 × 36² evaluations, and the six-hour window narrows {@code b} much
 * further in practice.
 */
public class ItinerarySearchService {

    private static final Logger log = LoggerFactory.getLogger(ItinerarySearchService.class);

    private final FlightRepository flights;
    private final ConnectionPolicy connectionPolicy;

    public ItinerarySearchService(FlightRepository flights, ConnectionPolicy connectionPolicy) {
        this.flights = flights;
        this.connectionPolicy = connectionPolicy;
    }

    /**
     * @return every itinerary matching the query, shortest total travel time first. Empty
     *     when no route exists; an unknown airport is a validation concern handled upstream.
     */
    public List<Itinerary> search(SearchQuery query) {
        List<Itinerary> found = new ArrayList<>();

        for (Flight first : flights.flightsDepartingFrom(query.origin())) {
            // The requested date is the departure date of the first segment, read at the
            // origin airport. Later segments may roll into the next calendar day.
            if (!first.departureLocalDate().equals(query.date())) {
                continue;
            }

            List<Flight> segments = new ArrayList<>(ConnectionRules.MAXIMUM_SEGMENTS);
            List<Layover> layovers = new ArrayList<>(ConnectionRules.MAXIMUM_SEGMENTS - 1);
            Set<String> visitedAirports = new HashSet<>();

            segments.add(first);
            visitedAirports.add(query.origin());
            visitedAirports.add(first.destination().code());

            expand(segments, layovers, visitedAirports, query, found);
        }

        return deduplicate(found).stream().sorted(ItineraryOrdering.SHORTEST_FIRST).toList();
    }

    /**
     * Extends the current path by one segment in every legal way.
     *
     * <p>Segments, layovers and the visited set are mutated and restored around each
     * recursive call, which is ordinary backtracking. {@link Itinerary} copies both lists on
     * construction, so a recorded result is never disturbed by later mutation.
     */
    private void expand(
            List<Flight> segments,
            List<Layover> layovers,
            Set<String> visitedAirports,
            SearchQuery query,
            List<Itinerary> found) {

        Flight last = segments.get(segments.size() - 1);

        // Checked before the depth bound so a path that arrives on its third segment is
        // still recorded. Reversing these two statements would silently drop every
        // two-stop itinerary.
        if (last.destination().code().equals(query.destination())) {
            found.add(new Itinerary(segments, layovers));
            // Reaching the destination ends the journey; flying onward and back would be
            // a different, nonsensical itinerary.
            return;
        }

        if (segments.size() == ConnectionRules.MAXIMUM_SEGMENTS) {
            return;
        }

        for (Flight next : candidateConnections(last)) {
            // Cycle prevention: never route through an airport already on this path.
            if (visitedAirports.contains(next.destination().code())) {
                continue;
            }

            Optional<Layover> layover = connectionPolicy.connect(last, next);
            if (layover.isEmpty()) {
                continue;
            }

            segments.add(next);
            layovers.add(layover.get());
            visitedAirports.add(next.destination().code());

            expand(segments, layovers, visitedAirports, query, found);

            segments.remove(segments.size() - 1);
            layovers.remove(layovers.size() - 1);
            visitedAirports.remove(next.destination().code());
        }
    }

    /**
     * Flights that could plausibly connect from {@code arriving}, narrowed to the six-hour
     * window before the policy is consulted.
     *
     * <p>Outgoing lists are sorted by departure instant, so the window is a contiguous
     * slice. The lower bound uses the <em>shorter</em> of the two minima, because whether a
     * connection is domestic depends on the candidate's destination and is therefore unknown
     * until the candidate is in hand; the exact rule still runs on every flight returned
     * here. The upper bound is independent of connection type, so it is a safe cut-off.
     */
    private List<Flight> candidateConnections(Flight arriving) {
        List<Flight> outgoing = flights.flightsDepartingFrom(arriving.destination().code());
        if (outgoing.isEmpty()) {
            return List.of();
        }

        Instant landed = arriving.arrivalInstant();
        Instant earliest = landed.plus(ConnectionRules.MINIMUM_DOMESTIC_LAYOVER);
        Instant latest = landed.plus(ConnectionRules.MAXIMUM_LAYOVER);

        int from = firstDepartureAtOrAfter(outgoing, earliest);
        int to = from;
        while (to < outgoing.size() && !outgoing.get(to).departureInstant().isAfter(latest)) {
            to++;
        }
        return outgoing.subList(from, to);
    }

    /** Lower bound: index of the first flight departing at or after {@code earliest}. */
    private int firstDepartureAtOrAfter(List<Flight> outgoing, Instant earliest) {
        int low = 0;
        int high = outgoing.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (outgoing.get(mid).departureInstant().isBefore(earliest)) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Guards against emitting the same flight sequence twice.
     *
     * <p>The traversal picks a distinct next flight on every branch, so this should never
     * remove anything. It logs when it does, because a duplicate would mean the traversal is
     * wrong and silently discarding the evidence would hide that.
     */
    private List<Itinerary> deduplicate(List<Itinerary> found) {
        Map<String, Itinerary> byFlightNumbers = new LinkedHashMap<>();
        for (Itinerary itinerary : found) {
            byFlightNumbers.putIfAbsent(itinerary.flightNumberSequence(), itinerary);
        }

        int duplicates = found.size() - byFlightNumbers.size();
        if (duplicates > 0) {
            log.warn("Traversal produced {} duplicate itineraries, which indicates a defect", duplicates);
        }
        return List.copyOf(byFlightNumbers.values());
    }
}
