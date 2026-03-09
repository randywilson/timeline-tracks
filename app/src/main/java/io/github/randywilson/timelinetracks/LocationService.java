package io.github.randywilson.timelinetracks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import java.util.ArrayDeque;

public class LocationService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "location_updates";
    private static final int MAX_RECENT = 5;
    private static final float AUTO_STOP_RADIUS_METERS = 100f;

    private LocationManager locationManager;
    private Handler handler;
    private Prefs prefs;
    private int intervalMillis;
    private boolean autoStop;
    private ArrayDeque<Location> recentLocations;
    private volatile boolean isStopping = false;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // We intentionally do nothing with this location.
            // The entire purpose of requesting it is the side effect:
            // Android broadcasts GPS fixes to all registered apps, so Google Timeline
            // (and any other location-aware app) receives this location for free.
            if (autoStop) {
                checkAutoStop(location);
            }
            addToRecent(location);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        prefs = new Prefs(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        intervalMillis = prefs.getIntervalSeconds() * 1000;
        autoStop = prefs.getAutoStop();
        recentLocations = new ArrayDeque<>();
        isStopping = false;

        prefs.setRunning(true);

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        requestLocation();
        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void requestLocation() {
        if (isStopping) return;
        try {
            // Cancel any pending request before registering a new one
            locationManager.removeUpdates(locationListener);
            locationManager.requestSingleUpdate(
                    LocationManager.GPS_PROVIDER,
                    locationListener,
                    Looper.getMainLooper()
            );
        } catch (SecurityException | IllegalArgumentException e) {
            // GPS unavailable or permission revoked — stop the service
            stopSelf();
            return;
        }
        // Schedule the next request regardless of whether this one gets a fix.
        // If GPS is unavailable (indoors, airplane mode), the request times out silently
        // and we try again at the next interval.
        handler.postDelayed(this::requestLocation, intervalMillis);
    }

    private void checkAutoStop(Location newLoc) {
        if (recentLocations.size() == MAX_RECENT) {
            for (Location recent : recentLocations) {
                if (newLoc.distanceTo(recent) > AUTO_STOP_RADIUS_METERS) {
                    return; // Not stationary yet
                }
            }
            // All 5 stored locations are within 100 m of the new location — user is stationary
            isStopping = true;
            stopSelf();
        }
    }

    private void addToRecent(Location loc) {
        recentLocations.addLast(loc);
        if (recentLocations.size() > MAX_RECENT) {
            recentLocations.removeFirst();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isStopping = true;
        locationManager.removeUpdates(locationListener);
        handler.removeCallbacksAndMessages(null);
        prefs.setRunning(false);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(getString(R.string.notification_channel_desc));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        int intervalSeconds = prefs.getIntervalSeconds();
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);

        // Tapping the notification body opens MainActivity
        Intent contentIntent = new Intent(this, MainActivity.class);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent, flags);

        // "Stop" action sends a broadcast to StopReceiver
        Intent stopIntent = new Intent(StopReceiver.ACTION_STOP);
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text, intervalSeconds))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .addAction(0, getString(R.string.stop), stopPendingIntent)
                .build();
    }
}
