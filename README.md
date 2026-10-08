# Daylight · Kotlin Android prototype

A small habit tracker demonstrating account registration, login, cryptographically protected credentials, editable account settings, and a REST API backed by SQLite. Both the Android app and the backend are written in **Kotlin**.

## Assignment requirements

| Requirement | Implementation |
|---|---|
| Register and log in | Email/password registration and login, duplicate-account errors, incorrect-password feedback, expiring sessions and logout |
| Protect passwords | Server-side PBKDF2-HMAC-SHA256, 600,000 iterations, unique random 16-byte salt, constant-time comparison |
| Change settings | Display name, daily goal, dark appearance and in-app nudges; saved in SQLite and restored after login |
| REST API and database | Kotlin/JVM HTTP server, JSON endpoints, parameterized SQL, persistent SQLite database |
| Demonstration | Repeatable live-API UI walkthrough; see `docs/DEMO.md` |

Passwords are **hashed**, rather than reversibly encrypted, following [OWASP password-storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). The app never persists a password. Its bearer session token is encrypted with AES-GCM using Android Keystore. The database stores a SHA-256 digest of each random session token, with a 24-hour expiry.

## Run on a USB-connected Android phone

Requirements: Android Studio with SDK 35 and platform tools, Java 17 or 21, Android 8.0+ phone, USB debugging enabled. The Gradle wrapper downloads Gradle 8.9. Android Studio can also open this repository directly and configure the SDK automatically.

1. Open this folder in Android Studio and let Gradle sync. Select its bundled JDK (21), or another JDK 17/21.
2. In a terminal in this folder, build the app and backend:

   ```powershell
   .\gradlew.bat :app:assembleDebug :server:installDist
   ```

3. Start the database-backed backend in a separate terminal:

   ```powershell
   .\server\build\install\server\bin\server.bat
   ```

4. Connect the phone, accept the debugging prompt, then run:

   ```powershell
   adb devices
   adb reverse tcp:8080 tcp:8080
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   adb shell am start -n com.daylight.app/.MainActivity
   ```

5. Register a new account, open Settings, save changes, sign out and sign in again. The default API URL is `http://127.0.0.1:8080`, which uses the USB tunnel set up above. The phone and computer do not need the same Wi-Fi network.

For an emulator, use `adb reverse` as above, or change **API connection** on the sign-in screen to `http://10.0.2.2:8080`. When multiple devices are connected, pass `adb -s SERIAL` to select the intended device.

## Project structure

```text
app/       Kotlin Android application, native views, encrypted token storage
server/    Kotlin REST API, SQLite schema and authentication tests
docs/      API reference, demo checklist and submission notes
scripts/   PowerShell helpers for installation and recording
```

No accounts are pre-seeded. Registration creates three starter habits. Users can add habits and toggle completion; each account has its own data. Completion is a persistent checklist and does not automatically reset at midnight. The daily goal controls the progress card. In-app nudges control the home-screen reminder text; they are not scheduled push notifications.

## API

See [docs/API.md](docs/API.md) for endpoint examples. SQLite is created as `daylight.db` in the server's working directory. It is excluded from Git along with tokens, logs and local SDK settings. The backend binds only to loopback by default; `PORT`, `BIND_HOST` and `DATABASE_PATH` environment variables can override its configuration.

## Validation

```powershell
.\gradlew.bat :server:test :app:lintDebug :app:assembleDebugAndroidTest
# With the API running, and adb reverse configured:
.\gradlew.bat :app:connectedDebugAndroidTest
```

Backend tests cover password hashing, duplicate registration, invalid credentials, unauthorized access, settings validation, database persistence across restarts, habit ownership, session revocation and authentication throttling. The Android walkthrough exercises the actual views against the actual REST API, including registration, habit completion, settings updates, logout, rejected login and settings restored after successful login.

## Development and deployment boundary

This is a local assignment prototype. The debug APK permits HTTP **only** for `127.0.0.1`, `localhost` and `10.0.2.2` so it can talk to the local API. The release manifest requires HTTPS. Before deploying remotely, host the API behind HTTPS and enter its HTTPS origin in **API connection**. The supplied server uses JDK's HTTP server and a synchronized SQLite connection for a small demo; a public production service would need operational controls, account recovery, observability and stronger distributed abuse protection. No hosted API or production signing key is included.

## Build tool versions

Kotlin 2.0.21 · Android Gradle Plugin 8.7.3 · Gradle 8.9 · compile/target SDK 35 · minimum SDK 26 · SQLite JDBC 3.47.1.0 · Gson 2.11.0.

The debug APK is intended for demonstration and is signed with an automatically generated development key.
