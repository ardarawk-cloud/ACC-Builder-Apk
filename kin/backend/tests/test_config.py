import pytest

from app.config import Settings
from app.database import normalize_database_url


def test_managed_postgres_urls_use_psycopg3_driver() -> None:
    assert normalize_database_url("postgres://user:pass@db/kin") == "postgresql+psycopg://user:pass@db/kin"
    assert normalize_database_url("postgresql://user:pass@db/kin") == "postgresql+psycopg://user:pass@db/kin"
    assert normalize_database_url("postgresql+psycopg://user:pass@db/kin") == "postgresql+psycopg://user:pass@db/kin"


def test_production_rejects_default_secret() -> None:
    settings = Settings(
        environment="production",
        database_url="postgresql://user:pass@db/kin",
        jwt_secret="dev-only-change-me",
    )
    with pytest.raises(RuntimeError, match="KIN_JWT_SECRET"):
        settings.validate_security()


def test_production_rejects_sqlite() -> None:
    settings = Settings(
        environment="production",
        database_url="sqlite:///./kin.db",
        jwt_secret="production-secret",
    )
    with pytest.raises(RuntimeError, match="PostgreSQL"):
        settings.validate_security()


def test_production_accepts_postgres_and_custom_secret() -> None:
    settings = Settings(
        environment="production",
        database_url="postgresql://user:pass@db/kin",
        jwt_secret="production-secret",
    )
    settings.validate_security()
