package com.umbrellareminder;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.app.PendingIntent.getActivity;
import static java.lang.Math.abs;

public class ReminderService extends IntentService {

    public ReminderService() {
        super("ReminderService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        String CHANNEL_ID = "umbrella_reminder";
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence name = "Umbrella Reminder";
            String description = "Notification channel used for Umbrella Reminder App";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        Intent aintent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = getActivity(this, 0, aintent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle("Getting location and weather information")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setChannelId(CHANNEL_ID)
                .setAutoCancel(true);

        Notification noti = builder.build();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(0, noti);
        startForeground(1, noti);

        Calendar next_alarm = Calendar.getInstance();
        next_alarm.add(1, Calendar.DATE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next_alarm.getTimeInMillis(), nextPendingIntent);
        }
        else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, next_alarm.getTimeInMillis(), nextPendingIntent);
        }

        FirebaseFunctions.getInstance().getHttpsCallable("getKeys").call().addOnCompleteListener(new OnCompleteListener<HttpsCallableResult>() {
            @Override
            public void onComplete(@NonNull Task<HttpsCallableResult> task) {
                if(task.isSuccessful() && task.getResult() != null && task.getResult().getData() != null) {
                    Map<String, Object> data = (Map<String, Object>) task.getResult().getData();
                    String placesKey = data.get("placesKey").toString();
                    String weatherKey = data.get("openWeatherKey").toString();

                    Places.initialize(getApplicationContext(), placesKey);
                    PlacesClient placesClient = Places.createClient(getApplicationContext());

                    List<Place.Field> placeFields = Collections.singletonList(Place.Field.LAT_LNG);
                    FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

                    if(ContextCompat.checkSelfPermission(getApplicationContext(), ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        Task<FindCurrentPlaceResponse> placeResponse = placesClient.findCurrentPlace(request);
                        placeResponse.addOnCompleteListener(placesTask -> {
                            if (placesTask.isSuccessful()){
                                FindCurrentPlaceResponse placesTaskResult = placesTask.getResult();
                                Place place = placesTaskResult.getPlaceLikelihoods().get(0).getPlace();
                                double lat = place.getLatLng().latitude;
                                double lon = place.getLatLng().longitude;

                                String url = "http://api.openweathermap.org/data/2.5/forecast?lat=" + lat
                                        + "&lon=" + lon
                                        + "&appid=" + weatherKey;
                                RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
                                StringRequest stringRequest = new StringRequest(Request.Method.GET, url, response -> {
                                    String location_info = response.substring(response.indexOf("city"));
                                    String city = location_info.substring(location_info.indexOf("name") + 7, location_info.indexOf("coord") - 3);
                                    String country = location_info.substring(location_info.indexOf("country") + 10, location_info.indexOf("population") - 3);
                                    location_info = "Current Location: " + city + ", " + country;
                                    SharedPreferences sharedPreferences = getSharedPreferences("Prefs", 0);
                                    sharedPreferences.edit().putString("loc_string", location_info).apply();

                                    int weather = determineUmbrella(response);
                                    if(weather != R.string.sunny) {
                                        showReminder(weather);
                                    }

                                    stopForeground(true);
                                    stopSelf();

                                }, error -> Log.d("ERROR", "Connection Error"));

                                requestQueue.add(stringRequest);
                            }
                        });
                    } else {
                        Toast.makeText(getApplicationContext(), "App requires location permission to check weather", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    private int determineUmbrella(String response) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        Calendar cal = Calendar.getInstance();
        long offset_hours = TimeUnit.HOURS.convert(cal.getTimeZone().getRawOffset(), TimeUnit.MILLISECONDS);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        Date tomorrow_date = cal.getTime();
        String tomorrow_string;

        if (abs(offset_hours) < 10) {
            tomorrow_string = formatter.format(tomorrow_date) + " 0" + abs(offset_hours) + ":00:00";
        } else {
            tomorrow_string = formatter.format(tomorrow_date) + " " + abs(offset_hours) + ":00:00";
        }

        int tomorrow_end = response.lastIndexOf(tomorrow_string);

        int forecast = R.string.sunny;

        if (tomorrow_end != -1) {
            String tomorrow_response = response.substring(0, tomorrow_end);
            if (tomorrow_response.contains("rain")) {
                forecast = R.string.forecast_rain;
            }
            else if(tomorrow_response.contains("storm")) {
                forecast = R.string.forecast_storms;
            }
            else if(tomorrow_response.contains("snow")) {
                forecast = R.string.forecast_snow;
            }
        }

        return forecast;
    }

    private void showReminder(int weather_string) {
        String CHANNEL_ID = "umbrella_reminder";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            CharSequence name = "Umbrella Reminder";
            String description = "Notification channel used for Umbrella Reminder App";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(CHANNEL_ID);
        }

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = getActivity(getApplicationContext(), 0, intent, 0);

        builder.setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(weather_string))
                .setContentText(getString(R.string.umbrella_yes))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[] {0, 500})
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
        notificationManager.notify(0, builder.build());
    }
}
