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
import com.musifone.powerwatch.predict.DrainPredictor;
import com.musifone.powerwatch.ui.MainActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlertDispatcher {
    private static final String TAG = "AlertDispatcher";

    private final Context appContext;
    private final SettingsStore settings;
    private final ExecutorService emailIo = Executors.newSingleThreadExecutor();

    public AlertDispatcher(Context context, SettingsStore settings) {
        this.appContext = context.getApplicationContext();
        this.settings = settings;
    }

    /**
     * Alerts once per threshold per discharge cycle (resets when plugged in).
     * Email is sent at most once per cycle, and only during wake hours.
     */
    public void evaluate(int levelPercent) {
        List<Integer> thresholds = settings.getThresholds();
        List<Integer> newlyCrossed = new ArrayList<>();
        for (Integer threshold : thresholds) {
            if (levelPercent <= threshold && !settings.hasFiredThreshold(threshold)) {
                newlyCrossed.add(threshold);
            }
        }

        if (!newlyCrossed.isEmpty()) {
            for (Integer threshold : newlyCrossed) {
                settings.markThresholdFired(threshold);
            }
            // One local notification for the most urgent newly crossed band.
            int hit = Collections.min(newlyCrossed);
            showLocalNotification(levelPercent, hit);
        }

        maybeEmailOnce(levelPercent, thresholds);
    }

    private void maybeEmailOnce(int levelPercent, List<Integer> thresholds) {
        if (!settings.isEmailEnabled() || !settings.isEmailConfigured()) {
            return;
        }
        if (settings.isEmailSentThisCycle()) {
            return;
        }
        if (!DrainPredictor.isWithinWakeHours(System.currentTimeMillis(), settings)) {
            return;
        }

        boolean belowAny = false;
        int nearest = -1;
        for (Integer threshold : thresholds) {
            if (levelPercent <= threshold) {
                belowAny = true;
                if (nearest < 0 || threshold < nearest) {
                    nearest = threshold;
                }
            }
        }
        if (!belowAny) {
            return;
        }

        settings.setEmailSentThisCycle(true);
        String subject = "Musifone: time to charge (" + levelPercent + "%)";
        String body = "Battery is at " + levelPercent
                + "% (at or below your " + nearest + "% alert).\n\n"
                + "This is the only email until you plug the phone in.\n"
                + "Charge it during your wake hours when you can.";
        emailIo.execute(() -> {
            try {
                EmailSender.send(settings, subject, body);
            } catch (Exception e) {
                Log.e(TAG, "Email alert failed", e);
                // Allow a later retry if SMTP failed.
                settings.setEmailSentThisCycle(false);
            }
        });
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
}
