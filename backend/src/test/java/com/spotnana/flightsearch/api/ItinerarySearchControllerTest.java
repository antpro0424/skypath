package com.spotnana.flightsearch.api;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** The HTTP contract: status codes, response shape and error schema. */
@SpringBootTest
@AutoConfigureMockMvc
class ItinerarySearchControllerTest {

    private static final String SEARCH = "/api/v1/itineraries/search";
    private static final String DATE = "2024-03-15";

    /** ISO-8601 with an explicit offset, seconds always present. */
    private static final String OFFSET_TIMESTAMP =
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}";

    @Autowired private MockMvc mockMvc;

    // ------------------------------------------------------------ successful searches

    @Nested
    @DisplayName("successful searches")
    class Successful {

        @Test
        @DisplayName("returns itineraries and echoes the normalized query")
        void returnsItineraries() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.query.origin").value("JFK"))
                    .andExpect(jsonPath("$.query.destination").value("LAX"))
                    .andExpect(jsonPath("$.query.date").value(DATE))
                    .andExpect(jsonPath("$.itineraries", hasSize(greaterThan(0))));
        }

        @Test
        @DisplayName("segments carry offset-aware times, zone ids and two-decimal prices")
        void segmentShape() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itineraries[0].segments[0].flightNumber").isNotEmpty())
                    .andExpect(jsonPath("$.itineraries[0].segments[0].airline").isNotEmpty())
                    .andExpect(jsonPath("$.itineraries[0].segments[0].origin").value("JFK"))
                    .andExpect(jsonPath("$.itineraries[0].segments[0].aircraft").isNotEmpty())
                    .andExpect(
                            jsonPath("$.itineraries[0].segments[0].departureTime")
                                    .value(matchesPattern(OFFSET_TIMESTAMP)))
                    .andExpect(
                            jsonPath("$.itineraries[0].segments[0].arrivalTime")
                                    .value(matchesPattern(OFFSET_TIMESTAMP)))
                    .andExpect(
                            jsonPath("$.itineraries[0].segments[0].departureTimezone")
                                    .value("America/New_York"))
                    .andExpect(jsonPath("$.itineraries[0].segments[0].price").isNumber());
        }

        @Test
        @DisplayName("prices are serialized as plain decimals with two places")
        void pricesAreSerializedAsMoney() throws Exception {
            // Asserted against the raw body: JsonPath parses JSON numbers into Double, which
            // would discard the trailing zero before any assertion could see it.
            String body = responseFor("JFK", "LAX");

            org.assertj.core.api.Assertions.assertThat(body)
                    .containsPattern("\"price\":\\d+\\.\\d{2}")
                    .containsPattern("\"totalPrice\":\\d+\\.\\d{2}")
                    .doesNotContain("E+")
                    .doesNotContain("E-");
        }

        @Test
        @DisplayName("itineraries report stops, total duration and total price")
        void itineraryTotals() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itineraries[0].stops").value(0))
                    .andExpect(jsonPath("$.itineraries[0].layovers", hasSize(0)))
                    .andExpect(
                            jsonPath("$.itineraries[0].totalDurationMinutes")
                                    .value(greaterThan(0)))
                    .andExpect(jsonPath("$.itineraries[0].totalPrice").isNumber());
        }

        @Test
        @DisplayName("layovers report the rule that admitted them")
        void layoverShape() throws Exception {
            // BOS to SEA has no direct flight, so every result carries at least one layover.
            mockMvc.perform(get(SEARCH).param("origin", "BOS").param("destination", "SEA").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itineraries[0].layovers[0].airport").isNotEmpty())
                    .andExpect(
                            jsonPath("$.itineraries[0].layovers[0].durationMinutes")
                                    .value(greaterThanOrEqualTo(45)))
                    .andExpect(
                            jsonPath("$.itineraries[0].layovers[0].minimumRequiredMinutes")
                                    .value(greaterThanOrEqualTo(45)))
                    .andExpect(
                            jsonPath("$.itineraries[0].layovers[0].connectionType")
                                    .value(matchesPattern("DOMESTIC|INTERNATIONAL")));
        }

        @Test
        @DisplayName("results are ordered shortest first")
        void ordersByDuration() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$.itineraries[0].totalDurationMinutes")
                                    .value(
                                            lessThanOrEqualTo(
                                                    (Integer)
                                                            com.jayway.jsonpath.JsonPath.read(
                                                                    responseFor("JFK", "LAX"),
                                                                    "$.itineraries[-1].totalDurationMinutes"))));
        }

        @Test
        @DisplayName("normalizes lower case and surrounding whitespace")
        void normalizesInput() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", " jfk ").param("destination", "lax").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.query.origin").value("JFK"))
                    .andExpect(jsonPath("$.query.destination").value("LAX"));
        }

        @Test
        @DisplayName("a date with no flights is an empty list, not an error")
        void emptyResultsAreNotAnError() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "LAX").param("date", "2024-03-20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itineraries", hasSize(0)));
        }

        @Test
        @DisplayName("no returned layover breaches the six hour maximum")
        void respectsMaximumLayover() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "BOS").param("destination", "SEA").param("date", DATE))
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$.itineraries[*].layovers[*].durationMinutes")
                                    .value(everyItem(lessThanOrEqualTo(360))));
        }

        private String responseFor(String origin, String destination) throws Exception {
            return mockMvc
                    .perform(get(SEARCH).param("origin", origin).param("destination", destination).param("date", DATE))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }
    }

    // ------------------------------------------------------------ rejected requests

    @Nested
    @DisplayName("rejected requests")
    class Rejected {

        @Test
        @DisplayName("case 4: JFK to JFK is a validation error, not a 500")
        void sameOriginAndDestination() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "JFK").param("date", DATE))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.code").value("SAME_ORIGIN_AND_DESTINATION"))
                    .andExpect(jsonPath("$.detail").isNotEmpty())
                    .andExpect(jsonPath("$.instance").value(SEARCH));
        }

        @Test
        @DisplayName("case 5: an unknown airport code is a 400 with a clear reason")
        void unknownAirport() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "XXX").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_AIRPORT"))
                    .andExpect(jsonPath("$.errors[0].field").value("origin"));
        }

        @Test
        @DisplayName("an unknown destination names the destination field")
        void unknownDestination() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "ZZZ").param("date", DATE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_AIRPORT"))
                    .andExpect(jsonPath("$.errors[0].field").value("destination"));
        }

        @Test
        @DisplayName("a code that is not three letters is rejected on format")
        void malformedAirportCode() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "J").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_AIRPORT_CODE"));

            mockMvc.perform(get(SEARCH).param("origin", "1234").param("destination", "LAX").param("date", DATE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_AIRPORT_CODE"));
        }

        @Test
        @DisplayName("a malformed date is rejected as a date problem")
        void malformedDate() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("destination", "LAX").param("date", "15-03-2024"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_DATE"))
                    .andExpect(jsonPath("$.errors[0].field").value("date"));
        }

        @Test
        @DisplayName("a missing parameter names the parameter")
        void missingParameter() throws Exception {
            mockMvc.perform(get(SEARCH).param("origin", "JFK").param("date", DATE))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                    .andExpect(jsonPath("$.detail").value("destination is required."));
        }
    }
}
