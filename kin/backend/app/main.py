from datetime import datetime, timezone
from uuid import uuid4

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException, Query, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from .database import Base, engine, get_db
from .models import Block, ChatMessage, Friendship, Post, PostAudience, RefreshSession, User
from .schemas import (
    AuthResponse,
    FriendRequestOut,
    FriendRequestsOut,
    LoginRequest,
    LogoutRequest,
    MessageCreate,
    MessageOut,
    PersonProfileOut,
    PostCreate,
    PostOut,
    PostPatch,
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
app = FastAPI(title="KIN API", version="1.2.0")


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


def are_friends(db: Session, a: int, b: int) -> bool:
    friendship = friendship_between(db, a, b)
    return friendship is not None and friendship.status == "accepted"


def block_between(db: Session, a: int, b: int) -> Block | None:
    return db.scalar(
        select(Block).where(
            or_(
                (Block.blocker_id == a) & (Block.blocked_id == b),
                (Block.blocker_id == b) & (Block.blocked_id == a),
            )
        )
    )


def viewer_block(db: Session, viewer_id: int, other_id: int) -> Block | None:
    return db.scalar(select(Block).where(Block.blocker_id == viewer_id, Block.blocked_id == other_id))


def relationship_state(db: Session, friendship: Friendship | None, viewer_id: int, other_id: int) -> str:
    own_block = viewer_block(db, viewer_id, other_id)
    if own_block is not None:
        return "blocked"
    if viewer_block(db, other_id, viewer_id) is not None:
        return "unavailable"
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
        relationship=relationship_state(db, friendship_between(db, viewer_id, user.id), viewer_id, user.id),
    )


def other_user(db: Session, friendship: Friendship, viewer_id: int) -> User | None:
    other_id = friendship.user_high_id if friendship.user_low_id == viewer_id else friendship.user_low_id
    return db.get(User, other_id)


def find_active_person(db: Session, username: str) -> User | None:
    normalized = normalize_username(username)
    return db.scalar(select(User).where(User.username == normalized, User.is_active.is_(True)))


def require_person(db: Session, username: str, viewer: User) -> User:
    target = find_active_person(db, username)
    if target is None or target.id == viewer.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="person not found")
    return target


def require_connected_person(db: Session, username: str, viewer: User) -> User:
    target = require_person(db, username, viewer)
    if block_between(db, viewer.id, target.id) is not None or not are_friends(db, viewer.id, target.id):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="KIN connection required")
    return target


def post_to_out(db: Session, post: Post) -> PostOut:
    author = db.get(User, post.author_id)
    if author is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="post author unavailable")
    return PostOut(
        id=post.id,
        author=PublicUserOut.model_validate(author),
        text=post.text,
        audience=post.audience,
        feeling=post.feeling,
        listening=post.listening,
        location=post.location,
        with_people=post.with_people,
        created_at=post.created_at,
        updated_at=post.updated_at,
    )


def message_to_out(db: Session, message: ChatMessage) -> MessageOut:
    sender = db.get(User, message.sender_id)
    recipient = db.get(User, message.recipient_id)
    if sender is None or recipient is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="message participant unavailable")
    return MessageOut(
        id=message.id,
        sender=PublicUserOut.model_validate(sender),
        recipient=PublicUserOut.model_validate(recipient),
        text=message.text,
        created_at=message.created_at,
    )


def replace_post_audience(db: Session, post: Post, viewer: User, allowed_user_ids: list[int]) -> None:
    existing = db.scalars(select(PostAudience).where(PostAudience.post_id == post.id)).all()
    for row in existing:
        db.delete(row)
    if post.audience != "selected":
        return
    for target_id in sorted(set(allowed_user_ids)):
        if target_id == viewer.id:
            continue
        target = db.get(User, target_id)
        if target is None or not target.is_active:
            continue
        if block_between(db, viewer.id, target_id) is not None:
            continue
        if not are_friends(db, viewer.id, target_id):
            continue
        db.add(PostAudience(post_id=post.id, user_id=target_id))


def post_visible_to(db: Session, post: Post, viewer: User) -> bool:
    if post.author_id == viewer.id:
        return True
    if block_between(db, viewer.id, post.author_id) is not None:
        return False
    if not are_friends(db, viewer.id, post.author_id):
        return False
    if post.audience in ("public", "friends"):
        return True
    if post.audience == "selected":
        return db.scalar(
            select(PostAudience).where(PostAudience.post_id == post.id, PostAudience.user_id == viewer.id)
        ) is not None
    return False


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
def patch_me(payload: ProfilePatch, user: User = Depends(current_user), db: Session = Depends(get_db)) -> User:
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
    candidates = db.scalars(
        select(User)
        .where(User.is_active.is_(True), User.id != user.id, User.username.ilike(f"%{term}%"))
        .order_by(User.username.asc())
        .limit(50)
    ).all()
    visible = [candidate for candidate in candidates if block_between(db, user.id, candidate.id) is None]
    return [public_profile(db, candidate, user.id) for candidate in visible[:20]]


