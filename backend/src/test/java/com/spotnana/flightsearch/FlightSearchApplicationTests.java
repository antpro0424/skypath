package com.spotnana.flightsearch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test proving the Spring context starts. As the dataset loader and search
 * components arrive, a failure here means startup wiring broke rather than any
 * individual rule.
 */
@SpringBootTest
class FlightSearchApplicationTests {

    @Test
    void contextLoads() {
        // Intentionally empty: the assertion is that context startup does not throw.
    }
}
