package com.spotnana.flightsearch.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.spotnana.flightsearch.application.DatasetLoadReport;
import com.spotnana.flightsearch.application.DatasetLoadReport.CoercedValue;
import com.spotnana.flightsearch.application.DatasetLoadReport.CoercionReason;
import com.spotnana.flightsearch.application.DatasetLoadReport.QuarantineReason;
import com.spotnana.flightsearch.application.DatasetLoadReport.QuarantinedFlight;
import com.spotnana.flightsearch.domain.Airport;
import com.spotnana.flightsearch.domain.Flight;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * Reads {@code flights.json}, resolves every timestamp through its airport's time zone,
 * and builds the immutable indexes the search uses.
 *
 * <p>This is a plain class with no Spring annotations so it can be exercised directly
 * against an in-memory resource in tests.
 */
public class FlightDatasetLoader {

    private static final Logger log = LoggerFactory.getLogger(FlightDatasetLoader.class);

    /** Departure order first, flight number as a stable tie-break for determinism. */
    private static final Comparator<Flight> DEPARTURE_ORDER =
            Comparator.comparing(Flight::departureInstant).thenComparing(Flight::flightNumber);

    private final ObjectMapper objectMapper;

    public FlightDatasetLoader() {
        this(defaultObjectMapper());
    }

