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
const adminKey = defineSecret('BWD_ADMIN_ENROLL_TOKEN');
const storageBucket = defineString('BWD_STORAGE_BUCKET', { default: '' });
const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '3mb' }));

function safeEqual(a, b) {
  const aa = Buffer.from(String(a || ''));
  const bb = Buffer.from(String(b || ''));
  if (!aa.length || aa.length !== bb.length) return false;
  return crypto.timingSafeEqual(aa, bb);
}
function text(v, max = 500) { return String(v == null ? '' : v).trim().slice(0, max); }
function number(v, min = 0, max = Number.MAX_SAFE_INTEGER) {
  const n = Number(v);
  if (!Number.isFinite(n)) return min;
  return Math.max(min, Math.min(max, Math.round(n)));
}
function sha256(v) { return crypto.createHash('sha256').update(String(v || '')).digest('hex'); }
function cleanBookingId(v) { return text(v, 64).replace(/[^A-Za-z0-9_-]/g, ''); }
function nowIso() { return new Date().toISOString(); }
function adminAuthorized(req) { return safeEqual(req.get('X-BWD-Admin-Key'), adminKey.value()); }
function clientAuthorized(snap, req) {
  const supplied = text(req.get('X-BWD-Client-Key'), 200);
  const expected = text(snap.get('client_token_hash'), 128);
  return supplied.length >= 32 && expected.length === 64 && safeEqual(expected, sha256(supplied));
}
function validBooking(body) {
  return cleanBookingId(body.booking_id).length >= 8 &&
    text(body.bride, 120).length > 0 && text(body.groom, 120).length > 0 &&
    /^\d{4}-\d{2}-\d{2}$/.test(text(body.wedding_date, 10)) &&
    text(body.email, 200).includes('@') && text(body.whatsapp, 40).length >= 7 &&
    text(body.client_token, 200).length >= 32;
}
function isoValue(v) {
  if (v == null) return v;
  if (typeof v.toDate === 'function') return v.toDate().toISOString();
  if (Array.isArray(v)) return v.map(isoValue);
  if (typeof v === 'object') {
    const out = {};
    for (const [k, x] of Object.entries(v)) out[k] = isoValue(x);
    return out;
  }
  return v;
}
function publicBooking(data, owner = false) {
  const out = isoValue(data || {});
  delete out.client_token_hash;
  delete out.client_fcm_tokens;
  if (!owner) {
    delete out.admin_notes;
    if (out.payment) delete out.payment.storage_path;
  }
  return out;
}
async function loadBooking(id) {
  const ref = db.collection('bwd_bookings').doc(id);
  const snap = await ref.get();
  return { ref, snap };
}

async function sendMulticast(tokens, title, body, data = {}) {
  const uniq = [...new Set((tokens || []).filter((x) => typeof x === 'string' && x.length > 40))].slice(0, 100);
  if (!uniq.length) return { sent: 0, failed: 0 };
  const payloadData = { title: String(title), body: String(body) };
  for (const [k, v] of Object.entries(data)) payloadData[k] = String(v == null ? '' : v);
  const result = await getMessaging().sendEachForMulticast({
    tokens: uniq,
    notification: { title, body },
    data: payloadData,
    android: { priority: 'high', notification: { channelId: 'bwd_booking_alerts', sound: 'default' } }
  });
  return { sent: result.successCount, failed: result.failureCount };
}
async function notifyAdmins(title, body, data = {}) {
  const snap = await db.collection('bwd_admin_devices').where('enabled', '==', true).limit(100).get();
  const tokens = snap.docs.map((d) => d.get('token')).filter(Boolean);
  const result = await sendMulticast(tokens, title, body, data);
  if (result.failed) {
    // Stale tokens are cleaned on the next explicit owner enrollment. Avoid disabling valid tokens on transient FCM errors.
    logger.warn('admin push partial failure', result);
  }
  return result;
}
async function notifyClient(bookingSnap, title, body, data = {}) {
  const tokens = Array.isArray(bookingSnap.get('client_fcm_tokens')) ? bookingSnap.get('client_fcm_tokens') : [];
  return sendMulticast(tokens, title, body, data);
}

