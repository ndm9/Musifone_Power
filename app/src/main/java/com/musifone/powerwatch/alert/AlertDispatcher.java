package com.musifone.powerwatch.alert;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.musifone.powerwatch.PowerWatchApp;
import com.musifone.powerwatch.R;
import com.musifone.powerwatch.data.SettingsStore;
import com.musifone.powerwatch.ui.MainActivity;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertDispatcher {
    private static final String TAG = "AlertDispatcher";
    private static final long COOLDOWN_MS = 45L * 60L * 1000L;

    private final Context appContext;
    private final SettingsStore settings;
    private final ExecutorService emailIo = Executors.newSingleThreadExecutor();

    public AlertDispatcher(Context context, SettingsStore settings) {
        this.appContext = context.getApplicationContext();
        this.settings = settings;
    }

    /**
     * Fires when battery crosses at or below a configured threshold while discharging.
     * Dedupes the same threshold for {@link #COOLDOWN_MS} so brief fluctuations do not spam.
     */
    public void evaluate(int levelPercent) {
        List<Integer> thresholds = settings.getThresholds();
        Integer hit = null;
        for (Integer threshold : thresholds) {
            if (levelPercent <= threshold) {
                hit = threshold;
            }
        }
        if (hit == null) {
            return;
        }

        long now = System.currentTimeMillis();
        int lastLevel = settings.getLastAlertLevel();
        long lastAt = settings.getLastAlertAt();
        if (lastLevel == hit && (now - lastAt) < COOLDOWN_MS) {
            return;
        }
        // Only re-alert a higher threshold after cooldown if we already alerted a lower one.
        if (lastLevel >= 0 && hit > lastLevel && (now - lastAt) < COOLDOWN_MS) {
            return;
        }

        settings.markAlert(hit, now);
        showLocalNotification(levelPercent, hit);
        maybeEmail(levelPercent, hit);
    }

    private void showLocalNotification(int level, int threshold) {
        Intent open = new Intent(appContext, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                appContext,
                1,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, PowerWatchApp.CHANNEL_ALERT)
                .setSmallIcon(R.drawable.ic_battery)
                .setContentTitle(appContext.getString(R.string.alert_title))
                .setContentText(appContext.getString(R.string.alert_body, level, threshold))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        try {
            NotificationManagerCompat.from(appContext).notify(2000 + threshold, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission missing", e);
        }
    }

    private void maybeEmail(int level, int threshold) {
        if (!settings.isEmailEnabled() || !settings.isEmailConfigured()) {
            return;
        }
        String subject = "Musifone: battery " + level + "% (≤" + threshold + "%)";
        String body = "The Galaxy phone battery is at " + level
                + "% and crossed your " + threshold + "% alert.\n\n"
                + "Open Musifone Power on that phone when you can recharge it.";
        emailIo.execute(() -> {
            try {
                EmailSender.send(settings, subject, body);
            } catch (Exception e) {
                Log.e(TAG, "Email alert failed", e);
            }
        });
    }
}
