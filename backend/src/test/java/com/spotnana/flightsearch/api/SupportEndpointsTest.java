package com.spotnana.flightsearch.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The airports listing and the dataset health endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
class SupportEndpointsTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("lists every airport, ordered by code")
    void listsAirports() throws Exception {
        mockMvc.perform(get("/api/v1/airports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(25)))
                .andExpect(jsonPath("$[0].code").value("AMS"))
                .andExpect(jsonPath("$[0].city").value("Amsterdam"))
                .andExpect(jsonPath("$[0].country").value("NL"))
                .andExpect(jsonPath("$[0].timezone").value("Europe/Amsterdam"));
    }

    @Test
    @DisplayName("health reports the dataset the service actually loaded")
    void reportsDatasetHealth() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.dataset.airports").value(25))
                .andExpect(jsonPath("$.dataset.flightsLoaded").value(302));
    }

    @Test
    @DisplayName("health names the quarantined record and the coerced values")
    void reportsDataQuirks() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataset.quarantinedCount").value(1))
                .andExpect(jsonPath("$.dataset.quarantined[0].flightNumber").value("SP995"))
                .andExpect(jsonPath("$.dataset.quarantined[0].reason").value("UNKNOWN_AIRPORT"))
                .andExpect(jsonPath("$.dataset.coercedCount").value(2))
                .andExpect(jsonPath("$.dataset.coercions", hasSize(2)))
                .andExpect(
                        jsonPath("$.dataset.coercions[0].reason")
                                .value("PRICE_STRING_TO_DECIMAL"));
    }
}
