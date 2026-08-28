# BALI WEDDING DJ — Play Console Release Gates

Do not move to public production until every applicable gate below is complete.

## Build identity

- [x] Stable package ID: `com.baliweddingdj.app`
- [x] Native Android foundation; WebView build is forbidden
- [x] Android 16 / target API 36 release path
- [x] Monotonic versionCode/versionName release workflow
- [x] Dedicated upload-key strategy
- [x] AAB production output workflow
- [ ] GitHub signing secrets installed
- [ ] Signed AAB successfully built and verified
- [ ] Google Play App Signing enabled in Play Console

## Store presence

- [x] English listing copy drafted
- [ ] Final app launcher/adaptive icon
- [ ] 512 x 512 Play icon
- [ ] Feature graphic
- [ ] Phone screenshots from production UI
- [ ] Support email confirmed
- [ ] Public support/privacy website confirmed

## Privacy and account policy

- [ ] Production backend behavior finalized
- [ ] Privacy Policy published at a public URL
- [ ] Data Safety declaration matches actual production data flows
- [ ] Payment receipt storage/retention policy documented
- [ ] Account deletion flow implemented before enabling customer account creation
- [ ] Public account-deletion request URL available if account creation is enabled
- [ ] Secure authentication verified; no admin/API secret stored in APK

## Booking/payment integrity

- [x] Booking request does not auto-confirm
- [x] Payment receipt upload does not auto-mark payment as paid
- [x] Admin verification is required before payment confirmation
- [x] Customer and admin states are separated in product flow
- [ ] Production server authorization rules verified
- [ ] Production payment-receipt access rules verified

## Release rollout

Recommended sequence:

1. Internal testing
2. Closed testing
3. Production

The currently installed debug APK is development-only. The first install from the Google Play testing/production track is the one-time migration to the permanent Play signing lineage. Future releases must keep the same package ID and signing lineage so Google Play can update the app normally without manual APK installation.
