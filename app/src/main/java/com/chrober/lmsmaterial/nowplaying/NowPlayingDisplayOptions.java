package com.chrober.lmsmaterial.nowplaying;

/**
 * Per-display customization of Material Skin's existing Now Playing UI.
 *
 * Material Skin's /np-only endpoint redirects to the ordinary application and
 * injects np-only.css. Technical info, track number, background image and
 * artist context use Material's origin-local preferences. Progress, queue
 * position, the optional clock and player name use scoped overrides of
 * the original Material Skin components.
 *
 * No changes are made to the Lyrion server or any other Material Skin client.
 */
final class NowPlayingDisplayOptions {
    private NowPlayingDisplayOptions() { }

    static String script(int colorUsage, boolean showProgress,
            boolean showTechnicalInfo, boolean showPlaylistPosition, boolean showClock,
            boolean showPlayerName, boolean showTrackNumber, boolean showBackgroundImage,
            boolean showArtistContext) {
        StringBuilder css = new StringBuilder();
        if (!showProgress) {
            // Hide both elapsed/remaining times and the progress slider in
            // expanded wide and regular layouts. Never hide the entire
            // controls container: it also contains track/queue information.
            css.append(".np-controls-wide .np-time-layout,")
               .append(".np-controls .np-time-layout,")
               .append(".np-controls .np-pos,")
               .append(".np-controls .np-duration,")
               .append(".np-controls #pos-slider{display:none!important;}");
        }
        if (!showPlaylistPosition) {
            if (showTechnicalInfo) {
                // Material renders the technical <obj> and the queue position
                // as separate children of the SAME .np-tech row: the latter
                // is a bare text node. CSS cannot target a bare text node, so
                // suppress inherited text, then restore text within <obj>.
                css.append(".np-tech{font-size:0!important;}")
                   .append(".np-tech obj{font-size:.85rem!important;}");
            } else {
                css.append(".np-tech{display:none!important;}");
            }
        }

        if (showClock || showPlayerName) {
            // np-only.css hides Material's toolbar and sets its height to zero.
            // Restore its existing clock and/or player title without moving them
            // or exposing the unrelated toolbar controls.
            css.append(":root{--main-toolbar-height:48px!important;}")
               .append("#main-toolbar{display:flex!important;}");
            if (showPlayerName) {
                // Vuetify renders the original v-layout as .layout and the
                // v-toolbar-title inside it as .v-toolbar__title.
                css.append("#main-toolbar>.v-toolbar__content>:not(.layout)")
                   .append(showClock ? ":not(.toolbar-clock)" : "")
                   .append("{display:none!important;}")
                   .append("#main-toolbar>.v-toolbar__content>.layout>:not(.v-toolbar__title),")
                   .append("#main-toolbar .v-toolbar__title .maintoolbar-subtitle,")
                   .append("#main-toolbar .v-toolbar__title .player-status-icon{display:none!important;}")
                   .append("#main-toolbar .v-toolbar__title{pointer-events:none!important;}");
            } else {
                css.append("#main-toolbar>.v-toolbar__content>:not(.toolbar-clock){display:none!important;}");
            }
        }

        // On wide desktop viewports Material Skin renders its clock directly.
        // On smaller Android WebViews, the original Vue component suppresses
        // it at <1300 CSS pixels. Relax only that computed visibility check,
        // keeping the clock's own DOM, formatting, localization and timer.
        // A future Material Skin update may require revisiting this adapter.
        String clockSetup = showClock
                ? "var installClock=function(tries){" +
                  "var bar=document.getElementById('main-toolbar');" +
                  "var vm=bar&&bar.__vue__;" +
                  "while(vm&&vm.$options&&vm.$options.name!=='lms-toolbar'&&" +
                  "vm.$options._componentTag!=='lms-toolbar'){vm=vm.$parent;}" +
                  "if(vm&&vm.$store&&typeof vm.$forceUpdate==='function'){" +
                  "if(!vm.__lyrionDisplayClock){" +
                  "Object.defineProperty(vm,'showClock',{configurable:true," +
                  "get:function(){return !!this.$store.state.nowPlayingClock&&" +
                  "this.nowPlayingExpanded;}});" +
                  "vm.__lyrionDisplayClock=true;}" +
                  "vm.$forceUpdate();return;}" +
                  "if(tries>0)setTimeout(function(){installClock(tries-1);},250);" +
                  "};installClock(20);"
                : "";

        return "(function(){try{" +
                "if(!document.getElementById('app'))return 'missing';" +
                "var colorKey='lms-material::colorUsage';" +
                "var expected='" + colorUsage + "';" +
                "var techKey='lms-material::techInfo';" +
                "var techWanted='" + showTechnicalInfo + "';" +
                "var clockKey='lms-material::nowPlayingClock';" +
                "var clockWanted='" + showClock + "';" +
                "var trackNumKey='lms-material::nowPlayingTrackNum';" +
                "var trackNumWanted='" + showTrackNumber + "';" +
                "var backdropKey='lms-material::nowPlayingBackdrop';" +
                "var backdropWanted='" + showBackgroundImage + "';" +
                "var contextKey='lms-material::nowPlayingContext';" +
                "var contextWanted='" + showArtistContext + "';" +
                "var changed=false;" +
                "if(localStorage.getItem(colorKey)!==expected){" +
                "localStorage.setItem(colorKey,expected);changed=true;}" +
                "if(localStorage.getItem(techKey)!==techWanted){" +
                "localStorage.setItem(techKey,techWanted);changed=true;}" +
                "if(localStorage.getItem(clockKey)!==clockWanted){" +
                "localStorage.setItem(clockKey,clockWanted);changed=true;}" +
                "if(localStorage.getItem(trackNumKey)!==trackNumWanted){" +
                "localStorage.setItem(trackNumKey,trackNumWanted);changed=true;}" +
                "if(localStorage.getItem(backdropKey)!==backdropWanted){" +
                "localStorage.setItem(backdropKey,backdropWanted);changed=true;}" +
                "if(localStorage.getItem(contextKey)!==contextWanted){" +
                "localStorage.setItem(contextKey,contextWanted);changed=true;}" +
                "if(changed){location.reload();return 'reload';}" +
                "var style=document.getElementById('lyrion-display-options');" +
                "if(!style){style=document.createElement('style');" +
                "style.id='lyrion-display-options';document.head.appendChild(style);}" +
                "style.textContent='" + css + "';" +
                clockSetup +
                "return 'ready';}catch(e){return 'missing';}})()";
    }
}