@app.get("/v1/people/{username}", response_model=PersonProfileOut)
def get_person(username: str, user: User = Depends(current_user), db: Session = Depends(get_db)) -> PersonProfileOut:
    candidate = require_person(db, username, user)
    if block_between(db, user.id, candidate.id) is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="person not found")
    return public_profile(db, candidate, user.id)


@app.post("/v1/friend-requests/{username}", response_model=PersonProfileOut, status_code=status.HTTP_201_CREATED)
def send_friend_request(username: str, user: User = Depends(current_user), db: Session = Depends(get_db)) -> PersonProfileOut:
    target = require_person(db, username, user)
    if block_between(db, user.id, target.id) is not None:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="connection unavailable")
    existing = friendship_between(db, user.id, target.id)
    if existing is not None:
        if existing.status == "accepted":
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="already friends")
        if existing.status == "pending" and existing.requested_by_id == user.id:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="friend request already sent")
        if existing.status == "pending":
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="this person already sent you a friend request")
    low_id, high_id = pair_ids(user.id, target.id)
    friendship = Friendship(user_low_id=low_id, user_high_id=high_id, requested_by_id=user.id, status="pending")
    db.add(friendship)
    db.commit()
    db.refresh(friendship)
    return public_profile(db, target, user.id)


@app.get("/v1/friend-requests", response_model=FriendRequestsOut)
def list_friend_requests(user: User = Depends(current_user), db: Session = Depends(get_db)) -> FriendRequestsOut:
    rows = db.scalars(
        select(Friendship)
        .where(Friendship.status == "pending", or_(Friendship.user_low_id == user.id, Friendship.user_high_id == user.id))
        .order_by(Friendship.created_at.asc())
    ).all()
    incoming: list[FriendRequestOut] = []
    outgoing: list[FriendRequestOut] = []
    for friendship in rows:
        counterpart = other_user(db, friendship, user.id)
        if counterpart is None or not counterpart.is_active or block_between(db, user.id, counterpart.id) is not None:
            continue
        item = FriendRequestOut(id=friendship.id, user=PublicUserOut.model_validate(counterpart), created_at=friendship.created_at)
        if friendship.requested_by_id == user.id:
            outgoing.append(item)
        else:
            incoming.append(item)
    return FriendRequestsOut(incoming=incoming, outgoing=outgoing)


@app.post("/v1/friend-requests/{request_id}/accept", response_model=PersonProfileOut)
def accept_friend_request(request_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)) -> PersonProfileOut:
    friendship = db.get(Friendship, request_id)
    if friendship is None or friendship.status != "pending" or user.id not in (friendship.user_low_id, friendship.user_high_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="friend request not found")
    if friendship.requested_by_id == user.id:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="you cannot accept your own friend request")
    counterpart = other_user(db, friendship, user.id)
    if counterpart is None or not counterpart.is_active or block_between(db, user.id, counterpart.id) is not None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="person not found")
    friendship.status = "accepted"
    db.add(friendship)
    db.commit()
    return public_profile(db, counterpart, user.id)


@app.delete("/v1/friend-requests/{request_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_friend_request(request_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)) -> None:
    friendship = db.get(Friendship, request_id)
    if friendship is None or friendship.status != "pending" or user.id not in (friendship.user_low_id, friendship.user_high_id):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="friend request not found")
    db.delete(friendship)
    db.commit()


@app.get("/v1/connections", response_model=list[PublicUserOut])
def list_connections(user: User = Depends(current_user), db: Session = Depends(get_db)) -> list[PublicUserOut]:
    rows = db.scalars(
        select(Friendship).where(
            Friendship.status == "accepted",
            or_(Friendship.user_low_id == user.id, Friendship.user_high_id == user.id),
        )
    ).all()
    people: list[User] = []
    for friendship in rows:
        counterpart = other_user(db, friendship, user.id)
        if counterpart is not None and counterpart.is_active and block_between(db, user.id, counterpart.id) is None:
            people.append(counterpart)
    people.sort(key=lambda candidate: (candidate.display_name.lower(), candidate.username))
    return [PublicUserOut.model_validate(candidate) for candidate in people]


@app.delete("/v1/connections/{username}", status_code=status.HTTP_204_NO_CONTENT)
def remove_connection(username: str, user: User = Depends(current_user), db: Session = Depends(get_db)) -> None:
    target = require_person(db, username, user)
    friendship = friendship_between(db, user.id, target.id)
    if friendship is None or friendship.status != "accepted":
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="connection not found")
    db.delete(friendship)
    db.commit()


@app.get("/v1/blocks", response_model=list[PublicUserOut])
def list_blocks(user: User = Depends(current_user), db: Session = Depends(get_db)) -> list[PublicUserOut]:
    rows = db.scalars(select(Block).where(Block.blocker_id == user.id).order_by(Block.created_at.desc())).all()
    people = [db.get(User, row.blocked_id) for row in rows]
    return [PublicUserOut.model_validate(person) for person in people if person is not None and person.is_active]


