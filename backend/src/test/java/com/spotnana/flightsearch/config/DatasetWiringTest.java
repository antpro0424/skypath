package com.spotnana.flightsearch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.spotnana.flightsearch.application.DatasetLoadReport;
import com.spotnana.flightsearch.application.FlightRepository;
import com.spotnana.flightsearch.domain.Airport;
import com.spotnana.flightsearch.domain.Flight;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** The schedule is loaded during context startup and served through the repository. */
@SpringBootTest
class DatasetWiringTest {

    @Autowired private FlightRepository flightRepository;
    @Autowired private DatasetLoadReport datasetLoadReport;

    @Test
    @DisplayName("resolves airports with their parsed time zone")
    void servesAirports() {
        Airport jfk = flightRepository.findAirport("JFK").orElseThrow();

        assertThat(jfk.country()).isEqualTo("US");
        assertThat(jfk.zone()).isEqualTo(ZoneId.of("America/New_York"));
    }

    @Test
    @DisplayName("looks airports up case-insensitively and ignores surrounding whitespace")
    void normalizesLookups() {
        assertThat(flightRepository.findAirport(" lax ")).isPresent();
        assertThat(flightRepository.findAirport("JKF")).isEmpty();
    }

    @Test
    @DisplayName("serves outgoing flights in departure order")
    void servesOutgoingFlights() {
        List<Flight> fromJfk = flightRepository.flightsDepartingFrom("JFK");

        assertThat(fromJfk).isNotEmpty();
        assertThat(fromJfk)
                .allSatisfy(flight -> assertThat(flight.origin().code()).isEqualTo("JFK"));
        assertThat(fromJfk)
                .isSortedAccordingTo(
                        (a, b) -> a.departureInstant().compareTo(b.departureInstant()));
    }

    @Test
    @DisplayName("returns an empty list for an airport with no departures")
    void unknownAirportYieldsNoFlights() {
        assertThat(flightRepository.flightsDepartingFrom("JKF")).isEmpty();
        assertThat(flightRepository.flightsDepartingFrom(null)).isEmpty();
    }

    @Test
    @DisplayName("publishes the load report so data quirks are observable at runtime")
    void publishesLoadReport() {
        assertThat(datasetLoadReport.airportCount()).isEqualTo(25);
        assertThat(datasetLoadReport.flightsLoaded()).isEqualTo(302);
        assertThat(datasetLoadReport.quarantinedCount()).isEqualTo(1);
        assertThat(datasetLoadReport.coercedCount()).isEqualTo(2);
        assertThat(datasetLoadReport.isClean()).isFalse();
    }
}
