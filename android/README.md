# Android Prototype

This is the first native Android shell for the accessible YouTube player.

It intentionally starts small:

- Native portrait UI.
- Four large controls.
- Native Android TextToSpeech.
- Long-press help.
- Swipe gestures.
- Triple tap Play for Home.
- Triple tap Status for caregiver settings.
- Caregiver settings can open the configured YouTube video.

The first native goal is to validate Android touch/TTS behavior on a real phone before embedding or controlling YouTube playback directly.

## Open In Android Studio

Open the `android` folder as the Android Studio project.

Android Studio should sync the Gradle project and download any missing Gradle/Android plugin files.

## Run On Phone

1. Connect the phone with USB debugging enabled.
2. Confirm `adb devices` shows the phone as `device`.
3. Press Run in Android Studio.

From PowerShell, this project can also be built with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

## Current Limitations

- No embedded YouTube playback yet.
- The caregiver action opens a hardcoded YouTube video.
- Playlist/source config is still in the browser prototype.