app.get('/health', (_req, res) => res.json({ ok: true, service: 'bwd-booking-system-v1', time: nowIso() }));

// PUBLIC/CLIENT ----------------------------------------------------------------
app.post('/v1/bookings', async (req, res) => {
  try {
    const body = req.body || {};
    if (!validBooking(body)) return res.status(400).json({ ok: false, error: 'invalid_booking_payload' });
    const bookingId = cleanBookingId(body.booking_id);
    const clientToken = text(body.client_token, 200);
    const clientTokenHash = sha256(clientToken);
    const { ref, snap: existing } = await loadBooking(bookingId);
    if (existing.exists) {
      const oldHash = text(existing.get('client_token_hash'), 128);
      if (oldHash && !safeEqual(oldHash, clientTokenHash)) return res.status(403).json({ ok: false, error: 'booking_token_mismatch' });
    }
    const fcmToken = text(body.fcm_token, 4096);
    const booking = {
      booking_id: bookingId,
      bride: text(body.bride, 120), groom: text(body.groom, 120),
      email: text(body.email, 200).toLowerCase(), whatsapp: text(body.whatsapp, 40),
      wedding_date: text(body.wedding_date, 10), venue_name: text(body.venue_name, 180),
      venue_location: text(body.venue_location, 180), planner: text(body.planner, 180),
      guests: number(body.guests, 0, 10000), package_name: text(body.package_name, 160),
      sections: text(body.sections, 1000), start_time: text(body.start_time, 40), finish_time: text(body.finish_time, 40),
      music_pref: text(body.music_pref, 1000), favorite_songs: text(body.favorite_songs, 2000),
      must_play: text(body.must_play, 2000), do_not_play: text(body.do_not_play, 2000),
      special_requests: text(body.special_requests, 3000), source: text(body.source, 40) || 'android',
      client_token_hash: clientTokenHash,
      status: existing.exists ? text(existing.get('status'), 40) || 'REQUEST RECEIVED' : 'REQUEST RECEIVED',
      updated_at: FieldValue.serverTimestamp()
    };
    if (!existing.exists) booking.created_at = FieldValue.serverTimestamp();
    if (fcmToken.length > 40) booking.client_fcm_tokens = FieldValue.arrayUnion(fcmToken);
    await ref.set(booking, { merge: true });
    let push = { sent: 0, failed: 0 };
    if (!existing.exists) {
      push = await notifyAdmins('New Wedding Booking', `${booking.bride} & ${booking.groom} · ${booking.wedding_date} · ${booking.package_name}`, { booking_id: bookingId, type: 'new_booking' });
    }
    const fresh = await ref.get();
    return res.status(existing.exists ? 200 : 201).json({ ok: true, booking: publicBooking(fresh.data()), push });
  } catch (err) {
    logger.error('booking submit failed', err);
    return res.status(500).json({ ok: false, error: 'server_error' });
  }
});

