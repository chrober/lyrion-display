package com.chrober.lmsmaterial.nowplaying;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import android.view.View;
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

    @Test public void legacyStartingWindowIsDisabledToRevealThemedNativeLoadingView() {
        if (Build.VERSION.SDK_INT >= 31) return;
        Resources.Theme launchTheme =
                ApplicationProvider.getApplicationContext().getResources().newTheme();
        launchTheme.applyStyle(R.style.AppTheme, true);
        TypedArray values = launchTheme.obtainStyledAttributes(
                new int[]{android.R.attr.windowDisablePreview});
        try {
            assertTrue("Legacy Android should draw the app's own first frame, "
                    + "not a separate launch preview", values.getBoolean(0, false));
        } finally {
            values.recycle();
        }
    }

    @Test public void activityWindowBackgroundMatchesSavedMaterialTheme() {
        assertWindowBackground("dark", 0xff212121);
        assertWindowBackground("light", 0xfffafafa);
        assertWindowBackground("black", 0xff000000);
        assertWindowBackground("dark-lyrion", 0xff081f22);
    }

    private void assertWindowBackground(String theme, int expected) {
        ApplicationProvider.getApplicationContext()
                .getSharedPreferences("material_now_playing", Context.MODE_PRIVATE)
                .edit().putString("material_theme", theme).commit();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Drawable background = activity.getWindow().getDecorView().getBackground();
                assertTrue("Expected a solid window background for " + theme,
                        background instanceof ColorDrawable);
                assertEquals("Window background for " + theme,
                        expected, ((ColorDrawable) background).getColor());
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


    @Test public void contentOptionsSaveImmediatelyWithoutSavingButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                android.widget.CheckBox technical = (android.widget.CheckBox)
                        findText(root, "Show technical track information");
                android.widget.CheckBox clock = (android.widget.CheckBox)
                        findText(root, "Show date and time");
                assertNotNull(technical);
                assertNotNull(clock);
                assertFalse(technical.isChecked());
                assertFalse(clock.isChecked());
                technical.performClick();
                clock.performClick();
                android.content.SharedPreferences prefs = activity.getSharedPreferences(
                        "material_now_playing", Context.MODE_PRIVATE);
                assertTrue(prefs.getBoolean("show_technical_info", false));
                assertTrue(prefs.getBoolean("show_material_clock", false));
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
            });
        }
    }
}
