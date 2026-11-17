# Device Google accounts and music connections

Vikify now uses Android Credential Manager for an explicit Google account chooser.
The listener consents to one account; the app does not silently read every Gmail,
obtain its password, or copy another app's login.

## Revised connection flow

Setup always offers **Start listening** below the optional account connections.
Google cloud sign-in shows a connected state without changing that navigation
button into another login. **Start listening** finishes setup and opens Home (or
Library when YouTube Music is already connected). Connections can be added later
from Settings.

1. Optionally sign in with Google for **Vikify cloud backup**. Firebase verifies
   the Google ID token and remembers that identity across normal app restarts.
   This step is not a prerequisite for either music service.
2. Connect YouTube Music directly using the existing website flow. Confirm the live
   Personal or Brand profile with **Use this profile**. The app validates the
   website session before saving it and loading that profile's library.
3. Connect Spotify directly using the existing website flow. Spotify authorizes
   its own account. **Continue with Google**
   requires that Google login method to be linked to the Spotify account.
4. Both music websites start without borrowing the Vikify cloud email. The
   optional **Choose account** button opens Android's native Google picker to
   supply an email hint for that music website only. It does not sign Firebase
   into the chosen account, change the Vikify nickname, or authorize the website.
   The header calls this an **Account hint**, rather than a verified music login.
5. Successful website connections remain local and are reused while valid.
   Setup displays **Session saved** for a stored music session: cookie presence
   does not establish that the provider still accepts it. Each service can be
   reopened independently. **Change Google account** changes only the Vikify
   cloud identity, leaving the active Spotify and YouTube profiles alone.

After a successful YouTube profile confirmation, setup closes and Library opens.
The **Use this profile** button stays below the website so the next step is
visible. Confirmation permits one capture and validation at a time. Closing the
screen or choosing another account cancels pending validation; callbacks from an
old website instance cannot submit or dismiss a later login screen.
A successful Spotify connection also finishes setup and opens its
playlists, with Back returning to Library. Cancelling or failing a connection
leaves setup available, including **Start listening**.

Firebase authentication is not a YouTube Music or Spotify cookie. The first
website login, provider security checks, expired sessions, explicit disconnects,
or reinstalling may require authentication again. WebViews do not share Chrome
or the installed music apps' cookie stores. Google may reject embedded sign-in;
selecting an email or changing the user agent cannot solve that restriction.
Spotify retains its website-session cookie and web-player token flow. This
implementation does not add a Spotify developer application, official OAuth
integration, client ID, client secret, or developer-dashboard setup.

## Browser session isolation

On WebView providers that support AndroidX WebKit's `MULTI_PROFILE` feature,
`BrowserProfiles` assigns separate cookie and web-storage profiles to YouTube
Music accounts, Spotify's interactive login, and Spotify's offscreen token
harvester. In particular, Google's federated-login cookies in one music service
must not silently select the Google account for the other service. The encrypted
YouTube account registry remains the source of saved cookie snapshots; channel
switching uses the selected account's browser profile. The API layer still has
one active YouTube account at a time.

Profile support is checked at runtime, because the installed WebView provider
determines availability. Providers without it use the legacy shared cookie jar
and service-scoped cleanup; they cannot offer the same isolation guarantee.
Disconnecting a music service clears its own saved credentials and browser data
without signing out the Vikify cloud account or the other music service.

## Firebase setup for this test APK

The local `app/google-services.json` contains the Web OAuth client but no Android
OAuth client entry. This can be an outdated download; it does not establish the
current state of the Firebase console. Verify these settings if the native
picker cannot finish:

- Project: `vikify-95144`; Android package: `com.music.vikify`.
- Enable **Google** in Firebase Authentication's sign-in providers.
- In Project settings > General > the Android app, register the test APK's SHA-1:
  `0D:EF:84:C9:86:6E:17:BF:4A:F9:1E:66:82:12:B1:3C:5F:27:B0:B5`.
- The matching SHA-256 is
  `6A:C2:6E:53:75:9E:B8:2F:DE:59:A4:16:B1:52:2D:DC:F5:2C:DA:AA:71:81:9C:BC:6A:0A:10:90:B5:27:D3:7B`.
- Download the updated `google-services.json`, replace the app's config and rebuild.
  Production and Play-distributed APKs need their own signing certificates.

The ID token audience comes from the generated `default_web_client_id` resource,
not a manually pasted or invented client ID. No client secret belongs in the APK.

## Phone verification

- During setup, confirm **Start listening** stays visible on a short screen and
  with larger text. After Google cloud sign-in, use it to reach Home; reopen the
  app and confirm setup stays dismissed.
- Cancel a music connection and confirm setup still offers **Start listening**.
  Failed YouTube profile validation must keep the confirmation screen open.
- With multiple Google accounts on the phone, choose the desired account during
  Google sign-in. Cancel once and confirm the app returns normally.
- Use three different accounts: A for Vikify, B for YouTube Music, and C for
  Spotify. Connecting or changing B/C must leave the Vikify cloud email at A.
  Both music services must also connect when Vikify cloud sign-in is skipped.
- Confirm **Google account in Vikify** displays A. A music screen's optional
  **Account hint** displays only the email explicitly selected for that service;
  the YouTube selector still displays the actual Personal/Brand profiles.
- Complete YouTube Music sign-in and confirm the intended profile. Check liked
  music and playlists, close/reopen Vikify and check them again.
- Tap **Use this profile** repeatedly during slow validation. Only one validation
  should run. Close the screen or change the account before it completes and
  confirm a delayed callback cannot change the later login screen.
- Connect Spotify and inspect its playlists. Refreshing Spotify must leave the
  YouTube connection intact. Close/reopen and check both libraries.
- Repeat account switching on a provider supporting `MULTI_PROFILE` and one using
  the legacy fallback. Verify scoped disconnects and note the fallback's shared
  browser identity limitation.
- Changing the Vikify account must not mislabel either music service as newly
  connected. Disconnecting Spotify must not disconnect YouTube Music.

Real device account authorization needs testing on a phone with Google accounts;
unit tests and an emulator without accounts cannot establish provider approval.

References: [Firebase Google sign-in](https://firebase.google.com/docs/auth/android/google-signin),
[Android Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg),
[browser versus WebView sessions](https://developer.chrome.com/docs/android/custom-tabs),
[Spotify Google login](https://support.spotify.com/us/article/google-login-help/).
