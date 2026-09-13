package gr.kostas.brillianceai;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
    private static final int FILE_CHOOSER_REQUEST = 4401;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private ApiKeyVault keyVault;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        keyVault = new ApiKeyVault(this);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setTextZoom(100);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception error) {
                        Toast.makeText(MainActivity.this, "Δεν βρέθηκε πρόγραμμα περιήγησης.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent;
                try {
                    intent = params.createIntent();
                } catch (Exception error) {
                    intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Δεν βρέθηκε εφαρμογή αρχείων.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(results);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private final class NativeBridge {
        @JavascriptInterface
        public boolean hasApiKey() {
            return keyVault.hasKey();
        }

        @JavascriptInterface
        public String saveApiKey(String apiKey) {
            String clean = apiKey == null ? "" : apiKey.trim();
            if (clean.length() < 20) return "Το κλειδί φαίνεται πολύ μικρό.";
            try {
                keyVault.save(clean);
                return "OK";
            } catch (Exception error) {
                return "Δεν αποθηκεύτηκε το κλειδί.";
            }
        }

        @JavascriptInterface
        public void clearApiKey() {
            keyVault.clear();
        }

        @JavascriptInterface
        public void generate(String requestId, String model, String jsonPayload) {
            new Thread(() -> callGemini(requestId, model, jsonPayload)).start();
        }

        @JavascriptInterface
        public void openAppSettings() {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void callGemini(String requestId, String model, String jsonPayload) {
        String apiKey = keyVault.read();
        if (apiKey.isEmpty()) {
            sendResult(requestId, 401, "{\"error\":{\"message\":\"Δεν έχει αποθηκευτεί κλειδί API.\"}}");
            return;
        }
        if (model == null || !model.matches("[A-Za-z0-9._-]{3,80}")) {
            sendResult(requestId, 400, "{\"error\":{\"message\":\"Μη έγκυρο μοντέλο.\"}}");
            return;
        }

        HttpURLConnection connection = null;
        try {
            new JSONObject(jsonPayload);
            URL endpoint = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");
            connection = (HttpURLConnection) endpoint.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("x-goog-api-key", apiKey);
            byte[] body = jsonPayload.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            sendResult(requestId, status, readStream(stream));
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Σφάλμα σύνδεσης." : error.getMessage();
            sendResult(requestId, 0, "{\"error\":{\"message\":" + JSONObject.quote(message) + "}}");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readStream(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private void sendResult(String requestId, int status, String body) {
        final String script = "window.KostasAI.onNativeResponse(" + JSONObject.quote(requestId)
                + "," + status + "," + JSONObject.quote(body == null ? "" : body) + ");";
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
}
