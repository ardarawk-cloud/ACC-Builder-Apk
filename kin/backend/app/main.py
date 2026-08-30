from datetime import datetime, timezone

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from .database import Base, engine, get_db
from .models import RefreshSession, User
from .schemas import AuthResponse, LoginRequest, LogoutRequest, ProfilePatch, RefreshRequest, RegisterRequest, UserOut
from .security import (
    create_access_token,
    decode_access_token,
    hash_password,
    hash_refresh_token,
    new_refresh_token,
    verify_password,
)

Base.metadata.create_all(bind=engine)
app = FastAPI(title="KIN API", version="1.0.0")


def issue_auth(db: Session, user: User) -> AuthResponse:
    access = create_access_token(user.id)
    refresh_raw, refresh_hash, session_id, expires_at = new_refresh_token()
    db.add(
        RefreshSession(
            id=session_id,
            user_id=user.id,
            token_hash=refresh_hash,
            expires_at=expires_at,
        )
    )
    db.commit()
    return AuthResponse(access_token=access, refresh_token=refresh_raw, user=user)


def current_user(
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> User:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="missing bearer token")
    token = authorization.split(" ", 1)[1].strip()
    try:
        user_id = decode_access_token(token)
    except (jwt.InvalidTokenError, ValueError):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid access token")
    user = db.get(User, user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="account unavailable")
    return user


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "kin-api"}


@app.post("/v1/auth/register", response_model=AuthResponse, status_code=status.HTTP_201_CREATED)
def register(payload: RegisterRequest, db: Session = Depends(get_db)) -> AuthResponse:
    email = str(payload.email).strip().lower()
    username = payload.username
    existing = db.scalar(select(User).where(or_(User.email == email, User.username == username)))
    if existing is not None:
        field = "email" if existing.email == email else "username"
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=f"{field} already in use")
    user = User(
        email=email,
        username=username,
        display_name=payload.display_name.strip(),
        password_hash=hash_password(payload.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return issue_auth(db, user)


@app.post("/v1/auth/login", response_model=AuthResponse)
def login(payload: LoginRequest, db: Session = Depends(get_db)) -> AuthResponse:
    identity = payload.identity.strip().lower().lstrip("@")
    user = db.scalar(select(User).where(or_(User.email == identity, User.username == identity)))
    if user is None or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid credentials")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="account disabled")
    return issue_auth(db, user)


@app.post("/v1/auth/refresh", response_model=AuthResponse)
def refresh(payload: RefreshRequest, db: Session = Depends(get_db)) -> AuthResponse:
    token_hash = hash_refresh_token(payload.refresh_token)
    session = db.scalar(select(RefreshSession).where(RefreshSession.token_hash == token_hash))
    now = datetime.now(timezone.utc)
    if session is None or session.revoked or session.expires_at.replace(tzinfo=timezone.utc) <= now:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid refresh token")
    user = db.get(User, session.user_id)
    if user is None or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="account unavailable")
    session.revoked = True
    db.commit()
    return issue_auth(db, user)


@app.post("/v1/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(payload: LogoutRequest, db: Session = Depends(get_db)) -> None:
    token_hash = hash_refresh_token(payload.refresh_token)
    session = db.scalar(select(RefreshSession).where(RefreshSession.token_hash == token_hash))
    if session is not None:
        session.revoked = True
        db.commit()


@app.get("/v1/me", response_model=UserOut)
def me(user: User = Depends(current_user)) -> User:
    return user


@app.patch("/v1/me", response_model=UserOut)
def patch_me(
    payload: ProfilePatch,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> User:
    if payload.display_name is not None:
        user.display_name = payload.display_name.strip()
    if payload.bio is not None:
        user.bio = payload.bio.strip()
    if payload.skin_id is not None:
        user.skin_id = payload.skin_id.strip()
    db.add(user)
    db.commit()
    db.refresh(user)
    return user
