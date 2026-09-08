# Cloud V2 Status

- Client + Owner Android compile/build: PASS (`run 34194128929`)
- Client applicationId: `com.baliweddingdj.app`
- Owner applicationId: `com.baliweddingdj.owner`
- Owner cloud Booking Inbox sync code: INTEGRATED / COMPILE PASS
- Backend `api` + secure `ownerApi` dependency/export/syntax QC: PASS (`run 34193872518`)
- Direct Firestore client access: DENIED by rules
- Firebase/Cloud runtime activation: PENDING external Firebase project provisioning, Functions/rules deployment, and CI secret injection
- Required Android Firebase registrations: Client + Owner in the same Firebase project
- Required build values include separate Client and Owner Firebase App IDs

Do not label Client → Cloud → Owner delivery, notifications, or Cloud V2 as LIVE/VERIFIED until the activation checklist passes on two physical devices.
