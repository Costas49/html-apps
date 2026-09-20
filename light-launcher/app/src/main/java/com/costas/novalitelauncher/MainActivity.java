package com.costas.novalitelauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int DEVICE_OUT_WIRED_HEADSET = 0x4;
    private static final int DEVICE_OUT_WIRED_HEADPHONE = 0x8;
    private static final int DEVICE_IN_WIRED_HEADSET = 0x80000010;

    private static final int FOR_MEDIA = 1;
    private static final int FORCE_NONE = 0;
    private static final int FORCE_SPEAKER = 1;

    private AudioManager audioManager;
    private TextView status;
    private String lastFmPackage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        buildUi();
        refreshStatus("Έτοιμο. Πάτησε το μεγάλο κουμπί για δοκιμή.");
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("FM ΧΩΡΙΣ ΑΚΟΥΣΤΙΚΑ");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth(dp(8)));

        TextView intro = new TextView(this);
        intro.setText("Αυτή η δοκιμή προσπαθεί να κάνει το Android να νομίζει ότι υπάρχει καλώδιο ακουστικών και μετά ανοίγει το εργοστασιακό FM.");
        intro.setTextSize(18);
        intro.setPadding(0, dp(8), 0, dp(14));
        root.addView(intro, fullWidth(dp(8)));

        Button test = bigButton("1. ΔΟΚΙΜΗ ΧΩΡΙΣ ΑΚΟΥΣΤΙΚΑ");
        test.setOnClickListener(v -> runUnlockAndOpen());
        root.addView(test, fullWidth(dp(10)));

        Button open = bigButton("2. ΑΝΟΙΞΕ ΜΟΝΟ ΤΟ FM");
        open.setOnClickListener(v -> {
            boolean ok = openFactoryFm();
            refreshStatus(ok ? "Άνοιξα το εργοστασιακό FM." : "Δεν βρήκα την εφαρμογή FM της συσκευής.");
        });
        root.addView(open, fullWidth(dp(10)));

        Button speaker = bigButton("3. ΠΡΟΣΠΑΘΗΣΕ ΗΧΟ ΣΤΟ ΗΧΕΙΟ");
        speaker.setOnClickListener(v -> {
            String r = forceSpeaker(true);
            refreshStatus("Δοκιμή ηχείου: " + r);
        });
        root.addView(speaker, fullWidth(dp(10)));

        Button check = bigButton("ΕΛΕΓΧΟΣ ΣΥΣΚΕΥΗΣ");
        check.setOnClickListener(v -> deviceCheck());
        root.addView(check, fullWidth(dp(10)));

        Button reset = bigButton("ΕΠΑΝΑΦΟΡΑ ΗΧΟΥ");
        reset.setOnClickListener(v -> resetAudio());
        root.addView(reset, fullWidth(dp(10)));

        status = new TextView(this);
        status.setTextSize(17);
        status.setPadding(dp(12), dp(14), dp(12), dp(14));
        status.setTextIsSelectable(true);
        root.addView(status, fullWidth(dp(8)));

        TextView note = new TextView(this);
        note.setText("Σημαντικό: αν μετά τη δοκιμή χαθεί ο ήχος από άλλη εφαρμογή, άνοιξε ξανά εδώ και πάτησε «ΕΠΑΝΑΦΟΡΑ ΗΧΟΥ». Η εφαρμογή δεν αλλάζει μόνιμα το Lenovo.");
        note.setTextSize(15);
        note.setPadding(0, dp(8), 0, 0);
        root.addView(note, fullWidth(0));

        setContentView(scroll);
    }

    private Button bigButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setMinHeight(dp(58));
        return b;
    }

    private LinearLayout.LayoutParams fullWidth(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottomMargin;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void runUnlockAndOpen() {
        refreshStatus("Γίνεται η δοκιμή…");

        new Thread(() -> {
            List<String> results = new ArrayList<>();
            results.add("Εικονικό ακουστικό: " + setVirtualHeadset(true));
            results.add("Ηχείο: " + forceSpeaker(true));

            runOnUiThread(() -> {
                boolean opened = openFactoryFm();
                results.add("FM: " + (opened ? "άνοιξε" : "δεν βρέθηκε"));
                refreshStatus(join(results));
                if (opened) {
                    new Handler().postDelayed(() -> forceSpeaker(true), 1200);
                }
            });
        }).start();
    }

    private String setVirtualHeadset(boolean connected) {
        int state = connected ? 1 : 0;
        List<String> out = new ArrayList<>();

        boolean any = false;
        any |= callSetWiredDeviceState(DEVICE_OUT_WIRED_HEADPHONE, state, "FM-Virtual", out);
        any |= callSetWiredDeviceState(DEVICE_OUT_WIRED_HEADSET, state, "FM-Virtual", out);
        any |= callSetWiredDeviceState(DEVICE_IN_WIRED_HEADSET, state, "FM-Virtual", out);

        boolean seen = false;
        try {
            seen = audioManager.isWiredHeadsetOn();
        } catch (Throwable ignored) {}

        if (any) {
            return connected
                    ? "εντολή στάλθηκε, Android βλέπει ακουστικό=" + (seen ? "ΝΑΙ" : "ΟΧΙ")
                    : "έγινε επαναφορά";
        }
        return "δεν επιτράπηκε από το Android (" + join(out) + ")";
    }

    private boolean callSetWiredDeviceState(int device, int state, String name, List<String> details) {
        try {
            Method m = AudioManager.class.getDeclaredMethod(
                    "setWiredDeviceConnectionState",
                    int.class, int.class, String.class, String.class);
            m.setAccessible(true);
            m.invoke(audioManager, device, state, "", name);
            return true;
        } catch (Throwable first) {
            try {
                Method old = AudioManager.class.getDeclaredMethod(
                        "setWiredDeviceConnectionState",
                        int.class, int.class, String.class);
                old.setAccessible(true);
                old.invoke(audioManager, device, state, name);
                return true;
            } catch (Throwable second) {
                details.add(shortError(second));
                return false;
            }
        }
    }

    private String forceSpeaker(boolean on) {
        try {
            Class<?> c = Class.forName("android.media.AudioSystem");
            Method m = c.getDeclaredMethod("setForceUse", int.class, int.class);
            m.setAccessible(true);
            Object result = m.invoke(null, FOR_MEDIA, on ? FORCE_SPEAKER : FORCE_NONE);

            try {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(on);
            } catch (Throwable ignored) {}

            return "εντολή στάλθηκε" + (result == null ? "" : " (" + result + ")");
        } catch (Throwable e) {
            try {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(on);
                return "έγινε απλή δρομολόγηση Android";
            } catch (Throwable e2) {
                return "δεν επιτράπηκε: " + shortError(e);
            }
        }
    }

    private boolean openFactoryFm() {
        PackageManager pm = getPackageManager();

        String[] candidates = new String[] {
                "com.android.fmradio",
                "com.mediatek.FMRadio",
                "com.mediatek.fmradio",
                "com.lenovo.fmradio"
        };

        for (String pkg : candidates) {
            if (launchPackage(pm, pkg)) {
                lastFmPackage = pkg;
                return true;
            }
        }

        try {
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo ai : apps) {
                String pkg = ai.packageName == null ? "" : ai.packageName;
                String low = pkg.toLowerCase();
                if (low.contains("fmradio") || low.contains("fm.radio")) {
                    if (launchPackage(pm, pkg)) {
                        lastFmPackage = pkg;
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private boolean launchPackage(PackageManager pm, String pkg) {
        try {
            Intent i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private void deviceCheck() {
        boolean wired = false;
        try {
            wired = audioManager.isWiredHeadsetOn();
        } catch (Throwable ignored) {}

        String fm = findFmPackage();
        StringBuilder sb = new StringBuilder();
        sb.append("Καλώδιο/ακουστικό που βλέπει το Android: ")
                .append(wired ? "ΝΑΙ" : "ΟΧΙ")
                .append("\n");
        sb.append("Εργοστασιακό FM: ")
                .append(fm == null ? "δεν εντοπίστηκε" : fm)
                .append("\n");
        sb.append("Android: ").append(android.os.Build.VERSION.RELEASE)
                .append(" / API ").append(android.os.Build.VERSION.SDK_INT)
                .append("\n");
        sb.append("Συσκευή: ").append(android.os.Build.MANUFACTURER)
                .append(" ").append(android.os.Build.MODEL);
        refreshStatus(sb.toString());
    }

    private String findFmPackage() {
        PackageManager pm = getPackageManager();
        String[] candidates = new String[] {
                "com.android.fmradio",
                "com.mediatek.FMRadio",
                "com.mediatek.fmradio",
                "com.lenovo.fmradio"
        };
        for (String pkg : candidates) {
            try {
                if (pm.getLaunchIntentForPackage(pkg) != null) return pkg;
            } catch (Throwable ignored) {}
        }

        try {
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                String p = ai.packageName == null ? "" : ai.packageName;
                String low = p.toLowerCase();
                if ((low.contains("fmradio") || low.contains("fm.radio"))
                        && pm.getLaunchIntentForPackage(p) != null) {
                    return p;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void resetAudio() {
        new Thread(() -> {
            String a = setVirtualHeadset(false);
            String b = forceSpeaker(false);
            runOnUiThread(() -> refreshStatus("Επαναφορά:\n" + a + "\nΗχείο: " + b));
        }).start();
    }

    private void refreshStatus(String text) {
        if (status != null) status.setText(text);
    }

    private String shortError(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        String s = t.getClass().getSimpleName();
        if (t.getMessage() != null && !t.getMessage().isEmpty()) {
            s += ": " + t.getMessage();
        }
        if (s.length() > 140) s = s.substring(0, 140);
        return s;
    }

    private String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
