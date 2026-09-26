package com.costas.builder;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        buildUi();
        setStatus("Έτοιμο. Πάτησε «ΔΟΚΙΜΗ ΧΩΡΙΣ ΑΚΟΥΣΤΙΚΑ».");
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
        root.addView(title, lp(8));

        TextView intro = new TextView(this);
        intro.setText("Δοκιμάζει να κάνει το Android να νομίζει ότι έχει συνδεθεί ακουστικό και μετά ανοίγει το εργοστασιακό FM του Lenovo.");
        intro.setTextSize(18);
        intro.setPadding(0, dp(8), 0, dp(14));
        root.addView(intro, lp(8));

        Button test = button("ΔΟΚΙΜΗ ΧΩΡΙΣ ΑΚΟΥΣΤΙΚΑ");
        test.setOnClickListener(v -> unlockAndOpen());
        root.addView(test, lp(10));

        Button open = button("ΑΝΟΙΞΕ ΤΟ ΕΡΓΟΣΤΑΣΙΑΚΟ FM");
        open.setOnClickListener(v -> setStatus(openFactoryFm()
                ? "Άνοιξα το εργοστασιακό FM."
                : "Δεν βρήκα εφαρμογή FM στη συσκευή."));
        root.addView(open, lp(10));

        Button speaker = button("ΗΧΟΣ ΣΤΟ ΗΧΕΙΟ");
        speaker.setOnClickListener(v -> setStatus("Ηχείο: " + forceSpeaker(true)));
        root.addView(speaker, lp(10));

        Button check = button("ΕΛΕΓΧΟΣ");
        check.setOnClickListener(v -> doCheck());
        root.addView(check, lp(10));

        Button reset = button("ΕΠΑΝΑΦΟΡΑ ΗΧΟΥ");
        reset.setOnClickListener(v -> resetAudio());
        root.addView(reset, lp(10));

        status = new TextView(this);
        status.setTextSize(17);
        status.setPadding(dp(12), dp(14), dp(12), dp(14));
        status.setTextIsSelectable(true);
        root.addView(status, lp(8));

        TextView note = new TextView(this);
        note.setText("Αν χαθεί ο ήχος από άλλες εφαρμογές, πάτησε «ΕΠΑΝΑΦΟΡΑ ΗΧΟΥ». Δεν γίνεται μόνιμη αλλαγή στο Lenovo.");
        note.setTextSize(15);
        root.addView(note, lp(0));

        setContentView(scroll);
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setMinHeight(dp(58));
        return b;
    }

    private LinearLayout.LayoutParams lp(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = bottom;
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void unlockAndOpen() {
        setStatus("Γίνεται δοκιμή…");

        new Thread(() -> {
            String virtual = setVirtualHeadset(true);
            String speaker = forceSpeaker(true);

            runOnUiThread(() -> {
                boolean opened = openFactoryFm();
                setStatus("Εικονικό ακουστικό: " + virtual
                        + "\nΗχείο: " + speaker
                        + "\nFM: " + (opened ? "ΑΝΟΙΞΕ" : "ΔΕΝ ΒΡΕΘΗΚΕ"));
                if (opened) {
                    new Handler().postDelayed(() -> forceSpeaker(true), 1200);
                }
            });
        }).start();
    }

    private String setVirtualHeadset(boolean connected) {
        int state = connected ? 1 : 0;
        List<String> errors = new ArrayList<>();

        boolean ok = false;
        ok |= invokeWiredState(DEVICE_OUT_WIRED_HEADPHONE, state, errors);
        ok |= invokeWiredState(DEVICE_OUT_WIRED_HEADSET, state, errors);
        ok |= invokeWiredState(DEVICE_IN_WIRED_HEADSET, state, errors);

        boolean seen = false;
        try {
            seen = audioManager.isWiredHeadsetOn();
        } catch (Throwable ignored) {}

        if (ok) {
            if (connected) {
                return "εντολή στάλθηκε — το Android λέει "
                        + (seen ? "ΝΑΙ" : "ΟΧΙ");
            }
            return "έγινε επαναφορά";
        }

        return "μπλοκαρίστηκε από το Android"
                + (errors.isEmpty() ? "" : " — " + errors.get(0));
    }

    private boolean invokeWiredState(int device, int state, List<String> errors) {
        try {
            Method m = AudioManager.class.getDeclaredMethod(
                    "setWiredDeviceConnectionState",
                    int.class, int.class, String.class, String.class);
            m.setAccessible(true);
            m.invoke(audioManager, device, state, "", "FM-Virtual");
            return true;
        } catch (Throwable first) {
            try {
                Method old = AudioManager.class.getDeclaredMethod(
                        "setWiredDeviceConnectionState",
                        int.class, int.class, String.class);
                old.setAccessible(true);
                old.invoke(audioManager, device, state, "FM-Virtual");
                return true;
            } catch (Throwable second) {
                errors.add(shortError(second));
                return false;
            }
        }
    }

    private String forceSpeaker(boolean on) {
        try {
            Class<?> audioSystem = Class.forName("android.media.AudioSystem");
            Method m = audioSystem.getDeclaredMethod("setForceUse", int.class, int.class);
            m.setAccessible(true);
            m.invoke(null, FOR_MEDIA, on ? FORCE_SPEAKER : FORCE_NONE);

            try {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(on);
            } catch (Throwable ignored) {}

            return "εντολή στάλθηκε";
        } catch (Throwable e) {
            try {
                audioManager.setMode(AudioManager.MODE_NORMAL);
                audioManager.setSpeakerphoneOn(on);
                return "έγινε απλή δρομολόγηση";
            } catch (Throwable ignored) {
                return "μπλοκαρίστηκε — " + shortError(e);
            }
        }
    }

    private boolean openFactoryFm() {
        PackageManager pm = getPackageManager();

        String[] candidates = {
                "com.android.fmradio",
                "com.mediatek.FMRadio",
                "com.mediatek.fmradio",
                "com.lenovo.fmradio"
        };

        for (String pkg : candidates) {
            if (launch(pm, pkg)) return true;
        }

        try {
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                String p = ai.packageName == null ? "" : ai.packageName;
                String low = p.toLowerCase();
                if ((low.contains("fmradio") || low.contains("fm.radio")) && launch(pm, p)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    private boolean launch(PackageManager pm, String pkg) {
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

    private String findFmPackage() {
        PackageManager pm = getPackageManager();
        String[] candidates = {
                "com.android.fmradio",
                "com.mediatek.FMRadio",
                "com.mediatek.fmradio",
                "com.lenovo.fmradio"
        };

        for (String p : candidates) {
            try {
                if (pm.getLaunchIntentForPackage(p) != null) return p;
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

    private void doCheck() {
        boolean wired = false;
        try {
            wired = audioManager.isWiredHeadsetOn();
        } catch (Throwable ignored) {}

        String fm = findFmPackage();
        setStatus("Android βλέπει ακουστικό: " + (wired ? "ΝΑΙ" : "ΟΧΙ")
                + "\nFM εφαρμογή: " + (fm == null ? "δεν βρέθηκε" : fm)
                + "\nAndroid: " + android.os.Build.VERSION.RELEASE
                + " / API " + android.os.Build.VERSION.SDK_INT
                + "\nΣυσκευή: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
    }

    private void resetAudio() {
        new Thread(() -> {
            String a = setVirtualHeadset(false);
            String b = forceSpeaker(false);
            runOnUiThread(() -> setStatus("Επαναφορά:\n" + a + "\nΗχείο: " + b));
        }).start();
    }

    private String shortError(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        String s = t.getClass().getSimpleName();
        if (t.getMessage() != null && !t.getMessage().isEmpty()) {
            s += ": " + t.getMessage();
        }
        if (s.length() > 120) s = s.substring(0, 120);
        return s;
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }
}
