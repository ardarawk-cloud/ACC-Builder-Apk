# Bali Wedding DJ Cloud V2 — Activation Checklist

Code status: implemented and compile/QC verified. Runtime cloud delivery remains disabled until the external Firebase project is provisioned and the required GitHub Actions secrets are populated.

## Firebase

- Create/choose Firebase project.
- Register Client Android app package `com.baliweddingdj.app`.
- Register Owner Android app package `com.baliweddingdj.owner` in the same Firebase project.
- Enable Cloud Firestore.
- Enable Firebase Cloud Messaging.
- Deploy `backend/bwd-cloud-v2` Functions + Firestore rules.
- Confirm both HTTPS functions are deployed: `api` and `ownerApi` in `asia-southeast2`.
- Set Functions secret `BWD_ADMIN_ENROLL_TOKEN` to a long random value.

## GitHub Actions secrets

- `BWD_FIREBASE_API_KEY`
- `BWD_FIREBASE_APP_ID` — Firebase Android App ID for Client (`com.baliweddingdj.app`)
- `BWD_FIREBASE_OWNER_APP_ID` — Firebase Android App ID for Owner (`com.baliweddingdj.owner`)
- `BWD_FIREBASE_PROJECT_ID`
- `BWD_FIREBASE_SENDER_ID`
- `BWD_CLOUD_BASE_URL` — deployed base URL ending in `/api`

## Final runtime QC

1. Rebuild Cloud V2 with all six GitHub values present.
2. Install/update Client on one phone and Owner on the owner phone; both packages must coexist.
3. Grant Android notification permission on Owner.
4. Owner → Enable Cloud Notifications → enter `BWD_ADMIN_ENROLL_TOKEN`.
5. Confirm the enrollment push arrives while Owner is backgrounded.
6. Submit a new booking from the Client phone.
7. Confirm Firestore receives exactly one `bwd_bookings` document.
8. Confirm Owner phone receives `New Wedding Booking` while Owner is closed/backgrounded.
9. Open Owner and confirm Booking Inbox automatically syncs the submitted cloud booking into the dashboard.
10. Open the booking and confirm couple/date/venue/package details match the Client submission.
11. Tap a subsequent notification and verify Bali Wedding DJ Owner opens and refreshes the inbox.
12. Only after this two-device runtime QC, label Client → Cloud → Owner delivery VERIFIED and publish the public download link.
