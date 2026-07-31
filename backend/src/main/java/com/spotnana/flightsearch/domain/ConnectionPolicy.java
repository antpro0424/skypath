package com.spotnana.flightsearch.domain;

import java.time.Duration;
import java.util.Optional;

/**
 * Decides whether two consecutive flights form a legal connection.
 *
 * <p>This is the single home for the connection rules. Traversal code asks this class and
 * never compares countries or minute counts itself, so there is one place to read, test and
 * change the policy.
 *
 * <p>Formally, for an arriving flight {@code a} and a departing flight {@code d}:
 *
 * <pre>
 *   valid(a, d) ⟺ a.destination == d.origin
 *               ∧ layover = d.departureInstant − a.arrivalInstant
 *               ∧ layover ≥ type(a, d).minimumLayover        (45m domestic, 90m otherwise)
 *               ∧ layover ≤ 6h
 * </pre>
 *
 * Both bounds are inclusive. A negative layover needs no separate rule: every minimum is
 * positive, so a flight departing before its predecessor lands fails the lower bound.
 */
public class ConnectionPolicy {

    /**
     * The layover if these flights connect legally, otherwise empty.
     *
     * <p>Returning the {@link Layover} rather than a boolean means the caller gets the
     * validated duration and connection type without recomputing them.
     */
    public Optional<Layover> connect(Flight arriving, Flight departing) {
        if (!isSameAirport(arriving, departing)) {
            return Optional.empty();
        }

        Duration layover = layoverBetween(arriving, departing);
        ConnectionType type = classify(arriving, departing);

        if (layover.compareTo(type.minimumLayover()) < 0
                || layover.compareTo(ConnectionRules.MAXIMUM_LAYOVER) > 0) {
            return Optional.empty();
        }
        return Optional.of(new Layover(arriving.destination(), layover, type));
    }

    /**
     * Classifies a connection, assuming the two flights meet at the same airport.
     *
     * <p>A connection is domestic only when neither flight crosses a border. Given that
     * the arriving flight lands where the departing flight starts, that pair of checks is
     * equivalent to requiring all three airports to share a country: if both legs were
     * internally domestic but in different countries, the shared airport would have to be
     * in two countries at once.
     *
     * <p>Note that the arriving flight counts. A domestic leg out of a hub is still an
     * international connection if the passenger arrived there from abroad, which is what
     * the assignment means by "both the arriving and departing flights".
     */
    public ConnectionType classify(Flight arriving, Flight departing) {
        boolean bothLegsStayInOneCountry =
                isWithinOneCountry(arriving) && isWithinOneCountry(departing);
        return bothLegsStayInOneCountry ? ConnectionType.DOMESTIC : ConnectionType.INTERNATIONAL;
    }

    /**
     * Wall-clock wait between landing and taking off again, measured between absolute
     * instants so it stays correct regardless of the zones involved.
     */
    public Duration layoverBetween(Flight arriving, Flight departing) {
        return Duration.between(arriving.arrivalInstant(), departing.departureInstant());
    }

    /**
     * Enforces the no-airport-change rule: a passenger cannot land at one airport and
     * depart from another, so JFK to LGA is not a connection.
     */
    private boolean isSameAirport(Flight arriving, Flight departing) {
        return arriving.destination().code().equals(departing.origin().code());
    }

    private boolean isWithinOneCountry(Flight flight) {
        return flight.origin().country().equals(flight.destination().country());
    }
}
