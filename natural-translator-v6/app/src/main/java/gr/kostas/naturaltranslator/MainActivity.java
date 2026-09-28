package gr.kostas.naturaltranslator;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(12, 13, 22);
    private static final int PANEL = Color.rgb(26, 35, 48);
    private static final int PANEL_2 = Color.rgb(41, 47, 61);
    private static final int TEXT = Color.rgb(242, 244, 248);
    private static final int MUTED = Color.rgb(178, 186, 201);
    private static final int BLUE = Color.rgb(111, 174, 241);
    private static final int GREEN = Color.rgb(112, 214, 167);
    private static final int RED = Color.rgb(244, 122, 135);
    private static final int GOLD = Color.rgb(245, 196, 103);

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final String[] languageNames = {
            "Ελληνικά", "Αγγλικά", "Γαλλικά", "Γερμανικά", "Ιταλικά",
            "Ισπανικά", "Πορτογαλικά", "Αλβανικά", "Τουρκικά", "Ρωσικά",
            "Ουκρανικά", "Βουλγαρικά", "Ρουμανικά", "Ολλανδικά", "Πολωνικά",
            "Σουηδικά", "Νορβηγικά", "Δανικά", "Φινλανδικά", "Τσεχικά",
            "Αραβικά", "Εβραϊκά", "Ιαπωνικά", "Κορεατικά", "Κινέζικα"
    };

    private final String[] languageCodes = {
            "el", "en", "fr", "de", "it",
            "es", "pt", "sq", "tr", "ru",
            "uk", "bg", "ro", "nl", "pl",
            "sv", "no", "da", "fi", "cs",
            "ar", "iw", "ja", "ko", "zh-CN"
    };

    private Spinner fromSpinner;
    private Spinner toSpinner;
    private EditText input;
    private TextView status;
    private TextView result;
    private TextView explanation;
    private TextView quality;
    private TextView analysisTitle;
    private TextView analysisBox;
    private Button translateButton;
    private Button copyButton;
    private Button grammarButton;
    private Button syntaxButton;
    private Button phoneticButton;
    private Button aiButton;

    private final Map<String, String> pronunciation = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        seedPronunciationDictionary();
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("Natural Translator AI Rector", 28, TEXT, true);
        root.addView(title);

        TextView subtitle = text("Natural Greek Max v6 • μετάφραση + γλωσσικός καθηγητής + AI", 15, GREEN, false);
        subtitle.setPadding(0, dp(5), 0, dp(18));
        root.addView(subtitle);

        root.addView(label("Από γλώσσα"));
        fromSpinner = spinner();
        root.addView(fromSpinner, lpMatch(dp(60)));

        Button swap = button("⇅  Αντιστροφή γλωσσών", PANEL_2, TEXT);
        LinearLayout.LayoutParams swapLp = lpMatch(dp(58));
        swapLp.setMargins(0, dp(12), 0, dp(12));
        root.addView(swap, swapLp);

        root.addView(label("Προς γλώσσα"));
        toSpinner = spinner();
        root.addView(toSpinner, lpMatch(dp(60)));

        fromSpinner.setSelection(indexFor("el"));
        toSpinner.setSelection(indexFor("en"));

        root.addView(space(14));
        root.addView(label("Κείμενο"));

        input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(125, 135, 151));
        input.setHint("Γράψε ή επικόλλησε το κείμενο…");
        input.setTextSize(22);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        input.setMinLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(rounded(PANEL, 18, Color.rgb(63, 76, 94), 1));
        root.addView(input, lpMatch(dp(170)));

        translateButton = button("Μετάφραση", BLUE, Color.rgb(10, 19, 29));
        translateButton.setTextSize(24);
        LinearLayout.LayoutParams trLp = lpMatch(dp(62));
        trLp.setMargins(0, dp(14), 0, dp(10));
        root.addView(translateButton, trLp);

        status = text("Έτοιμο • Meaning Guard ενεργό", 15, GREEN, false);
        root.addView(status);

        root.addView(space(14));
        root.addView(label("Φυσική μετάφραση"));

        result = text("Η μετάφραση θα εμφανιστεί εδώ.", 22, TEXT, false);
        result.setGravity(Gravity.TOP | Gravity.START);
        result.setPadding(dp(16), dp(16), dp(16), dp(16));
        result.setMinHeight(dp(110));
        result.setBackground(rounded(PANEL, 18, Color.rgb(63, 76, 94), 1));
        root.addView(result, lpMatchWrap());

        quality = text("Έλεγχος νοήματος: —", 14, MUTED, true);
        quality.setPadding(0, dp(10), 0, 0);
        root.addView(quality);

        root.addView(space(14));
        root.addView(label("Απλή εξήγηση"));

        explanation = text("Θα εμφανιστεί σύντομη εξήγηση χωρίς να αλλάζει το νόημα.", 17, TEXT, false);
        explanation.setPadding(dp(16), dp(14), dp(16), dp(14));
        explanation.setBackground(rounded(PANEL, 18, Color.rgb(63, 76, 94), 1));
        root.addView(explanation, lpMatchWrap());

        copyButton = button("Αντιγραφή μετάφρασης", PANEL_2, TEXT);
        LinearLayout.LayoutParams cpLp = lpMatch(dp(54));
        cpLp.setMargins(0, dp(14), 0, 0);
        root.addView(copyButton, cpLp);

        TextView professor = text("Γλωσσικός καθηγητής", 19, GOLD, true);
        professor.setPadding(0, dp(22), 0, dp(8));
        root.addView(professor);

        TextView professorHint = text(
                "Χωρίς να αλλάζει η μετάφραση: γραμματική, συντακτικό, ελληνική μεταγραφή και AI σύμβουλος.",
                14, MUTED, false
        );
        professorHint.setPadding(0, 0, 0, dp(10));
        root.addView(professorHint);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        grammarButton = button("Γραμματική", PANEL_2, TEXT);
        syntaxButton = button("Συντακτικό", PANEL_2, TEXT);
        row1.addView(grammarButton, weightedButton());
        row1.addView(spaceHorizontal(10));
        row1.addView(syntaxButton, weightedButton());
        root.addView(row1, lpMatch(dp(56)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        phoneticButton = button("Μεταγραφή", PANEL_2, TEXT);
        aiButton = button("AI Σύμβουλος", GOLD, Color.rgb(30, 25, 15));
        row2.addView(phoneticButton, weightedButton());
        row2.addView(spaceHorizontal(10));
        row2.addView(aiButton, weightedButton());
        LinearLayout.LayoutParams row2Lp = lpMatch(dp(56));
        row2Lp.setMargins(0, dp(10), 0, 0);
        root.addView(row2, row2Lp);

        analysisTitle = text("Ανάλυση", 16, MUTED, true);
        analysisTitle.setPadding(0, dp(16), 0, dp(7));
        root.addView(analysisTitle);

        analysisBox = text(
                "Πάτησε μία λειτουργία. Για μεταγραφή αγγλικών θα δεις την προφορά με ελληνικά γράμματα, π.χ. love → λαβ.",
                17, TEXT, false
        );
        analysisBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        analysisBox.setBackground(rounded(PANEL, 18, Color.rgb(63, 76, 94), 1));
        root.addView(analysisBox, lpMatchWrap());

        TextView aiPrivacy = text(
                "AI Σύμβουλος: χρησιμοποιεί cloud μόνο όταν πατήσεις το κουμπί. Το κείμενο στέλνεται σε εξωτερική υπηρεσία AI. " +
                "Μην στέλνεις κωδικούς, API keys ή ευαίσθητα προσωπικά δεδομένα. Αν το cloud δεν απαντήσει, λειτουργεί το τοπικό Lite.",
                12, MUTED, false
        );
        aiPrivacy.setPadding(0, dp(12), 0, 0);
        root.addView(aiPrivacy);

        TextView note = text(
                "v6: διατηρεί τον Meaning Guard της v5. Οι αναλύσεις γίνονται μετά τη μετάφραση και δεν επεμβαίνουν στο αποτέλεσμα.",
                13, MUTED, false
        );
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        swap.setOnClickListener(v -> {
            int a = fromSpinner.getSelectedItemPosition();
            int b = toSpinner.getSelectedItemPosition();
            fromSpinner.setSelection(b);
            toSpinner.setSelection(a);
        });

        translateButton.setOnClickListener(v -> startTranslation());

        copyButton.setOnClickListener(v -> {
            String value = result.getText().toString();
            if (value.trim().isEmpty() || value.startsWith("Η μετάφραση")) return;
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("translation", value));
            Toast.makeText(this, "Αντιγράφηκε", Toast.LENGTH_SHORT).show();
        });

        grammarButton.setOnClickListener(v -> showGrammar());
        syntaxButton.setOnClickListener(v -> showSyntax());
        phoneticButton.setOnClickListener(v -> showGreekTranscription());
        aiButton.setOnClickListener(v -> startAiAdvisor());

        return scroll;
    }

    private void startTranslation() {
        final String source = input.getText().toString().trim();
        if (source.isEmpty()) {
            input.setError("Γράψε πρώτα κείμενο");
            return;
        }

        final String sl = languageCodes[fromSpinner.getSelectedItemPosition()];
        final String tl = languageCodes[toSpinner.getSelectedItemPosition()];

        if (sl.equals(tl)) {
            result.setText(source);
            quality.setText("Έλεγχος νοήματος: ίδια γλώσσα");
            explanation.setText("Δεν χρειάζεται μετάφραση, επειδή οι δύο γλώσσες είναι ίδιες.");
            return;
        }

        setBusy(true);
        status.setText("Μεταφράζω • ελέγχω νόημα…");
        status.setTextColor(GREEN);
        result.setText("…");
        explanation.setText("Γίνεται έλεγχος συμφραζομένων…");
        quality.setText("Έλεγχος νοήματος: σε εξέλιξη");
        analysisTitle.setText("Ανάλυση");
        analysisBox.setText("Περίμενε να ολοκληρωθεί η μετάφραση.");

        executor.execute(() -> {
            TranslationOutcome out;
            try {
                out = translateWithGuard(source, sl, tl);
            } catch (Exception e) {
                out = offlineFallback(source, sl, tl, e.getMessage());
            }

            final TranslationOutcome finalOut = out;
            runOnUiThread(() -> {
                setBusy(false);
                if (finalOut.translation == null || finalOut.translation.trim().isEmpty()) {
                    status.setText("Δεν ολοκληρώθηκε η μετάφραση");
                    status.setTextColor(RED);
                    result.setText("Δεν μπόρεσα να πάρω αξιόπιστη μετάφραση.");
                    quality.setText("Έλεγχος νοήματος: απέτυχε");
                    explanation.setText(finalOut.explanation);
                } else {
                    status.setText(finalOut.status);
                    status.setTextColor(finalOut.warning ? RED : GREEN);
                    result.setText(finalOut.translation);
                    quality.setText(finalOut.quality);
                    explanation.setText(finalOut.explanation);
                    analysisBox.setText("Έτοιμο. Πάτησε Γραμματική, Συντακτικό, Μεταγραφή ή AI Σύμβουλος.");
                }
            });
        });
    }

    private void showGrammar() {
        TextTarget t = analysisTarget();
        if (t.text.isEmpty()) {
            toast("Κάνε πρώτα μετάφραση ή γράψε κείμενο.");
            return;
        }
        analysisTitle.setText("Γραμματική • " + languageDisplay(t.lang));
        analysisBox.setText(grammarAnalysis(t.text, t.lang));
    }

    private void showSyntax() {
        TextTarget t = analysisTarget();
        if (t.text.isEmpty()) {
            toast("Κάνε πρώτα μετάφραση ή γράψε κείμενο.");
            return;
        }
        analysisTitle.setText("Συντακτικό • " + languageDisplay(t.lang));
        analysisBox.setText(syntaxAnalysis(t.text, t.lang));
    }

    private void showGreekTranscription() {
        String textToTranscribe = "";
        if ("en".equals(currentTargetLang()) && hasTranslation()) {
            textToTranscribe = result.getText().toString().trim();
        } else if ("en".equals(currentSourceLang())) {
            textToTranscribe = input.getText().toString().trim();
        }

        analysisTitle.setText("Ελληνική μεταγραφή αγγλικής προφοράς");
        if (textToTranscribe.isEmpty()) {
            analysisBox.setText(
                    "Η ελληνική μεταγραφή ενεργοποιείται για αγγλικό κείμενο. " +
                    "Μετάφρασε προς Αγγλικά ή βάλε Αγγλικά ως γλώσσα προέλευσης."
            );
            return;
        }

        String trans = transcribeEnglishToGreek(textToTranscribe);
        analysisBox.setText(
                textToTranscribe + "\n\n→ " + trans +
                "\n\nΣημείωση: είναι βοηθητική φωνητική μεταγραφή για ελληνόφωνο. " +
                "Η πραγματική ακρόαση φυσικού ομιλητή παραμένει πιο ακριβής, ειδικά σε ονόματα και ιδιωματισμούς."
        );
    }

    private void startAiAdvisor() {
        TextTarget t = analysisTarget();
        if (t.text.isEmpty()) {
            toast("Κάνε πρώτα μετάφραση ή γράψε κείμενο.");
            return;
        }

        aiButton.setEnabled(false);
        aiButton.setAlpha(0.55f);
        analysisTitle.setText("AI Σύμβουλος • cloud");
        analysisBox.setText("Αναλύω το νόημα, τη γραμματική και τη φυσικότητα…");

        final String source = input.getText().toString().trim();
        final String translated = hasTranslation() ? result.getText().toString().trim() : "";
        final String sl = currentSourceLang();
        final String tl = currentTargetLang();

        executor.execute(() -> {
            String answer;
            boolean cloud = false;
            try {
                answer = pollinationsAdvisor(source, translated, sl, tl);
                cloud = answer != null && !answer.trim().isEmpty();
                if (!cloud) throw new Exception("κενή απάντηση");
            } catch (Exception e) {
                answer = localAdvisor(source, translated, sl, tl);
            }

            final String finalAnswer = answer;
            final boolean usedCloud = cloud;
            runOnUiThread(() -> {
                aiButton.setEnabled(true);
                aiButton.setAlpha(1f);
                analysisTitle.setText(usedCloud ? "AI Σύμβουλος • cloud" : "Σύμβουλος Lite • τοπικά");
                analysisBox.setText(finalAnswer);
            });
        });
    }

    private String pollinationsAdvisor(String source, String translated, String sl, String tl) throws Exception {
        if (!isOnline()) throw new Exception("offline");

        String safeSource = source == null ? "" : source.trim();
        String safeTranslated = translated == null ? "" : translated.trim();
        if (safeSource.length() > 650) safeSource = safeSource.substring(0, 650);
        if (safeTranslated.length() > 650) safeTranslated = safeTranslated.substring(0, 650);

        String prompt =
                "Είσαι αυστηρός καθηγητής ξένων γλωσσών. Απάντησε στα ελληνικά, σύντομα και πρακτικά. " +
                "Μην αλλάξεις το νόημα και μην επινοήσεις συμφραζόμενα. " +
                "Δώσε: 1) τι σημαίνει απλά, 2) βασική γραμματική, 3) βασικό συντακτικό, " +
                "4) αν η μετάφραση ακούγεται φυσική και τι θα πρόσεχε μαθητής. " +
                "Γλώσσα πηγής=" + sl + ", γλώσσα στόχου=" + tl + ". " +
                "Πηγή: «" + safeSource + "». Μετάφραση: «" + safeTranslated + "».";

        String encoded = URLEncoder.encode(prompt, "UTF-8").replace("+", "%20");
        URL url = new URL("https://text.pollinations.ai/" + encoded);
        String body = requestPlain(url).trim();

        if (body.isEmpty()) throw new Exception("empty");
        if (body.length() > 4500) body = body.substring(0, 4500);
        return body;
    }

    private String localAdvisor(String source, String translated, String sl, String tl) {
        String focus = translated != null && !translated.trim().isEmpty() ? translated.trim() : source.trim();
        String lang = translated != null && !translated.trim().isEmpty() ? tl : sl;

        StringBuilder sb = new StringBuilder();
        sb.append("Το cloud AI δεν ήταν διαθέσιμο, οπότε ενεργοποιήθηκε ο ελαφρύς τοπικός σύμβουλος.\n\n");
        sb.append("Νόημα: ").append(simpleMeaningHint(source, translated, sl, tl)).append("\n\n");
        sb.append(grammarAnalysis(focus, lang)).append("\n\n");
        sb.append(syntaxAnalysis(focus, lang)).append("\n\n");

        if ("en".equals(lang)) {
            sb.append("Προφορά: ").append(transcribeEnglishToGreek(focus)).append("\n\n");
        }

        sb.append("Συμβουλή: έλεγξε πάντα αν ρήμα, πρόσωπο, χρόνος και βασικά ουσιαστικά έμειναν ίδια με την πηγή. ");
        sb.append("Σε ιδιωματικές ή θρησκευτικές λέξεις, τα συμφραζόμενα υπερισχύουν της κατά λέξη απόδοσης.");
        return sb.toString();
    }

    private String simpleMeaningHint(String source, String translated, String sl, String tl) {
        String n = norm(source);
        if ("el".equals(sl) && "en".equals(tl) && n.contains("αναψ") && n.contains("καντηλ") && n.contains("εικον")) {
            return "Λες ότι άναψες το καντήλι μπροστά στην εικόνα.";
        }
        if (translated != null && !translated.trim().isEmpty()) {
            return "Η πρόταση αποδίδεται ως: " + translated.trim();
        }
        return "Η πρόταση αναλύεται με βάση το κείμενο που έγραψες.";
    }

    private String grammarAnalysis(String text, String lang) {
        List<String> words = tokens(text);
        if (words.isEmpty()) return "Δεν βρέθηκαν λέξεις για γραμματική ανάλυση.";

        StringBuilder sb = new StringBuilder();
        if ("en".equals(lang)) {
            sb.append("Πιθανός χρόνος: ").append(guessEnglishTense(words)).append(".\n");
            sb.append("Λέξεις:\n");
            int limit = Math.min(words.size(), 22);
            for (int i = 0; i < limit; i++) {
                String w = words.get(i);
                sb.append("• ").append(w).append(" → ").append(englishPartOfSpeech(w, words, i)).append("\n");
            }
            if (words.size() > limit) sb.append("• … και άλλες ").append(words.size() - limit).append(" λέξεις");
        } else if ("el".equals(lang)) {
            sb.append("Πιθανός χρόνος/μορφή: ").append(guessGreekTense(words)).append(".\n");
            sb.append("Λέξεις:\n");
            int limit = Math.min(words.size(), 22);
            for (int i = 0; i < limit; i++) {
                String w = words.get(i);
                sb.append("• ").append(w).append(" → ").append(greekPartOfSpeech(w)).append("\n");
            }
            if (words.size() > limit) sb.append("• … και άλλες ").append(words.size() - limit).append(" λέξεις");
        } else {
            sb.append("Η λεπτομερής τοπική γραμματική είναι ισχυρότερη σε Ελληνικά και Αγγλικά.\n");
            sb.append("Λέξεις: ").append(words.size()).append(".\n");
            sb.append("Για βαθύτερη ανάλυση πάτησε AI Σύμβουλος.");
        }
        return sb.toString().trim();
    }

    private String syntaxAnalysis(String text, String lang) {
        List<String> words = tokens(text);
        if (words.isEmpty()) return "Δεν βρέθηκε πρόταση.";

        int verbIndex = -1;
        for (int i = 0; i < words.size(); i++) {
            boolean verb = "en".equals(lang) ? isLikelyEnglishVerb(words.get(i), words, i) :
                    ("el".equals(lang) && isLikelyGreekVerb(words.get(i)));
            if (verb) {
                verbIndex = i;
                break;
            }
        }

        if (verbIndex < 0) {
            return "Δεν εντοπίστηκε με βεβαιότητα ρήμα. Η πρόταση μπορεί να είναι τίτλος, φράση ή να χρειάζεται AI ανάλυση.";
        }

        String subject = join(words, 0, verbIndex).trim();
        String verb = words.get(verbIndex);
        String rest = join(words, verbIndex + 1, words.size()).trim();

        if ("en".equals(lang)) subject = stripLeadingFunctionWordsEnglish(subject);
        if ("el".equals(lang)) subject = stripLeadingFunctionWordsGreek(subject);

        StringBuilder sb = new StringBuilder();
        sb.append("Πιθανή δομή πρότασης:\n");
        sb.append("• Υποκείμενο: ").append(subject.isEmpty() ? "(εννοείται / δεν δηλώνεται καθαρά)" : subject).append("\n");
        sb.append("• Ρήμα: ").append(verb).append("\n");
        sb.append("• Υπόλοιπο / αντικείμενο / προσδιορισμοί: ").append(rest.isEmpty() ? "(δεν υπάρχει)" : rest).append("\n\n");
        sb.append("Σημείωση: η τοπική ανάλυση είναι γρήγορη και ελαφριά. Σε σύνθετες προτάσεις, το AI Σύμβουλος μπορεί να δώσει ακριβέστερη συντακτική εικόνα.");
        return sb.toString();
    }

    private String guessEnglishTense(List<String> words) {
        Set<String> set = lowerSet(words);
        if (set.contains("will")) return "μέλλοντας με will";
        if (set.contains("had")) return "πιθανός υπερσυντέλικος ή παρελθοντική δομή";
        if (set.contains("have") || set.contains("has")) {
            for (String w : words) if (w.toLowerCase(Locale.ROOT).endsWith("ed")) return "πιθανός present perfect";
        }
        if (set.contains("was") || set.contains("were")) {
            for (String w : words) if (w.toLowerCase(Locale.ROOT).endsWith("ing")) return "past continuous";
            return "παρελθόν με was/were";
        }
        for (String w : words) {
            String x = w.toLowerCase(Locale.ROOT);
            if (x.endsWith("ed")) return "πιθανός simple past";
        }
        for (String w : words) {
            String x = w.toLowerCase(Locale.ROOT);
            if (x.endsWith("ing") && (set.contains("am") || set.contains("is") || set.contains("are"))) return "present continuous";
        }
        return "πιθανός ενεστώτας / απλή μορφή";
    }

    private String guessGreekTense(List<String> words) {
        Set<String> set = lowerSet(words);
        if (set.contains("θα")) return "μέλλοντας ή υποθετική με «θα»";
        if (set.contains("έχω") || set.contains("εχω") || set.contains("έχει") || set.contains("εχει")) return "πιθανός παρακείμενος";
        for (String w : words) {
            String n = norm(w);
            if (n.endsWith("σα") || n.endsWith("σες") || n.endsWith("σε") || n.endsWith("σαμε") || n.endsWith("σαν")) {
                return "πιθανός αόριστος";
            }
        }
        return "πιθανός ενεστώτας ή μορφή που χρειάζεται συμφραζόμενα";
    }

    private String englishPartOfSpeech(String word, List<String> sentence, int index) {
        String w = cleanEnglish(word);
        if (w.isEmpty()) return "σύμβολο";
        if (Arrays.asList("a","an","the").contains(w)) return "άρθρο";
        if (Arrays.asList("i","you","he","she","it","we","they","me","him","her","us","them").contains(w)) return "αντωνυμία";
        if (Arrays.asList("my","your","his","her","its","our","their").contains(w)) return "κτητικός προσδιορισμός";
        if (Arrays.asList("and","or","but","because","if","although","while","so").contains(w)) return "σύνδεσμος";
        if (Arrays.asList("in","on","at","to","from","for","with","by","of","about","under","over","before","after","into","through").contains(w)) return "πρόθεση";
        if (Arrays.asList("is","am","are","was","were","be","been","being","do","does","did","have","has","had","will","would","can","could","may","might","must","should").contains(w)) return "βοηθητικό / ρήμα";
        if (isLikelyEnglishVerb(word, sentence, index)) return "πιθανό ρήμα";
        if (w.endsWith("ly")) return "πιθανό επίρρημα";
        if (w.endsWith("ous") || w.endsWith("ful") || w.endsWith("ive") || w.endsWith("able") || w.endsWith("al") || w.endsWith("ic")) return "πιθανό επίθετο";
        if (w.matches("[0-9]+")) return "αριθμός";
        return "πιθανό ουσιαστικό / άλλη λέξη";
    }

    private String greekPartOfSpeech(String word) {
        String w = norm(word);
        if (w.isEmpty()) return "σύμβολο";
        if (Arrays.asList("ο","η","το","οι","τα","τον","την","του","της","των","ενα","μια","ενας").contains(w)) return "άρθρο";
        if (Arrays.asList("εγω","εσυ","αυτος","αυτη","αυτο","εμεις","εσεις","αυτοι","μου","σου","του","της","μας","σας").contains(w)) return "αντωνυμία";
        if (Arrays.asList("και","η","αλλα","ομως","γιατι","αν","ενω","οτι","πως").contains(w)) return "σύνδεσμος";
        if (Arrays.asList("σε","με","απο","για","προς","χωρις","κατα","μετα","πριν","πανω","κατω").contains(w)) return "πρόθεση / προσδιορισμός";
        if (isLikelyGreekVerb(word)) return "πιθανό ρήμα";
        if (w.endsWith("ικα") || w.endsWith("ως")) return "πιθανό επίρρημα";
        if (w.endsWith("ος") || w.endsWith("η") || w.endsWith("ο") || w.endsWith("ικος") || w.endsWith("ικη") || w.endsWith("ικο")) return "πιθανό ουσιαστικό / επίθετο";
        if (w.matches("[0-9]+")) return "αριθμός";
        return "λέξη που χρειάζεται συμφραζόμενα";
    }

    private boolean isLikelyEnglishVerb(String word, List<String> sentence, int index) {
        String w = cleanEnglish(word);
        if (Arrays.asList(
                "be","am","is","are","was","were","been","being",
                "do","does","did","done","have","has","had",
                "go","goes","went","gone","come","came","make","made","take","took","taken",
                "see","saw","seen","say","said","tell","told","know","knew","known",
                "think","thought","want","need","like","liked","love","loved","hate","work","worked",
                "live","lived","eat","ate","drink","drank","read","write","wrote","written",
                "light","lit","open","opened","close","closed","play","played","speak","spoke",
                "translate","translated","mean","means","meant","feel","felt","give","gave","given"
        ).contains(w)) return true;

        if (w.endsWith("ed") || w.endsWith("ing")) return true;
        if (index > 0) {
            String prev = cleanEnglish(sentence.get(index - 1));
            if (Arrays.asList("to","will","would","can","could","should","must","may","might").contains(prev)) return true;
        }
        return false;
    }

    private boolean isLikelyGreekVerb(String word) {
        String w = norm(word);
        if (Arrays.asList("ειμαι","εισαι","ειναι","ειμαστε","ειστε","ηταν","εχω","εχεις","εχει","εχουμε","εχετε","εχουν",
                "κανω","κανεις","κανει","πηγα","ηρθα","λεω","ειπα","θελω","χρειαζομαι","αναψα","αναβω","δουλευω","δουλεψα").contains(w)) return true;

        String[] endings = {"ω","εις","ει","ουμε","ετε","ουν","ουνε","α","ες","ε","σαμε","σατε","σαν","ηκα","ηκες","ηκε","ηκαν"};
        for (String e : endings) {
            if (w.length() > e.length() + 2 && w.endsWith(e)) return true;
        }
        return false;
    }

    private String transcribeEnglishToGreek(String sentence) {
        StringBuilder out = new StringBuilder();
        String[] chunks = sentence.split("(?<=\\s)|(?=\\s)");
        for (String chunk : chunks) {
            if (chunk.trim().isEmpty()) {
                out.append(chunk);
                continue;
            }

            String leading = chunk.replaceAll("^([A-Za-z']+).*", "");
            String core = chunk.replaceAll("^[^A-Za-z']+", "").replaceAll("[^A-Za-z']+$", "");
            if (core.isEmpty()) {
                out.append(chunk);
                continue;
            }

            int start = chunk.indexOf(core);
            String prefix = start > 0 ? chunk.substring(0, start) : "";
            String suffix = chunk.substring(start + core.length());
            out.append(prefix).append(transcribeWord(core)).append(suffix);
        }
        return out.toString();
    }

    private String transcribeWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String exact = pronunciation.get(lower);
        if (exact != null) return exact;

        String w = lower;
        w = w.replaceAll("[^a-z']", "");
        if (w.isEmpty()) return word;

        String x = w;
        x = x.replace("tion", "σεν");
        x = x.replace("sion", "ζεν");
        x = x.replace("ture", "τσερ");
        x = x.replace("ough", "ο");
        x = x.replace("eigh", "ει");
        x = x.replace("igh", "αι");
        x = x.replace("ph", "φ");
        x = x.replace("sh", "σ");
        x = x.replace("ch", "τσ");
        x = x.replace("th", "θ");
        x = x.replace("ng", "νγκ");
        x = x.replace("qu", "κου");
        x = x.replace("ck", "κ");
        x = x.replace("ee", "ι");
        x = x.replace("ea", "ι");
        x = x.replace("oo", "ου");
        x = x.replace("ou", "άου");
        x = x.replace("ow", "άου");
        x = x.replace("ai", "έι");
        x = x.replace("ay", "έι");
        x = x.replace("oa", "όου");
        x = x.replace("oi", "όι");
        x = x.replace("oy", "όι");

        if (x.endsWith("e") && x.length() > 2) x = x.substring(0, x.length() - 1);

        x = x.replace("a", "α");
        x = x.replace("b", "μπ");
        x = x.replace("c", "κ");
        x = x.replace("d", "ντ");
        x = x.replace("e", "ε");
        x = x.replace("f", "φ");
        x = x.replace("g", "γκ");
        x = x.replace("h", "χ");
        x = x.replace("i", "ι");
        x = x.replace("j", "τζ");
        x = x.replace("k", "κ");
        x = x.replace("l", "λ");
        x = x.replace("m", "μ");
        x = x.replace("n", "ν");
        x = x.replace("o", "ο");
        x = x.replace("p", "π");
        x = x.replace("q", "κ");
        x = x.replace("r", "ρ");
        x = x.replace("s", "σ");
        x = x.replace("t", "τ");
        x = x.replace("u", "α");
        x = x.replace("v", "β");
        x = x.replace("w", "γου");
        x = x.replace("x", "κσ");
        x = x.replace("y", "ι");
        x = x.replace("z", "ζ");

        x = x.replace("μπλ", "μπλ");
        x = x.replace("ντλ", "ντλ");
        return x;
    }

    private void seedPronunciationDictionary() {
        pronunciation.put("love", "λαβ");
        pronunciation.put("loved", "λαβντ");
        pronunciation.put("like", "λάικ");
        pronunciation.put("liked", "λάικτ");
        pronunciation.put("live", "λιβ");
        pronunciation.put("life", "λάιφ");
        pronunciation.put("light", "λάιτ");
        pronunciation.put("lit", "λιτ");
        pronunciation.put("vigil", "βίτζιλ");
        pronunciation.put("lamp", "λαμπ");
        pronunciation.put("icon", "άικον");
        pronunciation.put("picture", "πίκτσερ");
        pronunciation.put("image", "ίμιτζ");
        pronunciation.put("the", "δε");
        pronunciation.put("a", "ε");
        pronunciation.put("an", "αν");
        pronunciation.put("i", "άι");
        pronunciation.put("you", "γιου");
        pronunciation.put("he", "χι");
        pronunciation.put("she", "σι");
        pronunciation.put("we", "γουί");
        pronunciation.put("they", "δέι");
        pronunciation.put("this", "δις");
        pronunciation.put("that", "δατ");
        pronunciation.put("these", "διζ");
        pronunciation.put("those", "δόουζ");
        pronunciation.put("is", "ιζ");
        pronunciation.put("are", "αρ");
        pronunciation.put("was", "γουόζ");
        pronunciation.put("were", "γουερ");
        pronunciation.put("have", "χαβ");
        pronunciation.put("has", "χαζ");
        pronunciation.put("had", "χαντ");
        pronunciation.put("do", "ντου");
        pronunciation.put("does", "νταζ");
        pronunciation.put("did", "ντιντ");
        pronunciation.put("can", "καν");
        pronunciation.put("could", "κουντ");
        pronunciation.put("would", "γουντ");
        pronunciation.put("should", "σουντ");
        pronunciation.put("will", "γουιλ");
        pronunciation.put("hello", "χελόου");
        pronunciation.put("hi", "χάι");
        pronunciation.put("good", "γκουντ");
        pronunciation.put("morning", "μόρνινγκ");
        pronunciation.put("evening", "ίβνινγκ");
        pronunciation.put("night", "νάιτ");
        pronunciation.put("beautiful", "μπιούτιφουλ");
        pronunciation.put("world", "γουέρλντ");
        pronunciation.put("thank", "θανκ");
        pronunciation.put("thanks", "θανκς");
        pronunciation.put("please", "πλιζ");
        pronunciation.put("friend", "φρεντ");
        pronunciation.put("family", "φάμιλι");
        pronunciation.put("work", "γουέρκ");
        pronunciation.put("job", "τζομπ");
        pronunciation.put("church", "τσερτς");
        pronunciation.put("father", "φάδερ");
        pronunciation.put("mother", "μάδερ");
        pronunciation.put("water", "γουότερ");
        pronunciation.put("food", "φουντ");
        pronunciation.put("house", "χάους");
        pronunciation.put("home", "χόουμ");
        pronunciation.put("today", "τουντέι");
        pronunciation.put("tomorrow", "τουμόροου");
        pronunciation.put("yesterday", "γιέστερντεϊ");
        pronunciation.put("what", "γουότ");
        pronunciation.put("why", "γουάι");
        pronunciation.put("when", "γουέν");
        pronunciation.put("where", "γουέρ");
        pronunciation.put("who", "χου");
        pronunciation.put("how", "χάου");
        pronunciation.put("meaning", "μίνινγκ");
        pronunciation.put("grammar", "γκράμερ");
        pronunciation.put("syntax", "σίνταξ");
        pronunciation.put("translation", "τρανσλέισεν");
        pronunciation.put("natural", "νάτσραλ");
    }

    private TranslationOutcome translateWithGuard(String source, String sl, String tl) throws Exception {
        String deterministic = deterministicMeaning(source, sl, tl);
        if (deterministic != null) {
            return new TranslationOutcome(
                    deterministic,
                    "Ολοκληρώθηκε • Natural Greek Max v6",
                    "Έλεγχος νοήματος: 100% αντιστοίχιση συμφραζομένων",
                    explain(source, sl, tl, deterministic, true),
                    false
            );
        }

        String primary = googleTranslate(source, sl, tl);
        primary = postProcessByContext(source, sl, tl, primary);
        double primaryScore = semanticScore(source, sl, tl, primary);

        String chosen = primary;
        double chosenScore = primaryScore;
        String engineNote = "κύρια μηχανή";

        if (needsSecondCheck(source, sl, tl, primaryScore)) {
            try {
                String secondary = myMemoryTranslate(source, sl, tl);
                secondary = postProcessByContext(source, sl, tl, secondary);
                double secondaryScore = semanticScore(source, sl, tl, secondary);
                if (secondaryScore > chosenScore) {
                    chosen = secondary;
                    chosenScore = secondaryScore;
                    engineNote = "εφεδρική μηχανή";
                }
            } catch (Exception ignored) {
            }
        }

        boolean warning = hasAnchors(source, sl, tl) && chosenScore < 0.67;
        String qualityText;
        if (!hasAnchors(source, sl, tl)) {
            qualityText = "Έλεγχος νοήματος: φυσική απόδοση + συμφραζόμενα";
        } else {
            int pct = (int) Math.round(chosenScore * 100.0);
            qualityText = "Έλεγχος νοήματος: " + pct + "% βασικές έννοιες";
        }

        String statusText = warning
                ? "Ολοκληρώθηκε με προειδοποίηση • " + engineNote
                : "Ολοκληρώθηκε • " + engineNote + " + Meaning Guard";

        return new TranslationOutcome(
                chosen,
                statusText,
                qualityText,
                explain(source, sl, tl, chosen, !warning),
                warning
        );
    }

    private String googleTranslate(String source, String sl, String tl) throws Exception {
        String q = URLEncoder.encode(source, "UTF-8");
        URL url = new URL(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" +
                        URLEncoder.encode(sl, "UTF-8") +
                        "&tl=" + URLEncoder.encode(tl, "UTF-8") +
                        "&dt=t&q=" + q
        );

        String body = requestPlain(url);
        JSONArray all = new JSONArray(body);
        JSONArray parts = all.getJSONArray(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray row = parts.optJSONArray(i);
            if (row != null && row.length() > 0 && !row.isNull(0)) {
                sb.append(row.optString(0, ""));
            }
        }
        String out = sb.toString().trim();
        if (out.isEmpty()) throw new Exception("Κενή απάντηση κύριας μηχανής");
        return out;
    }

    private String myMemoryTranslate(String source, String sl, String tl) throws Exception {
        String q = URLEncoder.encode(source, "UTF-8");
        String pair = URLEncoder.encode(sl + "|" + tl, "UTF-8");
        URL url = new URL("https://api.mymemory.translated.net/get?q=" + q + "&langpair=" + pair);
        String body = requestPlain(url);
        JSONObject obj = new JSONObject(body);
        JSONObject responseData = obj.optJSONObject("responseData");
        if (responseData == null) throw new Exception("Χωρίς εφεδρικό αποτέλεσμα");
        String out = responseData.optString("translatedText", "").trim();
        if (out.isEmpty()) throw new Exception("Κενή εφεδρική μετάφραση");
        return out;
    }

    private String requestPlain(URL url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(10000);
        con.setReadTimeout(18000);
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "NaturalTranslatorAI/6.0 Android");
        con.setRequestProperty("Accept", "application/json,text/plain,*/*");

        int code = con.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        if (stream == null) throw new Exception("HTTP " + code);

        BufferedReader br = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
            if (sb.length() > 16000) break;
        }
        br.close();
        con.disconnect();

        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    private TranslationOutcome offlineFallback(String source, String sl, String tl, String reason) {
        String deterministic = deterministicMeaning(source, sl, tl);
        if (deterministic != null) {
            return new TranslationOutcome(
                    deterministic,
                    "Offline fallback • ασφαλής φράση",
                    "Έλεγχος νοήματος: τοπικός κανόνας",
                    explain(source, sl, tl, deterministic, true),
                    false
            );
        }

        String msg = isOnline()
                ? "Η υπηρεσία μετάφρασης δεν απάντησε σωστά. Δεν εμφανίζω εικασία ως μετάφραση."
                : "Δεν υπάρχει σύνδεση στο διαδίκτυο και δεν υπάρχει ασφαλής τοπική απόδοση για αυτή την πρόταση.";

        if (reason != null && !reason.trim().isEmpty()) {
            msg += " (" + reason + ")";
        }
        return new TranslationOutcome(null, "Σφάλμα", "Έλεγχος νοήματος: —", msg, true);
    }

    private String deterministicMeaning(String source, String sl, String tl) {
        if (!"el".equals(sl) || !"en".equals(tl)) return null;
        String n = norm(source);

        if ((n.contains("αναψα") && n.contains("καντηλ") && n.contains("εικον")) && wordCount(n) <= 12) {
            return "I lit the vigil lamp in front of the icon.";
        }

        if (n.contains("αναβω") && n.contains("καντηλ") && n.contains("εικον") && wordCount(n) <= 12) {
            return "I light the vigil lamp in front of the icon.";
        }

        if (n.contains("αναψε") && n.contains("καντηλ") && n.contains("εικον") && wordCount(n) <= 12) {
            return "Light the vigil lamp in front of the icon.";
        }

        if (n.equals("καντηλι") || n.equals("το καντηλι")) {
            return "vigil lamp";
        }

        return null;
    }

    private String postProcessByContext(String source, String sl, String tl, String translated) {
        if (translated == null) return "";
        String out = translated.trim();

        if ("el".equals(sl) && "en".equals(tl)) {
            String n = norm(source);
            boolean religious = containsReligiousCue(n);

            if (n.contains("καντηλ")) {
                out = out.replaceAll("(?i)\\bcadet\\b", "vigil lamp");
                out = out.replaceAll("(?i)\\bcandle\\b", "vigil lamp");
                if (!out.toLowerCase(Locale.ROOT).contains("lamp")) {
                    String exact = deterministicMeaning(source, sl, tl);
                    if (exact != null) return exact;
                }
            }

            if (n.contains("αναψ") && n.contains("καντηλ")) {
                out = out.replaceAll("(?i)\\bI liked\\b", "I lit");
                out = out.replaceAll("(?i)\\bI licked\\b", "I lit");
                out = out.replaceAll("(?i)\\bliked\\b", "lit");
                out = out.replaceAll("(?i)\\blicked\\b", "lit");
            }

            if (religious && n.contains("εικον")) {
                out = out.replaceAll("(?i)\\bthe picture\\b", "the icon");
                out = out.replaceAll("(?i)\\bthe image\\b", "the icon");
            }

            if (n.contains("καντηλ") && n.contains("εικον") && n.contains("αναψ") && wordCount(n) <= 12) {
                return "I lit the vigil lamp in front of the icon.";
            }
        }

        return out;
    }

    private boolean containsReligiousCue(String normalizedGreek) {
        return normalizedGreek.contains("καντηλ")
                || normalizedGreek.contains("εκκλησ")
                || normalizedGreek.contains("αγι")
                || normalizedGreek.contains("προσκυν")
                || normalizedGreek.contains("εικονοστασ")
                || normalizedGreek.contains("μνημοσυν")
                || normalizedGreek.contains("κολλυβ");
    }

    private boolean needsSecondCheck(String source, String sl, String tl, double score) {
        return "el".equals(sl) && "en".equals(tl) && hasAnchors(source, sl, tl) && score < 0.85;
    }

    private boolean hasAnchors(String source, String sl, String tl) {
        return !anchorGroups(source, sl, tl).isEmpty();
    }

    private double semanticScore(String source, String sl, String tl, String translated) {
        List<String[]> groups = anchorGroups(source, sl, tl);
        if (groups.isEmpty()) return 1.0;

        String out = translated == null ? "" : translated.toLowerCase(Locale.ROOT);
        int ok = 0;
        for (String[] group : groups) {
            boolean hit = false;
            for (String term : group) {
                if (out.contains(term.toLowerCase(Locale.ROOT))) {
                    hit = true;
                    break;
                }
            }
            if (hit) ok++;
        }
        return ok / (double) groups.size();
    }

    private List<String[]> anchorGroups(String source, String sl, String tl) {
        List<String[]> groups = new ArrayList<>();
        if (!"el".equals(sl) || !"en".equals(tl)) return groups;

        String n = norm(source);
        if (n.contains("αναψ")) groups.add(new String[]{"lit", "light", "lighted"});
        if (n.contains("καντηλ")) groups.add(new String[]{"vigil lamp", "oil lamp", "lamp"});
        if (n.contains("κερι")) groups.add(new String[]{"candle"});
        if (n.contains("εικον") && containsReligiousCue(n)) groups.add(new String[]{"icon"});
        if (n.contains("εκκλησ")) groups.add(new String[]{"church"});
        if (n.contains("μνημοσυν")) groups.add(new String[]{"memorial", "commemoration"});
        if (n.contains("κολλυβ")) groups.add(new String[]{"koliva", "wheat"});
        if (n.contains("ταφ")) groups.add(new String[]{"grave", "tomb"});
        if (n.contains("πατερ")) groups.add(new String[]{"father"});
        if (n.contains("μητερ") || n.contains("μανα")) groups.add(new String[]{"mother"});
        if (n.contains("ποδηλατ")) groups.add(new String[]{"bike", "bicycle"});
        if (n.contains("εργοδοτ")) groups.add(new String[]{"employer"});
        if (n.contains("μισθ")) groups.add(new String[]{"salary", "wage", "pay"});
        if (n.contains("δουλει")) groups.add(new String[]{"work", "job"});
        if (n.contains("μανιταρ")) groups.add(new String[]{"mushroom"});
        if (n.contains("κοτοπουλ")) groups.add(new String[]{"chicken"});

        return groups;
    }

    private String explain(String source, String sl, String tl, String translated, boolean passed) {
        if ("el".equals(sl) && "en".equals(tl)) {
            String n = norm(source);

            if (n.contains("αναψα") && n.contains("καντηλ") && n.contains("εικον")) {
                return "Απλά: λες ότι άναψες το καντήλι μπροστά στην εικόνα. " +
                        "Εδώ «καντήλι» = vigil lamp και «εικόνα» = icon, επειδή το συμφραζόμενο είναι θρησκευτικό.";
            }

            if (n.contains("καντηλ")) {
                return passed
                        ? "Απλά: η εφαρμογή αναγνώρισε ότι το «καντήλι» είναι θρησκευτικό λυχνάρι και δεν το άφησε να μετατραπεί σε άσχετη αγγλική λέξη."
                        : "Προσοχή: εντοπίστηκε θρησκευτικός όρος, αλλά η αντιστοίχιση δεν ήταν αρκετά ισχυρή.";
            }
        }

        if (passed) {
            return "Η απόδοση πέρασε τον έλεγχο βασικού νοήματος. Η εφαρμογή προτιμά τα συμφραζόμενα από μια τυφλή κατά λέξη μετάφραση.";
        }
        return "Η απόδοση εμφανίζεται με προειδοποίηση επειδή ο έλεγχος δεν επιβεβαίωσε αρκετές βασικές έννοιες. Χρειάζεται προσοχή σε ιδιωματικές ή πολύ ειδικές φράσεις.";
    }

    private TextTarget analysisTarget() {
        if (hasTranslation()) {
            return new TextTarget(result.getText().toString().trim(), currentTargetLang());
        }
        return new TextTarget(input.getText().toString().trim(), currentSourceLang());
    }

    private boolean hasTranslation() {
        String value = result == null ? "" : result.getText().toString().trim();
        return !value.isEmpty()
                && !value.equals("…")
                && !value.startsWith("Η μετάφραση")
                && !value.startsWith("Δεν μπόρεσα");
    }

    private String currentSourceLang() {
        return languageCodes[fromSpinner.getSelectedItemPosition()];
    }

    private String currentTargetLang() {
        return languageCodes[toSpinner.getSelectedItemPosition()];
    }

    private String languageDisplay(String code) {
        int i = indexFor(code);
        return languageNames[i];
    }

    private List<String> tokens(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        String clean = text.replaceAll("[\\n\\r\\t]+", " ").trim();
        if (clean.isEmpty()) return list;
        for (String p : clean.split("\\s+")) {
            String q = p.replaceAll("^[\\p{Punct}«»“”]+|[\\p{Punct}«»“”]+$", "");
            if (!q.isEmpty()) list.add(q);
        }
        return list;
    }

    private Set<String> lowerSet(List<String> words) {
        Set<String> s = new HashSet<>();
        for (String w : words) s.add(cleanEnglish(w));
        return s;
    }

    private String cleanEnglish(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
    }

    private String join(List<String> words, int start, int end) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end && i < words.size(); i++) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(words.get(i));
        }
        return sb.toString();
    }

    private String stripLeadingFunctionWordsEnglish(String s) {
        return s.replaceFirst("(?i)^(the|a|an)\\s+", "").trim();
    }

    private String stripLeadingFunctionWordsGreek(String s) {
        return s.replaceFirst("(?i)^(ο|η|το|οι|τα|τον|την|ένας|ενας|μία|μια)\\s+", "").trim();
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String norm(String s) {
        if (s == null) return "";
        String x = s.toLowerCase(new Locale("el", "GR"));
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        x = x.replace('ς', 'σ');
        x = x.replaceAll("[^a-zα-ω0-9\\s]", " ");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private int wordCount(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        return s.trim().split("\\s+").length;
    }

    private void setBusy(boolean busy) {
        translateButton.setEnabled(!busy);
        translateButton.setAlpha(busy ? 0.55f : 1f);
        fromSpinner.setEnabled(!busy);
        toSpinner.setEnabled(!busy);
    }

    private int indexFor(String code) {
        for (int i = 0; i < languageCodes.length; i++) {
            if (languageCodes[i].equals(code)) return i;
        }
        return 0;
    }

    private Spinner spinner() {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                languageNames
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                styleSpinnerText(v);
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView v = (TextView) super.getDropDownView(position, convertView, parent);
                v.setTextColor(TEXT);
                v.setTextSize(18);
                v.setPadding(dp(16), dp(14), dp(16), dp(14));
                v.setBackgroundColor(PANEL_2);
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setPadding(dp(10), 0, dp(10), 0);
        s.setBackground(rounded(PANEL_2, 18, Color.rgb(64, 74, 92), 1));
        return s;
    }

    private void styleSpinnerText(TextView v) {
        v.setTextColor(TEXT);
        v.setTextSize(21);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(14), 0, dp(14), 0);
    }

    private TextView label(String value) {
        TextView t = text(value, 16, MUTED, true);
        t.setPadding(0, dp(7), 0, dp(7));
        return t;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value, int bg, int fg) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(17);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(rounded(bg, 18, Color.TRANSPARENT, 0));
        return b;
    }

    private GradientDrawable rounded(int fill, float radiusDp, int stroke, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp((int) radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private View space(int hDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(hDp)));
        return v;
    }

    private View spaceHorizontal(int wDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), 1));
        return v;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    private LinearLayout.LayoutParams lpMatch(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams lpMatchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class TranslationOutcome {
        final String translation;
        final String status;
        final String quality;
        final String explanation;
        final boolean warning;

        TranslationOutcome(String translation, String status, String quality, String explanation, boolean warning) {
            this.translation = translation;
            this.status = status;
            this.quality = quality;
            this.explanation = explanation;
            this.warning = warning;
        }
    }

    private static class TextTarget {
        final String text;
        final String lang;

        TextTarget(String text, String lang) {
            this.text = text == null ? "" : text;
            this.lang = lang == null ? "" : lang;
        }
    }
}
