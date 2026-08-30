# Bali Wedding DJ Cloud V2

This backend receives public booking requests and sends Firebase Cloud Messaging alerts only to explicitly enrolled Bali Wedding DJ admin devices.

## Required Firebase setup

1. Create a Firebase project and register Android package `com.baliweddingdj.app`.
2. Enable Cloud Firestore and Cloud Messaging.
3. Deploy this Functions project from `backend/bwd-cloud-v2`.
4. Create the Firebase Functions secret `BWD_ADMIN_ENROLL_TOKEN` with a long random value. Never embed this value in the APK or repository.
5. Put the deployed `api` function base URL into GitHub Actions secret `BWD_CLOUD_BASE_URL`.
6. Add these Android Firebase public config values as GitHub Actions secrets used by the APK build:
   - `BWD_FIREBASE_API_KEY`
   - `BWD_FIREBASE_APP_ID`
   - `BWD_FIREBASE_PROJECT_ID`
   - `BWD_FIREBASE_SENDER_ID`
7. Rebuild the `feat/bali-wedding-dj-cloud-v2` APK.
8. On the owner phone, open Admin > Enable Cloud Notifications and enter the one-time admin enrollment token.

## Runtime behavior

- Booking is written locally first so the form never loses data when connectivity drops.
- The request is queued and POSTed to `/v1/bookings` when cloud is configured.
- New cloud bookings are stored in Firestore collection `bwd_bookings`.
- The backend sends a high-priority FCM notification to enabled documents in `bwd_admin_devices`.
- Admin FCM tokens can only be registered through `/v1/admin/devices` with `X-BWD-Admin-Enroll` matching the server-side secret.
- Invalid/stale FCM tokens are automatically disabled.

The app remains functional in local-only mode when Firebase/cloud values are absent; it reports that cloud setup is required instead of crashing.
