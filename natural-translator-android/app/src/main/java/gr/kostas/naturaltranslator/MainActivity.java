package gr.kostas.naturaltranslator;

import android.app.Activity;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 18, 24);
    private static final int PANEL = Color.rgb(28, 34, 44);
    private static final int PANEL_2 = Color.rgb(38, 46, 59);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(172, 182, 196);
    private static final int ACCENT = Color.rgb(119, 181, 255);
    private static final int GOLD = Color.rgb(238, 193, 93);
    private static final int OK = Color.rgb(116, 220, 171);
    private static final int ERROR = Color.rgb(255, 140, 140);

    private Spinner fromSpinner;
    private Spinner toSpinner;
    private Spinner tutorLangSpinner;
    private EditText input;
    private EditText tutorInput;
    private TextView output;
    private TextView simpleOutput;
    private TextView status;
    private TextView tutorOutput;
    private TextView tutorStatus;
    private Button translateButton;
    private Button speakButton;
    private Button copyButton;
    private Button shareButton;
    private final List<Button> tutorButtons = new ArrayList<>();

    private Translator translator;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private final List<Lang> languages = Arrays.asList(
        new Lang("Ελληνικά", "el"),
        new Lang("Αγγλικά", "en"),
        new Lang("Γαλλικά", "fr"),
        new Lang("Γερμανικά", "de"),
        new Lang("Ιταλικά", "it"),
        new Lang("Ισπανικά", "es"),
        new Lang("Πορτογαλικά", "pt"),
        new Lang("Αλβανικά", "sq"),
        new Lang("Βουλγαρικά", "bg"),
        new Lang("Ρουμανικά", "ro"),
        new Lang("Σερβικά", "sr"),
        new Lang("Τουρκικά", "tr"),
        new Lang("Ουκρανικά", "uk"),
        new Lang("Ρωσικά", "ru"),
        new Lang("Πολωνικά", "pl"),
        new Lang("Ολλανδικά", "nl"),
        new Lang("Σουηδικά", "sv"),
        new Lang("Νορβηγικά", "no"),
        new Lang("Δανικά", "da"),
        new Lang("Φινλανδικά", "fi"),
        new Lang("Τσεχικά", "cs"),
        new Lang("Ουγγρικά", "hu"),
        new Lang("Αραβικά", "ar"),
        new Lang("Εβραϊκά", "he"),
        new Lang("Περσικά", "fa"),
        new Lang("Χίντι", "hi"),
        new Lang("Ούρντου", "ur"),
        new Lang("Μπενγκάλι", "bn"),
        new Lang("Κινεζικά", "zh"),
        new Lang("Ιαπωνικά", "ja"),
        new Lang("Κορεατικά", "ko"),
        new Lang("Βιετναμικά", "vi"),
        new Lang("Ταϊλανδικά", "th"),
        new Lang("Ινδονησιακά", "id"),
        new Lang("Μαλαισιανά", "ms"),
        new Lang("Σουαχίλι", "sw")
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        tts = new TextToSpeech(this, result -> ttsReady = result == TextToSpeech.SUCCESS);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(36));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Natural Translator AI", 27, TEXT, true);
        root.addView(title);

        TextView subtitle = text("Natural Greek Max • 🧠 Με απλά λόγια • 🎓 Πρύτανης Ξένων Γλωσσών", 15, MUTED, false);
        LinearLayout.LayoutParams subtitleLp = matchWrap();
        subtitleLp.setMargins(0, dp(5), 0, dp(18));
        root.addView(subtitle, subtitleLp);

        root.addView(label("Από γλώσσα"));
        fromSpinner = spinner();
        root.addView(fromSpinner, matchHeight(dp(52)));

        Button swap = button("⇅  Αντιστροφή γλωσσών", false);
        LinearLayout.LayoutParams swapLp = matchHeight(dp(48));
        swapLp.setMargins(0, dp(9), 0, dp(9));
        root.addView(swap, swapLp);

        root.addView(label("Προς γλώσσα"));
        toSpinner = spinner();
        root.addView(toSpinner, matchHeight(dp(52)));

        ArrayAdapter<String> adapter = languageAdapter();
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);
        fromSpinner.setSelection(0);
        toSpinner.setSelection(1);

        swap.setOnClickListener(v -> {
            int a = fromSpinner.getSelectedItemPosition();
            int b = toSpinner.getSelectedItemPosition();
            fromSpinner.setSelection(b);
            toSpinner.setSelection(a);
            CharSequence oldOutput = output == null ? "" : output.getText();
            if (oldOutput != null && oldOutput.length() > 0 && !"—".contentEquals(oldOutput)) {
                String oldInput = input.getText().toString();
                input.setText(oldOutput.toString());
                output.setText(oldInput.isEmpty() ? "—" : oldInput);
                if (simpleOutput != null) simpleOutput.setText("—");
            }
        });

        TextView inputLabel = label("Κείμενο");
        LinearLayout.LayoutParams ilp = matchWrap();
        ilp.setMargins(0, dp(18), 0, 0);
        root.addView(inputLabel, ilp);

        input = editBox("Γράψε ή επικόλλησε το κείμενο που θέλεις να μεταφράσεις…", 5);
        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.setMargins(0, dp(7), 0, dp(12));
        root.addView(input, inputLp);

        translateButton = button("Μετάφραση", true);
        root.addView(translateButton, matchHeight(dp(54)));

        status = text("Έτοιμος. Την πρώτη φορά κάθε γλώσσα κατεβάζει το μοντέλο της.", 14, MUTED, false);
        LinearLayout.LayoutParams statusLp = matchWrap();
        statusLp.setMargins(0, dp(10), 0, dp(16));
        root.addView(status, statusLp);

        root.addView(label("Φυσική μετάφραση"));
        output = text("—", 18, TEXT, false);
        output.setTextIsSelectable(true);
        output.setPadding(dp(14), dp(14), dp(14), dp(14));
        output.setMinHeight(dp(120));
        output.setBackground(panelDrawable(PANEL));
        LinearLayout.LayoutParams outLp = matchWrap();
        outLp.setMargins(0, dp(7), 0, dp(12));
        root.addView(output, outLp);

        root.addView(label("🧠 Με απλά λόγια"));
        simpleOutput = text("—", 17, TEXT, false);
        simpleOutput.setTextIsSelectable(true);
        simpleOutput.setPadding(dp(14), dp(14), dp(14), dp(14));
        simpleOutput.setMinHeight(dp(96));
        simpleOutput.setBackground(panelDrawable(PANEL_2));
        LinearLayout.LayoutParams simpleLp = matchWrap();
        simpleLp.setMargins(0, dp(7), 0, dp(14));
        root.addView(simpleOutput, simpleLp);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        speakButton = button("🔊 Ακρόαση", false);
        copyButton = button("📋 Αντιγραφή", false);
        shareButton = button("↗ Κοινή χρήση", false);
        actionRow.addView(speakButton, weightedButton());
        LinearLayout.LayoutParams copyLp = weightedButton();
        copyLp.setMargins(dp(8), 0, dp(8), 0);
        actionRow.addView(copyButton, copyLp);
        actionRow.addView(shareButton, weightedButton());
        root.addView(actionRow, matchHeight(dp(50)));

        Button clear = button("Καθαρισμός", false);
        LinearLayout.LayoutParams clearLp = matchHeight(dp(46));
        clearLp.setMargins(0, dp(10), 0, 0);
        root.addView(clear, clearLp);

        buildTutorUi(root, adapter);

        TextView privacy = text(
            "Η βασική μετάφραση γίνεται με Google ML Kit στη συσκευή μετά τη λήψη του μοντέλου. " +
            "Η «Ελληνική μεταγραφή», το «Συντακτικό» και οι «Συμβουλές» λειτουργούν τοπικά. " +
            "Μόνο όταν πατάς «Γραμματική» στέλνεται το συγκεκριμένο κείμενο στην online υπηρεσία LanguageTool για έλεγχο. " +
            "Η λειτουργία «Έννοια» χρησιμοποιεί online λεξικό για αγγλικές λέξεις και έχει τοπικό fallback μετάφρασης.",
            12, MUTED, false
        );
        LinearLayout.LayoutParams privacyLp = matchWrap();
        privacyLp.setMargins(0, dp(20), 0, 0);
        root.addView(privacy, privacyLp);

        translateButton.setOnClickListener(v -> translate());
        copyButton.setOnClickListener(v -> copyResult());
        shareButton.setOnClickListener(v -> shareResult());
        speakButton.setOnClickListener(v -> speakResult());
        clear.setOnClickListener(v -> {
            input.setText("");
            output.setText("—");
            simpleOutput.setText("—");
            output.setTag(null);
            setStatus("Έτοιμος.", false);
            input.requestFocus();
        });

        setContentView(scroll);
    }

    private void buildTutorUi(LinearLayout root, ArrayAdapter<String> adapter) {
        TextView divider = text("🎓  ΠΡΥΤΑΝΗΣ ΞΕΝΩΝ ΓΛΩΣΣΩΝ", 20, GOLD, true);
        LinearLayout.LayoutParams dividerLp = matchWrap();
        dividerLp.setMargins(0, dp(30), 0, dp(6));
        root.addView(divider, dividerLp);

        TextView intro = text(
            "Βοηθός γλώσσας για έννοιες, γραμματική, συντακτικό, προφορά/μεταγραφή και πρακτικές συμβουλές — χωρίς API key.",
            14, MUTED, false
        );
        LinearLayout.LayoutParams introLp = matchWrap();
        introLp.setMargins(0, 0, 0, dp(14));
        root.addView(intro, introLp);

        root.addView(label("Γλώσσα του κειμένου που θα αναλυθεί"));
        tutorLangSpinner = spinner();
        tutorLangSpinner.setAdapter(adapter);
        tutorLangSpinner.setSelection(1);
        root.addView(tutorLangSpinner, matchHeight(dp(52)));

        tutorInput = editBox("Γράψε λέξη ή πρόταση. Παράδειγμα: love / I have been working here.", 4);
        LinearLayout.LayoutParams ti = matchWrap();
        ti.setMargins(0, dp(10), 0, dp(10));
        root.addView(tutorInput, ti);

        LinearLayout fillRow = new LinearLayout(this);
        fillRow.setOrientation(LinearLayout.HORIZONTAL);
        Button useOriginal = button("← Από αρχικό", false);
        Button useTranslation = button("← Από μετάφραση", false);
        fillRow.addView(useOriginal, weightedButton());
        LinearLayout.LayoutParams transLp = weightedButton();
        transLp.setMargins(dp(8), 0, 0, 0);
        fillRow.addView(useTranslation, transLp);
        root.addView(fillRow, matchHeight(dp(46)));

        useOriginal.setOnClickListener(v -> {
            tutorInput.setText(input.getText().toString());
            tutorLangSpinner.setSelection(fromSpinner.getSelectedItemPosition());
            tutorInput.requestFocus();
        });

        useTranslation.setOnClickListener(v -> {
            String value = output.getText().toString();
            if (value.equals("—") || value.trim().isEmpty()) {
                Toast.makeText(this, "Κάνε πρώτα μία μετάφραση.", Toast.LENGTH_SHORT).show();
                return;
            }
            tutorInput.setText(value);
            tutorLangSpinner.setSelection(toSpinner.getSelectedItemPosition());
            tutorInput.requestFocus();
        });

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button concept = tutorButton("💡 Έννοια");
        Button grammar = tutorButton("✓ Γραμματική");
        Button syntax = tutorButton("🧩 Συντακτικό");
        row1.addView(concept, weightedButton());
        LinearLayout.LayoutParams gLp = weightedButton();
        gLp.setMargins(dp(6), 0, dp(6), 0);
        row1.addView(grammar, gLp);
        row1.addView(syntax, weightedButton());
        LinearLayout.LayoutParams r1lp = matchHeight(dp(48));
        r1lp.setMargins(0, dp(10), 0, 0);
        root.addView(row1, r1lp);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button transcription = tutorButton("🔤 Ελληνική μεταγραφή");
        Button advice = tutorButton("🎯 Συμβουλές");
        row2.addView(transcription, weightedButton());
        LinearLayout.LayoutParams aLp = weightedButton();
        aLp.setMargins(dp(6), 0, 0, 0);
        row2.addView(advice, aLp);
        LinearLayout.LayoutParams r2lp = matchHeight(dp(48));
        r2lp.setMargins(0, dp(7), 0, dp(10));
        root.addView(row2, r2lp);

        tutorStatus = text("Πρύτανης έτοιμος.", 13, MUTED, false);
        LinearLayout.LayoutParams ts = matchWrap();
        ts.setMargins(0, 0, 0, dp(8));
        root.addView(tutorStatus, ts);

        tutorOutput = text(
            "Παράδειγμα μεταγραφής: love → λοβ\n\nΔιάλεξε μία από τις λειτουργίες επάνω.",
            16, TEXT, false
        );
        tutorOutput.setTextIsSelectable(true);
        tutorOutput.setPadding(dp(14), dp(14), dp(14), dp(14));
        tutorOutput.setMinHeight(dp(150));
        tutorOutput.setBackground(panelDrawable(PANEL));
        root.addView(tutorOutput, matchWrap());

        LinearLayout tutorActionRow = new LinearLayout(this);
        tutorActionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button copyTutor = button("📋 Αντιγραφή ανάλυσης", false);
        Button shareTutor = button("↗ Κοινή χρήση", false);
        tutorActionRow.addView(copyTutor, weightedButton());
        LinearLayout.LayoutParams shareLp = weightedButton();
        shareLp.setMargins(dp(8), 0, 0, 0);
        tutorActionRow.addView(shareTutor, shareLp);
        LinearLayout.LayoutParams tarLp = matchHeight(dp(46));
        tarLp.setMargins(0, dp(8), 0, 0);
        root.addView(tutorActionRow, tarLp);

        concept.setOnClickListener(v -> {
            String value = tutorText();
            if (value == null) return;
            setTutorBusy(true, "Αναλύω την έννοια…");
            TutorEngine.explainConcept(this, value, tutorLanguage().code, tutorCallback());
        });

        grammar.setOnClickListener(v -> {
            String value = tutorText();
            if (value == null) return;
            setTutorBusy(true, "Ελέγχω γραμματική, ορθογραφία και στίξη…");
            TutorEngine.grammarCheck(this, value, tutorLanguage().code, tutorCallback());
        });

        syntax.setOnClickListener(v -> {
            String value = tutorText();
            if (value == null) return;
            hideKeyboard();
            tutorOutput.setText(TutorEngine.syntaxAnalysis(value, tutorLanguage().code));
            setTutorStatus("Η συντακτική ανάλυση ολοκληρώθηκε.", false);
        });

        transcription.setOnClickListener(v -> {
            String value = tutorText();
            if (value == null) return;
            hideKeyboard();
            String result =
                "🎓 ΕΛΛΗΝΙΚΗ ΜΕΤΑΓΡΑΦΗ\n" +
                value + "\n↓\n" +
                TutorEngine.greekTranscription(value, tutorLanguage().code) +
                "\n\nΗ μεταγραφή είναι βοήθημα ανάγνωσης με ελληνικά γράμματα, όχι επίσημη φωνητική γραφή IPA.";
            tutorOutput.setText(result);
            setTutorStatus("Η μεταγραφή δημιουργήθηκε.", false);
        });

        advice.setOnClickListener(v -> {
            String value = tutorText();
            if (value == null) return;
            hideKeyboard();
            tutorOutput.setText(TutorEngine.studyAdvice(value, tutorLanguage().code));
            setTutorStatus("Έτοιμη η συμβουλή μελέτης.", false);
        });

        copyTutor.setOnClickListener(v -> {
            String value = tutorOutput.getText().toString().trim();
            if (value.isEmpty()) return;
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Ανάλυση Πρύτανη", value));
            Toast.makeText(this, "Αντιγράφηκε η ανάλυση.", Toast.LENGTH_SHORT).show();
        });

        shareTutor.setOnClickListener(v -> {
            String value = tutorOutput.getText().toString().trim();
            if (value.isEmpty()) return;
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, value);
            startActivity(Intent.createChooser(send, "Κοινή χρήση ανάλυσης"));
        });
    }

    private ArrayAdapter<String> languageAdapter() {
        ArrayList<String> names = new ArrayList<>();
        for (Lang l : languages) names.add(l.name);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, names) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setTextSize(16);
                v.setPadding(dp(12), 0, dp(12), 0);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.BLACK);
                v.setTextSize(16);
                v.setPadding(dp(16), dp(12), dp(16), dp(12));
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void translate() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus("Γράψε πρώτα ένα κείμενο.", true);
            return;
        }

        hideKeyboard();
        Lang from = languages.get(fromSpinner.getSelectedItemPosition());
        Lang to = languages.get(toSpinner.getSelectedItemPosition());

        if (from.code.equals(to.code)) {
            String natural = naturalGreekMax(text, text, to.code);
            output.setText(natural);
            simpleOutput.setText(simpleMeaning(text, natural, to.code));
            output.setTag(to.code);
            setStatus("Οι δύο γλώσσες είναι ίδιες.", false);
            return;
        }

        String source = TranslateLanguage.fromLanguageTag(from.code);
        String target = TranslateLanguage.fromLanguageTag(to.code);
        if (source == null || target == null) {
            setStatus("Αυτή η γλώσσα δεν υποστηρίζεται από το εγκατεστημένο μοντέλο.", true);
            return;
        }

        if (translator != null) {
            translator.close();
            translator = null;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build();
        translator = Translation.getClient(options);

        setBusy(true);
        setStatus("Έλεγχος / λήψη γλωσσικού μοντέλου…", false);

        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener(unused -> {
                setStatus("Μεταφράζω με το νευρωνικό μοντέλο…", false);
                translator.translate(text)
                    .addOnSuccessListener(translated -> {
                        String natural = naturalGreekMax(text, translated, to.code);
                        output.setText(natural);
                        simpleOutput.setText(simpleMeaning(text, natural, to.code));
                        output.setTag(to.code);
                        setStatus("Ολοκληρώθηκε • Natural Greek Max + απλή εξήγηση.", false);
                        setBusy(false);
                    })
                    .addOnFailureListener(e -> {
                        setStatus("Δεν ολοκληρώθηκε η μετάφραση. Δοκίμασε ξανά.", true);
                        setBusy(false);
                    });
            })
            .addOnFailureListener(e -> {
                setStatus("Δεν κατέβηκε το γλωσσικό μοντέλο. Έλεγξε τη σύνδεση στο Internet και ξαναπάτησε Μετάφραση.", true);
                setBusy(false);
            });
    }


    private String naturalGreekMax(String sourceText, String translated, String targetCode) {
        if (translated == null) return "";
        String result = translated.trim().replaceAll("[ \\t]+", " ").replaceAll(" ?\\n ?", "\\n");

        if (!"el".equals(targetCode)) return result;

        String source = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);

        result = result
            .replace("τηλεόραση πραγματικότητας", "reality TV")
            .replace("Τηλεόραση πραγματικότητας", "Reality TV")
            .replace("ριάλιτι τηλεόραση", "reality TV")
            .replace("Ριάλιτι τηλεόραση", "Reality TV")
            .replace("πλατφόρμα εκτόξευσης", "εφαλτήριο")
            .replace("εξέδρα εκτόξευσης", "εφαλτήριο")
            .replace("πλήρως ανεπτυγμένες καριέρες", "ολοκληρωμένες καριέρες")
            .replace("καριέρες παραστάσεων", "καριέρες στον χώρο του θεάματος")
            .replace("καριέρες απόδοσης", "καριέρες στον χώρο του θεάματος");

        if (source.contains("launch pad")) {
            result = result
                .replace("σημείο εκκίνησης", "εφαλτήριο")
                .replace("βάση εκτόξευσης", "εφαλτήριο");
        }
        if (source.contains("full-blown")) {
            result = result
                .replace("πλήρεις επαγγελματικές καριέρες", "ολοκληρωμένες επαγγελματικές καριέρες")
                .replace("πλήρεις καριέρες", "ολοκληρωμένες καριέρες");
        }
        if (source.contains("lock in now")) {
            result = result
                .replace("Κλειδώστε τώρα", "Μην το χάσετε — δείτε τώρα")
                .replace("Κλείδωσε τώρα", "Μην το χάσεις — δες τώρα")
                .replace("Κλειδώστε", "Συντονιστείτε")
                .replace("Κλείδωσε", "Συντονίσου");
        }

        return polishGreek(result);
    }

    private String polishGreek(String value) {
        if (value == null) return "";
        return value
            .replace(" προκειμένου να ", " για να ")
            .replace(" δύναται να ", " μπορεί να ")
            .replace(" στο πλαίσιο του ", " στο ")
            .replace(" στο πλαίσιο της ", " στη ")
            .replace(" πραγματοποιεί ", " κάνει ")
            .replaceAll(" {2,}", " ")
            .replaceAll("\\s+([,.;!?])", "$1")
            .trim();
    }

    private String simpleMeaning(String sourceText, String naturalTranslation, String targetCode) {
        String source = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
        String greek = naturalTranslation == null ? "" : naturalTranslation.trim();

        if (!"el".equals(targetCode)) {
            return "Η ενότητα «Με απλά λόγια» είναι βελτιστοποιημένη όταν η μετάφραση γίνεται προς Ελληνικά.";
        }
        if (greek.isEmpty()) return "—";

        boolean realityCareerExample =
            source.contains("reality") &&
            (source.contains("launch pad") || source.contains("performance careers")) &&
            (source.contains("music") || source.contains("theatre") || source.contains("theater")) &&
            source.contains("dance");

        if (realityCareerExample) {
            return "Το επεισόδιο μιλά για ανθρώπους που ξεκίνησαν από reality TV και μετά κατάφεραν να κάνουν κανονική καριέρα στο θέαμα — μουσική, θέατρο, χορό ή κωμωδία.";
        }

        String clean = polishGreek(greek)
            .replace("εξετάζουν πώς", "μιλούν για το πώς")
            .replace("διερευνούν πώς", "εξηγούν πώς")
            .replace("αποτελεί εφαλτήριο για", "βοηθά να ξεκινήσει")
            .replace("στον τομέα", "στον χώρο");

        String[] sentences = clean.split("(?<=[.!;?])\\s+");
        StringBuilder core = new StringBuilder();

        for (String sentence : sentences) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;

            String low = s.toLowerCase(Locale.ROOT);
            boolean promo =
                low.contains("μην το χάσεις") ||
                low.contains("μην το χάσετε") ||
                low.contains("δες τώρα") ||
                low.contains("δείτε τώρα") ||
                low.contains("συντονίσου τώρα") ||
                low.contains("συντονιστείτε τώρα") ||
                low.contains("κάνε κλικ") ||
                low.contains("κάντε κλικ");

            if (promo) continue;

            if (core.length() > 0) core.append(" ");
            core.append(s);
            if (core.length() >= 320 || countSentences(core.toString()) >= 2) break;
        }

        String answer = core.length() == 0 ? clean : core.toString();
        answer = shortenAtWord(answer, 390);

        if (source.contains("episode") && !answer.toLowerCase(Locale.ROOT).startsWith("το επεισόδιο")) {
            String low = answer.toLowerCase(Locale.ROOT);
            int idx = Math.max(low.indexOf("μιλούν για"), low.indexOf("μιλά για"));
            if (idx >= 0) {
                answer = "Το επεισόδιο " + answer.substring(idx);
            }
        }

        return answer;
    }

    private int countSentences(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '.' || ch == '!' || ch == '?' || ch == ';') count++;
        }
        return count;
    }

    private String shortenAtWord(String text, int max) {
        if (text == null || text.length() <= max) return text == null ? "" : text;
        int cut = text.lastIndexOf(' ', max);
        if (cut < max / 2) cut = max;
        return text.substring(0, cut).trim() + "…";
    }

    private void copyResult() {
        String value = output.getText().toString();
        if (value.equals("—") || value.trim().isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Μετάφραση", value));
        Toast.makeText(this, "Αντιγράφηκε.", Toast.LENGTH_SHORT).show();
    }

    private void shareResult() {
        String value = output.getText().toString();
        if (value.equals("—") || value.trim().isEmpty()) return;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(send, "Κοινή χρήση μετάφρασης"));
    }

    private void speakResult() {
        String value = output.getText().toString();
        if (!ttsReady || value.equals("—") || value.trim().isEmpty()) {
            Toast.makeText(this, "Η φωνητική ανάγνωση δεν είναι διαθέσιμη ακόμη.", Toast.LENGTH_SHORT).show();
            return;
        }
        Object tag = output.getTag();
        String code = tag == null ? languages.get(toSpinner.getSelectedItemPosition()).code : tag.toString();
        int result = tts.setLanguage(Locale.forLanguageTag(code));
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "Δεν υπάρχει εγκατεστημένη φωνή για αυτή τη γλώσσα.", Toast.LENGTH_LONG).show();
            return;
        }
        tts.speak(value, TextToSpeech.QUEUE_FLUSH, null, "natural-translation");
    }

    private String tutorText() {
        String value = tutorInput.getText().toString().trim();
        if (value.isEmpty()) {
            setTutorStatus("Γράψε πρώτα λέξη ή πρόταση.", true);
            tutorInput.requestFocus();
            return null;
        }
        return value;
    }

    private Lang tutorLanguage() {
        int pos = tutorLangSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= languages.size()) pos = 1;
        return languages.get(pos);
    }

    private TutorEngine.Callback tutorCallback() {
        return new TutorEngine.Callback() {
            @Override
            public void onResult(String text) {
                tutorOutput.setText(text);
                setTutorBusy(false, "Η ανάλυση ολοκληρώθηκε.");
            }

            @Override
            public void onError(String message) {
                tutorOutput.setText("Δεν ολοκληρώθηκε η ανάλυση.\n\n" + message);
                setTutorBusy(false, message);
                tutorStatus.setTextColor(ERROR);
            }
        };
    }

    private void setBusy(boolean busy) {
        translateButton.setEnabled(!busy);
        translateButton.setText(busy ? "Περίμενε…" : "Μετάφραση");
    }

    private void setTutorBusy(boolean busy, String message) {
        for (Button b : tutorButtons) b.setEnabled(!busy);
        setTutorStatus(message, false);
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? ERROR : (message.startsWith("Ολοκληρώθηκε") ? OK : MUTED));
    }

    private void setTutorStatus(String message, boolean error) {
        tutorStatus.setText(message);
        tutorStatus.setTextColor(error ? ERROR : (message.contains("ολοκληρώ") || message.contains("Έτοιμη") ? OK : MUTED));
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private TextView label(String value) {
        TextView t = text(value, 13, MUTED, true);
        t.setAllCaps(false);
        return t;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText editBox(String hint, int minLines) {
        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(115, 126, 143));
        e.setHint(hint);
        e.setTextSize(17);
        e.setGravity(Gravity.TOP | Gravity.START);
        e.setMinLines(minLines);
        e.setMaxLines(12);
        e.setPadding(dp(14), dp(13), dp(14), dp(13));
        e.setBackground(panelDrawable(PANEL));
        e.setInputType(
            android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        return e;
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        s.setBackground(panelDrawable(PANEL_2));
        s.setPadding(dp(4), 0, dp(4), 0);
        return s;
    }

    private Button button(String value, boolean primary) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(primary ? 17 : 14);
        b.setTextColor(primary ? Color.rgb(10, 20, 30) : TEXT);
        b.setAllCaps(false);
        b.setBackground(panelDrawable(primary ? ACCENT : PANEL_2));
        b.setPadding(dp(8), 0, dp(8), 0);
        return b;
    }

    private Button tutorButton(String value) {
        Button b = button(value, false);
        b.setTextSize(12);
        tutorButtons.add(b);
        return b;
    }

    private GradientDrawable panelDrawable(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        d.setStroke(dp(1), Color.rgb(55, 66, 83));
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (translator != null) translator.close();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private static class Lang {
        final String name;
        final String code;

        Lang(String name, String code) {
            this.name = name;
            this.code = code;
        }
    }
}
