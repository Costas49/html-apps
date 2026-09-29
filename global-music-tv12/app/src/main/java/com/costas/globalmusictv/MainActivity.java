package com.costas.globalmusictv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5,7,11));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " KostasGlobalMusicTV/1.0");

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidTV");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception ignored) {
                    return false;
                }
            }
        });

        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                if (webView.canGoBack()) webView.goBack();
                else finish();
                return true;
            }
            return false;
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void openExternal(String url) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void httpGet(String url, String callbackId) {
            if (url == null || callbackId == null) return;
            if (!(url.startsWith("https://itunes.apple.com/") ||
                  url.startsWith("https://musicbrainz.org/") ||
                  url.startsWith("https://coverartarchive.org/"))) {
                sendCallback(callbackId, null, "Μη επιτρεπτός προορισμός δικτύου");
                return;
            }

            executor.execute(() -> {
                HttpURLConnection c = null;
                try {
                    c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(12000);
                    c.setReadTimeout(18000);
                    c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent",
                            "KostasGlobalMusicTV/1.0 (https://github.com/Costas49/html-apps)");
                    int status = c.getResponseCode();
                    InputStream in = status >= 200 && status < 400
                            ? c.getInputStream() : c.getErrorStream();
                    StringBuilder out = new StringBuilder();
                    if (in != null) {
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(in, StandardCharsets.UTF_8));
                        String line;
                        while ((line = br.readLine()) != null) out.append(line);
                        br.close();
                    }
                    if (status >= 200 && status < 300) {
                        sendCallback(callbackId, out.toString(), null);
                    } else {
                        sendCallback(callbackId, null, "HTTP " + status);
                    }
                } catch (Exception e) {
                    sendCallback(callbackId, null, e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    if (c != null) c.disconnect();
                }
            });
        }
    }

    private void sendCallback(String id, String body, String error) {
        if (webView == null) return;
        String js = "window.__nativeCallback(" +
                JSONObject.quote(id) + "," +
                (body == null ? "null" : JSONObject.quote(body)) + "," +
                (error == null ? "null" : JSONObject.quote(error)) + ");";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(js, null);
        });
    }
}
