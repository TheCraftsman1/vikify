import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { get, onValue, ref, remove, runTransaction, serverTimestamp, set } from 'firebase/database';

const code = 'PARTY1';
let environment;
const copy = value => structuredClone(value);
const google = uid => environment.authenticatedContext(uid, {
  firebase: { sign_in_provider: 'google.com', identities: { 'google.com': [uid] } },
}).database();
const reference = (uid, path = `partyRooms/${code}`) => ref(google(uid), path);
const person = (name, slot, at = Date.now()) => ({ displayName: name, joinedAtMs: at, slot });
const song = n => ({ videoId: `video${String(n).padStart(6, '0')}`, title: `Song ${n}`, artist: 'Test', fromAutoplay: false, durationMs: 180000 });

function room(now = Date.now(), owner = 'host', capacity = 5) {
  return {
    v: 1, code, hostUid: owner, createdAtMs: now, updatedAtMs: now,
    expiresAtMs: now + 43200000, maxMembers: capacity, hostOnlyControl: false, revision: 0,
    members: { [owner]: person(owner, 's0', now) }, slots: { s0: owner },
    playback: {
      seq: 0, queueSeq: 0, queueLength: 0, queueIndex: -1,
      isPlaying: false, positionMs: 0, anchorMs: now, effectivePositionMs: 0,
      autoplayEnabled: false, updatedAtMs: now,
    },
    queue: { seq: 0, index: -1 },
  };
}

function touch(previous) {
  return { ...copy(previous), revision: previous.revision + 1, updatedAtMs: Date.now() };
}

function join(previous, uid, slot = `s${Object.keys(previous.members).length}`) {
  const next = touch(previous);
  next.members[uid] = person(uid, slot);
  next.slots[slot] = uid;
  return next;
}

function queue(previous, tracks, uid = 'host') {
  const next = touch(previous);
  const index = tracks.length ? 0 : -1;
  next.queue = { seq: previous.queue.seq + 1, index, ...(tracks.length ? { items: tracks } : {}) };
  next.playback = {
    ...next.playback, seq: previous.playback.seq + 1, queueSeq: next.queue.seq,
    queueLength: tracks.length, queueIndex: index, isPlaying: tracks.length > 0,
    positionMs: 0, effectivePositionMs: 0, anchorMs: Date.now() + 1000,
    updatedAtMs: Date.now(), updatedBy: uid,
    ...(tracks.length ? { track: tracks[0], startedBy: uid, startedByName: uid } : {}),
  };
  if (!tracks.length) delete next.playback.track;
  return next;
}

async function seed(value, extras = {}) {
  await environment.withSecurityRulesDisabled(async context => {
    await set(ref(context.database()), { partyRooms: { [code]: value }, ...extras });
  });
}

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-vikify-party',
    database: {
      host: '127.0.0.1', port: 9004,
      rules: readFileSync(new URL('../party-database.rules.json', import.meta.url), 'utf8'),
    },
  });
});
beforeEach(async () => { await environment.clearDatabase(); });
after(async () => { await environment?.cleanup(); });

const nativeFixture = new URL('../../../output/firebase-party-client-payloads.json', import.meta.url);
test('native Android engine and codec payloads pass the production room rules', { skip: !existsSync(nativeFixture) }, async () => {
  const fixture = JSON.parse(readFileSync(nativeFixture, 'utf8'));
  const delta = Date.now() - fixture.baseMs;
  const clockFields = new Set(['createdAtMs', 'updatedAtMs', 'expiresAtMs', 'joinedAtMs', 'anchorMs']);
  function shifted(value) {
    if (Array.isArray(value)) return value.map(shifted);
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) =>
      [key, clockFields.has(key) && typeof item === 'number' ? item + delta : shifted(item)]));
    return value;
  }
  for (const [index, step] of fixture.steps.entries()) {
    try { await assertSucceeds(set(reference(step.uid), shifted(step.room))); }
    catch (error) { throw new Error(`Native transition ${index + 1} by ${step.uid} was rejected`, { cause: error }); }
  }
});

