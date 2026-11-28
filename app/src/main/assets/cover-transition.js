/* Experimental, fail-closed Material Skin 6.5.0 /np-only cover transition.
 * No Vue hooks, server preference changes or persistent client preferences.
 * Keep a detached snapshot of the last fully loaded foreground cover and its
 * matching track text/background. On the same Vue DOM update, hold the old
 * visual state until the new cover decodes; release text and fade both covers
 * together. Native rendering is unchanged between transitions. ES5-compatible.
 */
(function () {
    'use strict';
    if (window.__lyrionCoverTransition) return window.__lyrionCoverTransition.status;
    var state = window.__lyrionCoverTransition = {status: 'checking', ready: false};
    var observer, retryTimer, loadTimer, fadeTimer, frame, style, waitingImage, listeningImage;
    var app, parent, image, source, previous, overlay, visualOverlay;
    var serial = 0, stopped = false;
    var WAIT = 'data-lyrion-cover-wait', OVERLAY = 'data-lyrion-cover-overlay';
    var VISUAL_WAIT = 'data-lyrion-visual-wait', VISUAL_OVERLAY = 'data-lyrion-visual-overlay';
    var duration = 1500, loadLimit = 8000;

    function report(status) {
        state.status = status;
        if (window.console && console.info) console.info('LyrionCoverTransition: ' + status);
    }
    function compatibleVersion() {
        // Exact release allowlist; DEV/GIT builds and future releases are
        // deliberately native-only until their DOM contract is reviewed.
        return typeof LMS_MATERIAL_REVISION !== 'undefined' && LMS_MATERIAL_REVISION === '6.5.0';
    }
    function npOnly() {
        return /(?:^|[?&])css=msk--np-only(?:&|$)/.test(location.search) &&
            /(?:^|[?&])js=msk--np-only(?:&|$)/.test(location.search);
    }
    function cancelPending() {
        ++serial;
        clearTimeout(loadTimer);
        if (listeningImage) listeningImage.removeEventListener('load', onLoad);
        listeningImage = null;
    }
    function removeOverlay() {
        clearTimeout(fadeTimer);
        if (frame !== undefined) cancelAnimationFrame(frame);
        frame = undefined;
        if (waitingImage) waitingImage.removeAttribute(WAIT);
        waitingImage = null;
        if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay);
        overlay = null;
        clearVisualOverlay();
    }
    function disable(reason) {
        if (stopped) return;
        stopped = true;
        clearTimeout(retryTimer);
        if (observer) observer.disconnect();
        window.removeEventListener('resize', onResize);
        window.removeEventListener('pagehide', onPageHide);
        cancelPending();
        removeOverlay();
        if (style && style.parentNode) style.parentNode.removeChild(style);
        previous = null;
        state.ready = false;
        report(reason);
    }
    function onPageHide() { disable('page-hidden'); }
    function getImage() {
        if (!compatibleVersion() || !npOnly() || !app || !document.documentElement.contains(app)) return null;
        var pages = app.querySelectorAll('#np-page');
        if (pages.length !== 1) return null;
        var covers = pages[0].querySelectorAll('.np-cover');
        if (covers.length !== 1) return null;
        var img = covers[0], p = img.parentNode;
        if (img.tagName !== 'IMG' || !p || img.hasAttribute('srcset') || !img.getAttribute('src')) return null;
        if (!(p.classList.contains('np-image') || p.classList.contains('np-image-landscape'))) return null;
        if (p.querySelectorAll('img.np-cover').length !== 1 ||
            getComputedStyle(p).position !== 'relative' ||
            getComputedStyle(img).objectFit !== 'contain') return null;
        if (!visualParts(img)) return null;
        return img;
    }

    // Only the known 6.5.0 expanded-page contract is supported. No Vue state
    // or native text/URLs are rewritten, and a background-off element remains
    // a valid part of the contract.
    function visualParts(img) {
        var page=app.querySelector('#np-page');
        if (!page) return null;
        var tracks=page.querySelectorAll('#np-track-info');
        var backgrounds=app.querySelectorAll('.np-bgnd');
        if (tracks.length!==1 || backgrounds.length!==1) return null;
        var track=tracks[0], wrapper=track.parentNode, bgParent=backgrounds[0];
        if (!wrapper || !(wrapper.classList.contains('np-landscape-track-info') ||
            wrapper.classList.contains('np-portrait-track-info'))) return null;
        if (track.tagName!=='DIV' || track.querySelector('img,video,canvas,iframe,object')) return null;
        var nativeBg=bgParent.firstElementChild;
        if (!nativeBg || nativeBg.tagName!=='DIV' ||
            !(nativeBg.classList.contains('np-bgnd-cover') ||
              nativeBg.classList.contains('np-bgnd-cover-none'))) return null;
        // The real background is the first child; our temporary layer is
        // explicitly marked and never considered part of the native contract.
        for (var n=nativeBg.nextElementSibling;n;n=n.nextElementSibling)
            if (!n.hasAttribute(VISUAL_OVERLAY)) return null;
        return {track:track, wrapper:wrapper, bg:nativeBg, bgParent:bgParent};
    }
    function stripIds(node) {
        node.removeAttribute('id');
        var ids=node.querySelectorAll('[id]');
        for (var i=0;i<ids.length;i++) ids[i].removeAttribute('id');
    }
    function markClone(node,kind) {
        node.removeAttribute(VISUAL_WAIT);
        node.setAttribute(VISUAL_OVERLAY,kind);
        node.setAttribute('aria-hidden','true');
        node.style.pointerEvents='none';
        node.style.transition='none';
        node.style.opacity='1';
        return node;
    }
    function copyComputed(node,css,properties) {
        for(var i=0;i<properties.length;i++){
            var k=properties[i], v=css.getPropertyValue(k);
            if(v)node.style.setProperty(k,v,'important');
        }
    }
    function captureVisual(img) {
        var parts=visualParts(img);
        if (!parts) return null;
        var t=parts.track, r=t.getBoundingClientRect(), css=getComputedStyle(t);
        var text=markClone(t.cloneNode(true),'text');
        stripIds(text);
        // Keep the original text's exact rectangle and styling while Vue
        // freely updates the real element beneath it.
        copyComputed(text,css,['box-sizing','text-align','font-family','font-size',
            'font-weight','line-height','color','overflow-x','overflow-y']);
        text.style.setProperty('position','fixed','important');
        text.style.setProperty('left',r.left+'px','important');
        text.style.setProperty('top',r.top+'px','important');
        text.style.setProperty('width',(r.width||r.right-r.left)+'px','important');
        text.style.setProperty('height',(r.height||r.bottom-r.top)+'px','important');
        text.style.setProperty('max-height','none','important');
        text.style.setProperty('margin','0','important');
        var bg=null, bcss=getComputedStyle(parts.bg);
        if (parts.bg.classList.contains('np-bgnd-cover') &&
            bcss.backgroundImage!=='none' && getComputedStyle(parts.bgParent).display!=='none') {
            bg=markClone(parts.bg.cloneNode(false),'background');
            stripIds(bg);
            // Copy the already-used CSS URL and actual computed layout/filter
            // because Material's geometry rules target only its first child.
            // No explicit preload request; browser cache policy still applies.
            copyComputed(bg,bcss,['position','top','left','right','bottom','width','height',
                'box-sizing','margin-top','margin-left','margin-right','margin-bottom',
                'background-image','background-color','background-size','background-repeat',
                'background-position','filter','-webkit-filter','transform','transform-origin',
                'box-shadow','overflow','backdrop-filter','-webkit-backdrop-filter']);
        }
        return {parts:parts,text:text,bg:bg,scrollTop:t.scrollTop};
    }
    function revealVisual() {
        if (!visualOverlay) return;
        var v=visualOverlay;
        v.parts.track.removeAttribute(VISUAL_WAIT);
        v.parts.bg.removeAttribute(VISUAL_WAIT);
        if (v.text.parentNode) v.text.parentNode.removeChild(v.text);
    }
    function clearVisualOverlay() {
        if (!visualOverlay) return;
        revealVisual();
        if (visualOverlay.bg && visualOverlay.bg.parentNode)
            visualOverlay.bg.parentNode.removeChild(visualOverlay.bg);
        visualOverlay=null;
    }
    function holdVisual(img) {
        var v=previous.visual, parts=visualParts(img);
        if (!v || !parts || v.parts.track!==parts.track || v.parts.wrapper!==parts.wrapper ||
            v.parts.bg!==parts.bg || v.parts.bgParent!==parts.bgParent) return false;
        visualOverlay=v;
        parts.wrapper.appendChild(v.text);
        v.text.scrollTop=v.scrollTop;
        parts.track.setAttribute(VISUAL_WAIT,'');
        if (v.bg) {
            v.bg.style.transition='none';
            v.bg.style.opacity='1';
            parts.bgParent.appendChild(v.bg);
        }
        parts.bg.setAttribute(VISUAL_WAIT,'');
        return true;
    }
    function geometry(img) {
        var r = img.getBoundingClientRect(), p = img.parentNode.getBoundingClientRect();
        return {left:r.left-p.left-img.parentNode.clientLeft,
                top:r.top-p.top-img.parentNode.clientTop,
                width:r.width || r.right-r.left, height:r.height || r.bottom-r.top};
    }
    function sameGeometry(a,b) {
        return a && b && Math.abs(a.left-b.left)<1 && Math.abs(a.top-b.top)<1 &&
            Math.abs(a.width-b.width)<1 && Math.abs(a.height-b.height)<1;
    }
    function normal(img) {
        var g = geometry(img);
        // Material's intentionally dimmed placeholders/overlays keep their
        // native behavior; also decline unusual/invisible layouts.
        return g.width>0 && g.height>0 && !img.classList.contains('np-trans') &&
            !img.classList.contains('np-cover-dim') && getComputedStyle(img).opacity === '1' &&
            !img.parentNode.querySelector('.np-emblem, .np-skip');
    }
    function snapshot(img, url) {
        var g = geometry(img), css = getComputedStyle(img), c = document.createElement('canvas');
        if (!img.complete || !img.naturalWidth || !img.naturalHeight || !g.width || !g.height) return null;
        // Bound memory to two ~4 MiB RGBA surfaces even on a 4K display.
        var scale = Math.min(window.devicePixelRatio || 1, 1024/Math.max(g.width,g.height));
        c.width = Math.max(1, Math.round(g.width*scale));
        c.height = Math.max(1, Math.round(g.height*scale));
        var ctx = c.getContext('2d');
        if (!ctx) return null;
        var fit = Math.min(c.width/img.naturalWidth,c.height/img.naturalHeight);
        var w = img.naturalWidth*fit,h = img.naturalHeight*fit;
        // No readback (works for cross-origin covers too). No second image request.
        ctx.drawImage(img,(c.width-w)/2,(c.height-h)/2,w,h);
        c.setAttribute(OVERLAY,'');
        c.setAttribute('aria-hidden','true');
        c.style.cssText = 'position:absolute;pointer-events:none;z-index:1;margin:0;padding:0;border:0;opacity:1;';
        c.style.left=g.left+'px'; c.style.top=g.top+'px';
        c.style.width=g.width+'px'; c.style.height=g.height+'px';
        c.style.borderRadius=css.borderRadius; c.style.boxShadow=css.boxShadow;
        c.style.backgroundColor=css.backgroundColor;
        var visual=captureVisual(img);
        if (!visual) return null;
        return {canvas:c,url:url,geometry:g,parent:img.parentNode,visual:visual};
    }
    function showPrevious(img) {
        if (!previous || previous.parent !== img.parentNode ||
            !sameGeometry(previous.geometry,geometry(img))) return;
        if (!holdVisual(img)) { disable('changed-layout'); return; }
        overlay = previous.canvas;
        overlay.style.transition='none'; overlay.style.opacity='1';
        // Attach before masking the native replacement. MutationObserver runs
        // after Vue's DOM patch and before the browser's next normal paint.
        parent.appendChild(overlay);
        waitingImage=img;
        img.setAttribute(WAIT,'');
    }
    function finish(img,url,token) {
        if (stopped || token!==serial) return;
        try {
            if (getImage()!==img || img.getAttribute('src')!==url) { sync(); return; }
            if (!normal(img)) { sync(); return; }
            var next = snapshot(img,url);
            if (!next) { disable('snapshot-unavailable'); return; }
            cancelPending();
            previous=next;
            state.ready=true;
            if (!overlay) return;
            var old=overlay, bg=visualOverlay && visualOverlay.bg;
            // Both native images and the native text become visible in the
            // same animation frame. Keep the existing 1500ms fade-out.
            old.style.transition='opacity '+duration+'ms ease-in-out';
            if(bg)bg.style.transition='opacity '+duration+'ms ease-in-out';
            frame=requestAnimationFrame(function(){
                frame=undefined;
                if (overlay!==old || stopped) return;
                if(waitingImage)waitingImage.removeAttribute(WAIT);
                waitingImage=null;
                revealVisual();
                old.style.opacity='0';
                if(bg)bg.style.opacity='0';
                clearTimeout(fadeTimer);
                fadeTimer=setTimeout(function(){if(overlay===old)removeOverlay();},duration+100);
            });
            // Safety bound for a hidden/stalled document whose frame never runs.
            fadeTimer=setTimeout(function(){if(overlay===old)removeOverlay();},duration+2000);
        } catch(e) { disable('render-fallback'); }
    }
    function onLoad() {
        var img=listeningImage, url=source, token=serial;
        if (!img || !img.complete || !img.naturalWidth) return;
        img.removeEventListener('load',onLoad);
        // decode() is optional on older Fire OS WebViews. Their load event
        // and natural dimensions are the compatibility fallback.
        if (typeof img.decode==='function') {
            try {
                img.decode().then(function(){finish(img,url,token);},function(){
                    if (token===serial && !stopped) disable('decode-fallback');
                });
            } catch(e) { disable('decode-fallback'); }
        } else finish(img,url,token);
    }
    function sync(force) {
        if (stopped) return;
        try {
            var next=getImage();
            if (!next) { disable('unsupported-dom'); return; }
            var url=next.getAttribute('src'), p=next.parentNode;
            if (parent && p!==parent && !force) { disable('changed-layout'); return; }
            if (!normal(next)) {
                cancelPending(); removeOverlay(); previous=null;
                image=null; parent=p; source=null; state.ready=false;
                return;
            }
            if(visualOverlay) {
                var parts=visualParts(next), held=visualOverlay.parts;
                if(!parts || parts.track!==held.track || parts.wrapper!==held.wrapper ||
                    parts.bg!==held.bg || parts.bgParent!==held.bgParent) {
                    disable('changed-layout');return;
                }
            }
            if (!force && next===image && url===source) {
                // Same-album metadata is immediately native. Refresh the
                // detached snapshot so the next different cover holds the
                // most recent displayed text, not the first track of an album.
                if (previous && previous.url===url && !waitingImage) {
                    var visual=captureVisual(next);
                    if(!visual){disable('unsupported-dom');return;}
                    previous.visual=visual;
                }
                return;
            }
            cancelPending(); removeOverlay();
            if (force || (previous && !sameGeometry(previous.geometry,geometry(next)))) previous=null;
            image=next; parent=p; source=url;
            if (previous && previous.url!==url) showPrevious(next);
            if(stopped)return;
            listeningImage=next;
            next.addEventListener('load',onLoad);
            loadTimer=setTimeout(function(){ disable('load-timeout'); },loadLimit);
            onLoad();
        } catch(e) { disable('render-fallback'); }
    }
    function onResize() { sync(true); }
    function relevant(records) {
        var parts=previous && previous.visual ? previous.visual.parts : (image && visualParts(image));
        for (var i=0;i<records.length;i++) {
            var r=records[i], t=r.target, e=t.nodeType===1?t:t.parentElement;
            if(!e)continue;
            if(e.closest && e.closest('['+OVERLAY+'],['+VISUAL_OVERLAY+']'))continue;
            if(r.type==='childList') {
                var nodes=Array.prototype.slice.call(r.addedNodes).concat(Array.prototype.slice.call(r.removedNodes));
                if(nodes.length && nodes.every(function(n){return n.nodeType===1 &&
                    (n.hasAttribute(OVERLAY)||n.hasAttribute(VISUAL_OVERLAY));}))continue;
            }
            if (r.type==='attributes') {
                if (t===image || t===parent || (parent && t.parentNode===parent && t.tagName==='IMG') ||
                    (r.attributeName==='id' && parent && t.contains(parent))) return true;
                if(parts && (e===parts.bg || e===parts.bgParent || e===parts.track ||
                    e===parts.wrapper || parts.track.contains(e)))return true;
            } else {
                if (parent && (e===parent || parent.contains(e) || e.contains(parent))) return true;
                if(parts && (parts.wrapper.contains(e)||parts.bgParent.contains(e)||
                    e.contains(parts.wrapper)||e.contains(parts.bgParent)))return true;
            }
        }
        return !!parent && !app.contains(parent);
    }
    function start(remaining) {
        if (stopped) return;
        try {
            app=document.getElementById('app');
            if (!app || !app.querySelector('#np-page .np-cover')) {
                if (remaining>0) { retryTimer=setTimeout(function(){start(remaining-1);},250); return; }
                disable('unsupported-dom'); return;
            }
            var first=getImage();
            if (!first) { disable('unsupported-dom'); return; }
            parent=first.parentNode;
            style=document.createElement('style');
            style.textContent='img['+WAIT+'],['+VISUAL_WAIT+']{visibility:hidden!important;}';
            document.head.appendChild(style);
            observer=new MutationObserver(function(records){if(relevant(records))sync(false);});
            observer.observe(app,{childList:true,subtree:true,characterData:true,attributes:true,attributeFilter:['src','class','id','srcset','style']});
            window.addEventListener('resize',onResize);
            window.addEventListener('pagehide',onPageHide);
            report('active');
            sync(false);
        } catch(e) { disable('unsupported-dom'); }
    }
    if (!npOnly()) { report('unsupported-mode'); return state.status; }
    if (!compatibleVersion()) { report('unsupported-version'); return state.status; }
    if (!window.MutationObserver || !window.requestAnimationFrame || !window.cancelAnimationFrame) {
        report('unsupported-webview'); return state.status;
    }
    start(20);
    return state.status;
})()
