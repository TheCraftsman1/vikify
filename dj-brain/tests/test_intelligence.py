import unittest

from pydantic import ValidationError

from server.intelligence import InventoryTrack, MusicIntent, MusicPlanRequest, fallback_intent, plan_music, select_tracks


class FakeModel:
    enabled = True

    def __init__(self, result):
        self.result = result

    async def json(self, *args, **kwargs):
        return self.result


class IntelligenceTests(unittest.IsolatedAsyncioTestCase):
    def request(self, **extra):
        return MusicPlanRequest(transcript="Play music", inventory=[
            InventoryTrack(id="one", title="Night", artist="First", inLibrary=True),
            InventoryTrack(id="two", title="Day", artist="Second", inLibrary=False),
            InventoryTrack(id="three", title="Morning", artist="Third", inLibrary=True),
            InventoryTrack(id="four", title="Evening", artist="First", inLibrary=True),
            InventoryTrack(id="unplayable", title="Other", artist="Other", playable=False),
        ], **extra)

    async def test_invented_ids_and_actions_are_never_accepted_from_the_model(self):
        request = self.request(blockedIds=["three"])
        result = await plan_music(request, FakeModel({"action": "set_url", "trackIds": ["invented"]}))
        self.assertEqual("rules", result["source"])
        self.assertTrue(set(result["trackIds"]) <= {"one", "two", "four"})

    async def test_model_cannot_relax_explicit_user_hard_constraints(self):
        request = self.request(playedTodayIds=["one"])
        request.transcript = "Only from my library; no repeats today"
        result = await plan_music(request, FakeModel(MusicIntent(query="", confidence=.9).model_dump()))
        self.assertTrue(result["intent"]["libraryOnly"])
        self.assertTrue(result["intent"]["excludePlayedToday"])
        self.assertEqual({"three", "four"}, set(result["trackIds"]))

    async def test_unavailable_model_is_an_honest_deterministic_fallback(self):
        result = await plan_music(self.request(), FakeModel(None))
        self.assertEqual("rules", result["source"])
        self.assertEqual(len(result["trackIds"]), len(set(result["trackIds"])))
        self.assertNotEqual("one", result["trackIds"][1])

    async def test_valid_model_intent_influences_real_inventory(self):
        result = await plan_music(self.request(), FakeModel(MusicIntent(query="Night", confidence=.9).model_dump()))
        self.assertEqual("model", result["source"])
        self.assertEqual(["one"], result["trackIds"])

    def test_hard_filters_precede_ranking_and_unknown_query_has_no_fake_results(self):
        request = self.request(blockedIds=["three"])
        self.assertEqual([], select_tracks(request, MusicIntent(query="invented title")))
        self.assertEqual(["two"], [track.id for track in select_tracks(request, MusicIntent(excludedArtists=["FIRST"]))])

    def test_no_acoustic_claims_or_urls_can_be_injected(self):
        for payload in [{"query": "https://example.com"}, {"bpm": 120}, {"confidence": float("nan")}, {"action": "delete"}]:
            with self.assertRaises(ValidationError):
                MusicIntent.model_validate(payload)

    def test_offline_controls_do_not_turn_song_titles_into_transport_commands(self):
        self.assertEqual("play", fallback_intent("Play Pause by An Artist").action)
        self.assertEqual("pause", fallback_intent("pause").action)
        self.assertEqual("Telugu songs", fallback_intent("Play Telugu songs, only from my library; no repeats today").query)

    async def test_model_cannot_hijack_a_song_title_as_a_transport_command(self):
        request = self.request()
        request.transcript = "Play Pause by An Artist"
        result = await plan_music(request, FakeModel(MusicIntent(action="pause", confidence=.99).model_dump()))
        self.assertEqual("rules", result["source"])
        self.assertEqual("play", result["intent"]["action"])

    async def test_incomplete_model_response_uses_local_interpretation(self):
        result = await plan_music(self.request(), FakeModel({}))
        self.assertEqual("rules", result["source"])


if __name__ == "__main__":
    unittest.main()