test('unauthenticated and anonymous callers cannot read or create rooms; root listing stays denied', async () => {
  await assertFails(set(ref(environment.unauthenticatedContext().database(), `partyRooms/${code}`), room()));
  const anonymous = environment.authenticatedContext('anonymous', { firebase: { sign_in_provider: 'anonymous' } }).database();
  await assertFails(set(ref(anonymous, `partyRooms/${code}`), room(Date.now(), 'anonymous')));
  await assertSucceeds(set(reference('host'), room()));
  await assertFails(get(ref(environment.unauthenticatedContext().database(), `partyRooms/${code}`)));
  await assertFails(get(ref(anonymous, `partyRooms/${code}`)));
  await assertFails(get(reference('host', 'partyRooms')));
  await assertFails(get(reference('host', '/')));
  await assertSucceeds(get(reference('outsider'))); // The unlisted code is the invitation capability.
});

test('creation cannot impersonate another host, add people, forge schema or use an unbounded room code', async () => {
  const value = room();
  const forged = copy(value); forged.hostUid = 'other';
  await assertFails(set(reference('host'), forged));
  const extra = join(value, 'other'); extra.revision = 0;
  await assertFails(set(reference('host'), extra));
  await assertFails(set(reference('host'), { ...value, v: 2 }));
  await assertFails(set(reference('host'), { ...value, admin: true }));
  await assertFails(set(reference('host', 'partyRooms/TOO-LONG'), { ...value, code: 'TOO-LONG' }));
  await assertSucceeds(set(reference('host'), value));
});

test('a join occupies only its own available seat and cannot exceed capacity or duplicate a UID', async () => {
  const original = room(Date.now(), 'host', 2);
  await assertSucceeds(set(reference('host'), original));
  const joined = join(original, 'guest');
  await assertSucceeds(set(reference('guest'), joined));
  await assertFails(set(reference('third'), join(joined, 'third', 's2')));
  const stolen = join(original, 'guest', 's0');
  delete stolen.members.host;
  await assertFails(set(reference('guest'), stolen));
  const duplicated = touch(joined); duplicated.slots.s0 = 'guest';
  await assertFails(set(reference('guest'), duplicated));
  const unindexed = touch(joined); unindexed.members.intruder = person('intruder', 's1');
  await assertFails(set(reference('guest'), unindexed));
});

test('simultaneous joins cannot consume the same final seat', async () => {
  await assertSucceeds(set(reference('host'), room(Date.now(), 'host', 2)));
  const results = await Promise.allSettled(['alice', 'bob'].map(async uid => {
    const target = reference(uid);
    const stop = onValue(target, () => {});
    // A cold RTDB transaction starts from its local cache, which may still be empty.
    try {
      await get(target);
      return await runTransaction(target, previous => {
        if (previous == null || previous.slots.s1) return;
        return join(previous, uid, 's1');
      }, { applyLocally: false });
    } finally { stop(); }
  }));
  assert.equal(results.filter(result => result.status === 'fulfilled' && result.value.committed).length, 1);
  const snapshot = await assertSucceeds(get(reference('host')));
  assert.equal(Object.keys(snapshot.val().members).length, 2);
});

test('members may leave themselves but cannot delete or edit another member', async () => {
  const value = join(join(room(), 'guest'), 'third');
  await seed(value);
  const removedOther = touch(value); delete removedOther.members.third; delete removedOther.slots.s2;
  await assertFails(set(reference('guest'), removedOther));
  const changedName = touch(value); changedName.members.third.displayName = 'Forged';
  await assertFails(set(reference('guest'), changedName));
  const changedAvatar = touch(value); changedAvatar.members.host.avatarUrl = 'https://example.com/fake.png';
  await assertFails(set(reference('guest'), changedAvatar));
  const deletedRequired = touch(value); delete deletedRequired.members.host.displayName;
  await assertFails(set(reference('guest'), deletedRequired));
  const leave = touch(value); delete leave.members.guest; delete leave.slots.s1;
  await assertSucceeds(set(reference('guest'), leave));
});

test('host transfer and capacity compaction preserve member identity; listeners cannot take an active host', async () => {
  const value = join(join(room(), 'guest', 's3'), 'third', 's4');
  await seed(value, { partyLeases: { [code]: { host: Date.now() } } });
  const hijacked = touch(value); hijacked.hostUid = 'guest';
  await assertFails(set(reference('guest'), hijacked));
  const reduced = touch(value); reduced.maxMembers = 3;
  reduced.members.guest.slot = 's1'; reduced.members.third.slot = 's2';
  reduced.slots = { s0: 'host', s1: 'guest', s2: 'third' };
  await assertSucceeds(set(reference('host'), reduced));
  const leave = touch(reduced); delete leave.members.host; delete leave.slots.s0; leave.hostUid = 'guest';
  await assertSucceeds(set(reference('host'), leave));
});

