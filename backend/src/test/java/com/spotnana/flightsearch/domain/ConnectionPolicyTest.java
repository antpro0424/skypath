package com.spotnana.flightsearch.domain;

import static com.spotnana.flightsearch.domain.TestAirports.CDG;
import static com.spotnana.flightsearch.domain.TestAirports.JFK;
import static com.spotnana.flightsearch.domain.TestAirports.LAX;
import static com.spotnana.flightsearch.domain.TestAirports.LGA;
import static com.spotnana.flightsearch.domain.TestAirports.LHR;
import static com.spotnana.flightsearch.domain.TestAirports.NRT;
import static com.spotnana.flightsearch.domain.TestAirports.ORD;
import static com.spotnana.flightsearch.domain.TestAirports.YYZ;
import static com.spotnana.flightsearch.domain.TestAirports.arrivingAt;
import static com.spotnana.flightsearch.domain.TestAirports.departingAt;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConnectionPolicyTest {

    private static final Instant TOUCHDOWN = Instant.parse("2024-03-15T18:00:00Z");

    private final ConnectionPolicy policy = new ConnectionPolicy();

    // ------------------------------------------------------------ classification

    @Nested
    @DisplayName("domestic versus international")
    class Classification {

        @Test
        @DisplayName("JFK to ORD to LAX is domestic: no leg leaves the US")
        void bothLegsWithinOneCountry() {
            assertThat(classifyVia(JFK, ORD, LAX)).isEqualTo(ConnectionType.DOMESTIC);
        }

        @Test
        @DisplayName("JFK to LHR to CDG is international")
        void bothLegsCrossBorders() {
            assertThat(classifyVia(JFK, LHR, CDG)).isEqualTo(ConnectionType.INTERNATIONAL);
        }

        @Test
        @DisplayName("LHR to JFK to LAX is international because the arriving leg crossed a border")
        void internationalArrivalMakesTheConnectionInternational() {
            // The departing leg is purely domestic, but the passenger arrived from abroad.
            assertThat(classifyVia(LHR, JFK, LAX)).isEqualTo(ConnectionType.INTERNATIONAL);
        }

        @Test
        @DisplayName("JFK to LAX to NRT is international because the departing leg leaves the US")
        void internationalDepartureMakesTheConnectionInternational() {
            assertThat(classifyVia(JFK, LAX, NRT)).isEqualTo(ConnectionType.INTERNATIONAL);
        }

        @Test
        @DisplayName("connecting through a third country is international even between US airports")
        void connectingThroughAnotherCountryIsInternational() {
            assertThat(classifyVia(JFK, YYZ, LAX)).isEqualTo(ConnectionType.INTERNATIONAL);
        }

        private ConnectionType classifyVia(Airport from, Airport via, Airport to) {
            return policy.classify(
                    arrivingAt("SP1", from, via, TOUCHDOWN),
                    departingAt("SP2", via, to, TOUCHDOWN.plus(Duration.ofHours(2))));
        }
    }

    // ------------------------------------------------------------ domestic minimum

    @Nested
    @DisplayName("domestic minimum of 45 minutes")
    class DomesticMinimum {

        @Test
        @DisplayName("exactly 45 minutes connects")
        void exactMinimumIsAccepted() {
            assertThat(domesticLayoverOf(Duration.ofMinutes(45))).isPresent();
        }

        @Test
        @DisplayName("44 minutes does not connect")
        void oneMinuteShortIsRejected() {
            assertThat(domesticLayoverOf(Duration.ofMinutes(44))).isEmpty();
        }

        @Test
        @DisplayName("46 minutes connects")
        void oneMinuteOverIsAccepted() {
            assertThat(domesticLayoverOf(Duration.ofMinutes(46))).isPresent();
        }

        @Test
        @DisplayName("the accepted layover reports 45 minutes as its requirement")
        void reportsTheDomesticThreshold() {
            Layover layover = domesticLayoverOf(Duration.ofMinutes(45)).orElseThrow();

            assertThat(layover.connectionType()).isEqualTo(ConnectionType.DOMESTIC);
            assertThat(layover.minimumRequired()).isEqualTo(Duration.ofMinutes(45));
            assertThat(layover.airport()).isEqualTo(ORD);
            assertThat(layover.duration()).isEqualTo(Duration.ofMinutes(45));
        }

        private Optional<Layover> domesticLayoverOf(Duration layover) {
            return connectVia(JFK, ORD, LAX, layover);
        }
    }

    // ------------------------------------------------------------ international minimum

    @Nested
    @DisplayName("international minimum of 90 minutes")
    class InternationalMinimum {

        @Test
        @DisplayName("exactly 90 minutes connects")
        void exactMinimumIsAccepted() {
            assertThat(internationalLayoverOf(Duration.ofMinutes(90))).isPresent();
        }

        @Test
        @DisplayName("89 minutes does not connect")
        void oneMinuteShortIsRejected() {
            assertThat(internationalLayoverOf(Duration.ofMinutes(89))).isEmpty();
        }

        @Test
        @DisplayName("60 minutes would pass domestically but fails an international connection")
        void domesticThresholdDoesNotApply() {
            assertThat(connectVia(JFK, ORD, LAX, Duration.ofMinutes(60))).isPresent();
            assertThat(internationalLayoverOf(Duration.ofMinutes(60))).isEmpty();
        }

        @Test
        @DisplayName("the accepted layover reports 90 minutes as its requirement")
        void reportsTheInternationalThreshold() {
            Layover layover = internationalLayoverOf(Duration.ofMinutes(90)).orElseThrow();

            assertThat(layover.connectionType()).isEqualTo(ConnectionType.INTERNATIONAL);
            assertThat(layover.minimumRequired()).isEqualTo(Duration.ofMinutes(90));
        }

        private Optional<Layover> internationalLayoverOf(Duration layover) {
            return connectVia(JFK, LHR, CDG, layover);
        }
    }

    // ------------------------------------------------------------ maximum

    @Nested
    @DisplayName("maximum layover of 6 hours")
    class MaximumLayover {

        @Test
        @DisplayName("exactly 6 hours connects")
        void exactMaximumIsAccepted() {
            assertThat(connectVia(JFK, ORD, LAX, Duration.ofHours(6))).isPresent();
        }

        @Test
        @DisplayName("6 hours and 1 minute does not connect")
        void oneMinuteOverIsRejected() {
            assertThat(connectVia(JFK, ORD, LAX, Duration.ofHours(6).plusMinutes(1))).isEmpty();
        }

        @Test
        @DisplayName("the maximum applies to international connections too")
        void maximumIsIndependentOfConnectionType() {
            assertThat(connectVia(JFK, LHR, CDG, Duration.ofHours(6))).isPresent();
            assertThat(connectVia(JFK, LHR, CDG, Duration.ofHours(6).plusMinutes(1))).isEmpty();
        }
    }

    // ------------------------------------------------------------ impossible connections

    @Nested
    @DisplayName("connections that cannot be flown")
    class ImpossibleConnections {

        @Test
        @DisplayName("a passenger cannot change airports during a layover")
        void airportChangeIsRejected() {
            // Lands at JFK, next flight leaves LGA. Generous timing must not rescue it.
            Flight arriving = arrivingAt("SP1", ORD, JFK, TOUCHDOWN);
            Flight departing = departingAt("SP2", LGA, LAX, TOUCHDOWN.plus(Duration.ofHours(3)));

            assertThat(policy.connect(arriving, departing)).isEmpty();
        }

        @Test
        @DisplayName("a next flight departing before the previous one lands does not connect")
        void negativeLayoverIsRejected() {
            assertThat(connectVia(JFK, ORD, LAX, Duration.ofMinutes(-30))).isEmpty();
        }

        @Test
        @DisplayName("a next flight departing at the moment of arrival does not connect")
        void zeroLayoverIsRejected() {
            assertThat(connectVia(JFK, ORD, LAX, Duration.ZERO)).isEmpty();
        }
    }

    // ------------------------------------------------------------ layover measurement

    @Nested
    @DisplayName("layover measurement")
    class LayoverMeasurement {

        @Test
        @DisplayName("is measured between absolute instants, not local clocks")
        void measuredBetweenInstants() {
            Flight arriving = arrivingAt("SP1", JFK, ORD, TOUCHDOWN);
            Flight departing = departingAt("SP2", ORD, LAX, TOUCHDOWN.plus(Duration.ofMinutes(75)));

            assertThat(policy.layoverBetween(arriving, departing)).isEqualTo(Duration.ofMinutes(75));
        }

        @Test
        @DisplayName("a layover spanning local midnight is still measured correctly")
        void spansMidnight() {
            // 23:30 to 00:15 the next day in Chicago is a 45-minute wait, not a negative one.
            Instant landing = Instant.parse("2024-03-16T04:30:00Z"); // 23:30 CDT on 15 March
            Flight arriving = arrivingAt("SP1", JFK, ORD, landing);
            Flight departing = departingAt("SP2", ORD, LAX, landing.plus(Duration.ofMinutes(45)));

            assertThat(arriving.arrival().toLocalDate())
                    .isNotEqualTo(departing.departure().toLocalDate());
            assertThat(policy.connect(arriving, departing))
                    .get()
                    .extracting(Layover::duration)
                    .isEqualTo(Duration.ofMinutes(45));
        }
    }

    private Optional<Layover> connectVia(Airport from, Airport via, Airport to, Duration layover) {
        return policy.connect(
                arrivingAt("SP1", from, via, TOUCHDOWN),
                departingAt("SP2", via, to, TOUCHDOWN.plus(layover)));
    }
}
