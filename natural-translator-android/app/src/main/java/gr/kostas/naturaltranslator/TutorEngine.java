package gr.kostas.naturaltranslator;

import android.app.Activity;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TutorEngine {
    private TutorEngine() {}

    public interface Callback {
        void onResult(String text);
        void onError(String message);
    }

    private static final Map<String, String> COMMON_EN = new HashMap<>();
    static {
        COMMON_EN.put("love", "λοβ");
        COMMON_EN.put("the", "δε");
        COMMON_EN.put("this", "δις");
        COMMON_EN.put("that", "δατ");
        COMMON_EN.put("these", "διζ");
        COMMON_EN.put("those", "δοουζ");
        COMMON_EN.put("they", "δεϊ");
        COMMON_EN.put("them", "δεμ");
        COMMON_EN.put("there", "δερ");
        COMMON_EN.put("their", "δερ");
        COMMON_EN.put("you", "γιου");
        COMMON_EN.put("your", "γιορ");
        COMMON_EN.put("hello", "χελοου");
        COMMON_EN.put("world", "γουερλντ");
        COMMON_EN.put("good", "γκουντ");
        COMMON_EN.put("morning", "μορνινγκ");
        COMMON_EN.put("night", "ναϊτ");
        COMMON_EN.put("beautiful", "μπιουτιφουλ");
        COMMON_EN.put("language", "λανγκουιτζ");
        COMMON_EN.put("think", "θινκ");
        COMMON_EN.put("thanks", "θενκς");
        COMMON_EN.put("thank", "θενκ");
        COMMON_EN.put("have", "χαβ");
        COMMON_EN.put("give", "γκιβ");
        COMMON_EN.put("what", "γουοτ");
        COMMON_EN.put("where", "γουερ");
        COMMON_EN.put("when", "γουεν");
        COMMON_EN.put("why", "γουαϊ");
        COMMON_EN.put("who", "χου");
        COMMON_EN.put("how", "χαου");
        COMMON_EN.put("yes", "γιες");
        COMMON_EN.put("no", "νοου");
    }

    public static void explainConcept(Activity activity, String text, String sourceCode, Callback cb) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            deliverError(activity, cb, "Γράψε πρώτα μια λέξη ή φράση.");
            return;
        }

        if ("en".equals(sourceCode) && clean.matches("[A-Za-z][A-Za-z'’-]{0,60}")) {
            explainEnglishWord(activity, clean, cb);
            return;
        }

        translateBlock(activity, clean, sourceCode, "el", new Callback() {
            @Override
            public void onResult(String translated) {
                StringBuilder out = new StringBuilder();
                out.append("🎓 ΕΝΝΟΙΑ\n");
                out.append("Κείμενο: ").append(clean).append("\n");
                out.append("Βασική απόδοση στα ελληνικά: ").append(translated).append("\n");
                if (containsLatin(clean)) {
                    out.append("Ελληνική μεταγραφή: ").append(greekTranscription(clean, sourceCode)).append("\n");
                }
                out.append("\nΣημείωση: Η απόδοση δείχνει το βασικό νόημα. Για ιδιωματισμούς ή εξειδικευμένη χρήση, έλεγξε ολόκληρη την πρόταση και όχι μόνο τη λέξη.");
                deliver(activity, cb, out.toString());
            }

            @Override
            public void onError(String message) {
                deliverError(activity, cb, message);
            }
        });
    }

    private static void explainEnglishWord(Activity activity, String word, Callback cb) {
        new Thread(() -> {
            try {
                String url = "https://api.dictionaryapi.dev/api/v2/entries/en/" +
                    URLEncoder.encode(word, StandardCharsets.UTF_8.name());
                String json = get(url);
                DictionaryResult d = parseDictionary(json);

                if (d.definitions.isEmpty()) {
                    fallbackMeaning(activity, word, "en", cb);
                    return;
                }

                String englishDefinitions = String.join("\n", d.definitions);
                translateBlock(activity, englishDefinitions, "en", "el", new Callback() {
                    @Override
                    public void onResult(String greekDefinitions) {
                        StringBuilder out = new StringBuilder();
                        out.append("🎓 ΕΝΝΟΙΑ\n");
                        out.append("Λέξη: ").append(word).append("\n");
                        out.append("Ελληνική μεταγραφή: ").append(greekTranscription(word, "en")).append("\n");
                        if (!d.phonetic.isEmpty()) {
                            out.append("IPA: ").append(d.phonetic).append("\n");
                        }
                        out.append("\n").append(greekDefinitions);
                        if (!d.example.isEmpty()) {
                            out.append("\n\nΠαράδειγμα στα Αγγλικά: ").append(d.example);
                        }
                        out.append("\n\nΣυμβουλή: μάθε τη λέξη μαζί με μία φυσική πρόταση, όχι απομονωμένη.");
                        deliver(activity, cb, out.toString());
                    }

                    @Override
                    public void onError(String message) {
                        StringBuilder out = new StringBuilder();
                        out.append("🎓 ΕΝΝΟΙΑ\n");
                        out.append("Λέξη: ").append(word).append("\n");
                        out.append("Ελληνική μεταγραφή: ").append(greekTranscription(word, "en")).append("\n");
                        if (!d.phonetic.isEmpty()) out.append("IPA: ").append(d.phonetic).append("\n");
                        out.append("\n").append(englishDefinitions);
                        deliver(activity, cb, out.toString());
                    }
                });
            } catch (Exception e) {
                fallbackMeaning(activity, word, "en", cb);
            }
        }).start();
    }

    private static void fallbackMeaning(Activity activity, String text, String sourceCode, Callback cb) {
        translateBlock(activity, text, sourceCode, "el", new Callback() {
            @Override
            public void onResult(String translated) {
                String result =
                    "🎓 ΕΝΝΟΙΑ\n" +
                    "Κείμενο: " + text + "\n" +
                    "Βασική απόδοση: " + translated + "\n" +
                    (containsLatin(text) ? "Ελληνική μεταγραφή: " + greekTranscription(text, sourceCode) + "\n" : "") +
                    "\nΗ λεξικογραφική υπηρεσία δεν έδωσε αναλυτικό λήμμα, οπότε εμφανίζεται η ασφαλέστερη βασική απόδοση.";
                deliver(activity, cb, result);
            }

            @Override
            public void onError(String message) {
                deliverError(activity, cb, "Δεν μπόρεσα να αναλύσω την έννοια αυτή τη στιγμή. " + message);
            }
        });
    }

    public static void grammarCheck(Activity activity, String text, String sourceCode, Callback cb) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            deliverError(activity, cb, "Γράψε πρώτα κείμενο για γραμματικό έλεγχο.");
            return;
        }
        if (clean.length() > 8000) {
            deliverError(activity, cb, "Για τον δωρεάν γραμματικό έλεγχο χρησιμοποίησε έως 8.000 χαρακτήρες κάθε φορά.");
            return;
        }

        new Thread(() -> {
            try {
                String body =
                    "text=" + URLEncoder.encode(clean, StandardCharsets.UTF_8.name()) +
                    "&language=" + URLEncoder.encode(languageToolCode(sourceCode), StandardCharsets.UTF_8.name()) +
                    "&motherTongue=el";

                String json = postForm("https://api.languagetool.org/v2/check", body);
                JSONObject root = new JSONObject(json);
                JSONArray matches = root.optJSONArray("matches");
                if (matches == null || matches.length() == 0) {
                    deliver(activity, cb,
                        "🎓 ΓΡΑΜΜΑΤΙΚΗ\nΔεν εντοπίστηκαν σαφή βασικά λάθη από τον αυτόματο έλεγχο.\n\n" +
                        "Αυτό δεν αποδεικνύει ότι το κείμενο είναι τέλειο· ιδιωματισμοί και λεπτές αποχρώσεις μπορεί να χρειάζονται ανθρώπινο έλεγχο.");
                    return;
                }

                List<GrammarIssue> issues = new ArrayList<>();
                int limit = Math.min(matches.length(), 10);
                for (int i = 0; i < limit; i++) {
                    JSONObject m = matches.getJSONObject(i);
                    GrammarIssue issue = new GrammarIssue();
                    issue.message = m.optString("message", "");
                    issue.shortMessage = m.optString("shortMessage", "");
                    issue.offset = m.optInt("offset", 0);
                    issue.length = m.optInt("length", 0);

                    JSONObject rule = m.optJSONObject("rule");
                    if (rule != null) {
                        issue.ruleId = rule.optString("id", "");
                        JSONObject cat = rule.optJSONObject("category");
                        if (cat != null) {
                            issue.category = cat.optString("name", cat.optString("id", ""));
                        }
                    }

                    int start = Math.max(0, Math.min(issue.offset, clean.length()));
                    int end = Math.max(start, Math.min(start + issue.length, clean.length()));
                    issue.fragment = clean.substring(start, end);

                    JSONArray replacements = m.optJSONArray("replacements");
                    if (replacements != null) {
                        for (int r = 0; r < Math.min(3, replacements.length()); r++) {
                            String value = replacements.getJSONObject(r).optString("value", "");
                            if (!value.isEmpty()) issue.replacements.add(value);
                        }
                    }
                    issues.add(issue);
                }

                translateIssueMessages(activity, issues, cb);
            } catch (Exception e) {
                deliverError(activity, cb,
                    "Ο online γραμματικός έλεγχος δεν απάντησε. Η μετάφραση, η μεταγραφή και η τοπική συντακτική ανάλυση συνεχίζουν να λειτουργούν.");
            }
        }).start();
    }

    private static void translateIssueMessages(Activity activity, List<GrammarIssue> issues, Callback cb) {
        String source = TranslateLanguage.fromLanguageTag("en");
        String target = TranslateLanguage.fromLanguageTag("el");
        if (source == null || target == null) {
            deliver(activity, cb, buildGrammarReport(issues, null));
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build();
        Translator tr = Translation.getClient(options);
        tr.downloadModelIfNeeded(new DownloadConditions.Builder().build())
            .addOnSuccessListener(unused -> {
                List<String> translated = new ArrayList<>();
                translateIssueAt(activity, tr, issues, 0, translated, cb);
            })
            .addOnFailureListener(e -> {
                tr.close();
                deliver(activity, cb, buildGrammarReport(issues, null));
            });
    }

    private static void translateIssueAt(
        Activity activity,
        Translator tr,
        List<GrammarIssue> issues,
        int index,
        List<String> translated,
        Callback cb
    ) {
        if (index >= issues.size()) {
            tr.close();
            deliver(activity, cb, buildGrammarReport(issues, translated));
            return;
        }

        String msg = issues.get(index).message;
        if (msg == null || msg.trim().isEmpty()) {
            translated.add("");
            translateIssueAt(activity, tr, issues, index + 1, translated, cb);
            return;
        }

        tr.translate(msg)
            .addOnSuccessListener(value -> {
                translated.add(value);
                translateIssueAt(activity, tr, issues, index + 1, translated, cb);
            })
            .addOnFailureListener(e -> {
                translated.add(msg);
                translateIssueAt(activity, tr, issues, index + 1, translated, cb);
            });
    }

    private static String buildGrammarReport(List<GrammarIssue> issues, List<String> translated) {
        StringBuilder out = new StringBuilder();
        out.append("🎓 ΓΡΑΜΜΑΤΙΚΗ — εντοπίστηκαν ").append(issues.size()).append(" σημεία\n\n");
        for (int i = 0; i < issues.size(); i++) {
            GrammarIssue it = issues.get(i);
            String explanation = translated != null && i < translated.size() ? translated.get(i) : it.message;
            out.append(i + 1).append(". ");
            if (!it.category.isEmpty()) out.append("[").append(greekCategory(it.category)).append("] ");
            if (!it.fragment.isEmpty()) out.append("«").append(it.fragment).append("»\n");
            if (explanation != null && !explanation.isEmpty()) {
                out.append("Εξήγηση: ").append(explanation).append("\n");
            }
            if (!it.replacements.isEmpty()) {
                out.append("Πιθανή διόρθωση: ").append(String.join(" / ", it.replacements)).append("\n");
            }
            if (!it.ruleId.isEmpty()) out.append("Κανόνας: ").append(it.ruleId).append("\n");
            out.append("\n");
        }
        out.append("Οι προτάσεις είναι αυτόματες. Διάβασε ολόκληρη την πρόταση πριν δεχτείς οποιαδήποτε διόρθωση.");
        return out.toString();
    }

    public static String syntaxAnalysis(String text, String languageCode) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) return "Γράψε πρώτα μια πρόταση.";

        if ("en".equals(languageCode)) {
            return syntaxEnglish(clean);
        }

        String[] words = clean.split("\\s+");
        int sentences = Math.max(1, clean.split("[.!?]+").length);
        StringBuilder out = new StringBuilder();
        out.append("🎓 ΣΥΝΤΑΚΤΙΚΟ — ενδεικτική ανάλυση\n");
        out.append("Γλώσσα: ").append(languageCode.toUpperCase(Locale.ROOT)).append("\n");
        out.append("Λέξεις: ").append(words.length).append(" • Προτάσεις: ").append(sentences).append("\n\n");
        out.append("Για αυτή τη γλώσσα η τοπική ανάλυση κρατά συντηρητική στάση ώστε να μη βαφτίζει λάθος ένα υποκείμενο ή ρήμα. ");
        out.append("Χρησιμοποίησε και το κουμπί «Γραμματική» για έλεγχο συμφωνίας, σειράς λέξεων, στίξης και πιθανών λαθών όπου υποστηρίζεται.");
        return out.toString();
    }

    private static String syntaxEnglish(String clean) {
        String normalized = clean.replaceAll("[^A-Za-z'’\\s]", " ").trim().toLowerCase(Locale.ROOT);
        String[] tokens = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        if (tokens.length == 0) return "Δεν βρήκα λέξεις για ανάλυση.";

        Set<String> verbs = new HashSet<>(Arrays.asList(
            "am","is","are","was","were","be","been","being",
            "have","has","had","do","does","did",
            "will","would","can","could","shall","should","may","might","must",
            "go","goes","went","come","comes","came","make","makes","made",
            "say","says","said","know","knows","knew","think","thinks","thought",
            "see","sees","saw","want","wants","wanted","need","needs","needed",
            "like","likes","liked","love","loves","loved","give","gives","gave",
            "take","takes","took","speak","speaks","spoke","learn","learns","learned",
            "study","studies","studied","work","works","worked","live","lives","lived"
        ));

        int verbIndex = -1;
        for (int i = 0; i < tokens.length; i++) {
            String w = tokens[i];
            if (verbs.contains(w) || (w.length() > 4 && (w.endsWith("ing") || w.endsWith("ed")))) {
                verbIndex = i;
                break;
            }
        }

        String subject = verbIndex > 0 ? join(tokens, 0, verbIndex) : "δεν εντοπίστηκε με βεβαιότητα";
        String verb = verbIndex >= 0 ? tokens[verbIndex] : "δεν εντοπίστηκε με βεβαιότητα";
        String rest = verbIndex >= 0 && verbIndex + 1 < tokens.length ? join(tokens, verbIndex + 1, tokens.length) : "—";

        String tense = "πιθανός Simple Present / βασική μορφή";
        String low = " " + normalized + " ";
        if (low.contains(" will ")) tense = "Future με will";
        else if ((low.contains(" have ") || low.contains(" has ")) && containsPastParticiple(tokens)) tense = "Present Perfect (πιθανό)";
        else if ((low.contains(" had ")) && containsPastParticiple(tokens)) tense = "Past Perfect (πιθανό)";
        else if ((low.contains(" am ") || low.contains(" is ") || low.contains(" are ")) && containsIng(tokens)) tense = "Present Continuous (πιθανό)";
        else if ((low.contains(" was ") || low.contains(" were ")) && containsIng(tokens)) tense = "Past Continuous (πιθανό)";
        else if (low.contains(" did ") || containsEd(tokens)) tense = "Simple Past (πιθανό)";

        String kind = clean.endsWith("?") ? "ερώτηση" : (clean.endsWith("!") ? "επιφωνηματική πρόταση" : "δήλωση");

        return "🎓 ΣΥΝΤΑΚΤΙΚΟ — Αγγλικά\n" +
            "Τύπος: " + kind + "\n" +
            "Πιθανό υποκείμενο: " + subject + "\n" +
            "Πυρήνας ρήματος: " + verb + "\n" +
            "Υπόλοιπο / πιθανό αντικείμενο-συμπλήρωμα: " + rest + "\n" +
            "Χρόνος/δομή: " + tense + "\n\n" +
            "Η ανάλυση είναι ενδεικτική, όχι σχολική συντακτική ετυμηγορία. Για σύνθετες προτάσεις χρησιμοποίησε μαζί και τον γραμματικό έλεγχο.";
    }

    public static String greekTranscription(String text, String sourceCode) {
        if (text == null || text.trim().isEmpty()) return "—";
        StringBuilder out = new StringBuilder();
        StringBuilder word = new StringBuilder();

        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '\'' || c == '’') {
                word.append(c);
            } else {
                if (word.length() > 0) {
                    String w = word.toString();
                    out.append("en".equals(sourceCode) ? transcribeEnglishWord(w) : transcribeLatinWord(w));
                    word.setLength(0);
                }
                if (i < text.length()) out.append(c);
            }
        }
        return out.toString();
    }

    private static String transcribeEnglishWord(String raw) {
        String w = raw.toLowerCase(Locale.ROOT).replace("’", "'");
        String common = COMMON_EN.get(w);
        if (common != null) return common;

        if (w.length() > 3 && w.endsWith("e") && !w.endsWith("ee")) {
            w = w.substring(0, w.length() - 1);
        }

        String[][] digraphs = {
            {"tion","σιον"}, {"sion","ζιον"}, {"tch","τσ"}, {"igh","αϊ"},
            {"sh","σ"}, {"ch","τσ"}, {"th","θ"}, {"ph","φ"}, {"qu","κου"},
            {"ck","κ"}, {"ng","νγκ"}, {"ee","ι"}, {"oo","ου"}, {"ea","ι"},
            {"ou","αου"}, {"ow","αου"}, {"ai","εϊ"}, {"ay","εϊ"},
            {"oi","οϊ"}, {"oy","οϊ"}, {"wh","γου"}
        };
        for (String[] pair : digraphs) w = w.replace(pair[0], pair[1]);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            switch (c) {
                case 'a': out.append('α'); break;
                case 'b': out.append("μπ"); break;
                case 'c': out.append('κ'); break;
                case 'd': out.append("ντ"); break;
                case 'e': out.append('ε'); break;
                case 'f': out.append('φ'); break;
                case 'g': out.append("γκ"); break;
                case 'h': out.append('χ'); break;
                case 'i': out.append('ι'); break;
                case 'j': out.append("τζ"); break;
                case 'k': out.append('κ'); break;
                case 'l': out.append('λ'); break;
                case 'm': out.append('μ'); break;
                case 'n': out.append('ν'); break;
                case 'o': out.append('ο'); break;
                case 'p': out.append('π'); break;
                case 'q': out.append('κ'); break;
                case 'r': out.append('ρ'); break;
                case 's': out.append('σ'); break;
                case 't': out.append('τ'); break;
                case 'u': out.append("ου"); break;
                case 'v': out.append('β'); break;
                case 'w': out.append("γου"); break;
                case 'x': out.append('ξ'); break;
                case 'y': out.append('ι'); break;
                case 'z': out.append('ζ'); break;
                case '\'': out.append('’'); break;
                default: out.append(c); break;
            }
        }
        return out.toString();
    }

    private static String transcribeLatinWord(String raw) {
        String w = Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            switch (c) {
                case 'a': out.append('α'); break;
                case 'b': out.append('μ').append('π'); break;
                case 'c': out.append('κ'); break;
                case 'd': out.append('ν').append('τ'); break;
                case 'e': out.append('ε'); break;
                case 'f': out.append('φ'); break;
                case 'g': out.append('γ').append('κ'); break;
                case 'h': out.append('χ'); break;
                case 'i': out.append('ι'); break;
                case 'j': out.append('ζ'); break;
                case 'k': out.append('κ'); break;
                case 'l': out.append('λ'); break;
                case 'm': out.append('μ'); break;
                case 'n': out.append('ν'); break;
                case 'o': out.append('ο'); break;
                case 'p': out.append('π'); break;
                case 'q': out.append('κ'); break;
                case 'r': out.append('ρ'); break;
                case 's': out.append('σ'); break;
                case 't': out.append('τ'); break;
                case 'u': out.append('ο').append('υ'); break;
                case 'v': out.append('β'); break;
                case 'w': out.append('β'); break;
                case 'x': out.append('ξ'); break;
                case 'y': out.append('ι'); break;
                case 'z': out.append('ζ'); break;
                case '\'': out.append('’'); break;
                default: out.append(c); break;
            }
        }
        return out.toString();
    }

    public static String studyAdvice(String text, String languageCode) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return "🎓 ΣΥΜΒΟΥΛΗ\nΓράψε μια λέξη ή πρόταση και ο Πρύτανης θα σου δώσει πιο συγκεκριμένη μέθοδο μελέτης.";
        }

        int words = clean.split("\\s+").length;
        String transcription = containsLatin(clean) ? greekTranscription(clean, languageCode) : "—";
        if (words == 1) {
            return "🎓 ΣΥΜΒΟΥΛΗ ΓΙΑ ΛΕΞΗ\n" +
                "1. Μάθε πρώτα το νόημα.\n" +
                "2. Δες την ελληνική μεταγραφή: " + transcription + "\n" +
                "3. Άκου τη λέξη από το κουμπί ακρόασης όταν υπάρχει μετάφραση.\n" +
                "4. Βάλε τη λέξη σε μία φυσική πρόταση.\n" +
                "5. Επανέλαβέ την αργότερα χωρίς να κοιτάς.\n\n" +
                "Μην αποστηθίζεις μόνο «λέξη = λέξη». Μάθε χρήση + ήχο + παράδειγμα.";
        }

        return "🎓 ΣΥΜΒΟΥΛΗ ΓΙΑ ΠΡΟΤΑΣΗ\n" +
            "1. Διάβασε πρώτα όλη την πρόταση για νόημα.\n" +
            "2. Εντόπισε το βασικό ρήμα και ποιος κάνει την πράξη.\n" +
            "3. Σύγκρινε τη φυσική μετάφραση, όχι λέξη προς λέξη.\n" +
            "4. Πάτησε «Γραμματική» για πιθανά λάθη και «Συντακτικό» για δομή.\n" +
            "5. Διάβασε δυνατά και μετά προσπάθησε να την πεις χωρίς να κοιτάς.\n\n" +
            "Βοηθητική ελληνική μεταγραφή:\n" + transcription;
    }

    private static void translateBlock(Activity activity, String text, String sourceCode, String targetCode, Callback cb) {
        if (sourceCode.equals(targetCode)) {
            deliver(activity, cb, text);
            return;
        }

        String source = TranslateLanguage.fromLanguageTag(sourceCode);
        String target = TranslateLanguage.fromLanguageTag(targetCode);
        if (source == null || target == null) {
            deliverError(activity, cb, "Η συγκεκριμένη γλώσσα δεν υποστηρίζεται από το τοπικό μοντέλο μετάφρασης.");
            return;
        }

        TranslatorOptions options = new TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build();
        Translator tr = Translation.getClient(options);
        tr.downloadModelIfNeeded(new DownloadConditions.Builder().build())
            .addOnSuccessListener(unused ->
                tr.translate(text)
                    .addOnSuccessListener(value -> {
                        tr.close();
                        deliver(activity, cb, value);
                    })
                    .addOnFailureListener(e -> {
                        tr.close();
                        deliverError(activity, cb, "Δεν ολοκληρώθηκε η βοηθητική μετάφραση.");
                    })
            )
            .addOnFailureListener(e -> {
                tr.close();
                deliverError(activity, cb, "Χρειάζεται Internet την πρώτη φορά για να κατέβει το γλωσσικό μοντέλο.");
            });
    }

    private static DictionaryResult parseDictionary(String json) throws Exception {
        JSONArray arr = new JSONArray(json);
        JSONObject first = arr.getJSONObject(0);
        DictionaryResult result = new DictionaryResult();

        result.phonetic = first.optString("phonetic", "");
        if (result.phonetic.isEmpty()) {
            JSONArray phonetics = first.optJSONArray("phonetics");
            if (phonetics != null) {
                for (int i = 0; i < phonetics.length(); i++) {
                    String p = phonetics.getJSONObject(i).optString("text", "");
                    if (!p.isEmpty()) {
                        result.phonetic = p;
                        break;
                    }
                }
            }
        }

        JSONArray meanings = first.optJSONArray("meanings");
        if (meanings != null) {
            for (int i = 0; i < Math.min(3, meanings.length()); i++) {
                JSONObject meaning = meanings.getJSONObject(i);
                String part = meaning.optString("partOfSpeech", "");
                JSONArray defs = meaning.optJSONArray("definitions");
                if (defs == null) continue;

                int added = 0;
                for (int d = 0; d < defs.length() && added < 2; d++) {
                    JSONObject def = defs.getJSONObject(d);
                    String definition = def.optString("definition", "");
                    if (!definition.isEmpty()) {
                        result.definitions.add((part.isEmpty() ? "" : part + ": ") + definition);
                        added++;
                    }
                    if (result.example.isEmpty()) {
                        String ex = def.optString("example", "");
                        if (!ex.isEmpty()) result.example = ex;
                    }
                }
            }
        }
        return result;
    }

    private static String get(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NaturalTranslatorAI/2.0");
        return readResponse(conn);
    }

    private static String postForm(String address, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "NaturalTranslatorAI/2.0");

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        return readResponse(conn);
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) throw new IllegalStateException("HTTP " + code);

        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line);
        } finally {
            conn.disconnect();
        }
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        return out.toString();
    }

    private static String languageToolCode(String code) {
        switch (code) {
            case "en": return "en-US";
            case "de": return "de-DE";
            case "pt": return "pt-PT";
            case "zh": return "zh-CN";
            default: return code;
        }
    }

    private static String greekCategory(String value) {
        String v = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (v.contains("grammar")) return "Γραμματική";
        if (v.contains("typo") || v.contains("spell")) return "Ορθογραφία";
        if (v.contains("punct")) return "Στίξη";
        if (v.contains("style")) return "Ύφος";
        if (v.contains("capital")) return "Κεφαλαία/πεζά";
        return value == null || value.isEmpty() ? "Έλεγχος" : value;
    }

    private static boolean containsLatin(String text) {
        return text != null && text.matches(".*[A-Za-z].*");
    }

    private static boolean containsIng(String[] tokens) {
        for (String t : tokens) if (t.endsWith("ing")) return true;
        return false;
    }

    private static boolean containsEd(String[] tokens) {
        for (String t : tokens) if (t.endsWith("ed")) return true;
        return false;
    }

    private static boolean containsPastParticiple(String[] tokens) {
        Set<String> irregular = new HashSet<>(Arrays.asList(
            "been","gone","done","seen","known","given","taken","made","said","come","spoken","written","read","thought"
        ));
        for (String t : tokens) if (t.endsWith("ed") || irregular.contains(t)) return true;
        return false;
    }

    private static String join(String[] tokens, int start, int end) {
        if (start >= end) return "—";
        StringBuilder out = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (i > start) out.append(' ');
            out.append(tokens[i]);
        }
        return out.toString();
    }

    private static void deliver(Activity activity, Callback cb, String text) {
        activity.runOnUiThread(() -> cb.onResult(text));
    }

    private static void deliverError(Activity activity, Callback cb, String message) {
        activity.runOnUiThread(() -> cb.onError(message));
    }

    private static class DictionaryResult {
        String phonetic = "";
        String example = "";
        final List<String> definitions = new ArrayList<>();
    }

    private static class GrammarIssue {
        String message = "";
        String shortMessage = "";
        String ruleId = "";
        String category = "";
        String fragment = "";
        int offset;
        int length;
        final List<String> replacements = new ArrayList<>();
    }
}
