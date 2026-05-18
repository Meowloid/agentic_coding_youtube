# Accessible YouTube Player Prototype

This is Prototype 1 for a simple YouTube-oriented player designed around touch memory and spoken feedback.

The original browser prototype still lives at the repo root. The native Android prototype now lives in `android/` and is the main direction for phone testing.

## How To Run

---
DEPRECATED: Recommended to download the APK off of Github Releases instead of using the webhosted version, which was used for initial prototyping/testing.
---

Run the local server:

```powershell
powershell -ExecutionPolicy Bypass -File .\serve.ps1
```

Then open the printed local URL, usually:

```text
http://localhost:8080/
```

For phone testing, use one of the printed phone URLs, usually something like:

```text
http://192.168.0.4:8080/
```

The laptop and phone must be on the same Wi-Fi network. If the phone cannot connect, Windows Firewall may be blocking the server.

To stop the server, press `Ctrl+C` in the PowerShell window running `serve.ps1`. If PowerShell does not return immediately, close that terminal window or run this from another PowerShell window:

```powershell
Get-Process powershell | Stop-Process
```

For the most realistic test:

1. On the Huawei/Android phone, sign in to YouTube Premium in the browser.
2. Open this project page in that same browser.
3. Tap the top-left quadrant to start playback.
4. Long-press any quadrant to hear what it does.

## Current Controls

- Top left: play the configured YouTube video or playlist.
- Top right: speak the current state.
- Bottom left: previous video.
- Bottom right: next video.
- Swipe right: next video.
- Swipe left: previous video.
- Swipe up: speak current state.
- Swipe down: play or pause.
- Triple tap Status: open caregiver settings.
- Triple tap Play: Home/reset to the starting source.

## Home

Triple tap Play to return Home. In the current prototype, Home means cueing the beginning of the configured playlist or video without starting playback. Press Play again to start.

In future versions, Home can become the stable starting point for multiple sources, such as playlists, latest uploads, or discovery modes.

## Stretch Goals

- Add a discovery queue that behaves more like YouTube recommendations or related videos, while keeping the main controls simple and predictable.
- Add an easy blind-user macro for saving the current video to a caregiver-managed list.
- Explore Google/YouTube account integration so saved videos could optionally go into a real YouTube playlist, similar to Watch Later.
- Keep account integration optional. A local saved-video list is simpler, safer, and more reliable for the prototype, while real YouTube playlist writes would require Google sign-in, OAuth permissions, and more caregiver setup.

## Caregiver Settings

Triple tap the Status control to open caregiver settings. The first caregiver action is `Open Current Video In YouTube`, which opens the currently playing video in a normal YouTube page.

This is meant as an escape hatch for checking channels, comments, or new audio-novel uploads without adding that complexity to the main blind-user interface.

The caregiver panel can be closed with the Close button, by tapping outside the panel, or by pressing the Android/browser Back button. Opening the current video includes the current playback time when available.

## Portrait Phone Behavior

The prototype is designed for portrait use first. The top part of the screen shows the current state, and the lower area is split into four large touch zones.

This means the zones are technically four large buttons rather than perfect mathematical quadrants of the whole phone screen. That is intentional: she gets a stable spoken status area plus four predictable touch regions below it.

## Configuration

The starting video and optional playlist are in `config.js`:

```js
window.ACCESSIBLE_PLAYER_CONFIG = {
  videoId: "rKd-Bmr7e_k",
  playlistId: "",
  interfaceLang: "en-US",
  titleLang: "id-ID",
};
```

Replace `videoId` with a known safe test video. If using a playlist, set `playlistId` to the YouTube playlist ID.

Set `interfaceLang` and `titleLang` to browser-supported speech languages. `interfaceLang` is used for controls like "Next" and "Paused"; `titleLang` is used for video titles.

Useful values to try:

- `id-ID` for Indonesian.
- `ms-MY` for Malay.
- `en-US` for English.

After changing `config.js`, refresh the browser page. The server does not need to restart.

## Testing

Use `TEST_CHECKLIST.md` for single-video testing and `TEST_CHECKLIST_V2_PLAYLIST.md` for playlist testing. The most important early signals are whether playback starts, whether ads appear, whether long-press speech feels reliable, and whether Home/caregiver gestures recover cleanly from mistakes.

## Native Android

The native Android prototype now lives in `android/`.

Open the `android` folder in Android Studio when working on the native app.

Native Android gives better control over:

- TTS language and interruption behavior.
- Gesture detection.
- Android Back behavior.
- Fullscreen/kiosk-style presentation.
- Device-level accessibility settings.
- YouTube handoff through Android intents or WebView experiments.
- Embedded YouTube playback is currently being tested in the native Android shell with the YouTube IFrame Player API.
- Caregiver-managed channel links can now be refreshed into a generated recent-uploads queue.
- Saved channel links are refreshed on app startup when usable links exist.

The Android version should preserve the current tested behavior before adding new features.

For private APK shipping, use GitHub Releases rather than committing APK files into the repo. See [android/README.md](android/README.md).
