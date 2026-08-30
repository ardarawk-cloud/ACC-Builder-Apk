# KIN API Contract v1

Status: LOCKED for Phase 1A client integration

This document is the compatibility contract between the KIN Android client and the Phase 1A backend. Breaking changes require a new API version; existing `/v1` behavior must remain compatible.

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

## Error body

Application errors use FastAPI's stable detail shape:

```json
{"detail":"human-readable reason"}
```

Validation errors may use FastAPI/Pydantic `422` validation detail arrays. Clients must not depend on exact validation-array wording.

## Phase 1A invariants

- Passwords are hashed server-side and never returned by the API.
- Access uses Bearer tokens.
- Refresh tokens are opaque, stored server-side only as hashes, and rotate on refresh.
- Room remains local cache; remote account state does not replace the existing UI/data architecture.
- Private Relationship Notes are not part of the account/profile API and must not be uploaded by Phase 1A.
- `/v1` is now compatibility-locked for the Android Phase 1A integration.
