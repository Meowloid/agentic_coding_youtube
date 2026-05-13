# Changelog

## 2026-05-08

### Added

- Created the first runnable prototype as a browser-based Android/Huawei test.
- Added a four-quadrant control surface:
  - Top left: start playlist/video.
  - Top right: announce current state.
  - Bottom left: previous video.
  - Bottom right: next video.
- Added long-press text-to-speech help for each quadrant.
- Added swipe handling:
  - Swipe right: next video.
  - Swipe left: previous video.
  - Swipe up: announce current state.
  - Swipe down: play/pause.
- Added a YouTube IFrame player integration using the official YouTube embed API.
- Added spoken feedback for app state changes.
- Added `serve.ps1`, a tiny local static server, so the YouTube embed runs from `http://localhost` instead of a direct file URL.
- Used YouTube's IFrame API demo video as the default test video.
- Added `config.js` so test videos/playlists can be changed without editing app logic.
- Added `TEST_CHECKLIST.md` for recording desktop and phone test results.
- Added `TEST_CHECKLIST_V2_PLAYLIST.md` for testing playlist behavior, queue navigation, and audio-novel use cases.
- Added caregiver settings opened by triple-tapping Status.
- Added a caregiver action to open the current video in YouTube.
- Added caregiver panel dismissal by tapping outside the panel or pressing Android/browser Back.
- Updated `Open Current Video In YouTube` to include the current playback timestamp using YouTube's `t=` URL parameter.
- Added triple-tap Play as Home/reset, returning to the starting playlist or configured video.
- Changed Home/reset to cue the starting source without immediately starting playback.
- Suppressed late YouTube `PLAYING` events after Home/reset so triple-tap Play does not announce a stale title.
- Added initial native Android project shell under `android/`.
- Tightened the native Android shell layout so the status panel avoids the system status bar and takes less vertical space.
- Added a native caregiver settings dialog opened by triple-tapping Status instead of immediately launching YouTube.
- Added a short native caregiver dialog tap lockout after opening to avoid accidental button activation.
- Changed native Play to open the configured YouTube video/playlist through Android's normal YouTube handoff.

### Fixed

- Moved the PowerShell server's `Write-Response` helper above the request loop so the server can respond to browser requests correctly.
- Wrapped each server request in error handling and added a shutdown message so stopping the script from PowerShell is clearer.
- Suppressed misleading "Paused" announcements during playlist next/previous transitions.
- Prevented the final tap of the Status triple-tap from immediately activating caregiver panel buttons.
- Prevented the final tap of the Status triple-tap from immediately closing the caregiver panel through backdrop dismissal.

### Changed

- Adjusted the layout to treat portrait phone use as the primary case: the status area now sits above the four large touch zones instead of overlaying them.
- Changed the local server to listen on the local network and print phone-accessible URLs for Android testing.
- Split TTS language configuration into `interfaceLang` and `titleLang`, so controls can stay in English while video titles use Indonesian or Malay.

### Documented

- Added a future caregiver/settings story to the feasibility notes, including an "Open current video in YouTube" escape hatch for checking channels and new audio novel uploads.
- Updated README and playlist checklist to reflect current Home/caregiver behavior before native Android work.

### Notes

- This is intentionally a web prototype, not a native Android app yet.
- Reason: this machine does not currently expose `java`, `gradle`, or `adb` in the shell, so a native Android build would need Android Studio/JDK setup first.
- The web prototype lets us test the main uncertainty earlier: whether her Huawei/Android browser can play YouTube in a signed-in Premium context.
- For Premium testing, open this prototype in the same browser where her Google/YouTube Premium account is signed in.
