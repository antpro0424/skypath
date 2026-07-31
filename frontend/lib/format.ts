/**
 * Presentation helpers.
 *
 * The time functions are the important ones. `new Date(...).toLocaleTimeString()` renders
 * in the *browser's* zone, so a reviewer in London would see a JFK departure shown in GMT.
 * Every function here formats in an explicitly supplied airport zone instead, which is why
 * the API carries an IANA zone id beside each timestamp.
 */

/** Currency is not stated in the dataset; amounts are presented as US dollars. */
const CURRENCY = "USD";
const MONEY_LOCALE = "en-US";

/**
 * Dates and times use en-GB for its 24-hour clock and unambiguous `15 Mar` ordering.
 * AM/PM and numeric month order are both easy to misread on an itinerary.
 */
const LOCALE = "en-GB";

export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;

  if (hours === 0) return `${minutes}m`;
  if (minutes === 0) return `${hours}h`;
  return `${hours}h ${minutes}m`;
}

export function formatPrice(amount: number): string {
  return new Intl.NumberFormat(MONEY_LOCALE, {
    style: "currency",
    currency: CURRENCY,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/** Clock time at the given airport, for example `08:30`. */
export function formatTimeInZone(isoTimestamp: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone,
  }).format(new Date(isoTimestamp));
}

/** Calendar date at the given airport, for example `Fri 15 Mar`. */
export function formatDateInZone(isoTimestamp: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone,
  }).format(new Date(isoTimestamp));
}

/**
 * Whole calendar days between two moments, each read in its own airport zone.
 *
 * A flight can land on a later local date than it left (a red-eye) or, crossing the date
 * line westbound, on the same one despite taking fifteen hours. Showing `+1` on the arrival
 * time is what stops `19:30 → 22:45` from looking like a three-hour hop when it is not.
 */
export function dayOffset(
  departureIso: string,
  departureZone: string,
  arrivalIso: string,
  arrivalZone: string,
): number {
  const departureDay = calendarDayInZone(departureIso, departureZone);
  const arrivalDay = calendarDayInZone(arrivalIso, arrivalZone);

  const millisecondsPerDay = 24 * 60 * 60 * 1000;
  return Math.round((arrivalDay.getTime() - departureDay.getTime()) / millisecondsPerDay);
}

/** The local calendar date in a zone, as a UTC midnight so days can be subtracted. */
function calendarDayInZone(isoTimestamp: string, timeZone: string): Date {
  const parts = new Intl.DateTimeFormat("en-CA", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    timeZone,
  }).format(new Date(isoTimestamp));

  return new Date(`${parts}T00:00:00Z`);
}

export function formatStops(stops: number): string {
  if (stops === 0) return "Nonstop";
  if (stops === 1) return "1 stop";
  return `${stops} stops`;
}
