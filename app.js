const PLAYER_CONFIG = window.ACCESSIBLE_PLAYER_CONFIG || {
  videoId: "M7lc1UVf-VE",
  playlistId: "",
  interfaceLang: "en-US",
  titleLang: "en-US",
};

const LONG_PRESS_MS = 750;
const SWIPE_MIN_DISTANCE = 60;
const NAVIGATION_TRANSITION_MS = 1800;
const TRIPLE_TAP_MS = 900;
const CAREGIVER_OPEN_GUARD_MS = 450;

const controls = {
  play: {
    label: "Play",
    help: "Play. Starts the configured YouTube video or playlist.",
  },
  status: {
    label: "Status",
    help: "Status. Speaks what is currently happening.",
  },
  previous: {
    label: "Previous",
    help: "Previous. Goes back to the previous video.",
  },
  next: {
    label: "Next",
    help: "Next. Goes to the next video.",
  },
};

let player;
let playerReady = false;
let hasStarted = false;
let isPlaying = false;
let currentTitle = "No video loaded yet.";
let longPressTimer = null;
let longPressTriggered = false;
let touchStart = null;
let suppressPauseUntil = 0;
let statusTapTimes = [];
let playTapTimes = [];
let caregiverHistoryOpen = false;
let caregiverOpenedAt = 0;

const statusText = document.querySelector("[data-status]");
const titleText = document.querySelector("[data-title]");
const controlButtons = document.querySelectorAll("[data-action]");
const caregiverPanel = document.querySelector("[data-caregiver-panel]");
const caregiverOpenYoutubeButton = document.querySelector("[data-caregiver-open-youtube]");
const caregiverCloseButton = document.querySelector("[data-caregiver-close]");

window.onYouTubeIframeAPIReady = () => {
  player = new YT.Player("youtube-player", {
    videoId: PLAYER_CONFIG.videoId,
    playerVars: buildPlayerVars(),
    events: {
      onReady: handlePlayerReady,
      onStateChange: handlePlayerStateChange,
      onError: handlePlayerError,
    },
  });
};

function buildPlayerVars() {
  const vars = {
    playsinline: 1,
    rel: 0,
    controls: 1,
  };

  if (PLAYER_CONFIG.playlistId) {
    vars.listType = "playlist";
    vars.list = PLAYER_CONFIG.playlistId;
  }

  return vars;
}

function handlePlayerReady(event) {
  playerReady = true;
  updateTitle(event.target);
  setStatus("Ready. Long press any area for help.");
  speak("Ready. Long press any area for help.");
}

function handlePlayerStateChange(event) {
  updateTitle(event.target);

  switch (event.data) {
    case YT.PlayerState.PLAYING:
      isPlaying = true;
      hasStarted = true;
      setStatus(`Playing. ${currentTitle}`);
      speakParts([
        { text: "Playing.", lang: getInterfaceLang() },
        { text: currentTitle, lang: getTitleLang() },
      ]);
      break;
    case YT.PlayerState.PAUSED:
      isPlaying = false;
      if (Date.now() < suppressPauseUntil) {
        break;
      }
      setStatus("Paused.");
      speak("Paused.");
      break;
    case YT.PlayerState.ENDED:
      isPlaying = false;
      setStatus("Video ended. Moving to next.");
      speak("Video ended. Moving to next.");
      nextVideo();
      break;
    case YT.PlayerState.BUFFERING:
      setStatus("Loading.");
      break;
    default:
      break;
  }
}

function handlePlayerError(event) {
  const message = `YouTube playback error ${event.data}. Try opening this page in a browser where YouTube is signed in.`;
  setStatus(message);
  speak(message);
}

function updateTitle(target) {
  if (!target || typeof target.getVideoData !== "function") {
    return;
  }

  const data = target.getVideoData();
  currentTitle = data && data.title ? data.title : currentTitle;
  titleText.textContent = currentTitle;
}

function performAction(action) {
  if (action === "status" && registerStatusTap()) {
    openCaregiverPanel();
    return;
  }

  if (action === "play" && registerPlayTap()) {
    goHome();
    return;
  }

  switch (action) {
    case "play":
      startPlayback();
      break;
    case "status":
      speakStatus();
      break;
    case "previous":
      previousVideo();
      break;
    case "next":
      nextVideo();
      break;
    default:
      break;
  }
}

