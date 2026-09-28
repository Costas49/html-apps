package com.kostas.geminibrowser;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import android.media.MediaPlayer;

import org.json.*;

import java.io.*;
import java.net.URL;\nimport java.net.URLEncoder;\nimport javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.SecretKey;

public class MainActivity extends Activity {
    private WebView web;
    private EditText address;
    private ProgressBar progress;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private MediaPlayer mediaPlayer;

    private static final String PREFS = "k_browser_prefs";
    private static final String KEY_ALIAS = "gemini_api_key_aes_v1";
    private static final String MODEL_TEXT = "gemini-3.8-flash";
    private static final String MODEL_TTS = "gemini-3.8-flash-lite-tts";

    private final Set<String> blocked = new HashSet<>(Arrays.asList(
        "doubleclick.net","googlesyndication.com","googleadservices.com",
        "adservice.google.com","adsystem.com","scorecardresearch.com",
        "taboola.com","outbrain.com","criteo.com","criteo.net",
        "amazon-adsystem.com","adsrvr.org","rubiconproject.com",
        "pubmatic.com","openx.net","quantserve.com","hotjar.com",
        "segment.com","mixpanel.com","appsflyer.com","adjust.com"
    ));

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        configureWebView();
        goHome();
    }

    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,245,248));

        LinearLayout bar1 = new LinearLayout(this);
        bar1.setOrientation(LinearLayout.HORIZONTAL);
        bar1.setPadding(dp(6),dp(4),dp(6),dp(2));

        Button home = btn("G");
        home.setOnClickListener(v -> goHome());
        bar1.addView(home, new LinearLayout.LayoutParams(dp(48), dp(48)));

        address = new EditText(this);
        address.setSingleLine(true);
        address.setHint("Αναζήτηση ή https://...");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setTextSize(15);
        address.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(48), 1f);
        bar1.addView(address, ap);

        Button go = btn("➜");
        go.setOnClickListener(v -> loadInput(address.getText().toString()));
        bar1.addView(go, new LinearLayout.LayoutParams(dp(52), dp(48)));
        address.setOnEditorActionListener((v, actionId, event) -> { loadInput(address.getText().toString()); return true; });

        LinearLayout bar2 = new LinearLayout(this);
        bar2.setOrientation(LinearLayout.HORIZONTAL);
        bar2.setPadding(dp(6),0,dp(6),dp(2));

        Button back = btn("‹");
        Button forward = btn("›");
        Button reload = btn("↻");
        Button translate = btn("Μετάφραση");
        Button gemini = btn("Gemini ✦");
        Button more = btn("⋮");

        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        forward.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        reload.setOnClickListener(v -> web.reload());
        translate.setOnClickListener(v -> showTranslateDialog());
        gemini.setOnClickListener(v -> showGeminiMenu());
        more.setOnClickListener(v -> showSettings());

        bar2.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
        bar2.addView(forward, new LinearLayout.LayoutParams(dp(48), dp(44)));
        bar2.addView(reload, new LinearLayout.LayoutParams(dp(48), dp(44)));
        bar2.addView(translate, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar2.addView(gemini, new LinearLayout.LayoutParams(0, dp(44), 1f));
        bar2.addView(more, new LinearLayout.LayoutParams(dp(48), dp(44)));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);

        web = new WebView(this);

        root.addView(bar1);
        root.addView(bar2);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSaveFormData(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        android.webkit.CookieManager.getInstance().setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= 26) {
            WebView.startSafeBrowsing(this, value -> {});
        }

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme();
                if ("http".equalsIgnoreCase(scheme)) {
                    Uri secure = u.buildUpon().scheme("https").build();
                    loadSecure(secure.toString());
                    return true;
                }
                if (!"https".equalsIgnoreCase(scheme)) return true;
                return false;
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("http".equalsIgnoreCase(u.getScheme())) {
                    return emptyResponse();
                }
                String host = u.getHost();
                if (host != null && isBlocked(host)) return emptyResponse();
                return null;
            }

            @Override public void onPageFinished(WebView view, String url) {
                address.setText(url);
                if (prefs.getBoolean("auto_translate", false) && !isTranslateUrl(url)) {
                    String target = prefs.getString("translate_target", defaultLanguage());
                    if (url.startsWith("https://") && !url.contains("google.com/search") && !url.contains("google.gr/search")) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> translateUrl(url, target), 450);
                    }
                }
            }

            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                toast("Μπλοκαρίστηκε μη ασφαλής σύνδεση SSL");
            }
        });
    }

    private boolean isBlocked(String host) {
        host = host.toLowerCase(Locale.ROOT);
        for (String d : blocked) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }

    private void loadInput(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return;
        if (q.matches("(?i)^https?://.*")) {
            if (q.toLowerCase(Locale.ROOT).startsWith("http://")) q = "https://" + q.substring(7);
            loadSecure(q);
        } else if (q.matches("(?i)^([a-z0-9-]+\\.)+[a-z]{2,}([/:?#].*)?$")) {
            loadSecure("https://" + q);
        } else {
            String domain = prefs.getString("google_domain", "google.gr");
            try {
                loadSecure("https://www." + domain + "/search?q=" + URLEncoder.encode(q, "UTF-8"));
            } catch (Exception e) {
                loadSecure("https://www." + domain + "/");
            }
        }
    }

    private void loadSecure(String url) {
        if (!url.startsWith("https://")) { toast("Επιτρέπεται μόνο HTTPS"); return; }
        Map<String,String> headers = new HashMap<>();
        headers.put("DNT","1");
        headers.put("Sec-GPC","1");
        web.loadUrl(url, headers);
    }

    private void goHome() {
        String domain = prefs.getString("google_domain", "google.gr");
        loadSecure("https://www." + domain + "/");
    }

    private String defaultLanguage() {
        String l = Locale.getDefault().getLanguage();
        return (l == null || l.isEmpty()) ? "el" : l;
    }

    private boolean isTranslateUrl(String url) {
        try { return "translate.google.com".equalsIgnoreCase(Uri.parse(url).getHost()); }
        catch(Exception e){ return false; }
    }

    private void showTranslateDialog() {
        final String[] names = {"Ελληνικά","English","Deutsch","Français","Español","Italiano","Português","Nederlands","Polski","Русский","Українська","Türkçe","العربية","עברית","日本語","한국어","中文","Shqip","Română","Български","Srpski","Hrvatski","Άλλη γλώσσα…"};
        final String[] codes = {"el","en","de","fr","es","it","pt","nl","pl","ru","uk","tr","ar","he","ja","ko","zh-CN","sq","ro","bg","sr","hr","custom"};
        new AlertDialog.Builder(this)
            .setTitle("Μετάφραση σελίδας • πηγή: Αυτόματα")
            .setItems(names, (d, which) -> {
                if ("custom".equals(codes[which])) askCustomLanguage();
                else {
                    prefs.edit().putString("translate_target", codes[which]).apply();
                    translateCurrentPage(codes[which]);
                }
            }).show();
    }

    private void askCustomLanguage() {
        EditText e = new EditText(this);
        e.setHint("π.χ. sv, cs, hi, id");
        e.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Κωδικός γλώσσας στόχου")
            .setView(e)
            .setPositiveButton("Μετάφραση", (d,w) -> {
                String c = e.getText().toString().trim();
                if (!c.matches("[A-Za-z-]{2,12}")) { toast("Μη έγκυρος κωδικός"); return; }
                prefs.edit().putString("translate_target", c).apply();
                translateCurrentPage(c);
            }).setNegativeButton("Άκυρο",null).show();
    }

    private void translateCurrentPage(String target) {
        String url = web.getUrl();
        if (url == null || !url.startsWith("https://")) { toast("Άνοιξε πρώτα ιστοσελίδα HTTPS"); return; }
        translateUrl(url, target);
    }

    private void translateUrl(String url, String target) {
        if (isTranslateUrl(url)) return;
        try {
            String tr = "https://translate.google.com/translate?sl=auto&tl=" +
                URLEncoder.encode(target, "UTF-8") + "&u=" + URLEncoder.encode(url, "UTF-8");
            prefs.edit().putBoolean("translation_in_progress", true).apply();
            loadSecure(tr);
        } catch (Exception e) { toast("Αποτυχία μετάφρασης"); }
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(8),dp(20),0);

        TextView t = new TextView(this);
        t.setText("Μηχανή αναζήτησης");
        box.addView(t);

        RadioGroup g = new RadioGroup(this);
        RadioButton gr = new RadioButton(this); gr.setText("Google.gr");
        RadioButton com = new RadioButton(this); com.setText("Google.com");
        g.addView(gr); g.addView(com);
        String cur = prefs.getString("google_domain","google.gr");
        (cur.equals("google.com") ? com : gr).setChecked(true);
        box.addView(g);

        CheckBox auto = new CheckBox(this);
        auto.setText("Αυτόματη μετάφραση κάθε ξένης σελίδας");
        auto.setChecked(prefs.getBoolean("auto_translate",false));
        box.addView(auto);

        TextView secure = new TextView(this);
        secure.setPadding(0,dp(8),0,0);
        secure.setText("HTTPS-only • Safe Browsing • τρίτα cookies κλειστά • tracker/ad block");
        box.addView(secure);

        new AlertDialog.Builder(this)
            .setTitle("Ρυθμίσεις browser")
            .setView(box)
            .setPositiveButton("Αποθήκευση",(d,w)->{
                prefs.edit()
                    .putString("google_domain", com.isChecked() ? "google.com" : "google.gr")
                    .putBoolean("auto_translate", auto.isChecked())
                    .apply();
                toast("Αποθηκεύτηκε");
            })
            .setNeutralButton("Καθαρισμός",(d,w)->clearBrowsingData())
            .setNegativeButton("Κλείσιμο",null).show();
    }

    private void clearBrowsingData() {
        web.clearHistory();
        web.clearCache(true);
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        WebStorage.getInstance().deleteAllData();
        toast("Καθαρίστηκαν cookies, cache και ιστορικό");
    }

    private void showGeminiMenu() {
        String key = loadApiKey();
        String[] items = {
            key.isEmpty() ? "🔑 Αποθήκευση Gemini Free key" : "🔑 Αλλαγή Gemini key",
            "✦ Ρώτα Gemini 3.8",
            "📄 Περίληψη τρέχουσας σελίδας",
            "🔊 Gemini 3.8 Flash-Lite TTS",
            "🧪 Έλεγχος key",
            "🗑 Διαγραφή key"
        };
        new AlertDialog.Builder(this).setTitle("Gemini")
            .setItems(items,(d,w)->{
                if (w==0) askAndSaveApiKey();
                if (w==1) askGeminiPrompt();
                if (w==2) summarizePage();
                if (w==3) askTtsText();
                if (w==4) testApiKey();
                if (w==5) clearApiKey();
            }).show();
    }

    private void askAndSaveApiKey() {
        EditText e = new EditText(this);
        e.setHint("Gemini API key");
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Gemini Free key")
            .setMessage("Το key δεν υπάρχει μέσα στο APK. Κρυπτογραφείται τοπικά με Android Keystore.")
            .setView(e)
            .setPositiveButton("Αποθήκευση",(d,w)->{
                String k=e.getText().toString().trim();
                if(k.length()<20){ toast("Το key φαίνεται μη έγκυρο"); return; }
                try { saveApiKey(k); toast("Το key αποθηκεύτηκε κρυπτογραφημένα"); }
                catch(Exception ex){ toast("Αποτυχία ασφαλούς αποθήκευσης"); }
            }).setNegativeButton("Άκυρο",null).show();
    }

    private void askGeminiPrompt() {
        if (!ensureKey()) return;
        EditText e = new EditText(this);
        e.setHint("Τι θέλεις να ρωτήσεις;");
        e.setMinLines(4);
        e.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this).setTitle("Gemini 3.8")
            .setView(e)
            .setPositiveButton("Αποστολή",(d,w)-> callGeminiText(e.getText().toString()))
            .setNegativeButton("Άκυρο",null).show();
    }

    private void summarizePage() {
        if (!ensureKey()) return;
        toast("Διαβάζω τη σελίδα…");
        web.evaluateJavascript("(function(){return document.body?document.body.innerText.slice(0,14000):'';})()", value -> {
            String text = decodeJsString(value);
            if (text.trim().isEmpty()) { toast("Δεν βρέθηκε κείμενο"); return; }
            callGeminiText("Κάνε σύντομη, ακριβή περίληψη στα ελληνικά του παρακάτω κειμένου ιστοσελίδας. Ξεχώρισε γεγονότα από ισχυρισμούς:\n\n" + text);
        });
    }

    private String decodeJsString(String value) {
        try { return new JSONArray("[" + value + "]").getString(0); }
        catch(Exception e){ return ""; }
    }

    private void callGeminiText(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) return;
        final String key = loadApiKey();
        toast("Gemini…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONArray contents = new JSONArray();
                JSONObject content = new JSONObject();
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text", prompt));
                content.put("parts", parts);
                contents.put(content);
                body.put("contents", contents);

                String response = postJson(
                    "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_TEXT + ":generateContent",
                    key, body.toString());

                JSONObject o = new JSONObject(response);
                JSONArray candidates = o.optJSONArray("candidates");
                if (candidates == null || candidates.length()==0) throw new Exception(apiMessage(o));
                JSONArray outParts = candidates.getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                StringBuilder sb = new StringBuilder();
                for(int i=0;i<outParts.length();i++) sb.append(outParts.getJSONObject(i).optString("text",""));
                runOnUiThread(() -> showTextResult("Gemini 3.8", sb.toString()));
            } catch(Exception ex) {
                runOnUiThread(() -> showError(ex.getMessage()));
            }
        });
    }

    private void askTtsText() {
        if (!ensureKey()) return;
        EditText e = new EditText(this);
        e.setHint("Κείμενο για εκφώνηση");
        e.setMinLines(4);
        e.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this).setTitle("Gemini 3.8 Flash-Lite TTS")
            .setMessage("Ανιχνεύει αυτόματα τη γλώσσα.")
            .setView(e)
            .setPositiveButton("▶ Εκφώνηση",(d,w)-> callTts(e.getText().toString()))
            .setNegativeButton("Άκυρο",null).show();
    }

    private void callTts(String text) {
        if(text==null || text.trim().isEmpty()) return;
        final String key=loadApiKey();
        toast("Δημιουργία φωνής…");
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("model", MODEL_TTS);

                JSONObject txt = new JSONObject().put("type","text").put("text",text);
                JSONArray annotations = new JSONArray();
                annotations.put(new JSONObject().put("type","speech_metadata").put("style","natural, clear and friendly"));
                txt.put("annotations", annotations);

                JSONArray content = new JSONArray().put(txt);
                JSONObject userInput = new JSONObject().put("type","user_input").put("content",content);
                body.put("input", new JSONArray().put(userInput));
                body.put("response_format", new JSONObject().put("type","audio"));
                body.put("generation_config",
                    new JSONObject().put("speech_config",
                        new JSONArray().put(new JSONObject().put("voice","Kore"))));

                String response = postJson("https://generativelanguage.googleapis.com/v1beta/interactions", key, body.toString());
                JSONObject o = new JSONObject(response);
                String b64 = findAudio(o);
                if (b64 == null || b64.isEmpty()) throw new Exception(apiMessage(o));

                byte[] wav = Base64.decode(b64, Base64.DEFAULT);
                File f = new File(getCacheDir(),"gemini_tts.wav");
                try(FileOutputStream fos=new FileOutputStream(f)){ fos.write(wav); }

                runOnUiThread(() -> playWav(f));
            } catch(Exception ex) {
                runOnUiThread(() -> showError(ex.getMessage()));
            }
        });
    }

    private String findAudio(JSONObject o) {
        JSONArray steps=o.optJSONArray("steps");
        if(steps==null) return null;
        for(int i=steps.length()-1;i>=0;i--){
            JSONObject step=steps.optJSONObject(i);
            if(step==null || !"model_output".equals(step.optString("type"))) continue;
            JSONArray c=step.optJSONArray("content");
            if(c==null) continue;
            for(int j=c.length()-1;j>=0;j--){
                JSONObject x=c.optJSONObject(j);
                if(x!=null && "audio".equals(x.optString("type")) && x.has("data")) return x.optString("data");
            }
        }
        return null;
    }

    private void playWav(File f) {
        try {
            if(mediaPlayer!=null){ mediaPlayer.release(); mediaPlayer=null; }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(f.getAbsolutePath());
            mediaPlayer.setOnPreparedListener(mp -> { toast("Αναπαραγωγή TTS"); mp.start(); });
            mediaPlayer.setOnCompletionListener(mp -> { mp.release(); if(mediaPlayer==mp) mediaPlayer=null; });
            mediaPlayer.prepareAsync();
        } catch(Exception e){ showError("Δεν μπόρεσε να παίξει το audio"); }
    }

    private void testApiKey() {
        if(!ensureKey()) return;
        callGeminiText("Απάντησε μόνο με τη λέξη OK.");
    }

    private String postJson(String url, String key, String json) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        c.setRequestProperty("x-goog-api-key", key);
        try(OutputStream os=c.getOutputStream()){ os.write(json.getBytes(StandardCharsets.UTF_8)); }
        int code=c.getResponseCode();
        InputStream in = code>=200 && code<300 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(in);
        if(code<200 || code>=300) throw new Exception("Gemini API " + code + ": " + extractError(text));
        return text;
    }

    private String readAll(InputStream in) throws Exception {
        if(in==null) return "";
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        int n;
        while((n=in.read(buf))!=-1) b.write(buf,0,n);
        return b.toString("UTF-8");
    }

    private String extractError(String text) {
        try { return new JSONObject(text).getJSONObject("error").optString("message",text); }
        catch(Exception e){ return text.length()>300?text.substring(0,300):text; }
    }

    private String apiMessage(JSONObject o) {
        JSONObject e=o.optJSONObject("error");
        return e!=null ? e.optString("message","Δεν επέστρεψε αποτέλεσμα") : "Δεν επέστρεψε αποτέλεσμα";
    }

    private void showTextResult(String title, String text) {
        TextView tv=new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(18),dp(12),dp(18),dp(12));
        ScrollView sv=new ScrollView(this); sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(sv)
            .setPositiveButton("Κλείσιμο",null)
            .setNeutralButton("Αντιγραφή",(d,w)->{
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText(title,text));
                toast("Αντιγράφηκε");
            }).show();
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this).setTitle("Σφάλμα")
            .setMessage(msg==null?"Άγνωστο σφάλμα":msg)
            .setPositiveButton("OK",null).show();
    }

    private boolean ensureKey() {
        if(loadApiKey().isEmpty()){ toast("Αποθήκευσε πρώτα το Gemini key"); askAndSaveApiKey(); return false; }
        return true;
    }

    private void saveApiKey(String value) throws Exception {
        SecretKey key = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key);
        byte[] ct=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
            .putString("gemini_iv", Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("gemini_ct", Base64.encodeToString(ct,Base64.NO_WRAP))
            .apply();
    }

    private String loadApiKey() {
        try {
            String ivs=prefs.getString("gemini_iv","");
            String cts=prefs.getString("gemini_ct","");
            if(ivs.isEmpty()||cts.isEmpty()) return "";
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
            SecretKey key=(SecretKey)ks.getKey(KEY_ALIAS,null);
            if(key==null) return "";
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(ivs,Base64.NO_WRAP)));
            byte[] p=cipher.doFinal(Base64.decode(cts,Base64.NO_WRAP));
            return new String(p,StandardCharsets.UTF_8);
        } catch(Exception e){ return ""; }
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if(ks.containsAlias(KEY_ALIAS)) return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return gen.generateKey();
    }

    private void clearApiKey() {
        prefs.edit().remove("gemini_iv").remove("gemini_ct").apply();
        try {
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
            if(ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        } catch(Exception ignored){}
        toast("Το Gemini key διαγράφηκε");
    }

    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    @Override public void onBackPressed() {
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        if(mediaPlayer!=null){ mediaPlayer.release(); mediaPlayer=null; }
        if(web!=null){ web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
}
