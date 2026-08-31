import os
from pathlib import Path

TEST_DB = Path("test-kin.db")
if TEST_DB.exists():
    TEST_DB.unlink()

os.environ["KIN_DATABASE_URL"] = f"sqlite:///{TEST_DB}"
os.environ["KIN_JWT_SECRET"] = "test-secret-not-for-production"

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


def test_health():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_register_login_refresh_profile_logout():
    register = client.post(
        "/v1/auth/register",
        json={
            "email": "arda@example.com",
            "username": "ardarawk",
            "display_name": "Arda",
            "password": "strong-pass-123",
        },
    )
    assert register.status_code == 201, register.text
    body = register.json()
    assert body["user"]["username"] == "ardarawk"

    access = body["access_token"]
    refresh = body["refresh_token"]

    me = client.get("/v1/me", headers={"Authorization": f"Bearer {access}"})
    assert me.status_code == 200
    assert me.json()["display_name"] == "Arda"

    patch = client.patch(
        "/v1/me",
        headers={"Authorization": f"Bearer {access}"},
        json={"bio": "DJ, Gamer, Developer", "skin_id": "midnight"},
    )
    assert patch.status_code == 200
    assert patch.json()["bio"] == "DJ, Gamer, Developer"
    assert patch.json()["skin_id"] == "midnight"

    refreshed = client.post("/v1/auth/refresh", json={"refresh_token": refresh})
    assert refreshed.status_code == 200
    new_refresh = refreshed.json()["refresh_token"]
    assert new_refresh != refresh

    old_refresh = client.post("/v1/auth/refresh", json={"refresh_token": refresh})
    assert old_refresh.status_code == 401

    logout = client.post("/v1/auth/logout", json={"refresh_token": new_refresh})
    assert logout.status_code == 204


def test_username_and_email_are_unique():
    first = client.post(
        "/v1/auth/register",
        json={
            "email": "second@example.com",
            "username": "unique_user",
            "display_name": "Second",
            "password": "strong-pass-123",
        },
    )
    assert first.status_code == 201

    duplicate_username = client.post(
        "/v1/auth/register",
        json={
            "email": "third@example.com",
            "username": "unique_user",
            "display_name": "Third",
            "password": "strong-pass-123",
        },
    )
    assert duplicate_username.status_code == 409


def test_people_search_friend_request_accept_and_decline():
    arda = register_user("friend-arda@example.com", "ardmrn", "Arda Moron")
    nadia = register_user("friend-nadia@example.com", "nadia_kin", "Nadia")
    raka = register_user("friend-raka@example.com", "raka.kin", "Raka")

    search = client.get("/v1/people/search?q=nadia", headers=headers(arda))
    assert search.status_code == 200, search.text
    assert [person["username"] for person in search.json()] == ["nadia_kin"]
    assert search.json()[0]["relationship"] == "none"
    assert "email" not in search.json()[0]

    self_search = client.get("/v1/people/search?q=ardmrn", headers=headers(arda))
    assert self_search.status_code == 200
    assert self_search.json() == []

    sent = client.post("/v1/friend-requests/@nadia_kin", headers=headers(arda))
    assert sent.status_code == 201, sent.text
    assert sent.json()["relationship"] == "outgoing_pending"

    duplicate = client.post("/v1/friend-requests/nadia_kin", headers=headers(arda))
    assert duplicate.status_code == 409

    arda_requests = client.get("/v1/friend-requests", headers=headers(arda))
    assert arda_requests.status_code == 200
    assert len(arda_requests.json()["outgoing"]) == 1
    request_id = arda_requests.json()["outgoing"][0]["id"]

    nadia_requests = client.get("/v1/friend-requests", headers=headers(nadia))
    assert nadia_requests.status_code == 200
    assert nadia_requests.json()["incoming"][0]["user"]["username"] == "ardmrn"

    own_accept = client.post(f"/v1/friend-requests/{request_id}/accept", headers=headers(arda))
    assert own_accept.status_code == 403

    accepted = client.post(f"/v1/friend-requests/{request_id}/accept", headers=headers(nadia))
    assert accepted.status_code == 200, accepted.text
    assert accepted.json()["relationship"] == "friends"

    arda_connections = client.get("/v1/connections", headers=headers(arda))
    nadia_connections = client.get("/v1/connections", headers=headers(nadia))
    assert [person["username"] for person in arda_connections.json()] == ["nadia_kin"]
    assert [person["username"] for person in nadia_connections.json()] == ["ardmrn"]

    profile = client.get("/v1/people/@nadia_kin", headers=headers(arda))
    assert profile.status_code == 200
    assert profile.json()["relationship"] == "friends"

    sent_to_raka = client.post("/v1/friend-requests/raka.kin", headers=headers(arda))
    assert sent_to_raka.status_code == 201
    raka_requests = client.get("/v1/friend-requests", headers=headers(raka)).json()
    decline_id = raka_requests["incoming"][0]["id"]

    declined = client.delete(f"/v1/friend-requests/{decline_id}", headers=headers(raka))
    assert declined.status_code == 204

    resend = client.post("/v1/friend-requests/raka.kin", headers=headers(arda))
    assert resend.status_code == 201


