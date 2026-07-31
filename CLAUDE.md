# CLAUDE.md

## 1. Project Mission

Build a production-minded prototype flight connection search engine for the Spotnana take-home exercise.

The application must:

- Load `flights.json` at backend startup.
- Search itineraries by origin, destination, and departure date.
- Return direct, one-stop, and two-stop itineraries.
- Apply domestic and international connection rules precisely.
- Handle airport-local times and time zones correctly.
- Sort itineraries by total elapsed travel time.
- Provide a polished Next.js user interface.
- Run both services with `docker-compose up`.
- Include tests, documentation, and an honest discussion of tradeoffs.

This is a take-home prototype. Favor correctness, clarity, testability, and explainable design over unnecessary infrastructure.

---

## 2. Mandatory Technology Stack

### Backend

- Java 17
- Spring Boot
- Maven
- Jackson
- Jakarta Bean Validation
- JUnit 5
- AssertJ
- Spring Boot Test

### Frontend

- Next.js
- React
- TypeScript
- App Router
- CSS Modules or a small global stylesheet
- React Testing Library and Jest or Vitest

### Infrastructure

- Docker
- Docker Compose

Do not replace the required stack without explicit user approval.

---

## 3. Mandatory Design-First Workflow

Do not begin implementation immediately.

Before making code changes:

1. Read the assignment and this file completely.
2. Inspect the repository and `flights.json`.
3. Produce a concise design proposal covering:
   - repository structure;
   - domain model;
   - time-zone conversion strategy;
   - connection classification;
   - search algorithm;
   - API contract;
   - validation and error behavior;
   - frontend state model;
   - testing strategy;
   - Docker strategy;
   - assumptions and tradeoffs.
4. Identify any ambiguity that could affect correctness.
5. Recommend a default decision for every ambiguity.
6. Wait for user approval before implementing.

After approval:

- Implement one milestone at a time.
- Keep changes focused and reviewable.
- Run tests after every meaningful milestone.
- Summarize changed files, tests run, and remaining risks.
- Do not silently redesign approved architecture.
- Ask before adding major dependencies or features.

---

## 4. Recommended Repository Structure

```text
.
├── CLAUDE.md
├── README.md
├── docker-compose.yml
├── flights.json
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/spotnana/flightsearch/
│       │   │   ├── FlightSearchApplication.java
│       │   │   ├── api/
│       │   │   ├── application/
│       │   │   ├── domain/
│       │   │   ├── infrastructure/
│       │   │   └── config/
│       │   └── resources/
│       │       ├── application.yml
│       │       └── flights.json
│       └── test/
│           └── java/com/spotnana/flightsearch/
└── frontend/
    ├── Dockerfile
    ├── package.json
    ├── next.config.ts
    ├── tsconfig.json
    ├── app/
    ├── components/
    ├── lib/
    └── tests/
```

A simpler package structure is acceptable if the dependency direction remains clear.

---

## 5. Architectural Boundaries

Use a lightweight layered architecture.

### Domain layer

Owns:

- airport and flight concepts;
- resolved flight times;
- itinerary and layover concepts;
- connection-rule calculations;
- duration and price calculations.

The domain layer must not depend on Spring MVC, JSON DTOs, or frontend concerns.

### Application layer

Owns:

- the itinerary search use case;
- search constraints;
- orchestration of repositories and domain policies;
- deterministic result sorting.

### Infrastructure layer

Owns:

- loading and parsing `flights.json`;
- building the in-memory indexes;
- repository implementations;
- framework configuration.

### API layer

Owns:

- HTTP request validation;
- request and response DTOs;
- mapping application results to API responses;
- consistent error responses.

Do not create interfaces or abstractions that have only speculative value. Introduce a boundary when it improves testing, dependency direction, or clarity.

---

## 6. Data Loading and In-Memory Indexing

The dataset is static and small, approximately 260 flights and 25 airports.

Do not add a database unless the user explicitly requests one.

At startup:

1. Deserialize airports and raw flights from `flights.json`.
2. Validate airport references.
3. Resolve every flight's local departure and arrival time using airport time zones.
4. Convert both endpoints to `Instant`.
5. Reject or clearly report malformed records.
6. Build immutable lookup structures.

Recommended indexes:

```java
Map<String, Airport> airportsByCode;
Map<String, List<Flight>> outgoingFlightsByOrigin;
```

Sort each outgoing-flight list by departure `Instant`, then flight number for deterministic behavior.

Prefer immutable collections after startup.

---

## 7. Time-Zone Rules

This is the highest-risk correctness area.