test('host lease expiration permits takeover only by an existing member', async () => {
  const value = join(room(Date.now() - 60000), 'guest');
  await seed(value, { partyLeases: { [code]: { host: Date.now() - 50000 } } });
  const takeover = touch(value); takeover.hostUid = 'guest';
  await assertSucceeds(set(reference('guest'), takeover));
  const outsider = touch(takeover); outsider.hostUid = 'outsider';
  outsider.members.outsider = person('outsider', 's2'); outsider.slots.s2 = 'outsider';
  await assertFails(set(reference('outsider'), outsider));
});

test('only the host changes capacity, control lock or bans; a kicked account cannot rejoin or clear its ban', async () => {
  const value = join(room(), 'guest');
  await seed(value);
  await assertFails(set(reference('guest'), { ...touch(value), maxMembers: 10 }));
  await assertFails(set(reference('guest'), { ...touch(value), hostOnlyControl: true }));
  await assertFails(set(reference('guest', `partyBans/${code}/host`), true));
  await assertSucceeds(set(reference('host', `partyBans/${code}/guest`), true));
  const kicked = touch(value); delete kicked.members.guest; delete kicked.slots.s1;
  await assertSucceeds(set(reference('host'), kicked));
  await assertFails(set(reference('guest'), join(kicked, 'guest', 's1')));
  await assertFails(remove(reference('guest', `partyBans/${code}/guest`)));
  await assertFails(remove(reference('guest', `partyBans/${code}`)));
  await assertSucceeds(get(reference('guest', `partyBans/${code}/guest`)));
  await assertFails(get(reference('guest')));
});

test('playback and queue changes require membership and respect host-only control', async () => {
  const value = join(room(), 'guest');
  await seed(value);
  await assertFails(set(reference('outsider'), queue(value, [song(1)], 'outsider')));
  const playing = queue(value, [song(1), song(2)], 'guest');
  await assertSucceeds(set(reference('guest'), playing));
  const locked = { ...touch(playing), hostOnlyControl: true };
  await assertSucceeds(set(reference('host'), locked));
  await assertFails(set(reference('guest'), queue(locked, [song(3)], 'guest')));
  await assertSucceeds(set(reference('host'), queue(locked, [song(3)])));
});

test('joining and leaving cannot alter playback, delete optional fields or remove queue items', async () => {
  const value = queue(room(), [song(1), song(2)]);
  value.playback.track.thumbnailUrl = 'https://example.com/art.jpg';
  value.queue.items[0].thumbnailUrl = 'https://example.com/art.jpg';
  await seed(value);
  const changed = join(value, 'guest'); changed.playback.positionMs = 12000;
  await assertFails(set(reference('guest'), changed));
  const missingTrack = join(value, 'guest'); delete missingTrack.playback.track;
  await assertFails(set(reference('guest'), missingTrack));
  const deletedArt = join(value, 'guest'); delete deletedArt.playback.track.thumbnailUrl;
  await assertFails(set(reference('guest'), deletedArt));
  const deletedItem = join(value, 'guest'); delete deletedItem.queue.items[1];
  await assertFails(set(reference('guest'), deletedItem));
  const modifiedItem = join(value, 'guest'); modifiedItem.queue.items[0].title = 'Forged';
  await assertFails(set(reference('guest'), modifiedItem));
  await assertSucceeds(set(reference('guest'), join(value, 'guest')));
});

test('bounded dense queues reject oversized arrays, sparse indexes, invalid tracks and missing sequence increments', async () => {
  const value = room();
  await seed(value);
  await assertFails(set(reference('host'), queue(value, Array.from({ length: 27 }, (_, n) => song(n)))));
  const sparse = queue(value, [song(1)]); sparse.queue.items = { 0: song(1), 25: song(2) };
  sparse.playback.queueLength = 26;
  await assertFails(set(reference('host'), sparse));
  const invalid = queue(value, [{ ...song(1), thumbnailUrl: 'file:///sdcard/private.jpg' }]);
  await assertFails(set(reference('host'), invalid));
  const valid = queue(value, [song(1), song(2)]);
  await assertSucceeds(set(reference('host'), valid));
  const wrongSequence = touch(valid); wrongSequence.queue.items[0].title = 'Changed without queue seq';
  await assertFails(set(reference('host'), wrongSequence));
  const jumped = queue(valid, [song(3)]); jumped.playback.seq += 3;
  await assertFails(set(reference('host'), jumped));
});

