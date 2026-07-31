package com.spotnana.flightsearch.config;

import com.spotnana.flightsearch.application.FlightRepository;
import com.spotnana.flightsearch.application.ItinerarySearchService;
import com.spotnana.flightsearch.domain.ConnectionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the search use case.
 *
 * <p>The domain classes carry no framework annotations, so they are declared as beans here
 * instead. That keeps {@code domain} free of any Spring dependency and constructible in a
 * plain unit test.
 */
@Configuration
public class SearchConfiguration {

    @Bean
    public ConnectionPolicy connectionPolicy() {
        return new ConnectionPolicy();
    }

    @Bean
    public ItinerarySearchService itinerarySearchService(
            FlightRepository flightRepository, ConnectionPolicy connectionPolicy) {
        return new ItinerarySearchService(flightRepository, connectionPolicy);
    }
}
