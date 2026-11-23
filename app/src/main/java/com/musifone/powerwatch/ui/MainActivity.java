package com.musifone.powerwatch.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.musifone.powerwatch.PowerWatchApp;
import com.musifone.powerwatch.R;
import com.musifone.powerwatch.data.AppDatabase;
import com.musifone.powerwatch.data.BatterySample;
import com.musifone.powerwatch.data.SettingsStore;
import com.musifone.powerwatch.monitor.BatteryMonitorService;
import com.musifone.powerwatch.monitor.BatterySnapshot;
import com.musifone.powerwatch.monitor.MonitorHeartbeatWorker;
import com.musifone.powerwatch.predict.DrainPredictor;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_POST_NOTIFICATIONS = 41;

    private SettingsStore settings;
    private AppDatabase database;
    private ExecutorService io;

    private TextView statusLabel;
    private TextView batteryLevel;
    private TextView batteryState;
    private TextView predictionText;
    private TextView drainRateText;
    private TextView thresholdsSummary;
    private Button toggleMonitor;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            int level = intent.getIntExtra(BatteryMonitorService.EXTRA_LEVEL, -1);
            boolean charging = intent.getBooleanExtra(BatteryMonitorService.EXTRA_CHARGING, false);
            if (level >= 0) {
                renderBattery(level, charging);
                refreshPrediction(level, charging);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PowerWatchApp app = (PowerWatchApp) getApplication();
        settings = app.getSettings();
        database = app.getDatabase();
        io = Executors.newSingleThreadExecutor();

        statusLabel = findViewById(R.id.statusLabel);
        batteryLevel = findViewById(R.id.batteryLevel);
        batteryState = findViewById(R.id.batteryState);
        predictionText = findViewById(R.id.predictionText);
        drainRateText = findViewById(R.id.drainRateText);
        thresholdsSummary = findViewById(R.id.thresholdsSummary);
        toggleMonitor = findViewById(R.id.toggleMonitor);

        findViewById(R.id.openSettings).setOnClickListener(
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.openHistory).setOnClickListener(
                v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.batteryOpt).setOnClickListener(v -> requestUnrestrictedBattery());
        toggleMonitor.setOnClickListener(v -> toggleMonitoring());

        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BatteryMonitorService.ACTION_STATUS);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        refreshUi();
    }

    @Override
    protected void onStop() {
        try {
            unregisterReceiver(statusReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (io != null) {
            io.shutdownNow();
        }
        super.onDestroy();
    }

    private void refreshUi() {
        boolean on = settings.isMonitoringEnabled();
        statusLabel.setText(on ? R.string.watching_on : R.string.watching_off);
        toggleMonitor.setText(on ? R.string.btn_stop : R.string.btn_start);
        thresholdsSummary.setText(String.format(
                Locale.getDefault(),
                "Alerts at %s%% · wake %s",
                settings.getThresholdsRaw(),
                settings.describeWakeWindow()
        ));

        BatterySnapshot snap = BatterySnapshot.fromSticky(this);
        renderBattery(snap.levelPercent, snap.charging);
        refreshPrediction(snap.levelPercent, snap.charging);
    }

    private void renderBattery(int level, boolean charging) {
        batteryLevel.setText(level + "%");
        int color;
        if (level <= 15) {
            color = ContextCompat.getColor(this, R.color.danger);
        } else if (level <= 40) {
            color = ContextCompat.getColor(this, R.color.warn);
        } else {
            color = ContextCompat.getColor(this, R.color.ok);
        }
        batteryLevel.setTextColor(color);
        batteryState.setText(getString(charging ? R.string.status_charging : R.string.status_discharging));
    }

    private void refreshPrediction(int level, boolean charging) {
        io.execute(() -> {
            long since = System.currentTimeMillis() - 36L * 60L * 60L * 1000L;
            List<BatterySample> samples = database.batteryDao().samplesSince(since);
            DrainPredictor.Result result = DrainPredictor.predict(
                    samples,
                    level,
                    charging,
                    settings,
                    getString(R.string.prediction_unknown),
                    getString(R.string.prediction_charging),
                    getString(R.string.prediction_ok),
                    getString(R.string.prediction_already_low),
                    getString(R.string.prediction_charge_by)
            );
            runOnUiThread(() -> {
                predictionText.setText(result.message);
                if (result.drainPercentPerHour > 0.05) {
                    drainRateText.setText(getString(
                            R.string.drain_rate_fmt,
                            result.drainPercentPerHour
                    ));
                } else {
                    drainRateText.setText("—");
                }
                int colorRes;
                switch (result.kind) {
                    case ALREADY_LOW:
                    case CHARGE_BY:
                        colorRes = R.color.warn;
                        break;
                    case OK:
                    case CHARGING:
                        colorRes = R.color.ok;
                        break;
                    default:
                        colorRes = R.color.text_primary;
                        break;
                }
                predictionText.setTextColor(ContextCompat.getColor(this, colorRes));
            });
        });
    }

    private void toggleMonitoring() {
        if (settings.isMonitoringEnabled()) {
            settings.setMonitoringEnabled(false);
            BatteryMonitorService.stop(this);
            MonitorHeartbeatWorker.cancel(this);
        } else {
            settings.setMonitoringEnabled(true);
            BatteryMonitorService.start(this);
            MonitorHeartbeatWorker.schedule(this);
            maybePromptBatteryOptimization();
        }
        refreshUi();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
            }
        }
    }

    private void maybePromptBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null) {
            return;
        }
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            requestUnrestrictedBattery();
        }
    }

    private void requestUnrestrictedBattery() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(fallback);
        }
    }
}
