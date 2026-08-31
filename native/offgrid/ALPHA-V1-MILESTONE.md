# OFFGRID Alpha v1 — Milestone Authority

Status: ACTIVE CANDIDATE / BUILD ONLY AT MILESTONE

## Product target
OFFGRID is an Android-first offline communication app. Alpha v1 focuses on phone-to-phone operation with no internet dependency during use.

## User-facing scope
- CHATS: encrypted BLE direct chat with local persistent history.
- GROUPS: encrypted group messages with persistent store-and-forward queue.
- NEARBY: discover and connect to OFFGRID phones nearby.
- SETTINGS: auto relay, local diagnostics, and offline APK sharing.
- Existing persistent device identity and verified-peer safety code remain preserved.

## Mesh behavior
- Messages are stored locally before/while forwarding.
- Per-message ID dedupe prevents loops.
- TTL and max-hop bounds prevent endless propagation.
- Relay sync occurs over the secure BLE transport.
- Auto relay may hand off a transport session from one nearby carrier to another while preserving the user's displayed direct chat context.
- Physical MESH PASS requires a real 3-device test where A and C cannot communicate directly and B carries the message.

## Build policy
- Source changes DO NOT automatically run GitHub Actions.
- OFFGRID workflow triggers only from `native/offgrid/.release-trigger` or explicit workflow dispatch.
- One build is produced per milestone candidate after source/static QC.
- Physical QC findings are collected as a batch before the next candidate build.

## Alpha v1 QC gate
1. Install/update on 3 Android phones.
2. No internet required after installation.
3. Nearby discovery works on all 3.
4. Direct A↔B chat sends/receives and preserves history.
5. Identity verification remains stable across reconnect.
6. Group config with same name/code is usable on all intended members.
7. Group message queues when no carrier is connected.
8. Automatic relay can move a queued packet A→B→C.
9. Duplicate packet does not produce duplicate visible message.
10. App switching/reconnect does not erase direct/group history.
11. Offline Share App exposes the currently installed APK through Android local sharing transports.
12. Visual/UI QC covers CHATS, GROUPS, NEARBY, SETTINGS and small-screen keyboard behavior.

## Explicit non-goals for Alpha v1
- LoRa/external hardware.
- Voice/video calls.
- Silent APK installation.
- Production/Play Store signing.
- Guaranteed background relay after Android kills the app; Alpha v1 relay authority is while OFFGRID remains running.