function registerPlayTap() {
  const now = Date.now();
  playTapTimes = playTapTimes.filter((tapTime) => now - tapTime <= TRIPLE_TAP_MS);
  playTapTimes.push(now);

  if (playTapTimes.length >= 3) {
    playTapTimes = [];
    return true;
  }

  return false;
}

function registerStatusTap() {
  const now = Date.now();
  statusTapTimes = statusTapTimes.filter((tapTime) => now - tapTime <= TRIPLE_TAP_MS);
  statusTapTimes.push(now);

  if (statusTapTimes.length >= 3) {
    statusTapTimes = [];
    return true;
  }

  return false;
}

function startPlayback() {
  if (!playerReady) {
    speak("YouTube player is still loading.");
    return;
  }

  if (!hasStarted && PLAYER_CONFIG.playlistId && typeof player.loadPlaylist === "function") {
    player.loadPlaylist({ list: PLAYER_CONFIG.playlistId });
  } else {
    player.playVideo();
  }

  hasStarted = true;
  setStatus("Starting playback.");
  speak("Starting playback.");
}

function goHome() {
  if (!playerReady) {
    speak("Home.");
    return;
  }

  if (!caregiverPanel.hidden) {
    caregiverPanel.hidden = true;
    caregiverHistoryOpen = false;
  }

  window.speechSynthesis.cancel();
  markNavigationTransition();
  hasStarted = false;
  isPlaying = false;

  if (PLAYER_CONFIG.playlistId && typeof player.loadPlaylist === "function") {
    player.loadPlaylist({ list: PLAYER_CONFIG.playlistId, index: 0, startSeconds: 0 });
    player.pauseVideo();
  } else if (PLAYER_CONFIG.videoId && typeof player.loadVideoById === "function") {
    player.loadVideoById({ videoId: PLAYER_CONFIG.videoId, startSeconds: 0 });
    player.pauseVideo();
  }

  setStatus("Home. Starting source reset.");
  speak("Home.");
}

function previousVideo() {
  if (!playerReady) {
    speak("YouTube player is still loading.");
    return;
  }

  if (typeof player.previousVideo === "function") {
    markNavigationTransition();
    player.previousVideo();
    setStatus("Previous video.");
    speak("Previous.");
  }
}

function nextVideo() {
  if (!playerReady) {
    speak("YouTube player is still loading.");
    return;
  }

  if (typeof player.nextVideo === "function") {
    markNavigationTransition();
    player.nextVideo();
    setStatus("Next video.");
    speak("Next.");
  }
}

function markNavigationTransition() {
  suppressPauseUntil = Date.now() + NAVIGATION_TRANSITION_MS;
}

function togglePlayPause() {
  if (!playerReady) {
    speak("YouTube player is still loading.");
    return;
  }

  if (isPlaying) {
    player.pauseVideo();
  } else {
    player.playVideo();
  }
}

function speakStatus() {
  if (!hasStarted) {
    const message = "Home. No video started yet.";
    setStatus(message);
    speak(message);
    return;
  }

  setStatus(`Current video. ${currentTitle}`);
  speakParts([
    { text: "Current video.", lang: getInterfaceLang() },
    { text: currentTitle, lang: getTitleLang() },
  ]);
}

function openCaregiverPanel() {
  if (!caregiverPanel.hidden) {
    return;
  }

  caregiverPanel.hidden = false;
  caregiverHistoryOpen = true;
  caregiverOpenedAt = Date.now();
  window.history.pushState({ caregiverPanel: true }, "", window.location.href);
  setStatus("Caregiver settings opened.");
  speak("Caregiver settings.");
}

function closeCaregiverPanel(options = {}) {
  if (caregiverPanel.hidden) {
    return;
  }

  if (caregiverHistoryOpen && !options.fromHistory) {
    window.history.back();
    return;
  }

  caregiverPanel.hidden = true;
  caregiverHistoryOpen = false;
  setStatus("Caregiver settings closed.");
  speak("Closed.");
}

function openCurrentVideoInYouTube() {
  if (Date.now() - caregiverOpenedAt < CAREGIVER_OPEN_GUARD_MS) {
    return;
  }

  const videoId = getCurrentVideoId();

  if (!videoId) {
    speak("No current video available.");
    return;
  }

  const currentSeconds = getCurrentVideoSeconds();
  const timeParam = currentSeconds > 0 ? `&t=${currentSeconds}s` : "";
  const url = `https://www.youtube.com/watch?v=${encodeURIComponent(videoId)}${timeParam}`;
  window.open(url, "_blank", "noopener");
}

