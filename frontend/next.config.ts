import type { NextConfig } from "next";

/**
 * The browser only ever talks to the frontend origin. Requests to /api/* are
 * forwarded server-side to the backend container, so the Docker service hostname
 * never reaches client code and no CORS configuration is required.
 */
const backendUrl = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Produces a self-contained server bundle, keeping the runtime image small.
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
