import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import HomePage from "@/app/page";

/**
 * Smoke test proving the Vitest + React Testing Library harness renders an App
 * Router page. Replaced by real state and validation coverage once the search
 * interface exists.
 */
describe("HomePage", () => {
  it("renders the application heading", () => {
    render(<HomePage />);

    expect(screen.getByRole("heading", { name: "SkyPath" })).toBeInTheDocument();
  });
});
