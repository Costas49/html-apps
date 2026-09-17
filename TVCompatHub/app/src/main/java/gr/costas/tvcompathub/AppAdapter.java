package gr.costas.tvcompathub;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public final class AppAdapter extends BaseAdapter {
    private final Context context;
    private final List<AppItem> items = new ArrayList<>();

    public AppAdapter(Context context) {
        this.context = context;
    }

    public void submit(List<AppItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public AppItem itemAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @Override public int getCount() { return items.size(); }
    @Override public AppItem getItem(int position) { return items.get(position); }
    @Override public long getItemId(int position) { return position; }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int strokeColor, int fillColor) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(fillColor);
        d.setStroke(dp(2), strokeColor);
        return d;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder h;
        if (convertView == null) {
            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(12), dp(12), dp(12), dp(12));
            root.setFocusable(true);
            root.setClickable(true);
            root.setMinimumHeight(dp(170));
            root.setBackground(bg(Color.rgb(58, 88, 122), Color.rgb(18, 35, 57)));

            ImageView icon = new ImageView(context);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(72), dp(72));
            iconLp.bottomMargin = dp(8);
            root.addView(icon, iconLp);

            TextView label = new TextView(context);
            label.setTextColor(Color.WHITE);
            label.setTextSize(17);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            root.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView kind = new TextView(context);
            kind.setTextColor(Color.rgb(170, 199, 228));
            kind.setTextSize(12);
            kind.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams kindLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            kindLp.topMargin = dp(5);
            root.addView(kind, kindLp);

            h = new Holder(icon, label, kind);
            root.setTag(h);
            root.setOnFocusChangeListener((v, hasFocus) -> v.setBackground(
                hasFocus
                    ? bg(Color.WHITE, Color.rgb(32, 79, 126))
                    : bg(Color.rgb(58, 88, 122), Color.rgb(18, 35, 57))
            ));
            convertView = root;
        } else {
            h = (Holder) convertView.getTag();
        }

        AppItem item = items.get(position);
        h.icon.setImageDrawable(item.icon);
        h.label.setText(item.label);
        h.kind.setText(item.tvNative ? "TV APP" : "MOBILE / TABLET APP");
        convertView.setContentDescription(item.label + (item.tvNative ? ", εφαρμογή TV" : ", εφαρμογή κινητού ή tablet"));
        return convertView;
    }

    private static final class Holder {
        final ImageView icon;
        final TextView label;
        final TextView kind;
        Holder(ImageView icon, TextView label, TextView kind) {
            this.icon = icon;
            this.label = label;
            this.kind = kind;
        }
    }
}