app.post('/v1/client/bookings/:bookingId/device', async (req, res) => {
  try {
    const id = cleanBookingId(req.params.bookingId);
    const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    if (!clientAuthorized(snap, req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const token = text(req.body && req.body.token, 4096);
    if (token.length < 40) return res.status(400).json({ ok: false, error: 'invalid_fcm_token' });
    await ref.set({ client_fcm_tokens: FieldValue.arrayUnion(token), updated_at: FieldValue.serverTimestamp() }, { merge: true });
    return res.json({ ok: true });
  } catch (err) { logger.error('client device register failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.get('/v1/client/bookings/:bookingId', async (req, res) => {
  try {
    const id = cleanBookingId(req.params.bookingId);
    const { snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    if (!clientAuthorized(snap, req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    return res.json({ ok: true, booking: publicBooking(snap.data()) });
  } catch (err) { logger.error('client booking fetch failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/client/bookings/:bookingId/quote/accept', async (req, res) => {
  try {
    const id = cleanBookingId(req.params.bookingId);
    const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    if (!clientAuthorized(snap, req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const quote = snap.get('quote');
    if (!quote) return res.status(409).json({ ok: false, error: 'quotation_not_ready' });
    await ref.set({ 'quote.accepted': true, 'quote.accepted_at': FieldValue.serverTimestamp(), status: 'WAITING FOR DEPOSIT', updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyAdmins('Quotation Accepted', `${id} · ${text(snap.get('bride'), 60)} & ${text(snap.get('groom'), 60)}`, { booking_id: id, type: 'quote_accepted' });
    const fresh = await ref.get();
    return res.json({ ok: true, booking: publicBooking(fresh.data()) });
  } catch (err) { logger.error('quote accept failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/client/bookings/:bookingId/music', async (req, res) => {
  try {
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    if (!clientAuthorized(snap, req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const plan = text(req.body && req.body.music_plan, 8000);
    await ref.set({ music_plan: plan, music_updated_at: FieldValue.serverTimestamp(), updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyAdmins('Music Plan Updated', `${id} has new wedding music details.`, { booking_id: id, type: 'music_updated' });
    return res.json({ ok: true });
  } catch (err) { logger.error('music update failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/client/bookings/:bookingId/timeline', async (req, res) => {
  try {
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    if (!clientAuthorized(snap, req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const timeline = text(req.body && req.body.timeline, 8000);
    await ref.set({ timeline, timeline_updated_at: FieldValue.serverTimestamp(), updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyAdmins('Wedding Timeline Updated', `${id} has new timeline details.`, { booking_id: id, type: 'timeline_updated' });
    return res.json({ ok: true });
  } catch (err) { logger.error('timeline update failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/bookings/:bookingId/payment-receipt', async (req, res) => {
  try {
    const id = cleanBookingId(req.params.bookingId); const body = req.body || {};
    if (!id || cleanBookingId(body.booking_id) !== id) return res.status(400).json({ ok: false, error: 'invalid_booking_id' });
    const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    const supplied = text(body.client_token, 200);
    if (supplied.length < 32 || !safeEqual(text(snap.get('client_token_hash'), 128), sha256(supplied))) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const b64 = text(body.image_base64, 2600000);
    let bytes;
    try { bytes = Buffer.from(b64, 'base64'); } catch (_e) { return res.status(400).json({ ok: false, error: 'invalid_receipt_encoding' }); }
    if (!bytes || bytes.length < 3 || bytes.length > 1800000) return res.status(413).json({ ok: false, error: 'invalid_receipt_size' });
    if (bytes[0] !== 0xff || bytes[1] !== 0xd8 || bytes[2] !== 0xff) return res.status(400).json({ ok: false, error: 'receipt_must_be_jpeg' });
    const bucketName = text(storageBucket.value(), 300);
    if (!bucketName) return res.status(503).json({ ok: false, error: 'storage_not_configured' });
    const bucket = getStorage().bucket(bucketName);
    const objectPath = `bwd_receipts/${id}/${Date.now()}-${crypto.randomBytes(8).toString('hex')}.jpg`;
    await bucket.file(objectPath).save(bytes, { resumable: false, validation: 'md5', metadata: { contentType: 'image/jpeg', cacheControl: 'private,max-age=0' } });
    const paymentId = crypto.randomUUID();
    const payment = { payment_id: paymentId, receipt_available: true, storage_path: objectPath, status: 'RECEIPT UPLOADED', verified: false, amount: 0, uploaded_at: nowIso() };
    await ref.set({ payment, updated_at: FieldValue.serverTimestamp() }, { merge: true });
    const push = await notifyAdmins('Payment Receipt Uploaded', `${id} · ${text(snap.get('bride'), 60)} & ${text(snap.get('groom'), 60)}`, { booking_id: id, payment_id: paymentId, type: 'payment_receipt' });
    return res.status(201).json({ ok: true, payment_id: paymentId, push });
  } catch (err) { logger.error('payment receipt failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

// OWNER/ADMIN ------------------------------------------------------------------
app.post('/v1/admin/devices', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const token = text(req.body && req.body.token, 4096);
    if (token.length < 40) return res.status(400).json({ ok: false, error: 'invalid_fcm_token' });
    const id = sha256(token);
    await db.collection('bwd_admin_devices').doc(id).set({ token, enabled: true, platform: text(req.body.platform, 32) || 'android', app_version: text(req.body.app_version, 64), updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await getMessaging().send({ token, notification: { title: 'Bali Wedding DJ Owner', body: 'Cloud notifications are enabled on this device.' }, data: { type: 'admin_enrolled', title: 'Bali Wedding DJ Owner', body: 'Cloud notifications are enabled on this device.' }, android: { priority: 'high', notification: { channelId: 'bwd_booking_alerts', sound: 'default' } } });
    return res.json({ ok: true, enrolled: true });
  } catch (err) { logger.error('admin device enrollment failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.get('/v1/admin/bookings', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const snap = await db.collection('bwd_bookings').limit(300).get();
    const bookings = snap.docs.map((d) => publicBooking(d.data(), true));
    bookings.sort((a, b) => String(b.created_at || '').localeCompare(String(a.created_at || '')));
    const counts = { upcoming: 0, new_requests: 0, pending_quotations: 0, waiting_payments: 0, confirmed: 0, completed: 0 };
    for (const b of bookings) {
      const s = String(b.status || '');
      if (!['COMPLETED', 'CANCELLED'].includes(s)) counts.upcoming++;
      if (s === 'REQUEST RECEIVED') counts.new_requests++;
      if (['AVAILABLE', 'QUOTATION SENT'].includes(s)) counts.pending_quotations++;
      if (['WAITING FOR DEPOSIT', 'DEPOSIT RECEIVED'].includes(s)) counts.waiting_payments++;
      if (s === 'BOOKING CONFIRMED') counts.confirmed++;
      if (s === 'COMPLETED') counts.completed++;
    }
    return res.json({ ok: true, counts, bookings });
  } catch (err) { logger.error('admin bookings fetch failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.get('/v1/admin/bookings/:bookingId', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const id = cleanBookingId(req.params.bookingId); const { snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    return res.json({ ok: true, booking: publicBooking(snap.data(), true) });
  } catch (err) { logger.error('admin booking fetch failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/admin/bookings/:bookingId/status', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const allowed = ['REQUEST RECEIVED','DATE CHECKING','AVAILABLE','QUOTATION SENT','WAITING FOR DEPOSIT','DEPOSIT RECEIVED','BOOKING CONFIRMED','EVENT PREPARATION','COMPLETED','CANCELLED'];
    const status = text(req.body && req.body.status, 40).toUpperCase();
    if (!allowed.includes(status)) return res.status(400).json({ ok: false, error: 'invalid_status' });
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    await ref.set({ status, updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyClient(snap, 'Booking Status Updated', `${id} · ${status}`, { booking_id: id, type: 'booking_status', status });
    const fresh = await ref.get(); return res.json({ ok: true, booking: publicBooking(fresh.data(), true) });
  } catch (err) { logger.error('admin status update failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/admin/bookings/:bookingId/quote', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    const body = req.body || {};
    const base = number(body.base_price, 0, 1000000000), addons = number(body.addons, 0, 1000000000), discount = number(body.discount, 0, 1000000000);
    const total = Math.max(0, base + addons - discount), depositPercent = number(body.deposit_percent || 50, 1, 100), deposit = Math.round(total * depositPercent / 100), balance = total - deposit;
    const year = new Date().getFullYear();
    const old = snap.get('quote') || {};
    const quoteNo = text(old.quote_no, 80) || `BWD-Q-${year}-${id.replace(/[^0-9]/g, '').slice(-4).padStart(4, '0')}`;
    const invoiceOld = snap.get('invoice') || {};
    const invoiceNo = text(invoiceOld.invoice_no, 80) || `BWD-INV-${year}-${id.replace(/[^0-9]/g, '').slice(-4).padStart(4, '0')}`;
    const quote = { quote_no: quoteNo, additional_services: text(body.additional_services, 3000), base_price: base, addons, discount, total, deposit_percent: depositPercent, deposit_required: deposit, balance, due_date: text(body.due_date, 20), notes: text(body.notes, 3000), terms: text(body.terms, 6000), bank_account: text(body.bank_account, 1000), payment_instructions: text(body.payment_instructions, 2000), accepted: false, updated_at: nowIso() };
    const invoice = { invoice_no: invoiceNo, total, deposit_required: deposit, balance, status: text(invoiceOld.status, 40) || 'UNPAID', due_date: text(body.due_date, 20), updated_at: nowIso() };
    await ref.set({ quote, invoice, status: 'QUOTATION SENT', updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyClient(snap, 'Quotation Ready', `${id} quotation is ready to review.`, { booking_id: id, type: 'quotation_ready' });
    const fresh = await ref.get(); return res.json({ ok: true, booking: publicBooking(fresh.data(), true) });
  } catch (err) { logger.error('admin quote failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/admin/bookings/:bookingId/payment/verify', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    const payment = snap.get('payment');
    if (!payment || !payment.receipt_available) return res.status(409).json({ ok: false, error: 'receipt_not_uploaded' });
    const paidInFull = Boolean(req.body && req.body.paid_in_full), amount = number(req.body && req.body.amount, 0, 1000000000);
    const nextPayment = { ...payment, verified: true, amount, status: 'VERIFIED', verified_at: nowIso() };
    const invoice = { ...(snap.get('invoice') || {}), status: paidInFull ? 'PAID' : 'PARTIALLY PAID', updated_at: nowIso() };
    const status = paidInFull ? 'BOOKING CONFIRMED' : 'DEPOSIT RECEIVED';
    await ref.set({ payment: nextPayment, invoice, status, updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyClient(snap, 'Payment Verified', `${id} payment has been verified by Bali Wedding DJ.`, { booking_id: id, type: 'payment_verified', status });
    const fresh = await ref.get(); return res.json({ ok: true, booking: publicBooking(fresh.data(), true) });
  } catch (err) { logger.error('admin payment verify failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/admin/bookings/:bookingId/timeline', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    const timeline = text(req.body && req.body.timeline, 8000);
    await ref.set({ timeline, timeline_updated_at: FieldValue.serverTimestamp(), updated_at: FieldValue.serverTimestamp() }, { merge: true });
    await notifyClient(snap, 'Wedding Timeline Updated', `${id} timeline has been updated.`, { booking_id: id, type: 'timeline_updated' });
    return res.json({ ok: true });
  } catch (err) { logger.error('admin timeline failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.post('/v1/admin/bookings/:bookingId/notes', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const id = cleanBookingId(req.params.bookingId); const { ref, snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    await ref.set({ admin_notes: text(req.body && req.body.admin_notes, 6000), updated_at: FieldValue.serverTimestamp() }, { merge: true });
    return res.json({ ok: true });
  } catch (err) { logger.error('admin notes failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

app.get('/v1/admin/bookings/:bookingId/payment-receipt', async (req, res) => {
  try {
    if (!adminAuthorized(req)) return res.status(401).json({ ok: false, error: 'unauthorized' });
    const id = cleanBookingId(req.params.bookingId); const { snap } = await loadBooking(id);
    if (!snap.exists) return res.status(404).json({ ok: false, error: 'booking_not_found' });
    const payment = snap.get('payment') || {}; const path = text(payment.storage_path, 1000);
    if (!path) return res.status(404).json({ ok: false, error: 'receipt_not_found' });
    const bucketName = text(storageBucket.value(), 300);
    if (!bucketName) return res.status(503).json({ ok: false, error: 'storage_not_configured' });
    const [bytes] = await getStorage().bucket(bucketName).file(path).download();
    res.set('Cache-Control', 'private, no-store'); res.type('image/jpeg'); return res.send(bytes);
  } catch (err) { logger.error('admin receipt fetch failed', err); return res.status(500).json({ ok: false, error: 'server_error' }); }
});

exports.api = onRequest({
  region: 'asia-southeast2',
  secrets: [adminKey],
  cors: false,
  maxInstances: 10,
  timeoutSeconds: 60,
  memory: '512MiB'
}, app);
