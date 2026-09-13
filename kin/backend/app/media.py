from __future__ import annotations

import mimetypes
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

import jwt
from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, status
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from .config import get_settings
from .database import get_db
from .models import Block, Friendship, MediaAsset, Post, PostAudience, PostMedia, User
from .schemas import PublicUserOut
from .security import create_media_token, decode_access_token, decode_media_token


router = APIRouter()


class MediaItemOut(BaseModel):
    id: str
    type: str
    content_type: str
    url: str


class FeedMediaOut(BaseModel):
    post_id: str
    media: list[MediaItemOut]


class MediaPostCreate(BaseModel):
    text: str = Field(default="", max_length=1000)
    audience: str = "friends"
    allowed_user_ids: list[int] = Field(default_factory=list, max_length=200)
    feeling: str | None = Field(default=None, max_length=80)
    listening: str | None = Field(default=None, max_length=500)
    location: str | None = Field(default=None, max_length=120)
    with_people: str | None = Field(default=None, max_length=240)
    media_ids: list[str] = Field(min_length=1, max_length=6)

    @field_validator("audience")
    @classmethod
    def validate_audience(cls, value: str) -> str:
        normalized = value.strip().lower().replace(" ", "_")
        aliases = {
            "circle": "selected",
            "selected": "selected",
            "friends": "friends",
            "public": "public",
            "only_me": "only_me",
        }
        if normalized not in aliases:
            raise ValueError("audience must be public, friends, selected, or only_me")
        return aliases[normalized]


class MediaPostOut(BaseModel):
    id: str
    author: PublicUserOut
    text: str
    audience: str
    feeling: str | None = None
    listening: str | None = None
    location: str | None = None
    with_people: str | None = None
    created_at: datetime
    updated_at: datetime
    media: list[MediaItemOut]


def current_media_user(
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
    row = friendship_between(db, a, b)
    return row is not None and row.status == "accepted"


def block_between(db: Session, a: int, b: int) -> Block | None:
    return db.scalar(
        select(Block).where(
            or_(
                (Block.blocker_id == a) & (Block.blocked_id == b),
                (Block.blocker_id == b) & (Block.blocked_id == a),
            )
        )
    )


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
            select(PostAudience).where(
                PostAudience.post_id == post.id,
                PostAudience.user_id == viewer.id,
            )
        ) is not None
    return False


def replace_post_audience(db: Session, post: Post, viewer: User, allowed_user_ids: list[int]) -> None:
    existing = db.scalars(select(PostAudience).where(PostAudience.post_id == post.id)).all()
    for row in existing:
        db.delete(row)
    if existing:
        db.flush()
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


def media_item_for(db: Session, asset: MediaAsset, viewer: User) -> MediaItemOut:
    token = create_media_token(viewer.id, asset.id)
    return MediaItemOut(
        id=asset.id,
        type=asset.media_type,
        content_type=asset.content_type,
        url=f"/v1/media/{asset.id}?token={token}",
    )


def media_for_post(db: Session, post_id: str, viewer: User) -> list[MediaItemOut]:
    rows = db.scalars(
        select(PostMedia)
        .where(PostMedia.post_id == post_id)
        .order_by(PostMedia.position.asc())
    ).all()
    items: list[MediaItemOut] = []
    for row in rows:
        asset = db.get(MediaAsset, row.media_id)
        if asset is not None:
            items.append(media_item_for(db, asset, viewer))
    return items


def validate_media_group(assets: list[MediaAsset]) -> None:
    if not assets:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="at least one media item is required")
    if len(assets) > 6:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="a post may contain up to 6 photos")
    videos = [asset for asset in assets if asset.media_type == "video"]
    if videos and (len(videos) != 1 or len(assets) != 1):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="video posts currently support one video at a time")


