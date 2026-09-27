package gr.maga.vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class MainActivity extends Activity implements VpnController.Listener {
    private static final int REQ_VPN = 1001;
    private static final int REQ_CONFIG = 1002;
    private VpnController controller;
    private TextView status;
    private TextView stats;
    private TextView details;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (controller != null) controller.refresh();
            handler.postDelayed(this, 1500);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        controller = VpnController.get();
        setContentView(buildUi());
    }

    private ScrollView buildUi() {
        int bg = Color.rgb(16,21,29), card = Color.rgb(24,33,44);
        int text = Color.rgb(244,247,250), muted = Color.rgb(170,183,196);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(22),dp(22),dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));

        TextView title = label("MAGA VPN",30,text,true);
        root.addView(title);
        TextView sub = label("WireGuard • encrypted config • full tunnel",15,muted,false);
        sub.setPadding(0,dp(4),0,dp(18));
        root.addView(sub);

        status = label("○ VPN ΚΛΕΙΣΤΟ\nΑποσυνδεδεμένο",22,text,true);
        status.setBackgroundColor(card);
        status.setPadding(dp(18),dp(18),dp(18),dp(18));
        root.addView(status, params(-1,-2,0,8));

        stats = label("RX 0 B   •   TX 0 B",16,muted,false);
        stats.setBackgroundColor(card);
        stats.setPadding(dp(16),dp(16),dp(16),dp(16));
        root.addView(stats, params(-1,-2,0,18));

        Button connect = button("ΣΥΝΔΕΣΗ VPN");
        connect.setOnClickListener(v -> requestVpnThenConnect());
        root.addView(connect, params(-1,dp(56),0,8));

        Button disconnect = button("ΑΠΟΣΥΝΔΕΣΗ");
        disconnect.setOnClickListener(v -> controller.disconnect());
        root.addView(disconnect, params(-1,dp(56),0,16));

        Button imp = button("ΕΙΣΑΓΩΓΗ WIREGUARD .CONF");
        imp.setOnClickListener(v -> openConfigPicker());
        root.addView(imp, params(-1,dp(54),0,8));

        Button settings = button("ALWAYS-ON / KILL SWITCH");
        settings.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_VPN_SETTINGS)); }
            catch (Throwable t) { Toast.makeText(this,"Ρυθμίσεις > Δίκτυο > VPN",Toast.LENGTH_LONG).show(); }
        });
        root.addView(settings, params(-1,dp(54),0,8));

        Button clear = button("ΔΙΑΓΡΑΦΗ ΡΥΘΜΙΣΗΣ");
        clear.setOnClickListener(v -> controller.clearConfig());
        root.addView(clear, params(-1,dp(54),0,14));

        details = label("",14,muted,false);
        root.addView(details);
        updateDetails();
        return scroll;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setAllCaps(false);
        return b;
    }

    private TextView label(String s,float size,int color,boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(null,android.graphics.Typeface.BOLD);
        v.setGravity(Gravity.START);
        return v;
    }

    private LinearLayout.LayoutParams params(int w,int h,int top,int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w,h);
        p.setMargins(0,dp(top),0,dp(bottom));
        return p;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void updateDetails() {
        if (details == null) return;
        details.setText("Backend: WireGuard " + controller.backendVersion()
                + "\nConfig: " + (controller.hasConfig() ? "κρυπτογραφημένο στη συσκευή" : "δεν έχει εισαχθεί")
                + "\nFull tunnel: AllowedIPs = 0.0.0.0/0, ::/0"
                + "\nKill switch: Android VPN > Always-on > Block without VPN");
    }

    @Override protected void onStart() {
        super.onStart();
        controller.addListener(this);
        handler.post(refresh);
    }

    @Override protected void onStop() {
        handler.removeCallbacks(refresh);
        controller.removeListener(this);
        super.onStop();
    }

    private void requestVpnThenConnect() {
        if (!controller.hasConfig()) {
            Toast.makeText(this,"Πρώτα εισήγαγε το client .conf",Toast.LENGTH_LONG).show();
            return;
        }
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission,REQ_VPN);
        else controller.connect();
    }

    private void openConfigPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,REQ_CONFIG);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == REQ_VPN && resultCode == RESULT_OK) {
            controller.connect();
            return;
        }
        if (requestCode == REQ_CONFIG && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new IllegalStateException("Δεν ανοίγει το αρχείο");
                controller.importConfig(new String(readAll(in,128*1024),StandardCharsets.UTF_8));
            } catch (Throwable t) {
                Toast.makeText(this,"Σφάλμα αρχείου: " + safe(t),Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onStatus(Tunnel.State state,String message,long rx,long tx) {
        status.setText((state == Tunnel.State.UP ? "● VPN ΕΝΕΡΓΟ\n" : "○ VPN ΚΛΕΙΣΤΟ\n") + message);
        stats.setText(String.format(Locale.ROOT,"RX %s   •   TX %s",human(rx),human(tx)));
        updateDetails();
    }

    private static byte[] readAll(InputStream in,int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0,n;
        while ((n=in.read(buf)) != -1) {
            total += n;
            if (total > max) throw new IllegalArgumentException("Το .conf είναι υπερβολικά μεγάλο");
            out.write(buf,0,n);
        }
        return out.toByteArray();
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes/1024.0;
        if (kb < 1024) return String.format(Locale.ROOT,"%.1f KB",kb);
        double mb = kb/1024.0;
        if (mb < 1024) return String.format(Locale.ROOT,"%.1f MB",mb);
        return String.format(Locale.ROOT,"%.2f GB",mb/1024.0);
    }

    private static String safe(Throwable t) {
        String m=t.getMessage();
        return (m==null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }
}

final class VpnController {
    interface Listener { void onStatus(Tunnel.State state,String message,long rx,long tx); }
    private static volatile VpnController INSTANCE;

    static void init(Context context) {
        if (INSTANCE == null) {
            synchronized (VpnController.class) {
                if (INSTANCE == null) INSTANCE = new VpnController(context.getApplicationContext());
            }
        }
    }

    static VpnController get() {
        if (INSTANCE == null) throw new IllegalStateException("VPN controller not initialized");
        return INSTANCE;
    }

    private final GoBackend backend;
    private final SecureStore store;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private final MagaTunnel tunnel = new MagaTunnel();
    private volatile Config config;
    private volatile String message = "Αποσυνδεδεμένο";

    private VpnController(Context context) {
        backend = new GoBackend(context);
        store = new SecureStore(context);
        load();
        GoBackend.setAlwaysOnCallback(() -> io.execute(() -> {
            if (config == null) loadSync();
            if (config != null) connectInternal("Always-on ενεργό");
        }));
    }

    void addListener(Listener l) { listeners.add(l); refresh(); }
    void removeListener(Listener l) { listeners.remove(l); }
    boolean hasConfig() { return config != null; }

    String backendVersion() {
        try { return backend.getVersion(); }
        catch (Throwable t) { return "unknown"; }
    }

    void importConfig(String text) {
        io.execute(() -> {
            try {
                Config parsed = Config.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                store.save(text);
                config = parsed;
                message = "Η ρύθμιση αποθηκεύτηκε κρυπτογραφημένα";
            } catch (Throwable t) {
                message = "Μη έγκυρο WireGuard .conf: " + shortMessage(t);
            }
            refreshInternal();
        });
    }

    void clearConfig() {
        io.execute(() -> {
            try { backend.setState(tunnel,Tunnel.State.DOWN,null); } catch (Throwable ignored) {}
            store.clear();
            config = null;
            message = "Η ρύθμιση διαγράφηκε";
            refreshInternal();
        });
    }

    void connect() { io.execute(() -> connectInternal("Συνδεδεμένο")); }

    private void connectInternal(String ok) {
        if (config == null) {
            message = "Πρώτα εισήγαγε client .conf";
            refreshInternal();
            return;
        }
        try {
            backend.setState(tunnel,Tunnel.State.UP,config);
            message = ok;
        } catch (Throwable t) {
            message = "Αποτυχία σύνδεσης: " + shortMessage(t);
        }
        refreshInternal();
    }

    void disconnect() {
        io.execute(() -> {
            try {
                backend.setState(tunnel,Tunnel.State.DOWN,null);
                message = "Αποσυνδεδεμένο";
            } catch (Throwable t) {
                message = "Αποτυχία αποσύνδεσης: " + shortMessage(t);
            }
            refreshInternal();
        });
    }

    void refresh() { io.execute(this::refreshInternal); }

    private void refreshInternal() {
        Tunnel.State state = Tunnel.State.DOWN;
        long rx=0,tx=0;
        try {
            state = backend.getState(tunnel);
            if (state == Tunnel.State.UP) {
                Statistics s = backend.getStatistics(tunnel);
                rx=s.totalRx();
                tx=s.totalTx();
            }
        } catch (Throwable ignored) {}
        final Tunnel.State fs=state;
        final long frx=rx,ftx=tx;
        final String fm=message;
        main.post(() -> {
            for (Listener l:listeners) l.onStatus(fs,fm,frx,ftx);
        });
    }

    private void load() { io.execute(this::loadSync); }

    private void loadSync() {
        try {
            String text=store.load();
            if (text != null) {
                config=Config.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
                message="Έτοιμο για σύνδεση";
            }
        } catch (Throwable t) {
            config=null;
            message="Η αποθηκευμένη ρύθμιση δεν διαβάστηκε";
        }
        refreshInternal();
    }

    private static String shortMessage(Throwable t) {
        String m=t.getMessage();
        if (m==null || m.trim().isEmpty()) return t.getClass().getSimpleName();
        return m.length()>140 ? m.substring(0,140) : m;
    }

    private final class MagaTunnel implements Tunnel {
        @Override public String getName() { return "MagaVPN"; }
        @Override public void onStateChange(State newState) { refreshInternal(); }
    }
}

final class SecureStore {
    private static final String PREFS="maga_vpn_secure";
    private static final String KEY_ALIAS="maga_vpn_config_key_v1";
    private static final String CIPHER_TEXT="config_cipher";
    private static final String IV="config_iv";
    private final SharedPreferences prefs;

    SecureStore(Context context) {
        prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return (SecretKey)ks.getKey(KEY_ALIAS,null);
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return kg.generateKey();
    }

    void save(String plaintext) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString(CIPHER_TEXT,Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString(IV,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
                .apply();
    }

    String load() throws Exception {
        String data=prefs.getString(CIPHER_TEXT,null);
        String iv=prefs.getString(IV,null);
        if (data==null || iv==null) return null;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(data,Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }

    void clear() { prefs.edit().clear().apply(); }
}
