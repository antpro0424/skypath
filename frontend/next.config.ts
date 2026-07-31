import type { NextConfig } from "next";

/**
 * Requests to /api/* are proxied to the backend by the route handler in
 * app/api/[...path]/route.ts, not by a rewrite here. Rewrite destinations are baked into
 * the build output, so an environment variable used in one would be fixed at image build
 * time rather than read when the container starts.
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Produces a self-contained server bundle, keeping the runtime image small.
  output: "standalone",
};

export default nextConfig;
