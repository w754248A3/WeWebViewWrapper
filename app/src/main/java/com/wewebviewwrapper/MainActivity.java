package com.wewebviewwrapper;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WeWebViewWrapper";
    private WebView webView;
    private ValueCallback<Uri[]> mUploadCallback;
    private StringBuilder errorLogs = new StringBuilder();
    private TextView logTextView;
    private View logContainer;
    private LinearLayout bottomToolbar;
    private AssetResourceLoader assetLoader;

    // Fullscreen related
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;
    private int originalOrientation;

    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mUploadCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        } else if (result.getData().getClipData() != null) {
                            ClipData clipData = result.getData().getClipData();
                            results = new Uri[clipData.getItemCount()];
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                results[i] = clipData.getItemAt(i).getUri();
                            }
                        }
                    }
                    mUploadCallback.onReceiveValue(results);
                    mUploadCallback = null;
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Setup error logging for this activity's lifecycle
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logError("Uncaught Exception: " + Log.getStackTraceString(throwable));
        });

        setContentView(R.layout.activity_main);

        originalOrientation = getRequestedOrientation();
        assetLoader = new AssetResourceLoader(this, "mypage.test", "dist"); 

        initViews();
        setupWebView();
        
        // Enable remote debugging for WebView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        
        webView.loadUrl("https://mypage.test/index.html");
    }

    private void initViews() {
        webView = findViewById(R.id.webview);
        bottomToolbar = findViewById(R.id.bottom_toolbar);
        logContainer = findViewById(R.id.log_container);
        logTextView = findViewById(R.id.log_text);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        findViewById(R.id.btn_home).setOnClickListener(v -> {
            webView.loadUrl("https://mypage.test/index.html");
        });

        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        findViewById(R.id.btn_log).setOnClickListener(v -> {
            logContainer.setVisibility(View.VISIBLE);
            logTextView.setText(errorLogs.toString());
        });

        findViewById(R.id.btn_close_log).setOnClickListener(v -> {
            logContainer.setVisibility(View.GONE);
        });

        findViewById(R.id.btn_copy_log).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Error Logs", errorLogs.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Logs copied to clipboard", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        // Performance
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldIntercept(request.getUrl());
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                logError("WebView Error [" + errorCode + "]: " + description + " (at " + failingUrl + ")");
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController swController = ServiceWorkerController.getInstance();
            swController.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return assetLoader.shouldIntercept(request.getUrl());
                }
            });
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadCallback != null) mUploadCallback.onReceiveValue(null);
                mUploadCallback = filePathCallback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    mUploadCallback = null;
                    logError("File Chooser Error: " + e.getMessage());
                    return false;
                }
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    onHideCustomView();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                
                fullscreenContainer = new FrameLayout(MainActivity.this);
                fullscreenContainer.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                fullscreenContainer.setBackgroundColor(0xFF000000);
                fullscreenContainer.addView(view);
                
                ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
                decorView.addView(fullscreenContainer);
                
                hideSystemUI();
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                bottomToolbar.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });

        // Auto-hide toolbar logic
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY > oldScrollY && scrollY > 100) {
                    // Scrolling down
                    if (bottomToolbar.getVisibility() == View.VISIBLE) {
                        bottomToolbar.animate().translationY(bottomToolbar.getHeight()).setDuration(200).withEndAction(() -> bottomToolbar.setVisibility(View.GONE));
                    }
                } else if (scrollY < oldScrollY) {
                    // Scrolling up
                    if (bottomToolbar.getVisibility() != View.VISIBLE) {
                        bottomToolbar.setVisibility(View.VISIBLE);
                        bottomToolbar.animate().translationY(0).setDuration(200);
                    }
                }
            });
        }
    }

    private void logError(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        errorLogs.append("[").append(timestamp).append("] ").append(message).append("\n\n");
        Log.e(TAG, message);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        showSystemUI();
        setRequestedOrientation(originalOrientation);
        ViewGroup decorView = (ViewGroup) getWindow().getDecorView();
        decorView.removeView(fullscreenContainer);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customView = null;
        customViewCallback = null;
        fullscreenContainer = null;
        bottomToolbar.setVisibility(View.VISIBLE);
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    public void onBackPressed() {
        if (logContainer.getVisibility() == View.VISIBLE) {
            logContainer.setVisibility(View.GONE);
            return;
        }
        if (customView != null) {
            exitFullscreen();
            return;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private static class AssetResourceLoader {
        private Activity context;
        private String virtualDomain;
        private String localAssetBase;
        private Map<String, String> mimeTypes;

        public AssetResourceLoader(Activity context, String domain, String assetBase) {
            this.context = context;
            this.virtualDomain = domain;
            this.localAssetBase = assetBase;
            initMimeTypes();
        }

        public WebResourceResponse shouldIntercept(Uri url) {
            if (url.getHost() != null && url.getHost().equals(virtualDomain)) {
                String assetPath = "";
                try {
                    String path = url.getPath();
                    if (path == null || path.equals("/") || path.isEmpty()) {
                        path = "/index.html";
                    }
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    assetPath = (localAssetBase.isEmpty() ? "" : localAssetBase + "/") + path;

                    InputStream stream = context.getAssets().open(assetPath);
                    String mimeType = getMimeType(assetPath);
                    return new WebResourceResponse(mimeType, "UTF-8", stream);
                } catch (IOException e) {
                    Log.e("WebViewDebug", "File not found: " + assetPath);
                    String errorHtml = "<html><body><h2 style='color:red;'>404 Not Found</h2><p>" + assetPath + "</p></body></html>";
                    return new WebResourceResponse("text/html", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(errorHtml.getBytes()));
                }
            }
            return null;
        }

        private void initMimeTypes() {
            mimeTypes = new HashMap<>();
            mimeTypes.put("html", "text/html");
            mimeTypes.put("css", "text/css");
            mimeTypes.put("js", "application/javascript");
            mimeTypes.put("json", "application/json");
            mimeTypes.put("png", "image/png");
            mimeTypes.put("jpg", "image/jpeg");
            mimeTypes.put("svg", "image/svg+xml");
        }

        private String getMimeType(String url) {
            String extension = "";
            int i = url.lastIndexOf('.');
            if (i > 0) extension = url.substring(i + 1);
            String mime = mimeTypes.get(extension);
            return mime != null ? mime : "application/octet-stream";
        }
    }
}
