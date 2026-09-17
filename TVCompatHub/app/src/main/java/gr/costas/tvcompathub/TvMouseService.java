package gr.costas.tvcompathub;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

public final class TvMouseService extends AccessibilityService {
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private TextView cursor;
    private int cursorSize;
    private int screenW;
    private int screenH;
    private boolean mouseActive = true;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        cursorSize = dp(46);

        cursor = new TextView(this);
        cursor.setText("+");
        cursor.setTextColor(Color.WHITE);
        cursor.setTextSize(30);
        cursor.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(215, 27, 102, 172));
        bg.setStroke(dp(2), Color.WHITE);
        cursor.setBackground(bg);

        params = new WindowManager.LayoutParams(
            cursorSize,
            cursorSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = Math.max(0, (screenW - cursorSize) / 2);
        params.y = Math.max(0, (screenH - cursorSize) / 2);

        try {
            wm.addView(cursor, params);
            toast("Ποντίκι TV ενεργό: βελάκια = κίνηση, OK = πάτημα, MENU = on/off");
        } catch (Exception e) {
            toast("Δεν ήταν δυνατό να εμφανιστεί ο δείκτης TV.");
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int key = event.getKeyCode();

        if (key == KeyEvent.KEYCODE_MENU || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            mouseActive = !mouseActive;
            if (cursor != null) cursor.setVisibility(mouseActive ? TextView.VISIBLE : TextView.INVISIBLE);
            toast(mouseActive ? "Ποντίκι TV ενεργό" : "Ποντίκι TV σε παύση");
            return true;
        }

        if (!mouseActive || params == null) return false;
        int step = dp(36);
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                params.x -= step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                params.x += step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                params.y -= step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                params.y += step;
                moveCursor();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                tapCursor();
                return true;
            default:
                return false;
        }
    }

    private void moveCursor() {
        params.x = Math.max(0, Math.min(params.x, Math.max(0, screenW - cursorSize)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, screenH - cursorSize)));
        try {
            if (wm != null && cursor != null) wm.updateViewLayout(cursor, params);
        } catch (Exception ignored) { }
    }

    private void tapCursor() {
        float x = params.x + cursorSize / 2f;
        float y = params.y + cursorSize / 2f;
        Path p = new Path();
        p.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p, 0, 70))
            .build();
        if (!dispatchGesture(gesture, null, null)) toast("Το πάτημα δεν υποστηρίχθηκε σε αυτή την οθόνη.");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    public void onDestroy() {
        try {
            if (wm != null && cursor != null) wm.removeView(cursor);
        } catch (Exception ignored) { }
        cursor = null;
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
