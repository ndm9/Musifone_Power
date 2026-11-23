package com.musifone.powerwatch;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.musifone.powerwatch.data.AppDatabase;
import com.musifone.powerwatch.data.SettingsStore;

public class PowerWatchApp extends Application {
    public static final String CHANNEL_MONITOR = "battery_monitor";
    public static final String CHANNEL_ALERT = "charge_alerts";

    private AppDatabase database;
    private SettingsStore settings;

    @Override
    public void onCreate() {
        super.onCreate();
        database = AppDatabase.getInstance(this);
        settings = new SettingsStore(this);
        createChannels();
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public SettingsStore getSettings() {
        return settings;
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }

        NotificationChannel monitor = new NotificationChannel(
                CHANNEL_MONITOR,
                getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        monitor.setDescription(getString(R.string.monitor_channel_desc));
        monitor.setShowBadge(false);
        nm.createNotificationChannel(monitor);

        NotificationChannel alert = new NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        alert.setDescription(getString(R.string.alert_channel_desc));
        alert.enableVibration(true);
        nm.createNotificationChannel(alert);
    }
}
