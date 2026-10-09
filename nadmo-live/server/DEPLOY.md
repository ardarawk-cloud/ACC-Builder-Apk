# NADMO LIVE Beta — hosting plan

Current state: no production backend has been deployed. The v0.3 APK can show its UI and local camera preview offline. Live rooms need this signaling service online.

Suggested free beta host: Render free Web Service (Node.js) in the owner's approved workspace, with:
- Repo: https://github.com/ardarawk-cloud/ACC-Builder-Apk
- Branch: feat/nadmo-live-android-beta-20261009
- Build command: npm install --prefix nadmo-live/server --no-audit --no-fund
- Start command: node nadmo-live/server/index.js
- Plan: free
- Region: singapore
- Do not create without explicit workspace confirmation.

After deployment, copy its HTTPS URL into NADMO LIVE > Settings > Alamat backend HTTPS. The APK will access the server without another Android build.

Safety/limitations:
- WebRTC P2P mesh: maximum 4 viewers per host, STUN-only; carrier-grade NAT may require a TURN relay not provisioned in beta.
- Room data is stored in volatile memory; sleep/restarts erase all rooms.
- Password rooms provide a basic invitation lock, **not** a paid room.
- No payments, identity proofing, wallet, earnings, royalty coverage, or moderation team yet. Do not open beta to the public.
- Do not use for production until identity checks, authentication, access tokens, abuse report handling, and monitoring are in place.
