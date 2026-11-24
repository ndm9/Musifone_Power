package com.musifone.powerwatch.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsStore {
    private static final String PREFS = "powerwatch_settings";
    private static final String KEY_THRESHOLDS = "thresholds";
    private static final String KEY_WAKE_START = "wake_start";
    private static final String KEY_WAKE_END = "wake_end";
    private static final String KEY_MONITORING = "monitoring";
    private static final String KEY_EMAIL_ENABLED = "email_enabled";
    private static final String KEY_EMAIL_TO = "email_to";
    private static final String KEY_SMTP_HOST = "smtp_host";
    private static final String KEY_SMTP_PORT = "smtp_port";
    private static final String KEY_SMTP_USER = "smtp_user";
    private static final String KEY_SMTP_PASS = "smtp_pass";
    private static final String KEY_SMTP_FROM = "smtp_from";
    private static final String KEY_FIRED_THRESHOLDS = "fired_thresholds";
    private static final String KEY_EMAIL_SENT_CYCLE = "email_sent_cycle";
    private static final String KEY_LAST_RECORDED_LEVEL = "last_recorded_level";
    /** Legacy keys cleared on reset for upgrades from the cooldown-based alerter. */
    private static final String KEY_LAST_ALERT_LEVEL = "last_alert_level";
    private static final String KEY_LAST_ALERT_AT = "last_alert_at";

    private final SharedPreferences prefs;

    public SettingsStore(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Integer> getThresholds() {
        String raw = prefs.getString(KEY_THRESHOLDS, "40,25,15");
        return parseThresholds(raw);
    }

    public String getThresholdsRaw() {
        return prefs.getString(KEY_THRESHOLDS, "40,25,15");
    }

    public void setThresholdsRaw(String raw) {
        List<Integer> parsed = parseThresholds(raw);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parsed.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parsed.get(i));
        }
        prefs.edit().putString(KEY_THRESHOLDS, sb.toString()).apply();
    }

    public static List<Integer> parseThresholds(String raw) {
        Set<Integer> set = new LinkedHashSet<>();
        if (raw == null) {
            return Collections.singletonList(20);
        }
        for (String part : raw.split("[,\\s]+")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                int v = Integer.parseInt(part.trim());
                if (v >= 1 && v <= 99) {
                    set.add(v);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (set.isEmpty()) {
            set.add(20);
        }
        List<Integer> list = new ArrayList<>(set);
        Collections.sort(list, Collections.reverseOrder());
        return list;
    }

    public int getWakeStartHour() {
        return prefs.getInt(KEY_WAKE_START, 7);
    }

    public void setWakeStartHour(int hour) {
        prefs.edit().putInt(KEY_WAKE_START, clampHour(hour)).apply();
    }

    public int getWakeEndHour() {
        return prefs.getInt(KEY_WAKE_END, 23);
    }

    public void setWakeEndHour(int hour) {
        prefs.edit().putInt(KEY_WAKE_END, clampHour(hour)).apply();
    }

    public boolean isMonitoringEnabled() {
        return prefs.getBoolean(KEY_MONITORING, false);
    }

    public void setMonitoringEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MONITORING, enabled).apply();
    }

    public boolean isEmailEnabled() {
        return prefs.getBoolean(KEY_EMAIL_ENABLED, false);
    }

    public void setEmailEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_EMAIL_ENABLED, enabled).apply();
    }

    public String getEmailTo() {
        return prefs.getString(KEY_EMAIL_TO, "");
    }

    public void setEmailTo(String value) {
        prefs.edit().putString(KEY_EMAIL_TO, value.trim()).apply();
    }

    public String getSmtpHost() {
        return prefs.getString(KEY_SMTP_HOST, "smtp.gmail.com");
    }

    public void setSmtpHost(String value) {
        prefs.edit().putString(KEY_SMTP_HOST, value.trim()).apply();
    }

    public int getSmtpPort() {
        return prefs.getInt(KEY_SMTP_PORT, 587);
    }

    public void setSmtpPort(int port) {
        prefs.edit().putInt(KEY_SMTP_PORT, port).apply();
    }

    public String getSmtpUser() {
        return prefs.getString(KEY_SMTP_USER, "");
    }

    public void setSmtpUser(String value) {
        prefs.edit().putString(KEY_SMTP_USER, value.trim()).apply();
    }

    public String getSmtpPass() {
        return prefs.getString(KEY_SMTP_PASS, "");
    }

    public void setSmtpPass(String value) {
        prefs.edit().putString(KEY_SMTP_PASS, value).apply();
    }

    public String getSmtpFrom() {
        return prefs.getString(KEY_SMTP_FROM, "");
    }

    public void setSmtpFrom(String value) {
        prefs.edit().putString(KEY_SMTP_FROM, value.trim()).apply();
    }

    public boolean isEmailConfigured() {
        return !getEmailTo().isEmpty()
                && !getSmtpHost().isEmpty()
                && !getSmtpUser().isEmpty()
                && !getSmtpPass().isEmpty();
    }

    public Set<Integer> getFiredThresholds() {
        String raw = prefs.getString(KEY_FIRED_THRESHOLDS, "");
        Set<Integer> set = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return set;
        }
        for (String part : raw.split(",")) {
            if (part.isEmpty()) {
                continue;
            }
            try {
                set.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return set;
    }

    public boolean hasFiredThreshold(int threshold) {
        return getFiredThresholds().contains(threshold);
    }

    public void markThresholdFired(int threshold) {
        Set<Integer> set = getFiredThresholds();
        if (!set.add(threshold)) {
            return;
        }
        prefs.edit().putString(KEY_FIRED_THRESHOLDS, joinInts(set)).apply();
    }

    public boolean isEmailSentThisCycle() {
        return prefs.getBoolean(KEY_EMAIL_SENT_CYCLE, false);
    }

    public void setEmailSentThisCycle(boolean sent) {
        prefs.edit().putBoolean(KEY_EMAIL_SENT_CYCLE, sent).apply();
    }

    /** Clears per-discharge alert state when the phone is plugged in. */
    public void clearAlertMarker() {
        prefs.edit()
                .putString(KEY_FIRED_THRESHOLDS, "")
                .putBoolean(KEY_EMAIL_SENT_CYCLE, false)
                .remove(KEY_LAST_ALERT_LEVEL)
                .remove(KEY_LAST_ALERT_AT)
                .apply();
    }

    private static String joinInts(Set<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (Integer value : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    public int getLastRecordedLevel() {
        return prefs.getInt(KEY_LAST_RECORDED_LEVEL, -1);
    }

    public void setLastRecordedLevel(int level) {
        prefs.edit().putInt(KEY_LAST_RECORDED_LEVEL, level).apply();
    }

    public String describeWakeWindow() {
        return String.format(Locale.US, "%02d:00–%02d:00", getWakeStartHour(), getWakeEndHour());
    }

    private static int clampHour(int hour) {
        if (hour < 0) {
            return 0;
        }
        if (hour > 23) {
            return 23;
        }
        return hour;
    }
}
