import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FlightSearch } from "@/components/FlightSearch";
import { directItinerary, oneStopItinerary, searchResponse } from "./fixtures";

/**
 * Every state the page must support, driven through the real form rather than by poking
 * state directly.
 */

type FetchStub = (url: string) => Promise<Response>;

const AIRPORTS = [
  { code: "JFK", name: "John F. Kennedy International", city: "New York", country: "US", timezone: "America/New_York" },
  { code: "LAX", name: "Los Angeles International", city: "Los Angeles", country: "US", timezone: "America/Los_Angeles" },
];

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    json: async () => body,
  } as Response;
}

/** Routes the airports call to a fixed list and the search call to the given handler. */
function stubFetch(onSearch: (url: string) => Promise<Response>) {
  const stub: FetchStub = (url) =>
    url.startsWith("/api/v1/airports") ? Promise.resolve(jsonResponse(AIRPORTS)) : onSearch(url);

  vi.stubGlobal("fetch", vi.fn(stub));
}

async function submitSearch(origin: string, destination: string) {
  const user = userEvent.setup();

  await user.type(screen.getByLabelText("From"), origin);
  await user.type(screen.getByLabelText("To"), destination);
  await user.click(screen.getByRole("button", { name: /search flights/i }));

  return user;
}

beforeEach(() => {
  stubFetch(() => Promise.resolve(jsonResponse(searchResponse([]))));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe("initial state", () => {
  // These await a query so the airport suggestions request settles inside act(), rather
  // than updating state after the test body has finished.
  it("starts idle with a prompt and no results", async () => {
    render(<FlightSearch />);

    expect(await screen.findByText(/enter a departure and arrival airport/i)).toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });

  it("defaults the date to a day the sample schedule covers", async () => {
    render(<FlightSearch />);

    expect(await screen.findByLabelText("Departure date")).toHaveValue("2024-03-15");
  });
});

describe("input validation", () => {
  it("requires both airports and does not call the API", async () => {
    render(<FlightSearch />);
    const user = userEvent.setup();

    await user.click(screen.getByRole("button", { name: /search flights/i }));

    expect(await screen.findByText("Enter a departure airport.")).toBeInTheDocument();
    expect(screen.getByText("Enter an arrival airport.")).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalledWith(
      expect.stringContaining("/itineraries/search"),
      expect.anything(),
    );
  });

  it("rejects an airport code that is not three letters", async () => {
    render(<FlightSearch />);
    await submitSearch("JF", "LAX");

    expect(
      await screen.findByText("Use a three-letter airport code, such as JFK."),
    ).toBeInTheDocument();
  });

  it("rejects identical origin and destination", async () => {
    render(<FlightSearch />);
    await submitSearch("JFK", "JFK");

    expect(
      await screen.findByText("Departure and arrival airports must be different."),
    ).toBeInTheDocument();
  });

  it("associates each message with its field for assistive technology", async () => {
    render(<FlightSearch />);
    await submitSearch("JF", "LAX");

    const origin = screen.getByLabelText("From");

    await waitFor(() => expect(origin).toHaveAttribute("aria-invalid", "true"));
    expect(origin).toHaveAccessibleDescription("Use a three-letter airport code, such as JFK.");
  });

  it("upper-cases codes as they are typed", async () => {
    render(<FlightSearch />);
    const user = userEvent.setup();

    await user.type(screen.getByLabelText("From"), "jfk");

    expect(screen.getByLabelText("From")).toHaveValue("JFK");
  });
});

describe("loading state", () => {
  it("shows progress while the request is in flight", async () => {
    let release: (value: Response) => void = () => {};
    stubFetch(() => new Promise<Response>((resolve) => (release = resolve)));

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(await screen.findByText(/searching for itineraries/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /searching/i })).toBeDisabled();

    release(jsonResponse(searchResponse([directItinerary])));
    await waitFor(() =>
      expect(screen.queryByText(/searching for itineraries/i)).not.toBeInTheDocument(),
    );
  });
});

describe("successful results", () => {
  it("renders an itinerary with times in each airport's own zone", async () => {
    stubFetch(() => Promise.resolve(jsonResponse(searchResponse([directItinerary]))));

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(await screen.findByText("SP101")).toBeInTheDocument();
    expect(screen.getByText("JFK → LAX")).toBeInTheDocument();
    expect(screen.getByText(/6h 15m · Nonstop/)).toBeInTheDocument();
    expect(screen.getByText(/299\.00/)).toBeInTheDocument();
    expect(screen.getByText(/SkyPath Airways · A320/)).toBeInTheDocument();

    // The suite runs in Sydney time, where this instant is 23:30. Seeing 08:30 proves the
    // card formats in the airport's zone rather than the viewer's.
    expect(screen.getByText("08:30")).toBeInTheDocument();
    expect(screen.getByText("11:45")).toBeInTheDocument();
    expect(screen.queryByText("23:30")).not.toBeInTheDocument();
  });

  it("renders layover details including the rule that admitted the connection", async () => {
    stubFetch(() => Promise.resolve(jsonResponse(searchResponse([oneStopItinerary]))));

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(await screen.findByText("SP110")).toBeInTheDocument();
    expect(screen.getByText("SP120")).toBeInTheDocument();
    expect(
      screen.getByText(/layover in ORD · Domestic connection, minimum 45m/),
    ).toBeInTheDocument();
    expect(screen.getByText(/1h 15m/)).toBeInTheDocument();
  });

  it("summarises how many itineraries were found", async () => {
    stubFetch(() =>
      Promise.resolve(jsonResponse(searchResponse([directItinerary, oneStopItinerary]))),
    );

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(await screen.findByText(/2 itineraries from JFK to LAX, shortest first/)).toBeInTheDocument();
  });
});

describe("empty results", () => {
  it("explains that nothing was found rather than showing a blank page", async () => {
    stubFetch(() => Promise.resolve(jsonResponse(searchResponse([]))));

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(
      await screen.findByText(/no itineraries from JFK to LAX on this date/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/at least 45 minutes domestically/i)).toBeInTheDocument();
  });
});

describe("API errors", () => {
  it("shows the backend's explanation for a rejected search", async () => {
    stubFetch(() =>
      Promise.resolve(
        jsonResponse(
          {
            status: 400,
            title: "Invalid search request",
            detail: "Unknown airport code 'ZZZ'.",
            code: "UNKNOWN_AIRPORT",
          },
          400,
        ),
      ),
    );

    render(<FlightSearch />);
    await submitSearch("JFK", "ZZZ");

    expect(await screen.findByRole("alert")).toHaveTextContent("Unknown airport code 'ZZZ'.");
    expect(screen.getByText("Search failed")).toBeInTheDocument();
  });

  it("reports a readable message when the service is unreachable", async () => {
    stubFetch(() => Promise.reject(new TypeError("Failed to fetch")));

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(await screen.findByRole("alert")).toHaveTextContent(/could not reach the search service/i);
  });

  it("does not crash when the error body is not JSON", async () => {
    stubFetch(() =>
      Promise.resolve({
        ok: false,
        status: 500,
        json: async () => {
          throw new SyntaxError("Unexpected token");
        },
      } as Response),
    );

    render(<FlightSearch />);
    await submitSearch("JFK", "LAX");

    expect(await screen.findByRole("alert")).toHaveTextContent(/search service failed/i);
  });
});
