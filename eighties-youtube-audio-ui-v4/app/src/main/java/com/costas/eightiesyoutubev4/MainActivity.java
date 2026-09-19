package com.costas.eightiesyoutubev4;

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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    s.setUserAgentString(s.getUserAgentString() + " Costas80sPlayer/4.0");

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

    try {
      InputStream in = getAssets().open("index.html");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int n;
      while((n=in.read(buf))!=-1) out.write(buf,0,n);
      in.close();
      String html = out.toString(StandardCharsets.UTF_8.name());
      // HTTPS base URL gives embedded YouTube a real web origin/referer instead of file://.
      webView.loadDataWithBaseURL("https://costas-player.local/", html, "text/html", "UTF-8", null);
    } catch(Exception e) {
      webView.loadUrl("file:///android_asset/index.html");
    }
  }

  public class Bridge {
    @JavascriptInterface
    public void openExternal(String url) {
      try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
      catch(Exception ignored) {}
    }
  }
}
