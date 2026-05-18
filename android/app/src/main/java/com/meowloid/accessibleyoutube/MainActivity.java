package com.meowloid.accessibleyoutube;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Xml;
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
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final long LONG_PRESS_MS = 750;
    private static final long TRIPLE_TAP_MS = 900;
    private static final long CAREGIVER_OPEN_GUARD_MS = 500;
    private static final int TAP_SLOP_DP = 18;
    private static final String EMBED_ORIGIN = "https://www.youtube-nocookie.com";
    private static final String PREFS_NAME = "accessible_youtube_prefs";
    private static final String PREF_CHANNEL_LINKS = "channel_links";
    private static final String CHANNEL_LINK_SEPARATOR = "\n";
    private static final int MAX_RECENT_UPLOADS = 25;
    private static final Locale INTERFACE_LOCALE = Locale.US;
    private static final Locale TITLE_LOCALE = Locale.US;
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

    private static class VideoItem {
        final String id;
        final String title;

        VideoItem(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private static class RefreshResult {
        final ArrayList<VideoItem> videos = new ArrayList<>();
        String report = "";
    }

    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioFocusRequest ttsAudioFocusRequest;
    private TextView titleText;
    private TextView statusText;
    private WebView playerWebView;
    private Dialog caregiverDialog;
    private Dialog channelDialog;
    private boolean playerReady = false;
    private boolean isPlaying = false;
    private boolean openYouTubeAfterSnapshot = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int currentSourceIndex = 0;
    private String currentTitle = currentSource().name;
    private String currentVideoId = currentSource().videoId;
    private int currentVideoSeconds = 0;
    private ArrayList<String> curatedChannelLinks = new ArrayList<>();
    private ArrayList<VideoItem> generatedRecentVideos = new ArrayList<>();
    private String latestRefreshReport = "No refresh run yet.";
    private long caregiverOpenedAt = 0L;
    private boolean refreshingUploads = false;
    private boolean playAfterRefresh = false;
    private final ArrayDeque<Long> playTaps = new ArrayDeque<>();
    private final ArrayDeque<Long> statusTaps = new ArrayDeque<>();
    private final Runnable finishSpeechAssistRunnable = () -> {
        callPlayer("restorePlayerVolume()");
        if (audioManager != null && ttsAudioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(ttsAudioFocusRequest);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        curatedChannelLinks = loadChannelLinks();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(5, 5, 5));
        window.setNavigationBarColor(Color.rgb(5, 5, 5));

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                configureTts();
                speak("Ready. Long press any area for help.");
            }
        });

        setContentView(buildLayout());
        mainHandler.postDelayed(this::refreshStartupSource, 700);
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

    private void refreshStartupSource() {
        if (!hasUsableCuratedChannelLinks()) {
            return;
        }

        currentSourceIndex = 1;
        currentTitle = currentSource().name;
        currentVideoId = "";
        currentVideoSeconds = 0;
        titleText.setText(currentTitle);
        setStatus("Refreshing saved channels.");
        refreshLatestUploads(false);
    }

    private void playEmbeddedSource() {
        if (!playerReady) {
            setStatus("YouTube player is still loading.");
            speak("YouTube player is still loading.");
            return;
        }
        if (!hasCurrentPlayableQueue()) {
            if (currentSource().type == SourceType.RECENT_UPLOADS) {
                refreshLatestUploads(true);
                return;
            }
            setStatus("This source has no playable queue.");
            speak("This source has no playable queue.");
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

        Button manageChannels = caregiverButton("Manage Channels");
        manageChannels.setOnClickListener(view -> {
            if (caregiverGuardActive()) {
                return;
            }
            openChannelDialog();
        });
        panel.addView(manageChannels);

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

    private void openChannelDialog() {
        closeCaregiverDialog();

        channelDialog = new Dialog(this);
        channelDialog.setContentView(buildChannelDialogContent());
        channelDialog.setCanceledOnTouchOutside(true);

        Window dialogWindow = channelDialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        channelDialog.show();
        setStatus("Manage channels opened.");
        speak("Manage channels.");
    }

    private View buildChannelDialogContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(20));
        panel.setBackgroundColor(Color.rgb(17, 17, 17));
        scrollView.addView(panel);

        TextView heading = new TextView(this);
        heading.setText("Channel Links");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(24);
        panel.addView(heading);

        TextView note = new TextView(this);
        note.setText("Paste channel links here, then refresh recent videos.");
        note.setTextColor(Color.WHITE);
        note.setTextSize(15);
        note.setPadding(0, dp(6), 0, dp(10));
        panel.addView(note);

        TextView report = new TextView(this);
        report.setText("Last refresh:\n" + latestRefreshReport);
        report.setTextColor(Color.WHITE);
        report.setTextSize(14);
        report.setPadding(0, 0, 0, dp(10));
        panel.addView(report);

        Button refresh = caregiverButton("Refresh Recent Videos");
        refresh.setOnClickListener(view -> {
            if (channelDialog != null) {
                channelDialog.dismiss();
            }
            currentSourceIndex = 1;
            titleText.setText(currentSource().name);
            refreshLatestUploads(false);
        });
        panel.addView(refresh);

        for (String link : curatedChannelLinks) {
            TextView row = new TextView(this);
            row.setText(link);
            row.setTextColor(Color.WHITE);
            row.setTextSize(14);
            row.setPadding(0, dp(10), 0, 0);
            panel.addView(row);

            Button remove = caregiverButton("Remove");
            remove.setOnClickListener(view -> {
                curatedChannelLinks.remove(link);
                saveChannelLinks();
                if (channelDialog != null) {
                    channelDialog.dismiss();
                }
                openChannelDialog();
            });
            panel.addView(remove);
        }

        EditText input = new EditText(this);
        input.setHint("Paste YouTube channel URL");
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.LTGRAY);
        input.setTextSize(16);
        panel.addView(input);

        Button add = caregiverButton("Add Channel Link");
        add.setOnClickListener(view -> {
            String link = input.getText().toString().trim();
            if (!link.isEmpty() && !curatedChannelLinks.contains(link)) {
                curatedChannelLinks.add(link);
                saveChannelLinks();
                input.setText("");
                if (channelDialog != null) {
                    channelDialog.dismiss();
                }
                openChannelDialog();
            }
        });
        panel.addView(add);

        Button close = caregiverButton("Close");
        close.setOnClickListener(view -> {
            if (channelDialog != null) {
                channelDialog.dismiss();
            }
            setStatus("Manage channels closed.");
            speak("Closed.");
        });
        panel.addView(close);

        return scrollView;
    }

    private boolean hasUsableCuratedChannelLinks() {
        for (String link : curatedChannelLinks) {
            if (!link.trim().isEmpty() && !link.toLowerCase(Locale.US).contains("replace")) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<String> loadChannelLinks() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String storedLinks = preferences.getString(PREF_CHANNEL_LINKS, null);

        if (storedLinks == null) {
            return new ArrayList<>(Arrays.asList(SOURCES[1].channelLinks));
        }

        ArrayList<String> links = new ArrayList<>();
        for (String link : storedLinks.split(CHANNEL_LINK_SEPARATOR)) {
            String trimmed = link.trim();
            if (!trimmed.isEmpty()) {
                links.add(trimmed);
            }
        }
        return links;
    }

    private void saveChannelLinks() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_CHANNEL_LINKS, String.join(CHANNEL_LINK_SEPARATOR, curatedChannelLinks))
                .apply();
    }

    private boolean caregiverGuardActive() {
        return System.currentTimeMillis() - caregiverOpenedAt < CAREGIVER_OPEN_GUARD_MS;
    }

    private void openConfiguredSource() {
        if (!hasCurrentPlayableQueue()) {
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

        if (hasCurrentPlayableQueue()) {
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
        } else if (source.type == SourceType.RECENT_UPLOADS) {
            setStatus("Source changed. Refreshing latest uploads.");
            speak("Source changed. Refreshing latest uploads.");
            refreshLatestUploads(false);
        } else {
            setStatus("Source changed. " + source.name + ". No playable queue.");
            speak("Source changed. No playable queue.");
        }
    }

    private boolean hasCurrentPlayableQueue() {
        Source source = currentSource();
        return source.hasPlayableQueue()
                || (source.type == SourceType.RECENT_UPLOADS && !generatedRecentVideos.isEmpty());
    }

    private void refreshLatestUploads(boolean shouldPlayAfterRefresh) {
        if (refreshingUploads) {
            playAfterRefresh = playAfterRefresh || shouldPlayAfterRefresh;
            setStatus("Already refreshing recent videos.");
            speak("Already refreshing recent videos.");
            return;
        }

        if (curatedChannelLinks.isEmpty()) {
            setStatus("No channel links saved.");
            speak("No channel links saved.");
            return;
        }

        refreshingUploads = true;
        playAfterRefresh = shouldPlayAfterRefresh;
        setStatus("Refreshing recent videos.");
        speak("Refreshing recent videos.");

        new Thread(() -> {
            RefreshResult result = fetchRecentVideos(curatedChannelLinks);
            runOnUiThread(() -> finishRecentVideoRefresh(result));
        }).start();
    }

    private void finishRecentVideoRefresh(RefreshResult result) {
        refreshingUploads = false;
        latestRefreshReport = result.report;

        if (result.videos.isEmpty()) {
            playAfterRefresh = false;
            String message = "No recent videos found. Open Manage Channels for details.";
            setStatus(message);
            speak(message);
            return;
        }

        generatedRecentVideos = result.videos;
        currentSourceIndex = 1;
        currentTitle = result.videos.get(0).title;
        currentVideoId = result.videos.get(0).id;
        currentVideoSeconds = 0;
        titleText.setText(currentTitle);

        playerReady = false;
        playerWebView.loadDataWithBaseURL(
                EMBED_ORIGIN,
                buildPlayerHtml(),
                "text/html",
                "UTF-8",
                null
        );

        setStatus("Recent videos ready. " + result.videos.size() + " videos.");
        speak("Recent videos ready.");
    }

    private RefreshResult fetchRecentVideos(ArrayList<String> channelLinks) {
        RefreshResult result = new RefreshResult();
        Set<String> seenVideoIds = new LinkedHashSet<>();
        StringBuilder report = new StringBuilder();

        for (String link : channelLinks) {
            if (link.toLowerCase(Locale.US).contains("replace")) {
                report.append("Skipped placeholder: ").append(link).append("\n");
                continue;
            }

            String channelId;
            try {
                channelId = resolveChannelId(link);
            } catch (Exception error) {
                report.append("Could not resolve: ")
                        .append(link)
                        .append(" (")
                        .append(describeError(error))
                        .append(")\n");
                continue;
            }

            if (channelId == null || channelId.isEmpty()) {
                report.append("Could not find channel ID: ").append(link).append("\n");
                continue;
            }

            ArrayList<VideoItem> feedVideos;
            try {
                feedVideos = fetchChannelFeed(channelId);
            } catch (Exception error) {
                report.append("Feed failed: ")
                        .append(channelId)
                        .append(" from ")
                        .append(link)
                        .append(" (")
                        .append(describeError(error))
                        .append(")\n");
                continue;
            }

            if (feedVideos.isEmpty()) {
                report.append("Zero videos: ").append(channelId).append("\n");
                continue;
            }

            int before = result.videos.size();
            for (VideoItem video : feedVideos) {
                if (seenVideoIds.add(video.id)) {
                    result.videos.add(video);
                    if (result.videos.size() >= MAX_RECENT_UPLOADS) {
                        report.append("Added ")
                                .append(result.videos.size() - before)
                                .append(" from ")
                                .append(channelId)
                                .append(". Reached queue limit.\n");
                        result.report = report.toString().trim();
                        return result;
                    }
                }
            }

            report.append("Added ")
                    .append(result.videos.size() - before)
                    .append(" from ")
                    .append(channelId)
                    .append("\n");
        }

        result.report = report.length() == 0 ? "No channel links checked." : report.toString().trim();
        return result;
    }

    private String resolveChannelId(String link) throws Exception {
        String normalized = normalizeChannelLink(link);
        if (normalized.startsWith("UC")) {
            return normalized;
        }

        Uri uri = Uri.parse(normalized);
        String channelIdQuery = uri.getQueryParameter("channel_id");
        if (channelIdQuery != null && channelIdQuery.startsWith("UC")) {
            return channelIdQuery;
        }

        ArrayList<String> segments = new ArrayList<>(uri.getPathSegments());
        for (int i = 0; i < segments.size(); i++) {
            if ("channel".equals(segments.get(i)) && i + 1 < segments.size()) {
                String candidate = segments.get(i + 1);
                if (candidate.startsWith("UC")) {
                    return candidate;
                }
            }
        }

        String channelId = findChannelId(fetchText(normalized, 1_000_000));
        if (!channelId.isEmpty()) {
            return channelId;
        }

        String aboutUrl = normalized.endsWith("/about") ? normalized : normalized + "/about";
        channelId = findChannelId(fetchText(aboutUrl, 1_000_000));
        if (!channelId.isEmpty()) {
            return channelId;
        }

        return "";
    }

    private String normalizeChannelLink(String link) {
        String trimmed = link.trim();
        if (trimmed.startsWith("@")) {
            return "https://www.youtube.com/" + trimmed;
        }
        if (trimmed.startsWith("youtube.com/") || trimmed.startsWith("www.youtube.com/")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    private String findChannelId(String page) {
        Pattern[] patterns = {
                Pattern.compile("\"channelId\"\\s*:\\s*\"(UC[\\w-]{20,})\""),
                Pattern.compile("\"externalId\"\\s*:\\s*\"(UC[\\w-]{20,})\""),
                Pattern.compile("\"browseId\"\\s*:\\s*\"(UC[\\w-]{20,})\""),
                Pattern.compile("youtube\\.com/channel/(UC[\\w-]{20,})"),
                Pattern.compile("channel_id=(UC[\\w-]{20,})"),
                Pattern.compile("channel_id%3D(UC[\\w-]{20,})")
        };

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(page);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return "";
    }

    private ArrayList<VideoItem> fetchChannelFeed(String channelId) throws Exception {
        ArrayList<VideoItem> videos = new ArrayList<>();
        URL url = new URL("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(12_000);
        applyBrowserHeaders(connection, "application/atom+xml,application/xml,text/xml,*/*");

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + responseCode);
        }

        try (InputStream input = connection.getInputStream()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, null);

            boolean inEntry = false;
            String videoId = "";
            String title = "";
            int eventType = parser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("entry".equals(name)) {
                        inEntry = true;
                        videoId = "";
                        title = "";
                    } else if (inEntry && "videoId".equals(name)) {
                        videoId = parser.nextText().trim();
                    } else if (inEntry && "title".equals(name)) {
                        title = parser.nextText().trim();
                    }
                } else if (eventType == XmlPullParser.END_TAG && "entry".equals(parser.getName())) {
                    if (!videoId.isEmpty()) {
                        videos.add(new VideoItem(videoId, title.isEmpty() ? "Untitled video" : title));
                    }
                    inEntry = false;
                }

                eventType = parser.next();
            }
        } finally {
            connection.disconnect();
        }

        return videos;
    }

    private String fetchText(String link, int maxBytes) throws Exception {
        URL url = new URL(link);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(12_000);
        applyBrowserHeaders(connection, "text/html,application/xhtml+xml,application/xml,*/*");

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400) {
            connection.disconnect();
            throw new IllegalStateException("HTTP " + responseCode);
        }

        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1 && total < maxBytes) {
                int bytesToWrite = Math.min(read, maxBytes - total);
                output.write(buffer, 0, bytesToWrite);
                total += bytesToWrite;
            }
            return output.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    private void applyBrowserHeaders(HttpURLConnection connection, String accept) {
        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        );
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
    }

    private String describeError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + message.trim();
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
        String sourceType = source.type.name();
        String generatedVideoIds = buildGeneratedVideoIdArray();
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
                + "var sourceType='" + escapeJs(sourceType) + "';"
                + "var sourceMode='" + escapeJs(sourceMode) + "';"
                + "var playlistId='" + escapeJs(source.playlistId) + "';"
                + "var videoId='" + escapeJs(source.videoId) + "';"
                + "var generatedVideoIds=" + generatedVideoIds + ";"
                + "var embedOrigin='" + escapeJs(EMBED_ORIGIN) + "';"
                + "function onYouTubeIframeAPIReady(){"
                + "  var options={host:embedOrigin,height:'100%',width:'100%',playerVars:{playsinline:1,controls:1,rel:0,origin:embedOrigin},events:{onReady:onReady,onStateChange:onStateChange,onError:onError}};"
                + "  if(sourceType==='RECENT_UPLOADS'&&generatedVideoIds.length>0){options.videoId=generatedVideoIds[0];}"
                + "  else if(sourceMode==='VIDEO'){options.videoId=videoId;}"
                + "  else if(sourceMode==='VIDEO_IN_PLAYLIST'){options.videoId=videoId;options.playerVars.listType='playlist';options.playerVars.list=playlistId;}"
                + "  else if(sourceMode==='PLAYLIST'){options.playerVars.listType='playlist';options.playerVars.list=playlistId;}"
                + "  player=new YT.Player('player',options);"
                + "}"
                + "function onReady(){"
                + "  if(sourceType==='RECENT_UPLOADS'&&generatedVideoIds.length>0&&player&&player.cuePlaylist){player.cuePlaylist({playlist:generatedVideoIds,index:0,startSeconds:0});}"
                + "  else if(sourceMode==='PLAYLIST'&&player&&player.cuePlaylist){player.cuePlaylist({list:playlistId,index:0,startSeconds:0});}"
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
                + "var previousVolume=100;"
                + "function duckPlayerForSpeech(){if(player&&player.getVolume&&player.setVolume){previousVolume=player.getVolume();player.setVolume(18);}}"
                + "function restorePlayerVolume(){if(player&&player.setVolume){player.setVolume(previousVolume);}}"
                + "function goHome(){if(!player||!player.cuePlaylist){return;} if(sourceType==='RECENT_UPLOADS'&&generatedVideoIds.length>0){player.cuePlaylist({playlist:generatedVideoIds,index:0,startSeconds:0});}else{player.cuePlaylist({list:playlistId,index:0,startSeconds:0});}}"
                + "</script></body></html>";
    }

    private String buildGeneratedVideoIdArray() {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < generatedRecentVideos.size(); i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("'").append(escapeJs(generatedRecentVideos.get(i).id)).append("'");
        }
        builder.append("]");
        return builder.toString();
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
                if (playAfterRefresh) {
                    playAfterRefresh = false;
                    mainHandler.postDelayed(() -> {
                        setStatus("Starting playback.");
                        speak("Starting playback.");
                        callPlayer("playVideo()");
                    }, 300);
                }
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

    private void configureTts() {
        AudioAttributes speechAttributes = buildSpeechAudioAttributes();
        tts.setAudioAttributes(speechAttributes);
        tts.setSpeechRate(0.95f);
        setTtsLanguage(INTERFACE_LOCALE);

        ttsAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(speechAttributes)
                .setOnAudioFocusChangeListener(focusChange -> {
                })
                .build();
    }

    private AudioAttributes buildSpeechAudioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
    }

    private void prepareSpeechAssist(String message) {
        if (audioManager != null && ttsAudioFocusRequest != null) {
            audioManager.requestAudioFocus(ttsAudioFocusRequest);
        }

        callPlayer("duckPlayerForSpeech()");
        mainHandler.removeCallbacks(finishSpeechAssistRunnable);
        mainHandler.postDelayed(finishSpeechAssistRunnable, estimateSpeechDurationMs(message));
    }

    private long estimateSpeechDurationMs(String message) {
        return Math.max(2500L, Math.min(12_000L, 1800L + (long) message.length() * 70L));
    }

    private boolean setTtsLanguage(Locale locale) {
        int result = tts.setLanguage(locale);
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private void speak(String message) {
        if (tts != null) {
            prepareSpeechAssist(message);
            tts.stop();
            setTtsLanguage(INTERFACE_LOCALE);
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "status");
        }
    }

    private void speakStatusWithTitle(String prefix, String title) {
        if (tts == null) {
            return;
        }

        prepareSpeechAssist(prefix + " " + title);
        tts.stop();
        setTtsLanguage(INTERFACE_LOCALE);
        tts.speak(prefix, TextToSpeech.QUEUE_FLUSH, null, "status-prefix");
        if (!setTtsLanguage(TITLE_LOCALE)) {
            setTtsLanguage(INTERFACE_LOCALE);
        }
        tts.speak(title, TextToSpeech.QUEUE_ADD, null, "status-title");
        setTtsLanguage(INTERFACE_LOCALE);
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
        mainHandler.removeCallbacks(finishSpeechAssistRunnable);
        if (audioManager != null && ttsAudioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(ttsAudioFocusRequest);
        }
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
