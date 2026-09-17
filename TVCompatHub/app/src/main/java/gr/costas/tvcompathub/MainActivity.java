package gr.costas.tvcompathub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MainActivity extends Activity {
    private TextView statusText;
    private GridView appGrid;
    private AppAdapter appAdapter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            statusText.setText("Πρόσβαση αρχείων: ενεργή • Επίλεξε εφαρμογή ή άνοιξε Αρχεία / APK.");
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackgroundColor(Color.rgb(8, 17, 31));

        TextView title = new TextView(this);
        title.setText("TV Compatibility Hub");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(this);
        subtitle.setText("Εφαρμογές κινητού/tablet • APK • αρχεία/USB • εικονικό ποντίκι");
        subtitle.setTextColor(Color.rgb(174, 202, 229));
        subtitle.setTextSize(15);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        root.addView(subtitle, subLp);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(10), 0, dp(8));

        actions.addView(actionButton("Εφαρμογές", v -> loadApps()));
        actions.addView(actionButton("Αρχεία / APK", v -> openStorage()));
        actions.addView(actionButton("Άδεια αρχείων", v -> requestStoragePermission()));
        actions.addView(actionButton("Ποντίκι TV", v -> openAccessibilitySettings()));
        actions.addView(actionButton("Οθόνη TV", v -> openDisplaySettings()));
        actions.addView(actionButton("Πληροφορίες", v -> showHelp()));
        scroller.addView(actions);
        root.addView(scroller, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(192, 215, 237));
        statusText.setTextSize(14);
        statusText.setText("Φόρτωση εφαρμογών…");
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.bottomMargin = dp(10);
        root.addView(statusText, statusLp);

        appGrid = new GridView(this);
        appGrid.setNumColumns(GridView.AUTO_FIT);
        appGrid.setColumnWidth(dp(205));
        appGrid.setHorizontalSpacing(dp(12));
        appGrid.setVerticalSpacing(dp(12));
        appGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        appGrid.setSelector(android.R.color.transparent);
        appGrid.setClipToPadding(false);
        appGrid.setPadding(0, 0, 0, dp(12));
        appAdapter = new AppAdapter(this);
        appGrid.setAdapter(appAdapter);
        appGrid.setOnItemClickListener((parent, view, position, id) -> launchApp(appAdapter.itemAt(position)));
        root.addView(appGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private Button actionButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(8), 0);
        b.setLayoutParams(lp);
        b.setBackground(buttonBg(false));
        b.setOnFocusChangeListener((v, focus) -> v.setBackground(buttonBg(focus)));
        b.setOnClickListener(listener);
        return b;
    }

    private GradientDrawable buttonBg(boolean focus) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(12));
        d.setColor(focus ? Color.rgb(38, 92, 146) : Color.rgb(22, 49, 79));
        d.setStroke(dp(2), focus ? Color.WHITE : Color.rgb(74, 111, 148));
        return d;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, AppItem> found = new LinkedHashMap<>();
        Set<String> tvComponents = new HashSet<>();

        Intent tvIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tvIntent, 0)) {
            if (r.activityInfo == null) continue;
            String key = r.activityInfo.packageName + "/" + r.activityInfo.name;
            tvComponents.add(key);
            found.put(key, toItem(pm, r, true));
        }

        Intent mobileIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(mobileIntent, 0)) {
            if (r.activityInfo == null) continue;
            String key = r.activityInfo.packageName + "/" + r.activityInfo.name;
            if (!found.containsKey(key)) found.put(key, toItem(pm, r, tvComponents.contains(key)));
        }

        List<AppItem> items = new ArrayList<>(found.values());
        Collections.sort(items, Comparator.comparing(a -> a.label.toLowerCase(Locale.getDefault())));
        appAdapter.submit(items);
        statusText.setText(items.size() + " εφαρμογές διαθέσιμες. OK για άνοιγμα. Τα MOBILE / TABLET APP μπορεί να χρειάζονται το Ποντίκι TV.");
        if (!items.isEmpty()) {
            appGrid.post(() -> {
                appGrid.setSelection(0);
                appGrid.requestFocus();
            });
        }
    }

    private AppItem toItem(PackageManager pm, ResolveInfo r, boolean tvNative) {
        String label = String.valueOf(r.loadLabel(pm));
        ComponentName component = new ComponentName(r.activityInfo.packageName, r.activityInfo.name);
        return new AppItem(label, r.activityInfo.packageName, component, r.loadIcon(pm), tvNative);
    }

    private void launchApp(AppItem item) {
        if (item == null) return;
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(item.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception first) {
            Intent fallback = getPackageManager().getLaunchIntentForPackage(item.packageName);
            if (fallback != null) {
                try {
                    startActivity(fallback);
                    return;
                } catch (Exception ignored) { }
            }
            toast("Η εφαρμογή δεν μπορεί να ξεκινήσει σε αυτή τη συσκευή.");
        }
    }

    private void openStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new AlertDialog.Builder(this)
                .setTitle("Χρειάζεται άδεια αρχείων")
                .setMessage("Για να βλέπει εσωτερική μνήμη, APK και USB, ενεργοποίησε μία φορά τη διαχείριση όλων των αρχείων.")
                .setNegativeButton("Άκυρο", null)
                .setPositiveButton("Άνοιγμα ρύθμισης", (d, w) -> requestStoragePermission())
                .show();
            return;
        }
        showStorageRoots();
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            toast("Σε αυτή την έκδοση Android χρησιμοποίησε τις κανονικές άδειες αποθήκευσης της εφαρμογής.");
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    private void showStorageRoots() {
        List<File> roots = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        File primary = Environment.getExternalStorageDirectory();
        if (primary != null && primary.exists()) addRoot(roots, paths, primary);

        File storage = new File("/storage");
        File[] mounted = storage.listFiles();
        if (mounted != null) {
            for (File f : mounted) {
                if (!f.isDirectory() || !f.canRead()) continue;
                String name = f.getName();
                if ("self".equals(name) || "emulated".equals(name)) continue;
                addRoot(roots, paths, f);
            }
        }

        if (roots.isEmpty()) {
            toast("Δεν βρέθηκε διαθέσιμος χώρος αποθήκευσης.");
            return;
        }
        String[] names = new String[roots.size()];
        for (int n = 0; n < roots.size(); n++) {
            File f = roots.get(n);
            names[n] = f.equals(primary) ? "Εσωτερικός χώρος" : "USB / Αποθήκευση: " + f.getName();
        }
        new AlertDialog.Builder(this)
            .setTitle("Επίλεξε χώρο")
            .setItems(names, (d, which) -> showDirectory(roots.get(which)))
            .setNegativeButton("Κλείσιμο", null)
            .show();
    }

    private void addRoot(List<File> roots, Set<String> paths, File f) {
        try {
            String p = f.getCanonicalPath();
            if (paths.add(p)) roots.add(f);
        } catch (Exception ignored) { }
    }

    private void showDirectory(File dir) {
        File[] array = dir.listFiles();
        if (array == null) {
            toast("Δεν είναι δυνατή η ανάγνωση αυτού του φακέλου.");
            return;
        }
        List<File> files = new ArrayList<>();
        Collections.addAll(files, array);
        files.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        String[] labels = new String[files.size()];
        for (int n = 0; n < files.size(); n++) {
            File f = files.get(n);
            labels[n] = (f.isDirectory() ? "[ΦΑΚΕΛΟΣ] " : "") + f.getName();
        }

        AlertDialog.Builder b = new AlertDialog.Builder(this)
            .setTitle(dir.getAbsolutePath())
            .setItems(labels, (dialog, which) -> {
                File selected = files.get(which);
                if (selected.isDirectory()) showDirectory(selected); else openFile(selected);
            })
            .setNegativeButton("Κλείσιμο", null);
        File parent = dir.getParentFile();
        if (parent != null && parent.canRead()) b.setPositiveButton("Πάνω", (d, w) -> showDirectory(parent));
        b.show();
    }

    private void openFile(File file) {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".apk")) {
            installApk(file);
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mimeFor(file));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Δεν υπάρχει εφαρμογή που να ανοίγει αυτόν τον τύπο αρχείου.");
        } catch (Exception e) {
            toast("Δεν ήταν δυνατό το άνοιγμα: " + e.getMessage());
        }
    }

    private void installApk(File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
                toast("Ενεργοποίησε «Να επιτρέπεται από αυτή την πηγή» και μετά άνοιξε ξανά το APK.");
            } catch (Exception e) {
                toast("Άνοιξε τις ρυθμίσεις και επίτρεψε εγκατάσταση άγνωστων εφαρμογών για το TV Compatibility Hub.");
            }
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            toast("Το APK δεν μπορεί να δοθεί στον εγκαταστάτη: " + e.getMessage());
        }
    }

    private String mimeFor(File file) {
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getName());
        String mime = ext == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ROOT));
        return mime == null ? "*/*" : mime;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            toast("Βρες «TV Compatibility Hub» και ενεργοποίησε το εικονικό ποντίκι.");
        } catch (Exception e) {
            toast("Δεν άνοιξαν οι ρυθμίσεις προσβασιμότητας.");
        }
    }

    private void openDisplaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        } catch (Exception e) {
            toast("Το συγκεκριμένο TV Box δεν διαθέτει αυτή τη σελίδα ρυθμίσεων.");
        }
    }

    private void showHelp() {
        new AlertDialog.Builder(this)
            .setTitle("Τι κάνει το TV Compatibility Hub")
            .setMessage("• Εμφανίζει εφαρμογές TV και εφαρμογές κινητού/tablet που έχουν launcher.\n\n" +
                "• Ανοίγει APK από εσωτερικό χώρο ή USB και τα δίνει στον εγκαταστάτη Android.\n\n" +
                "• Ανοίγει αρχεία με τις εφαρμογές που είναι εγκατεστημένες στο Box.\n\n" +
                "• Το Ποντίκι TV μετατρέπει τα βελάκια του τηλεχειριστηρίου σε δείκτη αφής για πολλές εφαρμογές κινητού.\n\n" +
                "Δεν μπορεί να κάνει συμβατή μια εφαρμογή που απαιτεί διαφορετικό επεξεργαστή, DRM, GPS, γυροσκόπιο ή λειτουργίες που λείπουν από το TV Box.")
            .setPositiveButton("OK", null)
            .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
