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
    .normalize("NFKC")
    .replace(/[\u200B-\u200D\u2060\uFEFF]/g, "")
    .trim();
  if (s.length >= 2) {
    const first = s[0];
    const last = s[s.length - 1];
    if ((first === '"' && last === '"') || (first === "'" && last === "'") || (first === "`" && last === "`")) {
      s = s.slice(1, -1).trim();
    }
  }
  // Enrollment codes are treated as case-insensitive alphanumeric tokens.
  // This avoids Android keyboard/copy-paste punctuation, spaces, or casing causing false mismatches.
  return s.replace(/[^A-Za-z0-9]/g, "").toLowerCase();
}

function parseServiceAccount(value) {
  let raw = String(value == null ? "" : value).trim();
  if (!raw) return null;

  raw = raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim();

  const attempts = [raw];
  const start = raw.indexOf("{");
  const end = raw.lastIndexOf("}");
  if (start >= 0 && end > start) attempts.push(raw.slice(start, end + 1));

  for (const candidate of attempts) {
    try {
      let v = JSON.parse(candidate);
      if (typeof v === "string") {
        try { v = JSON.parse(v); } catch (_) {}
      }
      if (v && typeof v === "object" && v.client_email && v.private_key && v.project_id) return v;
    } catch (_) {}
  }
  return null;
}

function normalizedEnv(env) {
  const out = Object.create(env);
  const serviceAccount = parseServiceAccount(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON);
  out.BWD_FIREBASE_SERVICE_ACCOUNT_JSON = serviceAccount ? JSON.stringify(serviceAccount) : String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim();
  out.BWD_ADMIN_ENROLL_TOKEN = normalizeAdminCode(env.BWD_ADMIN_ENROLL_TOKEN);
  return out;
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const effectiveEnv = normalizedEnv(env);

    if (request.method === "GET" && url.pathname === "/v1/health") {
      const rawPush = String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim();
      const parsedPush = parseServiceAccount(rawPush);
      const normalizedAdmin = normalizeAdminCode(env.BWD_ADMIN_ENROLL_TOKEN);
      if (!env.BWD_DB) {
        return json({
          ok: false,
          error: "database_not_bound",
          database: false,
          push_secret_present: !!rawPush,
          push_configured: !!parsedPush,
          push_project_id: parsedPush ? String(parsedPush.project_id || "") : "",
          admin_enroll_configured: normalizedAdmin.length >= 12,
          admin_enroll_length: normalizedAdmin.length
        }, 503);
      }

      try {
        await env.BWD_DB.prepare("SELECT 1 AS ok").first();
        return json({
          ok: true,
          service: "bwd-cloudflare",
          database: true,
          push_secret_present: !!rawPush,
          push_configured: !!parsedPush,
          push_project_id: parsedPush ? String(parsedPush.project_id || "") : "",
          admin_enroll_configured: normalizedAdmin.length >= 12,
          admin_enroll_length: normalizedAdmin.length
        });
      } catch (err) {
        return json({
          ok: false,
          error: "database_query_failed",
          database: false,
          detail: String(err && err.message || err).slice(0, 180),
          push_secret_present: !!rawPush,
          push_configured: !!parsedPush,
          push_project_id: parsedPush ? String(parsedPush.project_id || "") : "",
          admin_enroll_configured: normalizedAdmin.length >= 12,
          admin_enroll_length: normalizedAdmin.length
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
      return app.fetch(normalizedRequest, effectiveEnv, ctx);
    }

    return app.fetch(request, effectiveEnv, ctx);
  }
};