test('stale queued controls, stale revisions, immutable timestamps and extra fields are denied', async () => {
  const value = room();
  await seed(value);
  await assertFails(set(reference('host'), { ...touch(value), updatedAtMs: Date.now() - 11000 }));
  await assertFails(set(reference('host'), { ...touch(value), updatedAtMs: Date.now() + 6000 }));
  await assertFails(set(reference('host'), { ...touch(value), revision: value.revision }));
  await assertFails(set(reference('host'), { ...touch(value), expiresAtMs: value.expiresAtMs + 1 }));
  await assertFails(set(reference('host'), { ...touch(value), createdAtMs: value.createdAtMs - 1 }));
  await assertFails(set(reference('host'), { ...touch(value), secret: 'unbounded extra data' }));
  await assertSucceeds(set(reference('host'), touch(value)));
});

test('presence and server leases are account-owned, member-scoped and deny stale/future timestamps', async () => {
  const value = join(room(), 'guest');
  await seed(value);
  await assertSucceeds(set(reference('guest', `partyPresence/${code}/guest/device-1`), true));
  await assertFails(set(reference('host', `partyPresence/${code}/guest/device-2`), true));
  await assertFails(set(reference('outsider', `partyPresence/${code}/outsider/device-1`), true));
  await assertFails(get(reference('outsider', `partyPresence/${code}`)));
  await assertSucceeds(get(reference('host', `partyPresence/${code}`)));
  await assertSucceeds(set(reference('guest', `partyLeases/${code}/guest`), serverTimestamp()));
  await assertFails(set(reference('host', `partyLeases/${code}/guest`), serverTimestamp()));
  await assertFails(set(reference('guest', `partyLeases/${code}/guest`), Date.now() - 2000));
  await assertFails(set(reference('guest', `partyLeases/${code}/guest`), Date.now() + 2000));
  const left = touch(value); delete left.members.guest; delete left.slots.s1;
  await assertSucceeds(set(reference('guest'), left));
  await assertSucceeds(remove(reference('guest', `partyPresence/${code}/guest/device-1`)));
  await assertFails(set(reference('guest', `partyPresence/${code}/guest/device-1`), true));
});

test('only an owner can delete a room and no account can write expired rooms', async () => {
  const value = join(room(), 'guest');
  await seed(value);
  await assertFails(remove(reference('guest')));
  await assertFails(remove(reference('outsider')));
  await assertSucceeds(remove(reference('host')));
  const expired = join(room(Date.now() - 43200001), 'guest');
  await seed(expired);
  await assertFails(set(reference('host'), touch(expired)));
  await assertFails(set(reference('third'), join(expired, 'third')));
  await assertSucceeds(remove(reference('host')));
});

test('server validation matches the native client profile, track and playback limits', async () => {
  const original = room();
  const blankName = copy(original); blankName.members.host.displayName = '   ';
  await assertFails(set(reference('host'), blankName));
  const longName = copy(original); longName.members.host.displayName = 'x'.repeat(81);
  await assertFails(set(reference('host'), longName));
  const invalidJoin = copy(original); invalidJoin.members.host.joinedAtMs = original.createdAtMs - 1;
  await assertFails(set(reference('host'), invalidJoin));
  const oldUpdate = copy(original); oldUpdate.updatedAtMs = original.createdAtMs - 1;
  await assertFails(set(reference('host'), oldUpdate));
  await assertSucceeds(set(reference('host'), original));
  const longTrack = queue(original, [{ ...song(1), title: 'x'.repeat(301) }]);
  await assertFails(set(reference('host'), longTrack));
  const zeroDuration = queue(original, [{ ...song(1), durationMs: 0 }]);
  await assertFails(set(reference('host'), zeroDuration));
  const mismatched = queue(original, [song(1)]); mismatched.playback.track = song(2);
  await assertFails(set(reference('host'), mismatched));
  const zeroAnchor = queue(original, [song(1)]); zeroAnchor.playback.anchorMs = 0;
  await assertFails(set(reference('host'), zeroAnchor));
});
