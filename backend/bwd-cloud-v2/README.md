# Bali Wedding DJ Booking System v1 — Firebase Activation

The production architecture is two Android apps using one Firebase project and one trusted Cloud Functions backend.

## Android apps

Register both packages in the same Firebase project:

- Client: `com.baliweddingdj.app`
- Owner: `com.baliweddingdj.owner`

Enable Cloud Firestore, Cloud Storage, and Firebase Cloud Messaging.

## Backend configuration

Deploy `backend/bwd-cloud-v2` and configure:

- Firebase secret `BWD_ADMIN_ENROLL_TOKEN` — long private owner key. Never commit it or embed it in an APK.
- Firebase parameter `BWD_STORAGE_BUCKET` — Firebase Storage bucket used for private payment receipt objects.

Deploy the included Firestore and Storage rules. Direct app access is deny-all; application reads and writes go through Cloud Functions/Admin SDK.

## GitHub Actions secrets for the production Android build

- `BWD_FIREBASE_API_KEY`
- `BWD_FIREBASE_APP_ID` — Client Android Firebase App ID
- `BWD_FIREBASE_OWNER_APP_ID` — Owner Android Firebase App ID
- `BWD_FIREBASE_PROJECT_ID`
- `BWD_FIREBASE_SENDER_ID`
- `BWD_CLOUD_BASE_URL` — deployed `api` function base URL

## Runtime flow

Client booking → Firestore → Owner push → Owner status update → Client push/sync → quotation + invoice/deposit instructions → Client acceptance → private receipt upload → Owner receipt push/view → Owner payment verification → Client payment update → booking confirmation.

Music plan and wedding timeline also synchronize through the backend. Each booking receives a random private client key; only its SHA-256 hash is stored server-side. The owner key is entered once on the Owner device and remains outside source code and APK build configuration.

Payment receipts are private Storage objects. The Owner APK retrieves receipt bytes through the authenticated backend endpoint rather than exposing a public receipt URL.

## Release policy

Development is performed on `feat/bwd-v1-master-batch` without per-commit CI. Run one combined master RC build only after the batch is complete. Do not distribute the production Client APK until a two-phone test passes the entire booking → quotation → receipt → payment verification → confirmation loop.
