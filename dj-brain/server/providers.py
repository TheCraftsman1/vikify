"""Bounded OpenAI-compatible JSON calls; no transport commands use this path."""

from __future__ import annotations

import asyncio
import json
import logging

import httpx

from .config import Settings

log = logging.getLogger(__name__)


class LanguageModel:
    def __init__(self, settings: Settings, client: httpx.AsyncClient):
        self.settings, self.client = settings, client
        self._slots = asyncio.Semaphore(settings.max_concurrent_llm)

    @property
    def enabled(self) -> bool:
        return self.settings.llm_provider != "none" and bool(
            self.settings.resolved_llm_key or self.settings.llm_provider == "ollama"
        )

    async def json(self, system: str, context: dict, *, max_tokens: int | None = None) -> dict | None:
        if not self.enabled:
            return None
        settings = self.settings
        body = {
            "model": settings.resolved_llm_model,
            "messages": [{"role": "system", "content": system},
                         {"role": "user", "content": json.dumps(context, ensure_ascii=False)}],
            "temperature": 0.65,
            "max_tokens": max_tokens or settings.llm_max_tokens,
            "response_format": {"type": "json_object"},
            "stream": False,
        }
        if settings.llm_provider == "nvidia":
            body["chat_template_kwargs"] = {"enable_thinking": False}
        headers = {}
        if settings.resolved_llm_key:
            headers["Authorization"] = f"Bearer {settings.resolved_llm_key}"
        try:
            # Includes time waiting for a concurrency slot, not only socket I/O.
            async with asyncio.timeout(settings.llm_timeout_sec):
                async with self._slots:
                    response = await self.client.post(
                        settings.resolved_llm_url + "/chat/completions", json=body,
                        headers=headers, timeout=settings.llm_timeout_sec,
                    )
                    response.raise_for_status()
                    if len(response.content) > 65536:
                        return None
                    content = response.json()["choices"][0]["message"]["content"]
                    if not isinstance(content, str) or len(content) > 8192:
                        return None
                    # Never surface reasoning_content or tool calls to TTS.
                    result = json.loads(content)
                    return result if isinstance(result, dict) else None
        except (httpx.HTTPError, TimeoutError, ValueError, KeyError, IndexError, TypeError):
            log.warning("LLM unavailable or invalid JSON; using deterministic fallback (%s)", settings.llm_provider)
            return None
