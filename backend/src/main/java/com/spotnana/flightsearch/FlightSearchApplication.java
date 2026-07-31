package com.spotnana.flightsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the SkyPath flight connection search service.
 *
 * <p>The flight schedule is loaded into memory once at startup and never mutated, so the
 * application holds no external state and needs no datastore.
 */
@SpringBootApplication
public class FlightSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlightSearchApplication.class, args);
    }
}
