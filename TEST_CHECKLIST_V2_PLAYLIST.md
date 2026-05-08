# Prototype Test Checklist V2: Playlist

Use this checklist after setting a playlist in `config.js`.

## Config Used

//I note that it ignores the videoId if there's a valid playlist: fair enough I guess, no issues

```js
window.ACCESSIBLE_PLAYER_CONFIG = {
  videoId: "rKd-Bmr7e_k",
  playlistId: "PLmGt95b9fl5dHbCq_bWP8CTp6z4i1mP5x",
};
```


## Environment

- Date: 8/5/2026 6pm
- Device: honor magic v5
- Browser: gchrome
- Signed in to Google/YouTube: don't think so
- YouTube Premium: no
- URL used: see playlistId
- Playlist source/topic: audio novel in malay/indonesian

## Playlist Loading

- Page loads: y
- YouTube player loads: y
- Tap Play starts playlist: y
- First video is from expected playlist: y
- Video title is announced: y
- Ads appear: n, so far
- Playlist starts at expected video: y
- Playback continues after first video ends: did not test

## Queue Navigation

- Bottom-right Next moves to next playlist video: y
- Swipe right moves to next playlist video: y
- Bottom-left Previous moves to previous playlist video: y
- Swipe left moves to previous playlist video: y
- Repeated Next does not get stuck: y
- Repeated Previous behaves predictably: y
- End of playlist behavior: not sure
- Start of playlist behavior: looks good

## Speech And Titles

- Titles are understandable:
- Non-English titles are read correctly enough: igs
- TTS announces too often: see feedback in codex chat
- TTS interrupts playback too much:
- Status button gives useful current video info: seems okay
- Speech should be shorter: hard to say
- Speech should be clearer: hard to improve on, yeah? HOWEVER, maybe something can be done about the overlap/instant video playback

## Touch Behavior

- Long-press Play speaks help: y
- Long-press Status speaks help: y
- Long-press Previous speaks help: y
- Long-press Next speaks help: y
- Normal taps do not trigger long-press help: y
- Swipes are not too sensitive: y
- Taps are not mistaken for swipes: y
- Portrait layout feels usable: y

## Caregiver Observations

- Easy to change playlist ID: maybe? i'm alright with it, but i think i may wanna expand it to a general playlist/recently uploaded from a bunch of curated channels
- Need an "Open current video in YouTube" button: i would like it
- Need an "Open current channel in YouTube" button: if we can achieve the above, this one is redundant
- Need a settings/admin area: would be good
- Need playlist names/modes: would be good

## User Feedback

- What felt confusing: -
- What felt pleasant: -
- What action was hard to discover: -
- What action was easy to remember: -
- What caused recovery trouble: -
- Would this work for audio novels: y

## Verdict

- Playlist mode is usable: y
- Biggest issue: overlapping noise?
- Next change to make: up to you
