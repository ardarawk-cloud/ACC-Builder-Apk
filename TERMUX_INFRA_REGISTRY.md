# TERMUX INFRA REGISTRY

Status: ACTIVE AUTHORITY for shared Android/Termux infrastructure.

Purpose: prevent KAI TRADE X, MOSHI, KIN, or any future project chat from reusing or guessing another project's local port/session.

## Locked local ports

- `8000` — KAI TRADE X
- `8010` — MOSHI
- `8020` — KIN

These ports are reserved. Do not reassign one project's port to another project without an explicit registry revision.

## Locked tmux sessions

- `ktx` — KAI TRADE X
- `moshi` — MOSHI
- `kin` — KIN

Tunnel sessions must remain project-specific as well (for example `kin-tunnel`; MOSHI tunnel must not reuse it).

## Known source boundaries

### KAI TRADE X
- Termux project: `~/KAI-TRADE-X`
- Local API: `127.0.0.1:8000`

### MOSHI
- Termux clone: `~/MOSHI-SERVER`
- Ubuntu/proot path: `/root/MOSHI-SERVER`
- Canonical local backend port: `127.0.0.1:8010`
- Historical start path: `projects/moshi/scripts/alpha-smoke-termux.sh`

### KIN
- Termux source bind: `~/kin-source` -> `/root/kin-source`
- Canonical local backend port: `127.0.0.1:8020`
- Backend directory: `/root/kin-source/kin/backend`

## Incident note — 2026-08-31

KIN was observed running on port `8010`, which conflicts with MOSHI's pre-existing port reservation. This is configuration drift, not a registry revision. The repair direction is:

1. Preserve KIN data/source.
2. Move KIN backend and its tunnel to `8020`.
3. Restore MOSHI backend to `8010`.
4. Never point a KIN tunnel at MOSHI's `8010` port.
5. Any APK base URL must be rebuilt only after the correct KIN tunnel to `8020` is verified.

## Operating rule

Before issuing Termux commands for KAI TRADE X, MOSHI, or KIN, read this registry first. A process listing alone is not sufficient authority for project identity; source path + tmux session + reserved port must agree.
