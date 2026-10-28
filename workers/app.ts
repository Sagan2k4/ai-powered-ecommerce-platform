import { createRequestHandler } from "react-router";

const requestHandler = createRequestHandler(
  () => import("virtual:react-router/server-build"),
  import.meta.env.MODE,
);

/**
 * Cloudflare edge in front of the Spring Boot API: browser calls go to same-origin `/api/*` and are forwarded
 * here, so there is no CORS round-trip and the backend origin stays hidden behind Cloudflare.
 */
async function proxyToApi(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  const target = new URL(url.pathname + url.search, env.API_BASE_URL);

  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.set("X-Forwarded-Host", url.host);
  headers.set("X-Forwarded-Proto", url.protocol.replace(":", ""));
  const clientIp = request.headers.get("CF-Connecting-IP");
  if (clientIp) headers.set("X-Forwarded-For", clientIp);

  const hasBody = !["GET", "HEAD"].includes(request.method);
  try {
    const upstream = await fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      redirect: "manual",
    });
    return withSecurityHeaders(upstream);
  } catch {
    return Response.json(
      { status: 502, title: "Bad Gateway", detail: "The store API is unreachable. Is the backend running?" },
      { status: 502, headers: { "Content-Type": "application/problem+json" } },
    );
  }
}

function withSecurityHeaders(response: Response): Response {
  const res = new Response(response.body, response);
  res.headers.set("X-Content-Type-Options", "nosniff");
  res.headers.set("X-Frame-Options", "DENY");
  res.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  res.headers.set("Permissions-Policy", "camera=(), microphone=(self), geolocation=()");
  res.headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  return res;
}

export default {
  async fetch(request, env) {
    if (new URL(request.url).pathname.startsWith("/api/")) {
      return proxyToApi(request, env);
    }
    return withSecurityHeaders(await requestHandler(request));
  },
} satisfies ExportedHandler<Env>;
