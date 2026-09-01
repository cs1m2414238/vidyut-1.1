from __future__ import annotations

from contextvars import ContextVar, Token
from dataclasses import dataclass


authorization_context: ContextVar[str | None] = ContextVar(
    "authorization", default=None
)
request_id_context: ContextVar[str | None] = ContextVar("request_id", default=None)
user_message_context: ContextVar[str] = ContextVar("user_message", default="")


@dataclass(frozen=True)
class RequestContextTokens:
    authorization: Token[str | None]
    request_id: Token[str | None]
    user_message: Token[str]


def set_request_context(
    *, authorization: str, request_id: str, user_message: str
) -> RequestContextTokens:
    return RequestContextTokens(
        authorization=authorization_context.set(authorization),
        request_id=request_id_context.set(request_id),
        user_message=user_message_context.set(user_message),
    )


def reset_request_context(tokens: RequestContextTokens) -> None:
    authorization_context.reset(tokens.authorization)
    request_id_context.reset(tokens.request_id)
    user_message_context.reset(tokens.user_message)
