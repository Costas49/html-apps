package com.costas.builder;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;

    private Uri outputUri;
    private OutputStream outputStream;
    private boolean outputPending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " PhotoMotionAI/1.0");

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidTV");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams fileChooserParams) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;

                Intent intent;
                try {
                    intent = fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Δεν βρέθηκε εφαρμογή αρχείων.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    view.loadUrl(uri.toString());
                    return true;
                }
                return false;
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private synchronized void closeOutputStream() {
        try {
            if (outputStream != null) {
                outputStream.flush();
                outputStream.close();
            }
        } catch (Exception ignored) {
        }
        outputStream = null;
    }

    private synchronized void clearOutputState() {
        outputUri = null;
        outputPending = false;
        outputStream = null;
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void openExternal(String url) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void download(String url, String filename) {
            try {
                String safeName = (filename == null || filename.trim().isEmpty()) ? "download.bin" : filename;
                DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                req.setTitle(safeName);
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeName);
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                dm.enqueue(req);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public synchronized boolean beginFile(String filename, String mimeType) {
            try {
                cancelFile();

                String safeName = (filename == null || filename.trim().isEmpty())
                        ? "PhotoMotion.webm"
                        : filename.replace("/", "_").replace("\\", "_");

                String safeMime = (mimeType == null || mimeType.trim().isEmpty())
                        ? "video/webm"
                        : mimeType;

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                values.put(MediaStore.Downloads.MIME_TYPE, safeMime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PhotoMotionAI");
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                outputUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) {
                    clearOutputState();
                    return false;
                }

                outputStream = getContentResolver().openOutputStream(outputUri, "w");
                if (outputStream == null) {
                    getContentResolver().delete(outputUri, null, null);
                    clearOutputState();
                    return false;
                }

                outputPending = true;
                return true;
            } catch (Exception e) {
                cancelFile();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean appendBase64Chunk(String base64Chunk) {
            if (!outputPending || outputStream == null || base64Chunk == null) return false;
            try {
                byte[] bytes = Base64.decode(base64Chunk, Base64.DEFAULT);
                outputStream.write(bytes);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean finishFile() {
            if (!outputPending || outputUri == null) return false;
            try {
                closeOutputStream();

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(outputUri, values, null, null);

                final Uri completed = outputUri;
                clearOutputState();

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "Το βίντεο αποθηκεύτηκε στα Downloads/PhotoMotionAI",
                                Toast.LENGTH_LONG
                        ).show()
                );
                return completed != null;
            } catch (Exception e) {
                cancelFile();
                return false;
            }
        }

        @JavascriptInterface
        public synchronized void cancelFile() {
            try {
                closeOutputStream();
                if (outputUri != null) {
                    getContentResolver().delete(outputUri, null, null);
                }
            } catch (Exception ignored) {
            } finally {
                clearOutputState();
            }
        }
    }

    @Override
    protected void onDestroy() {
        try {
            new AndroidBridge().cancelFile();
        } catch (Exception ignored) {
        }

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.destroy();
        }

        super.onDestroy();
    }
}
