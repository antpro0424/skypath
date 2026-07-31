import { FlightSearch } from "@/components/FlightSearch";

export default function HomePage() {
  return (
    <main>
      <header>
        <h1>SkyPath</h1>
        <p>
          Search direct, one-stop and two-stop itineraries. Times are shown in each
          airport&rsquo;s local zone.
        </p>
      </header>

      <FlightSearch />
    </main>
  );
}
