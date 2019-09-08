package com.umbrellareminder;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.Request;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;

@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity {

    public static String AppId = "8cec4771477552dd0f2149350b290e4e";

    private static TextView display_location;
    private static TextView message;
    private TextView prompt;
    private Button location_button;
    private VideoView video;
    private double latitude;
    private double longitude;
    private Spinner time_list;
    private String remind_time;
    private String CHANNEL_ID = "umbrella_reminder";
    private GPSTracker gps;

    private AlarmManager alarmMgr;
    private PendingIntent alarmIntent;

    boolean mIsReceiverRegistered = false;
    AlarmReceiver mReceiver = null;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            super.onRestoreInstanceState(savedInstanceState);
        }
        setContentView(R.layout.activity_main);

        //getting al UI elements and bringing them in front of background video
        location_button = findViewById(R.id.refresh_location);
        display_location = findViewById(R.id.location);
        message = findViewById(R.id.message);
        video = findViewById(R.id.video);
        time_list = findViewById(R.id.time_list);
        prompt = findViewById(R.id.time_select_prompt);

        location_button.bringToFront();
        display_location.bringToFront();
        message.bringToFront();
        prompt.bringToFront();
        time_list.bringToFront();

        //background video running + set looping
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.rain);
        video.setVideoURI(uri);
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);
            }
        });
        video.start();

        //alarm initialization, NOT setting
        Intent intent = new Intent(this,AlarmReceiver.class);
        //need this flag for the alarm to be in the 'foreground', so user can receive notifications after app is killed
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        alarmIntent = PendingIntent.getBroadcast(MainActivity.this, 0, intent, 0);
        alarmMgr = (AlarmManager)MainActivity.this.getSystemService(Context.ALARM_SERVICE);

        location_button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // create class object
                gps = new GPSTracker(MainActivity.this);

                // check if GPS enabled
                if (gps.canGetLocation()) {
                    latitude = gps.getLatitude();
                    longitude = gps.getLongitude();

                    getFutureData(latitude, longitude, MainActivity.this, new Response() {
                        @Override
                        public void onSuccess(boolean success) {
                        }
                    });

                } else {
                    // can't get location
                    gps.showSettingsAlert();
                }

            }
        });

        //location permissions
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
            location_button.performClick();
        }
        else{
            location_button.performClick();
        }

        //setting the drop down list of time choices to previously selected time, if there was a selection
        SharedPreferences preferences = getSharedPreferences("time", 0);
        int choice = preferences.getInt("choice", -1);
        if(choice != -1){
            time_list.setSelection(choice);
        }

        time_list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                remind_time = time_list.getSelectedItem().toString();

                //saving selected time choice to be set when app is reopened
                int selected_time = time_list.getSelectedItemPosition();
                SharedPreferences time = getSharedPreferences("time",0);
                SharedPreferences.Editor editor = time.edit();
                editor.putInt("choice", selected_time);
                editor.apply();

                int hour = Integer.parseInt(Character.toString(remind_time.charAt(0)));
                int minute = Integer.parseInt(remind_time.substring(2,4));

                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(System.currentTimeMillis());
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);

                if(cal.getTimeInMillis() <= System.currentTimeMillis()){
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                }

                //line where we set the alarm
                alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),AlarmManager.INTERVAL_DAY, alarmIntent);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
    }

    //simple functions used to determine whether to update UI or not (only update if activity is visible)
    public static boolean isActivityVisible() {
        return activityVisible;
    }

    public static void activityResumed() {
        activityVisible = true;
    }

    public static void activityPaused() {
        activityVisible = false;
    }

    private static boolean activityVisible;

    @Override
    protected void onPause() {
        super.onPause();
        MainActivity.activityPaused();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Uri uri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.rain);
        video.setVideoURI(uri);
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.setLooping(true);
            }
        });
        video.start();
        //registering the receiver so the receiver can run after app destroy
        if (!mIsReceiverRegistered) {
            if (mReceiver == null)
                mReceiver = new AlarmReceiver();
            this.registerReceiver(mReceiver, new IntentFilter("AlarmReceiver"));
            mIsReceiverRegistered = true;
        }
        MainActivity.activityResumed();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    void getFutureData(double lat, double lon, Context context, Response callback) {
        String url = "http://api.openweathermap.org/data/2.5/forecast?lat=" + lat
                + "&lon=" + lon
                + "&appid=" + AppId;
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        //Create an error listener to handle errors appropriately.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url, response -> {
            setLocation(response);
            //need a callback because StringRequest runs in another thread from the rest of the activity
            callback.onSuccess(setMessage(response));
        }, error -> Log.d("ERROR", "Connection Error"));

        requestQueue.add(stringRequest);
    }

    boolean setMessage(String response){
        //get today's date
        boolean bring = false;
        boolean visible = isActivityVisible();

        //this section to retrieve current and next dates
        //need both because JSON response is in UTC, have to account for timezone differences
        //which might stretch today's local forecast into tomorrow UTC
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

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

        //only updating UI if application is visible
        if (bring && visible) {
            message.setText(R.string.umbrella_yes);
        }
        else if (visible){
            message.setText(R.string.umbrella_no);
        }

        return bring;
    }

    void setLocation(String response) {
        if(isActivityVisible()){
            //string manipulation of the JSON response to get user's location
            String location_info = response.substring(response.indexOf("city"));
            String city = location_info.substring(location_info.indexOf("name") + 7,location_info.indexOf("coord") - 3);
            String country = location_info.substring(location_info.indexOf("country") + 10, location_info.indexOf("population") - 3);
            location_info = "Current Location: " + city + ", " + country;

            if(location_info.contains("Earth")){
                //restarting from the top, "earth" means user's location was null, ie 0 lat, 0 lon, no good
                location_button.performClick();
            }
            display_location.setText(location_info);
        }
    }

}
