/**
 * The API contract, mirrored explicitly.
 *
 * Times arrive as offset-aware ISO strings such as `2024-03-15T08:30:00-04:00`, and each
 * one is paired with its IANA zone id. Both are needed: the offset fixes the absolute
 * moment, the zone id lets the interface render the clock a traveller would actually read
 * at that airport rather than in the viewer's own zone.
 */

export type ConnectionType = "DOMESTIC" | "INTERNATIONAL";

export interface Segment {
  flightNumber: string;
  airline: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  departureTimezone: string;
  arrivalTimezone: string;
  price: number;
  aircraft: string;
}

export interface Layover {
  airport: string;
  durationMinutes: number;
  minimumRequiredMinutes: number;
  connectionType: ConnectionType;
}

export interface Itinerary {
  segments: Segment[];
  layovers: Layover[];
  stops: number;
  totalDurationMinutes: number;
  totalPrice: number;
}

export interface SearchQuery {
  origin: string;
  destination: string;
  date: string;
}

export interface SearchResponse {
  query: SearchQuery;
  itineraries: Itinerary[];
}

export interface Airport {
  code: string;
  name: string;
  city: string;
  country: string;
  timezone: string;
}

/** RFC 7807 problem detail, with the extra fields this API adds. */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: { field?: string; message: string }[];
}
