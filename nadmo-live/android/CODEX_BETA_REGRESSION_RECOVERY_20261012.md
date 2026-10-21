# NADMO LIVE — CODEX P0/P1 RECOVERY HANDOFF (2026-10-12)

**Authority:** User's screenshots at 17:44–17:47 WITA are ground truth. Signed v0.10.15-beta (versionCode 34, package id.nadmo.live) is REGRESSED, not stable. Build/CI PASS did not mean the live path worked. Owner wants code repaired, not more explanations. Preserve original package/signing key, no uninstall.

**Repositories / branches:** Android `ardarawk-cloud/ACC-Builder-Apk`, `feat/nadmo-radio-open-music-camera-stage-20261012` (PR #57); web/Cloudflare `ardarawk-cloud/ACC-OS-X`, `feat/nadmo-live-unified-stage-pk-20261012` (PR #215). Native: `nadmo-live/android/app/src/main/java/id/nadmo/live/{MainActivity,GameCaptureService}.java`. Frontend: `nadmo-live-beta/public/app/index.html`. Backend: `nadmo-live-beta/worker.mjs`. Live beta: `https://live.nadmo.id/app/`, `wss://live.nadmo.id/ws`.

## Actual regressions
1. **GAME P0:** starts, Android live duration gets to 00:00:32 but `BUKA GAME` remains disabled as `MENYAMBUNG...`. No usable broadcast; timer must not imply acknowledged live.
2. **RADIO P0:** unable to use/open music and broadcast. Follow the same native ACK/media pipeline as GAME, keep full CAMERA-size viewer and privacy-masked screen frames; audio capture only if Android/player allow it.
3. **VOICE P1:** viewer alone has correct full portrait; viewer after 1 voice guest splits 50/50 vertical; host displays a very large horizontal guest panel on full portrait. Host/viewer must show SAME full CAMERA-size Talk stage with compact voice-avatar overlays (not split columns). Video guest mode stays separately functional.
4. **UI P1:** remove code-room/share technical text; only clear working buttons `MULAI LIVE`, `BUKA GAME`/`OPEN MUSIC`, `AKHIRI LIVE` plus toggles/settings. Do not hide actionable errors.

## Root cause investigation starting points (NOT confirmed)
- `GameCaptureService.initCapture()` -> `ScreenCapturerAndroid.onCapturerStarted` -> `connectWhenCaptureStarted()` -> `connectSignaling(false)` -> WebSocket OPEN/create -> Cloudflare `created` ACK -> `GameCaptureService.handleMessage` -> `ACTION_STATUS` -> `MainActivity.notifyGameStatus` -> `window nadmo-game-status` -> `gameLaunchGame` button. Instrument each transition and add finite timeout/retry; avoid false elapsed LIVE. Anonymous WS smoke PASS is inadequate for authenticated Android WebView.
- `MainActivity.onResume/onPause` safety pausing, screen-permission callback, cookies/identity, background service `:game`, WebMessage and status events may race; test both signed-in and anonymous beta behavior without exposing tokens.
- Worker `stagePublic()`: guest `mode`, `camera`, `ready`; web `syncMobileStage()` selects `.mobile-stage-voice` vs `.mobile-stage-split`; CSS order/host-vs-viewer DOM/events/cached versions may produce mismatches. Assert both roles' real computed sizes after `stage-updated`, reconnect and guest exit.
- Maintain strict MediaProjection privacy: in RADIO opaque video frames must always replace screen frames *before WebRTC*; do not bypass DRM/audio-capture permissions or fabricate audio.
- Keep PK/multiguest/gift controls and hourly break reminder intact; avoid unrelated feature work.

## Implementation order / gate
(1) Repair native GAME create/ACK and `BUKA GAME`, no endless CONNECTING; verify *real Android flow* as far as available, not just server 101 handshake. (2) Repair RADIO and OPEN MUSIC with actual room and media. (3) Fix voice-only layout same for viewer+host using actual WebRTC two contexts plus guest. Test 320/360/390/430 mobile viewports and screenshots. (4) Make visible UI concise. (5) Run CI and full regression, then **one** signed owner-test beta versionCode **>34**, original certificate; install over without uninstall; no blind repeated releases.

**Definition of done:** Actual create/ACK + media and working Game launch (or bounded actionable error, never fake LIVE); Radio working where Android captures audio; full-camera Voice layout consistent for both roles; safety/privacy and earlier Camera/multiguest/chat/gift regressions pass; signed install-over candidate and evidence. Report exact commits/CI, states honestly: code fixed vs tested vs blocked vs hardware unverified.

**SCREENSHOTS:** Owner has four images in ChatGPT handoff ZIP `NADMO_LIVE_CODEX_HANDOFF_WITH_SCREENSHOTS_2026-10-12.zip`. Attach/extract it in Codex. Owner wants you to continue fixing priority problems within remaining session budget, *not* to stop after a spec, and to hand off exact state if interrupted.
