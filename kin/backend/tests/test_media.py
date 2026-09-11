import os

os.environ.setdefault("KIN_DATABASE_URL", "sqlite:///test-kin.db")
os.environ.setdefault("KIN_JWT_SECRET", "test-secret-not-for-production")
os.environ.setdefault("KIN_MEDIA_DIR", "test-kin-media")

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def register_user(email: str, username: str, display_name: str) -> dict:
    response = client.post(
        "/v1/auth/register",
        json={
            "email": email,
            "username": username,
            "display_name": display_name,
            "password": "strong-pass-123",
        },
    )
    assert response.status_code == 201, response.text
    return response.json()


def headers(auth: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {auth['access_token']}"}


def connect(sender: dict, receiver: dict, receiver_username: str) -> None:
    sent = client.post(f"/v1/friend-requests/{receiver_username}", headers=headers(sender))
    assert sent.status_code == 201, sent.text
    incoming = client.get("/v1/friend-requests", headers=headers(receiver))
    assert incoming.status_code == 200, incoming.text
    request_id = incoming.json()["incoming"][-1]["id"]
    accepted = client.post(f"/v1/friend-requests/{request_id}/accept", headers=headers(receiver))
    assert accepted.status_code == 200, accepted.text


def test_private_media_post_roundtrip():
    arda = register_user("media-arda@example.com", "media_arda", "Media Arda")
    nadia = register_user("media-nadia@example.com", "media_nadia", "Media Nadia")
    stranger = register_user("media-stranger@example.com", "media_stranger", "Media Stranger")
    connect(arda, nadia, "media_nadia")

    payload = b"fake-jpeg-alpha-payload"
    upload_headers = headers(arda) | {
        "Content-Type": "image/jpeg",
        "X-KIN-Filename": "photo.jpg",
    }
    uploaded = client.post("/v1/media", headers=upload_headers, content=payload)
    assert uploaded.status_code == 201, uploaded.text
    media_id = uploaded.json()["id"]
    assert uploaded.json()["type"] == "image"

    created = client.post(
        "/v1/media-posts",
        headers=headers(arda),
        json={
            "text": "",
            "audience": "friends",
            "media_ids": [media_id],
        },
    )
    assert created.status_code == 201, created.text
    post_id = created.json()["id"]
    assert created.json()["media"][0]["id"] == media_id

    feed = client.get("/v1/feed", headers=headers(nadia))
    assert feed.status_code == 200
    assert post_id in {post["id"] for post in feed.json()}

    feed_media = client.get("/v1/feed/media", headers=headers(nadia))
    assert feed_media.status_code == 200, feed_media.text
    bundle = next(item for item in feed_media.json() if item["post_id"] == post_id)
    signed_url = bundle["media"][0]["url"]
    streamed = client.get(signed_url)
    assert streamed.status_code == 200, streamed.text
    assert streamed.content == payload
    assert streamed.headers["content-type"].startswith("image/jpeg")

    stranger_media = client.get("/v1/feed/media", headers=headers(stranger))
    assert stranger_media.status_code == 200
    assert post_id not in {item["post_id"] for item in stranger_media.json()}


def test_media_post_rejects_video_album_mix():
    owner = register_user("video-owner@example.com", "video_owner", "Video Owner")
    photo = client.post(
        "/v1/media",
        headers=headers(owner) | {"Content-Type": "image/png"},
        content=b"image",
    )
    video = client.post(
        "/v1/media",
        headers=headers(owner) | {"Content-Type": "video/mp4"},
        content=b"video",
    )
    assert photo.status_code == 201
    assert video.status_code == 201
    mixed = client.post(
        "/v1/media-posts",
        headers=headers(owner),
        json={
            "audience": "only_me",
            "media_ids": [photo.json()["id"], video.json()["id"]],
        },
    )
    assert mixed.status_code == 400
