# KIN Backend

FastAPI backend foundation for KIN Phase 1A real accounts and cloud sessions.

## API

The locked Android/backend compatibility contract is documented in `API_CONTRACT_V1.md`.

Implemented endpoints:

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `POST /v1/auth/logout`
- `GET /v1/me`
- `PATCH /v1/me`
- `GET /health`

## Security/session baseline

- Argon2 password hashing
- JWT access tokens
- Opaque refresh tokens stored server-side only as hashes
- Refresh-token rotation
- Logout revocation
- Production refuses SQLite and the development JWT secret

## Database

Development/test may use SQLite. Production is PostgreSQL-only.

`KIN_DATABASE_URL` accepts managed `postgres://` or `postgresql://` URLs and normalizes them for psycopg 3.

## Cloud alpha

The first Phase 1A cloud-alpha deployment path is Render Blueprint infrastructure-as-code at the repository root: `render.yaml`.

See `DEPLOY_RENDER.md` for the branch-specific deploy flow, health verification, Android base-URL wiring order, and the free-database limitation.

Do not claim cloud-live until the exact Render deployment URL passes `/health` and the Android client has been verified against it.
