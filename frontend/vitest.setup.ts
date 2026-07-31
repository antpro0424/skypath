import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Unmount rendered trees between tests so queries cannot leak across cases.
afterEach(() => {
  cleanup();
});
