## Scope

Bali Wedding DJ only.

## Changes

- Local-first booking queue with HTTPS cloud sync.
- Firebase Cloud Messaging receiver and high-priority booking notification channel.
- Owner/admin device enrollment without embedding admin credentials in the APK.
- Firebase Functions booking API + Firestore persistence.
- FCM fan-out to enabled owner devices; stale tokens disabled.
- Firestore deny-all direct client rules.
- Isolated Android and backend QC workflows.

## Verified

- Android build run `33332046628`: SUCCESS.
- Backend QC run `33332058382`: SUCCESS.
- QC APK SHA256: `fdfe707a52dac602923994f8bef3ee2f03b44e97170b3982b4fbb61b5bd65b76`.

## Activation gate

The repository currently has no Bali Wedding DJ Firebase runtime configuration in the Cloud V2 build. Production push must not be claimed until the external Firebase project is provisioned, the five CI config values are injected, the backend is deployed, and the two-device runtime checklist passes.