def test_social_core_posts_chat_remove_block_and_unblock():
    arda = register_user("core-arda@example.com", "core_arda", "Core Arda")
    nadia = register_user("core-nadia@example.com", "core_nadia", "Core Nadia")
    raka = register_user("core-raka@example.com", "core_raka", "Core Raka")

    connect(arda, nadia, "core_nadia")
    connect(arda, raka, "core_raka")

    friend_post = client.post(
        "/v1/posts",
        headers=headers(arda),
        json={
            "text": "hello people",
            "audience": "friends",
            "feeling": "Happy",
            "location": "Bali",
        },
    )
    assert friend_post.status_code == 201, friend_post.text
    friend_post_id = friend_post.json()["id"]

    nadia_feed = client.get("/v1/feed", headers=headers(nadia))
    assert nadia_feed.status_code == 200
    assert any(post["id"] == friend_post_id for post in nadia_feed.json())

    selected_post = client.post(
        "/v1/posts",
        headers=headers(arda),
        json={
            "text": "raka only",
            "audience": "selected",
            "allowed_user_ids": [raka["user"]["id"]],
        },
    )
    assert selected_post.status_code == 201, selected_post.text
    selected_id = selected_post.json()["id"]

    nadia_ids = {post["id"] for post in client.get("/v1/feed", headers=headers(nadia)).json()}
    raka_ids = {post["id"] for post in client.get("/v1/feed", headers=headers(raka)).json()}
    assert selected_id not in nadia_ids
    assert selected_id in raka_ids

    edited = client.patch(
        f"/v1/posts/{selected_id}",
        headers=headers(arda),
        json={"text": "raka only edited", "allowed_user_ids": [raka["user"]["id"]]},
    )
    assert edited.status_code == 200, edited.text
    assert edited.json()["text"] == "raka only edited"

    sent = client.post(
        "/v1/chats/core_nadia/messages",
        headers=headers(arda),
        json={"text": "real KIN chat"},
    )
    assert sent.status_code == 201, sent.text
    messages = client.get("/v1/chats/core_arda/messages", headers=headers(nadia))
    assert messages.status_code == 200, messages.text
    assert messages.json()[-1]["text"] == "real KIN chat"

    removed = client.delete("/v1/connections/core_nadia", headers=headers(arda))
    assert removed.status_code == 204
    after_remove = client.get("/v1/connections", headers=headers(arda)).json()
    assert "core_nadia" not in [person["username"] for person in after_remove]
    blocked_chat = client.post(
        "/v1/chats/core_nadia/messages",
        headers=headers(arda),
        json={"text": "should fail"},
    )
    assert blocked_chat.status_code == 403

    connect(arda, nadia, "core_nadia")
    blocked = client.post("/v1/blocks/core_nadia", headers=headers(arda))
    assert blocked.status_code == 200, blocked.text
    assert blocked.json()["relationship"] == "blocked"
    assert client.get("/v1/connections", headers=headers(arda)).json() == [
        person for person in client.get("/v1/connections", headers=headers(arda)).json() if person["username"] != "core_nadia"
    ]
    blocks = client.get("/v1/blocks", headers=headers(arda))
    assert blocks.status_code == 200
    assert "core_nadia" in [person["username"] for person in blocks.json()]
    search_blocked = client.get("/v1/people/search?q=core_nadia", headers=headers(arda))
    assert search_blocked.status_code == 200
    assert search_blocked.json() == []

    unblocked = client.delete("/v1/blocks/core_nadia", headers=headers(arda))
    assert unblocked.status_code == 204
    search_again = client.get("/v1/people/search?q=core_nadia", headers=headers(arda))
    assert search_again.status_code == 200
    assert [person["username"] for person in search_again.json()] == ["core_nadia"]

    deleted = client.delete(f"/v1/posts/{friend_post_id}", headers=headers(arda))
    assert deleted.status_code == 204
    own_ids = {post["id"] for post in client.get("/v1/feed", headers=headers(arda)).json()}
    assert friend_post_id not in own_ids
