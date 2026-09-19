package com.costas.apkbuilder;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
  private static final int FILE_CHOOSER_REQUEST = 1001;
  private WebView webView;
  private ValueCallback<Uri[]> fileCallback;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    webView = new WebView(this);
    setContentView(webView);

    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

    webView.setWebViewClient(new WebViewClient());
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                       FileChooserParams fileChooserParams) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = filePathCallback;
        try {
          Intent intent = fileChooserParams.createIntent();
          startActivityForResult(intent, FILE_CHOOSER_REQUEST);
          return true;
        } catch (Exception e) {
          fileCallback = null;
          return false;
        }
      }
    });

    webView.addJavascriptInterface(new VaultBridge(), "AndroidVault");
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
      Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
      fileCallback.onReceiveValue(results);
      fileCallback = null;
    }
  }

  public class VaultBridge {
    @JavascriptInterface
    public void saveToken(String token) {
      getSharedPreferences("apk_maker", MODE_PRIVATE)
        .edit().putString("github_token", token == null ? "" : token.trim()).apply();
    }

    @JavascriptInterface
    public String loadToken() {
      return getSharedPreferences("apk_maker", MODE_PRIVATE)
        .getString("github_token", "");
    }

    @JavascriptInterface
    public void clearToken() {
      getSharedPreferences("apk_maker", MODE_PRIVATE)
        .edit().remove("github_token").apply();
    }

    @JavascriptInterface
    public void openExternal(String url) {
      try {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
      } catch (Exception ignored) {}
    }
  }
}
