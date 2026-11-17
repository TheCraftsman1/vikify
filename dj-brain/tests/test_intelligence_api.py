import unittest

from fastapi.testclient import TestClient

from server.app import create_app
from server.config import Settings


class UnconfiguredModel:
    enabled = False

    async def json(self, *args, **kwargs):
        return None


class IntelligenceApiTests(unittest.TestCase):
    def settings(self, **extra):
        return Settings(_env_file=None, llm_provider="none", cors_origins=[], **extra)

    def test_production_rejects_missing_auth_and_development_header(self):
        with TestClient(create_app(self.settings(dev_mode=False), model=UnconfiguredModel())) as client:
            self.assertEqual(401, client.post("/v1/music/plan", json={"transcript": "play music"}).status_code)
            self.assertEqual(401, client.post("/v1/music/plan", headers={"X-Dev-User-Id": "test"},
                                             json={"transcript": "play music"}).status_code)

    def test_authenticated_development_call_uses_only_supplied_real_inventory(self):
        with TestClient(create_app(self.settings(dev_mode=True), model=UnconfiguredModel())) as client:
            response = client.post("/v1/music/plan", headers={"X-Dev-User-Id": "test"}, json={
                "transcript": "only my library", "inventory": [
                    {"id": "real", "title": "Real track", "artist": "Artist", "inLibrary": True},
                    {"id": "outside", "title": "Other", "artist": "Other", "inLibrary": False},
                ],
            })
            self.assertEqual(200, response.status_code)
            self.assertEqual(["real"], response.json()["trackIds"])
            self.assertEqual("rules", response.json()["source"])
            self.assertFalse(client.get("/health").json()["modelConfigured"])

    def test_schema_bounds_are_enforced_at_http_boundary(self):
        with TestClient(create_app(self.settings(dev_mode=True), model=UnconfiguredModel())) as client:
            headers = {"X-Dev-User-Id": "test"}
            for body in [
                {"transcript": "x" * 301}, {"transcript": "music", "audioUrl": "https://example.com"},
                {"transcript": "music", "inventory": [{"id": str(i), "title": "t", "artist": "a"} for i in range(121)]},
            ]:
                self.assertEqual(422, client.post("/v1/music/plan", json=body, headers=headers).status_code)

    def test_per_user_rate_limit_is_enforced(self):
        with TestClient(create_app(self.settings(dev_mode=True, requests_per_minute=10), model=UnconfiguredModel())) as client:
            for _ in range(10):
                self.assertEqual(200, client.post("/v1/music/plan", headers={"X-Dev-User-Id": "one"},
                                                 json={"transcript": "music"}).status_code)
            response = client.post("/v1/music/plan", headers={"X-Dev-User-Id": "one"}, json={"transcript": "music"})
            self.assertEqual(429, response.status_code)
            self.assertEqual("60", response.headers["Retry-After"])
            self.assertEqual(200, client.post("/v1/music/plan", headers={"X-Dev-User-Id": "two"},
                                             json={"transcript": "music"}).status_code)
