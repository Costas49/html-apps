package com.costas.shieldbrowser;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class TrackerBlocker {
    private final Set<String> suffixes = new HashSet<>();
    private final AtomicInteger blockedCount = new AtomicInteger();

    TrackerBlocker(Context context) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open("blocklist.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (!line.isEmpty() && !line.startsWith("#")) suffixes.add(line);
            }
        } catch (Exception ignored) { }
    }

    boolean shouldBlock(String host) {
        if (host == null || host.isEmpty()) return false;
        String normalized = host.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (normalized.equals(suffix) || normalized.endsWith("." + suffix)) {
                blockedCount.incrementAndGet();
                return true;
            }
        }
        return false;
    }

    int getBlockedCount() { return blockedCount.get(); }

    static boolean isTrackingParameter(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("utm_") || n.equals("fbclid") || n.equals("gclid") ||
                n.equals("dclid") || n.equals("msclkid") || n.equals("yclid") ||
                n.equals("mc_cid") || n.equals("mc_eid") || n.equals("igshid") ||
                n.equals("vero_id") || n.equals("_hsenc") || n.equals("_hsmi");
    }
}
