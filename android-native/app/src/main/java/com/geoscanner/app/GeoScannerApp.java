package com.geoscanner.app;

import android.app.Application;
import android.content.Context;

import com.geoscanner.app.ble.BLEManager;
import com.geoscanner.app.utils.LocaleHelper;

public class GeoScannerApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.applyLocale(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        BLEManager.getInstance().initialize(this);
    }
}
