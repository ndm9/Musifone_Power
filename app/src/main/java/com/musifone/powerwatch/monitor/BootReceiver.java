package com.musifone.powerwatch.monitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.musifone.powerwatch.data.SettingsStore;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        SettingsStore settings = new SettingsStore(context);
        if (settings.isMonitoringEnabled()) {
            BatteryMonitorService.start(context);
        }
    }
}
