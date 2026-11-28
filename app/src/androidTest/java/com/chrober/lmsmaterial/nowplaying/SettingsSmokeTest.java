package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** API 22+ native UI tests, running on a clean emulator profile. */
@RunWith(AndroidJUnit4.class)
public final class SettingsSmokeTest {
    @Before public void clearConfiguration() {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    private static View findText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText()))
            return root;
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) root;
            for (int i = 0; i < parent.getChildCount(); i++) {
                View match = findText(parent.getChildAt(i), text);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static EditText findInput(View root) {
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) root;
            for (int i = 0; i < parent.getChildCount(); i++) {
                EditText match = findInput(parent.getChildAt(i));
                if (match != null) return match;
            }
        }
        return null;
    }

    @Test public void splashUsesNativeAndroidBackgroundInLightAndDarkMode() {
        checkSplashColors(Configuration.UI_MODE_NIGHT_NO);
        checkSplashColors(Configuration.UI_MODE_NIGHT_YES);
    }

    private void checkSplashColors(int nightMode) {
        Context app = ApplicationProvider.getApplicationContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        Context configured = app.createConfigurationContext(config);
        Resources.Theme theme = configured.getResources().newTheme();
        theme.applyStyle(R.style.AppTheme, true);

        TypedArray background = theme.obtainStyledAttributes(
                new int[]{android.R.attr.colorBackground,
                        android.R.attr.windowBackground});
        final int expectedColor;
        try {
            expectedColor = background.getColor(0, 0);
            if (Build.VERSION.SDK_INT >= 31) {
                assertEquals("Android 12+ window background should match system theme",
                        expectedColor, background.getColor(1, 0));
            } else {
                assertEquals("Legacy splash should use the Lyrion icon drawable",
                        R.drawable.startup_background, background.getResourceId(1, 0));
            }
        } finally {
            background.recycle();
        }

        if (Build.VERSION.SDK_INT >= 31) {
            TypedArray splash = theme.obtainStyledAttributes(
                    new int[]{android.R.attr.windowSplashScreenBackground});
            try {
                assertEquals("System splash should use Android theme background",
                        expectedColor, splash.getColor(0, 0));
            } finally {
                splash.recycle();
            }
            return;
        }

        TypedArray preview = theme.obtainStyledAttributes(
                new int[]{android.R.attr.windowDisablePreview});
        try {
            assertFalse("Keep Android's native splash preview",
                    preview.getBoolean(0, false));
        } finally {
            preview.recycle();
        }

        Drawable drawable = configured.getResources()
                .getDrawable(R.drawable.startup_background, theme);
        assertTrue("Splash should combine the themed background and existing logo",
                drawable instanceof LayerDrawable);
        LayerDrawable layers = (LayerDrawable) drawable;
        assertEquals(2, layers.getNumberOfLayers());

        Drawable fill = layers.getDrawable(0);
        assertTrue("Background should be theme-resolved solid fill",
                fill instanceof GradientDrawable);
        // GradientDrawable.getColor() is unavailable on the supported API 22.
        // Rendering the solid shape verifies the actual resolved system color
        // on every supported Android version instead.
        Bitmap sample = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        try {
            fill.setBounds(0, 0, 8, 8);
            fill.draw(new Canvas(sample));
            assertEquals("Splash background must follow Android's Light/Dark theme",
                    expectedColor, sample.getPixel(4, 4));
        } finally {
            sample.recycle();
        }

        Drawable logo = layers.getDrawable(1);
        assertTrue("Logo should be the existing BitmapDrawable",
                logo instanceof BitmapDrawable);
        assertEquals("Logo should remain centered", Gravity.CENTER,
                ((BitmapDrawable) logo).getGravity());
    }

    @Test public void removedPerformanceExperimentsAreNeitherShownNorPersisted() {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().putBoolean("experimental_offscreen_preraster", true)
                .putBoolean("experimental_early_now_playing", true).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertNull(findText(root, "PERFORMANCE (EXPERIMENTAL)"));
                assertNull(findText(root, "Pre-render hidden WebView"));
                assertNull(findText(root, "Early Now Playing readiness"));
                android.content.SharedPreferences settings = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE);
                assertFalse(settings.contains("experimental_offscreen_preraster"));
                assertFalse(settings.contains("experimental_early_now_playing"));
            });
        }
    }

    @Test public void firstLaunchShowsNativeSettingsAndSetupActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertNotNull(findText(root, "Lyrion Display"));
                assertNotNull(findText(root, "LYRION SERVER"));
                assertNotNull(findText(root, "PLAYER"));
                Button start = (Button) findText(root, "Start Now Playing");
                assertNotNull(start);
                assertFalse(start.isEnabled());
                assertNull(findText(root, "Save and show Now Playing"));
                assertNull(findText(root, "Cancel and return to Now Playing"));
                assertNotNull(findText(root, "Show date and time"));
                assertNotNull(findText(root, "Show player name"));
                assertNotNull(findText(root, "Show track number"));
                assertNotNull(findText(root, "Show background image"));
                assertNotNull(findText(root, "Show artist context"));
                assertNull(findText(root, "Playlist position shows the current track's place in the playback queue (for example, 7 / 42)."));
                String version;
                try {
                    version = activity.getPackageManager().getPackageInfo(
                            activity.getPackageName(), 0).versionName;
                } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                    throw new AssertionError("Installed package metadata missing", e);
                }
                TextView footer = (TextView) findText(root, "Version " + version);
                assertNotNull("Installed version should appear as Settings footer", footer);
                assertFalse("Version footer must not take TV remote focus", footer.isFocusable());
                assertNotNull(findInput(root));
            });
        }
    }

    @Test public void invalidServerCannotBeSaved() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                EditText input = findInput(root);
                Button start = (Button) findText(root, "Start Now Playing");
                assertNotNull(input);
                assertNotNull(start);
                input.setText("ftp://not-a-lyrion-server");
                assertFalse(start.isEnabled());
                String configured = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE).getString("server", "");
                assertEquals("", configured);
            });
        }
    }

    @Test public void settingsDoNotKeepDisplayAwake() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                int flags = activity.getWindow().getAttributes().flags;
                assertEquals(0, flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
    }


    @Test public void settingsGroupAppearanceAndNowPlayingOptionsInLogicalOrder() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                String[] expected = {
                        "APPEARANCE", "Theme", "Color", "Show background image",
                        "DISPLAY", "Keep screen awake", "NOW PLAYING CONTENT",
                        "Show track number", "Show artist context",
                        "Show technical track information", "Show playback progress",
                        "Show playlist position", "Show player name", "Show date and time"
                };
                View appearance = findText(root, expected[0]);
                assertNotNull(appearance);
                ViewGroup content = (ViewGroup) appearance.getParent();
                int previous = -1;
                for (String text : expected) {
                    View item = findText(root, text);
                    assertNotNull(text, item);
                    assertSame(text, content, item.getParent());
                    int position = content.indexOfChild(item);
                    assertTrue("Unexpected order: " + text, position > previous);
                    previous = position;
                }
            });
        }
    }

    @Test public void nativeTrackOptionsDefaultAndPersistIndependently() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                android.widget.CheckBox track = (android.widget.CheckBox) findText(root, "Show track number");
                android.widget.CheckBox background = (android.widget.CheckBox) findText(root, "Show background image");
                android.widget.CheckBox context = (android.widget.CheckBox) findText(root, "Show artist context");
                assertNotNull(track);
                assertNotNull(background);
                assertNotNull(context);
                assertFalse(track.isChecked());
                assertTrue(background.isChecked());
                assertTrue(context.isChecked());
                track.performClick();
                assertTrue(background.isChecked());
                assertTrue(context.isChecked());
                background.performClick();
                assertTrue(track.isChecked());
                assertTrue(context.isChecked());
                context.performClick();
                android.content.SharedPreferences prefs = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE);
                assertTrue(prefs.getBoolean("show_track_number", false));
                assertFalse(prefs.getBoolean("show_background_image", true));
                assertFalse(prefs.getBoolean("show_artist_context", true));
                assertTrue(prefs.getBoolean("show_queue_position", true));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                android.widget.CheckBox track = (android.widget.CheckBox) findText(root, "Show track number");
                android.widget.CheckBox background = (android.widget.CheckBox) findText(root, "Show background image");
                android.widget.CheckBox context = (android.widget.CheckBox) findText(root, "Show artist context");
                assertTrue(track.isChecked());
                assertFalse(background.isChecked());
                assertFalse(context.isChecked());
                track.performClick();
                background.performClick();
                context.performClick();
                android.content.SharedPreferences prefs = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE);
                assertFalse(prefs.getBoolean("show_track_number", true));
                assertTrue(prefs.getBoolean("show_background_image", false));
                assertTrue(prefs.getBoolean("show_artist_context", false));
            });
        }
    }

    @Test public void contentOptionsSaveImmediatelyWithoutSavingButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                android.widget.CheckBox technical = (android.widget.CheckBox)
                        findText(root, "Show technical track information");
                android.widget.CheckBox clock = (android.widget.CheckBox)
                        findText(root, "Show date and time");
                android.widget.CheckBox playerName = (android.widget.CheckBox)
                        findText(root, "Show player name");
                assertNotNull(technical);
                assertNotNull(clock);
                assertNotNull(playerName);
                assertFalse(technical.isChecked());
                assertFalse(clock.isChecked());
                assertFalse(playerName.isChecked());
                playerName.performClick();
                android.content.SharedPreferences independent = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE);
                assertTrue(independent.getBoolean("show_player_name", false));
                assertFalse(independent.getBoolean("show_material_clock", false));
                technical.performClick();
                clock.performClick();
                android.content.SharedPreferences prefs = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE);
                assertTrue(prefs.getBoolean("show_technical_info", false));
                assertTrue(prefs.getBoolean("show_material_clock", false));
                assertTrue(prefs.getBoolean("show_player_name", false));
                assertEquals("", prefs.getString("server", ""));
            });
            // Saving on change survives a settings recreation without pressing Back.
            scenario.recreate();
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertTrue(((android.widget.CheckBox) findText(
                        root, "Show technical track information")).isChecked());
                assertTrue(((android.widget.CheckBox) findText(
                        root, "Show date and time")).isChecked());
                android.widget.CheckBox playerName = (android.widget.CheckBox) findText(root, "Show player name");
                assertTrue(playerName.isChecked());
                playerName.performClick();
                assertFalse(activity.getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                        .getBoolean("show_player_name", true));
                assertTrue(((android.widget.CheckBox) findText(root, "Show date and time")).isChecked());
            });
        }
    }
}
