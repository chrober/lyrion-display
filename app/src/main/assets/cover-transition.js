/* Experimental, fail-closed Material Skin 6.5.0 /np-only cover transition.
 * No server requests, Vue hooks, metadata changes or persistent preferences.
 * Keep a detached snapshot of the last fully loaded foreground cover. Before
 * the next paint, mask the replacement image until it is ready, then fade the
 * snapshot out over Material's own new image. Native rendering is unchanged
 * between transitions. ES5-compatible for Android API 22 WebViews.
 */
(function () {
    'use strict';
    if (window.__lyrionCoverTransition) return window.__lyrionCoverTransition.status;
    var state = window.__lyrionCoverTransition = {status: 'checking', ready: false};
    var observer, retryTimer, loadTimer, fadeTimer, frame, style, waitingImage, listeningImage;
    var app, parent, image, source, previous, overlay;
    var serial = 0, stopped = false;
    var WAIT = 'data-lyrion-cover-wait', OVERLAY = 'data-lyrion-cover-overlay';
    var duration = 400, loadLimit = 8000;

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
        return img;
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
        return {canvas:c,url:url,geometry:g,parent:img.parentNode};
    }
    function showPrevious(img) {
        if (!previous || previous.parent !== img.parentNode ||
            !sameGeometry(previous.geometry,geometry(img))) return;
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
            if (waitingImage) waitingImage.removeAttribute(WAIT);
            waitingImage=null;
            if (!overlay) return;
            var old=overlay;
            // Keep Material's real decoded image visible underneath.
            old.style.transition='opacity '+duration+'ms ease-in-out';
            frame=requestAnimationFrame(function(){
                frame=undefined;
                if (overlay===old) old.style.opacity='0';
            });
            fadeTimer=setTimeout(function(){ if (overlay===old) removeOverlay(); },duration+80);
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
            if (!force && next===image && url===source) return;
            cancelPending(); removeOverlay();
            if (force || (previous && !sameGeometry(previous.geometry,geometry(next)))) previous=null;
            image=next; parent=p; source=url;
            if (previous && previous.url!==url) showPrevious(next);
            listeningImage=next;
            next.addEventListener('load',onLoad);
            loadTimer=setTimeout(function(){ disable('load-timeout'); },loadLimit);
            onLoad();
        } catch(e) { disable('render-fallback'); }
    }
    function onResize() { sync(true); }
    function relevant(records) {
        for (var i=0;i<records.length;i++) {
            var r=records[i], t=r.target;
            if (r.type==='attributes') {
                if (t===image || t===parent || (parent && t.parentNode===parent && t.tagName==='IMG') ||
                    (r.attributeName==='id' && parent && t.contains(parent))) return true;
            } else if (parent && (t===parent || parent.contains(t) || t.contains(parent))) return true;
        }
        // Parent removal may leave a detached node outside all observed targets.
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
            style.textContent='img['+WAIT+']{visibility:hidden!important;}';
            document.head.appendChild(style);
            observer=new MutationObserver(function(records){if(relevant(records))sync(false);});
            observer.observe(app,{childList:true,subtree:true,attributes:true,attributeFilter:['src','class','id','srcset']});
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
