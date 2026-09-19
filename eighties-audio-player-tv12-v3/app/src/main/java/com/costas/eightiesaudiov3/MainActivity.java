package com.costas.eightiesaudiov3;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
  private WebView webView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    webView = new WebView(this);
    setContentView(webView);

    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

    webView.setWebChromeClient(new WebChromeClient());
    webView.setWebViewClient(new WebViewClient());
    webView.addJavascriptInterface(new Bridge(), "AndroidTV");

    webView.setOnKeyListener((v,keyCode,event)->{
      if(event.getAction()==KeyEvent.ACTION_DOWN && keyCode==KeyEvent.KEYCODE_BACK){
        if(webView.canGoBack()) webView.goBack(); else finish();
        return true;
      }
      return false;
    });

    webView.loadUrl("file:///android_asset/index.html");
  }

  public class Bridge {
    @JavascriptInterface
    public void openExternal(String url) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
      catch(Exception ignored) {}
    }
  }
}
