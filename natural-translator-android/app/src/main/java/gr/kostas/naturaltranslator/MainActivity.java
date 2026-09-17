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
    private static final int OK = Color.rgb(116, 220, 171);
    private static final int ERROR = Color.rgb(255, 140, 140);

    private Spinner fromSpinner;
    private Spinner toSpinner;
    private EditText input;
    private TextView output;
    private TextView status;
    private Button translateButton;
    private Button speakButton;
    private Button copyButton;
    private Button shareButton;

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
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Natural Translator AI", 27, TEXT, true);
        root.addView(title);

        TextView subtitle = text("Νευρωνική μετάφραση στη συσκευή • χωρίς API key ή συνδρομή", 15, MUTED, false);
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
            }
        });

        TextView inputLabel = label("Κείμενο");
        LinearLayout.LayoutParams ilp = matchWrap();
        ilp.setMargins(0, dp(18), 0, 0);
        root.addView(inputLabel, ilp);

        input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(115, 126, 143));
        input.setHint("Γράψε ή επικόλλησε το κείμενο που θέλεις να μεταφράσεις…");
        input.setTextSize(17);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(5);
        input.setMaxLines(12);
        input.setPadding(dp(14), dp(13), dp(14), dp(13));
        input.setBackground(panelDrawable(PANEL));
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
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

        TextView privacy = text(
            "Με τεχνολογία Google ML Kit. Η μετάφραση εκτελείται στη συσκευή μετά τη λήψη του γλωσσικού μοντέλου. Για πολύ απαιτητικά λογοτεχνικά ή εξειδικευμένα κείμενα, ένα online LLM μπορεί να δώσει ακόμη πιο φυσική απόδοση.",
            12, MUTED, false
        );
        LinearLayout.LayoutParams privacyLp = matchWrap();
        privacyLp.setMargins(0, dp(18), 0, 0);
        root.addView(privacy, privacyLp);

        translateButton.setOnClickListener(v -> translate());
        copyButton.setOnClickListener(v -> copyResult());
        shareButton.setOnClickListener(v -> shareResult());
        speakButton.setOnClickListener(v -> speakResult());
        clear.setOnClickListener(v -> {
            input.setText("");
            output.setText("—");
            setStatus("Έτοιμος.", false);
            input.requestFocus();
        });

        setContentView(scroll);
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
            output.setText(text);
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
                        output.setText(translated);
                        output.setTag(to.code);
                        setStatus("Ολοκληρώθηκε. Το μοντέλο μένει στη συσκευή για επόμενη χρήση.", false);
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

    private void setBusy(boolean busy) {
        translateButton.setEnabled(!busy);
        translateButton.setText(busy ? "Περίμενε…" : "Μετάφραση");
    }

    private void setStatus(String message, boolean error) {
        status.setText(message);
        status.setTextColor(error ? ERROR : (message.startsWith("Ολοκληρώθηκε") ? OK : MUTED));
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
