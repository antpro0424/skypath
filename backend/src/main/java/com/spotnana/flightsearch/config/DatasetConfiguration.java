package com.spotnana.flightsearch.config;

import com.spotnana.flightsearch.application.DatasetLoadReport;
import com.spotnana.flightsearch.application.FlightRepository;
import com.spotnana.flightsearch.infrastructure.FlightDatasetLoader;
import com.spotnana.flightsearch.infrastructure.InMemoryFlightSchedule;
import com.spotnana.flightsearch.infrastructure.LoadedFlightSchedule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the schedule once during context startup.
 *
 * <p>Because the load happens in a bean factory method, an unusable dataset fails startup
 * immediately rather than surfacing as an error on the first search.
 */
@Configuration
@EnableConfigurationProperties(DatasetProperties.class)
public class DatasetConfiguration {

    @Bean
    public FlightDatasetLoader flightDatasetLoader() {
        return new FlightDatasetLoader();
    }

    @Bean
    public LoadedFlightSchedule loadedFlightSchedule(
            FlightDatasetLoader loader, DatasetProperties properties) {
        return loader.load(properties.location());
    }

    @Bean
    public FlightRepository flightRepository(LoadedFlightSchedule schedule) {
        return new InMemoryFlightSchedule(schedule);
    }

    @Bean
    public DatasetLoadReport datasetLoadReport(LoadedFlightSchedule schedule) {
        return schedule.report();
    }
}
