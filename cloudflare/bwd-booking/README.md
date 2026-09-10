# Bali Wedding DJ — Cloudflare Git Integration

Public delivery model:

`Guest browser -> Cloudflare Worker + Static Assets -> /v1 proxy -> Firebase api -> Firestore/FCM -> Owner APK`

The public Client APK is no longer the guest distribution target. Guests use the web booking portal; the Owner APK remains the operational app.

## Cloudflare project

- Project / Worker name: `bali-wedding-dj-booking`
- Repository: `ardarawk-cloud/ACC-Builder-Apk`
- Production branch: `feat/bali-wedding-dj-cloud-v2`
- Root directory: repository root
- Build command: `bash cloudflare/bwd-booking/build.sh`
- Deploy command: `npx wrangler@latest deploy --config cloudflare/bwd-booking/wrangler.jsonc --keep-vars`
- Non-production branch builds: optional; production does not depend on them.

## Cloudflare runtime variable

Configure in Cloudflare project Settings -> Variables and Secrets:

- `BWD_API_ORIGIN` = HTTPS base URL of the deployed Firebase `api` function, without a trailing slash.

Example shape only:

`https://<region>-<firebase-project>.cloudfunctions.net/api`

Do not put Firebase admin credentials, service-account JSON, or `BWD_ADMIN_ENROLL_TOKEN` in the public site or repository.

## Routing

- Static website and `/booking/...` private-link navigation are served by Workers Static Assets in SPA mode.
- `/v1/*` invokes `src/index.js` first and is proxied to `BWD_API_ORIGIN`.
- The browser therefore talks to the same Cloudflare origin and does not need cross-origin Firebase access.

## Source of public web UI

Canonical web UI source remains:

`backend/bwd-cloud-v2/public/index.html`

`build.sh` copies that source into `cloudflare/bwd-booking/public` before every Cloudflare deployment.

## Verification gate

A successful Cloudflare build/deploy proves hosting only. Do not call guest booking LIVE/VERIFIED until a real web submission reaches Firestore, appears in Owner Inbox, and triggers the Owner notification on a physical phone.
