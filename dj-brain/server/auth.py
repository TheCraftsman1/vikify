"""Firebase ID-token authentication with a short, bounded hash-keyed cache."""

from __future__ import annotations

import asyncio
import hashlib
import re
import time
from collections import OrderedDict
from collections.abc import Callable, Mapping

from fastapi import HTTPException

from .config import Settings


def firebase_app(settings: Settings):
    """Admin SDK is optional in zero-key mode; initialize only when needed."""
    import firebase_admin
    from firebase_admin import credentials

    try:
        return firebase_admin.get_app("dj-brain")
    except ValueError:
        credential = (
            credentials.Certificate(str(settings.firebase_service_account))
            if settings.firebase_service_account else credentials.ApplicationDefault()
        )
        options = {"projectId": settings.firebase_project_id} if settings.firebase_project_id else None
        return firebase_admin.initialize_app(credential, options, name="dj-brain")


class TokenVerifier:
    def __init__(self, settings: Settings, verify: Callable | None = None):
        self.settings = settings
        self._verify = verify
        self._cache: OrderedDict[str, tuple[str, float]] = OrderedDict()
        self._lock = asyncio.Lock()

    def _firebase_verify(self, token: str) -> dict:
        from firebase_admin import auth
        return auth.verify_id_token(token, app=firebase_app(self.settings), check_revoked=True)

    async def authenticate(self, headers: Mapping[str, str]) -> str:
        authorization = headers.get("authorization", "")
        dev_uid = headers.get("x-dev-user-id", "")
        if self.settings.dev_mode and dev_uid and not authorization:
            if not re.fullmatch(r"[A-Za-z0-9_.:-]{1,128}", dev_uid):
                raise HTTPException(400, "Invalid development user ID")
            return dev_uid
        scheme, _, token = authorization.partition(" ")
        if scheme.lower() != "bearer" or not token or len(token) > 16384:
            raise HTTPException(401, "Firebase Bearer token required", headers={"WWW-Authenticate": "Bearer"})
        digest = hashlib.sha256(token.encode()).hexdigest()
        async with self._lock:
            cached = self._cache.get(digest)
            if cached and cached[1] > time.monotonic():
                self._cache.move_to_end(digest)
                return cached[0]
            self._cache.pop(digest, None)
        try:
            claims = await asyncio.wait_for(
                asyncio.to_thread(self._verify or self._firebase_verify, token), timeout=8,
            )
            uid = claims.get("uid") or claims.get("sub")
            if not isinstance(uid, str) or not uid or "/" in uid or len(uid) > 128:
                raise ValueError("Invalid token subject")
            remaining = float(claims["exp"]) - time.time()
            if remaining <= 0:
                raise ValueError("Expired token")
        except (ImportError, ModuleNotFoundError):
            raise HTTPException(503, "Firebase Admin SDK is not installed") from None
        except Exception:
            # Do not expose SDK errors, claims, token contents or credentials.
            raise HTTPException(401, "Invalid or expired Firebase ID token", headers={"WWW-Authenticate": "Bearer"}) from None
        async with self._lock:
            self._cache[digest] = (uid, time.monotonic() + min(remaining, self.settings.auth_cache_ttl_sec))
            while len(self._cache) > self.settings.auth_cache_size:
                self._cache.popitem(last=False)
        return uid
