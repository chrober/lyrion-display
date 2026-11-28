# Experimental foreground cover transition

This is an experimental Lyrion Display 0.4.4 RC, not a public release.

- Allowlisted Material Skin revision: **6.5.0** (source reviewed, physical-device verification pending). Uses the revision constant emitted by Material Skin, not a server-side modification.
- Exact /np-only mode markers plus expected expanded Now Playing image and parent DOM/CSS contract required.
- Unknown releases (including DEV/GIT builds), unexpected DOM, unsupported WebView features, decode failures and an eight-second load timeout fall back to Material Skin's own rendering. No persisted setting or network request is introduced.
- The adapter observes only the native foreground artwork, retains a bounded canvas snapshot of the last completely loaded image, masks the replacing image until load/decode, then fades the old snapshot out over 1500 ms. It does not alter native layout, Vue state, cover URLs, metadata, background image or accent color.
- Initial load and ordinary playback without artwork changes are not animated. Special dimmed/placeholder/overlay/emblem states use native rendering. Resizing resets the snapshot.
- Inspect console/logcat for `LyrionCoverTransition` status. The document's `window.__lyrionCoverTransition.status` exposes the compatibility decision without URLs or names.
- Test on the physical Fire TV Stick with Material Skin 6.5.0, large slow covers, rapid skips, same-album tracks, missing covers, background on/off, and a non-allowlisted version. Verify startup performance, no stale cover, no duplicate image requests, and native fallback.
- Future revisions require reviewing the exact DOM/CSS contract and testing before expanding the allowlist. Do not silently allow arbitrary versions.

Physical-device feedback confirmed that the initial crossfade was visible but too abrupt. Retain the original implementation and increase only its duration to 1500 ms; foreground/background/metadata synchronization is out of scope for this experiment.
