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
- Caregiver settings can manage a stored list of YouTube channel links.
- The channel manager can refresh recent uploads from saved channel links and load them as a generated queue.
- The channel manager shows the last refresh report so caregiver testing can see whether links resolved, feeds failed, or videos were added.
- On startup, the app refreshes saved channel links into the curated recent-uploads source when usable channel links exist.
- The first source is the working audio-novel playlist.
- The second source is curated channel latest uploads.

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

## Private Release APK

The debug APK is enough for quick testing. For a cleaner private APK, create a local signing key once:

```powershell
cd C:\Users\Tairul\Desktop\tahir\agentic_coding_youtube\android
powershell -ExecutionPolicy Bypass -File .\create-release-keystore.ps1
```

Choose passwords you can keep somewhere safe. The script creates:

- `android/accessible-youtube-release.jks`
- `android/keystore.properties`

Both files are ignored by Git because they are private signing material.

Then build the signed release APK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleRelease
```

The APK will be here:

```text
android/app/build/outputs/apk/release/app-release.apk
```

Keep the `.jks` file. Android treats updates as the same app only when future APKs are signed with the same key.

## Upload To GitHub Releases

Do not commit APK files into Git. APKs are binary build outputs, so GitHub Releases is the right place to share them.

For the current prototype, upload this file:

```text
android/app/build/outputs/apk/release/app-release.apk
```

Suggested release values:

- Tag: `v0.1.0`
- Title: `Accessible YouTube Player v0.1.0`
- Asset: `app-release.apk`

On GitHub:

1. Open the repository page.
2. Go to `Releases`.
3. Choose `Draft a new release`.
4. Create or choose tag `v0.1.0`.
5. Upload `app-release.apk`.
6. Publish the release.

On the Android phone:

1. Open the GitHub release page.
2. Download `app-release.apk`.
3. Allow install from the browser or file manager when Android asks.
4. Install the APK.

For future updates, keep using the same keystore and increase `versionCode` and `versionName` in `android/app/build.gradle.kts` before building a new release APK.

## Future Discovery And Saving

A possible stretch goal is a discovery source that builds a queue from related or recommended videos. The main risk is keeping it useful without turning the app into a full YouTube replacement.

Another stretch goal is a simple blind-user macro for saving the current video. The lowest-risk version would save videos locally inside the app for caregiver review. A fuller version could save to a real YouTube playlist, similar to Watch Later, but that would require Google account sign-in and playlist-write permissions.

## Current Limitations

- Embedded playback is experimental and may behave differently across devices, WebView versions, and signed-in YouTube/Premium states.
- Source slots are currently hardcoded in `MainActivity.java`.
- Channel links are stored in Android app storage through the caregiver Manage Channels dialog.
- Direct `/channel/UC...` YouTube links, raw `UC...` IDs, and YouTube RSS feed URLs with `channel_id=UC...` are the most reliable channel inputs. `@handle` links use a best-effort lookup and may fail if YouTube changes its page markup.
- Recent uploads are generated from YouTube RSS feeds. This avoids an API key, but it is still network-dependent.
- Refresh diagnostics include exception messages, so HTTP/network failures should be visible in the channel manager.
- Video title TTS currently uses the English voice because YouTube may return autotranslated titles.
