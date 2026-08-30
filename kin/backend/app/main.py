from datetime import datetime, timezone

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from .database import Base, engine, get_db
from .models import Friendship, RefreshSession, User
from .schemas import (
    AuthResponse,
    FriendRequestOut,
    FriendRequestsOut,
    LoginRequest,
    LogoutRequest,
    PersonProfileOut,
    ProfilePatch,
    PublicUserOut,
    RefreshRequest,
    RegisterRequest,
    UserOut,
)
from .security import (
    create_access_token,
    decode_access_token,
    hash_password,
    hash_refresh_token,
    new_refresh_token,
    verify_password,
)

Base.metadata.create_all(bind=engine)
app = FastAPI(title="KIN API", version="1.1.0")


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


def normalize_username(value: str) -> str:
    return value.strip().lower().lstrip("@")


def pair_ids(a: int, b: int) -> tuple[int, int]:
    return (a, b) if a < b else (b, a)


def friendship_between(db: Session, a: int, b: int) -> Friendship | None:
    low_id, high_id = pair_ids(a, b)
    return db.scalar(
        select(Friendship).where(
            Friendship.user_low_id == low_id,
            Friendship.user_high_id == high_id,
        )
    )


def relationship_state(friendship: Friendship | None, viewer_id: int) -> str:
    if friendship is None:
        return "none"
    if friendship.status == "accepted":
        return "friends"
    if friendship.status == "pending":
        return "outgoing_pending" if friendship.requested_by_id == viewer_id else "incoming_pending"
    return "none"


def public_profile(db: Session, user: User, viewer_id: int) -> PersonProfileOut:
    return PersonProfileOut(
        id=user.id,
        username=user.username,
        display_name=user.display_name,
        bio=user.bio,
        skin_id=user.skin_id,
        relationship=relationship_state(friendship_between(db, viewer_id, user.id), viewer_id),
    )


def other_user(db: Session, friendship: Friendship, viewer_id: int) -> User | None:
    other_id = friendship.user_high_id if friendship.user_low_id == viewer_id else friendship.user_low_id
    return db.get(User, other_id)


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


@app.get("/v1/people/search", response_model=list[PersonProfileOut])
def search_people(
    q: str = Query(min_length=1, max_length=32),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> list[PersonProfileOut]:
    term = normalize_username(q)
    if not term:
        return []
    users = db.scalars(
        select(User)
        .where(
            User.is_active.is_(True),
            User.id != user.id,
            User.username.ilike(f"%{term}%"),
        )
        .order_by(User.username.asc())
        .limit(20)
    ).all()
    return [public_profile(db, candidate, user.id) for candidate in users]


@app.get("/v1/people/{username}", response_model=PersonProfileOut)
def get_person(
    username: str,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> PersonProfileOut:
    normalized = normalize_username(username)
    candidate = db.scalar(select(User).where(User.username == normalized, User.is_active.is_(True)))
    if candidate is None or candidate.id == user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="person not found")
    return public_profile(db, candidate, user.id)


@app.post(
    "/v1/friend-requests/{username}",
    response_model=PersonProfileOut,
    status_code=status.HTTP_201_CREATED,
)
def send_friend_request(
    username: str,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> PersonProfileOut:
    normalized = normalize_username(username)
    target = db.scalar(select(User).where(User.username == normalized, User.is_active.is_(True)))
    if target is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="person not found")
    if target.id == user.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="you cannot add yourself")

    existing = friendship_between(db, user.id, target.id)
    if existing is not None:
        if existing.status == "accepted":
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="already friends")
        if existing.status == "pending" and existing.requested_by_id == user.id:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="friend request already sent")
        if existing.status == "pending":
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="this person already sent you a friend request")

    low_id, high_id = pair_ids(user.id, target.id)
    friendship = Friendship(
        user_low_id=low_id,
        user_high_id=high_id,
        requested_by_id=user.id,
        status="pending",
    )
    db.add(friendship)
    db.commit()
    db.refresh(friendship)
    return public_profile(db, target, user.id)


@app.get("/v1/friend-requests", response_model=FriendRequestsOut)
def list_friend_requests(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> FriendRequestsOut:
    rows = db.scalars(
        select(Friendship)
        .where(
            Friendship.status == "pending",
            or_(Friendship.user_low_id == user.id, Friendship.user_high_id == user.id),
        )
        .order_by(Friendship.created_at.asc())
    ).all()

    incoming: list[FriendRequestOut] = []
    outgoing: list[FriendRequestOut] = []
    for friendship in rows:
        counterpart = other_user(db, friendship, user.id)
        if counterpart is None or not counterpart.is_active:
            continue
        item = FriendRequestOut(
            id=friendship.id,
            user=PublicUserOut.model_validate(counterpart),
            created_at=friendship.created_at,
        )
        if friendship.requested_by_id == user.id:
            outgoing.append(item)
        else:
            incoming.append(item)
    return FriendRequestsOut(incoming=incoming, outgoing=outgoing)


@app.post("/v1/friend-requests/{request_id}/accept", response_model=PersonProfileOut)
def accept_friend_request(
    request_id: int,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> PersonProfileOut:
    friendship = db.get(Friendship, request_id)
    if friendship is None or friendship.status != "pending":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="friend request not found")
    if user.id not in (friendship.user_low_id, friendship.user_high_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="friend request not found")
    if friendship.requested_by_id == user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="you cannot accept your own friend request")

    counterpart = other_user(db, friendship, user.id)
    if counterpart is None or not counterpart.is_active:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="person not found")
    friendship.status = "accepted"
    db.add(friendship)
    db.commit()
    db.refresh(friendship)
    return public_profile(db, counterpart, user.id)


@app.delete("/v1/friend-requests/{request_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_friend_request(
    request_id: int,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> None:
    friendship = db.get(Friendship, request_id)
    if friendship is None or friendship.status != "pending":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="friend request not found")
    if user.id not in (friendship.user_low_id, friendship.user_high_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="friend request not found")
    db.delete(friendship)
    db.commit()


@app.get("/v1/connections", response_model=list[PublicUserOut])
def list_connections(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> list[PublicUserOut]:
    rows = db.scalars(
        select(Friendship).where(
            Friendship.status == "accepted",
            or_(Friendship.user_low_id == user.id, Friendship.user_high_id == user.id),
        )
    ).all()
    people = []
    for friendship in rows:
        counterpart = other_user(db, friendship, user.id)
        if counterpart is not None and counterpart.is_active:
            people.append(counterpart)
    people.sort(key=lambda candidate: (candidate.display_name.lower(), candidate.username))
    return [PublicUserOut.model_validate(candidate) for candidate in people]
