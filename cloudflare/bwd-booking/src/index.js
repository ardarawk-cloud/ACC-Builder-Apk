function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff"
    }
  });
}

async function proxyApi(request, env) {
  const origin = String(env.BWD_API_ORIGIN || "").trim().replace(/\/+$/, "");
  if (!origin || !origin.startsWith("https://")) {
    return json({ ok: false, error: "booking_backend_not_configured" }, 503);
  }

  const incoming = new URL(request.url);
  const target = new URL(origin + incoming.pathname);
  target.search = incoming.search;

  const headers = new Headers(request.headers);
  headers.delete("host");
  headers.delete("cf-connecting-ip");
  headers.delete("cf-ipcountry");
  headers.delete("cf-ray");
  headers.delete("cf-visitor");
  headers.set("x-bwd-web-proxy", "cloudflare-worker");

  const init = {
    method: request.method,
    headers,
    redirect: "manual"
  };
  if (request.method !== "GET" && request.method !== "HEAD") {
    init.body = request.body;
  }

  try {
    const upstream = await fetch(target.toString(), init);
    const outHeaders = new Headers(upstream.headers);
    outHeaders.set("cache-control", "no-store");
    outHeaders.set("x-content-type-options", "nosniff");
    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: outHeaders
    });
  } catch (_err) {
    return json({ ok: false, error: "booking_backend_unreachable" }, 502);
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/v1" || url.pathname.startsWith("/v1/")) {
      return proxyApi(request, env);
    }
    return env.ASSETS.fetch(request);
  }
};
