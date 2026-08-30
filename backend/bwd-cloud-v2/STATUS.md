# Cloud V2 Status

- Android Cloud V2 compile: PASS (`run 33332046628`)
- Android APK SHA256: `fdfe707a52dac602923994f8bef3ee2f03b44e97170b3982b4fbb61b5bd65b76`
- Backend dependency + syntax QC: PASS (`run 33332058382`)
- Direct Firestore client access: DENIED by rules
- Firebase/Cloud runtime activation: PENDING external project provisioning and CI secret injection

Do not label Cloud V2 as production-live until the activation checklist runtime tests pass on two physical devices.
