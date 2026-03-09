package io.github.randywilson.timelinetracks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopReceiver extends BroadcastReceiver {

    public static final String ACTION_STOP = "io.github.randywilson.timelinetracks.ACTION_STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        context.stopService(new Intent(context, LocationService.class));

        // Bring MainActivity to foreground so the button updates to "Start"
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(mainIntent);
    }
}
