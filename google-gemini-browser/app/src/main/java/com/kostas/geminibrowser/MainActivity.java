package com.kostas.geminibrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class MainActivity extends Activity {
    private static final String PREFS = "k_browser_prefs_v3";
    private static final String KEY_ALIAS = "gemini_browser_keys_v3";
    private static final String PREF_KEY1 = "gemini_key_primary";
    private static final String PREF_KEY2 = "gemini_key_backup";
    private static final String PREF_TRANSLATE = "translate_target";
    private static final String PREF_AUTO_TRANSLATE = "auto_translate";

    private final String[] GEMINI_MODELS = new String[] {
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite"
    };

    private WebView webView;
    private EditText address;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean translationInProgress = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        configureWebView();
        loadHome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setPadding(dp(4), dp(4), dp(4), dp(2));

        Button google = button("G", 48);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(18f);
        address.setHint("Αναζήτηση Google ή https://");
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(52), 1f);
        Button go = button("➜", 58);
        row1.addView(google);
        row1.addView(address, ap);
        row1.addView(go);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(dp(4), dp(2), dp(4), dp(4));

        Button back = button("‹", 54);
        Button forward = button("›", 54);
        Button reload = button("↻", 54);
        Button translate = button("Μετάφραση", 0);
        Button gemini = button("Gemini ✦", 0);
        Button menu = button("⋮", 54);

        row2.addView(back);
        row2.addView(forward);
        row2.addView(reload);
        row2.addView(translate, new LinearLayout.LayoutParams(0, dp(52), 1f));
        row2.addView(gemini, new LinearLayout.LayoutParams(0, dp(52), 1.2f));
        row2.addView(menu);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);

        webView = new WebView(this);
        root.addView(row1);
        root.addView(row2);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        google.setOnClickListener(v -> loadHome());
        go.setOnClickListener(v -> navigate(address.getText().toString()));
        address.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                navigate(address.getText().toString());
                return true;
            }
            return false;
        });

        back.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        forward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        reload.setOnClickListener(v -> webView.reload());
        translate.setOnClickListener(v -> translateCurrentPage());
        gemini.setOnClickListener(v -> showGeminiPrompt());
        menu.setOnClickListener(v -> showMenu());
    }

    private Button button(String text, int widthDp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15f);
        b.setAllCaps(false);
        b.setMinWidth(widthDp > 0 ? dp(widthDp) : 0);
        b.setMinimumWidth(widthDp > 0 ? dp(widthDp) : 0);
        if (widthDp > 0) b.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(52)));
        return b;
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (u == null) return true;
                String scheme = u.getScheme();
                if ("https".equalsIgnoreCase(scheme)) return false;
                if ("http".equalsIgnoreCase(scheme)) {
                    view.loadUrl(u.toString().replaceFirst("(?i)^http://", "https://"));
                    return true;
                }
                return true;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (req == null || req.getUrl() == null) return null;
                String host = req.getUrl().getHost();
                if (host != null && isTracker(host)) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && !url.startsWith("data:")) address.setText(url);
                if (prefs.getBoolean(PREF_AUTO_TRANSLATE, false) &&
                        !translationInProgress && url != null && !isTranslateUrl(url) &&
                        !url.contains("google.com/search")) {
                    translateCurrentPage();
                }
            }

            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                toast("Μπλοκαρίστηκε μη ασφαλές HTTPS πιστοποιητικό");
            }
        });
    }

    private boolean isTracker(String host) {
        String h = host.toLowerCase();
        return h.contains("doubleclick.net") || h.contains("googlesyndication.com") ||
                h.contains("googleadservices.com") || h.contains("facebook.net") ||
                h.contains("scorecardresearch.com") || h.contains("app-measurement.com");
    }

    private void navigate(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return;
        if (s.matches("(?i)^https?://.*")) {
            loadSecure(s);
            return;
        }
        if (!s.contains(" ") && s.matches("(?i)^([a-z0-9-]+\\.)+[a-z]{2,}([/:?#].*)?$")) {
            loadSecure("https://" + s);
            return;
        }
        try {
            webView.loadUrl("https://www.google.com/search?hl=el&gl=gr&q=" +
                    URLEncoder.encode(s, "UTF-8"));
        } catch (Exception e) {
            webView.loadUrl("https://www.google.com/");
        }
    }

    private void loadSecure(String url) {
        String u = url.replaceFirst("(?i)^http://", "https://");
        if (!u.startsWith("https://")) {
            toast("Επιτρέπονται μόνο HTTPS σελίδες");
            return;
        }
        webView.loadUrl(u);
    }

    private void loadHome() {
        address.setText("https://www.google.com/");
        webView.loadUrl("https://www.google.com/");
    }

    private void showGeminiPrompt() {
        final EditText input = new EditText(this);
        input.setText(currentQuery());
        input.setHint("Γράψε την ερώτησή σου");
        input.setMinLines(3);
        input.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this)
                .setTitle("Gemini")
                .setView(input)
                .setNegativeButton("Άκυρο", null)
                .setPositiveButton("Ρώτησε", (d, w) -> {
                    String q = input.getText().toString().trim();
                    if (q.isEmpty()) return;
                    askGemini(q);
                })
                .show();
    }

    private String currentQuery() {
        try {
            String u = webView.getUrl();
            if (u != null) {
                Uri uri = Uri.parse(u);
                String q = uri.getQueryParameter("q");
                if (q != null && !q.trim().isEmpty()) return q;
            }
        } catch (Exception ignored) {}
        String a = address.getText().toString();
        if (a != null && !a.startsWith("http")) return a;
        return "";
    }

    private void askGemini(String prompt) {
        final AlertDialog wait = new AlertDialog.Builder(this)
                .setTitle("Gemini")
                .setMessage("Εκτελεί το ερώτημα…\nΑυτόματη εφεδρεία κλειδιού και μοντέλου ενεργή.")
                .setCancelable(false)
                .create();
        wait.show();

        io.execute(() -> {
            GeminiResult r = runGeminiWithFallback(prompt);
            main.post(() -> {
                if (wait.isShowing()) wait.dismiss();
                if (r.ok) showAnswer(prompt, r.text, r.model, r.keySlot);
                else new AlertDialog.Builder(this)
                        .setTitle("Gemini API")
                        .setMessage(r.text)
                        .setPositiveButton("Ρυθμίσεις κλειδιών", (d, w) -> showKeyDialog())
                        .setNegativeButton("Κλείσιμο", null)
                        .show();
            });
        });
    }

    private GeminiResult runGeminiWithFallback(String prompt) {
        List<String> keys = new ArrayList<>();
        String k1 = loadSecret(PREF_KEY1);
        String k2 = loadSecret(PREF_KEY2);
        if (!k1.isEmpty()) keys.add(k1);
        if (!k2.isEmpty() && !k2.equals(k1)) keys.add(k2);

        if (keys.isEmpty()) {
            return GeminiResult.fail("Δεν έχει αποθηκευτεί Gemini API key.\nΠάτησε ⋮ → «Gemini κλειδιά».");
        }

        StringBuilder errors = new StringBuilder();
        int keyIndex = 0;
        for (String key : keys) {
            keyIndex++;
            for (String model : GEMINI_MODELS) {
                int[] delays = new int[] {1000, 2000, 4000};
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        ApiResponse a = callGemini(key, model, prompt);
                        if (a.code >= 200 && a.code < 300) {
                            String text = extractGeminiText(a.body);
                            if (!text.isEmpty()) return GeminiResult.ok(text, model, keyIndex);
                            errors.append(model).append(": κενή απάντηση\n");
                            break;
                        }
                        errors.append("K").append(keyIndex).append(" ")
                                .append(model).append(" → ").append(a.code).append("\n");

                        if (isRetryable(a.code) && attempt < 2) {
                            try { Thread.sleep(delays[attempt]); } catch (InterruptedException ignored) {}
                            continue;
                        }
                        break;
                    } catch (Exception e) {
                        errors.append("K").append(keyIndex).append(" ")
                                .append(model).append(" → σύνδεση απέτυχε\n");
                        if (attempt < 2) {
                            try { Thread.sleep(delays[attempt]); } catch (InterruptedException ignored) {}
                        } else break;
                    }
                }
            }
        }

        String tail = errors.length() > 0 ? "\n\nΔοκιμές:\n" + errors : "";
        return GeminiResult.fail(
                "Δεν μπόρεσε να ολοκληρωθεί το ερώτημα. Η εφαρμογή δοκίμασε αυτόματα " +
                "και τα δύο κλειδιά, επαναλήψεις και εφεδρικά Gemini μοντέλα." + tail);
    }

    private boolean isRetryable(int code) {
        return code == 408 || code == 409 || code == 429 ||
                code == 500 || code == 502 || code == 503 || code == 504;
    }

    private ApiResponse callGemini(String key, String model, String prompt) throws Exception {
        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" +
                model + ":generateContent");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(90000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("x-goog-api-key", key);
        c.setDoOutput(true);

        JSONObject part = new JSONObject().put("text", prompt);
        JSONArray parts = new JSONArray().put(part);
        JSONObject content = new JSONObject().put("parts", parts);
        JSONArray contents = new JSONArray().put(content);
        JSONObject gen = new JSONObject()
                .put("temperature", 0.4)
                .put("maxOutputTokens", 2048);
        JSONObject body = new JSONObject()
                .put("contents", contents)
                .put("generationConfig", gen);

        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = c.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String response = readAll(in);
        c.disconnect();
        return new ApiResponse(code, response);
    }

    private String extractGeminiText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return "";
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return "";
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null) return "";
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                String t = parts.getJSONObject(i).optString("text", "");
                if (!t.isEmpty()) {
                    if (out.length() > 0) out.append("\n");
                    out.append(t);
                }
            }
            return out.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void showAnswer(String prompt, String answer, String model, int keySlot) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));

        TextView meta = new TextView(this);
        meta.setText("Μοντέλο: " + model + " • Κλειδί: " + keySlot);
        meta.setTextSize(12f);
        meta.setTextColor(Color.DKGRAY);

        TextView text = new TextView(this);
        text.setText(answer);
        text.setTextSize(17f);
        text.setTextColor(Color.BLACK);
        text.setTextIsSelectable(true);
        text.setPadding(0, dp(12), 0, dp(12));

        ScrollView sc = new ScrollView(this);
        sc.addView(text);
        box.addView(meta);
        box.addView(sc, new LinearLayout.LayoutParams(-1, dp(430)));

        new AlertDialog.Builder(this)
                .setTitle("Gemini — " + prompt)
                .setView(box)
                .setPositiveButton("OK", null)
                .show();
    }

    private void translateCurrentPage() {
        String url = webView.getUrl();
        if (url == null || !url.startsWith("https://") || isTranslateUrl(url)) {
            toast("Δεν υπάρχει σελίδα για μετάφραση");
            return;
        }
        String target = prefs.getString(PREF_TRANSLATE, "el");
        try {
            translationInProgress = true;
            String t = "https://translate.google.com/translate?sl=auto&tl=" + target +
                    "&u=" + URLEncoder.encode(url, "UTF-8");
            webView.loadUrl(t);
            main.postDelayed(() -> translationInProgress = false, 2500);
        } catch (Exception e) {
            translationInProgress = false;
            toast("Η μετάφραση απέτυχε");
        }
    }

    private boolean isTranslateUrl(String u) {
        try {
            String h = Uri.parse(u).getHost();
            return h != null && h.contains("translate.google.");
        } catch (Exception e) { return false; }
    }

    private void showMenu() {
        String auto = prefs.getBoolean(PREF_AUTO_TRANSLATE, false) ? "ON" : "OFF";
        String[] items = new String[] {
                "Gemini κλειδιά",
                "Γλώσσα μετάφρασης",
                "Αυτόματη μετάφραση: " + auto,
                "Καθαρισμός browser",
                "Πληροφορίες"
        };
        new AlertDialog.Builder(this)
                .setTitle("Ρυθμίσεις")
                .setItems(items, (d, which) -> {
                    if (which == 0) showKeyDialog();
                    else if (which == 1) showLanguageDialog();
                    else if (which == 2) {
                        boolean n = !prefs.getBoolean(PREF_AUTO_TRANSLATE, false);
                        prefs.edit().putBoolean(PREF_AUTO_TRANSLATE, n).apply();
                        toast("Αυτόματη μετάφραση: " + (n ? "ON" : "OFF"));
                    } else if (which == 3) clearBrowser();
                    else showInfo();
                }).show();
    }

    private void showKeyDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), 0);

        TextView note = new TextView(this);
        note.setText("Βάλε κύριο και προαιρετικά εφεδρικό Gemini API key. " +
                "Αποθηκεύονται κρυπτογραφημένα στη συσκευή. Σε σφάλμα γίνεται αυτόματη εναλλαγή.");
        note.setTextSize(14f);

        EditText k1 = new EditText(this);
        k1.setHint(loadSecret(PREF_KEY1).isEmpty() ? "Κύριο Gemini key" : "Κύριο key — ήδη αποθηκευμένο");
        k1.setSingleLine(true);
        k1.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText k2 = new EditText(this);
        k2.setHint(loadSecret(PREF_KEY2).isEmpty() ? "Εφεδρικό Gemini key (προαιρετικό)" : "Εφεδρικό key — ήδη αποθηκευμένο");
        k2.setSingleLine(true);
        k2.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        box.addView(note);
        box.addView(k1);
        box.addView(k2);

        new AlertDialog.Builder(this)
                .setTitle("Gemini Free key")
                .setView(box)
                .setNeutralButton("Διαγραφή", (d, w) -> {
                    prefs.edit().remove(PREF_KEY1).remove(PREF_KEY2).apply();
                    toast("Διαγράφηκαν τα αποθηκευμένα κλειδιά");
                })
                .setNegativeButton("Άκυρο", null)
                .setPositiveButton("Αποθήκευση", (d, w) -> {
                    String a = k1.getText().toString().trim();
                    String b = k2.getText().toString().trim();
                    if (!a.isEmpty()) saveSecret(PREF_KEY1, a);
                    if (!b.isEmpty()) saveSecret(PREF_KEY2, b);
                    toast("Τα κλειδιά αποθηκεύτηκαν");
                })
                .show();
    }

    private void showLanguageDialog() {
        final String[] names = {"Ελληνικά","English","Deutsch","Français","Español","Italiano","Português","Türkçe","Русский","日本語","한국어"};
        final String[] codes = {"el","en","de","fr","es","it","pt","tr","ru","ja","ko"};
        String current = prefs.getString(PREF_TRANSLATE, "el");
        int selected = 0;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(current)) selected = i;
        new AlertDialog.Builder(this)
                .setTitle("Γλώσσα στόχος")
                .setSingleChoiceItems(names, selected, (d, which) -> {
                    prefs.edit().putString(PREF_TRANSLATE, codes[which]).apply();
                    d.dismiss();
                    toast("Γλώσσα: " + names[which]);
                }).show();
    }

    private void clearBrowser() {
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        WebStorage.getInstance().deleteAllData();
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        toast("Καθαρίστηκαν cookies, cache και ιστορικό");
        loadHome();
    }

    private void showInfo() {
        new AlertDialog.Builder(this)
                .setTitle("K Browser • Google + Gemini")
                .setMessage("Android 10+\nHTTPS-only • Google Search • Μετάφραση • Gemini\n\n" +
                        "Gemini fallback: 2 κλειδιά + 4 μοντέλα + αυτόματες επαναλήψεις για 408/409/429/5xx. " +
                        "Αν εξαντληθεί ή μπλοκαριστεί κάθε διαθέσιμο key/model, η εφαρμογή εμφανίζει καθαρό σφάλμα αντί να κολλάει.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void saveSecret(String prefName, String value) {
        try {
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(encrypted, 0, all, iv.length, encrypted.length);
            prefs.edit().putString(prefName, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
        } catch (Exception e) {
            toast("Αποθήκευση κλειδιού απέτυχε");
        }
    }

    private String loadSecret(String prefName) {
        String stored = prefs.getString(prefName, "");
        if (stored == null || stored.isEmpty()) return "";
        try {
            byte[] all = Base64.decode(stored, Base64.NO_WRAP);
            if (all.length < 13) return "";
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return kg.generateKey();
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private static class ApiResponse {
        final int code;
        final String body;
        ApiResponse(int c, String b) { code = c; body = b; }
    }

    private static class GeminiResult {
        final boolean ok;
        final String text;
        final String model;
        final int keySlot;
        GeminiResult(boolean o, String t, String m, int k) {
            ok = o; text = t; model = m; keySlot = k;
        }
        static GeminiResult ok(String t, String m, int k) {
            return new GeminiResult(true, t, m, k);
        }
        static GeminiResult fail(String t) {
            return new GeminiResult(false, t, "", 0);
        }
    }
}
