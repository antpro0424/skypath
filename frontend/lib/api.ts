import type { Airport, SearchQuery, SearchResponse, ProblemDetail } from "./types";

/**
 * Every network call the interface makes lives here, so components deal in data rather
 * than in fetch options and status codes.
 *
 * Requests go to the frontend's own origin. Next rewrites `/api/*` to the backend
 * server-side, which keeps the container hostname out of the browser and means no CORS.
 */

/** A failed request, carrying the backend's stable error code when there is one. */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

const NETWORK_MESSAGE =
  "Could not reach the search service. Check that the backend is running and try again.";

export async function searchItineraries(
  query: SearchQuery,
  signal?: AbortSignal,
): Promise<SearchResponse> {
  const params = new URLSearchParams({
    origin: query.origin,
    destination: query.destination,
    date: query.date,
  });

  return request<SearchResponse>(`/api/v1/itineraries/search?${params}`, signal);
}

/**
 * Powers the airport suggestions. Treated as optional: a failure here degrades
 * autocomplete rather than blocking a search the user could still type by hand.
 */
export async function fetchAirports(signal?: AbortSignal): Promise<Airport[]> {
  return request<Airport[]>("/api/v1/airports", signal);
}

async function request<T>(url: string, signal?: AbortSignal): Promise<T> {
  let response: Response;

  try {
    response = await fetch(url, { signal, headers: { Accept: "application/json" } });
  } catch (cause) {
    // An aborted request is a cancellation, not a failure; let the caller ignore it.
    if (cause instanceof DOMException && cause.name === "AbortError") {
      throw cause;
    }
    throw new ApiError(NETWORK_MESSAGE, 0);
  }

  if (!response.ok) {
    throw await toApiError(response);
  }

  return (await response.json()) as T;
}

/** Prefers the backend's own explanation, falling back to something readable. */
async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | null = null;

  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    problem = null;
  }

  const message =
    problem?.detail ??
    problem?.title ??
    (response.status >= 500
      ? "The search service failed to handle the request. Please try again."
      : "The search could not be completed.");

  return new ApiError(message, response.status, problem?.code);
}
