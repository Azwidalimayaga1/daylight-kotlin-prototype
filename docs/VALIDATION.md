# Validation record

Validated on 8 October 2026.

| Check | Result |
|---|---|
| Kotlin backend tests | 3 tests passed; no failures or errors |
| Android debug APK | Built successfully |
| Android instrumentation APK | Built successfully |
| Android lint | Passed, with no errors; advisory warnings remain for pinned versions, target SDK and backup configuration |
| Live Android UI walkthrough | 1 test passed in 90.522 seconds against the running Kotlin REST API |
| Password-field display | Instrumentation asserts that password masking is active |
| API/database health | HTTP `/health` reports `ok` with SQLite connected |
| APK signature | Verified with Android SDK `apksigner` |
| GitHub build | [Passed](https://github.com/Azwidalimayaga1/daylight-kotlin-prototype/actions/runs/37758801494) |

The walkthrough creates a unique account, completes a habit, changes the display name, daily goal, dark appearance and in-app nudges, saves settings, signs out, rejects an incorrect password, signs in correctly, and verifies that the profile, settings and completed habit were restored.

Backend tests separately verify password salts and hashes, duplicate registration, unauthorized access, invalid credentials, invalid settings, database persistence across backend restarts, isolation between users' habits, logout/session revocation and authentication throttling.

## Demonstration environment

The supplied MP4 is a recording of the real Android app on an **Android 16 / API 36 emulator**, using the real Kotlin API and SQLite database. It is labeled as an emulator demonstration and contains no simulated API responses. It includes a small explanatory banner and removes the initial blank launch frames. Encoding: H.264, 720 × 1710, 24 fps, approximately 81 seconds, silent.

## Remaining assignment requirement

**A physical-phone recording remains pending.** ADB did not detect a connected physical Android phone during this session. The emulator video demonstrates the features, but the assignment's physical-phone requirement still needs a connected and authorized phone. The same `scripts/record-demo.ps1` walkthrough can record it once the phone appears in `adb devices`.

The API is configured for local development. The repository includes source and instructions for running it; no public hosted API or production-signed APK was deployed.
