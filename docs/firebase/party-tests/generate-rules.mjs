import { writeFileSync } from 'node:fs';

// RTDB rules cannot count map children or compare object values. Fixed seats and
// bounded queue indexes let the rules protect every deletion as well as writes.
const room = "root.child('partyRooms').child($code)";
const google = "auth != null && auth.token.firebase.sign_in_provider == 'google.com'";
const member = `${room}.child('members').child(auth.uid).exists()`;
const host = `${room}.child('hostUid').val() == auth.uid`;
const controls = `(${member} && (${room}.child('hostOnlyControl').val() == false || ${host}))`;
const fresh = "newData.child('updatedAtMs').isNumber() && newData.child('updatedAtMs').val() >= now - 10000 && newData.child('updatedAtMs').val() <= now + 5000";
const seats = Array.from({ length: 10 }, (_, i) => `s${i}`);
const queueIndexes = Array.from({ length: 26 }, (_, i) => `${i}`);
const all = terms => terms.map(term => `(${term})`).join(' && ');
const any = terms => terms.map(term => `(${term})`).join(' || ');
const same = (path, base = 'newData', old = 'data') => `${base}.child('${path}').val() == ${old}.child('${path}').val()`;
const unchangedPlaybackFields = [
  'seq', 'queueSeq', 'queueLength', 'queueIndex', 'isPlaying', 'positionMs', 'anchorMs',
  'effectivePositionMs', 'updatedBy', 'startedBy', 'startedByName', 'autoplayEnabled', 'updatedAtMs',
];
const trackFields = ['videoId', 'title', 'artist', 'thumbnailUrl', 'durationMs', 'fromAutoplay'];
const unchangedPlayback = all([
  ...unchangedPlaybackFields.map(field => same(`playback/${field}`)),
  'newData.child(\'playback/track\').exists() == data.child(\'playback/track\').exists()',
  ...trackFields.map(field => same(`playback/track/${field}`)),
]);
const unchangedQueue = all([
  same('queue/seq'), same('queue/index'),
  ...queueIndexes.map(index => `newData.child('queue/items/${index}').exists() == data.child('queue/items/${index}').exists()`),
]);
const newHost = "newData.child('hostUid').val()";
const oldHost = `${room}.child('hostUid').val()`;
const oldLease = `root.child('partyLeases').child($code).child(${oldHost})`;
const leaseExpired = `${room}.child('createdAtMs').val() <= now - 45000 && (!${oldLease}.exists() || (${oldLease}.isNumber() && ${oldLease}.val() <= now - 45000))`;

const roomValidation = all([
  "newData.hasChildren(['v','code','hostUid','createdAtMs','updatedAtMs','expiresAtMs','maxMembers','hostOnlyControl','revision','members','slots','playback','queue'])",
  fresh,
  "newData.child('updatedAtMs').val() >= newData.child('createdAtMs').val()",
  `newData.child('members').child(${newHost}).exists()`,
  // A removed child does not run its own .validate rule. Guard all old seats here.
  ...seats.map(slot => `!data.child('slots/${slot}').exists() || ${host} || data.child('slots/${slot}').val() == auth.uid || newData.child('members').child(data.child('slots/${slot}').val()).exists()`),
  // Joining/leaving cannot alter playback or delete queue items.
  `!data.exists() || ${controls} || (${unchangedPlayback})`,
  `!data.exists() || ${controls} || (${unchangedQueue})`,
  // Cross-check queue summary values without unsupported child-count helpers.
  "newData.child('playback/queueSeq').val() == newData.child('queue/seq').val()",
  "newData.child('playback/queueIndex').val() == newData.child('queue/index').val()",
  ...queueIndexes.map(index => `newData.child('queue/index').val() != ${index} || newData.child('playback/track/videoId').val() == newData.child('queue/items/${index}/videoId').val()`),
  any([
    "newData.child('playback/queueLength').val() == 0 && !newData.child('queue/items').exists() && newData.child('queue/index').val() == -1",
    ...queueIndexes.map((index, i) => all([
      `newData.child('playback/queueLength').val() == ${i + 1}`,
      `newData.child('queue/items/${index}').exists()`,
      i < 25 ? `!newData.child('queue/items/${i + 1}').exists()` : 'true',
      `newData.child('queue/index').val() >= -1 && newData.child('queue/index').val() <= ${i}`,
    ])),
  ]),
  `!data.exists() || newData.child('queue/seq').val() != data.child('queue/seq').val() || (${all(queueIndexes.map(index => `newData.child('queue/items/${index}').exists() == data.child('queue/items/${index}').exists()`))})`,
]);

const number = (min, max) => `newData.isNumber() && newData.val() % 1 == 0 && newData.val() >= ${min} && newData.val() <= ${max}`;
const text = (min, max) => `newData.isString() && newData.val().length >= ${min} && newData.val().length <= ${max}`;
const unchangedOrControl = `${controls} || newData.val() == data.val()`;

