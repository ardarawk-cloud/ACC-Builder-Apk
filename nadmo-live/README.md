# NADMO LIVE Android — isolated beta v0.8

Private beta only. This branch `feat/nadmo-live-android-beta-20261009` does not modify any other NADMO application or the default branch.

- Package: `id.nadmo.live`. Android versionCode **8**, versionName **0.8.0-beta**.
- Source: `nadmo-live/android/`.
- Hosted web beta: https://nadmo-live-beta-20261009.ardarawk.workers.dev/app/
- Android shell opens the hosted beta first, with the embedded offline fallback as a retry path. Offline mode does not offer live streaming.
- Launcher uses the NADMO lime `N/` adaptive icon resources and `NADMO LIVE` app label. Icon changes are visible only in a newly built/installed APK; they are not remotely updatable through the hosted web client.
- Debug CI: `.github/workflows/nadmo-live-build-beta.yml`, for QA only.
- Signed update CI: `.github/workflows/nadmo-live-signed-release.yml`. Uses the already pinned SHA-256 release certificate from `nadmo-live/android/release-identity.json`. It will fail if the protected keystore is absent or certificate differs; no unsafe key rotation.
- Previous debug APKs can have incompatible one-time signatures; once installed on the stable signed certificate, future upgrades must retain the certificate and increment versionCode.
- Payments, paid room tickets, KYC and public creator onboarding are NOT active. WebRTC STUN-only peer streaming (up to 4 viewers/room) is a test configuration, not production scale.
- The hosting/backend is updated separately in the `ardarawk-cloud/ACC-OS-X` beta branch.

Do not distribute as unrestricted public commercial streaming until server-enforced verified-creator identity, moderation/reporting and multi-network reliability checks pass.
