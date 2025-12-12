# Vikify

Spotify-lookalike music player UI built with React + Vite, packaged for Android using Capacitor.

## Web dev

- Install: `npm install`
- Run: `npm run dev`
- Test: `npm test`

## Android (Capacitor)

This repo builds Android with `compileSdkVersion = 35` and sets Java compile options to Java 21 in the generated Capacitor Gradle files.

### Required: JDK 21 (not Java 25)

If you see this error when running Gradle:

`Unsupported class file major version 69`

it usually means Gradle is being run with an unsupported Java runtime (often Java 25/EA). Fix by selecting JDK 21.

**Android Studio**
- Settings → Build, Execution, Deployment → Build Tools → Gradle → **Gradle JDK** → select **JDK 21**

**CLI (PowerShell)**
- Ensure `JAVA_HOME` points to a JDK 21 install, then retry:
	- `cd android`
	- `./gradlew.bat :app:assembleDebug`

### About “errors” in node_modules Java files

Files under `node_modules/@capacitor/*/android/...` are library sources. VS Code may show red squiggles there if it isn’t configured with the Android SDK/Gradle classpath.

Treat the Gradle build output / Android Studio build as the source of truth. Avoid editing `node_modules` directly (changes will be overwritten on reinstall).

## Backend (required for Spotify public sections + YouTube streaming)

The backend expects Spotify credentials via environment variables (they are not hard-coded in the repo):

- `SPOTIFY_CLIENT_ID`
- `SPOTIFY_CLIENT_SECRET`

If these are missing, endpoints like `/spotify/featured-playlists` and `/spotify/new-releases` will return errors.
