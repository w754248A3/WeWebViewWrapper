# ProGuard rules for the app module.
# You can add custom rules here.

# Keep WebView classes and their members
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}

-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}

-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# Keep our custom DocumentsProvider
-keep class com.wewebviewwrapper.MyDocumentsProvider { *; }

# Keep MainActivity (it's referenced in Manifest)
-keep class com.wewebviewwrapper.MainActivity { *; }

# Keep AssetResourceLoader and its inner classes
-keep class com.wewebviewwrapper.MainActivity$AssetResourceLoader { *; }

# General WebView rules
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
