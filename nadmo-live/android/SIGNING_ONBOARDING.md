# NADMO LIVE — Signed APK Release Setup (One Time)

**Status:** app builds as debug; production signing material has NOT been confirmed present. Do not publish a debug APK as permanent release.

The NADMO LIVE Android package ID is `id.nadmo.live`. It has a separate signing identity from other NADMO apps. NEVER reuse or rotate a key unintentionally. A first transition from previously installed ephemeral debug APK to this stable signature can require reinstalling *once*.

## Owner-only secure setup

1. On a trusted Windows PC, install Java JDK (includes `keytool`). Generate a 3072-bit RSA signing key as a PKCS12 keystore, keeping it outside GitHub repositories:
   ```powershell
   keytool -genkeypair -v -storetype PKCS12 -keystore "$env:USERPROFILE\NADMO-LIVE-release.p12" -alias nadmo-live-release -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname "CN=NADMO LIVE, OU=Android, O=NADMO Studio, L=Bali, C=ID"
   ```
   Choose a strong password, save it separately in an encrypted password manager. The PKCS12 keystore usually uses the same password for the store and its key. Do **not** paste credentials in ChatGPT, screenshots, email, issues, or files committed to GitHub.
2. Create an encrypted offline backup of the keystore and retain its recovery password securely. The same keystore is needed for every later update.
3. Open GitHub repository **Settings → Secrets and variables → Actions → New repository secret** in `ardarawk-cloud/ACC-Builder-Apk`; add:
   - `NADMO_LIVE_KEYSTORE_B64`: the Base64 encoding of your PKCS12 keystore file, produced locally.
   - `NADMO_LIVE_RELEASE_STORE_PASSWORD`: its store password.
   - `NADMO_LIVE_RELEASE_KEY_ALIAS`: `nadmo-live-release`.
   - `NADMO_LIVE_RELEASE_KEY_PASSWORD`: its key password.
   Base64 is not encryption. Keep the Base64 bytes as secret credentials.
4. Request signed release by pushing a new text request file under `nadmo-live/android/release-requests/` on the dedicated branch `feat/nadmo-live-android-beta-20261009`. GitHub Actions workflow `nadmo-live-signed-release.yml` will run ONLY if all 4 secrets are present and signing verification passes. The workflow also supports manual dispatch if GitHub shows it.
5. Compare the signed release certificate SHA-256 fingerprint with the previous stable release before distributing any later updates, keep package ID unchanged, and increment Android `versionCode`.
6. After owner approval, test one device for first signed install then upgrade in-place on a second build; do not require repeated reinstallations for web UI changes.

## Delivery rules
- Never export signing passwords or private keystore to workflow logs, public artifacts, issues, chats or committed files. CI releases upload signed APK only.
- If keystore is lost, or future certificate differs, Android will normally reject updates over the installed package.
- Existing browser beta is live and independently functional. It is NOT a fully released production Android app.
- Confirm real device media, 18+ age verification, creator identity, abuse reporting and payment compliance before public rollout.