function getCurrentVideoSeconds() {
  if (playerReady && player && typeof player.getCurrentTime === "function") {
    return Math.max(0, Math.floor(player.getCurrentTime()));
  }

  return 0;
}

function getCurrentVideoId() {
  if (playerReady && player && typeof player.getVideoData === "function") {
    const data = player.getVideoData();
    if (data && data.video_id) {
      return data.video_id;
    }
  }

  return PLAYER_CONFIG.videoId || "";
}

function setStatus(message) {
  statusText.textContent = message;
}

function speak(message) {
  speakParts([{ text: message, lang: getInterfaceLang() }]);
}

function speakParts(parts) {
  if (!("speechSynthesis" in window)) {
    setStatus("Speech is not available in this browser.");
    return;
  }

  window.speechSynthesis.cancel();

  parts.forEach((part) => {
    const utterance = new SpeechSynthesisUtterance(part.text);
    const lang = part.lang || getInterfaceLang();
    utterance.lang = lang;
    utterance.rate = 0.9;
    utterance.pitch = 1;
    utterance.voice = getPreferredVoice(lang);
    window.speechSynthesis.speak(utterance);
  });
}

function getInterfaceLang() {
  return PLAYER_CONFIG.interfaceLang || "en-US";
}

function getTitleLang() {
  return PLAYER_CONFIG.titleLang || getInterfaceLang();
}

function getPreferredVoice(lang) {
  const voices = window.speechSynthesis.getVoices();

  if (!voices.length) {
    return null;
  }

  return voices.find((voice) => voice.lang === lang) ||
    voices.find((voice) => voice.lang && voice.lang.toLowerCase().startsWith(lang.slice(0, 2).toLowerCase())) ||
    null;
}

function beginLongPress(action) {
  longPressTriggered = false;
  window.clearTimeout(longPressTimer);
  longPressTimer = window.setTimeout(() => {
    longPressTriggered = true;
    speak(controls[action].help);
    setStatus(controls[action].help);
  }, LONG_PRESS_MS);
}

function endLongPress(action) {
  window.clearTimeout(longPressTimer);

  if (!longPressTriggered) {
    performAction(action);
  }
}

controlButtons.forEach((button) => {
  const action = button.dataset.action;

  button.addEventListener("pointerdown", (event) => {
    event.preventDefault();
    button.setPointerCapture(event.pointerId);
    beginLongPress(action);
  });

  button.addEventListener("pointerup", (event) => {
    event.preventDefault();
    endLongPress(action);
  });

  button.addEventListener("pointercancel", () => {
    window.clearTimeout(longPressTimer);
  });
});

caregiverOpenYoutubeButton.addEventListener("click", openCurrentVideoInYouTube);
caregiverCloseButton.addEventListener("click", () => {
  if (Date.now() - caregiverOpenedAt >= CAREGIVER_OPEN_GUARD_MS) {
    closeCaregiverPanel();
  }
});
caregiverPanel.addEventListener("click", (event) => {
  if (Date.now() - caregiverOpenedAt < CAREGIVER_OPEN_GUARD_MS) {
    return;
  }

  if (event.target === caregiverPanel) {
    closeCaregiverPanel();
  }
});

window.addEventListener("popstate", () => {
  if (!caregiverPanel.hidden) {
    closeCaregiverPanel({ fromHistory: true });
  }
});

document.addEventListener("touchstart", (event) => {
  const touch = event.changedTouches[0];
  touchStart = { x: touch.clientX, y: touch.clientY };
}, { passive: true });

document.addEventListener("touchend", (event) => {
  if (!touchStart) {
    return;
  }

  const touch = event.changedTouches[0];
  const dx = touch.clientX - touchStart.x;
  const dy = touch.clientY - touchStart.y;
  touchStart = null;

  if (Math.max(Math.abs(dx), Math.abs(dy)) < SWIPE_MIN_DISTANCE) {
    return;
  }

  if (Math.abs(dx) > Math.abs(dy)) {
    dx > 0 ? nextVideo() : previousVideo();
    return;
  }

  dy > 0 ? togglePlayPause() : speakStatus();
}, { passive: true });
