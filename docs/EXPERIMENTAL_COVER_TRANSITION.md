# Experimental synchronized cover transition

This is an experimental Lyrion Display 0.4.4 RC, not a public release.

- Allowlisted revision: **6.5.0**. Exact /np-only markers and expanded Now Playing cover, track-info and background DOM/CSS contracts are required. Unknown versions or structural changes fail closed, removing all temporary layers and visibility markers.
- The previous foreground is a bounded canvas snapshot. The previous text and background are detached clones of the native visual elements. The background layer reuses the already displayed CSS image URL and computed filter/geometry; normal browser caching applies, no explicit preload request.
- When Vue updates the cover, metadata and background together, hold the previous visual state until the new foreground is loaded/decoded. Reveal the real new text and both native images in the same animation frame; fade the two previous image layers out together over 1500 ms.
- The playback position, duration, queue position, technical row and cover-based accent color remain native. No Vue state, text, URL, server preference or persistent client preference is modified.
- Same-cover title changes remain immediate and refresh the held snapshot. If metadata and cover change in separate Vue updates, text already painted before the cover change cannot be retroactively held; verify on real devices.
- Background disabled is supported (no background overlay). Missing covers, unsupported WebView features, decode failures, layout changes and an eight-second load timeout return to native rendering; resizing resets the snapshot.
- Initial load is not animated. Special dimmed/placeholder/overlay/emblem states use native rendering. Rapid skips discard outdated pending work. Test physical Fire TV performance with both layers, particularly the CSS blur, and check no stale text or image remains after fallback.
- `window.__lyrionCoverTransition.status` exposes the compatibility decision without URLs or names.
