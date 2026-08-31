# BALI WEDDING DJ — BOOKING SYSTEM v1.0 MASTER PLAN

Status: ACTIVE / MASTER BATCH
Branch: `feat/bwd-v1-master-batch`

## Delivery rule

Do not run Android/backend CI on every implementation commit. Development commits on this branch are intentionally outside the existing auto-build branch filters. Run one combined RC build/QC only after the complete core loop is implemented. A second production build is allowed only after real Firebase credentials/backend URL are provisioned.

## Product split

### Client APK
- Package: `com.baliweddingdj.app`
- Public/customer app only.
- Navigation: Home · Packages · My Booking · Profile.
- No Admin Access, admin PIN, admin settings, or owner controls in the customer binary.

### Owner APK
- Package: `com.baliweddingdj.owner`
- Label: Bali Wedding DJ Owner.
- Owner-only operations app.
- Navigation: Dashboard · Bookings · Payments · Notifications · Settings.
- Cloud admin key is entered on the owner device and never embedded in source/APK.

## Core loop — release blocking

1. Client submits booking.
2. Booking is stored locally first and queued to cloud.
3. Owner receives New Wedding Booking push.
4. Owner syncs/opens booking and changes status.
5. Client syncs status and receives push updates.
6. Owner creates quotation and invoice/deposit instructions.
7. Client accepts quotation.
8. Client uploads payment receipt.
9. Receipt is stored privately in Firebase Storage.
10. Owner receives Payment Receipt Uploaded push and can view receipt from Owner APK.
11. Owner verifies/rejects payment.
12. Client receives payment verification update.
13. Owner confirms booking.
14. Client status becomes Booking Confirmed.
15. Music plan and wedding timeline sync between client and owner.

## Cloud authority

- Firestore: booking/quote/invoice/status/music/timeline metadata.
- Firebase Storage: private payment receipts only.
- Firebase Cloud Messaging: owner + client push notifications.
- Cloud Functions: all booking mutations, admin authorization, receipt access, and notification fanout.
- Direct Firestore/Storage client access remains denied.

## Security

- Client booking gets a random per-booking client key; backend stores only its SHA-256 hash.
- Owner API key is a server-side Firebase secret and is entered manually on Owner APK.
- Admin key is never hardcoded in repository or APK.
- Receipt files are not public URLs; Owner APK retrieves them through authenticated backend access.
- Customer APK cannot expose any admin UI even when offline.

## Release gate

Two real Android phones are mandatory for final validation:

Client phone → booking → owner push → owner status → client sync/push → quotation → accept → receipt upload → owner receipt push/view → payment verify → client update → booking confirmed.

Only after the full sequence passes may the Client APK be used for Instagram/Lynk public distribution.
