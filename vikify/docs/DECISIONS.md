# Decisions

## 2025-12-12 — Mobile scrubbing + UI testing

Chose Pointer Events based scrubbing (preview while dragging, commit seek on release) for Android-friendly control and to avoid repeated seeks during drag that can cause buffering. Also throttled frequent progress updates via `requestAnimationFrame` to reduce re-render jank.

Testing is done with Vitest + Testing Library in JSDOM (with `createPortal` mocked) to keep UI interaction tests fast and deterministic.
