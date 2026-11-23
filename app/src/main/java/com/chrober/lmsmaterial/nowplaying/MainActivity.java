package com.chrober.lmsmaterial.nowplaying;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import org.json.JSONArray;
import org.json.JSONObject;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Native, view-only wrapper for Material Skin's /material/np-only endpoint. */
public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "material_now_playing";
    private static final String KEY_SERVER = "server";
    private static final String KEY_PLAYER = "player_id";
    private static final String KEY_KEEP_AWAKE = "keep_awake";
    private static final String KEY_EXPERIMENTAL_PRERASTER = "experimental_offscreen_preraster";
    private static final String KEY_EXPERIMENTAL_EARLY_READY = "experimental_early_now_playing";
    private static final String KEY_SHOW_PROGRESS = "show_progress";
    private static final String KEY_SHOW_TECH_INFO = "show_technical_info";
    private static final String KEY_SHOW_QUEUE_POSITION = "show_queue_position";
    private static final String KEY_SHOW_CLOCK = "show_material_clock";
    private static final String KEY_THEME = "material_theme";
    private static final String KEY_COLOR = "material_color";
    private static final String KEY_COLOR_USAGE = "material_color_usage";
    private static final String KEY_SAMPLED_PROFILE = "material_sampled_profile";
    private static final String KEY_SAMPLED_BG = "material_sampled_background";
    private static final String KEY_SAMPLED_TEXT = "material_sampled_text";
    private static final String KEY_SAMPLED_ACCENT = "material_sampled_accent";

    private static final String STARTUP_LOG_TAG = "LyrionStartup";
    private static final String STARTUP_DETAIL_LOG_TAG = "LyrionStartupDetail";
    private static final long VISUAL_READY_TIMEOUT_MS = 1500L;
    private static final long EARLY_PROBE_INITIAL_DELAY_MS = 70L;
    private static final long EARLY_OPTIONS_FALLBACK_MS = 1400L;
    private static final long GEAR_VISIBLE_MS = 5000L;
    private static final long GEAR_FADE_IN_MS = 230L;
    private static final long GEAR_FADE_OUT_MS = 700L;
    // Settings colors are resolved from the active Android system theme.
    private int BG;
    private int TEXT;
    private int SUBTEXT;
    private int ACCENT;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<LyrionPlayers.Player> playerOptions = new ArrayList<>();
    private final Runnable hideGear = this::fadeGearOut;
    private SharedPreferences prefs;
    private boolean displayVisible;
    private boolean resumed;
    private boolean supportsAwake;
    private int requestSerial;
    private String activeUrl;
    private WebView browser;
    private FrameLayout displayRoot;
    private TextView displayLoading;
    private String browserServer;
    private String appliedOptionsScript;
    private boolean browserReady;
    private StartupTimings startupTimings;
    private WebViewNavigationTrace navigationTrace;
    private long visualReadyGeneration;
    private boolean visualReadyPending;
    private boolean optionsEvaluationPending;
    private long optionsEvaluationSerial;
    private boolean earlyPageFinished;
    private boolean earlyProbePending;
    private long earlyProbeSerial;
    private int earlyProbeAttempts;
    private int earlyConsecutivePasses;
    private ImageButton gearButton;
    private EditText serverInput;
    private Spinner playerSpinner;
    private TextView playerStatus;
    private CheckBox awakeCheckbox;
    private CheckBox progressCheckbox;
    private CheckBox technicalCheckbox;
    private CheckBox queuePositionCheckbox;
    private CheckBox clockCheckbox;
    private Button returnButton;
    private ScrollView settingsScroll;
    private Spinner themeSpinner;
    private Spinner colorUsageSpinner;
    private Spinner colorSpinner;
    private final List<MaterialTheme.Option> themeOptions = MaterialTheme.builtInThemes();
    private final List<MaterialTheme.ColorOption> colorOptions = MaterialTheme.builtInColors();
    private String themeCatalogServer = "";
    private String themeCatalogPending = "";
    private String playersLoadedServer = "";
    private String draftServer;
    private String draftPlayerId;
    private int draftScrollY;
    private int focusAfterAppearanceChange = -1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        resolveThemePalette();
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        supportsAwake = getWindow() != null && manager != null
                && manager.getDisplays().length != 0;
        if (!acceptUrlOverride(getIntent())) {
            if (configured()) showDisplay(); else showSettings(null);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptUrlOverride(intent);
    }

    /** Optional ADB convenience; no external URL is ever opened in a different app. */
    private boolean acceptUrlOverride(Intent intent) {
        // MainActivity is exported. An arbitrary third-party application must
        // not be able to overwrite the trusted server/player in production.
        if ((getApplicationInfo().flags
                & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) return false;
        String raw = intent == null ? null : intent.getStringExtra("url");
        if (TextUtils.isEmpty(raw)) return false;
        try {
            Uri uri = Uri.parse(raw);
            String server = ServerAddress.normalize(uri.getScheme() + "://"
                    + uri.getEncodedAuthority());
            String player = uri.getQueryParameter("player");
            if (server == null || TextUtils.isEmpty(player)) return false;
            prefs.edit().putString(KEY_SERVER, server).putString(KEY_PLAYER, player).apply();
            showDisplay();
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        updateWakeFlag();
    }

    @Override protected void onPause() {
        resumed = false;
        updateWakeFlag();
        super.onPause();
    }

    @Override protected void onDestroy() {
        // Invalidate callbacks from discovery, JSON-RPC and the Material probe.
        // A late callback must never show a dialog on a destroyed Activity.
        requestSerial++;
        displayVisible = false;
        themeCatalogPending = "";
        startupTimings = null;
        navigationTrace = null;
        optionsEvaluationPending = false;
        optionsEvaluationSerial++;
        earlyProbePending = false;
        earlyProbeSerial++;
        invalidateVisualReady();
        handler.removeCallbacksAndMessages(null);
        destroyBrowser();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && displayVisible) setFullscreen(true);
    }

    private boolean configured() {
        return ServerAddress.normalize(prefs.getString(KEY_SERVER, "")) != null
                && !TextUtils.isEmpty(prefs.getString(KEY_PLAYER, ""));
    }

    private void updateWakeFlag() {
        if (resumed && displayVisible && supportsAwake
                && prefs.getBoolean(KEY_KEEP_AWAKE, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void setFullscreen(boolean enabled) {
        getWindow().getDecorView().setSystemUiVisibility(enabled
                ? (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
                : View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void showSettings(String error) {
        resolveThemePalette();
        requestSerial++;
        handler.removeCallbacks(hideGear);
        if (gearButton != null) {
            gearButton.animate().cancel();
            gearButton.setVisibility(View.GONE);
        }
        displayVisible = false;
        invalidateVisualReady();
        // Any pending cold-start trace is no longer relevant after entering Settings.
        startupTimings = null;
        navigationTrace = null;
        optionsEvaluationPending = false;
        optionsEvaluationSerial++;
        earlyProbePending = false;
        earlyProbeSerial++;
        updateWakeFlag();
        setFullscreen(false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        int flags = View.SYSTEM_UI_FLAG_VISIBLE;
        if (Color.red(BG)*0.2126 + Color.green(BG)*0.7152
                + Color.blue(BG)*0.0722 > 150) {
            if (android.os.Build.VERSION.SDK_INT >= 23)
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26)
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
        // Keep a verified Material Skin WebView alive off-screen. Returning
        // from Settings should not repeat the network probe and initialization.
        // Unverified or broken pages are never reused.
        if (!browserReady) destroyBrowser();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        settingsScroll = scroll;
        FrameLayout body = new FrameLayout(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(22), dp(28), dp(28));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int columnWidth = Math.min(screenWidth, dp(720));
        FrameLayout.LayoutParams columnParams =
                new FrameLayout.LayoutParams(columnWidth, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        body.addView(content, columnParams);
        scroll.addView(body);
        setContentView(scroll);

        content.addView(label(getString(R.string.app_name), 28, TEXT, true));
        add(content, label("A dedicated Now Playing display for Lyrion Music Server.",
                16, SUBTEXT, false), 6, 20);
        if (error != null)
            add(content, label(error, 16, Color.rgb(255, 170, 148), false), 0, 14);

        content.addView(label("LYRION SERVER", 15, ACCENT, true));
        serverInput = input(draftServer != null ? draftServer : prefs.getString(KEY_SERVER, ""),
                "192.168.1.30:9000 or https://lyrion.local");
        add(content, framedControl(serverInput), 5, 6);
        Button discoveryButton = button("Find Lyrion servers");
        add(content, discoveryButton, 0, 20);
        discoveryButton.setOnClickListener(v -> {
            discoveryButton.setEnabled(false);
            discoveryButton.setText("Searching the local network…");
            final EditText currentInput = serverInput;
            LyrionDiscovery.discover(this, servers -> {
                if (isFinishing() || isDestroyed() || displayVisible
                        || serverInput != currentInput) return;
                discoveryButton.setEnabled(true);
                discoveryButton.setText("Find Lyrion servers");
                if (servers.isEmpty()) {
                    new AlertDialog.Builder(this)
                            .setMessage("No servers found. Ensure this device is on the same local network, or enter the server address manually.")
                            .setPositiveButton("OK", null).show();
                    return;
                }
                String[] choices = new String[servers.size()];
                for (int i = 0; i < servers.size(); i++)
                    choices[i] = servers.get(i).toString();
                new AlertDialog.Builder(this)
                        .setTitle("Choose Lyrion server")
                        .setItems(choices, (dialog, which) -> {
                            serverInput.setText(servers.get(which).url());
                            loadPlayers();
                        }).show();
            });
        });

        content.addView(label("PLAYER", 15, ACCENT, true));
        playerSpinner = new Spinner(this);
        add(content, framedControl(playerSpinner), 8, 6);
        playerStatus = label("Choose a server to see its players.", 15, SUBTEXT, false);
        add(content, playerStatus, 0, 8);
        Button refreshButton = button("Refresh player list");
        add(content, refreshButton, 0, 20);
        refreshButton.setOnClickListener(v -> loadPlayers());

        content.addView(label("APPEARANCE", 15, ACCENT, true));
        add(content, label("Theme", 15, SUBTEXT, false), 8, 0);
        themeSpinner = new Spinner(this);
        add(content, framedControl(themeSpinner), 4, 8);
        bindThemeSpinner();
        add(content, label("Color", 15, SUBTEXT, false), 3, 0);
        colorUsageSpinner = new Spinner(this);
        add(content, framedControl(colorUsageSpinner), 4, 8);
        bindColorUsageSpinner();
        if (prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER) == MaterialTheme.COLOR_FIXED) {
            add(content, label("Accent color", 15, SUBTEXT, false), 3, 0);
            colorSpinner = new Spinner(this);
            add(content, framedControl(colorSpinner), 4, 8);
            bindColorSpinner();
        } else {
            colorSpinner = null;
            add(content, label("Accent follows Material Skin's current "
                    + (prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER) == MaterialTheme.COLOR_PLAYER
                        ? "player." : "album cover."), 14, SUBTEXT, false), 0, 8);
        }
        content.addView(label("DISPLAY", 15, ACCENT, true));
        awakeCheckbox = new CheckBox(this);
        awakeCheckbox.setText("Keep screen awake");
        awakeCheckbox.setTextColor(TEXT);
        awakeCheckbox.setTextSize(17);
        awakeCheckbox.setButtonTintList(ColorStateList.valueOf(ACCENT));
        awakeCheckbox.setBackground(focusBackground());
        awakeCheckbox.setPadding(dp(10), dp(8), dp(10), dp(8));
        awakeCheckbox.setChecked(prefs.getBoolean(KEY_KEEP_AWAKE, true));
        awakeCheckbox.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(KEY_KEEP_AWAKE, checked).apply());
        awakeCheckbox.setEnabled(supportsAwake);
        add(content, awakeCheckbox, 5, 4);
        add(content, label(supportsAwake
                ? "Prevents Android's normal display timeout while this app is showing Now Playing. Normal power management resumes when the app is in the background."
                : "Not supported on this device: no applicable Android display was detected.",
                14, SUBTEXT, false), 0, 18);

        content.addView(label("PERFORMANCE (EXPERIMENTAL)", 15, ACCENT, true));
        CheckBox prerasterCheckbox = new CheckBox(this);
        prerasterCheckbox.setText("Pre-render hidden WebView");
        prerasterCheckbox.setTextColor(TEXT);
        prerasterCheckbox.setTextSize(17);
        prerasterCheckbox.setButtonTintList(ColorStateList.valueOf(ACCENT));
        prerasterCheckbox.setBackground(focusBackground());
        prerasterCheckbox.setPadding(dp(10), dp(8), dp(10), dp(8));
        prerasterCheckbox.setChecked(prefs.getBoolean(KEY_EXPERIMENTAL_PRERASTER, false));
        prerasterCheckbox.setEnabled(WebViewReadinessPolicy.supportsVisualState(
                Build.VERSION.SDK_INT));
        prerasterCheckbox.setOnCheckedChangeListener((button, checked) -> {
            prefs.edit().putBoolean(KEY_EXPERIMENTAL_PRERASTER, checked).apply();
            // A reused WebView need not reload. The visibility experiment
            // applies from the next page navigation or cold connection.
            if (browser != null && Build.VERSION.SDK_INT >= 23)
                WebViewApi23.setOffscreenPreRaster(browser, checked);
        });
        add(content, prerasterCheckbox, 6, 4);
        add(content, label(WebViewReadinessPolicy.supportsVisualState(Build.VERSION.SDK_INT)
                ? "Optional A/B test: prepare the hidden page before revealing it. "
                    + "May increase memory use or provide no benefit. Off by default; "
                    + "takes effect on the next page load. Not needed for normal use."
                : "Requires Android 6.0 or newer (API 23). Disabled on this device.",
                14, SUBTEXT, false), 0, 18);

        CheckBox earlyCheckbox = new CheckBox(this);
        earlyCheckbox.setText("Early Now Playing readiness");
        earlyCheckbox.setTextColor(TEXT);
        earlyCheckbox.setTextSize(17);
        earlyCheckbox.setButtonTintList(ColorStateList.valueOf(ACCENT));
        earlyCheckbox.setBackground(focusBackground());
        earlyCheckbox.setPadding(dp(10), dp(8), dp(10), dp(8));
        earlyCheckbox.setChecked(prefs.getBoolean(KEY_EXPERIMENTAL_EARLY_READY, false));
        earlyCheckbox.setEnabled(Build.VERSION.SDK_INT >= 23);
        earlyCheckbox.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(KEY_EXPERIMENTAL_EARLY_READY, checked).apply());
        add(content, earlyCheckbox, 3, 4);
        add(content, label(Build.VERSION.SDK_INT >= 23
                ? "A/B experiment: attempt to prepare the mounted Now Playing view "
                    + "before page loading finishes. The existing path is the fallback. "
                    + "Off by default; takes effect on the next cold connection."
                : "Requires Android 6.0 or newer (API 23). Disabled on this device.",
                14, SUBTEXT, false), 0, 18);

        content.addView(label("NOW PLAYING CONTENT", 15, ACCENT, true));
        progressCheckbox = displayCheckbox("Show playback progress",
                prefs.getBoolean(KEY_SHOW_PROGRESS, true), KEY_SHOW_PROGRESS);
        add(content, progressCheckbox, 6, 2);
        technicalCheckbox = displayCheckbox("Show technical track information",
                prefs.getBoolean(KEY_SHOW_TECH_INFO, false), KEY_SHOW_TECH_INFO);
        add(content, technicalCheckbox, 0, 2);
        queuePositionCheckbox = displayCheckbox("Show playlist position",
                prefs.getBoolean(KEY_SHOW_QUEUE_POSITION, true), KEY_SHOW_QUEUE_POSITION);
        add(content, queuePositionCheckbox, 0, 2);
        clockCheckbox = displayCheckbox("Show date and time",
                prefs.getBoolean(KEY_SHOW_CLOCK, false), KEY_SHOW_CLOCK);
        add(content, clockCheckbox, 0, 12);
        add(content, label(configured()
                ? "Changes are saved automatically. Press Back to return to Now Playing."
                : "Options save automatically. Select a server and player, then start Now Playing.",
                14, SUBTEXT, false), 0, 16);
        returnButton = button(configured() ? "Back to Now Playing" : "Start Now Playing");
        returnButton.setEnabled(configured());
        returnButton.setOnClickListener(v -> returnToNowPlaying());
        add(content, returnButton, 0, 6);
        TextView versionFooter = label(installedVersionLabel(), 14, SUBTEXT, false);
        versionFooter.setGravity(Gravity.CENTER);
        add(content, versionFooter, 18, 0);
        serverInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                requestSerial++;
                draftPlayerId = null; // A new server requires a matching player selection.
                playerOptions.clear();
                playersLoadedServer = "";
                showPlayerEntries();
                playerStatus.setText("Refresh players for this server.");
                themeCatalogServer = "";
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        showPlayerEntries();
        String settingsServer = ServerAddress.normalize(serverInput.getText().toString());
        if (settingsServer != null) {
            if (!settingsServer.equals(playersLoadedServer) || playerOptions.isEmpty()) {
                loadPlayers();
            } else {
                // Appearance changes can recreate Settings without refetching
                // players. Keep the status consistent with the restored list.
                playerStatus.setText(availablePlayersLabel(playerOptions.size()));
            }
            loadMaterialCatalog(settingsServer);
        } else if (draftServer == null) {
            discoveryButton.requestFocus();
            discoveryButton.performClick();
        }
        if (focusAfterAppearanceChange >= 0) {
            int target = focusAfterAppearanceChange;
            int scrollY = draftScrollY;
            focusAfterAppearanceChange = -1;
            scroll.post(() -> {
                scroll.scrollTo(0, scrollY);
                View focus = target == 1 ? colorUsageSpinner
                        : target == 2 ? colorSpinner
                        : themeSpinner;
                if (focus != null) focus.requestFocus();
                else if (themeSpinner != null) themeSpinner.requestFocus();
            });
        }
    }

    private <T> ArrayAdapter<T> themedSpinnerAdapter(List<T> items) {
        ArrayAdapter<T> adapter = new ArrayAdapter<T>(this,
                android.R.layout.simple_spinner_item, items) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(TEXT);
                view.setTextSize(17);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void bindThemeSpinner() {
        ArrayAdapter<MaterialTheme.Option> adapter = themedSpinnerAdapter(themeOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(adapter);
        String wanted = prefs.getString(KEY_THEME, MaterialTheme.DEFAULT_THEME);
        boolean found = false;
        for (MaterialTheme.Option option : themeOptions)
            if (wanted.equals(option.key)) { found = true; break; }
        if (!found && MaterialTheme.safeKey(wanted)) {
            themeOptions.add(new MaterialTheme.Option(wanted, wanted));
            adapter.notifyDataSetChanged();
        }
        for (int i=0; i<themeOptions.size(); i++)
            if (wanted.equals(themeOptions.get(i).key)) { themeSpinner.setSelection(i); break; }
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String key = themeOptions.get(pos).key;
                if (!key.equals(prefs.getString(KEY_THEME, MaterialTheme.DEFAULT_THEME))) {
                    prefs.edit().putString(KEY_THEME, key).apply();
                    appearanceChanged(0);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void bindColorUsageSpinner() {
        List<String> modes = new ArrayList<>();
        modes.add("For all players");
        modes.add("From current cover (all players)");
        modes.add("Per player");
        ArrayAdapter<String> adapter = themedSpinnerAdapter(modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorUsageSpinner.setAdapter(adapter);
        int wanted = prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER);
        colorUsageSpinner.setSelection(Math.max(0, Math.min(2, wanted)));
        colorUsageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos != prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER)) {
                    prefs.edit().putInt(KEY_COLOR_USAGE, pos).apply();
                    appearanceChanged(1);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void bindColorSpinner() {
        ArrayAdapter<MaterialTheme.ColorOption> adapter = themedSpinnerAdapter(colorOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        colorSpinner.setAdapter(adapter);
        String wanted = prefs.getString(KEY_COLOR, MaterialTheme.DEFAULT_COLOR);
        boolean found = false;
        for (MaterialTheme.ColorOption option : colorOptions)
            if (wanted.equals(option.key)) { found = true; break; }
        if (!found && MaterialTheme.safeKey(wanted)) {
            colorOptions.add(new MaterialTheme.ColorOption(wanted, wanted, "#1976d2", "#82b1ff"));
            adapter.notifyDataSetChanged();
        }
        for (int i=0; i<colorOptions.size(); i++)
            if (wanted.equals(colorOptions.get(i).key)) { colorSpinner.setSelection(i); break; }
        colorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String key = colorOptions.get(pos).key;
                if (!key.equals(prefs.getString(KEY_COLOR, MaterialTheme.DEFAULT_COLOR))) {
                    prefs.edit().putString(KEY_COLOR, key).apply();
                    appearanceChanged(2);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    /** Re-create controls to reflect the new native palette, without losing unsaved server edits. */
    private void appearanceChanged(int focus) {
        if (serverInput != null) draftServer = serverInput.getText().toString();
        if (playerSpinner != null) {
            int pos = playerSpinner.getSelectedItemPosition();
            if (pos > 0 && pos <= playerOptions.size()) draftPlayerId = playerOptions.get(pos - 1).id;
        }
        draftScrollY = settingsScroll == null ? 0 : settingsScroll.getScrollY();
        focusAfterAppearanceChange = focus;
        handler.post(() -> {
            if (!displayVisible && !isFinishing()) showSettings(null);
        });
    }

    private void loadMaterialCatalog(String server) {
        if (server.equals(themeCatalogServer) || server.equals(themeCatalogPending)) return;
        themeCatalogPending = server;
        MaterialSkinCatalog.fetch(server, result -> {
            if (!server.equals(themeCatalogPending) || isFinishing() || isDestroyed())
                return;
            themeCatalogPending = "";
            themeCatalogServer = server;
            themeOptions.clear();
            themeOptions.addAll(MaterialTheme.mergedThemes(result.themes));
            colorOptions.clear();
            colorOptions.addAll(MaterialTheme.builtInColors());
            for (MaterialTheme.ColorOption extra : result.colors) {
                boolean exists = false;
                for (MaterialTheme.ColorOption standard : colorOptions)
                    if (standard.key.equals(extra.key)) { exists = true; break; }
                if (!exists) colorOptions.add(extra);
            }
            if (!prefs.contains(KEY_THEME) && result.defaults != null) {
                String theme = result.defaults.optString("theme", MaterialTheme.DEFAULT_THEME);
                String color = result.defaults.optString("color", MaterialTheme.DEFAULT_COLOR);
                if (theme.endsWith("-colored"))
                    theme = theme.substring(0, theme.length() - "-colored".length());
                if (MaterialTheme.safeKey(theme) && MaterialTheme.safeKey(color)) {
                    prefs.edit().putString(KEY_THEME, theme).putString(KEY_COLOR, color)
                            .apply();
                    appearanceChanged(0);
                    return;
                }
            }
            if (!displayVisible && themeSpinner != null && server.equals(
                    ServerAddress.normalize(serverInput.getText().toString()))) {
                bindThemeSpinner();
                if (colorSpinner != null) bindColorSpinner();
            }
        });
    }

    private void showPlayerEntries() {
        if (playerSpinner == null) return;
        List<String> labels = new ArrayList<>();
        labels.add("Select a player…");
        for (LyrionPlayers.Player p : playerOptions) labels.add(p.toString());
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        playerSpinner.setAdapter(adapter);
        playerSpinner.setEnabled(!playerOptions.isEmpty());

        String server = ServerAddress.normalize(serverInput.getText().toString());
        // Never silently reselect a player from the previous server when the
        // user is editing the server address. Preserve only matching drafts.
        String wanted = draftPlayerId != null ? draftPlayerId
                : server != null && server.equals(prefs.getString(KEY_SERVER, ""))
                    ? prefs.getString(KEY_PLAYER, "") : "";
        for (int i = 0; i < playerOptions.size(); i++) {
            if (playerOptions.get(i).id.equals(wanted)) {
                playerSpinner.setSelection(i + 1);
                break;
            }
        }
        playerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                persistValidPlayerSelection(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                updateReturnButton();
            }
        });
        updateReturnButton();
    }

    /** Commit the connection atomically, only after a matching player was found. */
    private void persistValidPlayerSelection(int pos) {
        String server = ServerAddress.normalize(serverInput.getText().toString());
        String playerId = SettingsConnection.validPlayerId(
                serverInput.getText().toString(), playersLoadedServer, playerOptions, pos);
        if (playerId != null) {
            if (!server.equals(prefs.getString(KEY_SERVER, ""))
                    || !playerId.equals(prefs.getString(KEY_PLAYER, ""))) {
                prefs.edit().putString(KEY_SERVER, server)
                        .putString(KEY_PLAYER, playerId).apply();
            }
            draftPlayerId = playerId;
        }
        updateReturnButton();
    }

    private void updateReturnButton() {
        if (returnButton != null) returnButton.setEnabled(configured());
    }

    private static String availablePlayersLabel(int count) {
        return count == 1 ? "1 player available" : count + " players available";
    }

    private void loadPlayers() {
        String server = ServerAddress.normalize(serverInput.getText().toString());
        if (server == null) {
            playerStatus.setText("Enter a valid server hostname or IP address.");
            return;
        }
        int serial = ++requestSerial;
        playersLoadedServer = "";
        playerOptions.clear();
        showPlayerEntries();
        playerStatus.setText("Loading players…");
        final EditText currentInput = serverInput;
        LyrionPlayers.fetch(server, (players, error) -> {
            if (isFinishing() || isDestroyed() || displayVisible
                    || serial != requestSerial || serverInput != currentInput) return;
            if (error != null) {
                playerStatus.setText("Player lookup failed: " + error);
                return;
            }
            playerOptions.addAll(players);
            playersLoadedServer = server;
            showPlayerEntries();
            if (players.isEmpty()) {
                playerStatus.setText("No players found on this server.");
                return;
            }
            playerStatus.setText(availablePlayersLabel(players.size()));
        });
    }

    /** Return with persisted options, preserving the last valid connection. */
    private void returnToNowPlaying() {
        if (!configured()) {
            Toast.makeText(this, "Select a server and player first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String entered = ServerAddress.normalize(serverInput.getText().toString());
        if (!prefs.getString(KEY_SERVER, "").equals(entered)) {
            Toast.makeText(this, "Server change not applied; using the last working connection.",
                    Toast.LENGTH_LONG).show();
        }
        showDisplay();
    }

    private void showDisplay() {
        if (!configured()) { showSettings("Configure a server and player first."); return; }
        draftServer = null;
        draftPlayerId = null;
        requestSerial++;
        displayVisible = true;
        invalidateVisualReady();
        handler.removeCallbacks(hideGear);
        setFullscreen(true);
        updateWakeFlag();

        String server = ServerAddress.normalize(prefs.getString(KEY_SERVER, ""));
        String player = prefs.getString(KEY_PLAYER, "");
        String theme = prefs.getString(KEY_THEME, MaterialTheme.DEFAULT_THEME);
        String color = prefs.getString(KEY_COLOR, MaterialTheme.DEFAULT_COLOR);
        if (!MaterialTheme.safeKey(color)) color = MaterialTheme.DEFAULT_COLOR;
        String nextUrl = server + "/material/np-only?player=" + Uri.encode(player)
                + "&theme=" + MaterialTheme.urlTheme(theme) + "," + color;
        String nextOptions = displayOptionsScript();

        NowPlayingReturnPolicy.Action action = NowPlayingReturnPolicy.decide(
                browserReady && browser != null && displayRoot != null && displayLoading != null,
                browserServer, server, activeUrl, nextUrl, appliedOptionsScript, nextOptions);
        if (action != NowPlayingReturnPolicy.Action.RECONNECT) {
            // The same trusted server and already-initialized WebView are still
            // available. Reattach its original view hierarchy without probing.
            displayRoot.setBackgroundColor(BG);
            displayLoading.setBackgroundColor(BG);
            browser.setBackgroundColor(BG);
            if (gearButton != null) {
                gearButton.animate().cancel();
                gearButton.setVisibility(View.GONE);
            }
            setContentView(displayRoot);
            if (action == NowPlayingReturnPolicy.Action.REUSE) {
                displayLoading.setVisibility(View.GONE);
            } else if (action == NowPlayingReturnPolicy.Action.APPLY_OPTIONS) {
                // CSS-only changes take effect in place. Material's own
                // origin-local preference changes can request one reload.
                displayLoading.setText("Applying display settings…");
                displayLoading.setVisibility(View.VISIBLE);
                browserReady = false;
                if (experimentalPreRasterEnabled()) browser.setVisibility(View.INVISIBLE);
                verifyMaterialDisplay(browser, displayLoading, 4);
            } else {
                // Theme or selected player changed: navigate within the SAME
                // trusted origin, keeping the existing WebView and its security
                // client, but let Material Skin initialize the new page.
                activeUrl = nextUrl;
                browserReady = false;
                displayLoading.setText("Connecting to Lyrion…");
                displayLoading.setVisibility(View.VISIBLE);
                if (experimentalPreRasterEnabled()) browser.setVisibility(View.INVISIBLE);
                browser.loadUrl(activeUrl);
            }
            return;
        }

        destroyBrowser();
        activeUrl = nextUrl;
        startupTimings = new StartupTimings(SystemClock.elapsedRealtime(),
                experimentalPreRasterEnabled());
        navigationTrace = new WebViewNavigationTrace();
        // Cold starts and server-origin changes still use the verified probe.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        TextView loading = label("Connecting to Lyrion…", 18, TEXT, false);
        loading.setGravity(Gravity.CENTER);
        loading.setBackgroundColor(BG);
        root.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        displayRoot = root;
        displayLoading = loading;
        setContentView(root);

        final int serial = requestSerial;
        final StartupTimings timings = startupTimings;
        // Render one native, preference-themed loading frame *before*
        // initializing WebView. First WebView construction can stall the UI
        // thread and otherwise prolong Android's unthemed launch preview.
        // The posted work runs after this first pre-draw traversal.
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(() -> {
                    if (serial != requestSerial || !displayVisible || displayRoot != root
                            || startupTimings != timings || isFinishing() || isDestroyed())
                        return;
                    // Network preflight and empty WebView setup still overlap,
                    // and navigation remains gated by the trusted probe.
                    timings.probeStarted(SystemClock.elapsedRealtime());
                    MaterialSkinProbe.check(activeUrl, result -> {
                        if (serial != requestSerial || !displayVisible
                                || isFinishing() || isDestroyed()
                                || browser == null || startupTimings != timings)
                            return;
                        timings.probeFinished(SystemClock.elapsedRealtime());
                        if (result.status != MaterialSkinProbe.Status.AVAILABLE) {
                            materialProbeFailed(result);
                            return;
                        }
                        long navigationStart = SystemClock.elapsedRealtime();
                        timings.navigationStarted(navigationStart);
                        if (navigationTrace != null) {
                            navigationTrace.navigationStarted(navigationStart);
                            navigationTrace.earlyMode(experimentalEarlyReadinessEnabled());
                        }
                        browser.loadUrl(activeUrl);
                    });
                    timings.webViewStarted(SystemClock.elapsedRealtime());
                    prepareNowPlayingWebView(root, loading);
                    timings.webViewPrepared(SystemClock.elapsedRealtime());
                });
                return true;
            }
        });
    }

    private void prepareNowPlayingWebView(FrameLayout root, TextView loading) {
        final String trustedServer = ServerAddress.normalize(prefs.getString(KEY_SERVER, ""));
        browserServer = trustedServer;
        browserReady = false;
        browser = new WebView(this);
        browser.setBackgroundColor(BG);
        if (experimentalPreRasterEnabled()) browser.setVisibility(View.INVISIBLE);
        browser.setFocusable(false);
        browser.setFocusableInTouchMode(false);
        browser.setOnTouchListener((v, event) -> true); // Prevent any WebView interaction.
        browser.setLongClickable(false);
        browser.setVerticalScrollBarEnabled(false);
        browser.setHorizontalScrollBarEnabled(false);
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        if (Build.VERSION.SDK_INT >= 23)
            WebViewApi23.setOffscreenPreRaster(browser, experimentalPreRasterEnabled());
        // Keep the opaque native loading view *above* the WebView until the
        // actual Material Skin page is verified. No raw HTTP error is exposed.
        root.addView(browser, 0, new FrameLayout.LayoutParams(-1, -1));
        browser.setWebViewClient(new WebViewClient() {
            // Material Skin is view-only. Never let its page navigate the main
            // frame to another origin, including via a JavaScript redirect.
            @Override public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                return request.isForMainFrame()
                        && !ServerAddress.isAllowedMaterialNavigation(
                                trustedServer, request.getUrl().toString());
            }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !ServerAddress.isAllowedMaterialNavigation(trustedServer, url);
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (browser != view || !displayVisible) return;
                invalidateVisualReady();
                earlyPageFinished = false;
                earlyProbePending = false;
                earlyProbeSerial++;
                earlyProbeAttempts = 0;
                earlyConsecutivePasses = 0;
                optionsEvaluationPending = false;
                optionsEvaluationSerial++;

                boolean stub = isNowPlayingStub(url);
                boolean trusted = ServerAddress.isAllowedMaterialNavigation(
                        trustedServer, url);
                if (navigationTrace != null)
                    navigationTrace.pageStarted(SystemClock.elapsedRealtime(), stub);

                if (!trusted) {
                    view.stopLoading();
                    connectionFailed("Blocked navigation outside this Lyrion server.");
                    return;
                }
                // A committed frame is NOT an application-ready signal and on
                // the physical Fire TV often arrives after onPageFinished.
                // Start bounded, passive checks at the *real* page's start.
                // Only allow this experiment on a fresh, guarded cold start:
                // a reused WebView could still be displaying the old player
                // document when onPageStarted fires for a new navigation.
                if (!stub && experimentalEarlyReadinessEnabled()
                        && startupTimings != null && navigationTrace != null) {
                    final long generation = visualReadyGeneration;
                    handler.postDelayed(() -> tryEarlyNowPlaying(view, loading, generation),
                            EARLY_PROBE_INITIAL_DELAY_MS);
                }
            }
            @android.annotation.TargetApi(23)
            @Override public void onPageCommitVisible(WebView view, String url) {
                if (Build.VERSION.SDK_INT < 23 || browser != view || !displayVisible
                        || isNowPlayingStub(url)) return;
                final WebViewNavigationTrace trace = navigationTrace;
                if (trace == null) return;
                trace.pageCommitted(SystemClock.elapsedRealtime(), false);
                final int pageNumber = trace.currentPageCount();
                final int serial = requestSerial;
                // Read-only snapshot. It may be delivered after onPageFinished,
                // which is recorded separately; we never reveal the display
                // or apply options early based on this probe.
                view.evaluateJavascript(WebViewCommitProbe.javascript(), raw -> {
                    if (browser == view && displayVisible && requestSerial == serial
                            && navigationTrace == trace && trace.currentPageCount() == pageNumber)
                        trace.commitProbeCompleted(SystemClock.elapsedRealtime(),
                                WebViewCommitProbe.parse(raw));
                });
                // Still record onPageCommitVisible for diagnostics only;
                // the experiment no longer depends on this late callback.
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (browser != view || !displayVisible) return;
                // Theme/color are set via Material Skin's supported URL parameter.
                // The separate "colorUsage" preference must be stored in the
                // same origin as Material Skin, then loaded once more if changed.
                // /np-only is a small JavaScript redirect page, not the actual
                // Material display. Wait until its destination has loaded.
                boolean stub = isNowPlayingStub(url);
                if (navigationTrace != null)
                    navigationTrace.pageFinished(SystemClock.elapsedRealtime(), stub);
                if (stub) return;
                earlyPageFinished = true;
                earlyProbePending = false;
                earlyProbeSerial++; // Any scheduled early reply is now stale.
                // Always retain the original onPageFinished fallback. An early
                // gate may still be pending, but it cannot reveal an unfinished
                // document once this path has taken over.
                if (optionsEvaluationPending) {
                    // A speculative early evaluation may have been queued when
                    // the normal page-finished callback arrived. If the vendor
                    // WebView never completes it, resume the normal path with a
                    // fresh evaluation rather than leaving the overlay stuck.
                    final long generation = visualReadyGeneration;
                    final long evalSerial = optionsEvaluationSerial;
                    handler.postDelayed(() -> {
                        if (generation == visualReadyGeneration && browser == view
                                && displayVisible && optionsEvaluationPending
                                && optionsEvaluationSerial == evalSerial
                                && !visualReadyPending && !browserReady) {
                            optionsEvaluationSerial++;
                            optionsEvaluationPending = false;
                            verifyMaterialDisplay(view, loading, 4);
                        }
                    }, EARLY_OPTIONS_FALLBACK_MS);
                } else {
                    verifyMaterialDisplay(view, loading, 4);
                }
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                       WebResourceResponse response) {
                if (browser != view || !displayVisible || request == null
                        || !request.isForMainFrame()) return;
                int status = response.getStatusCode();
                // The preflight succeeded, so a later HTTP error does not
                // prove Material Skin is absent. Report a load failure instead.
                connectionFailed("Material Skin's Now Playing page returned HTTP "
                        + status + ". Please retry.");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                  WebResourceError error) {
                if (request != null && request.isForMainFrame())
                    connectionFailed("Unable to load Now Playing. Check the server address and network.");
            }
            @SuppressWarnings("deprecation")
            @Override public void onReceivedError(WebView view, int code, String message,
                                                  String failingUrl) {
                if (activeUrl != null && activeUrl.equals(failingUrl))
                    connectionFailed("Unable to connect to Lyrion: " + message);
            }
        });

        gearButton = new ImageButton(this);
        gearButton.setImageResource(R.drawable.ic_gear);
        // The overlay always sits on a dark pill above the Material Skin page.
        gearButton.setColorFilter(Color.WHITE);
        gearButton.setContentDescription(getString(R.string.open_settings));
        gearButton.setFocusable(true);
        gearButton.setClickable(true);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.argb(192, 12, 17, 24));
        shape.setCornerRadius(dp(29));
        gearButton.setBackground(shape);
        gearButton.setAlpha(0f);
        gearButton.setScaleX(0.94f);
        gearButton.setScaleY(0.94f);
        gearButton.setTranslationY(-dp(8));
        gearButton.setVisibility(View.GONE);
        gearButton.setOnClickListener(v -> openSettingsFromDisplay());
        gearButton.setOnFocusChangeListener((v, focused) -> {
            shape.setStroke(focused ? dp(2) : 0, ACCENT);
            if (focused) revealGear();
        });
        FrameLayout.LayoutParams gearParams = new FrameLayout.LayoutParams(
                dp(56), dp(56), Gravity.TOP | Gravity.END);
        gearParams.setMargins(0, dp(22), dp(22), 0);
        root.addView(gearButton, gearParams);
        // Deliberately do NOT load the page here: the probe callback is the
        // single gate that authorizes navigation to the trusted Lyrion URL.
    }

    /**
     * The preflight proved the plugin endpoint exists. Give a cold WebView
     * several chances to initialize before reporting a display load failure.
     */
    private static boolean isNowPlayingStub(String url) {
        String path = Uri.parse(url).getPath();
        return path != null && (path.endsWith("/np-only")
                || path.endsWith("/np-only.html"));
    }

    private String displayOptionsScript() {
        return NowPlayingDisplayOptions.script(
                prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER),
                prefs.getBoolean(KEY_SHOW_PROGRESS, true),
                prefs.getBoolean(KEY_SHOW_TECH_INFO, false),
                prefs.getBoolean(KEY_SHOW_QUEUE_POSITION, true),
                prefs.getBoolean(KEY_SHOW_CLOCK, false));
    }

    /**
     * The JavaScript acknowledgement only confirms Material Skin's DOM state.
     * API 23+ additionally waits until that state is eligible for the next
     * WebView draw. It is NOT a guarantee that pixels have reached the TV.
     */
    private void verifyMaterialDisplay(WebView view, TextView loading, int retriesRemaining) {
        verifyMaterialDisplay(view, loading, retriesRemaining, false);
    }

    /**
     * A successful early probe proves that mounted, visible Vue controls exist;
     * it does NOT prove the stream, artwork or every network request is ready.
     * Two consecutive snapshots are required, with onPageFinished always
     * available as the original safe fallback.
     */
    private void tryEarlyNowPlaying(WebView view, TextView loading, long generation) {
        if (!experimentalEarlyReadinessEnabled() || generation != visualReadyGeneration
                || browser != view || !displayVisible || earlyPageFinished
                || visualReadyPending || browserReady || optionsEvaluationPending
                || earlyProbePending || isFinishing() || isDestroyed()
                || earlyProbeAttempts >= EarlyNowPlayingProbe.MAX_ATTEMPTS) return;

        earlyProbeAttempts++;
        final long serial = ++earlyProbeSerial;
        final WebViewNavigationTrace trace = navigationTrace;
        if (trace != null) trace.earlyProbeStarted(SystemClock.elapsedRealtime());
        earlyProbePending = true;

        // A callback queued during navigation may never arrive on Fire OS.
        // This timer only invalidates a read-only probe; no duplicate script
        // mutation or visual-ready callback can result from a late reply.
        handler.postDelayed(() -> {
            if (serial != earlyProbeSerial || generation != visualReadyGeneration
                    || browser != view || !displayVisible || !earlyProbePending
                    || earlyPageFinished || visualReadyPending || browserReady
                    || isFinishing() || isDestroyed()) return;
            earlyProbePending = false;
            earlyProbeSerial++;
            earlyConsecutivePasses = 0;
            if (trace != null && navigationTrace == trace)
                trace.earlyProbeTimedOut();
            if (earlyProbeAttempts < EarlyNowPlayingProbe.MAX_ATTEMPTS)
                handler.postDelayed(() -> tryEarlyNowPlaying(view, loading, generation),
                        EarlyNowPlayingProbe.RETRY_DELAY_MS);
        }, EarlyNowPlayingProbe.PROBE_CALLBACK_TIMEOUT_MS);

        view.evaluateJavascript(EarlyNowPlayingProbe.javascript(), raw -> {
            if (serial != earlyProbeSerial || generation != visualReadyGeneration
                    || browser != view || !displayVisible
                    || isFinishing() || isDestroyed()) return;
            earlyProbePending = false;
            if (earlyPageFinished || visualReadyPending || browserReady
                    || optionsEvaluationPending) return;

            EarlyNowPlayingProbe.Result result = EarlyNowPlayingProbe.parse(raw);
            if (trace != null && navigationTrace == trace)
                trace.earlyProbeReplied(SystemClock.elapsedRealtime(), result);
            earlyConsecutivePasses = result.qualified()
                    ? earlyConsecutivePasses + 1 : 0;
            if (earlyConsecutivePasses >= EarlyNowPlayingProbe.REQUIRED_CONSECUTIVE_PASSES) {
                if (trace != null && navigationTrace == trace)
                    trace.earlyCandidate(SystemClock.elapsedRealtime());
                verifyMaterialDisplay(view, loading, 4, true);
            } else if (earlyProbeAttempts < EarlyNowPlayingProbe.MAX_ATTEMPTS) {
                handler.postDelayed(() -> tryEarlyNowPlaying(view, loading, generation),
                        EarlyNowPlayingProbe.RETRY_DELAY_MS);
            }
        });
    }

    private void verifyMaterialDisplay(WebView view, TextView loading,
            int retriesRemaining, boolean early) {
        if (browser != view || !displayVisible || visualReadyPending
                || browserReady || optionsEvaluationPending) return;
        optionsEvaluationPending = true;
        final long evalSerial = ++optionsEvaluationSerial;
        final long generation = visualReadyGeneration;
        String javascript = displayOptionsScript();
        final WebViewNavigationTrace trace = navigationTrace;
        if (trace != null) trace.optionsEvaluationStarted(SystemClock.elapsedRealtime());
        view.evaluateJavascript(TimedDisplayOptions.wrap(javascript), raw -> {
            if (generation != visualReadyGeneration || evalSerial != optionsEvaluationSerial
                    || browser != view || !displayVisible
                    || isFinishing() || isDestroyed()) return;
            optionsEvaluationPending = false;
            TimedDisplayOptions.Result result = TimedDisplayOptions.parse(raw);
            if (trace != null && navigationTrace == trace)
                trace.optionsEvaluationFinished(SystemClock.elapsedRealtime(),
                        result.status == TimedDisplayOptions.Status.RELOAD,
                        result.scriptMicros);
            if (result.status == TimedDisplayOptions.Status.RELOAD) return;
            if (result.status == TimedDisplayOptions.Status.READY) {
                appliedOptionsScript = javascript;
                if (trace != null && navigationTrace == trace)
                    trace.readySource(early && !earlyPageFinished);
                if (startupTimings != null)
                    startupTimings.domReady(SystemClock.elapsedRealtime());
                beginVisualReady(view, loading);
            } else if (retriesRemaining > 0) {
                handler.postDelayed(() -> {
                    if (generation == visualReadyGeneration)
                        verifyMaterialDisplay(view, loading, retriesRemaining - 1);
                }, 500L);
            } else {
                connectionFailed("Lyrion responded, but the Material Skin Now Playing "
                        + "display did not finish loading. Please retry.");
            }
        });
    }

    private boolean experimentalEarlyReadinessEnabled() {
        return EarlyNowPlayingProbe.enabled(Build.VERSION.SDK_INT,
                prefs.getBoolean(KEY_EXPERIMENTAL_EARLY_READY, false));
    }

    private boolean experimentalPreRasterEnabled() {
        return WebViewReadinessPolicy.shouldPreRaster(Build.VERSION.SDK_INT,
                prefs.getBoolean(KEY_EXPERIMENTAL_PRERASTER, false));
    }

    private void invalidateVisualReady() {
        visualReadyGeneration++;
        visualReadyPending = false;
    }

    private void beginVisualReady(WebView view, TextView loading) {
        if (visualReadyPending || browser != view || !displayVisible) return;
        final long generation = ++visualReadyGeneration;
        visualReadyPending = true;
        if (!WebViewReadinessPolicy.supportsVisualState(Build.VERSION.SDK_INT)) {
            // Android 5.1 (API 22): retain the original JavaScript-only path.
            completeVisualReady(view, loading, generation, "api22");
            return;
        }
        // Avoid a stuck loading screen if an older/vendor WebView never calls
        // us back. The timeout is marked clearly so benchmarks cannot count it
        // as a confirmed visual-state commit.
        handler.postDelayed(() -> completeVisualReady(
                view, loading, generation, "timeout"), VISUAL_READY_TIMEOUT_MS);
        WebViewApi23.requestVisualState(view, generation, requestId -> {
            if (requestId == generation)
                completeVisualReady(view, loading, generation, "callback");
        });
    }

    private void completeVisualReady(WebView view, TextView loading, long generation,
            String visualState) {
        if (!visualReadyPending || visualReadyGeneration != generation
                || browser != view || !displayVisible || isFinishing() || isDestroyed())
            return;
        invalidateVisualReady(); // Only the first callback/timeout can complete.
        // Required by Android when the preraster experiment hides the WebView:
        // make the attached INVISIBLE view VISIBLE from the visual callback.
        view.setVisibility(View.VISIBLE);
        browserReady = true;
        loading.setVisibility(View.GONE);
        long readyAt = SystemClock.elapsedRealtime();
        if (startupTimings != null) {
            startupTimings.visualReady(readyAt, visualState);
            Log.i(STARTUP_LOG_TAG, startupTimings.report("ready", readyAt));
            startupTimings = null;
        }
        if (navigationTrace != null) {
            final WebViewNavigationTrace completed = navigationTrace;
            navigationTrace = null;
            Log.i(STARTUP_DETAIL_LOG_TAG, completed.report("ready", readyAt));
            // Passive, asynchronous snapshot AFTER revealing the real display.
            // Do not hold the loading overlay for any diagnostic evaluation.
            final int serial = requestSerial;
            view.evaluateJavascript(WebViewPagePerformance.javascript(), raw -> {
                if (browser == view && displayVisible && requestSerial == serial
                        && !isFinishing() && !isDestroyed())
                    Log.i(STARTUP_DETAIL_LOG_TAG, WebViewPagePerformance.report(raw));
            });
        }
        sampleWebPalette(view, null);
    }

    private void materialProbeFailed(MaterialSkinProbe.Result result) {
        if (result.status == MaterialSkinProbe.Status.MISSING) {
            materialMissing();
        } else if (result.status == MaterialSkinProbe.Status.UNEXPECTED_PAGE) {
            connectionFailed("The Lyrion server returned an unexpected page instead "
                    + "of Material Skin's Now Playing display. Check that Material Skin "
                    + "is enabled and select Retry.");
        } else if (result.status == MaterialSkinProbe.Status.TIMEOUT) {
            connectionFailed("The Lyrion server did not respond in time. "
                    + "It may still be starting up. Please retry.");
        } else if (result.status == MaterialSkinProbe.Status.UNAUTHORIZED) {
            connectionFailed("Access to the Now Playing page was denied (HTTP "
                    + result.httpCode + "). Check the Lyrion server's authentication settings.");
        } else if (result.status == MaterialSkinProbe.Status.HTTP_ERROR) {
            connectionFailed("Unable to load the Now Playing page (HTTP "
                    + result.httpCode + "). Check the Lyrion server.");
        } else {
            connectionFailed("Could not contact the Lyrion server. Check the address and network.");
        }
    }

    private void materialMissing() {
        displayProblem("Material Skin unavailable",
                "The Lyrion server is reachable, but the Material Skin Now Playing page "
                + "is unavailable. Install or enable Material Skin under Lyrion's "
                + "Settings > Plugins, then select Retry.");
    }

    private void connectionFailed(String message) {
        displayProblem("Lyrion connection problem", message);
    }

    private void displayProblem(String title, String message) {
        if (!displayVisible || isFinishing()) return;
        // A page that reported an HTTP/network/render failure is no longer a
        // verified reuse candidate. Retry/Settings must take the guarded path.
        browserReady = false;
        displayVisible = false;
        invalidateVisualReady();
        if (startupTimings != null) {
            Log.i(STARTUP_LOG_TAG, startupTimings.report(
                    "error", SystemClock.elapsedRealtime()));
            startupTimings = null;
        }
        if (navigationTrace != null) {
            Log.i(STARTUP_DETAIL_LOG_TAG,
                    navigationTrace.report("error", SystemClock.elapsedRealtime()));
            navigationTrace = null;
        }
        // WebViewClient may call us from one of the renderer callbacks.
        // Never destroy that same WebView synchronously inside its callback:
        // defer cleanup by one UI message, and only destroy the failed view.
        // A retry will use a fresh origin-restricted WebView.
        final WebView failed = browser;
        if (failed != null) {
            handler.post(() -> {
                if (!displayVisible && browser == failed) destroyBrowser();
            });
        }
        updateWakeFlag();
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Retry", (d, which) -> showDisplay())
                .setNeutralButton("Settings", (d, which) -> showSettings(null))
                .setNegativeButton("Exit", (d, which) -> finish())
                .setCancelable(false).show();
    }

    /** Fast, softly decelerating fade-in; slower, unobtrusive fade-out. */
    private void revealGear() {
        if (!displayVisible || gearButton == null) return;
        handler.removeCallbacks(hideGear);
        ImageButton gear = gearButton;
        boolean hidden = gear.getVisibility() != View.VISIBLE;
        gear.animate().cancel();
        if (hidden) {
            gear.setAlpha(0f);
            gear.setTranslationY(-dp(8));
            gear.setScaleX(0.94f);
            gear.setScaleY(0.94f);
            gear.setVisibility(View.VISIBLE);
        }
        gear.animate().alpha(0.94f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(GEAR_FADE_IN_MS)
                .setInterpolator(new DecelerateInterpolator(1.7f)).start();
        handler.postDelayed(hideGear, GEAR_VISIBLE_MS);
    }

    private void fadeGearOut() {
        ImageButton gear = gearButton;
        if (gear == null || gear.getVisibility() != View.VISIBLE) return;
        gear.animate().cancel();
        gear.animate().alpha(0f).translationY(-dp(6))
                .scaleX(0.97f).scaleY(0.97f)
                .setDuration(GEAR_FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (gearButton == gear && gear.getAlpha() < 0.01f)
                        gear.setVisibility(View.GONE);
                }).start();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!displayVisible) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_MENU) {
            if (down && event.getRepeatCount() == 0) openSettingsFromDisplay();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down && event.getRepeatCount() == 0) {
                revealGear();
                gearButton.requestFocus();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_BUTTON_A) {
            if (down && event.getRepeatCount() == 0) {
                if (gearButton != null && gearButton.getVisibility() == View.VISIBLE) {
                    openSettingsFromDisplay();
                } else {
                    revealGear();
                    gearButton.requestFocus();
                }
            }
            return true;
        }
        // Prevent keyboard/media keys from reaching the view-only WebView.
        if (code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_HOME
                && code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN
                && code != KeyEvent.KEYCODE_VOLUME_MUTE && code != KeyEvent.KEYCODE_POWER)
            return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (displayVisible && event.getActionMasked() == MotionEvent.ACTION_DOWN) revealGear();
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (displayVisible && (event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE
                || event.getActionMasked() == MotionEvent.ACTION_MOVE))
            revealGear();
        return super.dispatchGenericMotionEvent(event);
    }

    @Override public void onBackPressed() {
        if (displayVisible) finish();
        else if (configured()) returnToNowPlaying();
        else finish();
    }

    private void destroyBrowser() {
        navigationTrace = null;
        earlyProbePending = false;
        earlyProbeSerial++;
        optionsEvaluationPending = false;
        optionsEvaluationSerial++;
        invalidateVisualReady();
        WebView old = browser;
        browser = null;
        browserReady = false;
        browserServer = null;
        appliedOptionsScript = null;
        displayRoot = null;
        displayLoading = null;
        gearButton = null;
        if (old == null) return;
        old.stopLoading();
        old.setWebViewClient(null);
        if (old.getParent() instanceof ViewGroup)
            ((ViewGroup) old.getParent()).removeView(old);
        old.destroy();
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        button.setPadding(dp(18), dp(10), dp(18), dp(10));
        button.setBackground(buttonBackground());
        int onAccent = contrastingText(ACCENT);
        button.setTextColor(new ColorStateList(
                new int[][]{
                    new int[]{-android.R.attr.state_enabled},
                    new int[]{android.R.attr.state_pressed},
                    new int[]{android.R.attr.state_focused},
                    new int[]{}
                },
                new int[]{blend(BG, TEXT, 0.45f), onAccent, onAccent, TEXT}));
        return button;
    }

    private void resolveThemePalette() {
        boolean darkSystem = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = prefs.getString(KEY_THEME, MaterialTheme.DEFAULT_THEME);
        String color = prefs.getString(KEY_COLOR, MaterialTheme.DEFAULT_COLOR);
        int mode = prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER);
        String active = MaterialTheme.effectiveTheme(theme, darkSystem);
        boolean light = "light".equals(active) || active.contains("/light/");
        // Use a matching framework theme for native spinners, popup menus and
        // dialogs even when the Material Skin choice differs from system mode.
        setTheme(light ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar);
        MaterialTheme.Palette palette = MaterialTheme.palette(theme, color, mode, darkSystem);
        BG = palette.background;
        TEXT = palette.text;
        SUBTEXT = palette.secondary;
        ACCENT = palette.accent;
        String signature = theme + "|" + color + "|" + mode;
        if (signature.equals(prefs.getString(KEY_SAMPLED_PROFILE, ""))) {
            BG = prefs.getInt(KEY_SAMPLED_BG, BG);
            TEXT = prefs.getInt(KEY_SAMPLED_TEXT, TEXT);
            ACCENT = prefs.getInt(KEY_SAMPLED_ACCENT, ACCENT);
        }
        // The Android launch preview cannot use preferences, so AppTheme starts
        // with the default dark background. Once preferences are available,
        // match the real window surface to the selected Material Skin palette.
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));
    }

    /** Read actual Material Skin computed colors, including custom themes and cover colors. */
    private void sampleWebPalette(WebView view, Runnable completion) {
        if (view == null) { if (completion != null) completion.run(); return; }
        String javascript = "(function(){try{" +
                "var root=getComputedStyle(document.documentElement);" +
                "var app=document.querySelector('.lms-app')||document.body;" +
                "var bg=root.getPropertyValue('--std-background-color').trim();" +
                "var accent=root.getPropertyValue('--accent-color').trim();" +
                "var text=getComputedStyle(app).color;" +
                "return [bg,text,accent].join('|');" +
                "}catch(e){return '';}})()";
        view.evaluateJavascript(javascript, raw -> {
            try {
                String value = new JSONArray("[" + raw + "]").getString(0);
                String[] parts = value.split("\\|", -1);
                if (parts.length == 3 && !parts[0].isEmpty()) {
                    int sampledBg = cssColor(parts[0], BG);
                    int sampledText = cssColor(parts[1], TEXT);
                    int sampledAccent = cssColor(parts[2], ACCENT);
                    String signature = prefs.getString(KEY_THEME, MaterialTheme.DEFAULT_THEME)
                            + "|" + prefs.getString(KEY_COLOR, MaterialTheme.DEFAULT_COLOR)
                            + "|" + prefs.getInt(KEY_COLOR_USAGE, MaterialTheme.COLOR_COVER);
                    prefs.edit().putString(KEY_SAMPLED_PROFILE, signature)
                            .putInt(KEY_SAMPLED_BG, sampledBg)
                            .putInt(KEY_SAMPLED_TEXT, sampledText)
                            .putInt(KEY_SAMPLED_ACCENT, sampledAccent).apply();
                }
            } catch (Exception ignored) { /* Never block settings if JavaScript fails. */ }
            if (completion != null) completion.run();
        });
    }

    /** CSS computed colors can be #RRGGBB, rgb(...) or rgba(...). */
    private int cssColor(String css, int fallback) {
        String value = css.trim();
        if (value.startsWith("#")) return MaterialTheme.parseHex(value, fallback);
        if (value.startsWith("rgb(") || value.startsWith("rgba(")) {
            try {
                String[] channels = value.substring(value.indexOf('(')+1, value.indexOf(')')).split(",");
                if (channels.length >= 3) return Color.rgb(
                        Integer.parseInt(channels[0].trim()),
                        Integer.parseInt(channels[1].trim()),
                        Integer.parseInt(channels[2].trim()));
            } catch (RuntimeException ignored) { }
        }
        return fallback;
    }

    private void openSettingsFromDisplay() {
        WebView current = browser;
        if (!displayVisible || current == null) { showSettings(null); return; }
        sampleWebPalette(current, () -> {
            if (browser == current && displayVisible) showSettings(null);
        });
    }

    /** High-contrast, clearly focused buttons, including on Fire TV's D-pad. */
    private StateListDrawable buttonBackground() {
        StateListDrawable background = new StateListDrawable();
        int onAccent = contrastingText(ACCENT);
        background.addState(new int[]{-android.R.attr.state_enabled},
                buttonShape(blend(BG, TEXT, 0.04f), blend(BG, TEXT, 0.12f), 1));
        background.addState(new int[]{android.R.attr.state_pressed},
                buttonShape(ACCENT, onAccent, 3));
        background.addState(new int[]{android.R.attr.state_focused},
                buttonShape(ACCENT, onAccent, 3));
        background.addState(new int[]{},
                buttonShape(blend(BG, ACCENT, 0.12f), blend(BG, ACCENT, 0.5f), 1));
        return background;
    }

    /** Outline focused text fields and spinners without replacing native controls. */
    private FrameLayout framedControl(View control) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(focusBackground());
        frame.setPadding(dp(8), dp(3), dp(8), dp(3));
        frame.addView(control, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        control.setOnFocusChangeListener((view, focused) -> frame.setActivated(focused));
        return frame;
    }

    private StateListDrawable focusBackground() {
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_activated},
                buttonShape(blend(BG, ACCENT, 0.14f), ACCENT, 3));
        background.addState(new int[]{android.R.attr.state_focused},
                buttonShape(blend(BG, ACCENT, 0.14f), ACCENT, 3));
        background.addState(new int[]{},
                buttonShape(BG, blend(BG, ACCENT, 0.22f), 1));
        return background;
    }

    private GradientDrawable buttonShape(int fill, int border, int strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(12));
        shape.setStroke(dp(strokeDp), border);
        return shape;
    }

    private static int blend(int from, int to, float amount) {
        return Color.rgb(
                Math.round(Color.red(from) * (1 - amount) + Color.red(to) * amount),
                Math.round(Color.green(from) * (1 - amount) + Color.green(to) * amount),
                Math.round(Color.blue(from) * (1 - amount) + Color.blue(to) * amount));
    }

    /** WCAG luminance threshold: choose readable text for any wallpaper accent. */
    private static int contrastingText(int background) {
        double brightness = 0.2126 * linear(Color.red(background))
                + 0.7152 * linear(Color.green(background))
                + 0.0722 * linear(Color.blue(background));
        return brightness > 0.179 ? Color.BLACK : Color.WHITE;
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** Display the installed APK version, not the Android-internal versionCode. */
    private String installedVersionLabel() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return "Version " + (TextUtils.isEmpty(version) ? "unknown" : version);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "Version unknown";
        }
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextColor(color);
        label.setTextSize(size);
        if (bold) label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private CheckBox displayCheckbox(String text, boolean checked, String preferenceKey) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(text);
        checkbox.setTextColor(TEXT);
        checkbox.setTextSize(17);
        checkbox.setButtonTintList(ColorStateList.valueOf(ACCENT));
        checkbox.setBackground(focusBackground());
        checkbox.setPadding(dp(10), dp(8), dp(10), dp(8));
        checkbox.setChecked(checked);
        checkbox.setOnCheckedChangeListener((button, value) ->
                prefs.edit().putBoolean(preferenceKey, value).apply());
        return checkbox;
    }

    private EditText input(String value, String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(value);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(SUBTEXT);
        input.setTextSize(18);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        return input;
    }

    private void add(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        parent.addView(child, params);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
