# NADMO LIVE GAME crash forensic, verified source defect (12 October 2026)

## Owner symptom
GAME broadcast worked in an earlier beta, then regressed around native floating privacy/pause controls, duration and End Live popup additions. Later beta versions could create a room but would stop when host opened Mobile Legends/Roblox, returning SERVICE_INTERRUPTED; RADIO uses the same native screen capture and was similarly broken. Hundreds of green CSS/JS/room synthetic tests did NOT prove Android :game process safety.

## Git forensic
- Last Android screen capture before privacy refactor: commit `3d1c2f9bc8a` (2026-10-12 00:38 UTC, already added privacy), clean original: `a84628c1d68` (2026-10-11 14:56 UTC).
- Commit `07c77853f49` (2026-10-12 00:36 UTC) introduced `privacyBlackBuffer=JavaI420Buffer.allocate(...)` and `blackPrivacyFrame()`. This code reused a single JavaI420Buffer but returned `new VideoFrame(privacyBlackBuffer, ...)` every frame; caller called `safe.release()`. **VideoFrame constructor transfers ownership without retaining**. The first release would consume the cache's sole reference, invoking `JniCommon.nativeFreeByteBuffer`. Subsequent frames reused dangling YUV planes — native use-after-free/crash risk. This was the defect, not the duration clock itself.
- Commit `d067bf1ddb0` (2026-10-12 01:55 UTC) added one-hour rest reminder and elapsed duration *after* the refcount bug was introduced; timing correlation alone is insufficient.
- Earlier v0.10.11+ releases combined privacy logic, elapsed clock and popup updates without validating native frame ownership.
- Official WebRTC code: `VideoFrame(Buffer,rotation,timestamp)` says buffer ownership transferred, `VideoFrame.release()` calls `buffer.release()`; `JavaI420Buffer.allocate` allocates with `nativeAllocateByteBuffer` and release callback `nativeFreeByteBuffer`. Sources:
  - https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/VideoFrame.java
  - https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/JavaI420Buffer.java

## Surgical fix
- `GameCaptureService.blackPrivacyFrame()` now delegates to `PrivacyFrameOwnership.newRetainedFrame(cached,...)`; helper calls `cached.retain()` before constructing each VideoFrame. Cache keeps its own reference; each frame's `safe.release()` consumes **only its borrowed reference**. Cache frees its own reference on resize/shutdown after pending frame references drain.
- `PrivacyFrameOwnershipTest`: 50,000 recycled frames survive without refcount drop to zero and older sink frames remain valid while cache rotates dimensions. Baseline broken pattern is explicitly reproduced by test.
- RADIO preserves opaque frames, never streams external app pixels. Do NOT remove privacy mask to "fix" GAME.
- Android CI `https://github.com/ardarawk-cloud/ACC-Builder-Apk/actions/runs/38262157653`: JUnit policy and 50k frame tests, host-create+viewer-media E2E, signed APK with original cert and code38 all PASS.
- Owner test candidate v0.10.19-beta tag `nadmo-live-v0.10.19-beta`, package `id.nadmo.live`, versionCode38 (install-over v0.10.18 code37), SHA256 `d2d110062d29c7376dc84d4790d62f097080ba966e15000f5b69a5554955ca06`.

## What remains uncertain
Strong source-level root cause and simulation tests != proof of actual Android field stability, especially on low-memory Infinix/XOS devices. App/user OEM optimization or other defects can still terminate the :game process. Android 11+ process-exit categorization is available from previous diagnostic commit. One direct field check: install-over v0.10.19; GAME Start -> app choice -> BUKA GAME -> RESUME GAME, verify broadcaster still online >60 seconds and viewer gets real images/audio; RADIO similarly requires player permission/license. If it still fails, collect the precise status (e.g. REASON_LOW_MEMORY, REASON_CRASH_NATIVE, ANR) and prioritize that, do not blind-bump builds.

## Release discipline
Do not claim full GAME/RADIO correctness before owner tries field candidate. Do not issue numerous one-function signed builds. Preserve original signing cert and package; never require uninstall. Future work continues from this file rather than re-bisecting history.
