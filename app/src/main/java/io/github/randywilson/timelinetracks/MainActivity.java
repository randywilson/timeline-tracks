package io.github.randywilson.timelinetracks;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 1;

    private boolean pendingStart = false;

    private TextView permissionWarning;
    private Button givePermissionButton;
    private final RadioButton[] intervalRadios = new RadioButton[3];
    private final EditText[] intervalMinFields = new EditText[3];
    private final EditText[] intervalSecFields = new EditText[3];
    private final TextView[] intervalMinLabels = new TextView[3];
    private final TextView[] intervalSecLabels = new TextView[3];
    private int selectedInterval;
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
        autoStopCheckbox = findViewById(R.id.auto_stop_checkbox);
        startStopButton = findViewById(R.id.start_stop_button);
        statsView = findViewById(R.id.stats_view);
        TextView aboutLink = findViewById(R.id.about_link);
        TextView howItWorksLink = findViewById(R.id.how_it_works_link);

        int[] radioIds = {R.id.interval_radio_0, R.id.interval_radio_1, R.id.interval_radio_2};
        int[] minIds = {R.id.interval_min_0, R.id.interval_min_1, R.id.interval_min_2};
        int[] secIds = {R.id.interval_sec_0, R.id.interval_sec_1, R.id.interval_sec_2};
        int[] minLabelIds = {R.id.interval_min_label_0, R.id.interval_min_label_1, R.id.interval_min_label_2};
        int[] secLabelIds = {R.id.interval_sec_label_0, R.id.interval_sec_label_1, R.id.interval_sec_label_2};

        for (int i = 0; i < 3; i++) {
            intervalRadios[i] = findViewById(radioIds[i]);
            intervalMinFields[i] = findViewById(minIds[i]);
            intervalSecFields[i] = findViewById(secIds[i]);
            intervalMinLabels[i] = findViewById(minLabelIds[i]);
            intervalSecLabels[i] = findViewById(secLabelIds[i]);
        }

        // Load saved interval settings
        selectedInterval = prefs.getSelectedInterval();
        for (int i = 0; i < 3; i++) {
            intervalMinFields[i].setText(String.valueOf(prefs.getSlotMinutes(i)));
            intervalSecFields[i].setText(String.valueOf(prefs.getSlotSeconds(i)));
            intervalRadios[i].setChecked(i == selectedInterval);
        }
        updateIntervalRowColors();

        View.OnClickListener radioClickListener = v -> {
            for (int i = 0; i < 3; i++) {
                if (intervalRadios[i] == v) {
                    selectedInterval = i;
                    break;
                }
            }
            for (int i = 0; i < 3; i++) {
                intervalRadios[i].setChecked(i == selectedInterval);
            }
            updateIntervalRowColors();
        };
        for (RadioButton radio : intervalRadios) {
            radio.setOnClickListener(radioClickListener);
        }

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
        updateStatsView(prefs.isRunning());
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

    private void updateIntervalRowColors() {
        int activeColor = ContextCompat.getColor(this, R.color.near_black);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_inactive);
        for (int i = 0; i < 3; i++) {
            int color = (i == selectedInterval) ? activeColor : inactiveColor;
            intervalMinFields[i].setTextColor(color);
            intervalMinLabels[i].setTextColor(color);
            intervalSecFields[i].setTextColor(color);
            intervalSecLabels[i].setTextColor(color);
        }
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
        // On API 29+, land directly on the app's permissions list (one tap from Location).
        // Fall back to the full app settings page if the intent isn't supported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Intent intent = new Intent("android.intent.action.MANAGE_APP_PERMISSIONS");
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, getPackageName());
                startActivity(intent);
                return;
            } catch (Exception e) {
                // fall through
            }
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private void requestLocationPermissions() {
        // Always open the app's permissions screen directly. The user taps
        // Location → Allow all the time, which grants fine + background in one step.
        // This avoids the clunky two-dialog flow that requestPermissions() produces.
        openAppSettings();
    }

    // Background GPS tracking while the app isn't in the foreground is this app's entire
    // purpose, which is one of Google Play's accepted exceptions to the battery-optimization
    // policy (see developer.android.com/training/monitoring-device-state/doze-standby).
    @SuppressLint("BatteryLife")
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
        for (int i = 0; i < 3; i++) {
            int min = parseFieldOrZero(intervalMinFields[i]);
            int sec = parseFieldOrZero(intervalSecFields[i]);
            prefs.setSlot(i, min, sec);
            // Reflect normalized values (e.g. 2m 90s → 3m 30s) back to the fields
            intervalMinFields[i].setText(String.valueOf(prefs.getSlotMinutes(i)));
            intervalSecFields[i].setText(String.valueOf(prefs.getSlotSeconds(i)));
        }
        prefs.setSelectedInterval(selectedInterval);
        prefs.setAutoStop(autoStopCheckbox.isChecked());
    }

    private int parseFieldOrZero(EditText field) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
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
            elapsed = getString(R.string.elapsed_hm, h, m);
        } else if (m > 0) {
            elapsed = getString(R.string.elapsed_ms, m, s);
        } else {
            elapsed = getString(R.string.elapsed_s, s);
        }

        String points = getResources().getQuantityString(R.plurals.points, count, count);

        String status;
        if (prefs.isRunning()) {
            status = getString(R.string.status_running);
        } else if (Prefs.STOP_REASON_AUTO.equals(prefs.getStopReason())) {
            status = getString(R.string.status_auto_stopped);
        } else {
            status = getString(R.string.status_stopped);
        }

        statsView.setText(getString(R.string.stats_line, elapsed, points, status));
    }

    private void showHowItWorksDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.how_it_works_title)
                .setMessage(R.string.how_it_works_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }
}
