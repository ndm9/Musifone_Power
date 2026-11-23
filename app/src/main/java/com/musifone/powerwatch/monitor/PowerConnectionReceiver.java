package com.musifone.powerwatch.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.musifone.powerwatch.data.SettingsStore;

/**
 * Ensures the monitor service is alive when power is plugged/unplugged.
 * Useful if Samsung killed the process while the phone sat idle.
 */
public class PowerConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SettingsStore settings = new SettingsStore(context);
        if (settings.isMonitoringEnabled()) {
            BatteryMonitorService.start(context);
        }
    }
}
