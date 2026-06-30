package io.github.randywilson.timelinetracks;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrefsTest {

    private Prefs prefs;

    @Before
    public void setUp() {
        prefs = new Prefs(ApplicationProvider.getApplicationContext());
        // Reset mutable state before each test
        prefs.setRunning(false);
        prefs.setLocationCount(0);
        prefs.setStartTime(0L);
        prefs.setStopTime(0L);
    }

    @Test
    public void running_defaultFalse_roundTrips() {
        assertFalse(prefs.isRunning());
        prefs.setRunning(true);
        assertTrue(prefs.isRunning());
        prefs.setRunning(false);
        assertFalse(prefs.isRunning());
    }

    @Test
    public void locationCount_defaultZero_incrementsAndResets() {
        assertEquals(0, prefs.getLocationCount());
        prefs.incrementLocationCount();
        prefs.incrementLocationCount();
        assertEquals(2, prefs.getLocationCount());
        prefs.setLocationCount(0);
        assertEquals(0, prefs.getLocationCount());
    }

    @Test
    public void stopReason_roundTrips() {
        prefs.setStopReason(Prefs.STOP_REASON_AUTO);
        assertEquals(Prefs.STOP_REASON_AUTO, prefs.getStopReason());

        prefs.setStopReason(Prefs.STOP_REASON_USER);
        assertEquals(Prefs.STOP_REASON_USER, prefs.getStopReason());
    }

    @Test
    public void slotValues_defaultAndRoundTrip() {
        assertEquals(1, prefs.getSlotMinutes(1));
        assertEquals(50, prefs.getSlotSeconds(1));
        prefs.setSlot(1, 3, 30);
        assertEquals(3, prefs.getSlotMinutes(1));
        assertEquals(30, prefs.getSlotSeconds(1));
    }

    @Test
    public void setSlot_normalizesOverflowSeconds() {
        prefs.setSlot(0, 2, 90);
        assertEquals(3, prefs.getSlotMinutes(0));
        assertEquals(30, prefs.getSlotSeconds(0));
    }

    @Test
    public void selectedInterval_defaultAndRoundTrip() {
        assertEquals(1, prefs.getSelectedInterval());
        prefs.setSelectedInterval(2);
        assertEquals(2, prefs.getSelectedInterval());
    }

    @Test
    public void getIntervalSeconds_usesSelectedSlot() {
        prefs.setSlot(1, 2, 30);
        prefs.setSelectedInterval(1);
        assertEquals(150, prefs.getIntervalSeconds());
    }

    @Test
    public void startAndStopTime_roundTrip() {
        long start = 1_000_000L;
        long stop  = 1_005_000L;
        prefs.setStartTime(start);
        prefs.setStopTime(stop);
        assertEquals(start, prefs.getStartTime());
        assertEquals(stop,  prefs.getStopTime());
    }

    @Test
    public void autoStop_roundTrips() {
        prefs.setAutoStop(true);
        assertTrue(prefs.getAutoStop());
        prefs.setAutoStop(false);
        assertFalse(prefs.getAutoStop());
    }
}
