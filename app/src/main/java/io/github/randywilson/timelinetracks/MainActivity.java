package io.github.randywilson.timelinetracks;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 1;
    private static final int REQUEST_FINE_LOCATION = 2;
    private static final int REQUEST_BACKGROUND_LOCATION = 3;

    private boolean pendingStart = false;

    private TextView permissionWarning;
    private Button givePermissionButton;
    private EditText intervalField;
    private CheckBox autoStopCheckbox;
    private Button startStopButton;
    private TextView statsView;

    private final Handler statsHandler = new Handler(Looper.getMainLooper());
    private final Runnable statsTicker = new Runnable() {
        @Override
        public void run() {
            if (!prefs.isRunning()) {
                // Service stopped on its own (auto-stop) — sync the UI
                updateStartStopButton(false);
                updateStatsView();
                return;
            }
            updateStatsView();
            statsHandler.postDelayed(this, 1000);
        }
    };

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);

        // Push title bar content below the status bar on edge-to-edge displays (API 35+)
        LinearLayout titleBar = findViewById(R.id.title_bar);
        ViewCompat.setOnApplyWindowInsetsListener(titleBar, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(), statusBarHeight, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        permissionWarning = findViewById(R.id.permission_warning);
        givePermissionButton = findViewById(R.id.give_permission_button);
        intervalField = findViewById(R.id.interval_field);
        autoStopCheckbox = findViewById(R.id.auto_stop_checkbox);
        startStopButton = findViewById(R.id.start_stop_button);
        statsView = findViewById(R.id.stats_view);
        TextView aboutLink = findViewById(R.id.about_link);
        TextView howItWorksLink = findViewById(R.id.how_it_works_link);

        // Load saved settings
        intervalField.setText(String.valueOf(prefs.getIntervalSeconds()));
        autoStopCheckbox.setChecked(prefs.getAutoStop());

        View intervalInfoButton = findViewById(R.id.interval_info_button);
        intervalInfoButton.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.interval_info_message)
                        .setPositiveButton(R.string.ok, null)
                        .show());

        ImageView autoStopInfo = findViewById(R.id.auto_stop_info);
        autoStopInfo.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage(R.string.auto_stop_info_message)
                        .setPositiveButton(R.string.ok, null)
                        .show());

        givePermissionButton.setOnClickListener(v -> requestLocationPermissions());

        startStopButton.setOnClickListener(v -> {
            if (prefs.isRunning()) {
                stopTracking();
            } else {
                startTracking();
            }
        });

        aboutLink.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle(R.string.about_title)
                        .setMessage(R.string.about_message)
                        .setPositiveButton(R.string.ok, null)
                        .show());

        howItWorksLink.setOnClickListener(v -> showHowItWorksDialog());

        // Request battery optimization exemption once (prompts only if not already exempt)
        requestBatteryOptimizationExemption();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Always re-check permissions (user may have granted/revoked them in Settings)
        checkPermissions();
        updateStatsView();
        if (prefs.isRunning()) {
            statsHandler.postDelayed(statsTicker, 1000);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettings();
        statsHandler.removeCallbacks(statsTicker);
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
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void requestLocationPermissions() {
        boolean hasFine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!hasFine) {
            // Step 1: request fine location — shows a system dialog on all API levels.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Step 2 (API 29+): request background location.
            // On Android 11+ the OS cannot show a dialog for background location —
            // it redirects the user directly to the location permission settings screen.
            // On Android 10 a dialog with "Allow all the time" is shown instead.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_BACKGROUND_LOCATION);
        }
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
        switch (requestCode) {
            case REQUEST_NOTIFICATIONS:
                if (pendingStart) {
                    pendingStart = false;
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        doStartTracking();
                    } else {
                        showNotificationPermissionDeniedDialog();
                    }
                }
                break;

            case REQUEST_FINE_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Fine location granted — continue to request background if needed.
                    requestLocationPermissions();
                } else if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // Permanently denied ("Don't ask again") — only app settings can unblock.
                    openAppSettings();
                }
                checkPermissions();
                break;

            case REQUEST_BACKGROUND_LOCATION:
                // On Android 11+: fires when user returns from the location settings screen.
                // On Android 10: fires after the inline dialog.
                // If permanently denied on Android 10, fall back to app settings.
                if (grantResults.length > 0
                        && grantResults[0] != PackageManager.PERMISSION_GRANTED
                        && Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                        && !ActivityCompat.shouldShowRequestPermissionRationale(
                                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    openAppSettings();
                }
                checkPermissions();
                break;
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
        prefs.setStartTime(System.currentTimeMillis());
        prefs.setLocationCount(0);
        Intent intent = new Intent(this, LocationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        prefs.setRunning(true);
        updateStartStopButton(true);
        updateStatsView(true);
        statsHandler.postDelayed(statsTicker, 1000);
    }

    private void stopTracking() {
        statsHandler.removeCallbacks(statsTicker);
        prefs.setStopTime(System.currentTimeMillis());
        prefs.setStopReason(Prefs.STOP_REASON_USER);
        stopService(new Intent(this, LocationService.class));
        prefs.setRunning(false);
        updateStartStopButton(false);
        updateStatsView();
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

    private void updateStatsView() {
        updateStatsView(false);
    }

    private void updateStatsView(boolean forceShow) {
        if (forceShow) statsView.setVisibility(View.VISIBLE);
        if (statsView.getVisibility() != View.VISIBLE) return;

        int count = prefs.getLocationCount();
        long endMs = prefs.isRunning() ? System.currentTimeMillis() : prefs.getStopTime();
        long totalSecs = (endMs - prefs.getStartTime()) / 1000;
        long h = totalSecs / 3600;
        long m = (totalSecs % 3600) / 60;
        long s = totalSecs % 60;

        String elapsed;
        if (h > 0) {
            elapsed = h + "h " + m + "m";
        } else if (m > 0) {
            elapsed = m + "m " + s + "s";
        } else {
            elapsed = s + "s";
        }

        String points = count == 1 ? "1 point" : count + " points";
        String status = prefs.isRunning() ? "running" : prefs.getStopReason();
        statsView.setText(elapsed + " · " + points + " · " + status);
    }

    private void showHowItWorksDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.how_it_works_title)
                .setMessage(R.string.how_it_works_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
