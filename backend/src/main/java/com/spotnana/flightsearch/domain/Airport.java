package com.spotnana.flightsearch.domain;

import java.time.ZoneId;
import java.util.Objects;

/**
 * An airport, resolved from the dataset with its IANA time zone already parsed.
 *
 * <p>Holding a {@link ZoneId} rather than the raw zone string means every downstream
 * calculation converts local times through a real zone, including historical daylight
 * saving rules, instead of a fixed offset that would be wrong half the year.
 */
public record Airport(String code, String name, String city, String country, ZoneId zone) {

    public Airport {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(country, "country");
        Objects.requireNonNull(zone, "zone");

        if (code.isBlank()) {
            throw new IllegalArgumentException("Airport code must not be blank");
        }
        if (country.isBlank()) {
            throw new IllegalArgumentException("Airport %s has a blank country".formatted(code));
        }
    }
}
