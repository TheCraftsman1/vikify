# Vikify cloud data setup

Replay and saved mixes use **Cloud Firestore**. Party Room uses **Realtime
Database** in the same project; see [Party Room setup](PARTY_ROOM.md). Google
sign-in can work before a database exists, but cannot restore a backup that was
never saved.

1. Open Firebase Console and select **vikify-95144**.
2. Open **Build → Firestore Database**. If needed, create the **Standard** edition
   database with database ID **(default)** and start in locked/production mode.
   Choose the location deliberately; it cannot be changed after creation.
3. Open the **Rules** tab. Replace the editor contents with the complete contents
   of [`firestore.rules`](firestore.rules) beside this file and click **Publish**.
   The file preserves the existing playlist, favorites, feedback, and Replay
   permissions and adds the account's curated-profile backup.
4. Install the updated Vikify APK, sign in with Google, and enable the Replay and
   curated-mix backup switch under **Settings → Your data**. Connect the same
   YouTube Music profile on each device so its curated collections stay together.
5. After a successful backup, open Firestore's **Data** tab. Under
   `users → your Firebase Authentication UID`, the app creates `replayMonths`
   and `curatedProfiles` documents automatically. Do not create empty documents
   manually. A backup document contains a compressed `bucket` field, so its
   contents are not readable as individual rows in the console.

YouTube Music remains responsible for YouTube playlists. Firestore stores
Vikify's Replay snapshots and curated profile data, including saved mix copies
and collection preferences. The new backup does not contain YouTube cookies,
Google tokens, or audio files. Each account can access only its own documents;
anonymous accounts cannot read or write Replay or curated-profile backups.

On a fresh installation, use the same Google account and YouTube Music profile
and enable backup to restore the data. Keep the original installation until its
backup succeeds. An existing local history can seed a new backup; enabling a
database cannot recreate data from an already removed installation.

If backup reports an error, check that the rules above are published in **Cloud
Firestore** for this project and that the device has an internet connection.
Turning the backup switch off stops sync on that installation. Existing cloud
documents remain until deleted by the account owner.

After a recommendation reset with cloud sync enabled, aggregate learning resumes
on the following calendar day. A partial daily cloud bucket cannot reliably
separate late pre-reset plays from new listening. Replay totals remain intact.
