package com.kostas.tvbuilder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;

public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(
                    "(function(){var s=document.createElement('script');s.src='https://appassets.androidplatform.net/assets/patch.js';s.async=false;s.onerror=function(){};document.body.appendChild(s);})();",
                    null
                );
                view.requestFocus();
            }
        });

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        webView.requestFocus();
    }

    private void sendTvAction(String action) {
        if (webView == null) return;
        final String js = "window.dispatchEvent(new CustomEvent('android-dpad-v23',{detail:'" + action + "'}));";
        webView.evaluateJavascript(js, null);
    }

    private String actionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return "left";
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return "right";
            case KeyEvent.KEYCODE_DPAD_UP:
                return "up";
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return "down";
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return "ok";
            default:
                return null;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        String action = actionForKey(event.getKeyCode());
        if (action != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                sendTvAction(action);
            }
            // Consume both DOWN and UP so the WebView cannot process the same
            // remote press a second time with a different focus/navigation path.
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
