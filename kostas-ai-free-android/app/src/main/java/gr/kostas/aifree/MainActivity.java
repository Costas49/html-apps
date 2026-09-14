package gr.kostas.aifree;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
 private WebView web;
 private ValueCallback<Uri[]> chooser;
 private String pendingText;
 private static final int OPEN=100,SAVE=101;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  web=new WebView(this);
  setContentView(web);
  WebSettings s=web.getSettings();
  s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
  s.setAllowFileAccess(false);s.setAllowContentAccess(true);
  s.setAllowFileAccessFromFileURLs(false);s.setAllowUniversalAccessFromFileURLs(false);
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  s.setTextZoom(100);
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
    Uri u=request.getUrl();
    WebResourceResponse asset=loader.shouldInterceptRequest(u);
    if(asset!=null)return asset;
    if("https".equals(u.getScheme())&&"generativelanguage.googleapis.com".equals(u.getHost()))return null;
    return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new java.io.ByteArrayInputStream(new byte[0]));
   }
   private boolean openExternal(Uri u){
    if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&"/assets/index.html".equals(u.getPath()))return false;
    if("https".equals(u.getScheme())){
     try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){notice("Δεν βρέθηκε πρόγραμμα περιήγησης.");}
    }
    return true;
   }
   @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !r.isForMainFrame()||openExternal(r.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return openExternal(Uri.parse(url));}
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> result,FileChooserParams params){
    if(chooser!=null)chooser.onReceiveValue(null);
    chooser=result;
    Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain");
    try{startActivityForResult(intent,OPEN);}catch(Exception e){chooser.onReceiveValue(null);chooser=null;notice("Δεν βρέθηκε εφαρμογή αρχείων.");}
    return true;
   }
  });
  web.addJavascriptInterface(new TextExport(),"AndroidExport");
  web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
 }
 public final class TextExport {
  @JavascriptInterface public void saveText(String text){
   if(text==null||text.length()>200000)return;
   runOnUiThread(()->{
    if(pendingText!=null){notice("Ολοκλήρωσε πρώτα την προηγούμενη αποθήκευση.");return;}
    pendingText=text;
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"Kostas-AI-answer.txt");
    try{startActivityForResult(intent,SAVE);}catch(Exception e){pendingText=null;notice("Δεν άνοιξε η αποθήκευση αρχείου.");}
   });
  }
 }
 private void notice(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==OPEN&&chooser!=null){chooser.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);chooser=null;}
  if(request==SAVE){
   final String text=pendingText;pendingText=null;
   if(result!=RESULT_OK||data==null||data.getData()==null||text==null)return;
   final Uri uri=data.getData();
   new Thread(()->{
    try(OutputStream out=getContentResolver().openOutputStream(uri)){
     if(out==null)throw new java.io.IOException();
     out.write(("\ufeff"+text).getBytes(StandardCharsets.UTF_8));
     runOnUiThread(()->notice("Η απάντηση αποθηκεύτηκε."));
    }catch(Exception e){runOnUiThread(()->notice("Δεν αποθηκεύτηκε το αρχείο."));}
   }).start();
  }
 }
 @Override public void onBackPressed(){super.onBackPressed();}
 @Override protected void onDestroy(){
  if(chooser!=null){chooser.onReceiveValue(null);chooser=null;}
  if(web!=null){web.removeJavascriptInterface("AndroidExport");web.destroy();}
  super.onDestroy();
 }
}
