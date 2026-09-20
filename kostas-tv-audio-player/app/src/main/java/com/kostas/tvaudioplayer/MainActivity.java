package com.kostas.tvaudioplayer;

import android.app.Activity;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS = "kostas_tv_audio_v2";
    private static final String KEY_ITEMS = "saved_items";
    private static final int PICK_AUDIO = 77;

    private final List<SavedItem> favorites = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private EditText searchInput;
    private EditText directNameInput;
    private EditText directUrlInput;
    private TextView status;
    private LinearLayout resultsBox;
    private LinearLayout favoritesBox;

    private MediaPlayer mediaPlayer;
    private String audiusBase = "https://api.audius.co/v1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(10, 14, 20));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(30), dp(24), dp(30), dp(40));

        TextView title = text("KOSTAS AUDIO SEARCH", 30, Color.WHITE);
        title.setTypeface(null, 1);
        page.addView(title);

        TextView subtitle = text(
                "Μόνο ήχος • χωρίς YouTube • Android TV 12",
                17, Color.rgb(180, 190, 205));
        subtitle.setPadding(0, dp(4), 0, dp(18));
        page.addView(subtitle);

        searchInput = edit("Γράψε τραγούδι ή καλλιτέχνη");
        page.addView(searchInput, fullHeight(dp(60)));

        LinearLayout searchRow = row();
        Button songsButton = button("🔎 ΤΡΑΓΟΥΔΙΑ");
        Button radioButton = button("📻 ΡΑΔΙΟΦΩΝΑ");
        searchRow.addView(songsButton, weightButton());
        searchRow.addView(spacer(dp(12)));
        searchRow.addView(radioButton, weightButton());
        page.addView(searchRow, top(dp(12), dp(62)));

        status = text("Έτοιμο για αναζήτηση.", 18, Color.rgb(93, 225, 174));
        status.setPadding(0, dp(16), 0, dp(12));
        page.addView(status);

        TextView resultTitle = text("ΑΠΟΤΕΛΕΣΜΑΤΑ", 22, Color.WHITE);
        resultTitle.setTypeface(null, 1);
        page.addView(resultTitle);

        resultsBox = new LinearLayout(this);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(resultsBox);

        TextView directTitle = text("DIRECT AUDIO", 22, Color.WHITE);
        directTitle.setTypeface(null, 1);
        directTitle.setPadding(0, dp(24), 0, dp(8));
        page.addView(directTitle);

        directNameInput = edit("Όνομα, π.χ. My Stream");
        directUrlInput = edit("Direct MP3 / AAC / stream URL");
        page.addView(directNameInput, fullHeight(dp(58)));
        page.addView(directUrlInput, top(dp(10), dp(58)));

        LinearLayout directRow = row();
        Button directPlay = button("▶ PLAY URL");
        Button directSave = button("★ SAVE");
        Button localFile = button("📁 ΑΡΧΕΙΟ");
        directRow.addView(directPlay, weightButton());
        directRow.addView(spacer(dp(10)));
        directRow.addView(directSave, weightButton());
        directRow.addView(spacer(dp(10)));
        directRow.addView(localFile, weightButton());
        page.addView(directRow, top(dp(12), dp(62)));

        LinearLayout transportRow = row();
        Button pause = button("⏯ PLAY / PAUSE");
        Button stop = button("■ STOP");
        transportRow.addView(pause, weightButton());
        transportRow.addView(spacer(dp(12)));
        transportRow.addView(stop, weightButton());
        page.addView(transportRow, top(dp(16), dp(62)));

        TextView favTitle = text("ΑΓΑΠΗΜΕΝΑ", 22, Color.WHITE);
        favTitle.setTypeface(null, 1);
        favTitle.setPadding(0, dp(24), 0, dp(8));
        page.addView(favTitle);

        favoritesBox = new LinearLayout(this);
        favoritesBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(favoritesBox);

        scroll.addView(page);
        setContentView(scroll);

        loadFavorites();
        renderFavorites();

        songsButton.setOnClickListener(v -> searchAudius());
        radioButton.setOnClickListener(v -> searchRadio());
        directPlay.setOnClickListener(v -> playDirectInput());
        directSave.setOnClickListener(v -> saveDirectInput());
        localFile.setOnClickListener(v -> pickLocalAudio());
        pause.setOnClickListener(v -> togglePlayPause());
        stop.setOnClickListener(v -> stopPlayback());

        songsButton.requestFocus();
    }

    private void searchAudius() {
        final String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            toast("Γράψε τραγούδι ή καλλιτέχνη.");
            return;
        }

        status.setText("Αναζήτηση τραγουδιών…");
        resultsBox.removeAllViews();

        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(q, "UTF-8");
                String[] bases = new String[]{
                        "https://api.audius.co/v1",
                        "https://discoveryprovider.audius.co/v1"
                };

                JSONObject root = null;
                String workingBase = null;
                Exception last = null;

                for (String base : bases) {
                    try {
                        String json = readUrl(base + "/tracks/search?query=" + encoded
                                + "&limit=25&app_name=KostasTVAudio");
                        root = new JSONObject(json);
                        workingBase = base;
                        break;
                    } catch (Exception e) {
                        last = e;
                    }
                }

                if (root == null || workingBase == null) {
                    throw last != null ? last : new Exception("Audius unavailable");
                }

                JSONArray data = root.optJSONArray("data");
                final List<SearchItem> found = new ArrayList<>();

                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject track = data.optJSONObject(i);
                        if (track == null) continue;

                        String id = track.optString("id", "");
                        String trackTitle = track.optString("title", "Χωρίς τίτλο");
                        JSONObject user = track.optJSONObject("user");
                        String artist = user != null ? user.optString("name", "Άγνωστος") : "Άγνωστος";

                        if (!id.isEmpty()) {
                            found.add(new SearchItem(
                                    trackTitle,
                                    artist + " • Audius",
                                    workingBase + "/tracks/" + id
                                            + "/stream?app_name=KostasTVAudio",
                                    "Audius"
                            ));
                        }
                    }
                }

                audiusBase = workingBase;
                runOnUiThread(() -> showSearchResults(found, "Audius"));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Η αναζήτηση Audius δεν απάντησε. Δοκίμασε ξανά ή Direct URL.");
                    toast("Σφάλμα online αναζήτησης.");
                });
            }
        });
    }

    private void searchRadio() {
        final String q = searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            toast("Γράψε όνομα σταθμού, χώρα ή είδος.");
            return;
        }

        status.setText("Αναζήτηση ραδιοφώνων…");
        resultsBox.removeAllViews();

        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(q, "UTF-8");
                String endpoint =
                        "https://all.api.radio-browser.info/json/stations/search"
                        + "?name=" + encoded
                        + "&hidebroken=true&limit=30&order=clickcount&reverse=true";

                JSONArray arr = new JSONArray(readUrl(endpoint));
                final List<SearchItem> found = new ArrayList<>();

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject st = arr.optJSONObject(i);
                    if (st == null) continue;

                    String name = st.optString("name", "Radio");
                    String url = st.optString("url_resolved", st.optString("url", ""));
                    String country = st.optString("country", "");
                    String codec = st.optString("codec", "");
                    String tags = st.optString("tags", "");

                    if (!url.isEmpty()) {
                        String info = country;
                        if (!codec.isEmpty()) info += (info.isEmpty() ? "" : " • ") + codec;
                        if (!tags.isEmpty()) {
                            String shortTags = tags.length() > 55 ? tags.substring(0, 55) + "…" : tags;
                            info += (info.isEmpty() ? "" : " • ") + shortTags;
                        }

                        found.add(new SearchItem(name, info, url, "Radio"));
                    }
                }

                runOnUiThread(() -> showSearchResults(found, "Radio Browser"));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Η αναζήτηση ραδιοφώνων δεν απάντησε.");
                    toast("Σφάλμα online αναζήτησης.");
                });
            }
        });
    }

    private void showSearchResults(List<SearchItem> found, String source) {
        resultsBox.removeAllViews();

        if (found.isEmpty()) {
            status.setText("Δεν βρέθηκαν διαθέσιμα αποτελέσματα.");
            return;
        }

        status.setText("Βρέθηκαν " + found.size() + " αποτελέσματα • " + source);

        for (SearchItem item : found) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(6), 0, dp(6));

            TextView name = text(item.title, 19, Color.WHITE);
            name.setTypeface(null, 1);
            card.addView(name);

            if (!item.subtitle.isEmpty()) {
                TextView info = text(item.subtitle, 15, Color.rgb(165, 177, 194));
                info.setPadding(0, dp(2), 0, dp(7));
                card.addView(info);
            }

            LinearLayout actions = row();
            Button play = button("▶ PLAY");
            Button save = button("★ SAVE");
            actions.addView(play, weightButton());
            actions.addView(spacer(dp(10)));
            actions.addView(save, weightButton());
            card.addView(actions, fullHeight(dp(60)));

            play.setOnClickListener(v -> playAudio(item.url, item.title));
            save.setOnClickListener(v -> {
                addFavorite(item.title, item.url, item.source);
                status.setText("★ Αποθηκεύτηκε: " + item.title);
            });

            resultsBox.addView(card);
        }
    }

    private void playDirectInput() {
        String url = directUrlInput.getText().toString().trim();
        if (url.isEmpty()) {
            toast("Βάλε direct audio URL.");
            return;
        }
        String name = directNameInput.getText().toString().trim();
        if (name.isEmpty()) name = "Direct Audio";
        playAudio(url, name);
    }

    private void saveDirectInput() {
        String url = directUrlInput.getText().toString().trim();
        if (url.isEmpty()) {
            toast("Βάλε direct audio URL.");
            return;
        }
        String name = directNameInput.getText().toString().trim();
        if (name.isEmpty()) name = "Direct Audio";

        addFavorite(name, url, "Direct");
        status.setText("★ Αποθηκεύτηκε: " + name);
    }

    private void pickLocalAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            try {
                final int takeFlags = data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (Exception ignored) {
            }

            String name = uri.getLastPathSegment();
            if (name == null || name.trim().isEmpty()) name = "Τοπικό αρχείο";

            addFavorite(name, uri.toString(), "Local");
            playAudio(uri.toString(), name);
        }
    }

    private void playAudio(String url, String label) {
        releasePlayer();
        status.setText("Σύνδεση: " + label + "…");

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);

        mediaPlayer.setOnPreparedListener(mp -> {
            mp.start();
            status.setText("▶ Παίζει: " + label);
        });

        mediaPlayer.setOnCompletionListener(mp ->
                status.setText("Ολοκληρώθηκε: " + label));

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            status.setText("Δεν μπόρεσε να παίξει αυτή η πηγή.");
            releasePlayer();
            return true;
        });

        try {
            if (url.startsWith("content://")) {
                mediaPlayer.setDataSource(this, Uri.parse(url));
            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                mediaPlayer.setDataSource(url);
            } else {
                status.setText("Μη έγκυρη πηγή ήχου.");
                releasePlayer();
                return;
            }
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            status.setText("Σφάλμα κατά το άνοιγμα του ήχου.");
            releasePlayer();
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            toast("Δεν παίζει κάτι.");
            return;
        }

        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                status.setText("⏸ Παύση.");
            } else {
                mediaPlayer.start();
                status.setText("▶ Συνέχεια.");
            }
        } catch (Exception e) {
            status.setText("Δεν ήταν δυνατή η παύση/συνέχεια.");
        }
    }

    private void stopPlayback() {
        releasePlayer();
        status.setText("■ Σταμάτησε.");
    }

    private void addFavorite(String name, String url, String source) {
        for (SavedItem item : favorites) {
            if (item.url.equals(url)) {
                toast("Υπάρχει ήδη στα αγαπημένα.");
                return;
            }
        }

        favorites.add(0, new SavedItem(name, url, source));
        persistFavorites();
        renderFavorites();
    }

    private void renderFavorites() {
        favoritesBox.removeAllViews();

        if (favorites.isEmpty()) {
            favoritesBox.addView(text(
                    "Δεν έχεις αποθηκευμένα ακόμα.",
                    16, Color.rgb(155, 167, 184)));
            return;
        }

        for (SavedItem item : new ArrayList<>(favorites)) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, dp(5), 0, dp(5));

            TextView name = text(item.name, 18, Color.WHITE);
            name.setTypeface(null, 1);
            card.addView(name);

            TextView source = text(item.source, 14, Color.rgb(155, 167, 184));
            source.setPadding(0, dp(2), 0, dp(6));
            card.addView(source);

            LinearLayout actions = row();
            Button play = button("▶ PLAY");
            Button delete = button("✕ ΔΙΑΓΡΑΦΗ");
            actions.addView(play, weightButton());
            actions.addView(spacer(dp(10)));
            actions.addView(delete, weightButton());
            card.addView(actions, fullHeight(dp(60)));

            play.setOnClickListener(v -> playAudio(item.url, item.name));
            delete.setOnClickListener(v -> {
                favorites.remove(item);
                persistFavorites();
                renderFavorites();
                status.setText("Διαγράφηκε: " + item.name);
            });

            favoritesBox.addView(card);
        }
    }

    private void loadFavorites() {
        favorites.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]");

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                String name = o.optString("name", "Audio");
                String url = o.optString("url", "");
                String source = o.optString("source", "Saved");

                if (!url.isEmpty()) {
                    favorites.add(new SavedItem(name, url, source));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void persistFavorites() {
        JSONArray arr = new JSONArray();

        try {
            for (SavedItem item : favorites) {
                JSONObject o = new JSONObject();
                o.put("name", item.name);
                o.put("url", item.url);
                o.put("source", item.source);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_ITEMS, arr.toString())
                .apply();
    }

    private String readUrl(String address) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "KostasTVAudio/2.0 AndroidTV");
            connection.setRequestProperty("Accept", "application/json");
            connection.connect();

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }

            reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), "UTF-8"));

            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
            return out.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (connection != null) {
                connection.disconnect();
            }
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            togglePlayPause();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        executor.shutdownNow();
        super.onDestroy();
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setPadding(dp(14), dp(7), dp(14), dp(7));
        b.setBackground(buttonDrawable(false));

        b.setOnFocusChangeListener((v, hasFocus) -> {
            b.setBackground(buttonDrawable(hasFocus));
            b.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
        });
        return b;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(125, 138, 155));
        e.setTextColor(Color.WHITE);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setPadding(dp(17), dp(10), dp(17), dp(10));
        e.setBackground(editDrawable(false));
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);

        e.setOnFocusChangeListener((v, hasFocus) ->
                e.setBackground(editDrawable(hasFocus)));
        return e;
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private View spacer(int width) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return v;
    }

    private LinearLayout.LayoutParams weightButton() {
        return new LinearLayout.LayoutParams(0, dp(60), 1f);
    }

    private LinearLayout.LayoutParams fullHeight(int height) {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams top(int marginTop, int height) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.topMargin = marginTop;
        return p;
    }

    private GradientDrawable buttonDrawable(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(focused ? Color.rgb(238, 202, 76) : Color.rgb(37, 49, 66));
        g.setCornerRadius(dp(10));
        g.setStroke(dp(focused ? 3 : 2),
                focused ? Color.WHITE : Color.rgb(73, 93, 119));
        return g;
    }

    private GradientDrawable editDrawable(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(20, 28, 38));
        g.setCornerRadius(dp(8));
        g.setStroke(dp(focused ? 3 : 1),
                focused ? Color.rgb(238, 202, 76) : Color.rgb(69, 83, 102));
        return g;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private static class SearchItem {
        final String title;
        final String subtitle;
        final String url;
        final String source;

        SearchItem(String title, String subtitle, String url, String source) {
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.source = source;
        }
    }

    private static class SavedItem {
        final String name;
        final String url;
        final String source;

        SavedItem(String name, String url, String source) {
            this.name = name;
            this.url = url;
            this.source = source;
        }
    }
}
