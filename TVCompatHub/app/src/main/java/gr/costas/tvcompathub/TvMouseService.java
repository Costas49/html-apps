package gr.costas.tvcompathub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public final class TvMouseService extends AccessibilityService {
    private WindowManager wm;
    private WindowManager.LayoutParams cursorParams;
    private TextView cursor;
    private int cursorSize;
    private int screenW;
    private int screenH;
    private boolean mouseActive = true;
    private boolean centerLong = false;

    private LinearLayout keyboard;
    private WindowManager.LayoutParams keyboardParams;
    private final List<TextView> keyboardKeys = new ArrayList<>();
    private final List<String> keyboardValues = new ArrayList<>();
    private int selectedKey = 0;
    private boolean keyboardVisible = false;
    private boolean greekKeyboard = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            setServiceInfo(info);
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        cursorSize = dp(42);

        cursor = new TextView(this);
        cursor.setText("↖");
        cursor.setTextColor(Color.WHITE);
        cursor.setTextSize(29);
        cursor.setGravity(Gravity.CENTER);
        cursor.setShadowLayer(4f, 2f, 2f, Color.BLACK);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(150, 25, 103, 180));
        bg.setStroke(dp(2), Color.WHITE);
        cursor.setBackground(bg);

        cursorParams = new WindowManager.LayoutParams(
            cursorSize,
            cursorSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        cursorParams.gravity = Gravity.TOP | Gravity.START;
        cursorParams.x = Math.max(0, (screenW - cursorSize) / 2);
        cursorParams.y = Math.max(0, (screenH - cursorSize) / 2);

        try {
            wm.addView(cursor, cursorParams);
            toast("TV Mouse: βελάκια=κίνηση, OK=click, κράτημα ↑/↓=scroll, κράτημα OK=πληκτρολόγιο");
        } catch (Exception e) {
            toast("Δεν ήταν δυνατό να εμφανιστεί το TV Mouse.");
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();

        if (keyboardVisible) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
            switch (key) {
                case KeyEvent.KEYCODE_BACK:
                    hideKeyboard();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    selectKey(selectedKey - 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    selectKey(selectedKey + 1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    selectKey(selectedKey - 10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    selectKey(selectedKey + 10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    activateSelectedKey();
                    return true;
                default:
                    return true;
            }
        }

        if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mouseActive = !mouseActive;
                if (cursor != null) cursor.setVisibility(mouseActive ? View.VISIBLE : View.INVISIBLE);
                toast(mouseActive ? "TV Mouse ενεργό" : "TV Mouse σε παύση");
            }
            return true;
        }

        if (!mouseActive || cursorParams == null) return false;

        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER || key == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() >= 2 && !centerLong) {
                    centerLong = true;
                    showKeyboard();
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (!centerLong) tapCursor();
                centerLong = false;
                return true;
            }
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

        if ((key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN) && event.getRepeatCount() >= 4) {
            scrollPage(key == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1);
            return true;
        }

        int step = dp(36);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                cursorParams.x -= step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cursorParams.x += step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                cursorParams.y -= step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                cursorParams.y += step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                scrollPage(-1);
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                scrollPage(1);
                return true;
            default:
                return false;
        }
    }

    private void moveCursor() {
        cursorParams.x = Math.max(0, Math.min(cursorParams.x, Math.max(0, screenW - cursorSize)));
        cursorParams.y = Math.max(0, Math.min(cursorParams.y, Math.max(0, screenH - cursorSize)));
        try {
            if (wm != null && cursor != null) wm.updateViewLayout(cursor, cursorParams);
        } catch (Exception ignored) { }
    }

    private void tapCursor() {
        float x = cursorParams.x + cursorSize / 2f;
        float y = cursorParams.y + cursorSize / 2f;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p, 0, 90))
            .build();
        if (!dispatchGesture(gesture, null, null)) toast("Το click δεν υποστηρίχθηκε σε αυτή την οθόνη.");
    }

    private void scrollPage(int direction) {
        float x = cursorParams.x + cursorSize / 2f;
        float fromY = direction > 0 ? screenH * 0.76f : screenH * 0.28f;
        float toY = direction > 0 ? screenH * 0.28f : screenH * 0.76f;
        Path p = new Path();
        p.moveTo(x, fromY);
        p.lineTo(x, toY);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p, 0, 330))
            .build();
        dispatchGesture(gesture, null, null);
    }

    private void showKeyboard() {
        if (keyboardVisible || wm == null) return;
        keyboardVisible = true;
        if (cursor != null) cursor.setVisibility(View.INVISIBLE);
        buildKeyboard();
        try {
            wm.addView(keyboard, keyboardParams);
            selectKey(0);
            toast("Πληκτρολόγιο: βελάκια=επιλογή, OK=γράμμα, BACK=κλείσιμο");
        } catch (Exception e) {
            keyboardVisible = false;
            if (cursor != null) cursor.setVisibility(View.VISIBLE);
            toast("Δεν άνοιξε το εικονικό πληκτρολόγιο.");
        }
    }

    private void buildKeyboard() {
        keyboardKeys.clear();
        keyboardValues.clear();
        keyboard = new LinearLayout(this);
        keyboard.setOrientation(LinearLayout.VERTICAL);
        keyboard.setPadding(dp(8), dp(6), dp(8), dp(8));
        keyboard.setBackground(roundBg(Color.argb(245, 12, 29, 47), Color.WHITE));

        String[] rows = greekKeyboard
            ? new String[]{"1234567890", "ερτυθιοπ", "ασδφγηξκλ", "ζχψωβνμ.,-/"}
            : new String[]{"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm.,-/"};
        for (String rowText : rows) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (int i = 0; i < rowText.length(); i++) {
                String value = String.valueOf(rowText.charAt(i));
                addKeyboardKey(row, value, value);
            }
            keyboard.addView(row, new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(42)));
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        addKeyboardKey(actions, greekKeyboard ? "EN" : "ΕΛ", "LANG");
        addKeyboardKey(actions, "SPACE", "SPACE");
        addKeyboardKey(actions, "⌫", "BACK");
        addKeyboardKey(actions, "ENTER", "ENTER");
        addKeyboardKey(actions, "ΚΛΕΙΣΙΜΟ", "CLOSE");
        keyboard.addView(actions, new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(48)));

        keyboardParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        keyboardParams.gravity = Gravity.BOTTOM;
    }

    private void addKeyboardKey(LinearLayout row, String label, String value) {
        TextView key = new TextView(this);
        key.setText(label);
        key.setGravity(Gravity.CENTER);
        key.setTextColor(Color.WHITE);
        key.setTextSize(label.length() > 2 ? 12 : 17);
        key.setBackground(keyBg(false));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(key, lp);
        keyboardKeys.add(key);
        keyboardValues.add(value);
    }

    private void selectKey(int index) {
        if (keyboardKeys.isEmpty()) return;
        selectedKey = Math.max(0, Math.min(index, keyboardKeys.size() - 1));
        for (int i = 0; i < keyboardKeys.size(); i++) keyboardKeys.get(i).setBackground(keyBg(i == selectedKey));
    }

    private void activateSelectedKey() {
        if (selectedKey < 0 || selectedKey >= keyboardValues.size()) return;
        String value = keyboardValues.get(selectedKey);
        switch (value) {
            case "LANG":
                greekKeyboard = !greekKeyboard;
                try { if (wm != null && keyboard != null) wm.removeView(keyboard); } catch (Exception ignored) { }
                buildKeyboard();
                try { wm.addView(keyboard, keyboardParams); } catch (Exception ignored) { }
                selectKey(0);
                break;
            case "SPACE": inputToFocused(" "); break;
            case "BACK": backspaceFocused(); break;
            case "ENTER": enterFocused(); break;
            case "CLOSE": hideKeyboard(); break;
            default: inputToFocused(value); break;
        }
    }

    private AccessibilityNodeInfo focusedInput() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        AccessibilityNodeInfo focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focus != null) return focus;
        return findEditable(root);
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isFocused()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private void inputToFocused(String text) {
        AccessibilityNodeInfo node = focusedInput();
        if (node == null) {
            toast("Πάτησε πρώτα σε πεδίο κειμένου.");
            return;
        }
        CharSequence old = node.getText();
        String next = (old == null ? "" : old.toString()) + text;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next);
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) toast("Αυτό το πεδίο δεν δέχεται εικονική πληκτρολόγηση.");
    }

    private void backspaceFocused() {
        AccessibilityNodeInfo node = focusedInput();
        if (node == null) return;
        String old = node.getText() == null ? "" : node.getText().toString();
        if (old.isEmpty()) return;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old.substring(0, old.length() - 1));
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }

    private void enterFocused() {
        AccessibilityNodeInfo node = focusedInput();
        if (node == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId());
        }
    }

    private void hideKeyboard() {
        if (!keyboardVisible) return;
        try {
            if (wm != null && keyboard != null) wm.removeView(keyboard);
        } catch (Exception ignored) { }
        keyboard = null;
        keyboardVisible = false;
        if (cursor != null && mouseActive) cursor.setVisibility(View.VISIBLE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        hideKeyboard();
        try {
            if (wm != null && cursor != null) wm.removeView(cursor);
        } catch (Exception ignored) { }
        cursor = null;
        super.onDestroy();
    }

    private GradientDrawable keyBg(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(7));
        d.setColor(selected ? Color.rgb(49, 112, 171) : Color.rgb(31, 61, 91));
        d.setStroke(dp(selected ? 2 : 1), selected ? Color.WHITE : Color.rgb(92, 133, 174));
        return d;
    }

    private GradientDrawable roundBg(int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(fill);
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
