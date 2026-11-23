package com.musifone.powerwatch.monitor;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.musifone.powerwatch.PowerWatchApp;
import com.musifone.powerwatch.R;
import com.musifone.powerwatch.alert.AlertDispatcher;
import com.musifone.powerwatch.data.AppDatabase;
import com.musifone.powerwatch.data.BatterySample;
import com.musifone.powerwatch.data.SettingsStore;
import com.musifone.powerwatch.ui.MainActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Event-driven battery watcher. No polling loop — only reacts to system
 * ACTION_BATTERY_CHANGED broadcasts, which keeps idle CPU near zero.
 */
public class BatteryMonitorService extends Service {
    public static final String ACTION_STATUS = "com.musifone.powerwatch.STATUS";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_CHARGING = "charging";

    private static final int NOTIFICATION_ID = 1001;
    private static final long RETENTION_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final long MIN_SAMPLE_GAP_MS = 60_000L;

    private SettingsStore settings;
    private AppDatabase database;
    private AlertDispatcher alerts;
    private ExecutorService io;
    private PowerManager.WakeLock briefLock;
    private long lastSampleAt;
    private int lastLevel = -1;
    private boolean lastCharging;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                return;
            }
            handleSnapshot(BatterySnapshot.fromIntent(intent));
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, BatteryMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, BatteryMonitorService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PowerWatchApp app = (PowerWatchApp) getApplication();
        settings = app.getSettings();
        database = app.getDatabase();
        alerts = new AlertDispatcher(this, settings);
        io = Executors.newSingleThreadExecutor();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            briefLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "musifone:battery_event");
            briefLock.setReferenceCounted(false);
        }

        BatterySnapshot snap = BatterySnapshot.fromSticky(this);
        startForeground(NOTIFICATION_ID, buildMonitorNotification(snap));
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        handleSnapshot(snap);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        settings.setMonitoringEnabled(true);
        BatterySnapshot snap = BatterySnapshot.fromSticky(this);
        startForeground(NOTIFICATION_ID, buildMonitorNotification(snap));
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (briefLock != null && briefLock.isHeld()) {
            briefLock.release();
        }
        if (io != null) {
            io.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleSnapshot(BatterySnapshot snap) {
        if (briefLock != null) {
            briefLock.acquire(3_000L);
        }
        try {
            boolean levelChanged = snap.levelPercent != lastLevel;
            boolean chargeChanged = snap.charging != lastCharging;
            long now = System.currentTimeMillis();
            boolean due = (now - lastSampleAt) >= MIN_SAMPLE_GAP_MS;

            if (levelChanged || chargeChanged || due || lastLevel < 0) {
                persistAndAlert(snap, now, levelChanged || chargeChanged || lastLevel < 0);
                lastSampleAt = now;
                lastLevel = snap.levelPercent;
                lastCharging = snap.charging;
            }

            startForeground(NOTIFICATION_ID, buildMonitorNotification(snap));
            broadcastStatus(snap);
        } finally {
            if (briefLock != null && briefLock.isHeld()) {
                briefLock.release();
            }
        }
    }

    private void persistAndAlert(BatterySnapshot snap, long now, boolean meaningfulChange) {
        io.execute(() -> {
            if (meaningfulChange || settings.getLastRecordedLevel() != snap.levelPercent) {
                database.batteryDao().insert(new BatterySample(
                        now,
                        snap.levelPercent,
                        snap.charging,
                        snap.status,
                        snap.temperatureTenthsC,
                        snap.voltageMv
                ));
                settings.setLastRecordedLevel(snap.levelPercent);
                database.batteryDao().deleteOlderThan(now - RETENTION_MS);
            }

            if (snap.charging) {
                settings.clearAlertMarker();
            } else {
                alerts.evaluate(snap.levelPercent);
            }
        });
    }

    private void broadcastStatus(BatterySnapshot snap) {
        Intent status = new Intent(ACTION_STATUS);
        status.setPackage(getPackageName());
        status.putExtra(EXTRA_LEVEL, snap.levelPercent);
        status.putExtra(EXTRA_CHARGING, snap.charging);
        sendBroadcast(status);
    }

    private Notification buildMonitorNotification(BatterySnapshot snap) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String state = getString(snap.charging ? R.string.status_charging : R.string.status_discharging);
        return new NotificationCompat.Builder(this, PowerWatchApp.CHANNEL_MONITOR)
                .setContentTitle(getString(R.string.monitor_notification_title))
                .setContentText(getString(R.string.monitor_notification_text, snap.levelPercent, state))
                .setSmallIcon(R.drawable.ic_battery)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
