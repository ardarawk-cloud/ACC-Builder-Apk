# KIN Play Release Baseline

Status: ACTIVE from KIN v0.3 development lane.

## Goal
Develop KIN in stages without repeatedly replacing foundations or forcing uninstall/reinstall after every bug fix.

## Permanent identities
- Production application ID: `com.ardacore.kin`
- Development application ID: `com.ardacore.kin.dev`
- Public brand: `KIN`

Do not rename these IDs after public testing begins unless a migration is explicitly approved.

## Updateable development APK
GitHub Actions restores one stable DEVELOPMENT-ONLY signing key before `assembleDebug`. Therefore all future KIN dev APKs use the same package + signing certificate and should install as updates over earlier KIN dev builds.

The development key is intentionally non-production. It cannot become the Google Play production signing identity.

## Google Play direction
KIN targets Android 16 / API 36 from this baseline so the project is aligned with Google Play submission requirements taking effect 31 August 2026.

Production publishing path:
1. Create the KIN app in Google Play Console with package `com.ardacore.kin`.
2. Enroll in Google Play App Signing.
3. Create a separate private upload key.
4. Store the upload keystore/password/alias only in protected GitHub Actions secrets or equivalent secure storage.
5. Produce a signed Android App Bundle (`.aab`) for Play uploads.
6. Use internal/closed testing before production.
7. Add Play Integrity server verification when protected backend actions are introduced.

## Play Protect / Play Integrity
Google Play Protect is part of the Android/Google Play safety system; an app does not simply turn on a local `Play Protect` flag. The long-term trusted path is Google Play distribution, correct signing, current target SDK, minimal permissions, HTTPS-only networking, and clean behavior.

Play Integrity is a separate API. Add it when KIN has a backend capable of verifying integrity tokens. Do not bolt it onto the client without server verification.

## Build lanes
### Development
- Package: `com.ardacore.kin.dev`
- Stable dev signing certificate
- APK artifact
- Install updates over previous dev build
- Used for rapid phone testing

### Play preflight
- Package: `com.ardacore.kin`
- Release build / AAB compile validation
- No public development key
- Not considered upload-ready until private upload signing is configured

### Production
- Package: `com.ardacore.kin`
- Signed upload AAB
- Google Play App Signing
- Play testing tracks before production

## Product implementation order
Build vertical slices that survive later phases:
1. Brand + launcher + package/signing/version baseline
2. Welcome / Login / Register UI contract
3. Real account/session repository boundary
4. Profile onboarding
5. Circle + Private Relationship Note
6. Home + Moment data contracts
7. My Space + Skin engine
8. Guestbook
9. Chat
10. Backend hardening / moderation / Play Integrity

Avoid temporary navigation or duplicate screens that will be discarded in the next phase.