function trackRules(depth, prefix = '') {
  return {
    '.validate': all([
      "newData.hasChildren(['videoId','title','artist','fromAutoplay'])",
      `!${room}.exists() || ${controls} || (${all(trackFields.map(field => same(field)))})`,
      depth === 3 ? `!${room}.exists() || newData.parent().parent().child('seq').val() != ${room}.child('queue/seq').val() || (${all(trackFields.map(field => same(field)))})` : 'true',
      prefix || 'true',
    ]),
    videoId: { '.validate': text(1, 128) },
    title: { '.validate': text(0, 300) },
    artist: { '.validate': text(0, 300) },
    thumbnailUrl: { '.validate': `${text(1, 1000)} && newData.val().matches(/^https?:\\/\\/.+/)` },
    durationMs: { '.validate': number(1, 86400000) },
    fromAutoplay: { '.validate': 'newData.isBoolean()' },
    '$other': { '.validate': false },
  };
}

const queueItems = Object.fromEntries(queueIndexes.map((index, i) => [index,
  trackRules(3, i === 0 ? 'true' : `newData.parent().child('${i - 1}').exists()`),
]));
queueItems.$other = { '.validate': false };

const playback = {
  '.validate': all([
    "newData.hasChildren(['seq','queueSeq','queueLength','queueIndex','isPlaying','positionMs','anchorMs','effectivePositionMs','autoplayEnabled','updatedAtMs'])",
    // Transport metadata may change without changing the current playback sequence.
    `!data.exists() || newData.child('seq').val() == data.child('seq').val() || newData.child('seq').val() == data.child('seq').val() + 1`,
    `!data.exists() || newData.child('seq').val() != data.child('seq').val() || (${all([
      ...unchangedPlaybackFields.filter(field => field !== 'seq').map(field => same(field)),
      "newData.child('track').exists() == data.child('track').exists()",
      ...trackFields.map(field => same(`track/${field}`)),
    ])})`,
    "newData.child('isPlaying').val() == false || newData.child('track').exists()",
  ]),
  seq: { '.validate': `${number(0, 9007199254740991)} && (${unchangedOrControl} || !${room}.exists())` },
  queueSeq: { '.validate': `${number(0, 9007199254740991)} && (${unchangedOrControl} || !${room}.exists())` },
  queueLength: { '.validate': `${number(0, 26)} && (${unchangedOrControl} || !${room}.exists())` },
  queueIndex: { '.validate': `${number(-1, 25)} && (${unchangedOrControl} || !${room}.exists())` },
  isPlaying: { '.validate': `newData.isBoolean() && (${unchangedOrControl} || !${room}.exists())` },
  positionMs: { '.validate': `${number(0, 86400000)} && (${unchangedOrControl} || !${room}.exists())` },
  anchorMs: { '.validate': `${number(1, 9007199254740991)} && (${unchangedOrControl} || !${room}.exists())` },
  effectivePositionMs: { '.validate': `${number(0, 86400000)} && (${unchangedOrControl} || !${room}.exists())` },
  autoplayEnabled: { '.validate': `newData.isBoolean() && (${unchangedOrControl} || !${room}.exists())` },
  updatedAtMs: { '.validate': `${number(1, 9007199254740991)} && (${unchangedOrControl} || !${room}.exists())` },
  updatedBy: { '.validate': `${text(1, 128)} && (!${room}.exists() || newData.val() == data.val() || (${controls} && newData.val() == auth.uid))` },
  startedBy: { '.validate': `${text(1, 128)} && (${unchangedOrControl} || !${room}.exists())` },
  startedByName: { '.validate': `${text(0, 200)} && (${unchangedOrControl} || !${room}.exists())` },
  track: trackRules(2),
  '$other': { '.validate': false },
};

