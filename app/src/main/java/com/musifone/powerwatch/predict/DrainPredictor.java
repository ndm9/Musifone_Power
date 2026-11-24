package com.musifone.powerwatch.predict;

import com.musifone.powerwatch.data.BatterySample;
import com.musifone.powerwatch.data.SettingsStore;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class DrainPredictor {
    public enum Kind {
        UNKNOWN,
        CHARGING,
        ALREADY_LOW,
        OK,
        CHARGE_BY
    }

    public static final class Result {
        public final Kind kind;
        public final String message;
        public final double drainPercentPerHour;
        public final Long chargeByEpochMs;

        public Result(Kind kind, String message, double drainPercentPerHour, Long chargeByEpochMs) {
            this.kind = kind;
            this.message = message;
            this.drainPercentPerHour = drainPercentPerHour;
            this.chargeByEpochMs = chargeByEpochMs;
        }
    }

    private DrainPredictor() {
    }

    public static Result predict(
            List<BatterySample> recentSamples,
            int currentLevel,
            boolean charging,
            SettingsStore settings,
            String unknownMsg,
            String chargingMsg,
            String okMsg,
            String alreadyLowMsg,
            String chargeByFmt
    ) {
        if (charging) {
            return new Result(Kind.CHARGING, chargingMsg, 0, null);
        }

        List<Integer> thresholds = settings.getThresholds();
        int target = thresholds.isEmpty() ? 20 : thresholds.get(thresholds.size() - 1);
        // Prefer the highest threshold still above current level for "time to charge" advice.
        for (Integer t : thresholds) {
            if (currentLevel > t) {
                target = t;
                break;
            }
        }

        if (currentLevel <= target) {
            return new Result(Kind.ALREADY_LOW, alreadyLowMsg, estimateDrainRate(recentSamples), null);
        }

        double drain = estimateDrainRate(recentSamples);
        if (drain <= 0.05) {
            return new Result(Kind.UNKNOWN, unknownMsg, drain, null);
        }

        double hoursToTarget = (currentLevel - target) / drain;
        long etaMs = System.currentTimeMillis() + (long) (hoursToTarget * 3_600_000L);

        if (!willHitDuringWakeHours(etaMs, settings) && isWithinWakeHours(System.currentTimeMillis(), settings)) {
            // Hits after wake window — still fine for tonight.
            return new Result(Kind.OK, okMsg, drain, etaMs);
        }

        long adviseAt = clampToWakeWindow(etaMs, settings);
        String timeLabel = formatClock(adviseAt);
        String message = String.format(Locale.getDefault(), chargeByFmt, timeLabel);
        return new Result(Kind.CHARGE_BY, message, drain, adviseAt);
    }

    /**
     * Weighted average of contiguous discharging segments over the last samples.
     * Ignores charging gaps so overnight plugs do not distort the rate.
     */
    public static double estimateDrainRate(List<BatterySample> samples) {
        if (samples == null || samples.size() < 2) {
            return 0;
        }

        double weightedRateSum = 0;
        double weightSum = 0;

        for (int i = 1; i < samples.size(); i++) {
            BatterySample prev = samples.get(i - 1);
            BatterySample cur = samples.get(i);
            if (prev.charging || cur.charging) {
                continue;
            }
            long dt = cur.timestampMs - prev.timestampMs;
            if (dt < TimeUnit.MINUTES.toMillis(2) || dt > TimeUnit.HOURS.toMillis(8)) {
                continue;
            }
            int drop = prev.levelPercent - cur.levelPercent;
            if (drop <= 0) {
                continue;
            }
            double hours = dt / 3_600_000.0;
            double rate = drop / hours;
            // Prefer fresher segments.
            double ageHours = Math.max(0.5, (System.currentTimeMillis() - cur.timestampMs) / 3_600_000.0);
            double weight = hours / ageHours;
            weightedRateSum += rate * weight;
            weightSum += weight;
        }

        if (weightSum <= 0) {
            return 0;
        }
        return weightedRateSum / weightSum;
    }

    public static boolean isWithinWakeHours(long epochMs, SettingsStore settings) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMs);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int start = settings.getWakeStartHour();
        int end = settings.getWakeEndHour();
        if (start == end) {
            return true;
        }
        if (start < end) {
            return hour >= start && hour < end;
        }
        // Overnight wake window (e.g. 22–6).
        return hour >= start || hour < end;
    }

    static boolean willHitDuringWakeHours(long etaMs, SettingsStore settings) {
        return isWithinWakeHours(etaMs, settings);
    }

    static long clampToWakeWindow(long etaMs, SettingsStore settings) {
        if (isWithinWakeHours(etaMs, settings)) {
            return etaMs;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(etaMs);
        int start = settings.getWakeStartHour();
        int end = settings.getWakeEndHour();

        if (start < end) {
            // Before wake: move to wake start same day. After wake: next day's wake start.
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour < start) {
                setHour(cal, start);
            } else {
                cal.add(Calendar.DAY_OF_YEAR, 1);
                setHour(cal, start);
            }
        } else {
            // Overnight window: if outside, next wake start today/tomorrow.
            setHour(cal, start);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return cal.getTimeInMillis();
    }

    private static void setHour(Calendar cal, int hour) {
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private static String formatClock(long epochMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(epochMs);
        return String.format(Locale.getDefault(), "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE));
    }
}
