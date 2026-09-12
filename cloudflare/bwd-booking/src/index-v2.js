const MAX_BODY_BYTES = 120000;
let schemaReady = false;
let cachedAccessToken = null;
let cachedAccessTokenUntil = 0;

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "referrer-policy": "no-referrer"
    }
  });
}

function text(value, max = 500) {
  return String(value == null ? "" : value).trim().slice(0, max);
}

function bookingIdOk(value) {
  return /^[A-Za-z0-9_-]{8,80}$/.test(value);
}

function dateOk(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value);
}

async function sha256Hex(value) {
  const bytes = new TextEncoder().encode(String(value));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, "0")).join("");
}

async function secureEqual(a, b) {
  const [ha, hb] = await Promise.all([sha256Hex(a), sha256Hex(b)]);
  let diff = ha.length ^ hb.length;
  for (let i = 0; i < Math.max(ha.length, hb.length); i++) {
    diff |= (ha.charCodeAt(i) || 0) ^ (hb.charCodeAt(i) || 0);
  }
  return diff === 0;
}

async function parseJson(request) {
  const raw = await request.text();
  if (raw.length > MAX_BODY_BYTES) throw new Error("payload_too_large");
  try { return JSON.parse(raw || "{}"); }
  catch (_) { throw new Error("invalid_json"); }
}

async function ensureSchema(env) {
  if (schemaReady) return;
  if (!env.BWD_DB) throw new Error("database_not_bound");

  const statements = [
    `CREATE TABLE IF NOT EXISTS bookings (
      booking_id TEXT PRIMARY KEY,
      client_token_hash TEXT NOT NULL,
      bride TEXT NOT NULL,
      groom TEXT NOT NULL,
      email TEXT NOT NULL,
      whatsapp TEXT NOT NULL,
      wedding_date TEXT NOT NULL,
      venue_name TEXT NOT NULL DEFAULT '',
      venue_location TEXT NOT NULL DEFAULT '',
      planner TEXT NOT NULL DEFAULT '',
      guests INTEGER NOT NULL DEFAULT 0,
      package_name TEXT NOT NULL DEFAULT '',
      sections TEXT NOT NULL DEFAULT '',
      start_time TEXT NOT NULL DEFAULT '',
      finish_time TEXT NOT NULL DEFAULT '',
      music_pref TEXT NOT NULL DEFAULT '',
      favorite_songs TEXT NOT NULL DEFAULT '',
      must_play TEXT NOT NULL DEFAULT '',
      do_not_play TEXT NOT NULL DEFAULT '',
      special_requests TEXT NOT NULL DEFAULT '',
      source TEXT NOT NULL DEFAULT 'web',
      status TEXT NOT NULL DEFAULT 'REQUEST RECEIVED',
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL
    )`,
    `CREATE INDEX IF NOT EXISTS idx_bookings_created_at ON bookings(created_at DESC)`,
    `CREATE TABLE IF NOT EXISTS admin_devices (
      token_hash TEXT PRIMARY KEY,
      fcm_token TEXT NOT NULL,
      platform TEXT NOT NULL DEFAULT 'android',
      app_version TEXT NOT NULL DEFAULT '',
      enabled INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL,
      updated_at TEXT NOT NULL,
      last_seen_at TEXT NOT NULL
    )`
  ];

  for (const sql of statements) {
    await env.BWD_DB.prepare(sql).run();
  }
  schemaReady = true;
}

function bookingView(row) {
  if (!row) return null;
  const keys = [
    "booking_id","bride","groom","email","whatsapp","wedding_date","venue_name",
    "venue_location","planner","guests","package_name","sections","start_time","finish_time",
    "music_pref","favorite_songs","must_play","do_not_play","special_requests","source",
    "status","created_at","updated_at"
  ];
  const out = {};
  for (const k of keys) out[k] = row[k] == null ? "" : row[k];
  out.guests = Number(row.guests || 0);
  return out;
}

