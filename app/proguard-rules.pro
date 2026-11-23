# R8 rules for Lyrion Display's release build.
# The standard Android optimization rules keep manifest components. The app has
# no JavaScript-to-Java interface or reflective class loading.
#
# API 22 must never resolve WebView.postVisualStateCallback (introduced API 23).
# Preserve this explicitly separated API 23 wrapper and its nested callback
# classes against R8 class merging/inlining across the SDK_INT guard.
-keep class com.chrober.lmsmaterial.nowplaying.WebViewApi23 { *; }
-keep class com.chrober.lmsmaterial.nowplaying.WebViewApi23$* { *; }
