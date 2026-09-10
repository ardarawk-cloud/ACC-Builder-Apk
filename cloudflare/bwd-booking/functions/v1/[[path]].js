const API_ORIGIN = "__BWD_API_ORIGIN__";

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

export async function onRequest(context) {
  const origin = String(API_ORIGIN || "").trim().replace(/\/+$/, "");
  if (!origin || origin.includes("__BWD_API_ORIGIN__") || !origin.startsWith("https://")) {
    return json({ ok: false, error: "booking_backend_not_configured" }, 503);
  }

  const incoming = new URL(context.request.url);
  const raw = context.params.path;
  const path = Array.isArray(raw) ? raw.join("/") : String(raw || "");
  const target = new URL(`${origin}/v1/${path}`);
  target.search = incoming.search;

  const headers = new Headers(context.request.headers);
  headers.delete("host");
  headers.delete("cf-connecting-ip");
  headers.delete("cf-ipcountry");
  headers.delete("cf-ray");
  headers.delete("cf-visitor");
  headers.set("x-bwd-web-proxy", "cloudflare-pages");

  const init = {
    method: context.request.method,
    headers,
    redirect: "manual"
  };
  if (context.request.method !== "GET" && context.request.method !== "HEAD") {
    init.body = context.request.body;
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