    public FlightDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reading floats as {@link BigDecimal} keeps prices exact, so {@code 299.00} never
     * passes through a double.
     *
     * <p>Trailing-zero stripping is switched off as well. Jackson enables it by default,
     * which would rewrite {@code 299.00} as {@code 299}; the loader's job is to report the
     * dataset faithfully, and presenting money at a fixed scale belongs at the API
     * boundary, not here.
     */
    private static ObjectMapper defaultObjectMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
                .build();
    }

    public LoadedFlightSchedule load(Resource location) {
        RawDataset raw = read(location);
        Map<String, Airport> airportsByCode = resolveAirports(raw.airports());

        List<QuarantinedFlight> quarantined = new ArrayList<>();
        List<CoercedValue> coerced = new ArrayList<>();
        List<Flight> flights = resolveFlights(raw.flights(), airportsByCode, quarantined, coerced);

        if (flights.isEmpty()) {
            throw new DatasetLoadException(
                    "Dataset at %s yielded no usable flights (%d rejected)"
                            .formatted(location.getDescription(), quarantined.size()));
        }

        DatasetLoadReport report =
                new DatasetLoadReport(airportsByCode.size(), flights.size(), quarantined, coerced);
        logReport(location, report);

        return new LoadedFlightSchedule(airportsByCode, indexByOrigin(flights), report);
    }

    private RawDataset read(Resource location) {
        try (InputStream in = location.getInputStream()) {
            RawDataset dataset = objectMapper.readValue(in, RawDataset.class);
            if (dataset == null || dataset.airports() == null || dataset.flights() == null) {
                throw new DatasetLoadException(
                        "Dataset at %s is missing an airports or flights array"
                                .formatted(location.getDescription()));
            }
            return dataset;
        } catch (IOException e) {
            throw new DatasetLoadException(
                    "Unable to read dataset at %s".formatted(location.getDescription()), e);
        }
    }

    // ---------------------------------------------------------------- airports

    /**
     * Airport defects fail startup rather than quarantine: an unusable airport silently
     * invalidates every flight that touches it, which is a broken dataset, not a bad row.
     */
    private Map<String, Airport> resolveAirports(List<RawDataset.RawAirport> rawAirports) {
        Map<String, Airport> byCode = new LinkedHashMap<>();

        for (RawDataset.RawAirport raw : rawAirports) {
            if (raw.code() == null || raw.code().isBlank()) {
                throw new DatasetLoadException("Dataset contains an airport with no code");
            }
            String code = normalizeCode(raw.code());

            Airport airport =
                    new Airport(code, raw.name(), raw.city(), raw.country(), parseZone(raw, code));

            if (byCode.putIfAbsent(code, airport) != null) {
                throw new DatasetLoadException("Duplicate airport code %s in dataset".formatted(code));
            }
        }

        if (byCode.isEmpty()) {
            throw new DatasetLoadException("Dataset contains no airports");
        }
        return Map.copyOf(byCode);
    }

    private ZoneId parseZone(RawDataset.RawAirport raw, String code) {
        try {
            return ZoneId.of(raw.timezone());
        } catch (DateTimeException | NullPointerException e) {
            throw new DatasetLoadException(
                    "Airport %s has an unrecognized time zone: %s".formatted(code, raw.timezone()), e);
        }
    }

    // ----------------------------------------------------------------- flights

    private List<Flight> resolveFlights(
            List<RawDataset.RawFlight> rawFlights,
            Map<String, Airport> airportsByCode,
            List<QuarantinedFlight> quarantined,
            List<CoercedValue> coerced) {

        List<Flight> flights = new ArrayList<>(rawFlights.size());
        Set<String> seenFlightNumbers = new HashSet<>();

        for (RawDataset.RawFlight raw : rawFlights) {
            try {
                if (raw.flightNumber() == null || raw.flightNumber().isBlank()) {
                    throw new RejectedFlightException(
                            QuarantineReason.MISSING_FLIGHT_NUMBER, "Record has no flight number");
                }
                if (!seenFlightNumbers.add(raw.flightNumber())) {
                    throw new RejectedFlightException(
                            QuarantineReason.DUPLICATE_FLIGHT_NUMBER,
                            "Flight number %s already seen; later record dropped"
                                    .formatted(raw.flightNumber()));
                }
                flights.add(resolveFlight(raw, airportsByCode, coerced));
            } catch (RejectedFlightException e) {
                quarantined.add(
                        new QuarantinedFlight(raw.flightNumber(), e.reason(), e.getMessage()));
            }
        }
        return flights;
    }

    private Flight resolveFlight(
            RawDataset.RawFlight raw, Map<String, Airport> airportsByCode, List<CoercedValue> coerced) {

        Airport origin = requireAirport(raw.origin(), "origin", airportsByCode);
        Airport destination = requireAirport(raw.destination(), "destination", airportsByCode);

        if (origin.code().equals(destination.code())) {
            throw new RejectedFlightException(
                    QuarantineReason.SELF_LOOP,
                    "Origin and destination are both %s".formatted(origin.code()));
        }

        // The crux of the whole exercise: each naive local timestamp is zoned at *its own*
        // airport, so departure and arrival can be compared as absolute instants even when
        // the two clocks disagree, as they do across the date line.
        ZonedDateTime departure = parseLocal(raw.departureTime(), "departureTime").atZone(origin.zone());
        ZonedDateTime arrival =
                parseLocal(raw.arrivalTime(), "arrivalTime").atZone(destination.zone());

        if (!arrival.toInstant().isAfter(departure.toInstant())) {
            throw new RejectedFlightException(
                    QuarantineReason.NON_POSITIVE_DURATION,
                    "Arrival %s is not after departure %s once resolved to instants"
                            .formatted(arrival.toInstant(), departure.toInstant()));
        }

        BigDecimal price = resolvePrice(raw, coerced);

        return new Flight(
                raw.flightNumber(),
                raw.airline(),
                origin,
                destination,
                departure,
                arrival,
                price,
                raw.aircraft());
    }

    private Airport requireAirport(String code, String field, Map<String, Airport> airportsByCode) {
        if (code == null || code.isBlank()) {
            throw new RejectedFlightException(
                    QuarantineReason.UNKNOWN_AIRPORT, "Flight has no %s airport".formatted(field));
        }
        Airport airport = airportsByCode.get(normalizeCode(code));
        if (airport == null) {
            throw new RejectedFlightException(
                    QuarantineReason.UNKNOWN_AIRPORT,
                    "%s '%s' is not present in the airports list".formatted(field, code));
        }
        return airport;
    }

    private LocalDateTime parseLocal(String timestamp, String field) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new RejectedFlightException(
                    QuarantineReason.INVALID_TIMESTAMP, "%s is missing".formatted(field));
        }
        try {
            return LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException e) {
            throw new RejectedFlightException(
                    QuarantineReason.INVALID_TIMESTAMP,
                    "%s '%s' is not an ISO-8601 local date-time".formatted(field, timestamp));
        }
    }

    /**
     * Most records carry price as a JSON number, but a few carry it as a string. Those are
     * accepted and recorded, so the inconsistency is visible instead of being absorbed
     * silently by Jackson's default coercion.
     */
    private BigDecimal resolvePrice(RawDataset.RawFlight raw, List<CoercedValue> coerced) {
        JsonNode node = raw.price();
        if (node == null || node.isNull()) {
            throw new RejectedFlightException(QuarantineReason.INVALID_PRICE, "price is missing");
        }

        BigDecimal price;
        if (node.isNumber()) {
            price = node.decimalValue();
        } else if (node.isTextual()) {
            price = parseTextualPrice(node.asText());
            coerced.add(
                    new CoercedValue(
                            raw.flightNumber(),
                            "price",
                            CoercionReason.PRICE_STRING_TO_DECIMAL,
                            "JSON string \"%s\" read as a decimal".formatted(node.asText())));
        } else {
            throw new RejectedFlightException(
                    QuarantineReason.INVALID_PRICE,
                    "price is neither a number nor a string (%s)".formatted(node.getNodeType()));
        }

        if (price.signum() < 0) {
            throw new RejectedFlightException(
                    QuarantineReason.INVALID_PRICE, "price %s is negative".formatted(price));
        }
        return price;
    }

    private BigDecimal parseTextualPrice(String text) {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new RejectedFlightException(
                    QuarantineReason.INVALID_PRICE, "price '%s' is not numeric".formatted(text));
        }
    }

    // ----------------------------------------------------------------- indexing

    private Map<String, List<Flight>> indexByOrigin(List<Flight> flights) {
        Map<String, List<Flight>> byOrigin = new HashMap<>();
        for (Flight flight : flights) {
            byOrigin.computeIfAbsent(flight.origin().code(), code -> new ArrayList<>()).add(flight);
        }
        byOrigin.replaceAll((code, list) -> list.stream().sorted(DEPARTURE_ORDER).toList());
        return Map.copyOf(byOrigin);
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void logReport(Resource location, DatasetLoadReport report) {
        if (report.isClean()) {
            log.info(
                    "Loaded {} airports and {} flights from {}",
                    report.airportCount(),
                    report.flightsLoaded(),
                    location.getDescription());
            return;
        }

        log.warn(
                "Loaded {} airports and {} flights from {} with {} quarantined and {} coerced",
                report.airportCount(),
                report.flightsLoaded(),
                location.getDescription(),
                report.quarantinedCount(),
                report.coercedCount());
        report.quarantinedFlights()
                .forEach(q -> log.warn("  quarantined {}: {} — {}", q.flightNumber(), q.reason(), q.detail()));
        report.coercedValues()
                .forEach(c -> log.warn("  coerced {}.{}: {} — {}", c.flightNumber(), c.field(), c.reason(), c.detail()));
    }

    /**
     * Signals that one record cannot be resolved. Caught immediately by the loop above and
     * mapped to a report entry, so it never escapes this class.
     */
    private static final class RejectedFlightException extends RuntimeException {
        private final QuarantineReason reason;

        private RejectedFlightException(QuarantineReason reason, String message) {
            super(message, null, false, false);
            this.reason = reason;
        }

        private QuarantineReason reason() {
            return reason;
        }
    }
}
