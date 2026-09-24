package com.costas.apkbuilder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final int FILE_CHOOSER_REQUEST=1001;
  private WebView webView;
  private ValueCallback<Uri[]> fileCallback;
  private SecureVault githubVault;
  private SecureVault geminiVault;

  @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
  @Override
  protected void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);

    githubVault=new SecureVault(this,"ultra_github_secure","ultra_github_token");
    geminiVault=new SecureVault(this,"ultra_gemini_secure","ultra_gemini_key");
    migrateLegacyGithubToken();

    webView=new WebView(this);
    setContentView(webView);

    WebSettings s=webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setAllowFileAccessFromFileURLs(false);
    s.setAllowUniversalAccessFromFileURLs(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    s.setCacheMode(WebSettings.LOAD_DEFAULT);

    webView.setWebViewClient(new WebViewClient(){
      @Override
      public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
        Uri uri=request.getUrl();
        String scheme=uri.getScheme();
        if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)){
          try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}
          catch(Exception e){Toast.makeText(MainActivity.this,"Δεν βρέθηκε browser.",Toast.LENGTH_LONG).show();}
          return true;
        }
        return false;
      }
    });

    webView.setWebChromeClient(new WebChromeClient(){
      @Override
      public boolean onShowFileChooser(WebView view,ValueCallback<Uri[]> callback,FileChooserParams params){
        if(fileCallback!=null)fileCallback.onReceiveValue(null);
        fileCallback=callback;
        try{
          Intent intent=params.createIntent();
          startActivityForResult(intent,FILE_CHOOSER_REQUEST);
          return true;
        }catch(Exception e){
          fileCallback=null;
          return false;
        }
      }
    });

    webView.addJavascriptInterface(new VaultBridge(),"AndroidVault");
    webView.loadUrl("file:///android_asset/index.html");
  }

  private void migrateLegacyGithubToken(){
    try{
      if(githubVault.hasValue())return;
      SharedPreferences old=getSharedPreferences("apk_maker",MODE_PRIVATE);
      String token=old.getString("github_token","");
      if(token!=null&&!token.trim().isEmpty()){
        githubVault.save(token.trim());
        old.edit().remove("github_token").apply();
      }
    }catch(Exception ignored){}
  }

  @Override
  protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);
    if(requestCode==FILE_CHOOSER_REQUEST&&fileCallback!=null){
      Uri[] results=WebChromeClient.FileChooserParams.parseResult(resultCode,data);
      fileCallback.onReceiveValue(results);
      fileCallback=null;
    }
  }

  @Override
  public void onBackPressed(){
    if(webView!=null&&webView.canGoBack())webView.goBack();
    else super.onBackPressed();
  }

  public final class VaultBridge {
    @JavascriptInterface
    public void saveToken(String token){
      try{githubVault.save(token==null?"":token.trim());}
      catch(Exception e){throw new RuntimeException("Δεν αποθηκεύτηκε το GitHub token.");}
    }

    @JavascriptInterface
    public String loadToken(){return githubVault.read();}

    @JavascriptInterface
    public void clearToken(){githubVault.clear();}

    @JavascriptInterface
    public boolean hasGeminiKey(){return geminiVault.hasValue();}

    @JavascriptInterface
    public String saveGeminiKey(String key){
      String clean=key==null?"":key.trim();
      if(clean.length()<20)return "Το Gemini key φαίνεται πολύ μικρό.";
      try{
        geminiVault.save(clean);
        return "OK";
      }catch(Exception e){
        return "Δεν αποθηκεύτηκε το Gemini key.";
      }
    }

    @JavascriptInterface
    public void clearGeminiKey(){geminiVault.clear();}

    @JavascriptInterface
    public void generate(String requestId,String model,String jsonPayload){
      new Thread(()->callGemini(requestId,model,jsonPayload)).start();
    }

    @JavascriptInterface
    public void openExternal(String url){
      try{
        Uri uri=Uri.parse(url);
        String scheme=uri.getScheme();
        if("http".equalsIgnoreCase(scheme)||"https".equalsIgnoreCase(scheme)){
          startActivity(new Intent(Intent.ACTION_VIEW,uri));
        }
      }catch(Exception ignored){}
    }
  }

  private void callGemini(String requestId,String model,String jsonPayload){
    String apiKey=geminiVault.read();
    if(apiKey.isEmpty()){
      sendAiResult(requestId,401,"{\"error\":{\"message\":\"Δεν έχει αποθηκευτεί Gemini API key.\"}}");
      return;
    }
    if(model==null||!model.matches("[A-Za-z0-9._-]{3,80}")){
      sendAiResult(requestId,400,"{\"error\":{\"message\":\"Μη έγκυρο μοντέλο.\"}}");
      return;
    }

    HttpURLConnection connection=null;
    try{
      new JSONObject(jsonPayload);
      URL endpoint=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
      connection=(HttpURLConnection)endpoint.openConnection();
      connection.setRequestMethod("POST");
      connection.setConnectTimeout(30000);
      connection.setReadTimeout(140000);
      connection.setDoOutput(true);
      connection.setRequestProperty("Content-Type","application/json; charset=UTF-8");
      connection.setRequestProperty("x-goog-api-key",apiKey);
      byte[] body=jsonPayload.getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(body.length);
      try(OutputStream output=connection.getOutputStream()){output.write(body);}
      int status=connection.getResponseCode();
      InputStream stream=status>=200&&status<300?connection.getInputStream():connection.getErrorStream();
      sendAiResult(requestId,status,readStream(stream));
    }catch(Exception e){
      String message=e.getMessage()==null?"Σφάλμα σύνδεσης.":e.getMessage();
      sendAiResult(requestId,0,"{\"error\":{\"message\":"+JSONObject.quote(message)+"}}");
    }finally{
      if(connection!=null)connection.disconnect();
    }
  }

  private String readStream(InputStream input)throws Exception{
    if(input==null)return "";
    StringBuilder b=new StringBuilder();
    try(BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){
      String line;
      while((line=r.readLine())!=null)b.append(line);
    }
    return b.toString();
  }

  private void sendAiResult(String requestId,int status,String body){
    String script="window.UltraAI.onNativeResponse("+JSONObject.quote(requestId)+","+status+","+JSONObject.quote(body==null?"":body)+");";
    webView.post(()->webView.evaluateJavascript(script,null));
  }
}
