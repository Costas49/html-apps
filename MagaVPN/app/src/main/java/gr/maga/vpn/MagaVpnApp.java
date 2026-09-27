package gr.maga.vpn;

import android.app.Application;

public final class MagaVpnApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        VpnController.init(this);
    }
}
