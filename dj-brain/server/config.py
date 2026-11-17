"""Environment-only configuration. No credentials belong in client code."""

from __future__ import annotations

from pathlib import Path
from typing import Literal
from urllib.parse import urlparse

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT = Path(__file__).resolve().parent.parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(ROOT / "server" / ".env", ROOT / ".env"),
        env_file_encoding="utf-8", extra="ignore", case_sensitive=False,
    )

    dev_mode: bool = True
    store_backend: Literal["local", "firestore", "memory"] = "local"
    local_store_path: Path = ROOT / ".data" / "store.json"
    firebase_service_account: Path | None = None
    firebase_project_id: str | None = None
    auth_cache_ttl_sec: int = Field(default=60, ge=0, le=300)
    auth_cache_size: int = Field(default=512, ge=1, le=10000)
    cors_origins: list[str] = ["http://localhost:8000", "http://127.0.0.1:8000", "null"]
    flush_interval_sec: float = Field(default=1.0, ge=0.05, le=60)
    max_pending_writes: int = Field(default=10000, ge=100, le=100000)
    cache_size: int = Field(default=512, ge=4, le=10000)
    session_ttl_sec: int = Field(default=43200, ge=60, le=604800)
    max_active_sessions_per_user: int = Field(default=8, ge=1, le=100)
    requests_per_minute: int = Field(default=180, ge=10, le=10000)
    max_concurrent_llm: int = Field(default=4, ge=1, le=32)

    llm_provider: Literal["none", "nvidia", "openai", "groq", "ollama"] = "none"
    llm_base_url: str | None = None
    llm_model: str | None = None
    llm_api_key: SecretStr | None = None
    nvidia_api_key: SecretStr | None = None
    openai_api_key: SecretStr | None = None
    groq_api_key: SecretStr | None = None
    llm_timeout_sec: float = Field(default=3.0, ge=0.1, le=30)
    llm_max_tokens: int = Field(default=256, ge=32, le=1024)

    tts_provider: Literal["local", "gnani"] = "local"
    gnani_api_key: SecretStr | None = None
    gnani_base_url: str = "https://api.vachana.ai"
    gnani_model: Literal["timbre-v2.5", "timbre-v3.0"] = "timbre-v2.5"
    gnani_voice: str = "Girish"
    gnani_language: str = "en-IN"
    gnani_speed: float = Field(default=1.0, ge=0.85, le=1.15)
    tts_timeout_sec: float = Field(default=10, ge=0.1, le=30)
    tts_cache_bytes: int = Field(default=16777216, ge=65536, le=134217728)
    tts_max_audio_bytes: int = Field(default=4194304, ge=65536, le=16777216)

    @model_validator(mode="after")
    def safe_deployment(self) -> "Settings":
        for value in (self.llm_base_url, self.gnani_base_url):
            if value:
                parsed = urlparse(value)
                if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
                    raise ValueError("Provider URLs must be HTTP(S) URLs without embedded credentials")
                if not self.dev_mode and parsed.scheme != "https" and parsed.hostname not in ("localhost", "127.0.0.1"):
                    raise ValueError("Production provider URLs require HTTPS")
        if not self.dev_mode and ("*" in self.cors_origins or "null" in self.cors_origins):
            raise ValueError("Production CORS must list explicit trusted origins (not '*' or 'null')")
        return self

    @property
    def resolved_llm_url(self) -> str:
        return (self.llm_base_url or {
            "nvidia": "https://integrate.api.nvidia.com/v1",
            "openai": "https://api.openai.com/v1",
            "groq": "https://api.groq.com/openai/v1",
            "ollama": "http://localhost:11434/v1",
            "none": "http://localhost",
        }[self.llm_provider]).rstrip("/")

    @property
    def resolved_llm_model(self) -> str:
        return self.llm_model or {
            "nvidia": "nvidia/nemotron-3.5-lightning-30b-a3b",
            "openai": "gpt-4.1-mini",
            "groq": "llama-3.3-70b-versatile",
            "ollama": "llama3.2",
            "none": "none",
        }[self.llm_provider]

    @property
    def resolved_llm_key(self) -> str | None:
        key = self.llm_api_key or {
            "nvidia": self.nvidia_api_key,
            "openai": self.openai_api_key,
            "groq": self.groq_api_key,
        }.get(self.llm_provider)
        return key.get_secret_value() if key else None
