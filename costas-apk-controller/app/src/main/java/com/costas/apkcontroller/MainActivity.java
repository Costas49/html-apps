package com.costas.apkcontroller;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("costas_builder", MODE_PRIVATE);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "Builder");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void callback(String fn, Object value) {
        runOnUiThread(() -> {
            String jsArg = JSONObject.quote(value == null ? "" : String.valueOf(value));
            webView.evaluateJavascript(fn + "(" + jsArg + ")", null);
        });
    }

    private HttpURLConnection github(String method, String url, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.trim().isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        return c;
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String response(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        String body = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
        if (code < 200 || code >= 300) {
            String msg = "HTTP " + code;
            try {
                JSONObject j = new JSONObject(body);
                msg = j.optString("message", msg);
            } catch (Exception ignored) {}
            throw new Exception(msg);
        }
        return body;
    }

    private void sendJson(HttpURLConnection c, JSONObject body) throws Exception {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = c.getOutputStream()) { os.write(data); }
    }

    public class Bridge {
        @JavascriptInterface
        public void saveSettings(String token, String owner, String repo, String branch) {
            prefs.edit()
                    .putString("token", token == null ? "" : token.trim())
                    .putString("owner", owner == null ? "Costas49" : owner.trim())
                    .putString("repo", repo == null ? "html-apps" : repo.trim())
                    .putString("branch", branch == null ? "main" : branch.trim())
                    .apply();
            callback("onMessage", "Αποθηκεύτηκαν τοπικά στη συσκευή.");
        }

        @JavascriptInterface
        public String getToken() { return prefs.getString("token", ""); }

        @JavascriptInterface
        public String getOwner() { return prefs.getString("owner", "Costas49"); }

        @JavascriptInterface
        public String getRepo() { return prefs.getString("repo", "html-apps"); }

        @JavascriptInterface
        public String getBranch() { return prefs.getString("branch", "main"); }

        @JavascriptInterface
        public void loadHtml(String token, String owner, String repo, String branch) {
            new Thread(() -> {
                try {
                    String path = "costas-apk-builder/app/src/main/assets/index.html";
                    String api = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path + "?ref=" + Uri.encode(branch);
                    HttpURLConnection c = github("GET", api, token);
                    JSONObject j = new JSONObject(response(c));
                    String content = j.optString("content", "").replace("\n", "");
                    byte[] decoded = Base64.decode(content, Base64.DEFAULT);
                    callback("onHtmlLoaded", new String(decoded, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    callback("onError", "Φόρτωση HTML: " + e.getMessage());
                }
            }).start();
        }

        @JavascriptInterface
        public void saveHtml(String token, String owner, String repo, String branch, String html) {
            new Thread(() -> {
                try {
                    String path = "costas-apk-builder/app/src/main/assets/index.html";
                    String api = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
                    String sha = null;
                    try {
                        HttpURLConnection g = github("GET", api + "?ref=" + Uri.encode(branch), token);
                        JSONObject cur = new JSONObject(response(g));
                        sha = cur.optString("sha", null);
                    } catch (Exception ignored) {}

                    JSONObject body = new JSONObject();
                    body.put("message", "Update app HTML from Costas APK Builder");
                    body.put("content", Base64.encodeToString(html.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
                    body.put("branch", branch);
                    if (sha != null && !sha.isEmpty()) body.put("sha", sha);

                    HttpURLConnection p = github("PUT", api, token);
                    sendJson(p, body);
                    response(p);
                    callback("onMessage", "Το HTML αποθηκεύτηκε στο GitHub.");
                } catch (Exception e) {
                    callback("onError", "Αποθήκευση HTML: " + e.getMessage());
                }
            }).start();
        }

        @JavascriptInterface
        public void runBuild(String token, String owner, String repo, String branch,
                             String appName, String packageName, String versionCode,
                             String versionName, String apkName) {
            new Thread(() -> {
                try {
                    String api = "https://api.github.com/repos/" + owner + "/" + repo +
                            "/actions/workflows/costas-apk-builder.yml/dispatches";
                    JSONObject inputs = new JSONObject();
                    inputs.put("app_name", appName);
                    inputs.put("package_name", packageName);
                    inputs.put("version_code", versionCode);
                    inputs.put("version_name", versionName);
                    inputs.put("apk_name", apkName);

                    JSONObject body = new JSONObject();
                    body.put("ref", branch);
                    body.put("inputs", inputs);

                    HttpURLConnection c = github("POST", api, token);
                    sendJson(c, body);
                    response(c);
                    callback("onBuildStarted", "Το build ξεκίνησε. Περίμενε λίγο και πάτα «Έλεγχος κατάστασης».");
                } catch (Exception e) {
                    callback("onError", "Build: " + e.getMessage());
                }
            }).start();
        }

        @JavascriptInterface
        public void checkStatus(String token, String owner, String repo, String branch) {
            new Thread(() -> {
                try {
                    String api = "https://api.github.com/repos/" + owner + "/" + repo +
                            "/actions/workflows/costas-apk-builder.yml/runs?event=workflow_dispatch&branch=" +
                            Uri.encode(branch) + "&per_page=1";
                    HttpURLConnection c = github("GET", api, token);
                    JSONObject j = new JSONObject(response(c));
                    JSONArray runs = j.optJSONArray("workflow_runs");
                    if (runs == null || runs.length() == 0) {
                        callback("onStatus", "Δεν βρέθηκε build ακόμη.");
                        return;
                    }
                    JSONObject r = runs.getJSONObject(0);
                    String status = r.optString("status", "");
                    String conclusion = r.optString("conclusion", "");
                    long runId = r.optLong("id", 0);
                    String text = "Κατάσταση: " + status + (conclusion.isEmpty() ? "" : " / " + conclusion) + " | Run " + runId;
                    callback("onStatus", text);
                } catch (Exception e) {
                    callback("onError", "Κατάσταση: " + e.getMessage());
                }
            }).start();
        }

        @JavascriptInterface
        public void downloadAndInstallLatest(String token, String owner, String repo, String branch) {
            new Thread(() -> {
                try {
                    callback("onMessage", "Ελέγχω το τελευταίο επιτυχημένο build…");
                    String runsApi = "https://api.github.com/repos/" + owner + "/" + repo +
                            "/actions/workflows/costas-apk-builder.yml/runs?event=workflow_dispatch&branch=" +
                            Uri.encode(branch) + "&status=success&per_page=1";
                    HttpURLConnection rc = github("GET", runsApi, token);
                    JSONObject rj = new JSONObject(response(rc));
                    JSONArray runs = rj.optJSONArray("workflow_runs");
                    if (runs == null || runs.length() == 0) throw new Exception("Δεν υπάρχει επιτυχημένο build.");
                    long runId = runs.getJSONObject(0).getLong("id");

                    String artApi = "https://api.github.com/repos/" + owner + "/" + repo +
                            "/actions/runs/" + runId + "/artifacts";
                    HttpURLConnection ac = github("GET", artApi, token);
                    JSONObject aj = new JSONObject(response(ac));
                    JSONArray arts = aj.optJSONArray("artifacts");
                    if (arts == null || arts.length() == 0) throw new Exception("Δεν βρέθηκε APK artifact.");

                    long artifactId = 0;
                    for (int i = 0; i < arts.length(); i++) {
                        JSONObject a = arts.getJSONObject(i);
                        if ("Costas-APK-Output".equals(a.optString("name"))) {
                            artifactId = a.getLong("id");
                            break;
                        }
                    }
                    if (artifactId == 0) artifactId = arts.getJSONObject(0).getLong("id");

                    callback("onMessage", "Κατεβάζω το APK…");
                    String zipApi = "https://api.github.com/repos/" + owner + "/" + repo +
                            "/actions/artifacts/" + artifactId + "/zip";

                    HttpURLConnection zc = github("GET", zipApi, token);
                    zc.setInstanceFollowRedirects(false);
                    int code = zc.getResponseCode();
                    String location = zc.getHeaderField("Location");
                    InputStream zin;
                    if (code >= 300 && code < 400 && location != null) {
                        HttpURLConnection dl = (HttpURLConnection) new URL(location).openConnection();
                        dl.setConnectTimeout(20000);
                        dl.setReadTimeout(120000);
                        zin = new BufferedInputStream(dl.getInputStream());
                    } else if (code >= 200 && code < 300) {
                        zin = new BufferedInputStream(zc.getInputStream());
                    } else {
                        throw new Exception("Artifact download HTTP " + code);
                    }

                    File outDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "generated");
                    if (!outDir.exists()) outDir.mkdirs();
                    File apk = null;

                    try (ZipInputStream zis = new ZipInputStream(zin)) {
                        ZipEntry e;
                        byte[] buf = new byte[8192];
                        while ((e = zis.getNextEntry()) != null) {
                            if (!e.isDirectory() && e.getName().toLowerCase().endsWith(".apk")) {
                                String name = new File(e.getName()).getName();
                                apk = new File(outDir, name);
                                try (FileOutputStream fos = new FileOutputStream(apk)) {
                                    int n;
                                    while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                                }
                                break;
                            }
                        }
                    }
                    if (apk == null || !apk.exists()) throw new Exception("Το ZIP δεν περιείχε APK.");

                    File finalApk = apk;
                    runOnUiThread(() -> installApk(finalApk));
                } catch (Exception e) {
                    callback("onError", "Λήψη/εγκατάσταση: " + e.getMessage());
                }
            }).start();
        }

        @JavascriptInterface
        public void openBuilds(String owner, String repo) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/" + owner + "/" + repo + "/actions/workflows/costas-apk-builder.yml")));
            } catch (Exception ignored) {}
        }
    }

    private void installApk(File apk) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(s);
            callback("onMessage", "Επίτρεψε εγκατάσταση από αυτή την εφαρμογή και μετά πάτα ξανά «Λήψη & Εγκατάσταση».");
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, "com.costas.apkcontroller.fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }
}
