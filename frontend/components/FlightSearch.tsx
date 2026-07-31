"use client";

import { useEffect, useRef, useState } from "react";
import { ApiError, fetchAirports, searchItineraries } from "@/lib/api";
import type { SearchState } from "@/lib/searchState";
import type { Airport, SearchQuery } from "@/lib/types";
import { SearchForm } from "./SearchForm";
import { SearchResults } from "./SearchResults";
import styles from "./FlightSearch.module.css";

/**
 * Owns the search state and the only network calls on the page.
 *
 * A `useState` union plus an AbortController is enough here. A data-fetching library would
 * add a dependency for a single endpoint, and the loading and error states are ones the
 * interface has to show deliberately rather than manage away.
 */
export function FlightSearch() {
  const [state, setState] = useState<SearchState>({ status: "idle" });
  const [airports, setAirports] = useState<Airport[]>([]);
  const inFlight = useRef<AbortController | null>(null);

  // Airport suggestions are a convenience. If the call fails the form still works, so the
  // failure is swallowed rather than surfaced as a search error.
  useEffect(() => {
    const controller = new AbortController();

    fetchAirports(controller.signal)
      .then(setAirports)
      .catch(() => setAirports([]));

    return () => controller.abort();
  }, []);

  useEffect(() => () => inFlight.current?.abort(), []);

  async function runSearch(query: SearchQuery) {
    // Cancel any request still running, so a slow first search cannot overwrite the
    // results of a faster second one.
    inFlight.current?.abort();
    const controller = new AbortController();
    inFlight.current = controller;

    setState({ status: "loading", query });

    try {
      const response = await searchItineraries(query, controller.signal);
      setState({ status: "success", query, itineraries: response.itineraries });
    } catch (cause) {
      if (controller.signal.aborted) return;

      setState({
        status: "error",
        query,
        message:
          cause instanceof ApiError
            ? cause.message
            : "Something went wrong while searching. Please try again.",
      });
    }
  }

  return (
    <div className={styles.layout}>
      <SearchForm
        airports={airports}
        isSearching={state.status === "loading"}
        onSearch={runSearch}
      />
      <SearchResults state={state} />
    </div>
  );
}