@app.post("/v1/blocks/{username}", response_model=PersonProfileOut)
def block_person(username: str, user: User = Depends(current_user), db: Session = Depends(get_db)) -> PersonProfileOut:
    target = require_person(db, username, user)
    existing = viewer_block(db, user.id, target.id)
    friendship = friendship_between(db, user.id, target.id)
    if friendship is not None:
        db.delete(friendship)
    if existing is None:
        db.add(Block(blocker_id=user.id, blocked_id=target.id))
    db.commit()
    return public_profile(db, target, user.id)


@app.delete("/v1/blocks/{username}", status_code=status.HTTP_204_NO_CONTENT)
def unblock_person(username: str, user: User = Depends(current_user), db: Session = Depends(get_db)) -> None:
    target = require_person(db, username, user)
    row = viewer_block(db, user.id, target.id)
    if row is not None:
        db.delete(row)
        db.commit()


@app.post("/v1/posts", response_model=PostOut, status_code=status.HTTP_201_CREATED)
def create_post(payload: PostCreate, user: User = Depends(current_user), db: Session = Depends(get_db)) -> PostOut:
    if not any([payload.text.strip(), payload.feeling, payload.listening, payload.location, payload.with_people]):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="post cannot be empty")
    now = datetime.now(timezone.utc)
    post = Post(
        id=str(uuid4()),
        author_id=user.id,
        text=payload.text.strip(),
        audience=payload.audience,
        feeling=payload.feeling.strip() if payload.feeling else None,
        listening=payload.listening.strip() if payload.listening else None,
        location=payload.location.strip() if payload.location else None,
        with_people=payload.with_people.strip() if payload.with_people else None,
        created_at=now,
        updated_at=now,
    )
    db.add(post)
    db.flush()
    replace_post_audience(db, post, user, payload.allowed_user_ids)
    db.commit()
    db.refresh(post)
    return post_to_out(db, post)


@app.get("/v1/feed", response_model=list[PostOut])
def feed(user: User = Depends(current_user), db: Session = Depends(get_db)) -> list[PostOut]:
    rows = db.scalars(select(Post).order_by(Post.created_at.desc()).limit(250)).all()
    visible = [post for post in rows if post_visible_to(db, post, user)]
    return [post_to_out(db, post) for post in visible[:100]]


@app.patch("/v1/posts/{post_id}", response_model=PostOut)
def edit_post(post_id: str, payload: PostPatch, user: User = Depends(current_user), db: Session = Depends(get_db)) -> PostOut:
    post = db.get(Post, post_id)
    if post is None or post.author_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="post not found")
    if payload.text is not None:
        post.text = payload.text.strip()
    if payload.audience is not None:
        post.audience = payload.audience
    if "feeling" in payload.model_fields_set:
        post.feeling = payload.feeling.strip() if payload.feeling else None
    if "listening" in payload.model_fields_set:
        post.listening = payload.listening.strip() if payload.listening else None
    if "location" in payload.model_fields_set:
        post.location = payload.location.strip() if payload.location else None
    if "with_people" in payload.model_fields_set:
        post.with_people = payload.with_people.strip() if payload.with_people else None
    if not any([post.text, post.feeling, post.listening, post.location, post.with_people]):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="post cannot be empty")
    if payload.allowed_user_ids is not None or payload.audience is not None:
        replace_post_audience(db, post, user, payload.allowed_user_ids or [])
    post.updated_at = datetime.now(timezone.utc)
    db.add(post)
    db.commit()
    db.refresh(post)
    return post_to_out(db, post)


@app.delete("/v1/posts/{post_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_post(post_id: str, user: User = Depends(current_user), db: Session = Depends(get_db)) -> None:
    post = db.get(Post, post_id)
    if post is None or post.author_id != user.id:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="post not found")
    audiences = db.scalars(select(PostAudience).where(PostAudience.post_id == post.id)).all()
    for row in audiences:
        db.delete(row)
    db.delete(post)
    db.commit()


@app.get("/v1/chats/{username}/messages", response_model=list[MessageOut])
def chat_messages(
    username: str,
    limit: int = Query(default=100, ge=1, le=200),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> list[MessageOut]:
    target = require_connected_person(db, username, user)
    rows = db.scalars(
        select(ChatMessage)
        .where(
            or_(
                (ChatMessage.sender_id == user.id) & (ChatMessage.recipient_id == target.id),
                (ChatMessage.sender_id == target.id) & (ChatMessage.recipient_id == user.id),
            )
        )
        .order_by(ChatMessage.created_at.desc())
        .limit(limit)
    ).all()
    return [message_to_out(db, message) for message in reversed(rows)]


@app.post("/v1/chats/{username}/messages", response_model=MessageOut, status_code=status.HTTP_201_CREATED)
def send_message(
    username: str,
    payload: MessageCreate,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> MessageOut:
    target = require_connected_person(db, username, user)
    message = ChatMessage(id=str(uuid4()), sender_id=user.id, recipient_id=target.id, text=payload.text.strip())
    db.add(message)
    db.commit()
    db.refresh(message)
    return message_to_out(db, message)
