package com.chrober.lmsmaterial.nowplaying;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NowPlayingDisplayOptionsTest {
    @Test public void defaultsPreserveProgressAndQueueButHideTechnicalInformation() {
        String script = NowPlayingDisplayOptions.script(0, true, false, true, false, false, false, true, true);
        assertTrue(script.contains("var techWanted='false';"));
        assertTrue(script.contains("lms-material::techInfo"));
        assertFalse(script.contains(".np-tech{display:none"));
        assertFalse(script.contains(".np-tech{font-size:0"));
        assertFalse(script.contains(".np-controls #pos-slider{display:none"));
    }

    @Test public void progressIsIndependentOfPlaylistPosition() {
        String script = NowPlayingDisplayOptions.script(2, false, false, true, false, false, false, true, true);
        assertTrue(script.contains(".np-controls-wide .np-time-layout,"));
        assertTrue(script.contains(".np-controls .np-pos,"));
        assertTrue(script.contains(".np-controls .np-duration,"));
        assertTrue(script.contains(".np-controls #pos-slider{display:none!important;}"));
        assertFalse(script.contains(".np-tech{display:none"));
    }

    @Test public void hidingQueueAndTechnicalInformationRemovesEmptyMetadataRow() {
        String script = NowPlayingDisplayOptions.script(1, true, false, false, false, false, false, true, true);
        assertTrue(script.contains("var techWanted='false';"));
        assertTrue(script.contains(".np-tech{display:none!important;}"));
        assertFalse(script.contains(".np-controls #pos-slider{display:none"));
    }

    @Test public void hidingQueuePreservesTechnicalInformationIfSelected() {
        String script = NowPlayingDisplayOptions.script(0, true, true, false, false, false, false, true, true);
        assertTrue(script.contains("var techWanted='true';"));
        assertTrue(script.contains(".np-tech{font-size:0!important;}"));
        assertTrue(script.contains(".np-tech obj{font-size:.85rem!important;}"));
        assertFalse(script.contains(".np-tech{display:none!important;}"));
    }

    @Test public void changingColorOrMaterialTechnicalPreferenceTriggersSingleReload() {
        String script = NowPlayingDisplayOptions.script(2, true, false, true, false, false, false, true, true);
        assertTrue(script.contains("var expected='2';"));
        assertTrue(script.contains("localStorage.setItem(colorKey,expected);changed=true;"));
        assertTrue(script.contains("localStorage.setItem(techKey,techWanted);changed=true;"));
        assertTrue(script.contains("if(changed){location.reload();return 'reload';}"));
        assertTrue(script.contains("style.id='lyrion-display-options'"));
        assertTrue(script.contains("return 'ready';"));
    }

    @Test public void materialClockIsDisabledByDefaultWithoutUnhidingToolbar() {
        String script = NowPlayingDisplayOptions.script(0, true, false, true, false, false, false, true, true);
        assertTrue(script.contains("var clockWanted='false';"));
        assertTrue(script.contains("lms-material::nowPlayingClock"));
        assertFalse(script.contains("#main-toolbar{display:flex!important;}"));
        assertFalse(script.contains("installClock(20)"));
        assertFalse(script.contains("Date(")); // No parallel clock implementation.
    }

    @Test public void nativeMaterialClockCanBeShownWithoutShowingOtherToolbarControls() {
        String script = NowPlayingDisplayOptions.script(0, true, false, true, true, false, false, true, true);
        assertTrue(script.contains("var clockWanted='true';"));
        assertTrue(script.contains("lms-material::nowPlayingClock"));
        assertTrue(script.contains(":root{--main-toolbar-height:48px!important;}"));
        assertTrue(script.contains("#main-toolbar{display:flex!important;}"));
        assertTrue(script.contains("#main-toolbar>.v-toolbar__content>:not(.toolbar-clock){display:none!important;}"));
        assertTrue(script.contains("vm.$options.name!=='lms-toolbar'"));
        assertTrue(script.contains("Object.defineProperty(vm,'showClock'"));
        assertTrue(script.contains("this.$store.state.nowPlayingClock&&this.nowPlayingExpanded"));
        assertTrue(script.contains("vm.$forceUpdate();return;"));
        assertFalse(script.contains("new Date(")); // Material owns clock and updates.
    }

    @Test public void playerNameCanBeShownWithoutClockOrOtherControls() {
        String script = NowPlayingDisplayOptions.script(0, true, false, true, false, true, false, true, true);
        assertTrue(script.contains("var clockWanted='false';"));
        assertTrue(script.contains(":root{--main-toolbar-height:48px!important;}"));
        assertTrue(script.contains("#main-toolbar{display:flex!important;}"));
        assertTrue(script.contains("#main-toolbar>.v-toolbar__content>:not(.layout){display:none!important;}"));
        assertTrue(script.contains("#main-toolbar>.v-toolbar__content>.layout>:not(.v-toolbar__title),"));
        assertTrue(script.contains("#main-toolbar .v-toolbar__title .maintoolbar-subtitle,"));
        assertTrue(script.contains("#main-toolbar .v-toolbar__title .player-status-icon{display:none!important;}"));
        assertTrue(script.contains("#main-toolbar .v-toolbar__title{pointer-events:none!important;}"));
        assertFalse(script.contains("installClock(20)"));
        assertFalse(script.contains("jsonrpc"));
    }

    @Test public void playerNameAndClockCanShareTheNativeToolbar() {
        String script = NowPlayingDisplayOptions.script(0, true, false, true, true, true, false, true, true);
        assertTrue(script.contains("#main-toolbar>.v-toolbar__content>:not(.layout):not(.toolbar-clock){display:none!important;}"));
        assertTrue(script.contains("#main-toolbar>.v-toolbar__content>.layout>:not(.v-toolbar__title),"));
        assertTrue(script.contains("installClock(20)"));
        assertFalse(script.contains("#main-toolbar>.v-toolbar__content>:not(.toolbar-clock){display:none!important;}"));
    }

    @Test public void nativeTrackPreferencesAreIndependentForAllEightCombinations() {
        for (boolean trackNumber : new boolean[]{false, true}) {
            for (boolean background : new boolean[]{false, true}) {
                for (boolean artistContext : new boolean[]{false, true}) {
                    String script = NowPlayingDisplayOptions.script(0, true, false, true,
                            false, false, trackNumber, background, artistContext);
                    assertTrue(script.contains("var trackNumKey='lms-material::nowPlayingTrackNum';"));
                    assertTrue(script.contains("var trackNumWanted='" + trackNumber + "';"));
                    assertTrue(script.contains("var backdropKey='lms-material::nowPlayingBackdrop';"));
                    assertTrue(script.contains("var backdropWanted='" + background + "';"));
                    assertTrue(script.contains("var contextKey='lms-material::nowPlayingContext';"));
                    assertTrue(script.contains("var contextWanted='" + artistContext + "';"));
                    assertTrue(script.contains("var clockWanted='false';"));
                    assertFalse(script.contains("#main-toolbar{display:flex!important;}"));
                    assertFalse(script.contains(".np-tech{display:none"));
                    assertFalse(script.contains(".np-controls #pos-slider{display:none"));
                }
            }
        }
    }

    @Test public void nativeTrackPreferencesAreWrittenBeforeTheSingleConditionalReload() {
        String script = NowPlayingDisplayOptions.script(2, false, true, false,
                true, true, true, false, false);
        int reload = script.indexOf("if(changed){location.reload();return 'reload';}");
        assertTrue(reload > 0);
        String[] writes = {
                "if(localStorage.getItem(trackNumKey)!==trackNumWanted){localStorage.setItem(trackNumKey,trackNumWanted);changed=true;}",
                "if(localStorage.getItem(backdropKey)!==backdropWanted){localStorage.setItem(backdropKey,backdropWanted);changed=true;}",
                "if(localStorage.getItem(contextKey)!==contextWanted){localStorage.setItem(contextKey,contextWanted);changed=true;}"
        };
        for (String write : writes) {
            assertTrue(script.contains(write));
            assertTrue(script.indexOf(write) < reload);
        }
        assertTrue(script.indexOf("location.reload()") == script.lastIndexOf("location.reload()"));
        assertTrue(script.contains("installClock(20)"));
        assertTrue(script.contains(".np-controls #pos-slider{display:none!important;}"));
        assertTrue(script.contains(".np-tech{font-size:0!important;}"));
    }

    @Test public void clockSettingRemainsIndependentOfExistingContentOptions() {
        String script = NowPlayingDisplayOptions.script(2, false, true, false, true, false, false, true, true);
        assertTrue(script.contains("var techWanted='true';"));
        assertTrue(script.contains(".np-controls #pos-slider{display:none!important;}"));
        assertTrue(script.contains(".np-tech{font-size:0!important;}"));
        assertTrue(script.contains("#main-toolbar{display:flex!important;}"));
        assertTrue(script.contains("if(localStorage.getItem(clockKey)!==clockWanted){"));
        assertTrue(script.contains("localStorage.setItem(clockKey,clockWanted);changed=true;"));
    }
}
