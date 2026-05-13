package com.meowloid.accessibleyoutube;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.view.Window;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final long LONG_PRESS_MS = 750;
    private static final long TRIPLE_TAP_MS = 900;
    private static final long CAREGIVER_OPEN_GUARD_MS = 500;
    private static final int TAP_SLOP_DP = 18;
    private static final String EMBED_ORIGIN = "https://www.youtube-nocookie.com";
    private static final Locale INTERFACE_LOCALE = Locale.US;
    private static final Locale TITLE_LOCALE = new Locale("id", "ID");
    private static final Source[] SOURCES = {
            new Source(
                    "Audio novel playlist",
                    SourceType.PLAYLIST,
                    SourceMode.VIDEO_IN_PLAYLIST,
                    "rKd-Bmr7e_k",
                    "PLmGt95b9fl5dHbCq_bWP8CTp6z4i1mP5x",
                    new String[]{}
            ),
            new Source(
                    "Curated channel uploads",
                    SourceType.RECENT_UPLOADS,
                    SourceMode.PLAYLIST,
                    "",
                    "",
                    new String[]{
                            "https://www.youtube.com/@replace-with-a-channel",
                            "https://www.youtube.com/channel/UC_REPLACE_WITH_CHANNEL_ID"
                    }
            )
    };

    private enum SourceType {
        PLAYLIST,
        RECENT_UPLOADS
    }

    private enum SourceMode {
        VIDEO,
        PLAYLIST,
        VIDEO_IN_PLAYLIST
    }

    private static class Source {
        final String name;
        final SourceType type;
        final SourceMode mode;
        final String videoId;
        final String playlistId;
        final String[] channelLinks;

        Source(String name, SourceType type, SourceMode mode, String videoId, String playlistId, String[] channelLinks) {
            this.name = name;
            this.type = type;
            this.mode = mode;
            this.videoId = videoId;
            this.playlistId = playlistId;
            this.channelLinks = channelLinks;
        }

        boolean hasPlayableQueue() {
            return !playlistId.isEmpty() || !videoId.isEmpty();
        }
    }

    private TextToSpeech tts;
    private TextView titleText;
    private TextView statusText;
    private WebView playerWebView;
    private Dialog caregiverDialog;
    private boolean playerReady = false;
    private boolean isPlaying = false;
    private boolean openYouTubeAfterSnapshot = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int currentSourceIndex = 0;
    private String currentTitle = currentSource().name;
    private String currentVideoId = currentSource().videoId;
    private int currentVideoSeconds = 0;
    private long caregiverOpenedAt = 0L;
    private final ArrayDeque<Long> playTaps = new ArrayDeque<>();
    private final ArrayDeque<Long> statusTaps = new ArrayDeque<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(5, 5, 5));
        window.setNavigationBarColor(Color.rgb(5, 5, 5));

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(INTERFACE_LOCALE);
                speak("Ready. Long press any area for help.");
            }
        });

        setContentView(buildLayout());
    }

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(5, 5, 5));

        LinearLayout statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setPadding(24, getStatusBarHeight() + dp(10), 24, dp(12));
        statusPanel.setBackgroundColor(Color.rgb(17, 17, 17));

        titleText = new TextView(this);
        titleText.setText(currentSource().name);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(22);
        titleText.setGravity(Gravity.START);

        statusText = new TextView(this);
        statusText.setText("Preparing controls.");
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(16);
        statusText.setPadding(0, dp(6), 0, 0);

        statusPanel.addView(titleText);
        statusPanel.addView(statusText);
        root.addView(statusPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                getStatusBarHeight() + dp(104)
        ));

        playerWebView = buildPlayerWebView();
        root.addView(playerWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(180)
        ));

        GridLayout controls = new GridLayout(this);
        controls.setColumnCount(2);
        controls.setRowCount(2);

        controls.addView(controlButton("Play", "Play. Starts playback inside this app.", this::handlePlay));
        controls.addView(controlButton("Status", "Status. Speaks what is currently happening.", this::handleStatus));
        controls.addView(controlButton("Previous", "Previous. Goes back.", this::handlePrevious));
        controls.addView(controlButton("Next", "Next. Goes forward.", this::handleNext));

        root.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        return root;
    }

    private Source currentSource() {
        return SOURCES[currentSourceIndex];
    }

    private WebView buildPlayerWebView() {
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new PlayerBridge(), "AndroidPlayer");

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.loadDataWithBaseURL(
                EMBED_ORIGIN,
                buildPlayerHtml(),
                "text/html",
                "UTF-8",
                null
        );

        return webView;
    }

    private Button controlButton(String label, String help, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(24);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        button.setLayoutParams(params);

        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] longPressTriggered = {false};
        final boolean[] flingTriggered = {false};
        final float[] startX = {0f};
        final float[] startY = {0f};

        GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                flingTriggered[0] = true;
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                if (Math.abs(dx) > Math.abs(dy)) {
                    if (dx > 0) {
                        handleNext();
                    } else {
                        handlePrevious();
                    }
                } else {
                    if (dy > 0) {
                        togglePlayPause();
                    } else {
                        handleStatus();
                    }
                }

                return true;
            }
        });

        final Runnable longPressRunnable = () -> {
            longPressTriggered[0] = true;
            speak(help);
            setStatus(help);
        };

        button.setOnTouchListener((view, event) -> {
            detector.onTouchEvent(event);

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                longPressTriggered[0] = false;
                flingTriggered[0] = false;
                startX[0] = event.getX();
                startY[0] = event.getY();
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                handler.removeCallbacks(longPressRunnable);
                float dx = event.getX() - startX[0];
                float dy = event.getY() - startY[0];
                boolean movedBeyondTap = Math.hypot(dx, dy) > dp(TAP_SLOP_DP);

                if (!longPressTriggered[0] && !flingTriggered[0] && !movedBeyondTap) {
                    action.run();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(longPressRunnable);
            }
            return true;
        });

        return button;
    }

    private void handlePlay() {
        if (registerTripleTap(playTaps)) {
            goHome();
            return;
        }

        playEmbeddedSource();
    }

    private void handleStatus() {
        if (registerTripleTap(statusTaps)) {
            openCaregiverDialog();
            return;
        }

        setStatus("Current source. " + currentTitle);
        speakStatusWithTitle("Current source.", currentTitle);
    }

    private void handlePrevious() {
        setStatus("Previous.");
        speak("Previous.");
        callPlayer("previousVideo()");
    }

    private void handleNext() {
        setStatus("Next.");
        speak("Next.");
        callPlayer("nextVideo()");
    }

    private void goHome() {
        closeCaregiverDialog();
        setStatus("Home. " + currentSource().name + " ready.");
        speak("Home.");
        callPlayer("goHome()");
    }

    private void playEmbeddedSource() {
        if (!playerReady) {
            setStatus("YouTube player is still loading.");
            speak("YouTube player is still loading.");
            return;
        }
        if (!currentSource().hasPlayableQueue()) {
            setStatus("This source needs refresh before playback.");
            speak("This source needs refresh before playback.");
            return;
        }

        setStatus("Starting playback.");
        speak("Starting playback.");
        callPlayer("playVideo()");
    }

    private void togglePlayPause() {
        if (!playerReady) {
            setStatus("YouTube player is still loading.");
            speak("YouTube player is still loading.");
            return;
        }

        if (isPlaying) {
            setStatus("Paused.");
            speak("Paused.");
            callPlayer("pauseVideo()");
        } else {
            setStatus("Playing.");
            speak("Playing.");
            callPlayer("playVideo()");
        }
    }

    private void openCaregiverDialog() {
        if (caregiverDialog != null && caregiverDialog.isShowing()) {
            return;
        }

        caregiverDialog = new Dialog(this);
        caregiverDialog.setContentView(buildCaregiverDialogContent());
        caregiverDialog.setCanceledOnTouchOutside(true);

        Window dialogWindow = caregiverDialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        caregiverDialog.setOnDismissListener(dialog -> {
            setStatus("Caregiver settings closed.");
            speak("Closed.");
        });

        caregiverDialog.show();
        caregiverOpenedAt = System.currentTimeMillis();
        setStatus("Caregiver settings opened.");
        speak("Caregiver settings.");
    }

    private View buildCaregiverDialogContent() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(20));
        panel.setBackgroundColor(Color.rgb(17, 17, 17));

        TextView heading = new TextView(this);
        heading.setText("Caregiver Settings");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(24);
        heading.setGravity(Gravity.START);
        panel.addView(heading);

        Button openYouTube = caregiverButton("Open Source In YouTube");
        openYouTube.setOnClickListener(view -> {
            if (caregiverGuardActive()) {
                return;
            }
            openConfiguredSource();
        });
        panel.addView(openYouTube);

        Button switchSource = caregiverButton("Switch Source");
        switchSource.setOnClickListener(view -> {
            if (caregiverGuardActive()) {
                return;
            }
            switchSource();
        });
        panel.addView(switchSource);

        Button close = caregiverButton("Close");
        close.setOnClickListener(view -> {
            if (caregiverGuardActive()) {
                return;
            }
            closeCaregiverDialog();
        });
        panel.addView(close);

        return panel;
    }

    private Button caregiverButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(5, 5, 5));
        button.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)
        );
        params.topMargin = dp(14);
        button.setLayoutParams(params);
        return button;
    }

    private void closeCaregiverDialog() {
        if (caregiverDialog != null && caregiverDialog.isShowing()) {
            caregiverDialog.dismiss();
        }
    }

    private boolean caregiverGuardActive() {
        return System.currentTimeMillis() - caregiverOpenedAt < CAREGIVER_OPEN_GUARD_MS;
    }

    private void openConfiguredSource() {
        if (!currentSource().hasPlayableQueue()) {
            setStatus("This source has no generated playlist yet.");
            speak("This source has no generated playlist yet.");
            return;
        }
        setStatus("Opening YouTube.");
        speak("Opening YouTube.");
        openYouTubeAfterSnapshot = true;
        refreshCurrentPlaybackSnapshot();
        mainHandler.postDelayed(() -> {
            if (openYouTubeAfterSnapshot) {
                openYouTubeAfterSnapshot = false;
                openCurrentPlaybackInYouTube();
            }
        }, 350);
    }

    private void switchSource() {
        currentSourceIndex = (currentSourceIndex + 1) % SOURCES.length;
        Source source = currentSource();
        currentTitle = source.name;
        currentVideoId = source.videoId;
        currentVideoSeconds = 0;
        titleText.setText(source.name);

        if (source.hasPlayableQueue()) {
            playerReady = false;
            playerWebView.loadDataWithBaseURL(
                    EMBED_ORIGIN,
                    buildPlayerHtml(),
                    "text/html",
                    "UTF-8",
                    null
            );
            setStatus("Source changed. " + source.name + ".");
            speak("Source changed. " + source.name + ".");
        } else {
            setStatus("Source changed. " + source.name + ". Refresh latest uploads is not implemented yet.");
            speak("Source changed. Refresh latest uploads is not implemented yet.");
        }
    }

    private void refreshCurrentPlaybackSnapshot() {
        callPlayer("sendSnapshot()");
    }

    private void openCurrentPlaybackInYouTube() {
        closeCaregiverDialog();
        Intent intent = new Intent(Intent.ACTION_VIEW, buildCurrentPlaybackUri());
        startActivity(intent);
    }

    private Uri buildCurrentPlaybackUri() {
        Uri.Builder builder = Uri.parse("https://www.youtube.com/watch").buildUpon();

        if (currentVideoId != null && !currentVideoId.isEmpty()) {
            builder.appendQueryParameter("v", currentVideoId);
        } else if (!currentSource().videoId.isEmpty()) {
            builder.appendQueryParameter("v", currentSource().videoId);
        }

        if (!currentSource().playlistId.isEmpty()) {
            builder.appendQueryParameter("list", currentSource().playlistId);
        }

        if (currentVideoSeconds > 0) {
            builder.appendQueryParameter("t", currentVideoSeconds + "s");
        }

        return builder.build();
    }

    private Uri buildConfiguredSourceUri() {
        Source source = currentSource();
        switch (source.mode) {
            case VIDEO:
                return Uri.parse("https://www.youtube.com/watch")
                        .buildUpon()
                        .appendQueryParameter("v", source.videoId)
                        .build();
            case VIDEO_IN_PLAYLIST:
                return Uri.parse("https://www.youtube.com/watch")
                        .buildUpon()
                        .appendQueryParameter("v", source.videoId)
                        .appendQueryParameter("list", source.playlistId)
                        .build();
            case PLAYLIST:
            default:
                return Uri.parse("https://www.youtube.com/playlist")
                        .buildUpon()
                        .appendQueryParameter("list", source.playlistId)
                        .build();
        }
    }

    private void callPlayer(String script) {
        if (playerWebView != null) {
            playerWebView.evaluateJavascript(script, null);
        }
    }

    private String buildPlayerHtml() {
        Source source = currentSource();
        String sourceMode = source.mode.name();
        return "<!doctype html>"
                + "<html><head>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>"
                + "html,body,#player{margin:0;width:100%;height:100%;background:#000;overflow:hidden;}"
                + "</style>"
                + "</head><body>"
                + "<div id='player'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>"
                + "var player;"
                + "var sourceMode='" + escapeJs(sourceMode) + "';"
                + "var playlistId='" + escapeJs(source.playlistId) + "';"
                + "var videoId='" + escapeJs(source.videoId) + "';"
                + "var embedOrigin='" + escapeJs(EMBED_ORIGIN) + "';"
                + "function onYouTubeIframeAPIReady(){"
                + "  var options={host:embedOrigin,height:'100%',width:'100%',playerVars:{playsinline:1,controls:1,rel:0,origin:embedOrigin},events:{onReady:onReady,onStateChange:onStateChange,onError:onError}};"
                + "  if(sourceMode==='VIDEO'){options.videoId=videoId;}"
                + "  if(sourceMode==='VIDEO_IN_PLAYLIST'){options.videoId=videoId;options.playerVars.listType='playlist';options.playerVars.list=playlistId;}"
                + "  if(sourceMode==='PLAYLIST'){options.playerVars.listType='playlist';options.playerVars.list=playlistId;}"
                + "  player=new YT.Player('player',options);"
                + "}"
                + "function onReady(){"
                + "  if(sourceMode==='PLAYLIST'&&player&&player.cuePlaylist){player.cuePlaylist({list:playlistId,index:0,startSeconds:0});}"
                + "  AndroidPlayer.onReady();"
                + "}"
                + "function onStateChange(event){"
                + "  var data=player.getVideoData ? player.getVideoData() : null;"
                + "  var title=data && data.title ? data.title : '';"
                + "  var videoId=data && data.video_id ? data.video_id : '';"
                + "  var seconds=player.getCurrentTime ? Math.floor(player.getCurrentTime()) : 0;"
                + "  AndroidPlayer.onStateChange(event.data,title,videoId,seconds);"
                + "}"
                + "function onError(code){AndroidPlayer.onError(String(code));}"
                + "function sendSnapshot(){"
                + "  if(!player){return;}"
                + "  var data=player.getVideoData ? player.getVideoData() : null;"
                + "  var title=data && data.title ? data.title : '';"
                + "  var videoId=data && data.video_id ? data.video_id : '';"
                + "  var seconds=player.getCurrentTime ? Math.floor(player.getCurrentTime()) : 0;"
                + "  AndroidPlayer.onSnapshot(title,videoId,seconds);"
                + "}"
                + "function playVideo(){if(!player){return;} player.playVideo();}"
                + "function pauseVideo(){if(player&&player.pauseVideo){player.pauseVideo();}}"
                + "function nextVideo(){if(player&&player.nextVideo){player.nextVideo();}}"
                + "function previousVideo(){if(player&&player.previousVideo){player.previousVideo();}}"
                + "function goHome(){if(player&&player.cuePlaylist){player.cuePlaylist({list:playlistId,index:0,startSeconds:0});}}"
                + "</script></body></html>";
    }

    private String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private class PlayerBridge {
        @JavascriptInterface
        public void onReady() {
            runOnUiThread(() -> {
                playerReady = true;
                setStatus("YouTube player ready.");
                speak("YouTube player ready.");
            });
        }

        @JavascriptInterface
        public void onStateChange(int state, String title, String videoId, int seconds) {
            runOnUiThread(() -> {
                if (title != null && !title.isEmpty()) {
                    currentTitle = title;
                    titleText.setText(title);
                }
                if (videoId != null && !videoId.isEmpty()) {
                    currentVideoId = videoId;
                }
                currentVideoSeconds = Math.max(0, seconds);

                if (state == 1) {
                    isPlaying = true;
                    setStatus("Playing. " + currentTitle);
                } else if (state == 2) {
                    isPlaying = false;
                    setStatus("Paused.");
                } else if (state == 3) {
                    setStatus("Loading.");
                }
            });
        }

        @JavascriptInterface
        public void onError(String code) {
            runOnUiThread(() -> {
                String message = "Embedded YouTube error " + code + ". Use caregiver settings to open YouTube directly.";
                setStatus(message);
                speak(message);
            });
        }

        @JavascriptInterface
        public void onSnapshot(String title, String videoId, int seconds) {
            runOnUiThread(() -> {
                updateCurrentPlayback(title, videoId, seconds);
                if (openYouTubeAfterSnapshot) {
                    openYouTubeAfterSnapshot = false;
                    openCurrentPlaybackInYouTube();
                }
            });
        }
    }

    private void updateCurrentPlayback(String title, String videoId, int seconds) {
        if (title != null && !title.isEmpty()) {
            currentTitle = title;
            titleText.setText(title);
        }
        if (videoId != null && !videoId.isEmpty()) {
            currentVideoId = videoId;
        }
        currentVideoSeconds = Math.max(0, seconds);
    }

    private boolean registerTripleTap(ArrayDeque<Long> taps) {
        long now = System.currentTimeMillis();
        while (!taps.isEmpty() && now - taps.peekFirst() > TRIPLE_TAP_MS) {
            taps.removeFirst();
        }
        taps.addLast(now);

        if (taps.size() >= 3) {
            taps.clear();
            return true;
        }

        return false;
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private void speak(String message) {
        if (tts != null) {
            tts.stop();
            tts.setLanguage(INTERFACE_LOCALE);
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "status");
        }
    }

    private void speakStatusWithTitle(String prefix, String title) {
        if (tts == null) {
            return;
        }

        tts.stop();
        tts.setLanguage(INTERFACE_LOCALE);
        tts.speak(prefix, TextToSpeech.QUEUE_FLUSH, null, "status-prefix");
        tts.setLanguage(TITLE_LOCALE);
        tts.speak(title, TextToSpeech.QUEUE_ADD, null, "status-title");
        tts.setLanguage(INTERFACE_LOCALE);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (playerWebView != null) {
            playerWebView.destroy();
        }
        super.onDestroy();
    }
}
