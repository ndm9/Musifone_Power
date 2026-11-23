package com.musifone.powerwatch.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.musifone.powerwatch.PowerWatchApp;
import com.musifone.powerwatch.R;
import com.musifone.powerwatch.alert.EmailSender;
import com.musifone.powerwatch.data.SettingsStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private SettingsStore settings;
    private ExecutorService io;

    private EditText thresholdsInput;
    private EditText wakeStartInput;
    private EditText wakeEndInput;
    private SwitchMaterial emailEnable;
    private EditText emailToInput;
    private EditText smtpHostInput;
    private EditText smtpPortInput;
    private EditText smtpUserInput;
    private EditText smtpPassInput;
    private EditText smtpFromInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        settings = ((PowerWatchApp) getApplication()).getSettings();
        io = Executors.newSingleThreadExecutor();

        thresholdsInput = findViewById(R.id.thresholdsInput);
        wakeStartInput = findViewById(R.id.wakeStartInput);
        wakeEndInput = findViewById(R.id.wakeEndInput);
        emailEnable = findViewById(R.id.emailEnable);
        emailToInput = findViewById(R.id.emailToInput);
        smtpHostInput = findViewById(R.id.smtpHostInput);
        smtpPortInput = findViewById(R.id.smtpPortInput);
        smtpUserInput = findViewById(R.id.smtpUserInput);
        smtpPassInput = findViewById(R.id.smtpPassInput);
        smtpFromInput = findViewById(R.id.smtpFromInput);

        load();

        Button save = findViewById(R.id.saveButton);
        save.setOnClickListener(v -> {
            persist();
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        });

        Button test = findViewById(R.id.testEmailButton);
        test.setOnClickListener(v -> {
            persist();
            sendTestEmail();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        if (io != null) {
            io.shutdownNow();
        }
        super.onDestroy();
    }

    private void load() {
        thresholdsInput.setText(settings.getThresholdsRaw());
        wakeStartInput.setText(String.valueOf(settings.getWakeStartHour()));
        wakeEndInput.setText(String.valueOf(settings.getWakeEndHour()));
        emailEnable.setChecked(settings.isEmailEnabled());
        emailToInput.setText(settings.getEmailTo());
        smtpHostInput.setText(settings.getSmtpHost());
        smtpPortInput.setText(String.valueOf(settings.getSmtpPort()));
        smtpUserInput.setText(settings.getSmtpUser());
        smtpPassInput.setText(settings.getSmtpPass());
        smtpFromInput.setText(settings.getSmtpFrom());
    }

    private void persist() {
        settings.setThresholdsRaw(thresholdsInput.getText().toString());
        settings.setWakeStartHour(parseInt(wakeStartInput.getText().toString(), 7));
        settings.setWakeEndHour(parseInt(wakeEndInput.getText().toString(), 23));
        settings.setEmailEnabled(emailEnable.isChecked());
        settings.setEmailTo(emailToInput.getText().toString());
        settings.setSmtpHost(smtpHostInput.getText().toString());
        settings.setSmtpPort(parseInt(smtpPortInput.getText().toString(), 587));
        settings.setSmtpUser(smtpUserInput.getText().toString());
        settings.setSmtpPass(smtpPassInput.getText().toString());
        settings.setSmtpFrom(smtpFromInput.getText().toString());
    }

    private void sendTestEmail() {
        if (!settings.isEmailConfigured()) {
            Toast.makeText(this, R.string.email_not_configured, Toast.LENGTH_LONG).show();
            return;
        }
        io.execute(() -> {
            try {
                EmailSender.send(
                        settings,
                        "Musifone Power test",
                        "If you see this on your main phone, email alerts are working."
                );
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.email_sent, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() ->
                        Toast.makeText(this, getString(R.string.email_failed, msg), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
