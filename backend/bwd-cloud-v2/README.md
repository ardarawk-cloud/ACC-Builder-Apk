# Bali Wedding DJ Cloud V2

This backend receives public booking requests and payment receipts and sends Firebase Cloud Messaging alerts only to explicitly enrolled Bali Wedding DJ owner/admin devices.

## Required Firebase setup

1. Create a Firebase project and register Android package `com.baliweddingdj.app`.
2. Enable Cloud Firestore, Cloud Messaging, and Cloud Storage.
3. Deploy this Functions project plus `firestore.rules` and `storage.rules` from `backend/bwd-cloud-v2`.
4. Set Firebase parameter `BWD_STORAGE_BUCKET` to the Cloud Storage bucket name used for private wedding receipt objects.
5. Create the Firebase Functions secret `BWD_ADMIN_ENROLL_TOKEN` with a long random value. Never embed this value in the APK or repository.
6. Put the deployed `api` function base URL into GitHub Actions secret `BWD_CLOUD_BASE_URL`.
7. Add these Android Firebase public config values as GitHub Actions secrets used by the APK build:
   - `BWD_FIREBASE_API_KEY`
   - `BWD_FIREBASE_APP_ID`
   - `BWD_FIREBASE_PROJECT_ID`
   - `BWD_FIREBASE_SENDER_ID`
8. Rebuild `feat/bali-wedding-dj-cloud-v2`. The workflow produces a public CLIENT APK and a private OWNER ADMIN APK.
9. Install only the OWNER ADMIN build on the owner phone. Open Admin > Enable Cloud Notifications and enter the private admin enrollment token.
10. Distribute only the CLIENT build to customers. The CLIENT build has no Admin Access entry.

## Runtime behavior

- Booking is written locally first so the form never loses data when connectivity drops.
- The booking request is queued and POSTed to `/v1/bookings` when cloud is configured.
- New cloud bookings are stored in Firestore collection `bwd_bookings`.
- Each booking gets a random client-side capability token; only its SHA-256 hash is stored in Firestore. That token authorizes receipt upload for that booking without exposing an admin credential.
- Customer payment receipt images are resized/compressed on-device and securely POSTed to `/v1/bookings/:bookingId/payment-receipt`.
- The backend stores receipt JPEGs in Cloud Storage and metadata in `bwd_payments`.
- Direct Firestore and Storage client access is denied by security rules; trusted backend Admin SDK performs the writes.
- A successful receipt upload sends a high-priority `Payment Receipt Uploaded` FCM alert to enrolled owner/admin devices. The alert carries the secure receipt viewing URL so tapping it opens the uploaded proof.
- New booking requests also send high-priority FCM alerts.
- Admin FCM tokens can only be registered through `/v1/admin/devices` with `X-BWD-Admin-Enroll` matching the server-side secret.
- Invalid/stale FCM tokens are automatically disabled.

The app remains functional in local-only mode when Firebase/cloud values are absent. In that mode receipt status explicitly says that cloud receipt sync is unavailable; it does not claim that an owner has received the image.
