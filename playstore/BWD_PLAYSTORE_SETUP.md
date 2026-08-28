# BALI WEDDING DJ — Play Store Release Lock

Status: PREPARED FOR GOOGLE PLAY RELEASE

## Permanent Android identity

- App name: BALI WEDDING DJ
- Package / applicationId: `com.baliweddingdj.app`
- Distribution: Google Play App Bundle (`.aab`)
- Target SDK: Android 16 / API 36
- Compile SDK: API 36
- Release signing: dedicated BWD upload key + Google Play App Signing
- Customer-facing language: English

The package ID and signing lineage must never be changed after the first production release.

## Versioning

Every Play Store upload must increase `versionCode`.

Initial production track recommendation:

- versionCode: `2`
- versionName: `1.1.0`

Future example:

- 3 / 1.1.1
- 4 / 1.2.0
- 5 / 1.2.1

## Required GitHub Actions secrets

Never commit the keystore or passwords to this repository.

- `BWD_UPLOAD_KEYSTORE_B64`
- `BWD_KEYSTORE_PASSWORD`
- `BWD_KEY_ALIAS`
- `BWD_KEY_PASSWORD`

Expected alias: `bwd-upload`

## Release workflow

Workflow: `.github/workflows/build-bali-wedding-dj-playstore.yml`

The workflow:

1. Restores the native Android source.
2. Rejects WebView usage.
3. Upgrades/locks compileSdk and targetSdk to API 36.
4. Locks package ID to `com.baliweddingdj.app`.
5. Applies production wording (`Wedding Reception`).
6. Applies monotonically increasing Play Store versioning.
7. Restores the private upload key only inside the GitHub runner.
8. Builds a signed release APK for controlled testing.
9. Builds the signed AAB for Google Play.
10. Verifies the AAB signature and uploads both files as private workflow artifacts.

## Google Play release strategy

Use Google Play App Signing.

Recommended rollout order:

1. Internal testing
2. Closed testing
3. Production

Once the Play Store build is installed, normal future updates should be distributed through Google Play rather than manually installing APK files.

## Current debug-install warning

The existing manually installed v1 APK is a debug-signed development build. It is not part of the permanent Play Store signing lineage. A one-time migration (normally uninstall debug build, then install from the Play testing/production track) is expected. After that migration, keep all releases on the same package ID and signing lineage.

## Store assets still required before public production

- Final launcher/adaptive icon
- 512x512 Play icon
- Feature graphic
- Phone screenshots
- Short description
- Full description
- Privacy Policy public URL
- Data Safety declaration matching the production backend
- Content rating questionnaire
- Support email / website

Do not declare cloud data collection or payment processing behavior until the production backend behavior is finalized and verified.
