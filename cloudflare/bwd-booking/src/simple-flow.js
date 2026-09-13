import core from "./index-v2.js";

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

function text(v, max = 1200) {
  return String(v == null ? "" : v).trim().slice(0, max);
}

function asInt(v, fallback = 0) {
  const n = Number(v);
  return Number.isFinite(n) ? Math.round(n) : fallback;
}

async function ensureSimpleSchema(env) {
  if (!env.BWD_DB) throw new Error("database_not_bound");
  const info = await env.BWD_DB.prepare("PRAGMA table_info(bookings)").all();
  const existing = new Set((info.results || []).map(r => String(r.name || "")));
  const columns = [
    ["invoice_no", "TEXT"],
    ["invoice_total", "INTEGER"],
    ["invoice_deposit", "INTEGER"],
    ["invoice_balance", "INTEGER"],
    ["invoice_due_date", "TEXT"],
    ["payment_instructions", "TEXT"],
    ["payment_status", "TEXT"],
    ["payment_submitted_at", "TEXT"],
    ["payment_confirmed_at", "TEXT"]
  ];
  for (const [name, type] of columns) {
    if (!existing.has(name)) {
      try {
        await env.BWD_DB.prepare(`ALTER TABLE bookings ADD COLUMN ${name} ${type}`).run();
      } catch (err) {
        if (!String(err && err.message || err).toLowerCase().includes("duplicate column")) throw err;
      }
    }
  }
}

function invoiceNo(bookingId) {
  const d = new Date();
  const day = `${d.getUTCFullYear()}${String(d.getUTCMonth()+1).padStart(2,"0")}${String(d.getUTCDate()).padStart(2,"0")}`;
  const suffix = String(bookingId || "").replace(/[^A-Za-z0-9]/g, "").slice(-8).toUpperCase() || "BOOKING";
  return `BWD-${day}-${suffix}`;
}

async function extras(env, bookingId) {
  return await env.BWD_DB.prepare(`
    SELECT invoice_no, invoice_total, invoice_deposit, invoice_balance,
           invoice_due_date, payment_instructions, payment_status,
           payment_submitted_at, payment_confirmed_at
    FROM bookings WHERE booking_id = ? LIMIT 1
  `).bind(bookingId).first();
}

function mergeBooking(booking, row) {
  if (!booking || !row) return booking;
  return {
    ...booking,
    invoice_no: row.invoice_no || "",
    invoice_total: asInt(row.invoice_total, 0),
    invoice_deposit: asInt(row.invoice_deposit, 0),
    invoice_balance: asInt(row.invoice_balance, 0),
    invoice_due_date: row.invoice_due_date || "",
    payment_instructions: row.payment_instructions || "",
    payment_status: row.payment_status || "",
    payment_submitted_at: row.payment_submitted_at || "",
    payment_confirmed_at: row.payment_confirmed_at || ""
  };
}

async function adminProbe(request, env, ctx) {
  const u = new URL(request.url);
  u.pathname = "/v1/admin/bookings";
  u.search = "";
  const headers = new Headers();
  const device = request.headers.get("X-BWD-Admin-Device");
  if (device) headers.set("X-BWD-Admin-Device", device);
  headers.set("Accept", "application/json");
  return await core.fetch(new Request(u.toString(), { method: "GET", headers }), env, ctx);
}

