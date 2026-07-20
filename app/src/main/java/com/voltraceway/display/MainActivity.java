package com.voltraceway.display;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final String PREFERENCES = "volt_display_preferences";
    private static final String KEY_DISPLAY_URL = "display_url";
    private static final long RETRY_DELAY_MS = 15_000L;
    private static final long BACK_PRESS_WINDOW_MS = 2_500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable retryRunnable = this::loadConfiguredUrl;

    private SharedPreferences preferences;
    private FrameLayout root;
    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;
    private long lastBackPressAt;
    private boolean pageLoadFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);

        enterImmersiveMode();
        createDisplay();

        if (getConfiguredUrl().isEmpty()) {
            showSettingsDialog(false);
        } else {
            loadConfiguredUrl();
        }
    }

    private void createDisplay() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        webView = new WebView(this);
        configureWebView(webView);
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(4));
        progressParams.gravity = Gravity.TOP;
        root.addView(progressBar, progressParams);

        statusText = new TextView(this);
        statusText.setBackgroundColor(0xDD111111);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(20);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(32), dp(24), dp(32), dp(24));
        statusText.setVisibility(View.GONE);
        root.addView(statusText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void configureWebView(WebView view) {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setUserAgentString(settings.getUserAgentString() + " VoltDisplay/1.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        }

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView currentView, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView currentView, String url, Bitmap favicon) {
                pageLoadFailed = false;
                handler.removeCallbacks(retryRunnable);
                hideStatus();
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView currentView, String url) {
                progressBar.setVisibility(View.GONE);
                if (!pageLoadFailed) {
                    handler.removeCallbacks(retryRunnable);
                    hideStatus();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView currentView, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView currentView, String url) {
                return handleNavigation(Uri.parse(url));
            }

            @Override
            public void onReceivedError(WebView currentView, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showRetryMessage();
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView currentView,
                    WebResourceRequest request,
                    WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) {
                    showRetryMessage();
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView currentView, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    showRetryMessage();
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView currentView, RenderProcessGoneDetail detail) {
                handler.removeCallbacks(retryRunnable);
                currentView.destroy();
                webView = null;
                recreate();
                return true;
            }
        });

        view.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                openExternal(Uri.parse(url)));
    }

    private boolean handleNavigation(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            return false;
        }
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_LONG).show();
        }
    }

    private void loadConfiguredUrl() {
        if (webView == null) {
            return;
        }

        String url = getConfiguredUrl();
        if (url.isEmpty()) {
            showSettingsDialog(false);
            return;
        }

        handler.removeCallbacks(retryRunnable);
        webView.loadUrl(url);
    }

    private String getConfiguredUrl() {
        return preferences.getString(KEY_DISPLAY_URL, "");
    }

    private void showRetryMessage() {
        pageLoadFailed = true;
        showStatus(getString(R.string.retry_message));
        handler.removeCallbacks(retryRunnable);
        handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        statusText.bringToFront();
    }

    private void hideStatus() {
        statusText.setVisibility(View.GONE);
    }

    private void showSettingsDialog(boolean allowCancel) {
        handler.removeCallbacks(retryRunnable);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(8), dp(28), 0);

        TextView instructions = new TextView(this);
        instructions.setText(R.string.settings_instructions);
        instructions.setTextSize(16);
        instructions.setPadding(0, 0, 0, dp(14));
        content.addView(instructions);

        EditText urlInput = new EditText(this);
        urlInput.setHint(R.string.url_hint);
        urlInput.setSingleLine(true);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(getConfiguredUrl());
        urlInput.setSelectAllOnFocus(true);
        content.addView(urlInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(content)
                .setPositiveButton(R.string.save_and_open, null)
                .setNegativeButton(allowCancel ? android.R.string.cancel : R.string.exit_app, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                try {
                    String normalizedUrl = DisplayUrl.normalize(urlInput.getText().toString());
                    preferences.edit().putString(KEY_DISPLAY_URL, normalizedUrl).apply();
                    dialog.dismiss();
                    enterImmersiveMode();
                    loadConfiguredUrl();
                } catch (IllegalArgumentException exception) {
                    urlInput.setError(exception.getMessage());
                    urlInput.requestFocus();
                }
            });

            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(button -> {
                dialog.dismiss();
                if (allowCancel) {
                    enterImmersiveMode();
                } else {
                    finish();
                }
            });
        });

        dialog.setOnDismissListener(ignored -> enterImmersiveMode());
        dialog.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showSettingsDialog(true);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                return true;
            }

            long now = System.currentTimeMillis();
            if (now - lastBackPressAt <= BACK_PRESS_WINDOW_MS) {
                lastBackPressAt = 0;
                showSettingsDialog(true);
            } else {
                lastBackPressAt = now;
                Toast.makeText(this, R.string.back_again_for_settings, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        enterImmersiveMode();
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            root.removeView(webView);
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
