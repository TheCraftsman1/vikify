"""Single-worker session cache with coalesced, retried asynchronous persistence.

Acknowledgement means accepted in RAM, not durably committed. Use a durable
queue/transactional store instead when crash-loss of a flush interval is not
acceptable. Firestore snapshots must not be shared by multiple API workers.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import time
from collections import OrderedDict
from copy import deepcopy
from pathlib import Path

from .auth import firebase_app
from .config import Settings
from .profile import new_profile

log = logging.getLogger(__name__)


class LocalJsonStore:
    def __init__(self, path: Path | None):
        self.path = path
        self.documents: dict[str, dict] = {}

    def open(self) -> None:
        if self.path and self.path.exists():
            with self.path.open(encoding="utf-8") as handle:
                data = json.load(handle)
            if data.get("version") != 1 or not isinstance(data.get("documents"), dict):
                raise ValueError("Unsupported local store format; refusing to discard existing data")
            self.documents = data["documents"]

    def read(self, path: str) -> dict | None:
        return deepcopy(self.documents.get(path))

    def write(self, documents: dict[str, dict]) -> None:
        # Only publish the new in-memory snapshot after an atomic disk replace.
        merged = {**self.documents, **deepcopy(documents)}
        event_paths = [path for path in merged if "/events/" in path or "/episodes/" in path]
        for path in event_paths[:-5000]:
            del merged[path]
        if self.path:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.path.with_suffix(".tmp")
            try:
                with temporary.open("w", encoding="utf-8") as handle:
                    json.dump({"version": 1, "documents": merged}, handle, ensure_ascii=False, allow_nan=False)
                    handle.flush()
                    os.fsync(handle.fileno())
                os.replace(temporary, self.path)
            finally:
                temporary.unlink(missing_ok=True)
        self.documents = merged


class FirestoreStore:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = None

    def open(self) -> None:
        from firebase_admin import firestore
        self.client = firestore.client(app=firebase_app(self.settings))

    def read(self, path: str) -> dict | None:
        snapshot = self.client.document(path).get(timeout=8)
        return snapshot.to_dict() if snapshot.exists else None

    def write(self, documents: dict[str, dict]) -> None:
        entries = list(documents.items())
        # Retrying a partially committed flush is safe: every document ID is stable.
        for start in range(0, len(entries), 400):
            batch = self.client.batch()
            for path, data in entries[start:start + 400]:
                batch.set(self.client.document(path), data)
            batch.commit(timeout=8)


class WriteBehindRepository:
    def __init__(self, settings: Settings, backend=None):
        self.settings = settings
        self.backend = backend or (
            FirestoreStore(settings) if settings.store_backend == "firestore" else
            LocalJsonStore(None if settings.store_backend == "memory" else settings.local_store_path)
        )
        self.profiles: OrderedDict[str, dict] = OrderedDict()
        self.sessions: OrderedDict[str, dict] = OrderedDict()
        self._pending: dict[str, dict] = {}
        self._flush_lock = asyncio.Lock()
        self._stop = asyncio.Event()
        self._worker: asyncio.Task | None = None
        self._uid_locks: dict[str, asyncio.Lock] = {}
        self.last_flush_at: float | None = None
        self.last_error: str | None = None
        self._closed = False

    def user_lock(self, uid: str) -> asyncio.Lock:
        # User-level serialization also prevents lost updates across two sessions.
        return self._uid_locks.setdefault(uid, asyncio.Lock())

    async def start(self) -> None:
        await asyncio.to_thread(self.backend.open)
        self._worker = asyncio.create_task(self._run(), name="dj-write-behind")

    def _remember(self, cache: OrderedDict, key: str, value: dict) -> dict:
        cache[key] = value
        cache.move_to_end(key)
        while len(cache) > self.settings.cache_size:
            old_key = next(iter(cache))
            if old_key == key:
                break
            cache.popitem(last=False)
        return value

    async def profile(self, uid: str) -> dict:
        if uid in self.profiles:
            self.profiles.move_to_end(uid)
            return self.profiles[uid]
        path = f"users/{uid}"
        data = deepcopy(self._pending.get(path))
        if data is None:
            data = await asyncio.to_thread(self.backend.read, path)
        profile = data if data is not None else new_profile(uid)
        if data is None:
            self.enqueue(path, profile)
        return self._remember(self.profiles, uid, profile)

    async def session(self, session_id: str) -> dict | None:
        if session_id in self.sessions:
            self.sessions.move_to_end(session_id)
            return self.sessions[session_id]
        path = f"sessions/{session_id}"
        data = deepcopy(self._pending.get(path))
        if data is None:
            data = await asyncio.to_thread(self.backend.read, path)
        return self._remember(self.sessions, session_id, data) if data is not None else None

    def ensure_capacity(self, writes: int = 4) -> None:
        if self._closed or len(self._pending) + writes > self.settings.max_pending_writes:
            raise RuntimeError("Persistence buffer is full or closing; retry after a successful flush")

    def enqueue(self, path: str, data: dict) -> None:
        if path not in self._pending:
            self.ensure_capacity(1)
        self._pending[path] = deepcopy(data)

    def save(self, profile: dict, session: dict | None = None) -> None:
        self._remember(self.profiles, profile["uid"], profile)
        self.enqueue(f"users/{profile['uid']}", profile)
        if session is not None:
            self._remember(self.sessions, session["id"], session)
            self.enqueue(f"sessions/{session['id']}", session)

    def event(self, uid: str, event_id: str, data: dict) -> None:
        self.enqueue(f"users/{uid}/events/{event_id}", data)

    def episode(self, uid: str, episode_id: str, data: dict) -> None:
        self.enqueue(f"users/{uid}/episodes/{episode_id}", data)

    async def flush(self) -> None:
        async with self._flush_lock:
            if not self._pending:
                return
            snapshot, self._pending = self._pending, {}
            try:
                await asyncio.to_thread(self.backend.write, snapshot)
            except Exception as error:
                # Newer updates received during disk/network I/O win on retry.
                self._pending = {**snapshot, **self._pending}
                self.last_error = type(error).__name__
                raise
            self.last_error = None
            self.last_flush_at = time.time()

    async def _run(self) -> None:
        while not self._stop.is_set():
            try:
                await asyncio.wait_for(self._stop.wait(), timeout=self.settings.flush_interval_sec)
            except asyncio.TimeoutError:
                pass
            try:
                await self.flush()
            except Exception:
                log.error("Persistence flush failed (%s); queued writes retained for retry", self.last_error)
                try:
                    await asyncio.wait_for(self._stop.wait(), timeout=min(5, self.settings.flush_interval_sec * 2))
                except asyncio.TimeoutError:
                    pass

    async def close(self) -> None:
        self._stop.set()
        if self._worker:
            await self._worker
        for attempt in range(3):
            try:
                await self.flush()
                self._closed = True
                return
            except Exception:
                if attempt == 2:
                    raise RuntimeError("Shutdown could not persist queued DJ state") from None
                await asyncio.sleep(0.1 * (attempt + 1))

    @property
    def status(self) -> dict:
        return {"pending_writes": len(self._pending), "last_flush_at": self.last_flush_at,
                "degraded": self.last_error is not None, "backend": self.settings.store_backend}
