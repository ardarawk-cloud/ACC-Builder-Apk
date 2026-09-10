# Cloud V2 Status

- Public guest delivery direction: WEB BOOKING on Cloudflare; public Client APK distribution is no longer the target.
- Owner Android operational app: ACTIVE direction.
- Client + Owner Android compile/build: PASS (`run 34453622668`)
- Client applicationId: `com.baliweddingdj.app`
- Owner applicationId: `com.baliweddingdj.owner`
- Owner cloud Booking Inbox sync code: INTEGRATED / COMPILE PASS
- Backend `api` + secure `ownerApi` dependency/export/syntax QC: PASS
- Cloudflare Worker + Static Assets build / Wrangler dry-run QC: PASS (`run 34511088612`)
- Cloudflare project config: `bali-wedding-dj-booking`
- Cloudflare deployment model: direct Cloudflare Git Integration from `ardarawk-cloud/ACC-Builder-Apk`, production branch `feat/bali-wedding-dj-cloud-v2`
- Cloudflare live deployment: PENDING one-time repository import/project connection in Cloudflare and runtime `BWD_API_ORIGIN` configuration.
- Direct Firestore client access: DENIED by rules
- Firebase/Cloud runtime activation: PENDING external Firebase project provisioning, Functions/rules deployment, and Android build configuration
- Required Android Firebase registrations: Client + Owner in the same Firebase project while the Owner APK remains operationally active
- Required build values include separate Client and Owner Firebase App IDs

Do not label Web -> Cloud -> Owner delivery, notifications, or Cloud V2 as LIVE/VERIFIED until the activation checklist passes on real runtime evidence.
