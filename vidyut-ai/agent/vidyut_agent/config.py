from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv


PACKAGE_DIR = Path(__file__).resolve().parent
load_dotenv(PACKAGE_DIR / ".env", override=False)
load_dotenv(override=False)


def _positive_float(name: str, default: float) -> float:
    raw = os.getenv(name, str(default)).strip()
    try:
        value = float(raw)
    except ValueError as exc:
        raise RuntimeError(f"{name} must be a number") from exc
    if value <= 0:
        raise RuntimeError(f"{name} must be greater than zero")
    return value


def _env_true(name: str) -> bool:
    return os.getenv(name, "").strip().lower() in {"1", "true", "yes"}


@dataclass(frozen=True)
class Settings:
    model: str
    fallback_models: tuple[str, ...]
    openrouter_api_key: str
    openrouter_model: str
    openrouter_fallback_models: tuple[str, ...]
    openrouter_base_url: str
    backend_base_url: str
    backend_timeout_seconds: float

    @property
    def google_auth_configured(self) -> bool:
        if _env_true("VIDYUT_AGENT_DISABLE_GEMINI"):
            return False
        if _env_true("GOOGLE_GENAI_USE_VERTEXAI") or _env_true(
            "GOOGLE_GENAI_USE_ENTERPRISE"
        ):
            return bool(
                os.getenv("GOOGLE_CLOUD_PROJECT", "").strip()
                and os.getenv("GOOGLE_CLOUD_LOCATION", "").strip()
            )
        api_key = os.getenv("GOOGLE_API_KEY", "").strip() or os.getenv("GEMINI_API_KEY", "").strip()
        return bool(api_key)

    @property
    def openrouter_auth_configured(self) -> bool:
        return bool(self.openrouter_api_key)

    @property
    def any_llm_auth_configured(self) -> bool:
        return self.google_auth_configured or self.openrouter_auth_configured


def load_settings() -> Settings:
    model = os.getenv("VIDYUT_AGENT_MODEL", "gemini-3.6-flash").strip()
    if not model:
        raise RuntimeError("VIDYUT_AGENT_MODEL cannot be empty")
    fallback_models = tuple(
        candidate
        for candidate in (
            value.strip()
            for value in os.getenv(
                "VIDYUT_AGENT_FALLBACK_MODELS",
                "gemini-3.5-flash,gemini-3.5-pro,gemini-3-flash-preview",
            ).split(",")
        )
        if candidate and candidate != model
    )

    openrouter_api_key = (
        os.getenv("OPENROUTER_API_KEY", "").strip()
        or os.getenv("OPEN_ROUTER_API_KEY", "").strip()
        or os.getenv("OPENROUTER_KEY", "").strip()
    )
    openrouter_model = os.getenv(
        "OPENROUTER_MODEL", "meta-llama/llama-3.3-70b-instruct"
    ).strip()
    if not openrouter_model:
        openrouter_model = "meta-llama/llama-3.3-70b-instruct"

    openrouter_fallback_models = tuple(
        candidate
        for candidate in (
            value.strip()
            for value in os.getenv(
                "OPENROUTER_FALLBACK_MODELS", ""
            ).split(",")
        )
        if candidate and candidate != openrouter_model
    )

    openrouter_base_url = os.getenv(
        "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"
    ).strip().rstrip("/")
    if not openrouter_base_url.startswith(("http://", "https://")):
        raise RuntimeError("OPENROUTER_BASE_URL must be an HTTP(S) URL")

    backend_base_url = os.getenv(
        "VIDYUT_BACKEND_BASE_URL", "http://localhost:8080"
    ).strip().rstrip("/")
    if not backend_base_url.startswith(("http://", "https://")):
        raise RuntimeError("VIDYUT_BACKEND_BASE_URL must be an HTTP(S) URL")

    return Settings(
        model=model,
        fallback_models=fallback_models,
        openrouter_api_key=openrouter_api_key,
        openrouter_model=openrouter_model,
        openrouter_fallback_models=openrouter_fallback_models,
        openrouter_base_url=openrouter_base_url,
        backend_base_url=backend_base_url,
        backend_timeout_seconds=_positive_float(
            "VIDYUT_BACKEND_TIMEOUT_SECONDS", 15
        ),
    )


settings = load_settings()