The dataset stores local airport times without offsets.

For each flight:

```text
departureTime + origin.timezone -> ZonedDateTime -> departureInstant
arrivalTime   + destination.timezone -> ZonedDateTime -> arrivalInstant
```

Use Java time types:

- `LocalDate` for the search date;
- `LocalDateTime` for raw dataset values;
- `ZoneId` for airport time zones;
- `ZonedDateTime` when presenting local zoned times;
- `Instant` for comparisons and duration calculations;
- `Duration` for travel and layover durations.

Never calculate elapsed time by subtracting two raw `LocalDateTime` values.

The `SYD -> LAX` case must work even when the local arrival clock appears earlier than the local departure clock.

Validate that:

```text
arrivalInstant > departureInstant
```

Do not invent a date rollover heuristic unless inspection of the actual dataset proves it is necessary. If malformed data exists, document the chosen policy.

---

## 8. Search-Date Semantics

Interpret the API `date` as:

> The local calendar date of the first segment's departure at the requested origin airport.

Therefore, a flight is an eligible first segment when:

```text
flight.origin == requested origin
AND flight.departure local date at origin == requested date
```

Later segments may depart on the next calendar day if their absolute departure instant satisfies the connection rules.

Document this behavior in the README and API examples.

---

## 9. Connection Rules

An itinerary can contain at most three flight segments:

- direct: 1 segment;
- one stop: 2 segments;
- two stops: 3 segments.

For consecutive segments `previous` and `next`:

```text
previous.destination == next.origin
```

This equality enforces the no-airport-change rule.

Calculate:

```text
layover = next.departureInstant - previous.arrivalInstant
```

### Maximum layover

A layover is valid only when:

```text
layover <= 6 hours
```

### Minimum layover

Use 45 minutes only when the connection is fully domestic within one country.

Recommended interpretation:

```text
previous.origin.country
== previous.destination.country
== next.destination.country
```

Because `previous.destination == next.origin`, this means both the arriving and departing flight are domestic within the same country.

Otherwise, use a 90-minute minimum.

The boundary values are inclusive:

```text
layover >= minimum
AND layover <= maximum
```

Keep this logic in one named policy method or value object. Do not duplicate it inside traversal code.

---

## 10. Search Algorithm

Model the schedule as a directed temporal graph:

- airport = node;
- flight = time-constrained directed edge.

Use bounded depth-first search or breadth-first search with a maximum of three segments.

Recommended behavior:

1. Start from eligible first flights matching origin and local departure date.
2. Add the current path as a result whenever its destination equals the requested destination.
3. Stop expanding after three segments.
4. Expand only flights whose origin matches the current destination.
5. Apply layover rules before adding a next segment.
6. Prevent airport cycles within one itinerary.
7. Do not continue expanding a path after it reaches the requested destination.
8. Avoid duplicate itineraries by segment flight-number sequence.

Because depth is capped at three and the dataset is small, a clear bounded traversal is preferred over premature optimization.

Expected conceptual complexity:

```text
O(outgoing(origin) * branchingFactor^2)
```

with a strict depth bound of three segments.

A departure-time-sorted adjacency list may use binary search to skip flights departing before the minimum valid connection time, but correctness and readability come first.

---

## 11. Itinerary Calculations

For every itinerary:

### Total travel duration

```text
last arrivalInstant - first departureInstant
```

This includes all layovers.

### Layover duration

For every connection:

```text
next departureInstant - previous arrivalInstant
```

### Total price

Use `BigDecimal`.

Do not use `double` for money calculations.

### Sorting

Sort by:

1. total travel duration ascending;
2. total price ascending;
3. segment count ascending;
4. concatenated flight numbers ascending.

Only the first criterion is required, but deterministic tie-breakers make tests and UI behavior stable.

---

## 12. API Contract

Recommended endpoint:

```http
GET /api/v1/itineraries/search?origin=JFK&destination=LAX&date=2024-03-15
```

### Validation

- Normalize airport codes to uppercase and trim whitespace.
- Require exactly three alphabetic characters.
- Reject unknown airport codes.
- Reject missing or invalid dates.
- Reject identical origin and destination with a clear validation error.
- Do not return HTTP 500 for client input errors.

Recommended status behavior:

- `200 OK` with an array for a valid search, including an empty array when no itinerary exists;
- `400 Bad Request` for malformed input or identical airports;
- `404 Not Found` or `400 Bad Request` for unknown airports, but choose one convention and document it;
- `500 Internal Server Error` only for unexpected failures.

