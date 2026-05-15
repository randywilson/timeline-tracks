package io.github.randywilson.timelinetracks;

import android.location.Location;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;

/**
 * Tracks recent GPS fixes and fires a callback when the device appears stationary.
 * Stationary is defined as: the newest fix is within {@code radiusMeters} of every fix
 * in the rolling window of size {@code maxRecent} (i.e. maxRecent + 1 consecutive nearby fixes).
 */
class AutoStopChecker {

    private final int maxRecent;
    private final float radiusMeters;
    private final Runnable onStop;
    private final ArrayDeque<Location> recentLocations = new ArrayDeque<>();

    AutoStopChecker(int maxRecent, float radiusMeters, Runnable onStop) {
        this.maxRecent = maxRecent;
        this.radiusMeters = radiusMeters;
        this.onStop = onStop;
    }

    /** Call once per location fix received. */
    void onLocationReceived(Location location) {
        if (recentLocations.size() == maxRecent) {
            boolean stationary = true;
            for (Location recent : recentLocations) {
                if (location.distanceTo(recent) > radiusMeters) {
                    stationary = false;
                    break;
                }
            }
            if (stationary) {
                onStop.run();
            }
        }
        recentLocations.addLast(location);
        if (recentLocations.size() > maxRecent) {
            recentLocations.removeFirst();
        }
    }

    @VisibleForTesting
    int getRecentCount() {
        return recentLocations.size();
    }
}
