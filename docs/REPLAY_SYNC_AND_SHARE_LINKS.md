# Replay sync and Android share links

Add this match inside the existing Firestore rules under
`match /databases/{database}/documents`. Keep the existing library rules. Check
that no broader matching rule grants other accounts access to these documents.

```text
match /users/{uid}/replayMonths/{snapshot} {
  function ownsReplay() {
    return request.auth != null &&
      request.auth.uid == uid &&
      request.auth.token.firebase.sign_in_provider != "anonymous";
  }
  allow read, delete: if ownsReplay();
  allow create, update: if ownsReplay() &&
    request.resource.data.keys().hasOnly(["bucket"]) &&
    request.resource.data.bucket is bytes &&
    request.resource.data.bucket.size() <= 950000;
}
```

On Android, open **Settings → Your data → Sync Replay** after signing into
Vikify. The switch opts this installation into uploading its existing and
future Replay totals. On a fresh installation, sign in to the same account
and enable the switch to restore them. Switching off stops sync on that
installation. Snapshots already uploaded remain in the account.

## Publish share links with Firebase Hosting

The Android app shares songs at `https://vikify.web.app/track/<videoId>`.
Listening invitations use the same host. Site `vikify` was created in
project `vikify-95144` on November 12, 2026. The `share` deployment target
in `.firebaserc` points to that site.

The Hosting configuration deploys `hosting/public`. Its ignore list allows
the hidden `.well-known` folder so Android can retrieve the verification file.
This deployment uses static Hosting and does not require a Go server.

After signing into Firebase, run this from the Music_player folder:

```powershell
firebase.cmd deploy --only hosting:share --project vikify-95144
```

Open this address and confirm it returns the JSON certificate association:

`https://vikify.web.app/.well-known/assetlinks.json`

The certificate in `hosting/public/.well-known/assetlinks.json` was verified
against `dist/Vikify-2.0.1.apk` (package `com.music.vikify`). If a newly built
APK uses another signing certificate, add that certificate's SHA-256 fingerprint
to the array too. When distributing through Google Play, use the Play app
signing certificate. The verification file must return HTTP 200 JSON directly.

Build and distribute the updated Android app signed with the existing release
key so installed apps recognize the new address. The app also accepts earlier
`vikify-95144.web.app` links. The earlier site keeps its last published content.
A browser fallback provides an **Open in Vikify** button using the custom scheme.

To check domain verification on an attached test phone:

```powershell
adb shell pm verify-app-links --re-verify com.music.vikify
adb shell pm get-app-links com.music.vikify
```

Allow Android time to fetch the association. The output should show
`vikify.web.app: verified`. Keep **Open supported links** enabled in the
phone's Vikify app settings. Tap a newly shared link from a message.

Reference documentation:

- https://firebase.google.com/docs/hosting/quickstart
- https://firebase.google.com/docs/hosting/multisites
- https://developer.android.com/training/app-links/configure-assetlinks