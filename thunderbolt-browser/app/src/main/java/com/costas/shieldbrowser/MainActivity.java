package com.costas.shieldbrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.net.IDN;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView webView;
    private EditText address;
    private ProgressBar progress;
    private TrackerBlocker blocker;
    private boolean greekSearch = true;

    private static final Map<String, String> PRIVACY_HEADERS = new HashMap<>();
    static {
        PRIVACY_HEADERS.put("DNT", "1");
        PRIVACY_HEADERS.put("Sec-GPC", "1");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(false);
        blocker = new TrackerBlocker(this);
        buildUi();
        hardenWebView();
        clearPrivateData(false);

        Uri incoming = getIntent() != null ? getIntent().getData() : null;
        if (incoming != null && "https".equalsIgnoreCase(incoming.getScheme())) {
            loadHttps(cleanTrackingParameters(incoming).toString());
        } else {
            loadHome();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(3);
        toolbar.setPadding(pad, pad, pad, pad);

        Button back = button("‹");
        Button home = button("⌂");
        Button scope = button("GR");
        Button shield = button("🛡");
        Button clear = button("✕");
        Button chrome = button("CH");

        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Thunderbolt.gr — αναζήτηση ή https://");
        address.setTextSize(15f);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

        toolbar.addView(back);
        toolbar.addView(home);
        toolbar.addView(address, addressParams);
        toolbar.addView(scope);
        toolbar.addView(shield);
        toolbar.addView(clear);
        toolbar.addView(chrome);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);

        webView = new WebView(this);
        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);

        root.addView(toolbar);
        root.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));
        root.addView(webView, webParams);
        setContentView(root);

        back.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        home.setOnClickListener(v -> loadHome());
        scope.setOnClickListener(v -> {
            greekSearch = !greekSearch;
            scope.setText(greekSearch ? "GR" : "COM");
            address.setHint(greekSearch
                    ? "Thunderbolt.gr — αναζήτηση ή https://"
                    : "Thunderbolt.com — search or https://");
            loadHome();
        });
        shield.setOnClickListener(v -> showSecurityReport());
        clear.setOnClickListener(v -> {
            clearPrivateData(true);
            loadHome();
        });
        chrome.setOnClickListener(v -> openCurrentInChrome());

        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14f);
        b.setMinWidth(dp(42));
        b.setMinimumWidth(dp(42));
        b.setAllCaps(false);
        return b;
    }

    private void hardenWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        try {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return blockedResponseIfNeeded(request);
                }
            });
        } catch (Throwable ignored) { }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();
                toast("Μπλοκαρίστηκε αίτημα άδειας από ιστοσελίδα");
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog,
                                          boolean isUserGesture, android.os.Message resultMsg) {
                toast("Οι νέες καρτέλες είναι απενεργοποιημένες");
                return false;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = cleanTrackingParameters(request.getUrl());
                String scheme = uri.getScheme();
                if (scheme == null) return true;
                if ("https".equalsIgnoreCase(scheme)) {
                    if (RiskEngine.shouldHardBlock(uri)) {
                        showBlocked(uri.toString(), "ύποπτη μορφή διεύθυνσης");
                        return true;
                    }
                    String original = request.getUrl().toString();
                    if (!uri.toString().equals(original)) {
                        view.loadUrl(uri.toString(), PRIVACY_HEADERS);
                        return true;
                    }
                    return false;
                }
                if ("http".equalsIgnoreCase(scheme)) {
                    loadHttps(uri.toString());
                    return true;
                }
                if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                    return true;
                }
                showBlocked(uri.toString(), "μη ασφαλές πρωτόκολλο " + scheme);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return blockedResponseIfNeeded(request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                address.setText(url != null && url.startsWith("data:") ? "Thunderbolt" : url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && !url.startsWith("data:")) address.setText(url);
                injectPrivacyGuards(view);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showBlocked(error != null ? String.valueOf(error.getUrl()) : "HTTPS",
                        "σφάλμα πιστοποιητικού HTTPS");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) toast("Η ασφαλής σύνδεση απέτυχε");
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request,
                                          int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
                String url = request != null ? request.getUrl().toString() : "ιστοσελίδα";
                showBlocked(url, "εντοπίστηκε από το Safe Browsing ως επικίνδυνο περιεχόμενο");
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                new AlertDialog.Builder(this)
                        .setTitle("Λήψη μπλοκαρισμένη")
                        .setMessage("Το Thunderbolt μπλοκάρει λήψεις αρχείων από ιστοσελίδες για προστασία από κακόβουλα APK, trojan και ransomware.\n\n" + url)
                        .setPositiveButton("OK", null)
                        .show());
    }

    private WebResourceResponse blockedResponseIfNeeded(WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        Uri uri = request.getUrl();
        String scheme = uri.getScheme();
        if (scheme != null && !"https".equalsIgnoreCase(scheme) &&
                !"data".equalsIgnoreCase(scheme) && !"blob".equalsIgnoreCase(scheme)) {
            return emptyResponse();
        }
        if (blocker.shouldBlock(uri.getHost())) return emptyResponse();
        return null;
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private void injectPrivacyGuards(WebView view) {
        String js = "(function(){try{" +
                "navigator.sendBeacon=function(){return false;};" +
                "window.RTCPeerConnection=undefined;window.webkitRTCPeerConnection=undefined;" +
                "if(window.Notification){Notification.requestPermission=function(){return Promise.resolve('denied');};}" +
                "}catch(e){}})();";
        try { view.evaluateJavascript(js, null); } catch (Exception ignored) { }
    }

    private void navigate(String raw) {
        String input = raw == null ? "" : raw.trim();
        if (input.isEmpty()) return;

        if (input.startsWith("http://") || input.startsWith("https://")) {
            loadHttps(input);
            return;
        }
        if (looksLikeHost(input)) {
            loadHttps("https://" + input);
            return;
        }

        String q;
        try { q = URLEncoder.encode(input, "UTF-8"); }
        catch (Exception e) { q = input.replace(" ", "+"); }

        String search = greekSearch
                ? "https://www.google.com/search?hl=el&gl=gr&pws=0&filter=1&q=" + q
                : "https://www.google.com/search?hl=en&pws=0&filter=1&q=" + q;
        webView.loadUrl(search, PRIVACY_HEADERS);
    }

    private boolean looksLikeHost(String input) {
        if (input.contains(" ")) return false;
        String host = input;
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        host = host.replaceAll(":\\d+$", "");
        try {
            host = IDN.toASCII(host);
            return host.contains(".") && !host.startsWith(".") && !host.endsWith(".");
        } catch (Exception e) { return false; }
    }

    private void loadHttps(String url) {
        String secure = url.replaceFirst("(?i)^http://", "https://");
        Uri uri = cleanTrackingParameters(Uri.parse(secure));
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            toast("Επιτρέπεται μόνο HTTPS");
            return;
        }
        if (RiskEngine.shouldHardBlock(uri)) {
            showBlocked(uri.toString(), "ύποπτη μορφή διεύθυνσης");
            return;
        }
        webView.loadUrl(uri.toString(), PRIVACY_HEADERS);
    }

    private Uri cleanTrackingParameters(Uri uri) {
        if (uri == null || uri.getQuery() == null) return uri;
        try {
            Uri.Builder b = uri.buildUpon().clearQuery();
            for (String name : uri.getQueryParameterNames()) {
                if (TrackerBlocker.isTrackingParameter(name)) continue;
                for (String value : uri.getQueryParameters(name)) b.appendQueryParameter(name, value);
            }
            return b.build();
        } catch (Exception e) { return uri; }
    }

    private void loadHome() {
        address.setText("");
        String brand = greekSearch ? "Thunderbolt.gr" : "Thunderbolt.com";
        String subtitle = greekSearch ? "Ελληνικός ιστός • HTTPS Shield" : "Global web • HTTPS Shield";
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;background:#101419;color:#fff;text-align:center;padding:14vh 20px}" +
                "h1{font-size:38px;margin:0}.bolt{font-size:58px}.s{opacity:.76;margin-top:10px}.box{margin:35px auto 0;max-width:520px;padding:16px;border:1px solid #3b4652;border-radius:16px;background:#171d24}</style>" +
                "</head><body><div class='bolt'>⚡</div><h1>" + brand + "</h1><div class='s'>" + subtitle + "</div>" +
                "<div class='box'>🛡 Safe Browsing • Anti-tracker • No tabs • No HTTP<br><small>Γράψε την αναζήτησή σου επάνω.</small></div></body></html>";
        webView.loadDataWithBaseURL("https://thunderbolt.local/", html, "text/html", "UTF-8", null);
    }

    private void showSecurityReport() {
        String url = webView.getUrl();
        if (url == null || url.startsWith("data:") || url.contains("thunderbolt.local")) {
            toast("Thunderbolt Shield ενεργό");
            return;
        }
        Uri uri = Uri.parse(url);
        RiskEngine.Report r = RiskEngine.analyze(uri);
        new AlertDialog.Builder(this)
                .setTitle("Thunderbolt AI Shield")
                .setMessage("Τοπικός έλεγχος διεύθυνσης\n\n" + r.message +
                        "\n\nHTTPS: " + ("https".equalsIgnoreCase(uri.getScheme()) ? "ΝΑΙ" : "ΟΧΙ") +
                        "\nTrackers μπλοκαρισμένοι: " + blocker.getBlockedCount())
                .setPositiveButton("OK", null)
                .show();
    }

    private void showBlocked(String url, String reason) {
        new AlertDialog.Builder(this)
                .setTitle("🛡 Thunderbolt Shield")
                .setMessage("Μπλοκαρίστηκε για ασφάλεια: " + reason + "\n\n" + url)
                .setPositiveButton("OK", null)
                .show();
    }

    private void openCurrentInChrome() {
        String url = webView.getUrl();
        if (url == null || !url.startsWith("https://")) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage("com.android.chrome");
        try { startActivity(intent); }
        catch (ActivityNotFoundException e) {
            intent.setPackage(null);
            try { startActivity(intent); } catch (Exception ignored) { }
        }
    }

    private void clearPrivateData(boolean notify) {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().removeSessionCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        if (webView != null) {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
        }
        if (notify) toast("Καθαρίστηκαν cookies, cache, storage και ιστορικό συνεδρίας");
    }

    @Override
    protected void onDestroy() {
        clearPrivateData(false);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
