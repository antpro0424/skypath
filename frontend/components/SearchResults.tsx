import type { SearchState } from "@/lib/searchState";
import { ItineraryCard } from "./ItineraryCard";
import styles from "./SearchResults.module.css";

/**
 * Renders whichever state the page is in. Every branch of the union is handled, so a new
 * state cannot be added without the compiler pointing here.
 */
export function SearchResults({ state }: { state: SearchState }) {
  switch (state.status) {
    case "idle":
      return (
        <p className={styles.idle}>
          Enter a departure and arrival airport to see available itineraries.
        </p>
      );

    case "loading":
      return <LoadingSkeleton />;

    case "error":
      return (
        <div className={styles.error} role="alert">
          <p className={styles.errorTitle}>Search failed</p>
          <p className={styles.errorDetail}>{state.message}</p>
        </div>
      );

    case "success":
      return state.itineraries.length === 0 ? (
        <EmptyState origin={state.query.origin} destination={state.query.destination} />
      ) : (
        <section aria-live="polite">
          <p className={styles.count}>
            {state.itineraries.length}{" "}
            {state.itineraries.length === 1 ? "itinerary" : "itineraries"} from{" "}
            {state.query.origin} to {state.query.destination}, shortest first
          </p>
          <ul className={styles.list}>
            {state.itineraries.map((itinerary) => (
              <li key={itinerary.segments.map((s) => s.flightNumber).join("-")}>
                <ItineraryCard itinerary={itinerary} />
              </li>
            ))}
          </ul>
        </section>
      );
  }
}

function LoadingSkeleton() {
  return (
    <div className={styles.loading} role="status" aria-live="polite">
      <p className={styles.loadingLabel}>Searching for itineraries…</p>
      <div className={styles.skeletons} aria-hidden="true">
        {[0, 1, 2].map((index) => (
          <div key={index} className={styles.skeleton} />
        ))}
      </div>
    </div>
  );
}

function EmptyState({ origin, destination }: { origin: string; destination: string }) {
  return (
    <div className={styles.empty}>
      <p className={styles.emptyTitle}>
        No itineraries from {origin} to {destination} on this date
      </p>
      <p className={styles.emptyDetail}>
        Every route is capped at two stops, and connections must allow at least 45 minutes
        domestically or 90 minutes internationally, and no more than six hours. Try another
        date or a different pair of airports.
      </p>
    </div>
  );
}
