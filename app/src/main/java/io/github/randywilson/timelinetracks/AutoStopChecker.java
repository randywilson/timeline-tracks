package io.github.randywilson.timelinetracks;

import android.location.Location;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayDeque;

/**
 * Tracks recent GPS fixes and fires a callback when the device appears stationary.
 * Stationary is defined as: every fix received in the last {@code maxAgeMillis} (per each
 * fix's own {@link Location#getTime()}) is within {@code radiusMeters} of the newest fix,
 * and that window actually spans at least {@code maxAgeMillis}.
 */
class AutoStopChecker {

    private final long maxAgeMillis;
    private final float radiusMeters;
    private final Runnable onStop;
    private final ArrayDeque<Location> recentLocations = new ArrayDeque<>();

    AutoStopChecker(long maxAgeMillis, float radiusMeters, Runnable onStop) {
        this.maxAgeMillis = maxAgeMillis;
        this.radiusMeters = radiusMeters;
        this.onStop = onStop;
    }

    /** Call once per location fix received. */
    void onLocationReceived(Location location) {
        // Capture the true oldest fix before pruning — pruning may remove it this same
        // call, but it's still what tells us whether the window has actually elapsed.
        Location oldest = recentLocations.peekFirst();
        boolean windowElapsed = oldest != null
                && location.getTime() - oldest.getTime() >= maxAgeMillis;

        // Drop fixes older than the window, but always keep at least one to compare against
        // (needed when the sampling interval itself exceeds maxAgeMillis).
        while (recentLocations.size() > 1
                && location.getTime() - recentLocations.peekFirst().getTime() > maxAgeMillis) {
            recentLocations.removeFirst();
        }

        if (windowElapsed) {
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
    }

    @VisibleForTesting
    int getRecentCount() {
        return recentLocations.size();
    }
}
