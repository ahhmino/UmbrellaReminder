package com.umbrellareminder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Shader;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Request;
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
import static java.lang.Math.abs;
import static java.lang.Math.min;

public class MainActivity extends AppCompatActivity implements TimePickerDialog.OnTimeSetListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button location_button = findViewById(R.id.refresh_location);
        location_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                getFutureData();
            }
        });

        SharedPreferences preferences = getSharedPreferences("Prefs", 0);
        String savedLoc = preferences.getString("loc_string", "Loading location...");

        TextView location = findViewById(R.id.location);
        location.setText(savedLoc);

        Button timeButton = findViewById(R.id.time_selector_label);
        String savedTime = preferences.getString("notify_time", "00:00 AM");
        if(savedTime.equals("00:00 AM")) {
            timeButton.setText(R.string.tap_here_to_set_a_reminder_time);
        }
        else {
            timeButton.setText(String.format(getString(R.string.selected_reminder_time), savedTime));
        }

        //location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION}, 1);
            }
            else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
        else {
            getFutureData();
        }

        timeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TimePickerFragment newFragment = new TimePickerFragment();
                newFragment.setListener(MainActivity.this);
                newFragment.show(getSupportFragmentManager(), newFragment.getTag());
            }
        });

    }

    void getFutureData() {
        ProgressBar refreshProgress = findViewById(R.id.refresh_progress);
        refreshProgress.setVisibility(View.VISIBLE);
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

                    if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        Task<FindCurrentPlaceResponse> placeResponse = placesClient.findCurrentPlace(request);
                        placeResponse.addOnCompleteListener(placesTask -> {
                            if (placesTask.isSuccessful() && placesTask.getResult() != null && placesTask.getResult().getPlaceLikelihoods().size() > 0){
                                FindCurrentPlaceResponse placesTaskResult = placesTask.getResult();
                                Place place = placesTaskResult.getPlaceLikelihoods().get(0).getPlace();
                                double lat = place.getLatLng().latitude;
                                double lon = place.getLatLng().longitude;

                                String url = "http://api.openweathermap.org/data/2.5/forecast?lat=" + lat
                                        + "&lon=" + lon
                                        + "&appid=" + weatherKey;
                                RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
                                StringRequest stringRequest = new StringRequest(Request.Method.GET, url, response -> {
                                    refreshProgress.setVisibility(View.INVISIBLE);
                                    String location_info = response.substring(response.indexOf("city"));
                                    String city = location_info.substring(location_info.indexOf("name") + 7, location_info.indexOf("coord") - 3);
                                    String country = location_info.substring(location_info.indexOf("country") + 10, location_info.indexOf("population") - 3);
                                    location_info = "Current Location: " + city + ", " + country;
                                    SharedPreferences sharedPreferences = getSharedPreferences("Prefs", 0);
                                    sharedPreferences.edit().putString("loc_string", location_info).apply();

                                    setMessage(response);
                                    setLocation(response);

                                }, error -> Log.d("ERROR", "Connection Error"));

                                requestQueue.add(stringRequest);
                            }
                            else {
                                refreshProgress.setVisibility(View.INVISIBLE);
                                Toast.makeText(getApplicationContext(), "Failed to get location and weather info", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        refreshProgress.setVisibility(View.INVISIBLE);
                        Toast.makeText(getApplicationContext(), "App requires background location permission to check weather", Toast.LENGTH_LONG).show();
                    }
                }
                else {
                    refreshProgress.setVisibility(View.INVISIBLE);
                    Toast.makeText(getApplicationContext(), "Failed to get location and weather info", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setMessage(String response) {
        //get today's date
        SharedPreferences preferences = getSharedPreferences("Prefs", 0);
        boolean bring = preferences.getBoolean("bring", false);

        //this section to retrieve current and next dates
        //need both because JSON response is in UTC, have to account for timezone differences
        //which might stretch today's local forecast into tomorrow UTC
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

        if (tomorrow_end != -1) {
            String tomorrow_response = response.substring(0, tomorrow_end);

            if (tomorrow_response.contains("rain")) {
                bring = true;
            } else {
                bring = false;
            }
        }

        TextView message = findViewById(R.id.message);
        if (bring) {
            message.setText(R.string.umbrella_yes);
        }
        else{
            message.setText(R.string.umbrella_no);
        }

    }

    void setLocation(String response) {
        //string manipulation of the JSON response to get user's location
        String location_info = response.substring(response.indexOf("city"));
        String city = location_info.substring(location_info.indexOf("name") + 7, location_info.indexOf("coord") - 3);
        String country = location_info.substring(location_info.indexOf("country") + 10, location_info.indexOf("population") - 3);
        location_info = "Current Location: " + city + ", " + country;

        if (location_info.contains("Earth")) {
            //restarting from the top, "earth" means user's location was null, ie 0 lat, 0 lon, no good
            Button location_button = findViewById(R.id.refresh_location);
            location_button.performClick();
        }
        TextView display_location = findViewById(R.id.location);
        display_location.setText(location_info);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if(permissions.length == 2) {
                getFutureData();
            }
            else {
                Toast.makeText(this, "Cannot get weather and location info without background location permissions", Toast.LENGTH_LONG).show();
            }
        }
        else {
            if(permissions.length == 1) {
                getFutureData();
            }
            else {
                Toast.makeText(this, "Cannot get weather and location info without location permissions", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onTimeSet(TimePicker timePicker, int i, int i1) {
        int hourOfDay = i;
        int minute = i1;

        String timeString = get_time_string(hourOfDay, minute);
        SharedPreferences settings = getSharedPreferences("Prefs", 0);
        settings.edit().putString("notify_time", timeString).apply();

        Button time_button = findViewById(R.id.time_selector_label);
        time_button.setText(String.format(getString(R.string.selected_reminder_time), get_time_string(hourOfDay, minute)));

        Calendar first_alarm = Calendar.getInstance();
        first_alarm.setTimeInMillis(System.currentTimeMillis());
        first_alarm.set(Calendar.HOUR_OF_DAY, hourOfDay);
        first_alarm.set(Calendar.MINUTE, minute);
        first_alarm.set(Calendar.SECOND, 0);
        if(first_alarm.get(Calendar.HOUR_OF_DAY) <= Calendar.getInstance().get(Calendar.HOUR_OF_DAY)){
            first_alarm.add(Calendar.DAY_OF_MONTH, 1);
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        PendingIntent nextPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(nextPendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, first_alarm.getTimeInMillis(), nextPendingIntent);
        }
        else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, first_alarm.getTimeInMillis(), nextPendingIntent);
        }
    }

    private String get_time_string(int hour, int minute){
        boolean pm = false;
        String time;

        if(hour >= 12){
            if(hour > 12) {
                hour -= 12;
            }
            pm = true;
        }

        String hour_string = Integer.toString(hour);
        String minute_string = Integer.toString(minute);

        if(minute < 10){
            minute_string = "0" + minute_string;
        }

        if(pm){
            time = hour_string + ":" + minute_string + " PM";
        }
        else{
            if(hour == 0){
                hour_string = "12";
            }
            time = hour_string + ":" + minute_string + " AM";
        }

        return time;
    }

}
