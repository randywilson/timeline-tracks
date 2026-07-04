package io.github.randywilson.timelinetracks;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for LocationService.
 *
 * The test starts the real service, then injects locations directly via
 * {@link LocationService#simulateLocationReceived} (bypassing FusedLocationProviderClient),
 * and verifies behaviour through {@link Prefs}.
 *
 * Two variants:
 *   - autoStopEnabled:  10 moving fixes, then stationary fixes spanning well past the
 *                       10-minute auto-stop window → service auto-stops
 *   - autoStopDisabled: same sequence → service keeps running
 *
 * Distance reference (at ~37° latitude):
 *   0.01°  ≈ 1,110 m  (moving steps — well outside 100 m auto-stop radius)
 *   0.0001° ≈ 11 m    (stationary steps — well inside 100 m auto-stop radius)
 *
 * Fixes are timestamped 2 minutes apart via {@link Location#setTime}; auto-stop is gated
 * on each fix's own timestamp, not wall-clock time, so the test runs instantly.
 */
@RunWith(AndroidJUnit4.class)
public class LocationServiceIntegrationTest {

    @Rule
    public final GrantPermissionRule permissionRule = GrantPermissionRule.grant(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            android.Manifest.permission.POST_NOTIFICATIONS
    );

    private Context ctx;
    private Prefs prefs;

    @Before
    public void setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = new Prefs(ctx);
    }

    @After
    public void tearDown() throws InterruptedException {
        ctx.stopService(new Intent(ctx, LocationService.class));
        // Wait for onDestroy (which clears instance and sets running=false)
        waitFor(() -> LocationService.instance == null, 5_000);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static final long MINUTE = 60 * 1000L;
    private static final long BASE_TIME = 1_700_000_000_000L;

    private static Location loc(double lat, double lon, long timeMillis) {
        Location l = new Location("test");
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setAccuracy(10f);
        l.setTime(timeMillis);
        return l;
    }

    /** Blocks until {@code condition} is true or {@code timeoutMs} elapses. */
    private static void waitFor(BooleanSupplier condition, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
    }

    private interface BooleanSupplier { boolean get(); }

    private LocationService startService(boolean autoStop) throws InterruptedException {
        prefs.setAutoStop(autoStop);
        prefs.setSlot(0, 1, 0); // irrelevant for simulation, but realistic
        prefs.setSelectedInterval(0);
        prefs.setLocationCount(0);
        prefs.setRunning(false);
        prefs.setStartTime(System.currentTimeMillis());

        ctx.startService(new Intent(ctx, LocationService.class));

        // Wait for onStartCommand to complete (sets instance in onCreate, running=true in onStartCommand)
        waitFor(() -> LocationService.instance != null && prefs.isRunning(), 5_000);
        LocationService service = LocationService.instance;
        assertNotNull("LocationService did not start within 5 seconds", service);
        return service;
    }

    // ── test cases ───────────────────────────────────────────────────────────

    @Test
    public void autoStopEnabled_stopsAfterTenStationaryMinutes() throws InterruptedException {
        LocationService service = startService(true);

        // First fix received immediately after start
        service.simulateLocationReceived(loc(37.0, -122.0, BASE_TIME));
        assertEquals("first fix should be counted", 1, prefs.getLocationCount());
        assertTrue("service should still be running", prefs.isRunning());

        // 10 moving locations (~1,110 m steps, 2 minutes apart) — no auto-stop
        for (int i = 1; i <= 10; i++) {
            service.simulateLocationReceived(
                    loc(37.0 + i * 0.01, -122.0, BASE_TIME + i * 2 * MINUTE));
        }
        assertEquals(11, prefs.getLocationCount());
        assertTrue("service should still be running after moving locations", prefs.isRunning());

        // Stationary locations (~11 m steps, all within 100 m of each other), 2 minutes apart.
        // The first few don't span 10 minutes yet (and stale moving fixes are still in the
        // auto-stop window), so the service should still be running partway through.
        long stationaryStart = BASE_TIME + 20 * MINUTE;
        for (int i = 0; i < 3; i++) {
            service.simulateLocationReceived(
                    loc(37.1 + i * 0.0001, -122.0, stationaryStart + i * 2 * MINUTE));
        }
        assertTrue("service should still be running before the 10-minute stationary window elapses",
                prefs.isRunning());

        // Continue well past the point where the stationary window has fully elapsed and
        // all moving fixes have aged out of the auto-stop deque.
        for (int i = 3; i < 10; i++) {
            service.simulateLocationReceived(
                    loc(37.1 + i * 0.0001, -122.0, stationaryStart + i * 2 * MINUTE));
        }
        assertEquals("all 21 fixes should be counted", 21, prefs.getLocationCount());

        // Auto-stop callback sets running=false synchronously before calling stopSelf()
        assertFalse("service should have auto-stopped", prefs.isRunning());
        assertEquals(Prefs.STOP_REASON_AUTO, prefs.getStopReason());
    }

    @Test
    public void autoStopDisabled_keepsRunningPastTenStationaryMinutes() throws InterruptedException {
        LocationService service = startService(false);

        // First fix
        service.simulateLocationReceived(loc(37.0, -122.0, BASE_TIME));
        assertEquals(1, prefs.getLocationCount());

        // 10 moving locations
        for (int i = 1; i <= 10; i++) {
            service.simulateLocationReceived(
                    loc(37.0 + i * 0.01, -122.0, BASE_TIME + i * 2 * MINUTE));
        }

        // Stationary locations spanning well past 10 minutes — no stop because autoStop is disabled
        long stationaryStart = BASE_TIME + 20 * MINUTE;
        for (int i = 0; i < 10; i++) {
            service.simulateLocationReceived(
                    loc(37.1 + i * 0.0001, -122.0, stationaryStart + i * 2 * MINUTE));
        }

        assertEquals("all 21 fixes should be counted", 21, prefs.getLocationCount());
        assertTrue("service should still be running with autoStop disabled", prefs.isRunning());
    }
}
