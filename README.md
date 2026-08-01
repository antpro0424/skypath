# SkyPath — Flight Connection Search Engine

A prototype flight search service for the Spotnana take-home exercise. It loads a static
flight schedule at startup and finds itineraries between two airports on a given date —
direct, one-stop and two-stop — applying connection rules and time-zone arithmetic
precisely, and returning results shortest first.

**Stack:** Java 17 · Spring Boot 3.3 · Maven — Next.js 15 · React 19 · TypeScript —
Docker Compose

---

## Quick start

```bash
git clone git@github.com:antpro0424/skypath.git
cd skypath
docker compose up --build
```

Then open <http://localhost:3000>.

The API is also published on <http://localhost:8080> so it can be exercised directly:

```bash
curl "http://localhost:8080/api/v1/itineraries/search?origin=JFK&destination=LAX&date=2024-03-15"
```

**Prerequisites:** Docker with Compose v2. Nothing else — Java, Maven and Node are only
needed to work on the code, not to run it. Ports 3000 and 8080 must be free, and the first
build needs network access to pull base images and dependencies.

> The first build takes a few minutes while Maven and npm dependencies download. Later
> builds reuse Docker's layer cache and take seconds. `docker-compose up --build` works too
> if you have the v1-style command installed.

---

## Repository layout

```text
skypath/
├── backend/            Java 17 · Spring Boot · Maven
│   ├── Dockerfile      built from the repository root, see below
│   └── src/
├── frontend/           Next.js · React · TypeScript
│   ├── Dockerfile
│   └── app/, components/, lib/, tests/
├── docker-compose.yml
├── flights.json        the schedule — single source of truth
└── README.md
```

---

## The interface

One page. A search form (origin, destination, date), then results below it.

Airport inputs upper-case as you type and offer suggestions drawn from the dataset. The
date defaults to **2024-03-15** rather than today, because the sample schedule only covers
15–16 March 2024 and defaulting to today would open the page in a state that can never
return a result.

Each itinerary is a card showing total duration, total price, and stop count, then every
segment with its flight number, airline, aircraft, and departure and arrival times **in the
local time of each airport**. Connections appear between segments, naming the airport, the
wait, and the rule that admitted it — for example *"3h layover in DEN · Domestic connection,
minimum 45m"*.

An arrival carries a `+1` or `+2` marker when it lands on a later local calendar date, so an
overnight flight cannot be misread as a short hop. `SFO 22:00 Fri 15 Mar → NRT 02:00 Sun 17
Mar` is a twelve-hour flight, not a four-hour one.

The page handles six states explicitly: idle, invalid input, loading, results, no results,
and API error.

---

## Running locally without Docker

Requires JDK 17+ and Node 20+ (an `.nvmrc` pins the Node version).

**Backend** — starts on port 8080:

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend** — starts on port 3000 and proxies `/api/*` to `http://localhost:8080`:

```bash
cd frontend
nvm use && npm install && npm run dev
```

---

## API

### `GET /api/v1/itineraries/search`

| Parameter | Format | Meaning |
|---|---|---|
| `origin` | 3 letters | Departure airport. Trimmed and upper-cased. |
| `destination` | 3 letters | Arrival airport. |
| `date` | `YYYY-MM-DD` | Local calendar date of the **first** segment's departure, read at the origin airport. |

Later segments may depart on the following calendar day, provided the connection rules hold.

```bash
curl "http://localhost:8080/api/v1/itineraries/search?origin=JFK&destination=LAX&date=2024-03-15"
```

```json
{
  "query": { "origin": "JFK", "destination": "LAX", "date": "2024-03-15" },
  "itineraries": [
    {
      "segments": [
        {
          "flightNumber": "SP103",
          "airline": "SkyPath Airways",
          "origin": "JFK",
          "destination": "LAX",
          "departureTime": "2024-03-15T19:30:00-04:00",
          "arrivalTime": "2024-03-15T22:45:00-07:00",
          "departureTimezone": "America/New_York",
          "arrivalTimezone": "America/Los_Angeles",
          "price": 279.00,
          "aircraft": "A321"
        }
      ],
      "layovers": [],
      "stops": 0,
      "totalDurationMinutes": 375,
      "totalPrice": 279.00
    }
  ]
}
```

A connection appears as:

```json
{
  "airport": "DEN",
  "durationMinutes": 180,
  "minimumRequiredMinutes": 45,
  "connectionType": "DOMESTIC"
}
```

