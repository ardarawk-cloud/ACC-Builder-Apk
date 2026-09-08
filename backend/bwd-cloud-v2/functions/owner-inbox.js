const crypto = require('crypto');
const express = require('express');
const { onRequest } = require('firebase-functions/v2/https');
const logger = require('firebase-functions/logger');
const { getApps, initializeApp } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');

if (!getApps().length) initializeApp();
const db = getFirestore();
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '512kb' }));

function text(v, max = 500) {
  return String(v || '').trim().slice(0, max);
}

function sha256(v) {
  return crypto.createHash('sha256').update(String(v || '')).digest('hex');
}

function safeEqual(a, b) {
  const aa = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (aa.length !== bb.length || aa.length === 0) return false;
  return crypto.timingSafeEqual(aa, bb);
}

async function authorizedAdmin(req) {
  const token = text(req.get('X-BWD-Admin-Device'), 4096);
  if (token.length < 50) return false;
  const snap = await db.collection('bwd_admin_devices').doc(sha256(token)).get();
  if (!snap.exists || snap.get('enabled') !== true) return false;
  return safeEqual(token, snap.get('token'));
}

function bookingJson(doc) {
  const b = doc.data() || {};
  const created = b.created_at && typeof b.created_at.toDate === 'function' ? b.created_at.toDate().toISOString() : '';
  return {
    booking_id: text(b.booking_id, 64),
    created_at: created,
    bride: text(b.bride, 120),
    groom: text(b.groom, 120),
    email: text(b.email, 200),
    whatsapp: text(b.whatsapp, 40),
    wedding_date: text(b.wedding_date, 10),
    venue_name: text(b.venue_name, 180),
    venue_location: text(b.venue_location, 180),
    planner: text(b.planner, 180),
    guests: Math.max(0, Math.min(10000, Number(b.guests) || 0)),
    package_name: text(b.package_name, 160),
    sections: text(b.sections, 1000),
    start_time: text(b.start_time, 40),
    finish_time: text(b.finish_time, 40),
    music_pref: text(b.music_pref, 1000),
    favorite_songs: text(b.favorite_songs, 2000),
    must_play: text(b.must_play, 2000),
    do_not_play: text(b.do_not_play, 2000),
    special_requests: text(b.special_requests, 3000),
    status: text(b.status, 80) || 'REQUEST RECEIVED'
  };
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'bwd-owner-inbox' }));

app.get('/v1/admin/bookings', async (req, res) => {
  try {
    if (!(await authorizedAdmin(req))) return res.status(401).json({ ok: false, error: 'unauthorized_admin_device' });
    const snap = await db.collection('bwd_bookings').orderBy('created_at', 'desc').limit(100).get();
    res.json({ ok: true, bookings: snap.docs.map(bookingJson) });
  } catch (err) {
    logger.error('owner inbox fetch failed', err);
    res.status(500).json({ ok: false, error: 'server_error' });
  }
});

exports.ownerApi = onRequest({ region: 'asia-southeast2', cors: false, maxInstances: 10 }, app);
