import type { Itinerary, Layover, Segment } from "@/lib/types";
import {
  dayOffset,
  formatDateInZone,
  formatDuration,
  formatPrice,
  formatStops,
  formatTimeInZone,
} from "@/lib/format";
import styles from "./ItineraryCard.module.css";

export function ItineraryCard({ itinerary }: { itinerary: Itinerary }) {
  const first = itinerary.segments[0];
  const last = itinerary.segments[itinerary.segments.length - 1];

  // `segments` is never empty: the API only returns itineraries of one to three flights.
  if (!first || !last) return null;

  return (
    <article className={styles.card}>
      <header className={styles.header}>
        <div className={styles.route}>
          <span className={styles.endpoints}>
            {first.origin} → {last.destination}
          </span>
          <span className={styles.summary}>
            {formatDuration(itinerary.totalDurationMinutes)} · {formatStops(itinerary.stops)}
          </span>
        </div>
        <p className={styles.price}>{formatPrice(itinerary.totalPrice)}</p>
      </header>

      <ol className={styles.legs}>
        {itinerary.segments.map((segment, index) => (
          <li key={segment.flightNumber} className={styles.leg}>
            <SegmentRow segment={segment} />
            {itinerary.layovers[index] && (
              <LayoverRow layover={itinerary.layovers[index]} />
            )}
          </li>
        ))}
      </ol>
    </article>
  );
}

function SegmentRow({ segment }: { segment: Segment }) {
  const offset = dayOffset(
    segment.departureTime,
    segment.departureTimezone,
    segment.arrivalTime,
    segment.arrivalTimezone,
  );

  return (
    <div className={styles.segment}>
      <p className={styles.flight}>
        <span className={styles.flightNumber}>{segment.flightNumber}</span>
        <span className={styles.meta}>
          {segment.airline} · {segment.aircraft}
        </span>
      </p>

      <div className={styles.times}>
        <Endpoint
          airport={segment.origin}
          isoTimestamp={segment.departureTime}
          timeZone={segment.departureTimezone}
        />
        <span className={styles.arrow} aria-hidden="true">
          →
        </span>
        <Endpoint
          airport={segment.destination}
          isoTimestamp={segment.arrivalTime}
          timeZone={segment.arrivalTimezone}
          dayOffset={offset}
        />
      </div>
    </div>
  );
}

interface EndpointProps {
  airport: string;
  isoTimestamp: string;
  timeZone: string;
  dayOffset?: number;
}

function Endpoint({ airport, isoTimestamp, timeZone, dayOffset: offset }: EndpointProps) {
  return (
    <div className={styles.endpoint}>
      <p className={styles.time}>
        {/* Formatted in the airport's own zone, never the viewer's. */}
        <time dateTime={isoTimestamp}>{formatTimeInZone(isoTimestamp, timeZone)}</time>
        {offset !== undefined && offset !== 0 && (
          <span className={styles.dayOffset}>
            {offset > 0 ? `+${offset}` : offset}
          </span>
        )}
      </p>
      <p className={styles.airport}>{airport}</p>
      <p className={styles.date}>{formatDateInZone(isoTimestamp, timeZone)}</p>
    </div>
  );
}

function LayoverRow({ layover }: { layover: Layover }) {
  const kind = layover.connectionType === "DOMESTIC" ? "Domestic" : "International";

  return (
    <p className={styles.layover}>
      <span className={styles.layoverDuration}>
        {formatDuration(layover.durationMinutes)}
      </span>{" "}
      layover in {layover.airport} · {kind} connection, minimum{" "}
      {formatDuration(layover.minimumRequiredMinutes)}
    </p>
  );
}