Timestamps are offset-aware, which makes time-zone behaviour visible in the raw payload
rather than something a reader has to take on trust. The IANA zone id travels alongside
because an offset alone is not enough to render a time correctly — see
[Time zones](#time-zones).

Reporting `minimumRequiredMinutes` and `connectionType` per layover means the rule that
admitted a connection can be audited from the response alone.

### Supporting endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/airports` | The 25 airports, code-ordered. Powers autocomplete. |
| `GET /api/v1/health` | Liveness plus what the loader made of the dataset. Used as the container health check. |

---

## Validation

Every client mistake is a **400** with an RFC 7807 `ProblemDetail` carrying a stable
machine-readable `code`. Nothing a client can type produces a 500.

| Condition | Status | `code` |
|---|---|---|
| Valid search, results found | `200` | — |
| Valid search, nothing found | `200`, empty array | — |
| Missing parameter | `400` | `MISSING_PARAMETER` |
| Code not three letters | `400` | `INVALID_AIRPORT_CODE` |
| Well-formed but unknown code | `400` | `UNKNOWN_AIRPORT` |
| Origin equals destination | `400` | `SAME_ORIGIN_AND_DESTINATION` |
| Unparseable date | `400` | `INVALID_DATE` |
| Unexpected failure | `500` | `INTERNAL_ERROR` |

```json
{
  "type": "about:blank",
  "title": "Invalid search request",
  "status": 400,
  "detail": "Unknown airport code 'XXX'.",
  "instance": "/api/v1/itineraries/search",
  "code": "UNKNOWN_AIRPORT",
  "errors": [{ "field": "origin", "message": "Unknown airport code 'XXX'." }]
}
```

**Unknown airports are 400, not 404.** The assignment permits either; splitting input errors
across two status classes forces clients into two code paths for one category of mistake.

**An empty result is not an error.** "No route exists" is a successful answer to a
well-formed question.

Airport existence is checked before the same-airport rule, so `XXX → XXX` reports
`UNKNOWN_AIRPORT` — the more useful of the two answers.

---

## Architecture

```text
┌── browser ────────────────────────────────────────────┐
│  http://localhost:3000 — same origin for page and API │
└───────────────────────┬───────────────────────────────┘
                        │  /api/*
            ┌───────────▼──────────┐        ┌─────────────────────┐
            │ frontend :3000       │───────►│ backend :8080       │
            │ Next.js, proxy route │        │ Spring Boot         │
            └──────────────────────┘        └─────────────────────┘
                        └──── docker network: skypath ────┘
```

The browser only ever talks to the frontend origin. A server-side route handler forwards
`/api/*` to the backend, so the container hostname never reaches client code and there is no
CORS configuration anywhere.

### Backend layers

```text
api ──────► application ──────► domain
                 ▲                 ▲
                 │                 │
          infrastructure ──────────┘
```

| Layer | Owns |
|---|---|
| `domain` | `Airport`, `Flight`, `Itinerary`, `Layover`, `ConnectionPolicy`, `ConnectionRules`. No Spring, no JSON, no HTTP. |
| `application` | The search use case, the `FlightRepository` contract, result ordering. |
| `infrastructure` | Reading `flights.json`, validating it, building the in-memory indexes. |
| `api` | Request validation, DTOs, error mapping. Controllers stay thin. |

`FlightRepository` is the only interface in the codebase, and it earns its place: the search
service can be tested against a two-flight fixture with no JSON parsing and no Spring
context. Domain classes carry no annotations, so they are declared as beans in `config/`
rather than depending on the framework.

### Why no database

The dataset is a static snapshot of roughly 300 flights. It loads once into unmodifiable
maps and is never mutated, so reads need no synchronisation and the service has no external
dependency. A database would add operational surface without answering a single question
faster.

---

## Time zones

The dataset stores naive local times with no offset. Every timestamp is resolved through
**its own airport's** zone:

```text
departureTime + origin.zone       → ZonedDateTime → departureInstant
arrivalTime   + destination.zone  → ZonedDateTime → arrivalInstant
```

All comparisons and durations use `Instant`. Elapsed time is never computed by subtracting
two `LocalDateTime` values, which would be wrong for any flight that changes zone.

**The date-line case works without special handling.** `SP540` departs Sydney at 09:00 and
lands in Los Angeles at 06:00 *the same calendar day*:

| | Local | Resolved |
|---|---|---|
| Depart SYD | `2024-03-15 09:00` (UTC+11) | `2024-03-14T22:00Z` |
| Arrive LAX | `2024-03-15 06:00` (UTC−7) | `2024-03-15T13:00Z` |

Fifteen hours, positive, from correct zone resolution alone. No rollover heuristic exists in
the code, and none is needed: **every one of the 302 loaded flights has a strictly positive
duration** once resolved, which the loader asserts at startup.

### DST

`ZonedDateTime` resolution is used as Java defines it: a spring-forward gap shifts forward by
the gap length, and a fall-back overlap takes the earlier offset. No flight in this dataset
crosses a DST transition — US DST began 10 March 2024, the EU switched on 31 March, Australia
on 7 April — so this is documentation for future data rather than a live concern.

### On the frontend

`new Date(iso).toLocaleTimeString()` renders in the **browser's** zone, so a reviewer in
London would see a JFK departure shown in GMT. Every time is instead formatted with an
explicit `timeZone` taken from the response. That is why the API carries IANA zone ids
beside the offsets.

The frontend test suite runs with `TZ=Australia/Sydney` on purpose: in that zone a
regression renders `23:30` instead of `08:30`, whereas running the suite in UTC would hide
the bug entirely.

---

## Connection rules

An itinerary is at most three flights. For consecutive segments `a` (arriving) and `d`
(departing):

```text
valid(a, d) ⟺ a.destination == d.origin                    (no airport changes)
            ∧ layover = d.departureInstant − a.arrivalInstant
            ∧ layover ≥ type(a, d).minimum                  (45m domestic, 90m otherwise)
            ∧ layover ≤ 6h
```

Both bounds are inclusive. Negative and zero layovers need no separate rule, since every
minimum is positive.

### Domestic versus international

A connection is **domestic only when neither flight crosses a border**:

| Arriving leg | Departing leg | Type | Minimum |
|---|---|---|---|
| US → US | US → US | DOMESTIC | 45m |
| GB → US | US → US | INTERNATIONAL | 90m |
| US → US | US → JP | INTERNATIONAL | 90m |
| US → CA | CA → US | INTERNATIONAL | 90m |

The second row is the one that separates a correct implementation from a plausible one.
`LHR → JFK → LAX` has a purely domestic second leg, so a rule that inspects only the
departing flight applies 45 minutes. The assignment says *both* flights must be within the
same country, and the real-world reason is immigration and customs: a passenger arriving from
abroad needs longer to reach a domestic gate.

**This is not hypothetical.** The dataset contains **16 connections that a naive rule would
wrongly accept**, and every one is exactly 60 minutes — comfortably above the domestic
minimum, comfortably below the international one:

```text
LHR->JFK SP402  then  JFK->SFO SP131   layover 60m
SYD->LAX SP540  then  LAX->DFW SP153   layover 60m
DXB->JFK SP620  then  JFK->ATL SP181   layover 60m
YYZ->JFK SP703  then  JFK->ORD SP111   layover 60m        … and 12 more
```

Worth noting: **passing the six supplied test cases does not prove this is right.** Case 2
(`SFO → NRT`) routes through a US hub, so its *departing* leg is the international one — a
naive rule still gets it right. The mixed case only bites when the international leg is the
arriving one. An integration test asserts that no itinerary connects in under 90 minutes
after an international arrival, and asserts that it actually inspected some, so it cannot
pass vacuously.

Because `NRT` and `HND` are both in Tokyo, a country-only comparison would call `NRT → HND`
domestic — but it is an airport change, so the structural precondition excludes it before
classification runs.

---

## Search algorithm

The schedule is a directed temporal graph: airports are nodes, flights are time-constrained
edges. The search is a depth-first traversal bounded at three segments.

1. Start from flights leaving the origin whose **local departure date** matches the request.
2. If the current path has reached the destination, record it and stop extending it.
3. Otherwise, if it already has three segments, stop.
4. Expand only into flights departing the airport just landed at, skipping any airport
   already on the path, and only when `ConnectionPolicy` admits the connection.

The destination check runs **before** the depth bound, so a path arriving on its third
segment is still recorded. Reversing those two statements silently drops every two-stop
itinerary, so it has a dedicated test.

**Cycle prevention tracks airports, not flights.** `JFK → ORD → JFK → LAX` uses three
distinct flights but revisits JFK, and is rejected.

### Complexity

**O(f · b²)** policy evaluations, where `f` is the number of eligible first flights and `b`
the branching factor at a connecting airport. The depth bound of three is what holds the
exponent at two. On this dataset the worst case is about 31 × 36² ≈ 40,000 evaluations —
microseconds.

Outgoing flight lists are sorted by departure instant, so candidate connections form a
contiguous slice found by binary search, bounded below by arrival + 45 minutes and above by
arrival + 6 hours. The lower bound has to use the *shorter* minimum: whether a connection is
domestic depends on the candidate's destination, which is unknown until the candidate is in
hand. The exact rule still runs on everything the window returns.

Results are ordered by total duration, then price, then segment count, then flight numbers.
Only the first is required; the rest make the order total, so identical searches return
identical output.

---

## The dataset

`flights.json` lives at the repository root and is the single source of truth. There is one
committed copy; the Maven build copies it onto the classpath during `process-resources`, so
tests, `spring-boot:run` and the packaged jar all resolve `classpath:flights.json` without a
second tracked file that could drift out of step. That generated copy is build output under
`target/` and is not tracked.

Because a Docker build context cannot reach outside itself, the backend image is built from
the repository root (`context: .`, `dockerfile: backend/Dockerfile`) rather than from
`backend/`. The in-image layout mirrors the repository so the same relative path resolves.

The location is configurable through `skypath.dataset.location`, which accepts any Spring
resource — `file:/path/to/flights.json` to point a container at a mounted schedule, or a
fixture path in a test — so nothing is hard-coded to the classpath.

### It has deliberate defects

`flights.json` contains 303 flight records. Three are malformed, and the handling is
deliberate rather than incidental.

| Record | Defect | Treatment |
|---|---|---|
| `SP995` | `origin: "JKF"` — a typo; no such airport | **Quarantined.** Excluded from the index. |
| `SP996`, `SP998` | `price` is a JSON *string* (`"99"`, `"95.00"`) | **Accepted**, and the coercion is recorded. |

Two outcomes, both visible:

- **Quarantine** — the record cannot be resolved, so it is dropped, with a typed reason.
- **Coercion** — the record is usable but off-schema; it loads, and the deviation is recorded.

A subtlety worth naming: had `RawFlight.price` been declared `BigDecimal`, **Jackson would
have coerced `"289.00"` silently** and the inconsistency would have vanished without trace.
Price is therefore read as a `JsonNode` and branched on explicitly. Detection is the point.

Startup log:

```text
WARN  Loaded 25 airports and 302 flights from class path resource [flights.json]
      with 1 quarantined and 2 coerced
WARN    quarantined SP995: UNKNOWN_AIRPORT — origin 'JKF' is not present in the airports list
WARN    coerced SP996.price: PRICE_STRING_TO_DECIMAL — JSON string "99" read as a decimal
WARN    coerced SP998.price: PRICE_STRING_TO_DECIMAL — JSON string "95.00" read as a decimal
```

The same report is served at `/api/v1/health`, so the data quirks are observable at runtime
rather than only in a log nobody reads.

**Dataset-level defects fail startup instead**: a missing file, unparseable JSON, a duplicate
airport code, an unrecognised time zone, or a dataset with no usable flights. Those indicate
a broken deployment, not a bad row. One typo should not take down a service that can still
answer almost every query.

---

## Testing

```bash
cd backend  && ./mvnw test        # 106 tests
cd frontend && npm test           # 28 tests
cd frontend && npm run typecheck  # tsc over the whole project, tests included
```

`next build` only type-checks files reachable from the app graph, so it does not cover
`tests/`. `npm run typecheck` runs `tsc --noEmit` across everything in `tsconfig.json` and is
the check to run in CI.

**Backend (106).** Unit tests cover local-time-to-`Instant` conversion, same-zone and
cross-zone durations, the date line, every quarantine reason, price precision, deterministic
ordering, index immutability, and both boundaries of every connection rule — 44/45/46
minutes domestic, 89/90 international, 6h00 and 6h01 maximum, plus negative and zero
layovers and the JFK/LGA airport-change case.

Integration tests run against the real dataset and assert **invariants, not counts** —
itineraries start and end where they should, have one to three segments, satisfy every
timing rule, are correctly ordered, and never repeat an airport. Counts would encode today's
dataset into the suite without saying anything about whether the rules are right. The three
exceptions are the loader's own volumes (25 airports, 302 flights, `SP995` quarantined),
which are the specific quirks the loader exists to handle.

**Frontend (28).** All six page states, all three validation rules, accessible error
association, non-JSON error bodies, and zone-correct rendering.

### Verified end to end

Run from a **clean clone** via `docker compose up --build`, through the frontend origin:

| Case | Result |
|---|---|
| `JFK → LAX` | `200` — 27 itineraries (3 direct, 14 one-stop, 10 two-stop), min layover 45m |
| `SFO → NRT` | `200` — 6 itineraries, **min layover 90m** |
| `BOS → SEA` | `200` — 13 itineraries, **no direct flight exists** |
| `JFK → JFK` | `400` `SAME_ORIGIN_AND_DESTINATION` |
| `XXX → LAX` | `400` `UNKNOWN_AIRPORT` |
| `SYD → LAX` | `200` — 2 itineraries, 15h across the date line |

These counts match an independent reference implementation written in Python before any Java
existed — two separately written implementations agreeing is worth more than either passing
its own tests.

---

## Assumptions

1. `date` is the local departure date of the **first** segment at the origin airport; later
   segments may fall on the next day.
2. Unknown airports are `400`, not `404`.
3. `JFK → JFK` is a validation error rather than an empty result.
4. Layover bounds are inclusive at both ends.
5. The 6-hour maximum applies to each layover independently, not cumulatively.
6. Total price is the sum of segment fares; no taxes, fare rules or through-fare discounts.
7. Minimum connection time is a global constant. Real MCT varies by airport and terminal
   pair; the dataset provides no such field.
8. Flights operate only on their stated date. There is no recurrence and no seat inventory.
9. Currency is not stated in the dataset; amounts are displayed as US dollars.
10. Unknown JSON properties are ignored, so an added field does not break startup.
11. A duplicate flight number keeps the first record and quarantines the later one.

## Tradeoffs

| Choice | Gained | Given up |
|---|---|---|
| In-memory index, no database | No infrastructure, instant startup, trivial tests | No persistence; the whole file reloads on change |
| Exhaustive bounded DFS | Simple and obviously correct | Would not scale to a real multi-day, multi-carrier schedule |
| No caching | Nothing to invalidate; deterministic | Recomputes every search — irrelevant at 40k operations, wrong at real scale |
| Offset-aware ISO strings | Time-zone behaviour auditable in raw JSON | Larger payload than epoch millis; clients must format carefully |
| `useState` + `AbortController` | No dependency; all six states explicit | Manual work a data-fetching library would handle |
| CSS Modules, no UI library | Small bundle, nothing to learn to review it | Less visual polish than a component library gives free |
| Backend port published | Reviewers can `curl` the API directly | Not how an internal service would be exposed in production |

## Known limitations

This is a prototype, not a production service.

- No authentication, rate limiting, or request tracing.
- Results are unpaginated. `JFK → LAX` returns 27 itineraries; a real schedule would return
  far more.
- Minimum connection times are global rather than per-airport.
- No structured logging, metrics, or distributed tracing.
- No accessibility audit beyond labelled controls and error association.
- `npm audit` reports findings in the ESLint 8 toolchain and in Next's `postcss`/`sharp`
  dependencies. All are dev or build-time; none ship in the runtime image, which serves only
  the standalone bundle. Clearing the ESLint chain means an ESLint 9 flat-config migration.

## With more time

- Pagination and result limits; filters by price, airline or stop count.
- Per-airport minimum connection times, sourced properly.
- Precomputed reachability or a caching layer, if query volume justified it.
- Persisted schedule storage with incremental updates rather than a full reload.
- Observability: structured logs, request correlation ids, metrics.
- Contract testing between frontend and backend.
- A Playwright end-to-end smoke test.
- A full accessibility audit including keyboard and screen-reader passes.
- CI running both suites and building both images on every push.

---

## AI assistance

This project was built with Claude Code, which the assignment explicitly permits. The design
was agreed before implementation and the work proceeded in reviewed milestones, each with its
own tests and commit.

Three points where reviewing the output mattered more than generating it:

- **A `next.config` rewrite looked correct and was not.** Rewrite destinations are resolved
  during `next build` and frozen into `routes-manifest.json`, so `BACKEND_INTERNAL_URL` would
  have been baked at image build time and the frontend container would have called its own
  `localhost`. Caught by inspecting the build output, and replaced with a proxy route handler
  that reads the variable per request.
- **`eclipse-temurin:17-jre-alpine` is published for amd64 only.** It would have run under
  emulation, or failed, on any arm64 machine. Caught by checking the published manifests
  rather than assuming.
- **A price assertion failed against `279.0` when the wire actually carried `279.00`.**
  JsonPath parses JSON numbers into `Double` before any matcher sees them, so the test was
  checking the parser, not the payload. Split into a structural assertion and a raw-body one.

The commit history reflects the real sequence of work, including the fixes above.
