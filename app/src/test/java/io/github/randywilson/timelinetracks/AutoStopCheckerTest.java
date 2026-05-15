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
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AutoStopCheckerTest {

    private static final int MAX_RECENT = 4;
    private static final float RADIUS_METERS = 100f;

    private boolean stopped;
    private AutoStopChecker checker;

    @Before
    public void setUp() {
        stopped = false;
        checker = new AutoStopChecker(MAX_RECENT, RADIUS_METERS, () -> stopped = true);
    }

    private static Location loc(double lat, double lon) {
        Location l = new Location("test");
        l.setLatitude(lat);
        l.setLongitude(lon);
        return l;
    }

    // ── basic deque behaviour ───────────────────────────────────────────────

    @Test
    public void firstLocation_addedToDeque_noStop() {
        checker.onLocationReceived(loc(37.0, -122.0));
        assertEquals(1, checker.getRecentCount());
        assertFalse(stopped);
    }

    @Test
    public void dequeGrowsUpToMaxRecent() {
        for (int i = 0; i < MAX_RECENT; i++) {
            checker.onLocationReceived(loc(37.0 + i * 0.01, -122.0));
        }
        assertEquals(MAX_RECENT, checker.getRecentCount());
    }

    @Test
    public void dequeCapsAtMaxRecent() {
        for (int i = 0; i < MAX_RECENT + 3; i++) {
            checker.onLocationReceived(loc(37.0 + i * 0.01, -122.0));
        }
        assertEquals(MAX_RECENT, checker.getRecentCount());
    }

    // ── no-stop scenarios ───────────────────────────────────────────────────

    @Test
    public void exactlyMaxRecentStationaryPoints_noStop() {
        // Fill the deque with identical points — trigger requires one MORE on top
        for (int i = 0; i < MAX_RECENT; i++) {
            checker.onLocationReceived(loc(37.0, -122.0));
        }
        assertFalse(stopped);
    }

    @Test
    public void movingLocations_neverStop() {
        for (int i = 0; i < 15; i++) {
            checker.onLocationReceived(loc(37.0 + i * 0.01, -122.0));
        }
        assertFalse(stopped);
    }

    // ── stop-trigger scenarios ──────────────────────────────────────────────

    @Test
    public void maxRecentPlusOneStationary_triggers() {
        for (int i = 0; i <= MAX_RECENT; i++) {          // MAX_RECENT + 1 total
            checker.onLocationReceived(loc(37.0, -122.0));
        }
        assertTrue(stopped);
    }

    @Test
    public void stopsOnExactlyFifthPoint_notBefore() {
        // 4 identical points — no stop yet
        for (int i = 0; i < MAX_RECENT; i++) {
            checker.onLocationReceived(loc(37.0, -122.0));
        }
        assertFalse("should not stop after " + MAX_RECENT + " points", stopped);

        // 5th point — stop triggered
        checker.onLocationReceived(loc(37.0, -122.0));
        assertTrue("should stop after " + (MAX_RECENT + 1) + " points", stopped);
    }

    // ── the integration scenario ────────────────────────────────────────────

    @Test
    public void tenMovingThenFiveStationary_triggers() {
        // 10 locations with 0.01° steps (~1,110 m each) — well outside 100 m
        for (int i = 0; i < 10; i++) {
            checker.onLocationReceived(loc(37.0 + i * 0.01, -122.0));
        }
        assertFalse("should not stop while moving", stopped);

        // 5 locations with 0.0001° steps (~11 m each), all within 100 m of each other.
        // The deque after the moving phase ends at ~[37.06, 37.07, 37.08, 37.09].
        // The first four stationary points flush those out one by one; only when the
        // deque contains four stationary-only points does the 5th stationary point trigger.
        for (int i = 0; i < 5; i++) {
            checker.onLocationReceived(loc(37.1 + i * 0.0001, -122.0));
        }
        assertTrue("should stop after 5 consecutive stationary points", stopped);
    }

    @Test
    public void movingPointBreaksStationary_noSpuriousStop() {
        // 3 stationary points
        for (int i = 0; i < 3; i++) {
            checker.onLocationReceived(loc(37.0, -122.0));
        }
        // 1 moving point — pushes old stationary point out of window over time
        checker.onLocationReceived(loc(38.0, -122.0));   // ~111 km away
        // 1 stationary — deque now contains the moving point, so no stop
        checker.onLocationReceived(loc(37.0, -122.0));
        assertFalse(stopped);
    }
}
