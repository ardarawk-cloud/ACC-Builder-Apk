# OFFGRID Alpha v1 — Three-phone QC gate (2026-10-10)

Status: **SOURCE AUDIT / PHYSICAL RETEST PENDING**. Owner confirms OFFGRID was used with three Android phones and GROUP is present. This is NOT evidence that isolated A → B → C relay or every case below passed.

## Source / build boundary
- Authority: `native/offgrid/` on `build/offgrid-phase0-native`; review changes on `feat/offgrid-scan-stability-20261010`.
- App package remains `com.offgrid.mesh.dev` for Alpha; do not change app ID or developer signer while collecting comparable device tests.
- Source-only updates MUST NOT automatically publish an APK. Only build a milestone candidate after static review, then perform on-device QC.
- NADMO LIVE `id.nadmo.live` remains a separate app that launches the installed OFFGRID app. It does not yet embed the BLE/group engine.

## Setup
- Label three devices A, B, C; record Android version, Bluetooth chipset/model, OFFGRID APK version/code and signer fingerprint.
- Enable Bluetooth permissions on each; switch Wi-Fi and mobile data OFF, leave Bluetooth ON.
- Use identical GROUP name/code on A, B and C. Record distinct device IDs/fingerprints.
- For the actual relay test, **verify A and C cannot discover or connect directly**; B must be able to reach each in turn. Simply placing three phones together does not test multi-hop relay.

## Required manual cases (record PASS / FAIL / BLOCKED for each)
1. Discovery: each device can discover another, stop and restart scan, and recover after Bluetooth permission/adapter interruptions.
2. Direct chat A ↔ B: message delivery, ACK, identity fingerprint/safety code, stable reconnect.
3. GROUP: same name/code joins on all devices; group message is readable on authorized receivers, stored locally after app restart.
4. Queue: with no reachable carrier, send GROUP message on A; status shows queued.
5. True A → B → C relay: A sends; B receives encrypted relay envelope; move B's connection to C **without letting A connect to C**; C receives exactly one readable copy.
6. Deduplication: repeat handoff/reconnect; C never shows the same packet twice, and hop/expiry limits are respected.
7. Switching and reconnect: B switches peers repeatedly; manual direct chat A ↔ B retains history, without stale or duplicated entries.
8. Scan failure/retry: after scan failure, SCAN can start again; Nearby controls remain responsive during many advertisements.
9. Background and battery: measure foreground scan and relay on each Android version; do not claim guaranteed background relay if OS suspends/kills the process.
10. OFFGRID local APK sharing and installation on a device with internet disabled; verify the receiver explicitly approves installation.
11. NADMO LIVE launcher: with internet disabled, open offline fallback, press OFFGRID → BUKA OFFGRID, confirm the installed `com.offgrid.mesh.dev` launches. If not installed, the UI should clearly explain installation is required.
12. Regression: existing streaming, GO LIVE layout, SOSIAL, PESAN, ME, accounts, and payment-disabled safety state are unchanged.

## Evidence required
For each device/test: date, devices, build commit / APK hash, steps, screenshots or logs, observed outcome, and any crash/ANR. Record relay hop sequence and packet ID in diagnostics where available. Do not record group secret/code in public logs.

**Current results:** Not executed in this source audit. Owner's previous three-phone success is acknowledged, but cannot be substituted for test-case PASS evidence.
