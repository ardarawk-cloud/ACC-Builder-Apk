# Bali Wedding DJ — Cloudflare Booking Backend

Active delivery model:

`Guest browser -> Cloudflare Worker -> Cloudflare D1 -> FCM -> Owner APK`

The public Client APK is no longer the guest distribution target. Guests use the web booking portal; the Owner APK remains the operational app.

Firebase Cloud Functions / Blaze are not required for the active booking path.

## Cloudflare project

- Project / Worker name: `bali-wedding-dj-booking`
- Repository: `ardarawk-cloud/ACC-Builder-Apk`
- Production branch: `feat/bali-wedding-dj-cloud-v2`
- Root directory: repository root
- Build command: `bash cloudflare/bwd-booking/build.sh`
- Deploy command: `npx wrangler@latest deploy --config cloudflare/bwd-booking/wrangler.jsonc --keep-vars`
- Public origin: `https://bali-wedding-dj-booking.ardarawk.workers.dev`

## D1

Wrangler config declares a D1 binding named `BWD_DB` with database name `bwd-booking-prod`.
Wrangler 4 automatic resource provisioning can create and bind the database during deploy when no database ID is committed.

The Worker lazily creates the required tables with `CREATE TABLE IF NOT EXISTS`:

- `bookings`
- `admin_devices`

No client access token or admin enrollment credential is stored in the public website.

## Cloudflare secrets

Configure these in Cloudflare project Settings -> Variables and Secrets as encrypted secrets:

- `BWD_FIREBASE_SERVICE_ACCOUNT_JSON` — Firebase/Google service-account JSON used only by the Worker to obtain an OAuth token for FCM HTTP v1.
- `BWD_ADMIN_ENROLL_TOKEN` — private one-time Owner device enrollment code. Use a strong random value of at least 12 characters.

Do not commit either value to GitHub and do not put them in the web UI or APK.

The Firebase Android client identifiers used by the Owner APK are public Firebase app configuration and are compiled into the Owner build. Server credentials are not.

## API

Same-origin endpoints:

- `GET /v1/health`
- `POST /v1/bookings`
- `GET /v1/bookings/:bookingId` with `X-BWD-Client-Token`
- `POST /v1/admin/devices` with `X-BWD-Admin-Enroll`
- `GET /v1/admin/bookings` with `X-BWD-Admin-Device`
- `POST /v1/admin/bookings/:bookingId/status` with `X-BWD-Admin-Device`

Booking persistence is independent from push delivery: if FCM is temporarily unavailable, the booking remains stored in D1.

## Source of public web UI

Canonical web UI source remains:

`backend/bwd-cloud-v2/public/index.html`

`build.sh` copies that source into `cloudflare/bwd-booking/public` before every Cloudflare deployment.

## Activation gate

Do not call the flow LIVE/VERIFIED until all of these have real evidence:

1. Cloudflare deployment succeeds with D1 bound.
2. `/v1/health` returns `ok: true` and `database: true`.
3. Both Cloudflare secrets are configured and `push_configured` becomes `true`.
4. Owner APK installs and obtains an FCM token.
5. Owner device enrollment succeeds and receives the enrollment push.
6. A real web booking creates exactly one D1 booking row.
7. Owner phone receives `New Wedding Booking` and Owner Inbox syncs the same booking.
8. Owner status change is visible in the guest private booking portal.
