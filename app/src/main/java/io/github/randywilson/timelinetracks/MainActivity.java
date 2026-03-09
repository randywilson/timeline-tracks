package io.github.randywilson.timelinetracks;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 1;

    private boolean pendingStart = false;

    private TextView permissionWarning;
    private Button givePermissionButton;
    private EditText intervalField;
    private CheckBox autoStopCheckbox;
    private Button startStopButton;

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);

        permissionWarning = findViewById(R.id.permission_warning);
        givePermissionButton = findViewById(R.id.give_permission_button);
        intervalField = findViewById(R.id.interval_field);
        autoStopCheckbox = findViewById(R.id.auto_stop_checkbox);
        startStopButton = findViewById(R.id.start_stop_button);
        TextView howItWorksLink = findViewById(R.id.how_it_works_link);

        // Load saved settings
        intervalField.setText(String.valueOf(prefs.getIntervalSeconds()));
        autoStopCheckbox.setChecked(prefs.getAutoStop());

        givePermissionButton.setOnClickListener(v -> openAppSettings());

        startStopButton.setOnClickListener(v -> {
            if (prefs.isRunning()) {
                stopTracking();
            } else {
                startTracking();
            }
        });

        howItWorksLink.setOnClickListener(v -> showHowItWorksDialog());

        // Request battery optimization exemption once (prompts only if not already exempt)
        requestBatteryOptimizationExemption();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always re-check permissions (user may have granted/revoked them in Settings)
        checkPermissions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
    }

    private void checkPermissions() {
        boolean hasFine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean hasBackground = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasBackground = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        boolean hasPermissions = hasFine && hasBackground;

        if (!hasPermissions) {
            permissionWarning.setVisibility(View.VISIBLE);
            givePermissionButton.setVisibility(View.VISIBLE);
            startStopButton.setEnabled(false);
        } else {
            permissionWarning.setVisibility(View.GONE);
            givePermissionButton.setVisibility(View.GONE);
            startStopButton.setEnabled(true);
            // Sync button label and color with actual service state
            updateStartStopButton(prefs.isRunning());
        }
    }

    private void openAppSettings() {
        // Send the user to the app's permission settings page
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void requestBatteryOptimizationExemption() {
        if (prefs.hasBatteryOptBeenAsked()) return;
        prefs.setBatteryOptAsked();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                // Some devices or launchers don't support this intent
            }
        }
    }

    private void startTracking() {
        // On Android 13+, ask for notification permission right before starting —
        // the reason is obvious at this moment ("you tapped Start").
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return;
        }
        doStartTracking();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS && pendingStart) {
            pendingStart = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                doStartTracking();
            } else {
                showNotificationPermissionDeniedDialog();
            }
        }
    }

    private void showNotificationPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_title)
                .setMessage(R.string.notification_permission_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void doStartTracking() {
        saveSettings();
        Intent intent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        prefs.setRunning(true);
        updateStartStopButton(true);
    }

    private void stopTracking() {
        stopService(new Intent(this, LocationService.class));
        prefs.setRunning(false);
        updateStartStopButton(false);
    }

    private void updateStartStopButton(boolean running) {
        startStopButton.setText(running ? R.string.stop : R.string.start);
        int colorRes = running ? R.color.button_stop : R.color.button_start;
        startStopButton.setBackgroundTintList(
                ColorStateList.valueOf(ContextCompat.getColor(this, colorRes)));
    }

    private void saveSettings() {
        String text = intervalField.getText().toString().trim();
        if (!text.isEmpty()) {
            try {
                int interval = Integer.parseInt(text);
                if (interval > 0) {
                    prefs.setIntervalSeconds(interval);
                }
            } catch (NumberFormatException e) {
                // Ignore invalid input
            }
        }
        prefs.setAutoStop(autoStopCheckbox.isChecked());
    }

    private void showHowItWorksDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.how_it_works_title)
                .setMessage(R.string.how_it_works_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
