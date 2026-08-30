import hashlib
import secrets
from datetime import datetime, timedelta, timezone

import jwt
from pwdlib import PasswordHash

from .config import get_settings


password_hash = PasswordHash.recommended()


def hash_password(password: str) -> str:
    return password_hash.hash(password)


def verify_password(password: str, hashed: str) -> bool:
    return password_hash.verify(password, hashed)


def create_access_token(user_id: int) -> str:
    settings = get_settings()
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "type": "access",
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(minutes=settings.access_token_minutes)).timestamp()),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def decode_access_token(token: str) -> int:
    settings = get_settings()
    payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    if payload.get("type") != "access":
        raise jwt.InvalidTokenError("wrong token type")
    return int(payload["sub"])


def new_refresh_token() -> tuple[str, str, str, datetime]:
    settings = get_settings()
    raw = secrets.token_urlsafe(48)
    token_hash = hashlib.sha256(raw.encode("utf-8")).hexdigest()
    session_id = secrets.token_hex(16)
    expires_at = datetime.now(timezone.utc) + timedelta(days=settings.refresh_token_days)
    return raw, token_hash, session_id, expires_at


def hash_refresh_token(raw: str) -> str:
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()
