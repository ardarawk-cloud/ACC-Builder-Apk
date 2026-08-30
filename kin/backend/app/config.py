from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "KIN API"
    environment: str = "development"
    database_url: str = "sqlite:///./kin.db"
    jwt_secret: str = "dev-only-change-me"
    access_token_minutes: int = 30
    refresh_token_days: int = 30

    model_config = SettingsConfigDict(env_prefix="KIN_", env_file=".env", extra="ignore")

    def validate_security(self) -> None:
        if self.environment.lower() == "production" and self.jwt_secret == "dev-only-change-me":
            raise RuntimeError("KIN_JWT_SECRET must be set in production")


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    settings.validate_security()
    return settings
