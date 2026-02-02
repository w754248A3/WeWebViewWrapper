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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用主界面，包含 WebView 核心逻辑、全屏切换处理以及存储授权管理。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WeWebViewWrapper";
    private WebView webView;
    private ValueCallback<Uri[]> mUploadCallback;
    private StringBuilder errorLogs = new StringBuilder();
    private TextView logTextView;
    private View logContainer;
    private View settingsContainer;
    private SwitchCompat switchDetailedLog;
    private boolean isDetailedLogEnabled = false;
    private LinearLayout bottomToolbar;
    private AssetResourceLoader assetLoader;

    // Fullscreen related
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private FrameLayout fullscreenContainer;
    private int originalOrientation;

    /**
     * 文件选择器启动器，处理标准文件选取请求（支持单选和多选）。
     */
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
                    
                    if (results != null) {
                        logInfo("File chooser result: SUCCESS, count=" + results.length);
                    } else {
                        logInfo("File chooser result: CANCELLED or EMPTY");
                    }
                    
                    mUploadCallback.onReceiveValue(results);
                    mUploadCallback = null;
                }
            }
    );

    /**
     * 目录选择器启动器，处理文件夹授权请求，并实现持久化访问。
     */
    private final ActivityResultLauncher<Uri> directoryChooserLauncher = registerForActivityResult(
            new ActivityActivityResultContractsOpenDocumentTree(),
            uri -> {
                if (mUploadCallback != null) {
                    Uri[] results = null;
                    if (uri != null) {
                        logInfo("Directory chooser result: SUCCESS, uri=" + uri.toString());
                        try {
                            // 1. 获取持久化访问权限
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(uri, takeFlags);

                            // 2. 将授权的 URI 静默保存到本地配置中
                            getSharedPreferences("storage_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("last_authorized_dir", uri.toString())
                                    .apply();

                            // 3. 直接返回目录 URI (适配 Web File System Access API)
                            results = new Uri[]{uri};
                            logInfo("Directory selected: " + uri.toString());
                        } catch (Exception e) {
                            logError("Directory Chooser Processing Error: " + Log.getStackTraceString(e));
                        }
                    } else {
                        logInfo("Directory chooser result: CANCELLED");
                    }
                    mUploadCallback.onReceiveValue(results);
                    mUploadCallback = null;
                }
            }
    );

    /**
     * 自定义目录选择合同，用于适配 ActivityResultLauncher。
     */
    private static class ActivityActivityResultContractsOpenDocumentTree extends ActivityResultContracts.OpenDocumentTree {
        @Override
        public Intent createIntent(Context context, Uri input) {
            Intent intent = super.createIntent(context, input);
            // 确保请求持久化权限所需的标志
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return intent;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 设置全局异常捕获，以便在调试控制台中显示
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logError("Uncaught Exception: " + Log.getStackTraceString(throwable));
        });

        setContentView(R.layout.activity_main);

        originalOrientation = getRequestedOrientation();
        assetLoader = new AssetResourceLoader(this, "mypage.test", "dist"); 

        initViews();
        setupWebView();
        setupBackPressed();
        
        // 开启 WebView 的远程调试模式 (仅在调试版开启)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)) {
                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
        
        webView.loadUrl("https://mypage.test/index.html");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /**
     * 初始化布局视图和底部工具栏按钮事件。
     */
    private void initViews() {
        webView = findViewById(R.id.webview);
        bottomToolbar = findViewById(R.id.bottom_toolbar);
        logContainer = findViewById(R.id.log_container);
        logTextView = findViewById(R.id.log_text);
        settingsContainer = findViewById(R.id.settings_container);
        switchDetailedLog = findViewById(R.id.switch_detailed_log);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        findViewById(R.id.btn_home).setOnClickListener(v -> {
            webView.loadUrl("https://mypage.test/index.html");
        });

        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        findViewById(R.id.btn_settings).setOnClickListener(v -> {
            settingsContainer.setVisibility(View.VISIBLE);
        });

        findViewById(R.id.btn_close_settings).setOnClickListener(v -> {
            settingsContainer.setVisibility(View.GONE);
        });

        findViewById(R.id.btn_open_log_viewer).setOnClickListener(v -> {
            logContainer.setVisibility(View.VISIBLE);
            logTextView.setText(errorLogs.toString());
        });

        switchDetailedLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isDetailedLogEnabled = isChecked;
            logInfo("Detailed Log Mode: " + (isChecked ? "ON" : "OFF"));
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

    /**
     * 配置 WebView 的核心属性，包括 JS 启用、DOM 存储以及资源请求拦截。
     */
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        
        // 设置缓存模式
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        webView.setWebViewClient(new WebViewClient() {
            /**
             * 拦截并处理 WebView 发起的资源请求，支持从 assets 中加载本地资源。
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldIntercept(request.getUrl());
            }

            /**
             * 捕获并记录 WebView 加载过程中的错误。
             */
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    logError("WebView Error [" + error.getErrorCode() + "]: " + error.getDescription() + " (at " + request.getUrl() + ")");
                }
            }
        });

        // 针对 Service Worker 拦截配置，确保离线能力
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
            /**
             * 处理 WebView 触发的文件选择请求。
             * 1. 支持标准文件选取 (showOpenFilePicker)。
             * 2. 支持通过 accept=".directory" 触发文件夹授权模式 (showDirectoryPicker)。
             * 3. 支持 WebView 132+ 的标准保存模式 (showSaveFilePicker)。
             */
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadCallback != null) {
                    try {
                        mUploadCallback.onReceiveValue(null);
                    } catch (Exception ignored) {}
                }
                mUploadCallback = filePathCallback;

                int mode = fileChooserParams.getMode();
                String[] acceptTypes = fileChooserParams.getAcceptTypes();
                String acceptStr = acceptTypes != null ? Arrays.toString(acceptTypes) : "none";
                logInfo("onShowFileChooser triggered. Mode: " + mode + ", Accept: " + acceptStr);

                // 1. 识别目录拾取 (MODE_OPEN_FOLDER = 2 或特定的 .directory 约定)
                boolean isDirectoryPick = (mode == 2);
                if (!isDirectoryPick && acceptTypes != null) {
                    for (String type : acceptTypes) {
                        if (".directory".equalsIgnoreCase(type)) {
                            isDirectoryPick = true;
                            break;
                        }
                    }
                }

                // 2. 识别保存模式 (MODE_SAVE = 3)
                boolean isSaveMode = (mode == 3);

                logInfo("Picker Type: " + (isDirectoryPick ? "DIRECTORY" : (isSaveMode ? "SAVE" : "OPEN")));

                try {
                    if (isDirectoryPick) {
                        logInfo("Launching directory chooser...");
                        directoryChooserLauncher.launch(null);
                    } else if (isSaveMode) {
                        logInfo("Launching save file chooser...");
                        // 手动构建 ACTION_CREATE_DOCUMENT 以确保兼容性
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        
                        // 设置 MIME 类型
                        if (acceptTypes != null && acceptTypes.length > 0 && !acceptTypes[0].isEmpty()) {
                            intent.setType(acceptTypes[0]);
                        } else {
                            intent.setType("*/*");
                        }
                        
                        // 设置预设文件名
                        String filename = fileChooserParams.getFilenameHint();
                        if (filename != null && !filename.isEmpty()) {
                            intent.putExtra(Intent.EXTRA_TITLE, filename);
                        }
                        
                        fileChooserLauncher.launch(intent);
                    } else {
                        // 标准打开文件 (单选或多选)
                        Intent intent = fileChooserParams.createIntent();
                        logInfo("Launching standard file chooser: " + intent.getAction());
                        fileChooserLauncher.launch(intent);
                    }
                } catch (Exception e) {
                    logError("File Chooser Launch Error: " + Log.getStackTraceString(e));
                    if (mUploadCallback != null) {
                        mUploadCallback.onReceiveValue(null);
                        mUploadCallback = null;
                    }
                    return false;
                }
                return true;
            }

            /**
             * 进入全屏模式播放视频。
             */
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

            /**
             * 退出全屏模式。
             */
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

    /**
     * 记录普通信息日志（仅在详细日志模式开启时记录）。
     */
    private void logInfo(String message) {
        if (isDetailedLogEnabled) {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            errorLogs.append("[").append(timestamp).append("] [INFO] ").append(message).append("\n\n");
            Log.i(TAG, message);
        }
    }

    /**
     * 记录错误日志并输出到控制台及调试面板。
     */
    private void logError(String message) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
        errorLogs.append("[").append(timestamp).append("] [ERROR] ").append(message).append("\n\n");
        Log.e(TAG, message);
    }

    /**
     * 退出全屏并恢复系统 UI 状态。
     */
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

    /**
     * 隐藏状态栏和导航栏（适配 Android 11+ 及旧版本）。
     */
    @SuppressWarnings("deprecation")
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

    /**
     * 显示状态栏和导航栏。
     */
    @SuppressWarnings("deprecation")
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

    /**
     * 设置返回键拦截逻辑：优先关闭日志、退出全屏、网页后退，最后才退出应用。
     */
    private void setupBackPressed() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (logContainer.getVisibility() == View.VISIBLE) {
                    logContainer.setVisibility(View.GONE);
                } else if (settingsContainer.getVisibility() == View.VISIBLE) {
                    settingsContainer.setVisibility(View.GONE);
                } else if (customView != null) {
                    exitFullscreen();
                } else if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    /**
     * 内部辅助类：负责拦截 WebView 的网络请求，并将其重定向到应用的 assets 目录。
     */
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
            if (url != null) {
                ((MainActivity)context).logInfo("Intercepting URL: " + url.toString());
            }
            if (url != null && url.getHost() != null && url.getHost().equals(virtualDomain)) {
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
            // 基础类型
            mimeTypes.put("html", "text/html");
            mimeTypes.put("css", "text/css");
            mimeTypes.put("js", "application/javascript");
            mimeTypes.put("json", "application/json");
            // 图片
            mimeTypes.put("png", "image/png");
            mimeTypes.put("jpg", "image/jpeg");
            mimeTypes.put("jpeg", "image/jpeg");
            mimeTypes.put("gif", "image/gif");
            mimeTypes.put("webp", "image/webp");
            mimeTypes.put("svg", "image/svg+xml");
            mimeTypes.put("ico", "image/x-icon");
            // WebAssembly
            mimeTypes.put("wasm", "application/wasm");
            // 字体
            mimeTypes.put("woff", "font/woff");
            mimeTypes.put("woff2", "font/woff2");
            mimeTypes.put("ttf", "font/ttf");
            mimeTypes.put("otf", "font/otf");
            // 媒体
            mimeTypes.put("mp3", "audio/mpeg");
            mimeTypes.put("mp4", "video/mp4");
            mimeTypes.put("wav", "audio/wav");
            mimeTypes.put("webm", "video/webm");
            // 其他
            mimeTypes.put("txt", "text/plain");
            mimeTypes.put("xml", "application/xml");
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
