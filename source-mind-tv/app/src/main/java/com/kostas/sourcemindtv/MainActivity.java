package com.kostas.sourcemindtv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int PICK_SOURCE = 91;
    private static final String PREFS = "source_mind_tv_v1";
    private static final String KEY_SOURCES = "sources_json";
    private static final int MAX_SOURCES = 12;
    private static final int MAX_CHARS_PER_SOURCE = 140000;

    private final List<SourceDoc> sources = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LinearLayout sourcesBox;
    private TextView status;
    private TextView output;
    private EditText questionInput;
    private TextToSpeech tts;

    private final Set<String> stopWords = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PDFBoxResourceLoader.init(getApplicationContext());
        setupStopWords();
        tts = new TextToSpeech(this, this);
        loadSources();
        buildUi();
        renderSources();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 13, 20));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(34), dp(24), dp(34), dp(42));

        TextView title = text("SOURCE MIND TV", 31, Color.WHITE);
        title.setTypeface(null, 1);
        page.addView(title);

        TextView subtitle = text(
                "Πηγές → περίληψη → ερωτήσεις → σημειώσεις • σχεδιασμένο για Android TV 12",
                17, Color.rgb(177, 189, 205));
        subtitle.setPadding(0, dp(4), 0, dp(16));
        page.addView(subtitle);

        LinearLayout addRow = row();
        Button addFile = button("+ ΑΡΧΕΙΟ / PDF");
        Button addText = button("+ ΚΕΙΜΕΝΟ");
        Button addUrl = button("+ URL");
        addRow.addView(addFile, weightButton());
        addRow.addView(spacer(dp(10)));
        addRow.addView(addText, weightButton());
        addRow.addView(spacer(dp(10)));
        addRow.addView(addUrl, weightButton());
        page.addView(addRow, fullHeight(dp(62)));

        LinearLayout actionRow = row();
        Button summary = button("ΠΕΡΙΛΗΨΗ");
        Button study = button("ΟΔΗΓΟΣ ΜΕΛΕΤΗΣ");
        Button speak = button("🔊 ΑΚΡΟΑΣΗ");
        actionRow.addView(summary, weightButton());
        actionRow.addView(spacer(dp(10)));
        actionRow.addView(study, weightButton());
        actionRow.addView(spacer(dp(10)));
        actionRow.addView(speak, weightButton());
        page.addView(actionRow, top(dp(12), dp(62)));

        status = text("Έτοιμο.", 17, Color.rgb(92, 222, 171));
        status.setPadding(0, dp(14), 0, dp(10));
        page.addView(status);

        TextView sourcesTitle = text("ΠΗΓΕΣ", 22, Color.WHITE);
        sourcesTitle.setTypeface(null, 1);
        page.addView(sourcesTitle);

        sourcesBox = new LinearLayout(this);
        sourcesBox.setOrientation(LinearLayout.VERTICAL);
        page.addView(sourcesBox);

        TextView askTitle = text("ΡΩΤΑ ΤΙΣ ΠΗΓΕΣ ΣΟΥ", 22, Color.WHITE);
        askTitle.setTypeface(null, 1);
        askTitle.setPadding(0, dp(22), 0, dp(8));
        page.addView(askTitle);

        questionInput = edit("Γράψε ερώτηση που απαντιέται από τις πηγές");
        page.addView(questionInput, fullHeight(dp(62)));

        Button ask = button("ΡΩΤΑ");
        page.addView(ask, top(dp(10), dp(62)));

        TextView outputTitle = text("ΣΗΜΕΙΩΜΑΤΑΡΙΟ / ΑΠΑΝΤΗΣΗ", 22, Color.WHITE);
        outputTitle.setTypeface(null, 1);
        outputTitle.setPadding(0, dp(24), 0, dp(8));
        page.addView(outputTitle);

        output = text(
                "Πρόσθεσε τουλάχιστον μία πηγή. Οι απαντήσεις βασίζονται μόνο σε ό,τι έχεις βάλει εσύ.",
                18, Color.rgb(225, 231, 239));
        output.setPadding(dp(18), dp(16), dp(18), dp(16));
        output.setBackground(panelDrawable());
        output.setMovementMethod(new ScrollingMovementMethod());
        output.setMinHeight(dp(210));
        page.addView(output);

        TextView privacy = text(
                "Βασική λειτουργία χωρίς API key. Τα κείμενα αποθηκεύονται τοπικά στη συσκευή.",
                14, Color.rgb(135, 149, 169));
        privacy.setPadding(0, dp(12), 0, 0);
        page.addView(privacy);

        scroll.addView(page);
        setContentView(scroll);

        addFile.setOnClickListener(v -> pickSource());
        addText.setOnClickListener(v -> showAddTextDialog());
        addUrl.setOnClickListener(v -> showAddUrlDialog());
        summary.setOnClickListener(v -> makeSummary());
        study.setOnClickListener(v -> makeStudyGuide());
        speak.setOnClickListener(v -> speakOutput());
        ask.setOnClickListener(v -> answerQuestion());

        addFile.requestFocus();
    }

    private void pickSource() {
        if (sources.size() >= MAX_SOURCES) {
            toast("Έφτασες το όριο των " + MAX_SOURCES + " πηγών.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "text/plain",
                "text/markdown",
                "text/html",
                "text/csv",
                "application/json",
                "application/xml"
        });
        startActivityForResult(intent, PICK_SOURCE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_SOURCE || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            final int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }

        final String name = getDisplayName(uri);
        status.setText("Διαβάζω: " + name + "…");

        executor.execute(() -> {
            try {
                String mime = getContentResolver().getType(uri);
                String content;
                if ("application/pdf".equals(mime) || name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    content = readPdf(uri);
                } else {
                    content = readText(uri);
                }
                content = cleanText(content);
                if (content.length() > MAX_CHARS_PER_SOURCE) {
                    content = content.substring(0, MAX_CHARS_PER_SOURCE);
                }
                final String finalContent = content;
                runOnUiThread(() -> addSource(name, finalContent, "Αρχείο"));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Δεν μπόρεσα να διαβάσω αυτό το αρχείο.");
                    toast("Δοκίμασε PDF ή αρχείο κειμένου.");
                });
            }
        });
    }

    private void showAddTextDialog() {
        if (sources.size() >= MAX_SOURCES) {
            toast("Έφτασες το όριο πηγών.");
            return;
        }

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        EditText title = new EditText(this);
        title.setHint("Τίτλος πηγής");
        title.setSingleLine(true);
        box.addView(title);

        EditText body = new EditText(this);
        body.setHint("Επικόλλησε εδώ το κείμενο");
        body.setMinLines(8);
        body.setGravity(Gravity.TOP);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(body);

        new AlertDialog.Builder(this)
                .setTitle("Νέα πηγή κειμένου")
                .setView(box)
                .setPositiveButton("ΠΡΟΣΘΗΚΗ", (d, w) -> {
                    String t = title.getText().toString().trim();
                    String b = cleanText(body.getText().toString());
                    if (t.isEmpty()) t = "Κείμενο " + (sources.size() + 1);
                    if (b.isEmpty()) {
                        toast("Το κείμενο είναι κενό.");
                    } else {
                        if (b.length() > MAX_CHARS_PER_SOURCE) b = b.substring(0, MAX_CHARS_PER_SOURCE);
                        addSource(t, b, "Κείμενο");
                    }
                })
                .setNegativeButton("ΑΚΥΡΟ", null)
                .show();
    }

    private void showAddUrlDialog() {
        if (sources.size() >= MAX_SOURCES) {
            toast("Έφτασες το όριο πηγών.");
            return;
        }

        EditText input = new EditText(this);
        input.setHint("https://…");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(this)
                .setTitle("Προσθήκη ιστοσελίδας")
                .setView(input)
                .setPositiveButton("ΦΟΡΤΩΣΗ", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("https://")) {
                        toast("Χρειάζεται σύνδεσμος https://");
                        return;
                    }
                    importUrl(url);
                })
                .setNegativeButton("ΑΚΥΡΟ", null)
                .show();
    }

    private void importUrl(String address) {
        status.setText("Φορτώνω ιστοσελίδα…");
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL url = new URL(address);
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 SourceMindTV/1.0");
                c.connect();
                if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) {
                    throw new Exception("HTTP " + c.getResponseCode());
                }
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null && sb.length() < MAX_CHARS_PER_SOURCE * 2) {
                    sb.append(line).append('\n');
                }
                br.close();

                String html = sb.toString()
                        .replaceAll("(?is)<script.*?>.*?</script>", " ")
                        .replaceAll("(?is)<style.*?>.*?</style>", " ")
                        .replaceAll("(?is)<[^>]+>", " ")
                        .replace("&nbsp;", " ")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">");

                String content = cleanText(html);
                if (content.length() > MAX_CHARS_PER_SOURCE) {
                    content = content.substring(0, MAX_CHARS_PER_SOURCE);
                }
                String host = url.getHost();
                final String finalContent = content;
                runOnUiThread(() -> addSource(host.isEmpty() ? address : host, finalContent, "URL"));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Η ιστοσελίδα δεν μπόρεσε να εισαχθεί.");
                    toast("Ορισμένες σελίδες μπλοκάρουν την ανάγνωση.");
                });
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private void addSource(String title, String content, String type) {
        if (content == null || content.trim().length() < 20) {
            status.setText("Η πηγή δεν είχε αρκετό αναγνώσιμο κείμενο.");
            return;
        }
        sources.add(new SourceDoc(title, content.trim(), type));
        persistSources();
        renderSources();
        status.setText("✓ Προστέθηκε: " + title);
        output.setText("Η πηγή «" + title + "» είναι έτοιμη. Πάτησε ΠΕΡΙΛΗΨΗ ή γράψε ερώτηση.");
    }

    private void renderSources() {
        if (sourcesBox == null) return;
        sourcesBox.removeAllViews();

        if (sources.isEmpty()) {
            TextView empty = text("Καμία πηγή ακόμη.", 16, Color.rgb(151, 164, 184));
            empty.setPadding(0, dp(8), 0, dp(8));
            sourcesBox.addView(empty);
            return;
        }

        for (int i = 0; i < sources.size(); i++) {
            final int index = i;
            SourceDoc s = sources.get(i);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(14), dp(8), dp(10), dp(8));
            card.setBackground(cardDrawable());

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);

            TextView name = text((i + 1) + ". " + s.title, 18, Color.WHITE);
            name.setTypeface(null, 1);
            info.addView(name);

            TextView meta = text(s.type + " • " + s.content.length() + " χαρακτήρες", 14, Color.rgb(151, 166, 187));
            info.addView(meta);

            card.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Button remove = button("ΔΙΑΓΡΑΦΗ");
            remove.setTextSize(14);
            card.addView(remove, new LinearLayout.LayoutParams(dp(160), dp(54)));
            remove.setOnClickListener(v -> {
                SourceDoc removed = sources.remove(index);
                persistSources();
                renderSources();
                status.setText("Διαγράφηκε: " + removed.title);
            });

            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(8);
            sourcesBox.addView(card, p);
        }
    }

    private void makeSummary() {
        if (!ensureSources()) return;
        String merged = mergedText();
        List<String> best = topSentences(merged, "", 6);
        if (best.isEmpty()) {
            output.setText("Δεν βρήκα αρκετές ολοκληρωμένες προτάσεις για περίληψη.");
            return;
        }

        StringBuilder sb = new StringBuilder("ΠΕΡΙΛΗΨΗ\n\n");
        for (String s : best) sb.append("• ").append(s.trim()).append("\n\n");

        List<String> keys = topKeywords(merged, 8);
        if (!keys.isEmpty()) {
            sb.append("ΚΥΡΙΕΣ ΕΝΝΟΙΕΣ\n");
            sb.append(join(keys, " • "));
        }
        output.setText(sb.toString().trim());
        status.setText("✓ Δημιουργήθηκε περίληψη από " + sources.size() + " πηγές.");
    }

    private void makeStudyGuide() {
        if (!ensureSources()) return;
        String merged = mergedText();
        List<String> points = topSentences(merged, "", 7);
        List<String> keys = topKeywords(merged, 7);

        StringBuilder sb = new StringBuilder("ΟΔΗΓΟΣ ΜΕΛΕΤΗΣ\n\nΒΑΣΙΚΑ ΣΗΜΕΙΑ\n");
        for (String p : points) sb.append("• ").append(p.trim()).append("\n");

        sb.append("\nΕΡΩΤΗΣΕΙΣ ΕΠΑΝΑΛΗΨΗΣ\n");
        int q = 1;
        for (String k : keys) {
            sb.append(q++).append(". Τι αναφέρουν οι πηγές για «").append(k).append("»;\n");
            if (q > 5) break;
        }
        sb.append("\nΑΡΧΗ ΑΚΡΙΒΕΙΑΣ\nΟι απαντήσεις πρέπει να στηρίζονται στα αποσπάσματα των πηγών και όχι σε εξωτερικές υποθέσεις.");

        output.setText(sb.toString().trim());
        status.setText("✓ Ο οδηγός μελέτης είναι έτοιμος.");
    }

    private void answerQuestion() {
        if (!ensureSources()) return;
        String q = questionInput.getText().toString().trim();
        if (q.isEmpty()) {
            toast("Γράψε πρώτα μια ερώτηση.");
            return;
        }

        List<String> qWords = tokens(q);
        List<Hit> hits = new ArrayList<>();

        for (int si = 0; si < sources.size(); si++) {
            SourceDoc source = sources.get(si);
            for (String sentence : splitSentences(source.content)) {
                if (sentence.length() < 25 || sentence.length() > 650) continue;
                List<String> sWords = tokens(sentence);
                int overlap = 0;
                for (String w : qWords) {
                    if (sWords.contains(w)) overlap++;
                }
                if (overlap > 0) {
                    double score = overlap * 8.0 + Math.min(sentence.length(), 260) / 260.0;
                    hits.add(new Hit(score, sentence.trim(), si));
                }
            }
        }

        Collections.sort(hits, (a, b) -> Double.compare(b.score, a.score));

        if (hits.isEmpty()) {
            output.setText("Δεν βρήκα άμεση στήριξη μέσα στις πηγές σου για αυτή την ερώτηση.\n\nΔοκίμασε πιο συγκεκριμένες λέξεις που υπάρχουν στα κείμενα.");
            status.setText("Δεν βρέθηκε τεκμηριωμένο απόσπασμα.");
            return;
        }

        StringBuilder sb = new StringBuilder("ΑΠΑΝΤΗΣΗ ΜΕ ΒΑΣΗ ΤΙΣ ΠΗΓΕΣ\n\n");
        int limit = Math.min(4, hits.size());
        for (int i = 0; i < limit; i++) {
            Hit h = hits.get(i);
            SourceDoc s = sources.get(h.sourceIndex);
            sb.append("[").append(h.sourceIndex + 1).append("] ")
                    .append(h.sentence).append("\n")
                    .append("— ").append(s.title).append("\n\n");
        }

        sb.append("Σημείωση: δεν πρόσθεσα πληροφορίες που δεν εμφανίζονται στις πηγές.");
        output.setText(sb.toString().trim());
        status.setText("✓ Βρέθηκαν " + limit + " σχετικά αποσπάσματα.");
    }

    private List<String> topSentences(String text, String query, int limit) {
        List<String> sentences = splitSentences(text);
        Map<String, Integer> freq = frequency(text);
        List<String> qWords = tokens(query);
        List<SentenceScore> scored = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i).trim();
            if (s.length() < 35 || s.length() > 520) continue;
            List<String> words = tokens(s);
            if (words.size() < 5) continue;

            double score = 0;
            for (String w : words) score += Math.min(freq.getOrDefault(w, 0), 8);
            score /= Math.sqrt(words.size());

            for (String q : qWords) if (words.contains(q)) score += 12;
            if (i < 8) score += 1.4;

            scored.add(new SentenceScore(score, i, s));
        }

        Collections.sort(scored, (a, b) -> Double.compare(b.score, a.score));
        List<SentenceScore> selected = new ArrayList<>();
        for (SentenceScore ss : scored) {
            boolean duplicate = false;
            for (SentenceScore prior : selected) {
                if (similar(ss.sentence, prior.sentence) > 0.72) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) selected.add(ss);
            if (selected.size() >= limit) break;
        }
        Collections.sort(selected, Comparator.comparingInt(a -> a.position));

        List<String> out = new ArrayList<>();
        for (SentenceScore ss : selected) out.add(ss.sentence);
        return out;
    }

    private List<String> topKeywords(String text, int limit) {
        Map<String, Integer> freq = frequency(text);
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        Collections.sort(entries, (a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries) {
            if (e.getKey().length() >= 4 && e.getValue() >= 2) out.add(e.getKey());
            if (out.size() >= limit) break;
        }
        return out;
    }

    private Map<String, Integer> frequency(String text) {
        Map<String, Integer> map = new HashMap<>();
        for (String w : tokens(text)) map.put(w, map.getOrDefault(w, 0) + 1);
        return map;
    }

    private List<String> tokens(String text) {
        String n = normalize(text);
        String[] raw = n.split("[^\\p{L}\\p{Nd}]+");
        List<String> out = new ArrayList<>();
        for (String w : raw) {
            if (w.length() < 3 || stopWords.contains(w)) continue;
            out.add(w);
        }
        return out;
    }

    private String normalize(String text) {
        String lower = text == null ? "" : text.toLowerCase(new Locale("el", "GR"));
        lower = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return lower.replaceAll("\\p{M}+", "");
    }

    private List<String> splitSentences(String text) {
        String cleaned = cleanText(text);
        String[] arr = cleaned.split("(?<=[.!?;])\\s+|\\n+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private double similar(String a, String b) {
        Set<String> aa = new HashSet<>(tokens(a));
        Set<String> bb = new HashSet<>(tokens(b));
        if (aa.isEmpty() || bb.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(aa);
        inter.retainAll(bb);
        Set<String> union = new HashSet<>(aa);
        union.addAll(bb);
        return inter.size() / (double) union.size();
    }

    private String mergedText() {
        StringBuilder sb = new StringBuilder();
        for (SourceDoc s : sources) sb.append(s.content).append("\n");
        return sb.toString();
    }

    private boolean ensureSources() {
        if (sources.isEmpty()) {
            toast("Πρόσθεσε πρώτα μία πηγή.");
            return false;
        }
        return true;
    }

    private void speakOutput() {
        String value = output.getText().toString().trim();
        if (value.isEmpty()) {
            toast("Δεν υπάρχει κείμενο για ανάγνωση.");
            return;
        }
        if (tts == null) {
            toast("Το TTS δεν είναι έτοιμο.");
            return;
        }
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "source_mind_output");
        status.setText("🔊 Διαβάζω το αποτέλεσμα.");
    }

    @Override
    public void onInit(int statusCode) {
        if (statusCode == TextToSpeech.SUCCESS && tts != null) {
            int r = tts.setLanguage(new Locale("el", "GR"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.setLanguage(Locale.getDefault());
            }
            tts.setSpeechRate(0.92f);
        }
    }

    private String readPdf(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("No stream");
        PDDocument doc = null;
        try {
            doc = PDDocument.load(in);
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } finally {
            try { if (doc != null) doc.close(); } catch (Exception ignored) {}
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    private String readText(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("No stream");
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null && sb.length() < MAX_CHARS_PER_SOURCE * 2) {
            sb.append(line).append('\n');
        }
        br.close();
        return sb.toString();
    }

    private String getDisplayName(Uri uri) {
        String name = "Πηγή";
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) name = c.getString(i);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return name == null ? "Πηγή" : name;
    }

    private String cleanText(String s) {
        if (s == null) return "";
        return s.replace("\r", " ")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n[ \\t]+", "\\n")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
    }

    private void loadSources() {
        sources.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SOURCES, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String title = o.optString("title", "Πηγή");
                String content = o.optString("content", "");
                String type = o.optString("type", "Κείμενο");
                if (!content.isEmpty()) sources.add(new SourceDoc(title, content, type));
            }
        } catch (Exception ignored) {
        }
    }

    private void persistSources() {
        JSONArray arr = new JSONArray();
        try {
            for (SourceDoc s : sources) {
                JSONObject o = new JSONObject();
                o.put("title", s.title);
                o.put("content", s.content);
                o.put("type", s.type);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_SOURCES, arr.toString())
                .apply();
    }

    private void setupStopWords() {
        String common = "και να το η ο οι τα των της τον την του σε για με από που πως ως ένα μια είναι ήταν θα δεν ναι ή αλλά αν όταν όσο αυτό αυτή αυτά εκεί εδώ πολύ πιο προς μετά πριν μέσα έξω χωρίς κάθε επίσης όμως επειδή ώστε γιατί τι ποιος ποια ποιο μου σου μας σας τους τις στο στη στα στον στην στους στις κι ούτε ότι ενώ ήδη μπορεί έχουν έχει είχε έχω είμαι είσαι είμαστε then the and are was were this that with from into your you our their they them for not have has had can could should would";
        for (String w : common.split(" ")) stopWords.add(normalize(w));
    }

    private String join(List<String> values, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setPadding(dp(12), dp(7), dp(12), dp(7));
        b.setBackground(buttonDrawable(false));
        b.setOnFocusChangeListener((v, focused) -> {
            b.setBackground(buttonDrawable(focused));
            b.setTextColor(focused ? Color.BLACK : Color.WHITE);
            b.setScaleX(focused ? 1.03f : 1f);
            b.setScaleY(focused ? 1.03f : 1f);
        });
        return b;
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(126, 140, 159));
        e.setTextColor(Color.WHITE);
        e.setTextSize(18);
        e.setSingleLine(true);
        e.setPadding(dp(16), dp(10), dp(16), dp(10));
        e.setFocusable(true);
        e.setFocusableInTouchMode(true);
        e.setBackground(editDrawable(false));
        e.setOnFocusChangeListener((v, focused) -> e.setBackground(editDrawable(focused)));
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
        return new LinearLayout.LayoutParams(0, dp(62), 1f);
    }

    private LinearLayout.LayoutParams fullHeight(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams top(int top, int height) {
        LinearLayout.LayoutParams p = fullHeight(height);
        p.topMargin = top;
        return p;
    }

    private GradientDrawable buttonDrawable(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(focused ? Color.rgb(239, 208, 90) : Color.rgb(34, 46, 64));
        g.setCornerRadius(dp(10));
        g.setStroke(dp(focused ? 3 : 2), focused ? Color.WHITE : Color.rgb(70, 90, 116));
        return g;
    }

    private GradientDrawable editDrawable(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(18, 26, 37));
        g.setCornerRadius(dp(9));
        g.setStroke(dp(focused ? 3 : 1), focused ? Color.rgb(239, 208, 90) : Color.rgb(65, 81, 104));
        return g;
    }

    private GradientDrawable panelDrawable() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(17, 24, 34));
        g.setCornerRadius(dp(11));
        g.setStroke(dp(1), Color.rgb(52, 69, 91));
        return g;
    }

    private GradientDrawable cardDrawable() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(15, 22, 31));
        g.setCornerRadius(dp(9));
        g.setStroke(dp(1), Color.rgb(46, 62, 82));
        return g;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private static class SourceDoc {
        final String title;
        final String content;
        final String type;

        SourceDoc(String title, String content, String type) {
            this.title = title;
            this.content = content;
            this.type = type;
        }
    }

    private static class SentenceScore {
        final double score;
        final int position;
        final String sentence;

        SentenceScore(double score, int position, String sentence) {
            this.score = score;
            this.position = position;
            this.sentence = sentence;
        }
    }

    private static class Hit {
        final double score;
        final String sentence;
        final int sourceIndex;

        Hit(double score, String sentence, int sourceIndex) {
            this.score = score;
            this.sentence = sentence;
            this.sourceIndex = sourceIndex;
        }
    }
}