async function clientProbe(request, env, ctx, bookingId) {
  const u = new URL(request.url);
  u.pathname = `/v1/bookings/${encodeURIComponent(bookingId)}`;
  u.search = "";
  const headers = new Headers();
  const token = request.headers.get("X-BWD-Client-Token");
  if (token) headers.set("X-BWD-Client-Token", token);
  headers.set("Accept", "application/json");
  return await core.fetch(new Request(u.toString(), { method: "GET", headers }), env, ctx);
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname;

    if (request.method === "GET" && /^\/v1\/bookings\/[^/]+$/.test(path)) {
      const res = await core.fetch(request, env, ctx);
      if (!res.ok) return res;
      const body = await res.json();
      const bookingId = decodeURIComponent(path.split("/").pop());
      await ensureSimpleSchema(env);
      const row = await extras(env, bookingId);
      if (body && body.booking) body.booking = mergeBooking(body.booking, row);
      return json(body, res.status);
    }

    if (request.method === "GET" && path === "/v1/admin/bookings") {
      const res = await core.fetch(request, env, ctx);
      if (!res.ok) return res;
      const body = await res.json();
      await ensureSimpleSchema(env);
      if (body && Array.isArray(body.bookings) && body.bookings.length) {
        const rows = await env.BWD_DB.prepare(`
          SELECT booking_id, invoice_no, invoice_total, invoice_deposit, invoice_balance,
                 invoice_due_date, payment_instructions, payment_status,
                 payment_submitted_at, payment_confirmed_at
          FROM bookings ORDER BY created_at DESC LIMIT 250
        `).all();
        const map = new Map((rows.results || []).map(r => [String(r.booking_id), r]));
        body.bookings = body.bookings.map(b => mergeBooking(b, map.get(String(b.booking_id))));
      }
      return json(body, res.status);
    }

    let m = path.match(/^\/v1\/admin\/bookings\/([^/]+)\/invoice$/);
    if (request.method === "POST" && m) {
      const auth = await adminProbe(request, env, ctx);
      if (!auth.ok) return auth;
      await ensureSimpleSchema(env);
      const bookingId = decodeURIComponent(m[1]);
      const exists = await env.BWD_DB.prepare("SELECT booking_id FROM bookings WHERE booking_id = ? LIMIT 1").bind(bookingId).first();
      if (!exists) return json({ ok: false, error: "booking_not_found" }, 404);
      let body = {};
      try { body = await request.json(); } catch (_) {}
      const total = Math.max(0, asInt(body.total, 0));
      if (total <= 0) return json({ ok: false, error: "invalid_invoice_total" }, 400);
      const pct = Math.max(1, Math.min(100, asInt(body.deposit_percent, 50)));
      const deposit = Math.round(total * pct / 100);
      const balance = Math.max(0, total - deposit);
      const due = text(body.due_date, 32);
      const instructions = text(body.payment_instructions, 1200);
      const suppliedNumber = text(body.invoice_no, 80).replace(/[^A-Za-z0-9._-]/g, "");
      const number = suppliedNumber || invoiceNo(bookingId);
      const now = new Date().toISOString();
      await env.BWD_DB.prepare(`
        UPDATE bookings SET
          invoice_no = ?, invoice_total = ?, invoice_deposit = ?, invoice_balance = ?,
          invoice_due_date = ?, payment_instructions = ?, payment_status = 'PENDING',
          payment_submitted_at = NULL, payment_confirmed_at = NULL,
          status = 'INVOICE SENT', updated_at = ?
        WHERE booking_id = ?
      `).bind(number, total, deposit, balance, due, instructions, now, bookingId).run();
      return json({
        ok: true,
        booking_id: bookingId,
        status: "INVOICE SENT",
        invoice: { invoice_no: number, total, deposit, balance, due_date: due, payment_instructions: instructions }
      });
    }

    m = path.match(/^\/v1\/bookings\/([^/]+)\/payment-submitted$/);
    if (request.method === "POST" && m) {
      const bookingId = decodeURIComponent(m[1]);
      const auth = await clientProbe(request, env, ctx, bookingId);
      if (!auth.ok) return auth;
      await ensureSimpleSchema(env);
      const row = await env.BWD_DB.prepare("SELECT invoice_total FROM bookings WHERE booking_id = ? LIMIT 1").bind(bookingId).first();
      if (!row) return json({ ok: false, error: "booking_not_found" }, 404);
      if (asInt(row.invoice_total, 0) <= 0) return json({ ok: false, error: "invoice_not_sent" }, 409);
      const now = new Date().toISOString();
      await env.BWD_DB.prepare(`
        UPDATE bookings SET status = 'PAYMENT SUBMITTED', payment_status = 'SUBMITTED',
          payment_submitted_at = ?, updated_at = ? WHERE booking_id = ?
      `).bind(now, now, bookingId).run();
      return json({ ok: true, booking_id: bookingId, status: "PAYMENT SUBMITTED" });
    }

    m = path.match(/^\/v1\/admin\/bookings\/([^/]+)\/payment-confirmed$/);
    if (request.method === "POST" && m) {
      const auth = await adminProbe(request, env, ctx);
      if (!auth.ok) return auth;
      await ensureSimpleSchema(env);
      const bookingId = decodeURIComponent(m[1]);
      const row = await env.BWD_DB.prepare("SELECT invoice_total FROM bookings WHERE booking_id = ? LIMIT 1").bind(bookingId).first();
      if (!row) return json({ ok: false, error: "booking_not_found" }, 404);
      if (asInt(row.invoice_total, 0) <= 0) return json({ ok: false, error: "invoice_not_sent" }, 409);
      const now = new Date().toISOString();
      await env.BWD_DB.prepare(`
        UPDATE bookings SET status = 'BOOKING CONFIRMED', payment_status = 'CONFIRMED',
          payment_confirmed_at = ?, updated_at = ? WHERE booking_id = ?
      `).bind(now, now, bookingId).run();
      return json({ ok: true, booking_id: bookingId, status: "BOOKING CONFIRMED" });
    }

    return core.fetch(request, env, ctx);
  }
};
