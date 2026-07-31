import type { Itinerary, SearchResponse } from "@/lib/types";

/** A JFK to LAX direct, matching a real record in the shipped dataset. */
export const directItinerary: Itinerary = {
  segments: [
    {
      flightNumber: "SP101",
      airline: "SkyPath Airways",
      origin: "JFK",
      destination: "LAX",
      departureTime: "2024-03-15T08:30:00-04:00",
      arrivalTime: "2024-03-15T11:45:00-07:00",
      departureTimezone: "America/New_York",
      arrivalTimezone: "America/Los_Angeles",
      price: 299.0,
      aircraft: "A320",
    },
  ],
  layovers: [],
  stops: 0,
  totalDurationMinutes: 375,
  totalPrice: 299.0,
};

/** A one-stop with a domestic connection through Chicago. */
export const oneStopItinerary: Itinerary = {
  segments: [
    {
      flightNumber: "SP110",
      airline: "SkyPath Airways",
      origin: "JFK",
      destination: "ORD",
      departureTime: "2024-03-15T07:00:00-04:00",
      arrivalTime: "2024-03-15T08:45:00-05:00",
      departureTimezone: "America/New_York",
      arrivalTimezone: "America/Chicago",
      price: 149.0,
      aircraft: "B737",
    },
    {
      flightNumber: "SP120",
      airline: "SkyPath Airways",
      origin: "ORD",
      destination: "LAX",
      departureTime: "2024-03-15T10:00:00-05:00",
      arrivalTime: "2024-03-15T12:15:00-07:00",
      departureTimezone: "America/Chicago",
      arrivalTimezone: "America/Los_Angeles",
      price: 179.0,
      aircraft: "A320",
    },
  ],
  layovers: [
    {
      airport: "ORD",
      durationMinutes: 75,
      minimumRequiredMinutes: 45,
      connectionType: "DOMESTIC",
    },
  ],
  stops: 1,
  totalDurationMinutes: 495,
  totalPrice: 328.0,
};

export function searchResponse(itineraries: Itinerary[]): SearchResponse {
  return {
    query: { origin: "JFK", destination: "LAX", date: "2024-03-15" },
    itineraries,
  };
}
