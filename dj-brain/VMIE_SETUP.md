# Vikify music intelligence: connected intent planning

The Android DJ supports a direct NVIDIA API key entered in the app for
Nemotron conversation and music planning. A separate server is not required
for this personal-key setup. The key stays in the app's local settings rather
than being committed to source or sent through a Vikify backend. The existing
NVIDIA Nemotron Lightning configuration is retained:
`nvidia/nemotron-3.5-lightning-30b-a3b` has 30B total parameters and 3B active per
token. This integration does not substitute a different model or describe it as
a 3B-total dense model.

One model interprets a bounded request into a strict music intent. Conventional
code filters and ranks actual supplied library candidates, spaces artists, and
returns only IDs present in that inventory. Android validates those IDs again,
then retrieves music through the existing provider adapters. No model-produced
playback URL, fictional recording, or invented acoustic measurement is accepted.

An optional music-intelligence backend remains available for deployments that
prefer to keep provider credentials on a server and authenticate app requests
with Firebase ID tokens. The setup below describes that optional route.

## Optional server

From `dj-brain`, create a Python environment and install `server/requirements.txt`.
Copy `server/.env.example` to `server/.env`, set the server's NVIDIA key and its
legitimate Firebase project/admin credentials, and run:

```powershell
python -m uvicorn server.app:app --host 127.0.0.1 --port 8001
```

Expose this service through your existing HTTPS host/reverse proxy. Production
requests require Firebase ID-token authentication; development user headers are
accepted only when `DEV_MODE=true`. Do not expose development authentication to
the public internet. The service also enforces bounded request schemas and a
per-user in-memory request limit. Multi-worker deployments need a shared quota
store before scaling that limit across workers.

`POST /v1/music/plan` accepts `transcript`, `inventory`, `blockedIds`,
`playedTodayIds`, `preferredArtists`, and `size`. Inventory records contain an
actual `id`, `title`, `artist`, `inLibrary`, and `playable` flag. The response has
`schemaVersion`, validated `intent`, an ordered subset `trackIds`, `source`, and
a grounded deterministic `explanation`. `/health` reports whether a model is
configured; it does not prove a successful live inference.

`POST /v1/music/conversation` uses the same authentication and per-user request
budget as planning. It accepts `transcript` (1–300 characters), optional
`history` (up to 12 `{role: "user" | "assistant", text}` records, each text at
most 1500 characters), and nullable `listenerName` (80 characters), `currentSong`
(500 characters of title/artist metadata), and `suggestedQuery` (250 characters).
It returns `{reply, query, source}`, where `reply` is at most 1500 characters,
`query` is a nullable music search suggestion, and `source` is `model` or
`rules`. The conversation endpoint cannot execute playback or modify a queue.
Introductions and greetings never become song queries. Model output with
unsupported fields, invalid bounds, query URLs, or claims of completed playback
actions falls back to a deterministic conversation reply. The Android client
may retain its richer local reply when the server reports `rules`.

## Optional backend connection on Android

Set `VMIE_BASE_URL=https://your-existing-backend.example` in this checkout's
`local.properties` or build environment, then rebuild. This value is a public
backend address, not an NVIDIA endpoint or provider key. The phone and TV builds
read the same property. The Android client sends its short-lived Firebase ID
token. A missing signed-in/anonymous Firebase identity, blank endpoint, timeout,
invalid model output, or unavailable backend falls back to the local request
vocabulary and existing recommendations. Transport controls work without LLM
inference.

The studio identifies connected AI only after valid model output; unavailable
or invalid model responses use local conversation and music handling. Neither
`dj-brain/server/.env` nor `dj-brain/.env` exists in this checkout, so these
server files do not currently provide an NVIDIA key. A direct key can be entered
in the app without creating either file or deploying a VMIE service.

Implemented intent constraints include saved-library-only selection, excluding
dated plays from today, and excluded credited artists. Explicit constraints are
rechecked on device and cannot be removed by model output. Native saved
playlists and downloaded songs contribute real candidates; local copies remain
available offline. Requests change upcoming music while the current track can
continue. “Why this song?” describes actual source/ranking evidence and states
that measured audio features are unavailable.

This is the first connected VMIE slice. Acoustic DSP, track/playlist DNA storage,
semantic lyric analysis, richer session arcs, and cross-playlist feature fusion
are still separate work; this change does not fabricate those capabilities.

Tests: `python -m unittest discover -s tests -p 'test_intelligence*.py' -v` and the
shared Kotlin `com.music.vikify.data.intelligence.MusicPlanTest` suite.