Preferred default: use `400 Bad Request` for all invalid search parameters, including unknown airports.

### Response shape

```json
{
  "query": {
    "origin": "JFK",
    "destination": "LAX",
    "date": "2024-03-15"
  },
  "itineraries": [
    {
      "segments": [
        {
          "flightNumber": "SP101",
          "airline": "SkyPath Airways",
          "origin": "JFK",
          "destination": "LAX",
          "departureTime": "2024-03-15T08:30:00-04:00",
          "arrivalTime": "2024-03-15T11:45:00-07:00",
          "departureTimezone": "America/New_York",
          "arrivalTimezone": "America/Los_Angeles",
          "price": 299.00,
          "aircraft": "A320"
        }
      ],
      "layovers": [],
      "totalDurationMinutes": 375,
      "totalPrice": 299.00
    }
  ]
}
```

For a connection:

```json
{
  "airport": "ORD",
  "durationMinutes": 75,
  "minimumRequiredMinutes": 45,
  "connectionType": "DOMESTIC"
}
```

Offset-aware timestamps are preferred because they make time-zone behavior visible and unambiguous.

Use DTOs. Do not expose internal domain objects directly.

Use Spring's `ProblemDetail` or one consistent custom error schema.

---

## 13. Frontend Design

Use one focused search page.

### Form

Fields:

- origin;
- destination;
- date.

Recommended input behavior:

- uppercase airport codes as the user types;
- trim whitespace;
- client-side format validation;
- prevent identical origin and destination;
- default date may be `2024-03-15` because the provided dataset is historical and limited.

Do not default to today's date because the dataset only covers March 15-16, 2024.

### States

The page must explicitly support:

- idle;
- invalid input;
- loading;
- success with results;
- success with empty results;
- API error.

### Results

Each itinerary card should show:

- total duration;
- total price;
- number of stops;
- each segment's flight number;
- departure and arrival airport;
- local departure and arrival time;
- airline;
- layover airport and duration.

Prefer readable text over decorative complexity.

### API access

Prefer a Next.js rewrite or server-side proxy so the browser calls the frontend origin and the frontend forwards `/api/*` to the backend container.

Avoid exposing the Docker service hostname to browser code.

---

## 14. Docker and Compose

`docker-compose up --build` must start both services.

Recommended services:

- `backend` on container port `8080`;
- `frontend` on container port `3000`.

Add:

- backend health check;
- frontend dependency on backend health where practical;
- environment variable for the internal backend URL;
- multi-stage builds;
- non-root runtime users where straightforward.

The application should be reachable at:

```text
http://localhost:3000
```

The backend may also be exposed at:

```text
http://localhost:8080
```

Do not rely on locally installed Java or Node after Docker starts.

---

## 15. Testing Strategy

### Backend unit tests

Cover:

- local time to `Instant` conversion;
- normal same-zone duration;
- cross-time-zone duration;
- international date-line crossing;
- exact 45-minute domestic layover;
- domestic layover below 45 minutes;
- exact 90-minute international layover;
- international layover below 90 minutes;
- exact six-hour maximum layover;
- layover above six hours;
- no airport changes;
- direct itinerary;
- one-stop itinerary;
- two-stop itinerary;
- maximum of two stops;
- cycle prevention;
- total duration;
- total price;
- deterministic sorting.

### Backend integration tests

Verify the supplied cases:

1. `JFK -> LAX, 2024-03-15`
2. `SFO -> NRT, 2024-03-15`
3. `BOS -> SEA, 2024-03-15`
4. `JFK -> JFK, 2024-03-15`
5. `XXX -> LAX, 2024-03-15`
6. `SYD -> LAX, 2024-03-15`

Do not assert guessed itinerary counts until the real dataset has been inspected.

Assert invariant behavior instead:

- all paths start and end correctly;
- segment count is between one and three;
- all connections satisfy timing rules;
- results are sorted;
- expected validation responses are stable;
- date-line duration is positive and correct.

### Frontend tests

Cover:

- required-field validation;
- airport-code validation;
- identical-airport validation;
- loading state;
- empty state;
- successful rendering;
- API error rendering.

### Optional end-to-end test

Add one Playwright smoke test only after the required implementation is complete and stable.

---

## 16. README Requirements

The README must include:

1. project overview;
2. screenshots or a concise UI description;
3. prerequisites;
4. Docker Compose instructions;
5. local backend instructions;
6. local frontend instructions;
7. example API request and response;
8. architecture overview;
9. time-zone strategy;
10. domestic/international connection interpretation;
11. search algorithm and complexity;
12. validation behavior;
13. testing instructions;
14. assumptions;
15. tradeoffs;
16. improvements with more time;
17. AI-assistance disclosure if appropriate.

