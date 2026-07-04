package io.github.randywilson.timelinetracks;

import android.location.Location;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for AutoStopChecker.
 *
 * Distance reference (at ~37° latitude):
 *   0.01°  ≈ 1,110 m  (used for "moving" steps — well outside 100 m radius)
 *   0.0001° ≈ 11 m    (used for "stationary" steps — well inside 100 m radius)
 *
 * Time reference: fixes carry their own timestamp via {@link Location#setTime}; age is
 * measured between a fix's timestamp and the newest fix's timestamp, not wall-clock time.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AutoStopCheckerTest {

    private static final long MAX_AGE_MILLIS = 10 * 60 * 1000L; // 10 minutes
    private static final float RADIUS_METERS = 100f;
    private static final long MINUTE = 60 * 1000L;
    private static final long BASE_TIME = 1_700_000_000_000L;

    private boolean stopped;
    private AutoStopChecker checker;

    @Before
    public void setUp() {
        stopped = false;
        checker = new AutoStopChecker(MAX_AGE_MILLIS, RADIUS_METERS, () -> stopped = true);
    }

    private static Location loc(double lat, double lon, long timeMillis) {
        Location l = new Location("test");
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setTime(timeMillis);
        return l;
    }

    // ── basic deque behaviour ───────────────────────────────────────────────

    @Test
    public void firstLocation_addedToDeque_noStop() {
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME));
        assertEquals(1, checker.getRecentCount());
        assertFalse(stopped);
    }

    @Test
    public void secondLocationWithinWindow_dequeGrows_noStop() {
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME));
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + 2 * MINUTE));
        assertEquals(2, checker.getRecentCount());
        assertFalse(stopped);
    }

    // ── the core new-behaviour tests: time span, not fix count, gates the stop ─────

    @Test
    public void stationaryFixes_noStopWhileSpanUnderTenMinutes() {
        // Five stationary fixes, 2 minutes apart — a fixed-count deque (old behaviour)
        // would have triggered on the 5th, but only 8 minutes have actually elapsed.
        for (int i = 0; i <= 4; i++) {
            checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + i * 2 * MINUTE));
        }
        assertFalse("should not stop after only 8 minutes stationary", stopped);
    }

    @Test
    public void stationaryFixes_stopsExactlyWhenSpanReachesTenMinutes() {
        for (int i = 0; i <= 4; i++) {
            checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + i * 2 * MINUTE));
        }
        assertFalse(stopped);
        // 6th fix at the 10-minute mark — span from the oldest kept fix now hits 10 minutes.
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + 10 * MINUTE));
        assertTrue("should stop once the stationary span reaches 10 minutes", stopped);
    }

    @Test
    public void spanJustUnderTenMinutes_noStop() {
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME));
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + MAX_AGE_MILLIS - 1));
        assertFalse(stopped);
    }

    @Test
    public void spanExactlyTenMinutes_stops() {
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME));
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + MAX_AGE_MILLIS));
        assertTrue(stopped);
    }

    // ── "keep at least one" floor, for sampling intervals longer than the window ───

    @Test
    public void sparseInterval_keepsSoleOldFix_stopsWhenWithinRadius() {
        // Sampling interval (15 min) exceeds the 10-minute window; pruning must still
        // keep the one prior fix around to compare against, rather than emptying the deque.
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME));
        assertEquals(1, checker.getRecentCount());
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + 15 * MINUTE));
        assertTrue("a single, older-than-window fix that's still in radius should count", stopped);
    }

    @Test
    public void windowSatisfyingAnchorPrunedThisRound_stillStops() {
        // The fix that proves 10 minutes have elapsed (t0) is itself the one that gets
        // pruned in the same call, once its age exceeds the window. The elapsed-window
        // check must be based on t0's age as it was at the *start* of this call, not on
        // whatever fix happens to be left at the front of the deque after pruning.
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME));
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + 5 * MINUTE));
        assertFalse(stopped);

        // 700s (~11.67 min) after t0 — window has elapsed relative to t0, but pruning
        // will drop t0 (700s > 600s old) before the stationary check, leaving only the
        // 5-minute fix in the deque.
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + 700_000L));
        assertTrue("should stop even though the qualifying anchor fix was pruned this round",
                stopped);
    }

    // ── moving fixes ────────────────────────────────────────────────────────

    @Test
    public void movingLocations_neverStop() {
        for (int i = 0; i < 20; i++) {
            checker.onLocationReceived(loc(37.0 + i * 0.01, -122.0, BASE_TIME + i * 2 * MINUTE));
        }
        assertFalse(stopped);
    }

    @Test
    public void movingFixInWindow_blocksStop_untilAgedOut() {
        // A moving fix at t=0, then stationary fixes every 2 minutes at a different location.
        checker.onLocationReceived(loc(38.0, -122.0, BASE_TIME));
        for (int i = 1; i <= 5; i++) {
            checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + i * 2 * MINUTE));
        }
        // At t=10min the moving fix (t=0) is still in the deque (age exactly 10 min, not
        // pruned) and is far from the stationary cluster, so no stop yet.
        assertFalse("stale moving fix should still block the stop", stopped);
        assertEquals(6, checker.getRecentCount());

        // t=12min: the moving fix is now >10 min old and gets pruned; the remaining fixes
        // (t=2..12), all stationary, span exactly 10 minutes and are all in radius.
        checker.onLocationReceived(loc(37.0, -122.0, BASE_TIME + 12 * MINUTE));
        assertTrue("should stop once the stale moving fix ages out of the window", stopped);
    }
}
