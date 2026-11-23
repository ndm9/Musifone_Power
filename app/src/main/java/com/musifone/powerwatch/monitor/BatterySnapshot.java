package com.musifone.powerwatch.monitor;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

public final class BatterySnapshot {
    public final int levelPercent;
    public final boolean charging;
    public final int status;
    public final int temperatureTenthsC;
    public final int voltageMv;

    public BatterySnapshot(int levelPercent, boolean charging, int status,
                           int temperatureTenthsC, int voltageMv) {
        this.levelPercent = levelPercent;
        this.charging = charging;
        this.status = status;
        this.temperatureTenthsC = temperatureTenthsC;
        this.voltageMv = voltageMv;
    }

    public static BatterySnapshot fromSticky(Context context) {
        Intent intent = context.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        );
        return fromIntent(intent);
    }

    public static BatterySnapshot fromIntent(Intent intent) {
        if (intent == null) {
            return new BatterySnapshot(0, false, BatteryManager.BATTERY_STATUS_UNKNOWN, 0, 0);
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int percent = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : 0;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean charging = plugged != 0
                || status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        return new BatterySnapshot(percent, charging, status, temp, voltage);
    }
}
