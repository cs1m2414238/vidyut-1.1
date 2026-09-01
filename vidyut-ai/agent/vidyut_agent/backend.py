from __future__ import annotations

from typing import Any

import httpx

from .config import settings
from .runtime_context import authorization_context


_SENSITIVE_KEYS = {
    "accessToken",
    "accountId",
    "authorization",
    "hostUserId",
    "password",
    "refreshToken",
    "secret",
    "token",
    "userId",
}


class BackendError(RuntimeError):
    def __init__(self, message: str, *, status_code: int = 502) -> None:
        super().__init__(message)
        self.status_code = status_code


def redact_sensitive(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: redact_sensitive(item)
            for key, item in value.items()
            if key not in _SENSITIVE_KEYS
        }
    if isinstance(value, list):
        return [redact_sensitive(item) for item in value]
    return value


class VidyutBackendClient:
    async def request(
        self,
        method: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json: dict[str, Any] | None = None,
        authenticated: bool = True,
        timeout_seconds: float | None = None,
    ) -> Any:
        if not path.startswith("/api/"):
            raise BackendError("Invalid Vidyut backend path", status_code=500)

        headers = {"Accept": "application/json"}
        if authenticated:
            authorization = authorization_context.get()
            if not authorization or not authorization.startswith("Bearer "):
                raise BackendError(
                    "A valid Vidyut login is required for this action",
                    status_code=401,
                )
            headers["Authorization"] = authorization

        clean_params = {
            key: value
            for key, value in (params or {}).items()
            if value is not None and value != ""
        }

        try:
            async with httpx.AsyncClient(
                base_url=settings.backend_base_url,
                timeout=timeout_seconds or settings.backend_timeout_seconds,
            ) as client:
                response = await client.request(
                    method,
                    path,
                    headers=headers,
                    params=clean_params or None,
                    json=json,
                )
        except httpx.RequestError as exc:
            raise BackendError(
                "The Vidyut backend is unavailable. Start Spring Boot on port 8080."
            ) from exc

        try:
            payload = response.json()
        except ValueError:
            payload = None

        if response.is_error:
            message = (
                payload.get("message")
                if isinstance(payload, dict) and payload.get("message")
                else f"Vidyut backend request failed ({response.status_code})"
            )
            raise BackendError(str(message), status_code=response.status_code)

        if not isinstance(payload, dict) or "data" not in payload:
            raise BackendError("The Vidyut backend returned an invalid response")
        if payload.get("success") is False:
            raise BackendError(str(payload.get("message") or "Vidyut request failed"))

        return redact_sensitive(payload["data"])


backend = VidyutBackendClient()
