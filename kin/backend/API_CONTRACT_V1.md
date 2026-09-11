# KIN API Contract v1

Status: LOCKED for Phase 1A client integration; Phase 1B People/Connections extension is additive

This document is the compatibility contract between the KIN Android client and backend. Breaking changes require a new API version; existing `/v1` behavior must remain compatible.

## Transport

- Production/development remote clients use HTTPS only.
- JSON request/response bodies use `application/json` and snake_case field names.
- Android keeps Room/DataStore as the local cache/session UI boundary; the remote API is the account source of truth when `KIN_API_BASE_URL` is configured.
- Broad notification access and WebView are outside this contract and remain forbidden for KIN.

## User object

```json
{
  "id": 1,
  "email": "arda@example.com",
  "username": "ardarawk",
  "display_name": "Arda",
  "bio": "",
  "skin_id": "kin-original"
}
```

`username` and `email` are unique. Usernames are normalized to lowercase and may contain letters, numbers, dots, and underscores.

## Public user object

People/Connections endpoints never expose email or authentication data:

```json
{
  "id": 2,
  "username": "nadia_kin",
  "display_name": "Nadia",
  "bio": "",
  "skin_id": "kin-original"
}
```

A person profile adds one viewer-relative `relationship` value: `none`, `outgoing_pending`, `incoming_pending`, or `friends`.

## Auth response

Register, login, and refresh return:

```json
{
  "access_token": "<jwt>",
  "refresh_token": "<opaque-token>",
  "token_type": "bearer",
  "user": { "...": "User object" }
}
```

The Android client stores access/refresh tokens encrypted with Android Keystore AES-GCM. Tokens must never be written to Room profile tables or normal logs.

## Endpoints

### `GET /health`

Success: `200`

```json
{"status":"ok","service":"kin-api"}
```

### `POST /v1/auth/register`

Request:

```json
{
  "email": "arda@example.com",
  "username": "ardarawk",
  "display_name": "Arda",
  "password": "minimum-8-characters"
}
```

Success: `201` with Auth response.

Conflict: `409` when email or username is already in use.

### `POST /v1/auth/login`

Request:

```json
{
  "identity": "arda@example.com-or-username",
  "password": "account-password"
}
```

`identity` accepts email, username, or username prefixed with `@`.

Success: `200` with Auth response.

Invalid credentials: `401`. Disabled account: `403`.

### `POST /v1/auth/refresh`

Request:

```json
{"refresh_token":"<opaque-token>"}
```

Success: `200` with a new Auth response. Refresh tokens rotate: the supplied refresh session is revoked before a new refresh token is issued. A rotated/revoked/expired token returns `401`.

### `POST /v1/auth/logout`

Request:

```json
{"refresh_token":"<opaque-token>"}
```

Success: `204` with no response body. If the refresh session exists, it is revoked. Android clears its encrypted tokens and local signed-in flag even if the network is unavailable, so logout always removes local account access.

### `GET /v1/me`

Header:

`Authorization: Bearer <access_token>`

Success: `200` with User object.

Missing, invalid, expired, inactive-account access: `401`.

Android startup behavior:

1. Try the encrypted access token against `/v1/me`.
2. On `401`, rotate with `/v1/auth/refresh` and cache the returned User object.
3. If refresh is rejected, clear tokens and local signed-in state.
4. If the network is temporarily unavailable but a previously verified local session exists, retain the Room/DataStore cache for offline-first use and retry synchronization later.

### `PATCH /v1/me`

Header:

`Authorization: Bearer <access_token>`

Any subset of:

```json
{
  "display_name": "Arda",
  "bio": "DJ, Gamer, Developer",
  "skin_id": "midnight"
}
```

Success: `200` with the updated User object. Android writes the returned User object into Room after server success.

## Phase 1B People / Connections extension

All endpoints below require `Authorization: Bearer <access_token>` and are additive to Phase 1A.

### `GET /v1/people/search?q=<username-fragment>`

Searches active accounts by username, excludes the caller, and returns at most 20 person profiles. `q` may start with `@`. Email is never returned.

### `GET /v1/people/{username}`

Returns one public person profile plus viewer-relative `relationship`. The caller cannot use this endpoint to open their own account profile.

### `POST /v1/friend-requests/{username}`

Creates a pending request. Success: `201` with the target person profile and `relationship = outgoing_pending`.

Conflicts return `409` when already friends, already requested, or when an incoming request from that person is already pending. Adding yourself returns `400`.

### `GET /v1/friend-requests`

Returns:

```json
{
  "incoming": [
    {"id": 10, "user": {"...": "Public user object"}, "created_at": "<iso-time>"}
  ],
  "outgoing": []
}
```

### `POST /v1/friend-requests/{request_id}/accept`

Only the recipient may accept a pending request. Success: `200` with the counterpart profile and `relationship = friends`.

### `DELETE /v1/friend-requests/{request_id}`

Either participant may remove a pending request: the recipient declines it or the sender cancels it. Success: `204`.

### `GET /v1/connections`

Returns accepted friends as public user objects, sorted by display name. Private local relationship metadata is not returned.

## Error body

Application errors use FastAPI's stable detail shape:

```json
{"detail":"human-readable reason"}
```

Validation errors may use FastAPI/Pydantic `422` validation detail arrays. Clients must not depend on exact validation-array wording.

## Invariants

- Passwords are hashed server-side and never returned by the API.
- Access uses Bearer tokens.
- Refresh tokens are opaque, stored server-side only as hashes, and rotate on refresh.
- Room remains local cache; remote account state does not replace the existing UI/data architecture.
- Private Relationship Notes remain device-owner-only and are never uploaded by People/Connections sync.
- Circle labels remain private/local in the first Phase 1B slice; remote Circle sync requires a later explicit contract extension.
- `/v1` compatibility is preserved; Phase 1B adds endpoints without breaking Phase 1A clients.