const rules = {
  rules: {
    '.read': false,
    '.write': false,
    partyRooms: {
      $code: {
        '.read': `${google} && $code.matches(/^[A-Z0-9]{6}$/) && !root.child('partyBans').child($code).child(auth.uid).exists() && (!data.exists() || data.child('expiresAtMs').val() > now || data.child('members').child(auth.uid).exists())`,
        '.write': all([
          google,
          '$code.matches(/^[A-Z0-9]{6}$/)',
          any([
            `!newData.exists() && data.exists() && ${host}`,
            all([
              'newData.exists()',
              `!root.child('partyBans').child($code).child(auth.uid).exists()`,
              any([
                `!data.exists() && newData.child('hostUid').val() == auth.uid && newData.child('members').child(auth.uid).exists()`,
                `data.exists() && data.child('expiresAtMs').val() > now && (${member} || newData.child('members').child(auth.uid).exists())`,
              ]),
            ]),
          ]),
        ]),
        '.validate': roomValidation,
        v: { '.validate': 'newData.val() == 1' },
        code: { '.validate': 'newData.isString() && newData.val() == $code' },
        hostUid: { '.validate': all([
          text(1, 128),
          any([
            `!${room}.exists() && newData.val() == auth.uid`,
            `newData.val() == ${oldHost}`,
            host,
            `${member} && newData.val() == auth.uid && (${leaseExpired})`,
          ]),
        ]) },
        createdAtMs: { '.validate': `${number(1, 9007199254740991)} && ((!data.exists() && newData.val() >= now - 10000 && newData.val() <= now + 5000) || newData.val() == data.val())` },
        updatedAtMs: { '.validate': number(1, 9007199254740991) },
        expiresAtMs: { '.validate': `${number(1, 9007199254740991)} && newData.val() == newData.parent().child('createdAtMs').val() + 43200000 && (!data.exists() || newData.val() == data.val())` },
        maxMembers: { '.validate': `${number(2, 10)} && (!${room}.exists() || ${host} || newData.val() == data.val())` },
        hostOnlyControl: { '.validate': `newData.isBoolean() && (!${room}.exists() || ${host} || newData.val() == data.val())` },
        revision: { '.validate': `${number(0, 9007199254740991)} && ((!data.exists() && newData.val() == 0) || newData.val() == data.val() + 1)` },
        members: {
          $uid: {
            '.validate': all([
              '$uid.matches(/^[A-Za-z0-9_-]{1,128}$/)',
              "newData.hasChildren(['displayName','joinedAtMs','slot'])",
              "newData.parent().parent().child('slots').child(newData.child('slot').val()).val() == $uid",
              any([
                `!data.exists() && $uid == auth.uid`,
                all([
                  'data.exists()',
                  same('joinedAtMs'),
                  `(${host} || $uid == auth.uid || ${same('slot')})`,
                  `($uid == auth.uid || (${same('displayName')} && ${same('avatarUrl')}))`,
                ]),
              ]),
            ]),
            displayName: { '.validate': `${text(1, 80)} && newData.val().matches(/.*[^ \\t\\r\\n].*/)` },
            avatarUrl: { '.validate': `${text(1, 2000)} && newData.val().matches(/^https?:\\/\\/.+/)` },
            joinedAtMs: { '.validate': `${number(1, 9007199254740991)} && newData.val() >= newData.parent().parent().parent().child('createdAtMs').val() && newData.val() < newData.parent().parent().parent().child('expiresAtMs').val() && (data.exists() || (newData.val() >= now - 10000 && newData.val() <= now + 5000))` },
            slot: { '.validate': "newData.isString() && newData.val().matches(/^s[0-9]$/)" },
            '$other': { '.validate': false },
          },
        },
        slots: Object.fromEntries([
          ...seats.map((slot, i) => [slot, { '.validate': all([
            text(1, 128),
            `newData.parent().parent().child('maxMembers').val() > ${i}`,
            `newData.parent().parent().child('members').child(newData.val()).child('slot').val() == '${slot}'`,
            any([
              'newData.val() == data.val()', host,
              '!data.exists() && newData.val() == auth.uid',
              'data.val() == auth.uid && newData.val() == auth.uid',
            ]),
          ]) }]),
          ['$other', { '.validate': false }],
        ]),
        playback,
        queue: {
          '.validate': all([
            "newData.hasChildren(['seq','index'])",
            `!data.exists() || newData.child('seq').val() == data.child('seq').val() || newData.child('seq').val() == data.child('seq').val() + 1`,
          ]),
          seq: { '.validate': `${number(0, 9007199254740991)} && (${unchangedOrControl} || !${room}.exists())` },
          index: { '.validate': `${number(-1, 25)} && (${unchangedOrControl} || !${room}.exists())` },
          items: queueItems,
          '$other': { '.validate': false },
        },
        '$other': { '.validate': false },
      },
    },
    partyBans: {
      $code: {
        '.read': `${google} && (${member} || ${host})`,
        '.write': `${google} && ${room}.exists() && ${host}`,
        $uid: {
          '.read': `${google} && ($uid == auth.uid || ${member})`,
          '.write': `${google} && ${room}.exists() && ${host}`,
          '.validate': "newData.val() == true && $uid != auth.uid",
        },
      },
    },
    partyPresence: {
      $code: {
        '.read': `${google} && ${member}`,
        $uid: {
          $device: {
            '.write': `${google} && auth.uid == $uid && (!newData.exists() || (${member} && ${room}.child('expiresAtMs').val() > now))`,
            '.validate': "newData.val() == true && $device.matches(/^[A-Za-z0-9_-]{1,100}$/)",
          },
        },
      },
    },
    partyLeases: {
      $code: {
        '.read': `${google} && ${member}`,
        $uid: {
          '.write': `${google} && auth.uid == $uid && ${member} && ${room}.child('expiresAtMs').val() > now`,
          '.validate': 'newData.isNumber() && newData.val() >= now - 1000 && newData.val() <= now + 1000',
        },
      },
    },
  },
};

const payload = JSON.stringify(rules, null, 2) + '\n';
writeFileSync(new URL('../party-database.rules.json', import.meta.url), payload);
// Firebase CLI requires its configured rules file to stay inside its project directory.
writeFileSync(new URL('rules.generated.json', import.meta.url), payload);
