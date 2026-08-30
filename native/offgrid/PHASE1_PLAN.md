# OFFGRID Phase 1 — Direct Offline Chat

Target gate:
- BLE discovery from Phase 0 remains working.
- Tap a nearby OFFGRID node on one phone to connect.
- GATT duplex transport (writes + notifications).
- Exchange persistent OFFGRID device IDs during handshake.
- Ephemeral P-256 ECDH per connection.
- AES-GCM encrypted text payloads.
- Message ACK / Delivered state.
- No internet or central server required.

Security note: Phase 1 encrypts the BLE session end-to-end, but peer identity is not yet authenticated against an active MITM. QR/fingerprint verification is a later gate.
