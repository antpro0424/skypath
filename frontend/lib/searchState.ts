import type { Itinerary, SearchQuery } from "./types";

/**
 * The page's state as one discriminated union.
 *
 * Separate `isLoading` / `error` / `data` flags allow states that make no sense — loading
 * and errored at once, or results present while a request is in flight. Modelling the
 * states as alternatives makes those unrepresentable.
 *
 * Note what is absent: invalid input. Field-level validation lives in the form, because it
 * is per-field, happens before any request, and must not replace results already on screen.
 */
export type SearchState =
  | { status: "idle" }
  | { status: "loading"; query: SearchQuery }
  | { status: "success"; query: SearchQuery; itineraries: Itinerary[] }
  | { status: "error"; query: SearchQuery; message: string };
