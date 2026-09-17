package gr.costas.tvcompathub;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class BrowserActivity extends Activity {
    private WebView webView;
    private EditText addressBar;
    private FrameLayout browserFrame;
    private TextView cursor;
    private LinearLayout keyboardPanel;
    private final List<View> floatingControls = new ArrayList<>();

    private int cursorX;
    private int cursorY;
    private int cursorSize;
    private boolean keyboardVisible = false;
    private boolean keyboardTargetUrl = false;
    private boolean greekKeyboard = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        configureWebView();
        webView.loadUrl("https://www.google.com/");
        browserFrame.post(() -> {
            cursorX = Math.max(0, browserFrame.getWidth() / 2 - cursorSize / 2);
            cursorY = Math.max(0, browserFrame.getHeight() / 2 - cursorSize / 2);
            updateCursor();
        });
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7, 14, 25));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(7), dp(8), dp(7));
        top.setBackgroundColor(Color.rgb(18, 35, 56));

        TextView title = new TextView(this);
        title.setText("TV Browser");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, 1);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.rightMargin = dp(10);
        top.addView(title, titleLp);

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextColor(Color.WHITE);
        addressBar.setHintTextColor(Color.rgb(160, 180, 200));
        addressBar.setHint("Διεύθυνση ή αναζήτηση");
        addressBar.setTextSize(15);
        addressBar.setFocusable(false);
        addressBar.setBackground(roundBg(Color.rgb(33, 53, 75), Color.rgb(83, 119, 153)));
        addressBar.setPadding(dp(12), dp(7), dp(12), dp(7));
        top.addView(addressBar, new LinearLayout.LayoutParams(0, dp(44), 1f));

        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        browserFrame = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        browserFrame.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(4), dp(4), dp(4), dp(4));
        controls.setBackground(roundBg(Color.argb(215, 15, 32, 53), Color.argb(240, 95, 137, 177)));

        addFloatButton(controls, "URL", v -> showKeyboard(true));
        addFloatButton(controls, "↑", v -> scrollPage(-1));
        addFloatButton(controls, "↓", v -> scrollPage(1));
        addFloatButton(controls, "⌨", v -> showKeyboard(false));
        addFloatButton(controls, "↻", v -> webView.reload());
        addFloatButton(controls, "←", v -> { if (webView.canGoBack()) webView.goBack(); });
        addFloatButton(controls, "→", v -> { if (webView.canGoForward()) webView.goForward(); });

        FrameLayout.LayoutParams controlsLp = new FrameLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        controlsLp.rightMargin = dp(8);
        browserFrame.addView(controls, controlsLp);

        cursorSize = dp(42);
        cursor = new TextView(this);
        cursor.setText("↖");
        cursor.setTextColor(Color.WHITE);
        cursor.setTextSize(30);
        cursor.setGravity(Gravity.CENTER);
        cursor.setShadowLayer(5f, 2f, 2f, Color.BLACK);
        GradientDrawable cursorBg = new GradientDrawable();
        cursorBg.setShape(GradientDrawable.OVAL);
        cursorBg.setColor(Color.argb(130, 20, 95, 170));
        cursorBg.setStroke(dp(2), Color.WHITE);
        cursor.setBackground(cursorBg);
        cursor.setFocusable(false);
        cursor.setClickable(false);
        browserFrame.addView(cursor, new FrameLayout.LayoutParams(cursorSize, cursorSize));

        root.addView(browserFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        keyboardPanel = new LinearLayout(this);
        keyboardPanel.setOrientation(LinearLayout.VERTICAL);
        keyboardPanel.setPadding(dp(8), dp(5), dp(8), dp(7));
        keyboardPanel.setBackgroundColor(Color.rgb(14, 29, 47));
        keyboardPanel.setVisibility(View.GONE);
        root.addView(keyboardPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addressBar.setText(url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
    }

    private void addFloatButton(LinearLayout parent, String text, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(text.length() > 1 ? 12 : 22);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setFocusable(false);
        b.setClickable(true);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundBg(Color.rgb(30, 64, 98), Color.rgb(111, 154, 196)));
        b.setOnClickListener(action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(60), dp(48));
        lp.bottomMargin = dp(4);
        parent.addView(b, lp);
        floatingControls.add(b);
    }

    private void showKeyboard(boolean targetUrl) {
        keyboardTargetUrl = targetUrl;
        keyboardVisible = true;
        cursor.setVisibility(View.INVISIBLE);
        rebuildKeyboard();
        keyboardPanel.setVisibility(View.VISIBLE);
        View first = keyboardPanel.getChildCount() > 0 ? ((ViewGroup) keyboardPanel.getChildAt(0)).getChildAt(0) : null;
        if (first != null) first.requestFocus();
        toast(targetUrl ? "Γράψε διεύθυνση ή αναζήτηση και πάτησε ENTER" : "Γράψε στο πεδίο της σελίδας που είχες επιλέξει");
    }

    private void hideKeyboard() {
        keyboardVisible = false;
        keyboardPanel.setVisibility(View.GONE);
        cursor.setVisibility(View.VISIBLE);
        browserFrame.requestFocus();
    }

    private void rebuildKeyboard() {
        keyboardPanel.removeAllViews();
        String[] rows = greekKeyboard
            ? new String[]{"1234567890", "ερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ.,-/"}
            : new String[]{"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm.,-/"};

        for (String row : rows) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER);
            for (int i = 0; i < row.length(); i++) {
                String key = String.valueOf(row.charAt(i));
                addKey(line, key, () -> inputText(key));
            }
            keyboardPanel.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        addWideKey(actions, greekKeyboard ? "EN" : "ΕΛ", () -> {
            greekKeyboard = !greekKeyboard;
            rebuildKeyboard();
            View first = ((ViewGroup) keyboardPanel.getChildAt(0)).getChildAt(0);
            first.requestFocus();
        });
        addWideKey(actions, "SPACE", () -> inputText(" "));
        addWideKey(actions, "⌫", this::backspace);
        addWideKey(actions, "ENTER", this::pressEnter);
        addWideKey(actions, "ΚΛΕΙΣΙΜΟ", this::hideKeyboard);
        keyboardPanel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
    }

    private void addKey(LinearLayout row, String text, Runnable action) {
        Button b = keyboardButton(text);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(b, lp);
    }

    private void addWideKey(LinearLayout row, String text, Runnable action) {
        Button b = keyboardButton(text);
        b.setTextSize(12);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        row.addView(b, lp);
    }

    private Button keyboardButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(17);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundBg(Color.rgb(31, 61, 91), Color.rgb(89, 133, 176)));
        b.setOnFocusChangeListener((v, focused) -> v.setBackground(roundBg(
            focused ? Color.rgb(48, 111, 170) : Color.rgb(31, 61, 91),
            focused ? Color.WHITE : Color.rgb(89, 133, 176))));
        return b;
    }

    private void inputText(String text) {
        if (keyboardTargetUrl) {
            addressBar.append(text);
            return;
        }
        String q = JSONObject.quote(text);
        String js = "(function(){var e=document.activeElement;if(!e)return;" +
            "if(e.isContentEditable){document.execCommand('insertText',false," + q + ");return;}" +
            "if(e.tagName==='INPUT'||e.tagName==='TEXTAREA'){var v=e.value||'';var s=(e.selectionStart==null?v.length:e.selectionStart);" +
            "var n=(e.selectionEnd==null?s:e.selectionEnd);e.value=v.substring(0,s)+" + q + "+v.substring(n);" +
            "e.selectionStart=e.selectionEnd=s+" + text.length() + ";e.dispatchEvent(new Event('input',{bubbles:true}));}})();";
        webView.evaluateJavascript(js, null);
    }

    private void backspace() {
        if (keyboardTargetUrl) {
            String s = addressBar.getText().toString();
            if (!s.isEmpty()) addressBar.setText(s.substring(0, s.length() - 1));
            addressBar.setSelection(addressBar.getText().length());
            return;
        }
        String js = "(function(){var e=document.activeElement;if(!e)return;" +
            "if(e.tagName==='INPUT'||e.tagName==='TEXTAREA'){var v=e.value||'';var s=(e.selectionStart==null?v.length:e.selectionStart);" +
            "var n=(e.selectionEnd==null?s:e.selectionEnd);if(s!==n){e.value=v.substring(0,s)+v.substring(n);e.selectionEnd=e.selectionStart=s;}" +
            "else if(s>0){e.value=v.substring(0,s-1)+v.substring(s);e.selectionEnd=e.selectionStart=s-1;}e.dispatchEvent(new Event('input',{bubbles:true}));}})();";
        webView.evaluateJavascript(js, null);
    }

    private void pressEnter() {
        if (keyboardTargetUrl) {
            String raw = addressBar.getText().toString().trim();
            if (raw.isEmpty()) return;
            String url;
            if (raw.startsWith("http://") || raw.startsWith("https://")) {
                url = raw;
            } else if (!raw.contains(" ") && raw.contains(".")) {
                url = "https://" + raw;
            } else {
                url = "https://www.google.com/search?q=" + Uri.encode(raw);
            }
            hideKeyboard();
            webView.loadUrl(url);
            return;
        }
        String js = "(function(){var e=document.activeElement;if(!e)return;if(e.form){if(e.form.requestSubmit)e.form.requestSubmit();else e.form.submit();}" +
            "else{e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',bubbles:true}));}})();";
        webView.evaluateJavascript(js, null);
    }

    private void scrollPage(int direction) {
        int amount = Math.max(dp(220), (int) (webView.getHeight() * 0.72f));
        webView.smoothScrollBy(0, direction * amount);
    }

    private void moveCursor(int dx, int dy) {
        cursorX += dx;
        cursorY += dy;
        int maxX = Math.max(0, browserFrame.getWidth() - cursorSize);
        int maxY = Math.max(0, browserFrame.getHeight() - cursorSize);
        cursorX = Math.max(0, Math.min(cursorX, maxX));
        cursorY = Math.max(0, Math.min(cursorY, maxY));
        updateCursor();
    }

    private void updateCursor() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cursor.getLayoutParams();
        lp.leftMargin = cursorX;
        lp.topMargin = cursorY;
        cursor.setLayoutParams(lp);
        cursor.bringToFront();
    }

    private void pointerClick() {
        int[] frameLoc = new int[2];
        browserFrame.getLocationOnScreen(frameLoc);
        int screenX = frameLoc[0] + cursorX + cursorSize / 2;
        int screenY = frameLoc[1] + cursorY + cursorSize / 2;

        for (View control : floatingControls) {
            int[] loc = new int[2];
            control.getLocationOnScreen(loc);
            Rect r = new Rect(loc[0], loc[1], loc[0] + control.getWidth(), loc[1] + control.getHeight());
            if (r.contains(screenX, screenY)) {
                control.performClick();
                return;
            }
        }

        int[] webLoc = new int[2];
        webView.getLocationOnScreen(webLoc);
        float x = screenX - webLoc[0];
        float y = screenY - webLoc[1];
        if (x < 0 || y < 0 || x >= webView.getWidth() || y >= webView.getHeight()) return;
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (keyboardVisible) {
            if (key == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                hideKeyboard();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int step = dp(34);
            if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) && event.getRepeatCount() >= 4) {
                scrollPage(key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
                return true;
            }
            switch (key) {
                case KeyEvent.KEYCODE_DPAD_LEFT: moveCursor(-step, 0); return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT: moveCursor(step, 0); return true;
                case KeyEvent.KEYCODE_DPAD_UP: moveCursor(0, -step); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: moveCursor(0, step); return true;
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP: scrollPage(-1); return true;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN: scrollPage(1); return true;
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: showKeyboard(false); return true;
                default: break;
            }
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                pointerClick();
                return true;
            }
            if (key == KeyEvent.KEYCODE_BACK) {
                if (webView.canGoBack()) webView.goBack(); else finish();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private GradientDrawable roundBg(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(10));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
