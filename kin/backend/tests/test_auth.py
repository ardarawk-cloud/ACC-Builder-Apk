import os
from pathlib import Path

TEST_DB = Path("test-kin.db")
os.environ["KIN_DATABASE_URL"] = f"sqlite:///{TEST_DB}"
os.environ["KIN_JWT_SECRET"] = "test-secret-not-for-production"

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def setup_module():
    if TEST_DB.exists():
        TEST_DB.unlink()


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
