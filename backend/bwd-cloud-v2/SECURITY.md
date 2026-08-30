# Security Notes

- No Firebase service-account credential belongs in the Android APK.
- `BWD_ADMIN_ENROLL_TOKEN` is a server-side Firebase Functions secret only.
- Android Firebase client configuration is injected at build time; it is not an admin credential.
- Firestore rules deny all direct client reads/writes. Trusted Cloud Functions use Firebase Admin SDK.
- Admin FCM registration requires explicit owner enrollment and the server-side enrollment token.
- Booking input is length-limited and normalized before storage.
- Stale FCM registration tokens are disabled when Firebase reports them invalid.
