package com.kostas.lencoremote;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newFixedThreadPool(8);
    private final Handler main = new Handler(Looper.getMainLooper());

    private EditText ipInput;
    private EditText searchInput;
    private TextView statusView;
    private TextView nowTitle;
    private TextView nowMeta;
    private TextView bluetoothView;
    private SeekBar volumeBar;
    private LinearLayout searchResults;
    private LinearLayout favoritesBox;

    private volatile RadioApi api;
    private volatile boolean connected = false;
    private boolean suppressVolume = false;

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (connected) refreshNowPlaying();
            main.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        String saved = getSharedPreferences("lenco", MODE_PRIVATE).getString("ip", "");
        ipInput.setText(saved);
        main.post(poller);
        if (!saved.isEmpty()) connect(saved);
    }

    @Override
    protected void onDestroy() {
        connected = false;
        main.removeCallbacks(poller);
        io.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(root);

        TextView title = text("LENCO DIR-70BK REMOTE", 26, true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView sub = text("Wi-Fi χειριστήριο • AirMusic • ελαφρύ για Lenovo", 14, false);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(sub);

        statusView = text("Δεν έχει συνδεθεί ακόμη.", 15, true);
        statusView.setPadding(0, dp(14), 0, dp(8));
        root.addView(statusView);

        LinearLayout conn = horizontal();
        ipInput = new EditText(this);
        ipInput.setHint("IP π.χ. 192.168.1.50");
        ipInput.setSingleLine(true);
        ipInput.setInputType(InputType.TYPE_CLASS_PHONE);
        conn.addView(ipInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        conn.addView(button("Σύνδεση", v -> connect(ipInput.getText().toString().trim())));
        root.addView(conn);

        LinearLayout discoverRow = horizontal();
        discoverRow.addView(button("🔎 Αυτόματος εντοπισμός", v -> autoDiscover()), weight());
        discoverRow.addView(button("↻ Ανανέωση", v -> refreshNowPlaying()), weight());
        root.addView(discoverRow);

        root.addView(section("Τώρα παίζει"));
        nowTitle = text("—", 22, true);
        nowMeta = text("—", 15, false);
        bluetoothView = text("Bluetooth: —", 14, false);
        root.addView(nowTitle);
        root.addView(nowMeta);
        root.addView(bluetoothView);

        root.addView(section("Χειριστήριο"));
        LinearLayout quick = horizontal();
        quick.addView(button("⏻ Power", v -> sendKey(7)), weight());
        quick.addView(button("⌂ Home", v -> sendKey(1)), weight());
        quick.addView(button("Mode", v -> sendKey(28)), weight());
        root.addView(quick);

        LinearLayout quick2 = horizontal();
        quick2.addView(button("Internet Radio", v -> sendKey(40)), weight());
        quick2.addView(button("♥ Αγαπημένα", v -> loadFavorites()), weight());
        quick2.addView(button("Bluetooth Pair", v -> startBluetoothPair()), weight());
        root.addView(quick2);

        LinearLayout up = horizontal();
        up.setGravity(Gravity.CENTER_HORIZONTAL);
        up.addView(button("▲", v -> sendKey(2)), new LinearLayout.LayoutParams(dp(90), dp(56)));
        root.addView(up);

        LinearLayout mid = horizontal();
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.addView(button("◀", v -> sendKey(4)), new LinearLayout.LayoutParams(dp(90), dp(56)));
        mid.addView(button("OK", v -> sendKey(6)), new LinearLayout.LayoutParams(dp(90), dp(56)));
        mid.addView(button("▶", v -> sendKey(5)), new LinearLayout.LayoutParams(dp(90), dp(56)));
        root.addView(mid);

        LinearLayout down = horizontal();
        down.setGravity(Gravity.CENTER_HORIZONTAL);
        down.addView(button("▼", v -> sendKey(3)), new LinearLayout.LayoutParams(dp(90), dp(56)));
        root.addView(down);

        LinearLayout nav = horizontal();
        nav.addView(button("← Back", v -> back()), weight());
        nav.addView(button("Dim", v -> sendKey(14)), weight());
        nav.addView(button("Next Fav", v -> sendKey(112)), weight());
        root.addView(nav);

        root.addView(section("Ήχος"));
        LinearLayout volButtons = horizontal();
        volButtons.addView(button("−", v -> sendKey(10)), new LinearLayout.LayoutParams(dp(58), dp(52)));
        volumeBar = new SeekBar(this);
        volumeBar.setMax(15);
        volumeBar.setProgress(7);
        volButtons.addView(volumeBar, new LinearLayout.LayoutParams(0, dp(52), 1));
        volButtons.addView(button("+", v -> sendKey(9)), new LinearLayout.LayoutParams(dp(58), dp(52)));
        root.addView(volButtons);
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (!suppressVolume) setVolume(seekBar.getProgress());
            }
        });

        LinearLayout play = horizontal();
        play.addView(button("🔇 Mute", v -> sendKey(8)), weight());
        play.addView(button("⏮", v -> sendKey(32)), weight());
        play.addView(button("⏯", v -> playPause()), weight());
        play.addView(button("⏭", v -> sendKey(31)), weight());
        play.addView(button("■", v -> stopPlayback()), weight());
        root.addView(play);

        root.addView(section("Αναζήτηση Internet Radio"));
        LinearLayout searchRow = horizontal();
        searchInput = new EditText(this);
        searchInput.setHint("Γράψε όνομα σταθμού");
        searchInput.setSingleLine(true);
        searchRow.addView(searchInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        searchRow.addView(button("🔍 Ψάξε", v -> searchStations()), new LinearLayout.LayoutParams(dp(120), dp(52)));
        root.addView(searchRow);

        searchResults = vertical();
        root.addView(searchResults);

        root.addView(section("Αγαπημένα"));
        favoritesBox = vertical();
        root.addView(favoritesBox);

        TextView foot = text("Για πλήρη χειρισμό το Lenovo και το Lenco πρέπει να είναι στο ίδιο Wi-Fi. Το Bluetooth χρησιμοποιείται κυρίως για ήχο.", 13, false);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);

        setContentView(scroll);
    }

    private void connect(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            toast("Βάλε IP ή πάτησε Αυτόματος εντοπισμός.");
            return;
        }
        status("Σύνδεση με " + ip + " …");
        io.execute(() -> {
            try {
                RadioApi candidate = new RadioApi(ip);
                String init = candidate.get("init", map("language", "en"), 3000);
                if (init == null || !init.contains("<result")) throw new Exception("Δεν απάντησε το AirMusic.");
                api = candidate;
                connected = true;
                String name = safeTag(candidate.get("irdevice.xml", null, 2500), "friendlyName");
                getSharedPreferences("lenco", MODE_PRIVATE).edit().putString("ip", ip).apply();
                main.post(() -> {
                    ipInput.setText(ip);
                    status("✓ Συνδέθηκε" + (name.isEmpty() ? "" : " — " + name) + " (" + ip + ")");
                });
                refreshNowPlaying();
                loadFavorites();
            } catch (Exception e) {
                connected = false;
                status("✗ Δεν συνδέθηκε: " + friendlyError(e));
            }
        });
    }

    private void autoDiscover() {
        status("🔎 Ψάχνω το Lenco στο τοπικό Wi-Fi…");
        io.execute(() -> {
            String prefix = localPrefix();
            if (prefix == null) {
                status("Δεν βρήκα τοπικό IPv4. Βάλε χειροκίνητα την IP του Lenco.");
                return;
            }
            ExecutorService scan = Executors.newFixedThreadPool(32);
            AtomicBoolean found = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(254);
            final String[] hit = {null};
            for (int i = 1; i <= 254; i++) {
                final String host = prefix + i;
                scan.execute(() -> {
                    try {
                        if (!found.get()) {
                            RadioApi probe = new RadioApi(host);
                            String xml = probe.get("irdevice.xml", null, 450);
                            if (xml != null && xml.contains("<friendlyName>")) {
                                if (found.compareAndSet(false, true)) hit[0] = host;
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try { latch.await(12, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            scan.shutdownNow();
            if (hit[0] != null) {
                String h = hit[0];
                main.post(() -> ipInput.setText(h));
                connect(h);
            } else {
                status("Δεν εντοπίστηκε αυτόματα. Βάλε την IP που δείχνει το Lenco στις ρυθμίσεις Wi-Fi.");
            }
        });
    }

    private String localPrefix() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(ifaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                for (InetAddress a : Collections.list(addrs)) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        String ip = a.getHostAddress();
                        int last = ip.lastIndexOf('.');
                        if (last > 0) return ip.substring(0, last + 1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void refreshNowPlaying() {
        RadioApi r = api;
        if (!connected || r == null) return;
        io.execute(() -> {
            try {
                String info = r.get("playinfo", null, 2500);
                String station = safeTag(info, "station_info");
                String song = safeTag(info, "song");
                String artist = safeTag(info, "artist");
                String fmt = safeTag(info, "stream_format");
                String stat = safeTag(info, "status");
                String vol = safeTag(info, "vol");
                String bt = "";
                try {
                    String btx = r.get("GetBTStatus", null, 1600);
                    String code = safeTag(btx, "Status");
                    if (!code.isEmpty()) bt = "Bluetooth status: " + code;
                } catch (Exception ignored) {}
                final String title = !station.isEmpty() ? station : (!song.isEmpty() ? song : "—");
                final String meta = joinNonEmpty(" • ", artist, song, fmt, stat);
                final String btFinal = bt.isEmpty() ? "Bluetooth: —" : bt;
                final int v = parseInt(vol, -1);
                main.post(() -> {
                    nowTitle.setText(title);
                    nowMeta.setText(meta.isEmpty() ? "—" : meta);
                    bluetoothView.setText(btFinal);
                    if (v >= 0 && v <= 15) {
                        suppressVolume = true;
                        volumeBar.setProgress(v);
                        suppressVolume = false;
                    }
                });
            } catch (Exception e) {
                // Keep the last useful screen; transient radio timeouts are normal.
            }
        });
    }

    private void searchStations() {
        String q = searchInput.getText().toString().trim();
        RadioApi r = api;
        if (!connected || r == null) {
            toast("Σύνδεσε πρώτα το Lenco.");
            return;
        }
        if (q.isEmpty()) {
            toast("Γράψε όνομα σταθμού.");
            return;
        }
        searchResults.removeAllViews();
        TextView wait = text("Αναζήτηση: " + q + " …", 14, false);
        searchResults.addView(wait);
        io.execute(() -> {
            try {
                try { r.get("Sendkey", map("key", "40"), 1800); } catch (Exception ignored) {}
                Thread.sleep(600);
                String sr = r.get("searchstn", map("str", q), 3500);
                String menuId = safeTag(sr, "id");
                if (menuId.isEmpty()) throw new Exception("Η συσκευή δεν επέστρεψε λίστα αναζήτησης.");
                r.get("gochild", map("id", menuId), 2500);
                String list = r.get("list", map3("id", menuId, "start", "1", "count", "100"), 4500);
                List<RadioItem> items = parseItems(list);
                main.post(() -> showSearchResults(items));
            } catch (Exception e) {
                main.post(() -> {
                    searchResults.removeAllViews();
                    TextView t = text("Δεν ολοκληρώθηκε η αναζήτηση. Πάτησε «Internet Radio» και ξαναδοκίμασε. " + friendlyError(e), 14, false);
                    searchResults.addView(t);
                });
            }
        });
    }

    private void showSearchResults(List<RadioItem> items) {
        searchResults.removeAllViews();
        if (items.isEmpty()) {
            searchResults.addView(text("Δεν βρέθηκαν σταθμοί.", 14, false));
            return;
        }
        int shown = 0;
        for (RadioItem item : items) {
            if (item.name.isEmpty() || item.id.isEmpty()) continue;
            Button b = button("▶ " + item.name, v -> playStation(item.id, item.name));
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            searchResults.addView(b, new LinearLayout.LayoutParams(-1, dp(52)));
            if (++shown >= 40) break;
        }
    }

    private void playStation(String id, String name) {
        RadioApi r = api;
        if (r == null) return;
        status("▶ " + name);
        io.execute(() -> {
            try {
                r.get("play_stn", map("id", id), 3500);
                Thread.sleep(700);
                refreshNowPlaying();
            } catch (Exception e) {
                status("Δεν ξεκίνησε ο σταθμός: " + friendlyError(e));
            }
        });
    }

    private void loadFavorites() {
        RadioApi r = api;
        if (!connected || r == null) return;
        io.execute(() -> {
            try {
                String xml = r.get("hotkeylist", null, 3000);
                List<RadioItem> items = parseItems(xml);
                main.post(() -> {
                    favoritesBox.removeAllViews();
                    int pos = 1;
                    for (RadioItem item : items) {
                        if (item.name.isEmpty() || item.name.toLowerCase(Locale.ROOT).contains("empty")) {
                            pos++;
                            continue;
                        }
                        final int key = pos++;
                        Button b = button("♥ " + key + ". " + item.name, v -> playFavorite(key));
                        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        favoritesBox.addView(b, new LinearLayout.LayoutParams(-1, dp(50)));
                    }
                    if (favoritesBox.getChildCount() == 0) {
                        favoritesBox.addView(text("Δεν υπάρχουν αποθηκευμένα αγαπημένα ή δεν επιστράφηκαν από το Lenco.", 14, false));
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    favoritesBox.removeAllViews();
                    favoritesBox.addView(text("Αδυναμία φόρτωσης αγαπημένων.", 14, false));
                });
            }
        });
    }

    private void playFavorite(int key) {
        runCommand("playhotkey", map("key", String.valueOf(key)), "Αγαπημένο " + key);
    }

    private void startBluetoothPair() {
        runCommand("StartBTMatch", null, "Bluetooth pairing ενεργό");
    }

    private void playPause() {
        runCommand("PlayOP", map("cmd", "PlayPause"), "Play / Pause");
    }

    private void stopPlayback() {
        runCommand("stop", null, "Stop");
    }

    private void back() {
        runCommand("back", null, "Back");
    }

    private void setVolume(int value) {
        runCommand("setvol", map("vol", String.valueOf(value)), "Ένταση " + value + "/15");
    }

    private void sendKey(int key) {
        runCommand("Sendkey", map("key", String.valueOf(key)), "Εντολή " + key);
    }

    private void runCommand(String path, Map<String,String> params, String okText) {
        RadioApi r = api;
        if (!connected || r == null) {
            toast("Σύνδεσε πρώτα το Lenco.");
            return;
        }
        io.execute(() -> {
            try {
                r.get(path, params, 2600);
                status("✓ " + okText);
                main.postDelayed(this::refreshNowPlaying, 450);
            } catch (Exception e) {
                status("✗ " + friendlyError(e));
            }
        });
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, dp(54), 1);
    }

    private TextView section(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, dp(20), 0, dp(8));
        return t;
    }

    private TextView text(String s, float size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setPadding(dp(4), dp(4), dp(4), dp(4));
        return t;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        b.setMinHeight(dp(48));
        return b;
    }

    private void status(String s) {
        main.post(() -> statusView.setText(s));
    }

    private void toast(String s) {
        main.post(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static String friendlyError(Exception e) {
        String m = e.getMessage();
        return (m == null || m.trim().isEmpty()) ? e.getClass().getSimpleName() : m;
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String joinNonEmpty(String sep, String... values) {
        StringBuilder out = new StringBuilder();
        for (String v : values) {
            if (v == null || v.trim().isEmpty()) continue;
            if (out.length() > 0) out.append(sep);
            out.append(v.trim());
        }
        return out.toString();
    }

    private static Map<String,String> map(String k, String v) {
        Map<String,String> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private static Map<String,String> map3(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String,String> m = new LinkedHashMap<>();
        m.put(k1, v1); m.put(k2, v2); m.put(k3, v3);
        return m;
    }

    private static String safeTag(String xml, String tag) {
        if (xml == null) return "";
        Pattern p = Pattern.compile("<" + Pattern.quote(tag) + ">(.*?)</" + Pattern.quote(tag) + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        if (!m.find()) return "";
        return clean(m.group(1));
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    private static List<RadioItem> parseItems(String xml) {
        List<RadioItem> out = new ArrayList<>();
        if (xml == null) return out;
        Matcher m = Pattern.compile("<item>(.*?)</item>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        while (m.find()) {
            String block = m.group(1);
            RadioItem item = new RadioItem();
            item.id = safeTag(block, "id");
            item.status = safeTag(block, "status");
            item.name = safeTag(block, "name");
            if (item.name.isEmpty()) item.name = safeTag(block, "Freq");
            out.add(item);
        }
        return out;
    }

    private static class RadioItem {
        String id = "";
        String status = "";
        String name = "";
    }

    private static class RadioApi {
        private static final String USER = "su3g4go6sk7";
        private static final String PASS = "ji39454xu/^";
        private final String host;

        RadioApi(String host) {
            this.host = host.trim();
        }

        String get(String path, Map<String,String> params, int timeoutMs) throws Exception {
            StringBuilder url = new StringBuilder("http://").append(host).append(":80/").append(path);
            if (params != null && !params.isEmpty()) {
                url.append("?");
                boolean first = true;
                for (Map.Entry<String,String> e : params.entrySet()) {
                    if (!first) url.append("&");
                    first = false;
                    url.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                       .append("=")
                       .append(URLEncoder.encode(e.getValue(), "UTF-8"));
                }
            }
            HttpURLConnection c = (HttpURLConnection) new java.net.URL(url.toString()).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setUseCaches(false);
            String auth = Base64.encodeToString((USER + ":" + PASS).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + auth);
            c.setRequestProperty("Connection", "close");
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
            String body = readAll(in);
            c.disconnect();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            return body;
        }

        private static String readAll(InputStream in) throws Exception {
            if (in == null) return "";
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        }
    }
}
