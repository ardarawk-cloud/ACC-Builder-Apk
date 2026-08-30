from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class RegisterRequest(BaseModel):
    email: EmailStr
    username: str = Field(min_length=3, max_length=32)
    display_name: str = Field(min_length=1, max_length=80)
    password: str = Field(min_length=8, max_length=128)

    @field_validator("username")
    @classmethod
    def normalize_username(cls, value: str) -> str:
        value = value.strip().lower().lstrip("@")
        if not value.replace("_", "").replace(".", "").isalnum():
            raise ValueError("username may contain letters, numbers, dots, and underscores")
        return value


class LoginRequest(BaseModel):
    identity: str = Field(min_length=1, max_length=320)
    password: str = Field(min_length=1, max_length=128)


class RefreshRequest(BaseModel):
    refresh_token: str


class LogoutRequest(BaseModel):
    refresh_token: str


class ProfilePatch(BaseModel):
    display_name: str | None = Field(default=None, min_length=1, max_length=80)
    bio: str | None = Field(default=None, max_length=160)
    skin_id: str | None = Field(default=None, max_length=40)


class UserOut(BaseModel):
    id: int
    email: EmailStr
    username: str
    display_name: str
    bio: str
    skin_id: str

    model_config = ConfigDict(from_attributes=True)


class PublicUserOut(BaseModel):
    id: int
    username: str
    display_name: str
    bio: str
    skin_id: str

    model_config = ConfigDict(from_attributes=True)


class PersonProfileOut(PublicUserOut):
    relationship: str


class FriendRequestOut(BaseModel):
    id: int
    user: PublicUserOut
    created_at: datetime


class FriendRequestsOut(BaseModel):
    incoming: list[FriendRequestOut]
    outgoing: list[FriendRequestOut]


class AuthResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserOut
