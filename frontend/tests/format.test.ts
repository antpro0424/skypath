import { describe, expect, it } from "vitest";
import {
  dayOffset,
  formatDateInZone,
  formatDuration,
  formatPrice,
  formatStops,
  formatTimeInZone,
} from "@/lib/format";

describe("formatDuration", () => {
  it("shows hours and minutes", () => {
    expect(formatDuration(375)).toBe("6h 15m");
  });

  it("omits hours under an hour", () => {
    expect(formatDuration(45)).toBe("45m");
  });

  it("omits minutes on a whole hour", () => {
    expect(formatDuration(360)).toBe("6h");
  });
});

describe("formatStops", () => {
  it("labels each stop count", () => {
    expect(formatStops(0)).toBe("Nonstop");
    expect(formatStops(1)).toBe("1 stop");
    expect(formatStops(2)).toBe("2 stops");
  });
});

describe("formatPrice", () => {
  it("always shows two decimal places", () => {
    expect(formatPrice(279)).toContain("279.00");
    expect(formatPrice(428.5)).toContain("428.50");
  });
});

describe("formatTimeInZone", () => {
  // The suite runs with TZ=Australia/Sydney, so a function that fell back to the
  // environment zone would render these times quite differently.
  it("renders a New York departure in New York time, not the machine's zone", () => {
    expect(formatTimeInZone("2024-03-15T08:30:00-04:00", "America/New_York")).toBe("08:30");
  });

  it("renders a Los Angeles arrival in Los Angeles time", () => {
    expect(formatTimeInZone("2024-03-15T11:45:00-07:00", "America/Los_Angeles")).toBe("11:45");
  });

  it("would show a different clock if the viewer's zone were used", () => {
    // Same instant, formatted in the environment zone: proof the assertions above are
    // actually exercising the timeZone argument.
    const inSydney = new Intl.DateTimeFormat("en-GB", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }).format(new Date("2024-03-15T08:30:00-04:00"));

    expect(inSydney).toBe("23:30");
    expect(formatTimeInZone("2024-03-15T08:30:00-04:00", "America/New_York")).not.toBe(inSydney);
  });

  it("renders the Sydney departure of the date-line flight", () => {
    expect(formatTimeInZone("2024-03-15T09:00:00+11:00", "Australia/Sydney")).toBe("09:00");
  });
});

describe("formatDateInZone", () => {
  it("renders the calendar date at the airport", () => {
    expect(formatDateInZone("2024-03-15T08:30:00-04:00", "America/New_York")).toBe("Fri 15 Mar");
  });
});

describe("dayOffset", () => {
  it("is zero when a flight lands on the same local date", () => {
    const offset = dayOffset(
      "2024-03-15T08:30:00-04:00",
      "America/New_York",
      "2024-03-15T11:45:00-07:00",
      "America/Los_Angeles",
    );

    expect(offset).toBe(0);
  });

  it("is +1 for an overnight flight", () => {
    const offset = dayOffset(
      "2024-03-15T22:00:00-07:00",
      "America/Los_Angeles",
      "2024-03-16T06:30:00-04:00",
      "America/New_York",
    );

    expect(offset).toBe(1);
  });

  it("is zero for the westbound date-line flight despite fifteen hours in the air", () => {
    // SYD 09:00 to LAX 06:00 on the same calendar date: the clock appears to go backwards.
    const offset = dayOffset(
      "2024-03-15T09:00:00+11:00",
      "Australia/Sydney",
      "2024-03-15T06:00:00-07:00",
      "America/Los_Angeles",
    );

    expect(offset).toBe(0);
  });
});