function base64UrlBytes(bytes) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlText(value) {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function pemToArrayBuffer(pem) {
  const clean = String(pem)
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const binary = atob(clean);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return bytes.buffer;
}

function serviceAccount(env) {
  const raw = String(env.BWD_FIREBASE_SERVICE_ACCOUNT_JSON || "").trim();
  if (!raw) return null;
  try {
    const v = JSON.parse(raw);
    if (!v.client_email || !v.private_key || !v.project_id) return null;
    return v;
  } catch (_) {
    return null;
  }
}

async function googleAccessToken(env) {
  const nowMs = Date.now();
  if (cachedAccessToken && nowMs < cachedAccessTokenUntil - 60000) return cachedAccessToken;
  const sa = serviceAccount(env);
  if (!sa) throw new Error("push_not_configured");
  const now = Math.floor(nowMs / 1000);
  const header = base64UrlText(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = base64UrlText(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600
  }));
  const unsigned = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(sa.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const sig = await crypto.subtle.sign(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    new TextEncoder().encode(unsigned)
  );
  const assertion = `${unsigned}.${base64UrlBytes(new Uint8Array(sig))}`;
  const body = new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion
  });
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body
  });
  const out = await res.json().catch(() => ({}));
  if (!res.ok || !out.access_token) throw new Error("google_oauth_failed");
  cachedAccessToken = out.access_token;
  cachedAccessTokenUntil = nowMs + Number(out.expires_in || 3600) * 1000;
  return cachedAccessToken;
}

