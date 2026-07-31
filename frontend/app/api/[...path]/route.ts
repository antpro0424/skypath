import { NextResponse, type NextRequest } from "next/server";

/**
 * Server-side proxy from the frontend origin to the backend.
 *
 * The browser only ever calls the frontend, so the backend hostname never reaches client
 * code and no CORS configuration is needed.
 *
 * This is a route handler rather than a `next.config` rewrite for one concrete reason:
 * rewrite destinations are resolved during `next build` and written into
 * `routes-manifest.json`, so an environment variable read there is frozen at image build
 * time. A container started with a different backend address would silently keep using the
 * baked-in one. Reading the variable here happens per request, which is what makes the same
 * image runnable against any backend.
 */

const DEFAULT_BACKEND_URL = "http://localhost:8080";

export const dynamic = "force-dynamic";

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const { path } = await context.params;
  const backendUrl = process.env.BACKEND_INTERNAL_URL ?? DEFAULT_BACKEND_URL;
  const target = `${backendUrl}/api/${path.join("/")}${request.nextUrl.search}`;

  try {
    const response = await fetch(target, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });

    // Status and body are passed through untouched so the backend's ProblemDetail
    // responses reach the client exactly as written.
    return new NextResponse(await response.text(), {
      status: response.status,
      headers: {
        "Content-Type": response.headers.get("content-type") ?? "application/json",
      },
    });
  } catch {
    return NextResponse.json(
      {
        status: 502,
        title: "Backend unavailable",
        detail: "The search service could not be reached. It may still be starting up.",
        code: "BACKEND_UNAVAILABLE",
      },
      { status: 502, headers: { "Content-Type": "application/problem+json" } },
    );
  }
}