@router.post("/v1/media", response_model=MediaItemOut, status_code=status.HTTP_201_CREATED)
async def upload_media(
    request: Request,
    user: User = Depends(current_media_user),
    db: Session = Depends(get_db),
) -> MediaItemOut:
    content_type = request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
    if content_type.startswith("image/"):
        media_type = "image"
        max_bytes = 12 * 1024 * 1024
    elif content_type.startswith("video/"):
        media_type = "video"
        max_bytes = 40 * 1024 * 1024
    else:
        raise HTTPException(status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE, detail="KIN accepts image or video media")

    media_id = str(uuid4())
    extension = mimetypes.guess_extension(content_type) or (".jpg" if media_type == "image" else ".mp4")
    if len(extension) > 10 or not extension.startswith("."):
        extension = ".bin"
    settings = get_settings()
    media_dir = Path(settings.media_dir).expanduser().resolve()
    media_dir.mkdir(parents=True, exist_ok=True)
    file_name = f"{media_id}{extension}"
    final_path = media_dir / file_name
    temp_path = media_dir / f".{file_name}.part"

    total = 0
    try:
        with temp_path.open("wb") as handle:
            async for chunk in request.stream():
                if not chunk:
                    continue
                total += len(chunk)
                if total > max_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail="media file is too large for this KIN alpha",
                    )
                handle.write(chunk)
        if total <= 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="empty media upload")
        temp_path.replace(final_path)
    except Exception:
        temp_path.unlink(missing_ok=True)
        final_path.unlink(missing_ok=True)
        raise

    asset = MediaAsset(
        id=media_id,
        owner_id=user.id,
        media_type=media_type,
        content_type=content_type,
        file_name=file_name,
        size_bytes=total,
    )
    db.add(asset)
    db.commit()
    db.refresh(asset)
    return media_item_for(db, asset, user)


@router.post("/v1/media-posts", response_model=MediaPostOut, status_code=status.HTTP_201_CREATED)
def create_media_post(
    payload: MediaPostCreate,
    user: User = Depends(current_media_user),
    db: Session = Depends(get_db),
) -> MediaPostOut:
    unique_ids = list(dict.fromkeys(payload.media_ids))
    assets: list[MediaAsset] = []
    for media_id in unique_ids:
        asset = db.get(MediaAsset, media_id)
        if asset is None or asset.owner_id != user.id:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="uploaded media not found")
        assets.append(asset)
    validate_media_group(assets)

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
    for position, asset in enumerate(assets):
        db.add(PostMedia(post_id=post.id, media_id=asset.id, position=position))
    db.commit()
    db.refresh(post)
    return MediaPostOut(
        id=post.id,
        author=PublicUserOut.model_validate(user),
        text=post.text,
        audience=post.audience,
        feeling=post.feeling,
        listening=post.listening,
        location=post.location,
        with_people=post.with_people,
        created_at=post.created_at,
        updated_at=post.updated_at,
        media=media_for_post(db, post.id, user),
    )


@router.get("/v1/feed/media", response_model=list[FeedMediaOut])
def feed_media(
    user: User = Depends(current_media_user),
    db: Session = Depends(get_db),
) -> list[FeedMediaOut]:
    rows = db.scalars(select(Post).order_by(Post.created_at.desc()).limit(250)).all()
    output: list[FeedMediaOut] = []
    for post in rows:
        if not post_visible_to(db, post, user):
            continue
        media = media_for_post(db, post.id, user)
        if media:
            output.append(FeedMediaOut(post_id=post.id, media=media))
    return output


@router.get("/v1/posts/{post_id}/media", response_model=list[MediaItemOut])
def post_media(
    post_id: str,
    user: User = Depends(current_media_user),
    db: Session = Depends(get_db),
) -> list[MediaItemOut]:
    post = db.get(Post, post_id)
    if post is None or not post_visible_to(db, post, user):
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="post not found")
    return media_for_post(db, post.id, user)


@router.get("/v1/media/{media_id}")
def stream_media(
    media_id: str,
    token: str = Query(min_length=20),
    db: Session = Depends(get_db),
):
    try:
        viewer_id = decode_media_token(token, media_id)
    except (jwt.InvalidTokenError, ValueError):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid media token")
    viewer = db.get(User, viewer_id)
    asset = db.get(MediaAsset, media_id)
    if viewer is None or not viewer.is_active or asset is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="media not found")

    allowed = asset.owner_id == viewer.id
    if not allowed:
        links = db.scalars(select(PostMedia).where(PostMedia.media_id == media_id)).all()
        for link in links:
            post = db.get(Post, link.post_id)
            if post is not None and post_visible_to(db, post, viewer):
                allowed = True
                break
    if not allowed:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="media not found")

    media_path = Path(get_settings().media_dir).expanduser().resolve() / asset.file_name
    if not media_path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="media file missing")
    return FileResponse(
        path=media_path,
        media_type=asset.content_type,
        filename=None,
        headers={"Cache-Control": "private, max-age=600"},
    )