Be honest. Do not claim production readiness.

Suggested future improvements:

- result limits and pagination;
- airline or price filters;
- airport autocomplete;
- persisted schedule storage;
- incremental schedule updates;
- precomputed reachability;
- caching;
- observability;
- contract testing;
- accessibility audit;
- end-to-end tests;
- deployment pipeline.

---

## 17. Commit Strategy

Do not squash the repository into one commit.

Recommended commit progression:

1. `chore: initialize backend and frontend projects`
2. `feat: load and validate flight schedule dataset`
3. `feat: add timezone-aware flight domain model`
4. `feat: implement bounded itinerary search`
5. `test: cover connection rules and timezone edge cases`
6. `feat: expose itinerary search API`
7. `feat: build flight search user interface`
8. `test: add frontend state and validation coverage`
9. `chore: add docker compose setup`
10. `docs: document architecture and tradeoffs`
11. `fix: address final integration and review findings`

Each commit should compile or intentionally document why it is transitional.

Never fabricate commit history after the fact. Commit naturally as work progresses.

---

## 18. Scope Control

Required before optional.

Do not add the following before the core assignment is complete:

- authentication;
- user accounts;
- booking or payment flows;
- a database;
- Kafka;
- Redis;
- Kubernetes;
- microservices beyond the requested backend and frontend;
- GraphQL in addition to REST;
- complex design systems;
- speculative cloud infrastructure.

Thoughtful optional polish is acceptable only after correctness, tests, Docker, and README are complete.

Good optional polish:

- airport datalist/autocomplete from the dataset;
- stop-count labels;
- duration formatting;
- accessible form labels;
- responsive result cards;
- one small end-to-end test;
- request correlation ID;
- basic structured logging.

---

## 19. Code Quality Rules

### Java

- Use constructor injection.
- Prefer records for immutable DTOs and value carriers.
- Use explicit domain names.
- Avoid deeply nested conditionals.
- Keep connection policy separate from traversal.
- Use `BigDecimal` for prices.
- Use `Instant` for ordering and elapsed-time calculations.
- Avoid static mutable state.
- Avoid Lombok unless explicitly approved.
- Avoid catching broad `Exception` without rethrowing or mapping.
- Keep controllers thin.
- Keep methods small enough to explain clearly in an interview.

### TypeScript and React

- Enable strict TypeScript.
- Avoid `any`.
- Keep API types explicit.
- Separate API access from UI components.
- Use semantic HTML.
- Include accessible labels and error associations.
- Avoid unnecessary global state.
- Do not add a UI library unless it materially improves the result.

### General

- No dead code.
- No commented-out implementation.
- No fake data in production paths.
- No unexplained magic numbers.
- Centralize 45 minutes, 90 minutes, six hours, and maximum segment count.
- Produce deterministic output.
- Keep formatting and linting consistent.

---

## 20. Claude Operating Rules

When working in this repository:

1. Never implement before presenting the requested design and receiving approval.
2. Never assume raw local timestamps are directly comparable.
3. Never use `LocalDateTime` to calculate elapsed flight or layover duration.
4. Never use floating-point arithmetic for prices.
5. Never allow more than three segments.
6. Never relax connection rules merely to make a test pass.
7. Never silently skip malformed dataset records.
8. Never add major dependencies without explaining why.
9. Never rewrite unrelated files.
10. Never mark work complete without running relevant tests.
11. Never claim a test passed unless it was actually executed.
12. Never invent dataset facts that have not been inspected.
13. Never guess expected route counts.
14. Never hide known limitations from the README.
15. Never produce one giant implementation commit.

At the end of each milestone, report:

```text
Implemented:
Files changed:
Tests executed:
Results:
Assumptions:
Remaining work:
Risks:
```

---

## 21. Definition of Done

The project is complete only when:

- the backend loads the real dataset on startup;
- the API validates input;
- direct, one-stop, and two-stop searches work;
- all connection rules are enforced;
- time-zone calculations use airport `ZoneId` values;
- date-line crossing is tested;
- results are sorted by total elapsed duration;
- the frontend covers loading, empty, success, validation, and error states;
- `docker-compose up --build` starts the application;
- backend tests pass;
- frontend tests pass;
- the six supplied test cases are addressed;
- the README documents architecture, assumptions, tradeoffs, and improvements;
- the commit history reflects incremental development;
- no known correctness issue is hidden.
