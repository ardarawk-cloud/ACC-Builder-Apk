# KIN Backend — Phase 1A

Real account and profile API for KIN.

## Current endpoints

- `GET /health`
- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `POST /v1/auth/logout`
- `GET /v1/me`
- `PATCH /v1/me`

## Security baseline

- Passwords hashed with Argon2 through `pwdlib`.
- Short-lived JWT access tokens.
- Random refresh tokens are stored only as SHA-256 hashes in the database.
- Refresh tokens rotate on refresh and can be revoked on logout.
- Production refuses the default development JWT secret.
- Android clients must use HTTPS in production.

## Database

Development defaults to SQLite. Production is designed to accept a SQLAlchemy database URL through `KIN_DATABASE_URL`, so PostgreSQL can replace SQLite without changing the API contract.

## Run locally

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env
uvicorn app.main:app --reload
```

## Test

```bash
pytest -q
```

## Deployment boundary

This folder is intentionally separate from `native/kin/`. `ACC-Builder-Apk` remains the APK build factory; `kin/backend/` can later be deployed as its own cloud service/container.

## Phase 1A completion gate

Phase 1A is complete only when:

1. CI tests pass.
2. The backend is deployed behind HTTPS.
3. Android auth repositories use the remote API.
4. Two devices can log into the same cloud account and receive the same profile.
