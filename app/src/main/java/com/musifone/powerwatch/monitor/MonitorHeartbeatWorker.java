package com.musifone.powerwatch.monitor;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.musifone.powerwatch.data.SettingsStore;

import java.util.concurrent.TimeUnit;

/**
 * Low-frequency safety net: if the foreground service was killed, restart it.
 * Interval is 30 minutes — WorkManager's practical minimum on Android 10.
 */
public class MonitorHeartbeatWorker extends Worker {
    private static final String UNIQUE = "monitor_heartbeat";

    public MonitorHeartbeatWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SettingsStore settings = new SettingsStore(getApplicationContext());
        if (settings.isMonitoringEnabled()) {
            BatteryMonitorService.start(getApplicationContext());
        }
        return Result.success();
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                MonitorHeartbeatWorker.class,
                30,
                TimeUnit.MINUTES
        ).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE);
    }
}