async function sendFcm(env, token, title, body, data = {}) {
  const sa = serviceAccount(env);
  if (!sa) throw new Error("push_not_configured");
  const accessToken = await googleAccessToken(env);
  const payloadData = {};
  for (const [k, v] of Object.entries(data)) payloadData[k] = String(v == null ? "" : v);
  payloadData.title = title;
  payloadData.body = body;
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(sa.project_id)}/messages:send`, {
    method: "POST",
    headers: {
      "authorization": `Bearer ${accessToken}`,
      "content-type": "application/json; charset=utf-8"
    },
    body: JSON.stringify({
      message: {
        token,
        notification: { title, body },
        data: payloadData,
        android: { priority: "high" }
      }
    })
  });
  if (!res.ok) {
    const detail = await res.text().catch(() => "");
    throw new Error(`fcm_send_failed:${res.status}:${detail.slice(0, 180)}`);
  }
}

async function notifyAdmins(env, title, body, data) {
  try {
    await ensureSchema(env);
    if (!serviceAccount(env)) return;
    const rows = await env.BWD_DB.prepare(
      "SELECT fcm_token, token_hash FROM admin_devices WHERE enabled=1 ORDER BY updated_at DESC LIMIT 12"
    ).all();
    await Promise.all((rows.results || []).map(async d => {
      try { await sendFcm(env, d.fcm_token, title, body, data); }
      catch (err) {
        const msg = String(err && err.message || err);
        if (msg.includes("UNREGISTERED") || msg.includes("registration-token-not-registered")) {
          await env.BWD_DB.prepare("UPDATE admin_devices SET enabled=0, updated_at=? WHERE token_hash=?")
            .bind(new Date().toISOString(), d.token_hash).run();
        }
      }
    }));
  } catch (_) {
    // Booking persistence must not fail because push is unavailable.
  }
}

async function verifyAdminDevice(request, env) {
  const token = text(request.headers.get("X-BWD-Admin-Device"), 4096);
  if (token.length < 20) return false;
  const hash = await sha256Hex(token);
  const row = await env.BWD_DB.prepare(
    "SELECT token_hash FROM admin_devices WHERE token_hash=? AND fcm_token=? AND enabled=1"
  ).bind(hash, token).first();
  if (!row) return false;
  const now = new Date().toISOString();
  await env.BWD_DB.prepare("UPDATE admin_devices SET last_seen_at=?, updated_at=? WHERE token_hash=?")
    .bind(now, now, hash).run();
  return true;
}

async function createBooking(request, env, ctx) {
  let p;
  try { p = await parseJson(request); }
  catch (err) { return json({ ok: false, error: err.message }, err.message === "payload_too_large" ? 413 : 400); }

  const bookingId = text(p.booking_id, 80);
  const clientToken = text(p.client_token, 512);
  const bride = text(p.bride, 120);
  const groom = text(p.groom, 120);
  const email = text(p.email, 180);
  const whatsapp = text(p.whatsapp, 80);
  const weddingDate = text(p.wedding_date, 10);
  if (!bookingIdOk(bookingId) || clientToken.length < 32 || !bride || !groom || !email.includes("@") || whatsapp.replace(/\D/g, "").length < 7 || !dateOk(weddingDate)) {
    return json({ ok: false, error: "invalid_booking" }, 400);
  }

  const now = new Date().toISOString();
  const tokenHash = await sha256Hex(clientToken);
  const values = [
    bookingId, tokenHash, bride, groom, email, whatsapp, weddingDate,
    text(p.venue_name, 180), text(p.venue_location, 240), text(p.planner, 180),
    Math.max(0, Math.min(100000, Number(p.guests || 0) || 0)), text(p.package_name, 180),
    text(p.sections, 500), text(p.start_time, 30), text(p.finish_time, 30), text(p.music_pref, 1200),
    text(p.favorite_songs, 2000), text(p.must_play, 2000), text(p.do_not_play, 2000),
    text(p.special_requests, 4000), text(p.source || "web", 30), "REQUEST RECEIVED", now, now
  ];

  try {
    await env.BWD_DB.prepare(`INSERT INTO bookings (
      booking_id,client_token_hash,bride,groom,email,whatsapp,wedding_date,venue_name,venue_location,
      planner,guests,package_name,sections,start_time,finish_time,music_pref,favorite_songs,must_play,
      do_not_play,special_requests,source,status,created_at,updated_at
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`).bind(...values).run();
  } catch (err) {
    const msg = String(err && err.message || err).toLowerCase();
    if (msg.includes("unique") || msg.includes("constraint")) {
      return json({ ok: false, error: "booking_id_exists" }, 409);
    }
    return json({ ok: false, error: "booking_store_failed" }, 500);
  }

  ctx.waitUntil(notifyAdmins(env, "New Wedding Booking", `${bride} & ${groom} · ${weddingDate}`, {
    type: "new_booking", booking_id: bookingId
  }));
  return json({ ok: true, booking_id: bookingId, status: "REQUEST RECEIVED" }, 201);
}

async function getBooking(request, env, bookingId) {
  if (!bookingIdOk(bookingId)) return json({ ok: false, error: "booking_not_found" }, 404);
  const token = text(request.headers.get("X-BWD-Client-Token"), 512);
  if (token.length < 32) return json({ ok: false, error: "invalid_client_token" }, 401);
  const row = await env.BWD_DB.prepare("SELECT * FROM bookings WHERE booking_id=?").bind(bookingId).first();
  if (!row) return json({ ok: false, error: "booking_not_found" }, 404);
  if (!(await secureEqual(await sha256Hex(token), String(row.client_token_hash || "")))) {
    return json({ ok: false, error: "invalid_client_token" }, 401);
  }
  return json({ ok: true, booking: bookingView(row) });
}

async function enrollAdmin(request, env) {
  const expected = String(env.BWD_ADMIN_ENROLL_TOKEN || "").trim();
  if (expected.length < 12) return json({ ok: false, error: "admin_enrollment_not_configured" }, 503);
  if (!serviceAccount(env)) return json({ ok: false, error: "push_not_configured" }, 503);
  const supplied = text(request.headers.get("X-BWD-Admin-Enroll"), 1024);
  if (!supplied || !(await secureEqual(supplied, expected))) return json({ ok: false, error: "invalid_admin_enrollment" }, 401);

  let p;
  try { p = await parseJson(request); }
  catch (err) { return json({ ok: false, error: err.message }, 400); }
  const token = text(p.token, 4096);
  if (token.length < 50) return json({ ok: false, error: "invalid_fcm_token" }, 400);

  const hash = await sha256Hex(token);
  const now = new Date().toISOString();
  await env.BWD_DB.prepare(`INSERT INTO admin_devices (
    token_hash,fcm_token,platform,app_version,enabled,created_at,updated_at,last_seen_at
  ) VALUES (?,?,?,?,1,?,?,?)
  ON CONFLICT(token_hash) DO UPDATE SET
    fcm_token=excluded.fcm_token,platform=excluded.platform,app_version=excluded.app_version,
    enabled=1,updated_at=excluded.updated_at,last_seen_at=excluded.last_seen_at`)
    .bind(hash, token, text(p.platform || "android", 30), text(p.app_version, 80), now, now, now).run();

  try {
    await sendFcm(env, token, "Bali Wedding DJ", "Cloud booking notifications are enabled on this device.", { type: "owner_enrolled" });
    return json({ ok: true, enrolled: true, notification_sent: true });
  } catch (_) {
    return json({ ok: true, enrolled: true, notification_sent: false });
  }
}

async function adminBookings(request, env) {
  if (!(await verifyAdminDevice(request, env))) return json({ ok: false, error: "owner_device_not_enrolled" }, 401);
  const rows = await env.BWD_DB.prepare("SELECT * FROM bookings ORDER BY created_at DESC LIMIT 100").all();
  return json({ ok: true, bookings: (rows.results || []).map(bookingView) });
}

async function adminStatus(request, env, bookingId) {
  if (!(await verifyAdminDevice(request, env))) return json({ ok: false, error: "owner_device_not_enrolled" }, 401);
  if (!bookingIdOk(bookingId)) return json({ ok: false, error: "booking_not_found" }, 404);

  let p;
  try { p = await parseJson(request); }
  catch (err) { return json({ ok: false, error: err.message }, 400); }
  const status = text(p.status, 80).toUpperCase();
  const allowed = new Set([
    "REQUEST RECEIVED","WAITING FOR DEPOSIT","DEPOSIT RECEIVED","BOOKING CONFIRMED",
    "BALANCE DUE","PAID IN FULL","COMPLETED","CANCELLED"
  ]);
  if (!allowed.has(status)) return json({ ok: false, error: "invalid_status" }, 400);

  const now = new Date().toISOString();
  const result = await env.BWD_DB.prepare("UPDATE bookings SET status=?, updated_at=? WHERE booking_id=?")
    .bind(status, now, bookingId).run();
  if (!result.meta || Number(result.meta.changes || 0) < 1) return json({ ok: false, error: "booking_not_found" }, 404);
  return json({ ok: true, booking_id: bookingId, status });
}

async function api(request, env, ctx) {
  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === "GET" && path === "/v1/health") {
    try {
      await ensureSchema(env);
      return json({
        ok: true,
        service: "bwd-cloudflare-v2",
        database: true,
        push_configured: !!serviceAccount(env),
        admin_enrollment_configured: String(env.BWD_ADMIN_ENROLL_TOKEN || "").trim().length >= 12
      });
    } catch (err) {
      const detail = text(err && err.message ? err.message : err, 240);
      return json({ ok: false, error: "database_health_failed", detail }, 503);
    }
  }

  await ensureSchema(env);

  if (request.method === "POST" && path === "/v1/bookings") return createBooking(request, env, ctx);

  const guest = path.match(/^\/v1\/bookings\/([A-Za-z0-9_-]+)$/);
  if (request.method === "GET" && guest) return getBooking(request, env, guest[1]);

  if (request.method === "POST" && path === "/v1/admin/devices") return enrollAdmin(request, env);
  if (request.method === "GET" && path === "/v1/admin/bookings") return adminBookings(request, env);

  const status = path.match(/^\/v1\/admin\/bookings\/([A-Za-z0-9_-]+)\/status$/);
  if (request.method === "POST" && status) return adminStatus(request, env, status[1]);

  return json({ ok: false, error: "not_found" }, 404);
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    if (url.pathname === "/v1" || url.pathname.startsWith("/v1/")) {
      try { return await api(request, env, ctx); }
      catch (err) {
        const msg = String(err && err.message || err);
        if (msg === "database_not_bound") return json({ ok: false, error: "database_not_configured" }, 503);
        return json({ ok: false, error: "internal_error" }, 500);
      }
    }
    return env.ASSETS.fetch(request);
  }
};
