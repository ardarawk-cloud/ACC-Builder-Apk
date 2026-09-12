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
          admin_enroll_configured: !!String(env.BWD_ADMIN_ENROLL_TOKEN || "").trim()
        }, 503);
      }

      try {
        await env.BWD_DB.prepare("SELECT 1 AS ok").first();
        return json({
          ok: true,
          service: "bwd-cloudflare",
          database: true,
          push_configured: !!String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim(),
          admin_enroll_configured: !!String(env.BWD_ADMIN_ENROLL_TOKEN || "").trim()
        });
      } catch (err) {
        return json({
          ok: false,
          error: "database_query_failed",
          database: false,
          detail: String(err && err.message || err).slice(0, 180),
          push_configured: !!String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim(),
          admin_enroll_configured: !!String(env.BWD_ADMIN_ENROLL_TOKEN || "").trim()
        }, 503);
      }
    }

    return app.fetch(request, env, ctx);
  }
};
