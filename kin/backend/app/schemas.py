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


class PostCreate(BaseModel):
    text: str = Field(default="", max_length=1000)
    audience: str = "friends"
    allowed_user_ids: list[int] = Field(default_factory=list, max_length=200)
    feeling: str | None = Field(default=None, max_length=80)
    listening: str | None = Field(default=None, max_length=500)
    location: str | None = Field(default=None, max_length=120)
    with_people: str | None = Field(default=None, max_length=240)

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


class PostPatch(BaseModel):
    text: str | None = Field(default=None, max_length=1000)
    audience: str | None = None
    allowed_user_ids: list[int] | None = Field(default=None, max_length=200)
    feeling: str | None = Field(default=None, max_length=80)
    listening: str | None = Field(default=None, max_length=500)
    location: str | None = Field(default=None, max_length=120)
    with_people: str | None = Field(default=None, max_length=240)

    @field_validator("audience")
    @classmethod
    def validate_audience(cls, value: str | None) -> str | None:
        if value is None:
            return None
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


class PostOut(BaseModel):
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


class MessageCreate(BaseModel):
    text: str = Field(min_length=1, max_length=2000)

    @field_validator("text")
    @classmethod
    def strip_message(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("message cannot be blank")
        return value


class MessageOut(BaseModel):
    id: str
    sender: PublicUserOut
    recipient: PublicUserOut
    text: str
    created_at: datetime


class AuthResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserOut
