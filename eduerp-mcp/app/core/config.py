"""Environment-driven settings for eduerp-mcp."""

from __future__ import annotations

from urllib.parse import quote_plus

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from app.platform.oauth.public import OAuthConstants
from app.shared.config import ConfigTypes


class Settings(BaseSettings):
    """Configuration shared with EduERP PostgreSQL, Redis, and JWT authentication."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    host: str = "0.0.0.0"
    port: int = 8700
    public_url: str = "http://localhost:8090"

    # Shared EduERP DB & Redis env vars (from backend/.env.docker*)
    db_host: str = "localhost"
    db_port: int = 5433
    db_name: str = "eduerp"
    db_username: str = "agent"
    db_password: str = "localdev"

    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""

    database_url: str = ""
    redis_url: str = ""
    eduerp_api_base_url: str = "http://localhost:8080"

    identity_jwt_secret: str = (
        "dev-only-secret-change-me-please-32bytes"
    )
    mcp_sealer_secret: str = (
        "dev-only-mcp-sealer-secret-key-must-be-at-least-32-bytes"
    )

    trusted_proxy_ips: str = "127.0.0.1,172.16.0.0/12,10.0.0.0/8,192.168.0.0/16"
    allowed_origins: ConfigTypes.LowerCsv = ["*"]
    allowed_redirect_hosts: ConfigTypes.LowerCsv = list(
        OAuthConstants.DEFAULT_REDIRECT_HOSTS
    )
    allowed_redirect_prefixes: ConfigTypes.LowerCsv = list(
        OAuthConstants.DEFAULT_REDIRECT_PREFIXES
    )
    auto_ensure_schema: bool = True

    @model_validator(mode="after")
    def _assemble_shared_urls(self) -> Settings:
        if not self.database_url:
            user = quote_plus(self.db_username)
            pwd = quote_plus(self.db_password)
            self.database_url = (
                f"postgresql+psycopg://{user}:{pwd}@{self.db_host}:{self.db_port}/{self.db_name}"
            )
        if not self.redis_url:
            if self.redis_password:
                pwd = quote_plus(self.redis_password)
                self.redis_url = f"redis://:{pwd}@{self.redis_host}:{self.redis_port}/0"
            else:
                self.redis_url = f"redis://{self.redis_host}:{self.redis_port}/0"
        return self
