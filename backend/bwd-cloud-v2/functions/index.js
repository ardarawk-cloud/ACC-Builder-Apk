const crypto = require('crypto');
const express = require('express');
const { onRequest } = require('firebase-functions/v2/https');
const { defineSecret } = require('firebase-functions/params');
const logger = require('firebase-functions/logger');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');

initializeApp();
const db = getFirestore();
const adminEnrollToken = defineSecret('BWD_ADMIN_ENROLL_TOKEN');
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '64kb' }));

function safeEqual(a, b) {
  const aa = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (aa.length !== bb.length || aa.length === 0) return false;
  return crypto.timingSafeEqual(aa, bb);
}

function text(v, max = 500) {
  return String(v || '').trim().slice(0, max);
}

function validBooking(body) {
  return text(body.booking_id, 64).length >= 8 &&
    text(body.bride, 120).length > 0 &&
    text(body.groom, 120).length > 0 &&
    /^\d{4}-\d{2}-\d{2}$/.test(text(body.wedding_date, 10)) &&
    text(body.email, 200).includes('@') &&
    text(body.whatsapp, 40).length >= 7;
}

async function notifyAdmins(booking) {
  const snap = await db.collection('bwd_admin_devices').where('enabled', '==', true).limit(100).get();
  const tokens = snap.docs.map((d) => d.get('token')).filter(Boolean);
  if (!tokens.length) return { sent: 0 };

  const title = 'New Wedding Booking';
  const body = `${text(booking.bride, 60)} & ${text(booking.groom, 60)} · ${text(booking.wedding_date, 10)} · ${text(booking.package_name, 80)}`;
  const result = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: {
      title,
      body,
      booking_id: text(booking.booking_id, 64),
      type: 'new_booking'
    },
    android: {
      priority: 'high',
      notification: { channelId: 'bwd_booking_alerts', sound: 'default' }
    }
  });

  const stale = [];
  result.responses.forEach((r, i) => {
    if (!r.success && r.error && ['messaging/registration-token-not-registered', 'messaging/invalid-registration-token'].includes(r.error.code)) {
      stale.push(tokens[i]);
    }
  });
  if (stale.length) {
    const batch = db.batch();
    snap.docs.forEach((doc) => {
      if (stale.includes(doc.get('token'))) batch.update(doc.ref, { enabled: false, disabled_at: FieldValue.serverTimestamp() });
    });
    await batch.commit();
  }
  return { sent: result.successCount, failed: result.failureCount };
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'bwd-cloud-v2' }));

app.post('/v1/bookings', async (req, res) => {
  try {
    const body = req.body || {};
    if (!validBooking(body)) return res.status(400).json({ ok: false, error: 'invalid_booking_payload' });

    const bookingId = text(body.booking_id, 64).replace(/[^A-Za-z0-9_-]/g, '');
    if (!bookingId) return res.status(400).json({ ok: false, error: 'invalid_booking_id' });

    const booking = {
      booking_id: bookingId,
      bride: text(body.bride, 120),
      groom: text(body.groom, 120),
      email: text(body.email, 200).toLowerCase(),
      whatsapp: text(body.whatsapp, 40),
      wedding_date: text(body.wedding_date, 10),
      venue_name: text(body.venue_name, 180),
      venue_location: text(body.venue_location, 180),
      planner: text(body.planner, 180),
      guests: Math.max(0, Math.min(10000, Number(body.guests) || 0)),
      package_name: text(body.package_name, 160),
      sections: text(body.sections, 1000),
      start_time: text(body.start_time, 40),
      finish_time: text(body.finish_time, 40),
      music_pref: text(body.music_pref, 1000),
      favorite_songs: text(body.favorite_songs, 2000),
      must_play: text(body.must_play, 2000),
      do_not_play: text(body.do_not_play, 2000),
      special_requests: text(body.special_requests, 3000),
      source: text(body.source, 40) || 'android',
      status: 'REQUEST RECEIVED',
      updated_at: FieldValue.serverTimestamp()
    };

    const ref = db.collection('bwd_bookings').doc(bookingId);
    const existing = await ref.get();
    if (!existing.exists) booking.created_at = FieldValue.serverTimestamp();
    await ref.set(booking, { merge: true });

    let push = { sent: 0 };
    if (!existing.exists) push = await notifyAdmins(booking);
    res.status(existing.exists ? 200 : 201).json({ ok: true, booking_id: bookingId, duplicate: existing.exists, push });
  } catch (err) {
    logger.error('booking submit failed', err);
    res.status(500).json({ ok: false, error: 'server_error' });
  }
});

app.post('/v1/admin/devices', async (req, res) => {
  try {
    const supplied = req.get('X-BWD-Admin-Enroll');
    if (!safeEqual(supplied, adminEnrollToken.value())) return res.status(401).json({ ok: false, error: 'unauthorized' });

    const token = text(req.body && req.body.token, 4096);
    if (token.length < 50) return res.status(400).json({ ok: false, error: 'invalid_fcm_token' });

    const id = crypto.createHash('sha256').update(token).digest('hex');
    await db.collection('bwd_admin_devices').doc(id).set({
      token,
      enabled: true,
      platform: text(req.body.platform, 32) || 'android',
      app_version: text(req.body.app_version, 64),
      updated_at: FieldValue.serverTimestamp()
    }, { merge: true });

    await getMessaging().send({
      token,
      notification: { title: 'Bali Wedding DJ', body: 'Cloud booking notifications are enabled on this device.' },
      data: { type: 'admin_enrolled', title: 'Bali Wedding DJ', body: 'Cloud booking notifications are enabled on this device.' },
      android: { priority: 'high', notification: { channelId: 'bwd_booking_alerts', sound: 'default' } }
    });
    res.json({ ok: true, enrolled: true });
  } catch (err) {
    logger.error('admin device enrollment failed', err);
    res.status(500).json({ ok: false, error: 'server_error' });
  }
});

exports.api = onRequest({ region: 'asia-southeast2', secrets: [adminEnrollToken], cors: false, maxInstances: 10 }, app);
