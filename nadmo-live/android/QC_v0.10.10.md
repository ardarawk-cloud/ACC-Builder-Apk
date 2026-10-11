# NADMO LIVE v0.10.10-beta — device QC handoff

Baseline: Android `a84628c` / v0.10.9-beta (versionCode 28); web PR #194 / `6ecf042`.

## Root cause and scope

`MainActivity.shouldInterceptRequest` substituted bundled `game-live-qa.html` for every main-frame HTTPS `/app/` request. `LOAD_NO_CACHE` could not fix this. That document lacked the deployed mobile HUD/host theme changes and still used a 360×640 camera compositor. Production HTML was verified byte-for-byte against web PR #194 on 2026-10-11.

v0.10.10 removes that substitution. Online `/app/` loads from `live.nadmo.id`; appassets still handles the local OFFGRID outage fallback. GAME status is refreshed after a trusted document finishes loading. The native GAME bridge and media implementations are preserved. The package stays `id.nadmo.live`, versionCode increases to 29, and release CI requires the existing pinned signing certificate.

This removes a confirmed stale-document path. It does **not** guarantee native portrait camera capture: normal LIVE still uses WebView `getUserMedia`, and portrait constraints are preferences. Landscape sources must use `contain` to retain the full frame; portrait sources use `cover`. Blank area around a landscape source is not a fullscreen portrait solution.

## Automated evidence and limits

- Document-source, OFFGRID and GAME lifecycle source contracts pass locally. The document-source regression rejects the v0.10.9 interception.
- Production browser regression passes with a stub native bridge and synthetic 1280×720 / 720×1280 tracks: original tracks retained, decoded dimensions preserved, appropriate fit, HUD visible, clean swipe and restoration.
- Native compilation, signature and package/version verification run in the signed GitHub workflow. Its logs/artifact are the evidence for those checks.
- Browser tests and source contracts do not establish physical Android sensor framing, MediaProjection/audio behavior, or successful installation upgrade.

## Required physical QC — still pending

1. Install over v0.10.9 without uninstalling or clearing data; verify retained login and app data.
2. Use one host stream on two viewer phones. Record host `MediaStreamTrack.getSettings()` width/height/frameRate and both decoded `videoWidth/videoHeight`, viewport, `object-fit`, active `live-immersive`/`watch-clean` classes, and host/viewer theme. Compare Electric and Noir using that same host.
3. Verify normal mobile HUD automatically appears, right swipe hides decoration/chat/controls, and left swipe restores them. Verify the page revision matches production.
4. Verify genuine portrait source framing and image detail. If Android returns landscape, record the actual source and retain the uncropped fallback; do not mark portrait capture fixed.
5. Check JOIN VOICE/CAMERA, independent mic/camera toggles, 2+ guest grid, host layout after guest descent, viewer join notifications and moderation on physical devices.
6. Check PC landscape/fullscreen and native GAME video, mic/internal audio and chat while the game is foregrounded.

Physical QC requires the owner's two devices; no physical device or ADB access is present in this environment. Keep these checks pending until device evidence is available.
