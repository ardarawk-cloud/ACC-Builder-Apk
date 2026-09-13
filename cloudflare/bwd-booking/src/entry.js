import app from "./index-v2.js";

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

function normalizeAdminCode(value) {
  let s = String(value == null ? "" : value)
    .replace(/[\u200B-\u200D\u2060\uFEFF]/g, "")
    .trim();
  if (s.length >= 2) {
    const first = s[0];
    const last = s[s.length - 1];
    if ((first === '"' && last === '"') || (first === "'" && last === "'") || (first === "`" && last === "`")) {
      s = s.slice(1, -1).trim();
    }
  }
  return s.replace(/\s+/g, "");
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/v1/health") {
      if (!env.BWD_DB) {
        return json({
          ok: false,
          error: "database_not_bound",
          database: false,
          push_configured: !!String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim(),
          admin_enroll_configured: !!normalizeAdminCode(env.BWD_ADMIN_ENROLL_TOKEN)
        }, 503);
      }

      try {
        await env.BWD_DB.prepare("SELECT 1 AS ok").first();
        return json({
          ok: true,
          service: "bwd-cloudflare",
          database: true,
          push_configured: !!String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim(),
          admin_enroll_configured: !!normalizeAdminCode(env.BWD_ADMIN_ENROLL_TOKEN)
        });
      } catch (err) {
        return json({
          ok: false,
          error: "database_query_failed",
          database: false,
          detail: String(err && err.message || err).slice(0, 180),
          push_configured: !!String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim(),
          admin_enroll_configured: !!normalizeAdminCode(env.BWD_ADMIN_ENROLL_TOKEN)
        }, 503);
      }
    }

    if (request.method === "POST" && url.pathname === "/v1/admin/devices") {
      const headers = new Headers(request.headers);
      headers.set("X-BWD-Admin-Enroll", normalizeAdminCode(headers.get("X-BWD-Admin-Enroll")));
      const body = await request.arrayBuffer();
      const normalizedRequest = new Request(request.url, {
        method: request.method,
        headers,
        body
      });
      const normalizedEnv = Object.create(env);
      normalizedEnv.BWD_ADMIN_ENROLL_TOKEN = normalizeAdminCode(env.BWD_ADMIN_ENROLL_TOKEN);
      return app.fetch(normalizedRequest, normalizedEnv, ctx);
    }

    return app.fetch(request, env, ctx);
  }
};
