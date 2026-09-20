package com.kostas.tvaudioplayer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String PREFS = "kostas_tv_audio";
    private static final String KEY_ITEMS = "saved_items";

    private final List<SavedItem> items = new ArrayList<>();
    private LinearLayout library;
    private EditText titleInput;
    private EditText valueInput;
    private TextView status;
    private MediaPlayer mediaPlayer;
    private boolean youtubeMode = false;

    private FrameLayout root;
    private LinearLayout videoPanel;
    private WebView youtubeWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(12, 16, 22));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(32), dp(26), dp(32), dp(40));

        TextView heading = text("KOSTAS TV AUDIO", 30, Color.WHITE);
        heading.setTypeface(null, 1);
        content.addView(heading);

        TextView sub = text("Direct MP3 + αποθήκευση συνδέσμων + YouTube Video ID", 17, Color.rgb(180, 190, 205));
        sub.setPadding(0, dp(6), 0, dp(20));
        content.addView(sub);

        LinearLayout modeRow = horizontal();
        Button mp3Mode = button("MP3 DIRECT");
        Button ytMode = button("VIDEO ID");
        modeRow.addView(mp3Mode, weighted());
        modeRow.addView(space(), new LinearLayout.LayoutParams(dp(14), 1));
        modeRow.addView(ytMode, weighted());
        content.addView(modeRow);

        titleInput = edit("Όνομα, π.χ. Rock Radio");
        valueInput = edit("Direct MP3 URL");
        titleInput.setSingleLine(true);
        valueInput.setSingleLine(true);
        content.addView(titleInput, topMargin(dp(18)));
        content.addView(valueInput, topMargin(dp(12)));

        LinearLayout actionRow = horizontal();
        Button play = button("▶ PLAY");
        Button save = button("★ SAVE");
        Button stop = button("■ STOP");
        actionRow.addView(play, weighted());
        actionRow.addView(space(), new LinearLayout.LayoutParams(dp(10), 1));
        actionRow.addView(save, weighted());
        actionRow.addView(space(), new LinearLayout.LayoutParams(dp(10), 1));
        actionRow.addView(stop, weighted());
        content.addView(actionRow, topMargin(dp(16)));

        status = text("Έτοιμο.", 18, Color.rgb(95, 220, 170));
        status.setPadding(0, dp(16), 0, dp(18));
        content.addView(status);

        TextView libraryTitle = text("ΑΠΟΘΗΚΕΥΜΕΝΑ", 23, Color.WHITE);
        libraryTitle.setTypeface(null, 1);
        content.addView(libraryTitle);

        library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);
        library.setPadding(0, dp(10), 0, 0);
        content.addView(library);

        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildVideoPanel();

        setContentView(root);
        loadItems();
        renderLibrary();
        setMode(false);

        mp3Mode.setOnClickListener(v -> setMode(false));
        ytMode.setOnClickListener(v -> setMode(true));
        play.setOnClickListener(v -> playCurrent());
        save.setOnClickListener(v -> saveCurrent());
        stop.setOnClickListener(v -> stopEverything());

        mp3Mode.requestFocus();
    }

    private void buildVideoPanel() {
        videoPanel = new LinearLayout(this);
        videoPanel.setOrientation(LinearLayout.VERTICAL);
        videoPanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        videoPanel.setBackgroundColor(Color.rgb(0, 0, 0));
        videoPanel.setVisibility(View.GONE);

        Button close = button("← ΚΛΕΙΣΙΜΟ");
        close.setOnClickListener(v -> closeVideo());
        videoPanel.addView(close, new LinearLayout.LayoutParams(dp(220), dp(58)));

        youtubeWebView = new WebView(this);
        WebSettings s = youtubeWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        youtubeWebView.setWebViewClient(new WebViewClient());
        youtubeWebView.setWebChromeClient(new WebChromeClient());
        youtubeWebView.setBackgroundColor(Color.BLACK);

        LinearLayout.LayoutParams webParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webParams.topMargin = dp(12);
        videoPanel.addView(youtubeWebView, webParams);

        root.addView(videoPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        close.setNextFocusDownId(youtubeWebView.getId());
    }

    private void setMode(boolean youtube) {
        youtubeMode = youtube;
        if (youtube) {
            valueInput.setHint("YouTube Video ID ή URL");
            status.setText("Video ID: παίζει σε ενσωματωμένο επίσημο YouTube player.");
        } else {
            valueInput.setHint("Direct MP3/AAC URL");
            status.setText("MP3 Direct: ο ήχος παίζει μέσα στην εφαρμογή.");
        }
    }

    private void playCurrent() {
        String value = valueInput.getText().toString().trim();
        if (value.isEmpty()) {
            toast("Βάλε σύνδεσμο ή Video ID.");
            return;
        }
        if (youtubeMode) {
            playYoutube(value);
        } else {
            playDirect(value);
        }
    }

    private void playDirect(String url) {
        closeVideo();
        releasePlayer();

        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
            status.setText("Χρειάζεται πραγματικό http/https direct audio URL.");
            return;
        }

        status.setText("Σύνδεση στο audio…");
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

        mediaPlayer.setOnPreparedListener(mp -> {
            mp.start();
            status.setText("▶ Παίζει μέσα στην εφαρμογή.");
        });

        mediaPlayer.setOnCompletionListener(mp ->
                status.setText("Ολοκληρώθηκε."));

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            status.setText("Δεν άνοιξε. Ο σύνδεσμος πρέπει να είναι πραγματικό direct MP3/AAC/stream.");
            releasePlayer();
            return true;
        });

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            status.setText("Σφάλμα συνδέσμου.");
            releasePlayer();
        }
    }

    private void playYoutube(String raw) {
        releasePlayer();
        String id = sanitizeYoutubeId(raw);
        if (id.isEmpty()) {
            status.setText("Μη έγκυρο YouTube Video ID/URL.");
            return;
        }

        String url = "https://www.youtube.com/embed/" + id +
                "?autoplay=1&playsinline=1&rel=0&controls=1";
        videoPanel.setVisibility(View.VISIBLE);
        youtubeWebView.loadUrl(url);
        status.setText("YouTube video μέσα στην εφαρμογή.");
        videoPanel.getChildAt(0).requestFocus();
    }

    private String sanitizeYoutubeId(String raw) {
        String v = raw.trim();

        if (v.matches("[A-Za-z0-9_-]{11}")) {
            return v;
        }

        try {
            Uri uri = Uri.parse(v);
            String host = uri.getHost();

            if (host != null && host.contains("youtu.be")) {
                String p = uri.getLastPathSegment();
                if (p != null && p.matches("[A-Za-z0-9_-]{11}")) return p;
            }

            String q = uri.getQueryParameter("v");
            if (q != null && q.matches("[A-Za-z0-9_-]{11}")) return q;

            List<String> seg = uri.getPathSegments();
            for (int i = 0; i < seg.size() - 1; i++) {
                if ("embed".equals(seg.get(i)) || "shorts".equals(seg.get(i))) {
                    String candidate = seg.get(i + 1);
                    if (candidate.matches("[A-Za-z0-9_-]{11}")) return candidate;
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private void saveCurrent() {
        String name = titleInput.getText().toString().trim();
        String value = valueInput.getText().toString().trim();

        if (value.isEmpty()) {
            toast("Δεν υπάρχει σύνδεσμος.");
            return;
        }

        if (name.isEmpty()) {
            name = youtubeMode ? "YouTube Video" : "Direct Audio";
        }

        items.add(0, new SavedItem(name, value, youtubeMode ? "youtube" : "audio"));
        persistItems();
        renderLibrary();
        status.setText("★ Αποθηκεύτηκε στη συσκευή.");
    }

    private void loadItems() {
        items.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.add(new SavedItem(
                        o.optString("name", "Χωρίς όνομα"),
                        o.optString("value", ""),
                        o.optString("type", "audio")
                ));
            }
        } catch (Exception ignored) {
        }
    }

    private void persistItems() {
        JSONArray arr = new JSONArray();
        try {
            for (SavedItem item : items) {
                JSONObject o = new JSONObject();
                o.put("name", item.name);
                o.put("value", item.value);
                o.put("type", item.type);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, arr.toString())
                .apply();
    }

    private void renderLibrary() {
        library.removeAllViews();

        if (items.isEmpty()) {
            TextView empty = text("Δεν έχεις αποθηκευμένους συνδέσμους.", 17,
                    Color.rgb(160, 170, 185));
            library.addView(empty);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            int index = i;
            SavedItem item = items.get(i);

            LinearLayout row = horizontal();
            row.setPadding(0, dp(5), 0, dp(5));

            String prefix = "youtube".equals(item.type) ? "▶ VIDEO • " : "▶ AUDIO • ";
            Button open = button(prefix + item.name);
            open.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            open.setOnClickListener(v -> {
                titleInput.setText(item.name);
                valueInput.setText(item.value);
                youtubeMode = "youtube".equals(item.type);
                if (youtubeMode) playYoutube(item.value);
                else playDirect(item.value);
            });

            Button delete = button("✕");
            delete.setOnClickListener(v -> {
                items.remove(index);
                persistItems();
                renderLibrary();
                status.setText("Διαγράφηκε.");
            });

            row.addView(open, new LinearLayout.LayoutParams(0, dp(62), 1f));
            LinearLayout.LayoutParams del = new LinearLayout.LayoutParams(dp(82), dp(62));
            del.leftMargin = dp(10);
            row.addView(delete, del);

            library.addView(row);
        }
    }

    private void stopEverything() {
        releasePlayer();
        closeVideo();
        status.setText("■ Σταμάτησε.");
    }

    private void closeVideo() {
        if (youtubeWebView != null) {
            youtubeWebView.loadUrl("about:blank");
        }
        if (videoPanel != null) {
            videoPanel.setVisibility(View.GONE);
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {
            }
            try {
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setPadding(dp(16), dp(8), dp(16), dp(8));
        b.setBackground(buttonDrawable());
        return b;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(125, 137, 154));
        e.setTextColor(Color.WHITE);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setPadding(dp(18), dp(12), dp(18), dp(12));
        e.setBackground(editDrawable());
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        return e;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private View space() {
        return new View(this);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, dp(62), 1f);
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        p.topMargin = margin;
        return p;
    }

    private GradientDrawable buttonDrawable() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(37, 48, 64));
        g.setCornerRadius(dp(10));
        g.setStroke(dp(2), Color.rgb(72, 91, 116));
        return g;
    }

    private GradientDrawable editDrawable() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(22, 29, 39));
        g.setCornerRadius(dp(8));
        g.setStroke(dp(1), Color.rgb(70, 83, 101));
        return g;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN &&
                event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE &&
                mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    status.setText("⏸ Παύση.");
                } else {
                    mediaPlayer.start();
                    status.setText("▶ Συνέχεια.");
                }
                return true;
            } catch (Exception ignored) {
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (videoPanel != null && videoPanel.getVisibility() == View.VISIBLE) {
            closeVideo();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        if (youtubeWebView != null) {
            youtubeWebView.destroy();
        }
        super.onDestroy();
    }

    private static class SavedItem {
        final String name;
        final String value;
        final String type;

        SavedItem(String name, String value, String type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }
    }
}
