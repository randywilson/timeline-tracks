package io.github.randywilson.timelinetracks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.ArrayDeque;

public class LocationService extends Service {

    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "location_updates";
    private static final int MAX_RECENT = 4;
    private static final float AUTO_STOP_RADIUS_METERS = 100f;

    private FusedLocationProviderClient fusedClient;
    private Prefs prefs;
    private boolean autoStop;
    private ArrayDeque<Location> recentLocations;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            Location location = result.getLastLocation();
            if (location == null) return;
            // We intentionally do nothing with this location.
            // The entire purpose of requesting it is the side effect:
            // FusedLocationProviderClient feeds fixes directly into Google Play Services,
            // so Google Timeline (and any other location-aware app) receives them for free.
            prefs.incrementLocationCount();
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
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        prefs = new Prefs(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long intervalMillis = (long) prefs.getIntervalSeconds() * 1000;
        autoStop = prefs.getAutoStop();
        recentLocations = new ArrayDeque<>();

        prefs.setRunning(true);

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis)
                .setWaitForAccurateLocation(false)
                .build();

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopSelf();
        }
        return START_STICKY;
    }

    private void checkAutoStop(Location newLoc) {
        if (recentLocations.size() == MAX_RECENT) {
            for (Location recent : recentLocations) {
                if (newLoc.distanceTo(recent) > AUTO_STOP_RADIUS_METERS) {
                    return; // Not stationary yet
                }
            }
            // All 5 stored locations are within 100 m of the new location — user is stationary
            prefs.setStopTime(System.currentTimeMillis());
            prefs.setStopReason(Prefs.STOP_REASON_AUTO);
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
        fusedClient.removeLocationUpdates(locationCallback);
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
