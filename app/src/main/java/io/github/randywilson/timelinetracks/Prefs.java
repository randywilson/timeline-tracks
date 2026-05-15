package io.github.randywilson.timelinetracks;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String PREF_FILE = "prefs";
    private static final String KEY_INTERVAL = "interval_seconds";
    private static final String KEY_AUTO_STOP = "auto_stop";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_BATTERY_OPT_ASKED = "battery_opt_asked";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_STOP_TIME = "stop_time";
    private static final String KEY_LOCATION_COUNT = "location_count";
    private static final String KEY_STOP_REASON = "stop_reason";

    public static final String STOP_REASON_USER = "stopped";
    public static final String STOP_REASON_AUTO = "auto-stopped";

    private static final int DEFAULT_INTERVAL = 110;
    private static final boolean DEFAULT_AUTO_STOP = true;

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public int getIntervalSeconds() {
        return prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL);
    }

    public void setIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_INTERVAL, seconds).apply();
    }

    public boolean getAutoStop() {
        return prefs.getBoolean(KEY_AUTO_STOP, DEFAULT_AUTO_STOP);
    }

    public void setAutoStop(boolean autoStop) {
        prefs.edit().putBoolean(KEY_AUTO_STOP, autoStop).apply();
    }

    public boolean isRunning() {
        return prefs.getBoolean(KEY_RUNNING, false);
    }

    public void setRunning(boolean running) {
        prefs.edit().putBoolean(KEY_RUNNING, running).apply();
    }

    public boolean hasBatteryOptBeenAsked() {
        return prefs.getBoolean(KEY_BATTERY_OPT_ASKED, false);
    }

    public void setBatteryOptAsked() {
        prefs.edit().putBoolean(KEY_BATTERY_OPT_ASKED, true).apply();
    }

    public long getStartTime() {
        return prefs.getLong(KEY_START_TIME, 0L);
    }

    public void setStartTime(long millis) {
        prefs.edit().putLong(KEY_START_TIME, millis).apply();
    }

    public long getStopTime() {
        return prefs.getLong(KEY_STOP_TIME, 0L);
    }

    public void setStopTime(long millis) {
        prefs.edit().putLong(KEY_STOP_TIME, millis).apply();
    }

    public String getStopReason() {
        return prefs.getString(KEY_STOP_REASON, STOP_REASON_USER);
    }

    public void setStopReason(String reason) {
        prefs.edit().putString(KEY_STOP_REASON, reason).apply();
    }

    public int getLocationCount() {
        return prefs.getInt(KEY_LOCATION_COUNT, 0);
    }

    public void setLocationCount(int count) {
        prefs.edit().putInt(KEY_LOCATION_COUNT, count).apply();
    }

    public void incrementLocationCount() {
        prefs.edit().putInt(KEY_LOCATION_COUNT, getLocationCount() + 1).apply();
    }
}
