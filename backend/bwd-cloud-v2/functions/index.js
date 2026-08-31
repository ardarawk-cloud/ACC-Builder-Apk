const crypto = require('crypto');
const express = require('express');
const { onRequest } = require('firebase-functions/v2/https');
const { defineSecret, defineString } = require('firebase-functions/params');
const logger = require('firebase-functions/logger');
const { initializeApp } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { getMessaging } = require('firebase-admin/messaging');
const { getStorage } = require('firebase-admin/storage');

initializeApp();
const db = getFirestore();
const adminEnrollToken = defineSecret('BWD_ADMIN_ENROLL_TOKEN');
const storageBucket = defineString('BWD_STORAGE_BUCKET', { default: '' });
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '3mb' }));

function safeEqual(a, b) {
  const aa = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (aa.length !== bb.length || aa.length === 0) return false;
  return crypto.timingSafeEqual(aa, bb);
}

function text(v, max = 500) {
  return String(v || '').trim().slice(0, max);
}

function sha256(v) {
  return crypto.createHash('sha256').update(String(v || '')).digest('hex');
}

function validBooking(body) {
  return text(body.booking_id, 64).length >= 8 &&
    text(body.bride, 120).length > 0 &&
    text(body.groom, 120).length > 0 &&
    /^\d{4}-\d{2}-\d{2}$/.test(text(body.wedding_date, 10)) &&
    text(body.email, 200).includes('@') &&
    text(body.whatsapp, 40).length >= 7 &&
    text(body.client_token, 160).length >= 32;
}

async function notifyAdmins({ title, body, data = {} }) {
  const snap = await db.collection('bwd_admin_devices').where('enabled', '==', true).limit(100).get();
  const tokens = snap.docs.map((d) => d.get('token')).filter(Boolean);
  if (!tokens.length) return { sent: 0, failed: 0 };

  const payloadData = {};
  Object.entries(data).forEach(([k, v]) => { payloadData[k] = String(v == null ? '' : v); });
  payloadData.title = String(title);
  payloadData.body = String(body);

  const result = await getMessaging().sendEachForMulticast({
    tokens,
    notification: { title, body },
    data: payloadData,
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

    const clientToken = text(body.client_token, 160);
    const clientTokenHash = sha256(clientToken);
    const ref = db.collection('bwd_bookings').doc(bookingId);
    const existing = await ref.get();
    if (existing.exists) {
      const existingHash = text(existing.get('client_token_hash'), 128);
      if (existingHash && !safeEqual(existingHash, clientTokenHash)) {
        return res.status(403).json({ ok: false, error: 'booking_token_mismatch' });
      }
    }

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
      client_token_hash: clientTokenHash,
      status: existing.exists ? (existing.get('status') || 'REQUEST RECEIVED') : 'REQUEST RECEIVED',
      updated_at: FieldValue.serverTimestamp()
    };

    if (!existing.exists) booking.created_at = FieldValue.serverTimestamp();
    await ref.set(booking, { merge: true });

    let push = { sent: 0, failed: 0 };
    if (!existing.exists) {
      const title = 'New Wedding Booking';
      const bodyText = `${booking.bride} & ${booking.groom} · ${booking.wedding_date} · ${booking.package_name}`;
      push = await notifyAdmins({
        title,
        body: bodyText,
        data: { booking_id: bookingId, type: 'new_booking' }
      });
    }
    res.status(existing.exists ? 200 : 201).json({ ok: true, booking_id: bookingId, duplicate: existing.exists, push });
  } catch (err) {
    logger.error('booking submit failed', err);
    res.status(500).json({ ok: false, error: 'server_error' });
  }
});

app.post('/v1/bookings/:bookingId/payment-receipt', async (req, res) => {
  try {
    const bookingId = text(req.params.bookingId, 64).replace(/[^A-Za-z0-9_-]/g, '');
    const body = req.body || {};
    if (!bookingId || text(body.booking_id, 64) !== bookingId) return res.status(400).json({ ok: false, error: 'invalid_booking_id' });

    const bookingRef = db.collection('bwd_bookings').doc(bookingId);
    const bookingSnap = await bookingRef.get();
    if (!bookingSnap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });

    const clientToken = text(body.client_token, 160);
    const expectedHash = text(bookingSnap.get('client_token_hash'), 128);
    if (!expectedHash || clientToken.length < 32 || !safeEqual(expectedHash, sha256(clientToken))) {
      return res.status(403).json({ ok: false, error: 'unauthorized_booking_upload' });
    }

    const b64 = text(body.image_base64, 2600000);
    if (!b64) return res.status(400).json({ ok: false, error: 'missing_receipt' });
    let bytes;
    try { bytes = Buffer.from(b64, 'base64'); } catch (_e) { return res.status(400).json({ ok: false, error: 'invalid_receipt_encoding' }); }
    if (!bytes.length || bytes.length > 1800000) return res.status(413).json({ ok: false, error: 'receipt_too_large' });
    if (bytes.length < 3 || bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff) {
      return res.status(400).json({ ok: false, error: 'receipt_must_be_jpeg' });
    }

    const bucketName = text(storageBucket.value(), 300);
    if (!bucketName) return res.status(503).json({ ok: false, error: 'storage_not_configured' });
    const bucket = getStorage().bucket(bucketName);
    const suffix = crypto.randomBytes(8).toString('hex');
    const objectPath = `bwd_receipts/${bookingId}/${Date.now()}-${suffix}.jpg`;
    const downloadToken = crypto.randomUUID();
    const file = bucket.file(objectPath);
    await file.save(bytes, {
      resumable: false,
      validation: 'md5',
      metadata: {
        contentType: 'image/jpeg',
        cacheControl: 'private,max-age=0',
        metadata: { firebaseStorageDownloadTokens: downloadToken }
      }
    });

    const receiptUrl = `https://firebasestorage.googleapis.com/v0/b/${encodeURIComponent(bucket.name)}/o/${encodeURIComponent(objectPath)}?alt=media&token=${encodeURIComponent(downloadToken)}`;
    const paymentRef = db.collection('bwd_payments').doc();
    await paymentRef.set({
      payment_id: paymentRef.id,
      booking_id: bookingId,
      storage_path: objectPath,
      status: 'RECEIPT UPLOADED',
      verified: false,
      created_at: FieldValue.serverTimestamp()
    });

    const bride = text(bookingSnap.get('bride'), 60);
    const groom = text(bookingSnap.get('groom'), 60);
    const title = 'Payment Receipt Uploaded';
    const bodyText = `${bookingId} · ${bride} & ${groom}`;
    const push = await notifyAdmins({
      title,
      body: bodyText,
      data: {
        booking_id: bookingId,
        payment_id: paymentRef.id,
        type: 'payment_receipt',
        receipt_url: receiptUrl
      }
    });

    res.status(201).json({ ok: true, payment_id: paymentRef.id, push });
  } catch (err) {
    logger.error('payment receipt upload failed', err);
    res.status(500).json({ ok: false, error: 'server_error' });
  }
});

app.post('/v1/admin/devices', async (req, res) => {
  try {
    const supplied = req.get('X-BWD-Admin-Enroll');
    if (!safeEqual(supplied, adminEnrollToken.value())) return res.status(401).json({ ok: false, error: 'unauthorized' });

    const token = text(req.body && req.body.token, 4096);
    if (token.length < 50) return res.status(400).json({ ok: false, error: 'invalid_fcm_token' });

    const id = sha256(token);
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
