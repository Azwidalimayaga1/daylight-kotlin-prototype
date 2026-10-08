# Demonstration checklist

Record the actual app running with the backend online. A physical-phone recording meets the assignment's mobile-phone requirement. An emulator recording should be identified as an emulator and used only if the assessor accepts it.

1. Show the sign-in screen and select **Create an account**.
2. Enter a name, a new email and a password of at least eight characters.
3. Select **Create account** and show the home screen.
4. Tap a habit; show the progress card and saved confirmation.
5. Open **Settings**. Change the display name and goal, enable dark appearance and disable in-app nudges.
6. Select **Save changes**. Show the saved confirmation and dark theme.
7. Return home and show the updated name and goal.
8. Open Settings and **Sign out**.
9. Attempt login with an incorrect password, showing the error.
10. Log in with the correct password. Show that settings and habit completion were restored from the API/database.

Password protection is a backend operation and has no visible encryption animation. Explain in narration or your submission that the backend stores a salted PBKDF2 hash. The tests in `server/src/test/.../ApiTest.kt` verify the stored salt/hash lengths and credential verification. Do not show a real password, account or token in a submission.

## Automated live-app walkthrough recording

The `DemoTest` instrumentation test follows this checklist using real UI controls and a running server. It creates a unique demo email each run. Build both APKs, start the API, and run:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
.\scripts\record-demo.ps1 -Device YOUR_ADB_SERIAL -OutputPath C:\path\to\daylight-demo.mp4
```

This script installs the prototype and test APK, clears **only the Daylight app's data**, sets up the USB tunnel, starts Android's screen recorder, and runs the walkthrough. The test APK is only used for automation. The final video shows the prototype making live REST requests, without mock responses.

## Manual phone recording

After starting the API and configuring `adb reverse`, use the phone's built-in screen recorder or:

```powershell
adb -s YOUR_SERIAL shell screenrecord --time-limit 180 /sdcard/daylight-demo.mp4
# Complete the checklist while recording; stop the command with Ctrl+C.
adb -s YOUR_SERIAL pull /sdcard/daylight-demo.mp4 .\daylight-demo.mp4
```

Use only demonstration credentials. Keep other apps and personal notifications out of the recording.
