package gr.costas.tvcompathub;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public final class AppItem {
    public final String label;
    public final String packageName;
    public final ComponentName component;
    public final Drawable icon;
    public final boolean tvNative;

    public AppItem(String label, String packageName, ComponentName component, Drawable icon, boolean tvNative) {
        this.label = label;
        this.packageName = packageName;
        this.component = component;
        this.icon = icon;
        this.tvNative = tvNative;
    }
}
