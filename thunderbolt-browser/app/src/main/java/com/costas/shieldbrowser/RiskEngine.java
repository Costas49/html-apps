package com.costas.shieldbrowser;

import android.net.Uri;

import java.net.IDN;
import java.util.Locale;

final class RiskEngine {
    static final class Report {
        final int score;
        final String message;
        Report(int score, String message) { this.score = score; this.message = message; }
    }

    static Report analyze(Uri uri) {
        if (uri == null) return new Report(100, "Δεν υπάρχει έγκυρη διεύθυνση.");
        int risk = 0;
        StringBuilder why = new StringBuilder();
        String host = uri.getHost();
        String url = uri.toString();

        if (!"https".equalsIgnoreCase(uri.getScheme())) { risk += 80; why.append("• Δεν είναι HTTPS.\n"); }
        if (host == null || host.isEmpty()) { risk += 80; why.append("• Λείπει έγκυρος host.\n"); }
        else {
            String h = host.toLowerCase(Locale.ROOT);
            if (h.startsWith("xn--") || h.contains(".xn--")) { risk += 35; why.append("• Διεθνοποιημένο/punycode domain.\n"); }
            if (isIpLiteral(h)) { risk += 25; why.append("• Χρησιμοποιεί αριθμητική IP αντί domain.\n"); }
            if (h.split("\\.").length > 5) { risk += 15; why.append("• Ασυνήθιστα πολλά subdomains.\n"); }
            try {
                String unicode = IDN.toUnicode(h);
                if (!unicode.equals(h) && !h.contains("xn--")) { risk += 10; why.append("• IDN domain.\n"); }
            } catch (Exception ignored) { }
        }
        if (uri.getUserInfo() != null) { risk += 35; why.append("• Περιέχει user-info πριν από το domain.\n"); }
        if (uri.getPort() != -1 && uri.getPort() != 443) { risk += 10; why.append("• Μη τυπική HTTPS θύρα.\n"); }
        if (url.length() > 300) { risk += 10; why.append("• Πολύ μεγάλη URL.\n"); }
        if (url.contains("%00") || url.contains("\\u0000")) { risk += 50; why.append("• Ύποπτος null χαρακτήρας.\n"); }

        risk = Math.min(100, risk);
        String level = risk >= 60 ? "ΥΨΗΛΟΣ ΚΙΝΔΥΝΟΣ" : risk >= 30 ? "ΧΡΕΙΑΖΕΤΑΙ ΠΡΟΣΟΧΗ" : "ΧΑΜΗΛΗ ΕΝΔΕΙΞΗ ΚΙΝΔΥΝΟΥ";
        if (why.length() == 0) why.append("• Δεν βρέθηκε ύποπτη δομή στη URL.\n");
        return new Report(risk, level + " (" + risk + "/100)\n" + why +
                "\nΟ έλεγχος URL δεν αντικαθιστά antivirus. Το WebView Safe Browsing ελέγχει γνωστές απειλές.");
    }

    static boolean shouldHardBlock(Uri uri) {
        if (uri == null || uri.getHost() == null) return true;
        String u = uri.toString().toLowerCase(Locale.ROOT);
        return uri.getUserInfo() != null || u.contains("%00") || u.startsWith("javascript:") ||
                u.startsWith("file:") || u.startsWith("content:");
    }

    private static boolean isIpLiteral(String host) {
        return host.matches("\\d{1,3}(\\.\\d{1,3}){3}") || host.contains(":");
    }
}
