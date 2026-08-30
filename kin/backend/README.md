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

The Android/backend compatibility contract is locked in `API_CONTRACT_V1.md`.

## Security baseline

- Passwords hashed with Argon2 through `pwdlib`.
- Short-lived JWT access tokens.
- Random refresh tokens are stored only as SHA-256 hashes in the database.
- Refresh tokens rotate on refresh and can be revoked on logout.
- Production refuses the default development JWT secret.
- Production refuses SQLite; Phase 1A cloud accounts require PostgreSQL persistence.
- Android clients must use HTTPS in production.

## Database

Development defaults to SQLite. Production uses PostgreSQL through `KIN_DATABASE_URL`.

The backend accepts common managed-database URL forms (`postgres://...` and `postgresql://...`) and normalizes them to SQLAlchemy's psycopg 3 dialect. The production image includes the psycopg binary driver.

For Phase 1A alpha, SQLAlchemy creates the current schema at service startup. Add explicit migrations before schema evolution becomes a production requirement.

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

## Cloud environment

Required production environment values:

```text
KIN_ENVIRONMENT=production
KIN_DATABASE_URL=postgresql://USER:PASSWORD@HOST:PORT/DATABASE
KIN_JWT_SECRET=<long-random-secret>
KIN_ACCESS_TOKEN_MINUTES=30
KIN_REFRESH_TOKEN_DAYS=30
```

The cloud platform should terminate HTTPS and route traffic to the container's port `8000`.

## Deployment boundary

This folder is intentionally separate from `native/kin/`. `ACC-Builder-Apk` remains the APK build factory; `kin/backend/` is deployable as its own cloud service/container.

## Phase 1A completion gate

Phase 1A is complete only when:

1. CI tests pass.
2. The backend is deployed behind HTTPS with persistent PostgreSQL.
3. Android builds are configured with the deployed `KIN_API_BASE_URL`.
4. Register/login/token restore and `/v1/me` sync work against that deployment.
5. Two devices can log into the same cloud account and receive the same profile.
