import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "SkyPath — Flight Search",
  description:
    "Search direct, one-stop, and two-stop flight itineraries with time-zone-correct durations.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
