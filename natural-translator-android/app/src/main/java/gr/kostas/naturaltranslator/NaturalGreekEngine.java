package gr.kostas.naturaltranslator;

import android.app.Activity;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NaturalGreekEngine {
    private NaturalGreekEngine() {}

    public interface Callback {
        void onResult(String text);
    }

    public static String prepareSource(String source, String sourceCode, String targetCode) {
        String clean = normalize(source);
        if (!"el".equals(targetCode)) return clean;
        if ("en".equals(sourceCode)) return prepareEnglishForGreek(clean);
        return clean;
    }

    public static void polishAsync(
        Activity activity,
        String translated,
        String originalSource,
        String sourceCode,
        String targetCode,
        Callback cb
    ) {
        String local = "el".equals(targetCode)
            ? polishGreek(translated, originalSource)
            : normalize(translated);

        if (!"el".equals(targetCode) || local.length() < 2 || local.length() > 6000) {
            deliver(activity, cb, local);
            return;
        }

        new Thread(() -> {
            String result = local;
            try {
                result = languageToolPolish(local);
                result = polishGreek(result, originalSource);
            } catch (Exception ignored) {
                result = local;
            }
            deliver(activity, cb, result);
        }, "natural-greek-max").start();
    }

    private static String prepareEnglishForGreek(String source) {
        String s = source;

        s = rx(s, "\\bpeople familiar with the matter\\b", "sources familiar with the matter");
        s = rx(s, "\\bpeople familiar with the situation\\b", "sources familiar with the situation");

        s = rx(s, "\\bpushed back against\\b", "opposed");
        s = rx(s, "\\bpushes back against\\b", "opposes");
        s = rx(s, "\\bpushing back against\\b", "opposing");
        s = rx(s, "\\bpush back against\\b", "oppose");

        s = rx(s, "\\bhit back at\\b", "responded sharply to");
        s = rx(s, "\\bhits back at\\b", "responds sharply to");
        s = rx(s, "\\bhitting back at\\b", "responding sharply to");

        s = rx(s, "\\bunder fire for\\b", "facing criticism for");
        s = rx(s, "\\bunder fire\\b", "facing criticism");
        s = rx(s, "\\bin the wake of\\b", "after");

        s = rx(s, "\\bdoubled down on\\b", "reaffirmed");
        s = rx(s, "\\bdouble down on\\b", "insist on");
        s = rx(s, "\\bdoubling down on\\b", "insisting on");

        s = rx(s, "\\brolled out\\b", "launched");
        s = rx(s, "\\brolls out\\b", "launches");
        s = rx(s, "\\broll out\\b", "launch");

        s = rx(s, "\\bstepped down\\b", "resigned");
        s = rx(s, "\\bsteps down\\b", "resigns");
        s = rx(s, "\\bstep down\\b", "resign");

        s = rx(s, "\\bweighed in on\\b", "commented on");
        s = rx(s, "\\bweighs in on\\b", "comments on");
        s = rx(s, "\\bweigh in on\\b", "comment on");

        s = rx(s, "\\bcalled out\\b", "criticized");
        s = rx(s, "\\bcalls out\\b", "criticizes");
        s = rx(s, "\\bcall out\\b", "criticize");

        s = rx(s, "\\bbroke (his|her|their) silence\\b", "spoke publicly for the first time");
        s = rx(s, "\\bbreaking (his|her|their) silence\\b", "speaking publicly for the first time");

        s = rx(s, "\\bfacing mounting pressure\\b", "facing increasing pressure");
        s = rx(s, "\\bmounting pressure\\b", "increasing pressure");

        String lower = source.toLowerCase(Locale.ROOT);
        if (isUsPoliticalContext(lower)) {
            s = rx(s, "\\blawmakers\\b", "members of Congress");
            s = rx(s, "\\bthe administration\\b", "the government");
        }

        return normalize(s);
    }

    private static String polishGreek(String translated, String originalSource) {
        String s = normalize(translated);
        if (s.isEmpty()) return s;

        s = rx(s, "\\bέχει λάβει χώρα\\b", "έχει γίνει");
        s = rx(s, "\\bθα λάβει χώρα\\b", "θα γίνει");
        s = rx(s, "\\bέλαβε χώρα\\b", "έγινε");
        s = rx(s, "\\bλαμβάνει χώρα\\b", "γίνεται");

        s = rx(s, "\\bβρίσκεται σε θέση να\\b", "μπορεί να");
        s = rx(s, "\\bείναι σε θέση να\\b", "μπορεί να");
        s = rx(s, "\\bέχει την ικανότητα να\\b", "μπορεί να");

        s = rx(s, "\\bσε καθημερινή βάση\\b", "καθημερινά");
        s = rx(s, "\\bσε αυτή τη χρονική στιγμή\\b", "αυτή τη στιγμή");
        s = rx(s, "\\bκατά τη διάρκεια της χρονικής περιόδου\\b", "κατά την περίοδο");

        s = rx(s, "\\bκάνει νόημα\\b", "βγάζει νόημα");
        s = rx(s, "\\bπιέζουν προς τα πίσω\\b", "αντιδρούν");
        s = rx(s, "\\bπιέζει προς τα πίσω\\b", "αντιδρά");
        s = rx(s, "\\bχτύπησε πίσω\\b", "αντέδρασε");
        s = rx(s, "\\bχτυπά πίσω\\b", "αντιδρά");

        s = rx(s, "\\bσήκωσε ανησυχίες\\b", "προκάλεσε ανησυχίες");
        s = rx(s, "\\bσήκωσε ερωτήματα\\b", "έθεσε ερωτήματα");
        s = rx(s, "\\bανέβασε ερωτήματα\\b", "έθεσε ερωτήματα");

        s = rx(s, "\\bέδωσε έμφαση στο γεγονός ότι\\b", "τόνισε ότι");
        s = rx(s, "\\bπροχώρησε σε ανακοίνωση\\b", "ανακοίνωσε");
        s = rx(s, "\\bπροχώρησε σε δήλωση\\b", "δήλωσε");
        s = rx(s, "\\bπραγματοποίησε μια δήλωση\\b", "δήλωσε");
        s = rx(s, "\\bπραγματοποίησε μία δήλωση\\b", "δήλωσε");
        s = rx(s, "\\bέκανε μια δήλωση\\b", "δήλωσε");
        s = rx(s, "\\bέκανε μία δήλωση\\b", "δήλωσε");

        s = rx(s, "\\bτρέχει για την προεδρία\\b", "είναι υποψήφιος για την προεδρία");
        s = rx(s, "\\bτρέχει για πρόεδρος\\b", "είναι υποψήφιος για την προεδρία");

        s = rx(s, "\\bτα νέα έρχονται καθώς\\b", "η εξέλιξη αυτή έρχεται καθώς");
        s = rx(s, "\\bτα νέα έρχονται μετά\\b", "η εξέλιξη αυτή έρχεται μετά");

        s = rx(s, "\\bάτομα εξοικειωμένα με το θέμα\\b", "πηγές που γνωρίζουν το θέμα");
        s = rx(s, "\\bάνθρωποι εξοικειωμένοι με το θέμα\\b", "πηγές που γνωρίζουν το θέμα");
        s = rx(s, "\\bπηγές εξοικειωμένες με το θέμα\\b", "πηγές που γνωρίζουν το θέμα");

        String lowerSource = originalSource == null ? "" : originalSource.toLowerCase(Locale.ROOT);
        if (isUsPoliticalContext(lowerSource)) {
            s = rx(s, "\\bνομοθέτες\\b", "μέλη του Κογκρέσου");
            s = rx(s, "\\bη διοίκηση\\b", "η κυβέρνηση");
            s = rx(s, "\\bτης διοίκησης\\b", "της κυβέρνησης");
            s = rx(s, "\\bτη διοίκηση\\b", "την κυβέρνηση");
            s = rx(s, "\\bδιοίκηση ([\\p{L}’'\\-]+)\\b", "κυβέρνηση $1");
        }

        s = rx(s, "\\bως αποτέλεσμα αυτού\\b", "γι’ αυτό");
        s = rx(s, "\\bλόγω του γεγονότος ότι\\b", "επειδή");
        s = rx(s, "\\bπαρά το γεγονός ότι\\b", "παρότι");
        s = rx(s, "\\bμε σκοπό να\\b", "για να");

        s = cleanPunctuation(s);
        s = sentenceCaps(s);
        return s.trim();
    }

    private static String languageToolPolish(String text) throws Exception {
        String body =
            "text=" + URLEncoder.encode(text, StandardCharsets.UTF_8.name()) +
            "&language=el";

        String json = postForm("https://api.languagetool.org/v2/check", body);
        JSONObject root = new JSONObject(json);
        JSONArray matches = root.optJSONArray("matches");
        if (matches == null || matches.length() == 0) return text;

        List<Correction> corrections = new ArrayList<>();
        int max = Math.min(matches.length(), 40);

        for (int i = 0; i < max; i++) {
            JSONObject match = matches.optJSONObject(i);
            if (match == null) continue;

            int offset = match.optInt("offset", -1);
            int length = match.optInt("length", 0);
            if (offset < 0 || length < 0 || offset + length > text.length()) continue;

            JSONArray replacements = match.optJSONArray("replacements");
            if (replacements == null || replacements.length() == 0) continue;

            String replacement = replacements.optJSONObject(0) == null
                ? ""
                : replacements.optJSONObject(0).optString("value", "");
            if (replacement.isEmpty() || replacement.length() > 120) continue;

            String fragment = text.substring(offset, offset + length);
            if (containsLatin(fragment)) continue;
            if (fragment.equals(replacement)) continue;

            JSONObject rule = match.optJSONObject("rule");
            String ruleId = rule == null ? "" : rule.optString("id", "");
            if (ruleId.contains("WHITESPACE_RULE") && fragment.isEmpty()) continue;

            corrections.add(new Correction(offset, length, replacement));
        }

        if (corrections.isEmpty()) return text;

        Collections.sort(corrections, Comparator.comparingInt((Correction c) -> c.offset).reversed());
        StringBuilder out = new StringBuilder(text);

        for (Correction c : corrections) {
            int end = c.offset + c.length;
            if (c.offset < 0 || end > out.length()) continue;
            out.replace(c.offset, end, c.replacement);
        }
        return out.toString();
    }

    private static String postForm(String endpoint, String body) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(4500);
        con.setReadTimeout(6000);
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "Natural-Translator-AI-Rector/3.0");

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        con.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = con.getOutputStream()) {
            os.write(bytes);
        }

        int code = con.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
        String response = read(stream);
        con.disconnect();

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("LanguageTool HTTP " + code);
        }
        return response;
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String s = text.replace("\r\n", "\n").replace('\r', '\n').replace('\u00A0', ' ');
        s = s.replaceAll("[\\t ]+", " ");
        s = s.replaceAll(" *\\n *", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private static String cleanPunctuation(String text) {
        String s = normalize(text);
        s = s.replaceAll("\\s+([,;:!?])", "$1");
        s = s.replaceAll("([,;:!?])(?=\\p{L})", "$1 ");
        s = s.replaceAll("«\\s+", "«");
        s = s.replaceAll("\\s+»", "»");
        s = s.replaceAll("\\(\\s+", "(");
        s = s.replaceAll("\\s+\\)", ")");
        s = s.replaceAll(" {2,}", " ");
        return s;
    }

    private static String sentenceCaps(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean capNext = true;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (capNext && Character.isLetter(ch)) {
                out.append(Character.toUpperCase(ch));
                capNext = false;
            } else {
                out.append(ch);
                if (Character.isLetter(ch) || Character.isDigit(ch)) capNext = false;
            }
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                capNext = true;
            }
        }
        return out.toString();
    }

    private static boolean isUsPoliticalContext(String lower) {
        return lower.contains("congress")
            || lower.contains("white house")
            || lower.contains("senate")
            || lower.contains("house of representatives")
            || lower.contains("u.s. president")
            || lower.contains("us president")
            || lower.contains("president trump")
            || lower.contains("president biden")
            || lower.contains("capitol hill");
    }

    private static boolean containsLatin(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return true;
        }
        return false;
    }

    private static String rx(String value, String regex, String replacement) {
        return Pattern
            .compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
            .matcher(value)
            .replaceAll(replacement);
    }

    private static void deliver(Activity activity, Callback cb, String value) {
        activity.runOnUiThread(() -> cb.onResult(value));
    }

    private static final class Correction {
        final int offset;
        final int length;
        final String replacement;

        Correction(int offset, int length, String replacement) {
            this.offset = offset;
            this.length = length;
            this.replacement = replacement;
        }
    }
}
