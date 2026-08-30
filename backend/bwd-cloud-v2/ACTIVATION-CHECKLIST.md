# Bali Wedding DJ Cloud V2 — Activation Checklist

Code status: implemented and compile/QC verified. Runtime cloud delivery remains disabled until the external Firebase project is provisioned and the required GitHub Actions secrets are populated.

## Firebase

- Create/choose Firebase project.
- Register Android app package `com.baliweddingdj.app`.
- Enable Cloud Firestore.
- Enable Firebase Cloud Messaging.
- Deploy `backend/bwd-cloud-v2` Functions + Firestore rules.
- Set Functions secret `BWD_ADMIN_ENROLL_TOKEN` to a long random value.

## GitHub Actions secrets

- `BWD_FIREBASE_API_KEY`
- `BWD_FIREBASE_APP_ID`
- `BWD_FIREBASE_PROJECT_ID`
- `BWD_FIREBASE_SENDER_ID`
- `BWD_CLOUD_BASE_URL`

## Final runtime QC

1. Rebuild Cloud V2 with all five values present.
2. Install the rebuilt APK on the owner phone.
3. Grant Android notification permission.
4. Admin → Enable Cloud Notifications → enter `BWD_ADMIN_ENROLL_TOKEN`.
5. Confirm the enrollment push arrives while the APK is backgrounded.
6. Submit a new booking from a separate phone.
7. Confirm Firestore receives exactly one booking document.
8. Confirm owner phone receives `New Wedding Booking` while the APK is closed/backgrounded.
9. Tap notification and verify Bali Wedding DJ opens.
10. Only after this runtime QC, publish the public download link.
