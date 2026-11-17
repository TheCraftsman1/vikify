"""Run with: python -m uvicorn server.app:app --host 127.0.0.1 --port 8001."""

from __future__ import annotations

import asyncio
import time
from collections import OrderedDict, deque
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware

from .auth import TokenVerifier
from .config import Settings
from .conversation import MusicConversationRequest, converse_music
from .intelligence import MusicPlanRequest, plan_music
from .providers import LanguageModel


def create_app(settings: Settings | None = None, *, model=None, verifier=None) -> FastAPI:
    settings = settings or Settings()
    client = httpx.AsyncClient(follow_redirects=False)
    reasoning = model or LanguageModel(settings, client)
    tokens = verifier or TokenVerifier(settings)
    requests: OrderedDict[str, deque[float]] = OrderedDict()
    lock = asyncio.Lock()

    @asynccontextmanager
    async def lifespan(app):
        yield
        await client.aclose()

    app = FastAPI(title="Vikify Music Intelligence", lifespan=lifespan)
    app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["POST", "GET"],
                       allow_headers=["Authorization", "Content-Type", "X-Dev-User-Id"])

    @app.get("/health")
    async def health():
        return {"ok": True, "modelConfigured": reasoning.enabled, "schemaVersion": 1}

    async def authorize_music_request(request: Request) -> None:
        uid = await tokens.authenticate(request.headers)
        now = time.monotonic()
        async with lock:
            recent = requests.setdefault(uid, deque())
            requests.move_to_end(uid)
            while recent and recent[0] < now - 60:
                recent.popleft()
            if len(recent) >= settings.requests_per_minute:
                raise HTTPException(429, "Music intelligence request limit reached", headers={"Retry-After": "60"})
            recent.append(now)
            while len(requests) > settings.auth_cache_size:
                requests.popitem(last=False)

    @app.post("/v1/music/plan")
    async def plan(body: MusicPlanRequest, request: Request):
        await authorize_music_request(request)
        return await plan_music(body, reasoning)

    @app.post("/v1/music/conversation")
    async def conversation(body: MusicConversationRequest, request: Request):
        await authorize_music_request(request)
        return await converse_music(body, reasoning)

    return app


app = create_app()
