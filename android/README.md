# Android Prototype

This is the first native Android shell for the accessible YouTube player.

It intentionally starts small:

- Native portrait UI.
- Four large controls.
- Native Android TextToSpeech.
- Embedded YouTube WebView using the IFrame Player API.
- Long-press help.
- Swipe gestures.
- Triple tap Play for Home.
- Triple tap Status for caregiver settings.
- Play starts playback inside the app.
- Previous/Next call the embedded player.
- Home cues the starting playlist source.
- Caregiver settings can also open the configured YouTube source.
- Caregiver settings can switch between hardcoded source slots.
- Caregiver settings can manage a stored list of YouTube channel links for future latest-upload refresh.
- The first source is the working audio-novel playlist.
- The second source is a placeholder for curated channel latest uploads.

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

- Embedded playback is experimental and may behave differently across devices, WebView versions, and signed-in YouTube/Premium states.
- Source slots are currently hardcoded in `MainActivity.java`.
- Channel links are stored in Android app storage through the caregiver Manage Channels dialog.
- Refresh/generation of recent uploads is not implemented yet.
