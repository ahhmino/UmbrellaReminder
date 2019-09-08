package com.umbrellareminder;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import static android.app.PendingIntent.getActivity;

public class AlarmReceiver extends BroadcastReceiver {
    private String CHANNEL_ID = "umbrella_reminder";

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onReceive(Context context, Intent intent) {
        GPSTracker gps = new GPSTracker(context);

        MainActivity ma = new MainActivity();
        // check if GPS enabled
        if (gps.canGetLocation()) {
            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();

            ma.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ma.getFutureData(latitude, longitude, context, new Response() {
                        @Override
                        public void onSuccess(boolean success) {
                            if(success){
                                //only runs notification if callback receives TRUE, ie variable 'bring' was true
                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                                    CharSequence name = "Umbrella Reminder";
                                    String description = "Notification channel used for Umbrella Reminder App";
                                    int importance = NotificationManager.IMPORTANCE_DEFAULT;
                                    NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                                    channel.setDescription(description);
                                    NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
                                    notificationManager.createNotificationChannel(channel);
                                }

                                Intent aintent = new Intent(context, AlertDialog.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                PendingIntent pendingIntent = getActivity(context, 0, aintent, 0);

                                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(R.drawable.notification_icon)
                                        .setContentTitle("Bring an umbrella!")
                                        .setContentText("Looks like it'll rain today")
                                        //high priority necessary for after app destroy
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setContentIntent(pendingIntent)
                                        .setAutoCancel(true);

                                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                                notificationManager.notify(0, builder.build());
                            }

                        }
                    });
                }
            });
        } else {
            // can't get location
            gps.showSettingsAlert();
        }

    }

}
