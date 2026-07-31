package com.spotnana.flightsearch.api;

import com.spotnana.flightsearch.api.dto.ItinerarySearchResponse;
import com.spotnana.flightsearch.application.ItinerarySearchService;
import com.spotnana.flightsearch.application.SearchQuery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Itinerary search.
 *
 * <p>Thin by design: validate, delegate, map. The rules live in the domain and the traversal
 * in the application layer.
 */
@RestController
@RequestMapping("/api/v1/itineraries")
@Validated
public class ItinerarySearchController {

    private static final String AIRPORT_CODE_PATTERN = "^\\s*[A-Za-z]{3}\\s*$";

    private final ItinerarySearchService searchService;
    private final SearchRequestValidator requestValidator;
    private final ItineraryResponseMapper responseMapper;

    public ItinerarySearchController(
            ItinerarySearchService searchService,
            SearchRequestValidator requestValidator,
            ItineraryResponseMapper responseMapper) {
        this.searchService = searchService;
        this.requestValidator = requestValidator;
        this.responseMapper = responseMapper;
    }

    /**
     * @param date the local calendar date of the first segment's departure, read at the
     *     origin airport. Later segments may fall on the following day.
     * @return 200 with the matching itineraries, shortest first. An empty list is a valid
     *     answer: it means no route exists, not that anything went wrong.
     */
    @GetMapping("/search")
    public ItinerarySearchResponse search(
            @RequestParam
                    @NotBlank(message = "origin is required")
                    @Pattern(
                            regexp = AIRPORT_CODE_PATTERN,
                            message = "origin must be a three-letter airport code")
                    String origin,
            @RequestParam
                    @NotBlank(message = "destination is required")
                    @Pattern(
                            regexp = AIRPORT_CODE_PATTERN,
                            message = "destination must be a three-letter airport code")
                    String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        SearchQuery query = requestValidator.toQuery(origin, destination, date);
        return responseMapper.toResponse(query, searchService.search(query));
    }
}
