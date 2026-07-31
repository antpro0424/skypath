import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";

/**
 * Run the suite in a zone that is neither UTC nor any airport in the dataset.
 *
 * Formatting a flight time with the machine's own zone is the classic bug here, and it
 * hides perfectly when the tests run in UTC. Pinning the environment to Sydney means any
 * component that forgets to format in the airport's zone produces a visibly wrong time.
 */
process.env.TZ = "Australia/Sydney";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    include: ["tests/**/*.test.{ts,tsx}"],
  },
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "."),
    },
  },
});
