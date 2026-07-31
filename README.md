# SkyPath — Flight Connection Search Engine

A prototype flight connection search engine built for the Spotnana take-home exercise.

Search for itineraries between two airports on a given date, including direct flights,
one-stop connections, and two-stop connections, with time-zone-correct durations and
precise domestic/international layover rules.

> **Status: in progress.** This README is a placeholder and will be completed at the
> final documentation milestone. See `instructions.md` for the assignment and
> `CLAUDE.md` for the working agreement this implementation follows.

## Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot, Maven |
| Frontend | Next.js (App Router), React, TypeScript |
| Infrastructure | Docker, Docker Compose |

## Dataset

The flight schedule lives at `backend/src/main/resources/flights.json` and is loaded
into memory at backend startup. Its location is configurable; the default resolves to
the classpath copy.

## Running

Full instructions land at the documentation milestone. The intended entry point is:

```bash
docker-compose up --build
```

with the UI at <http://localhost:3000> and the API at <http://localhost:8080>.
