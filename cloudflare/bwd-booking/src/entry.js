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
  return s.replace(/[^A-Za-z0-9]/g, "").toLowerCase();
}

function validServiceAccount(v) {
  return !!(v && typeof v === "object" && v.client_email && v.private_key && v.project_id);
}

function findServiceAccount(v, depth = 0) {
  if (depth > 3 || v == null) return null;
  if (validServiceAccount(v)) return v;
  if (typeof v === "string") {
    try { return findServiceAccount(JSON.parse(v), depth + 1); } catch (_) { return null; }
  }
  if (typeof v === "object") {
    for (const child of Object.values(v)) {
      const found = findServiceAccount(child, depth + 1);
      if (found) return found;
    }
  }
  return null;
}

function parseLooseServiceAccount(raw) {
  const keyStart = raw.indexOf("-----BEGIN PRIVATE KEY-----");
  const keyMarker = "-----END PRIVATE KEY-----";
  const keyEnd = keyStart >= 0 ? raw.indexOf(keyMarker, keyStart) : -1;
  if (keyStart < 0 || keyEnd < 0) return null;

  let privateKey = raw.slice(keyStart, keyEnd + keyMarker.length).replace(/\\n/g, "\n").trim();
  if (!privateKey.endsWith("\n")) privateKey += "\n";

  let clientEmail = "";
  const emailMatch = raw.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]*iam\.gserviceaccount\.com/i);
  if (emailMatch) clientEmail = emailMatch[0];

  let projectId = "";
  const projectMatch = raw.match(/["']?project_id["']?\s*[:=]\s*["']([^"'\r\n]+)["']/i);
  if (projectMatch) projectId = projectMatch[1].trim();
  if (!projectId && clientEmail) {
    const domainMatch = clientEmail.match(/@([A-Za-z0-9._-]+)\.iam\.gserviceaccount\.com$/i);
    if (domainMatch) projectId = domainMatch[1];
  }

  if (!clientEmail || !projectId) return null;
  return { type: "service_account", project_id: projectId, private_key: privateKey, client_email: clientEmail };
}

function parseServiceAccount(value) {
  let raw = String(value == null ? "" : value).trim();
  if (!raw) return null;

  raw = raw.replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/i, "").trim();

  const attempts = [raw];
  const start = raw.indexOf("{");
  const end = raw.lastIndexOf("}");
  if (start >= 0 && end > start) attempts.push(raw.slice(start, end + 1));

  if (!raw.includes("{") && raw.length > 200 && /^[A-Za-z0-9+/=\s]+$/.test(raw)) {
    try {
      const bytes = Uint8Array.from(atob(raw.replace(/\s+/g, "")), c => c.charCodeAt(0));
      attempts.push(new TextDecoder().decode(bytes));
    } catch (_) {}
  }

  for (const candidate of attempts) {
    try {
      const found = findServiceAccount(JSON.parse(candidate));
      if (found) return found;
    } catch (_) {}

    const loose = parseLooseServiceAccount(candidate);
    if (loose) return loose;
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
