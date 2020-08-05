package com.umbrellareminder;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.format.DateFormat;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.DialogFragment;

import java.util.Calendar;

public class TimePickerFragment extends DialogFragment {

    private TimePickerDialog.OnTimeSetListener listener;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        final Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);

        // Create a new instance of TimePickerDialog and return it
        if ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            return new TimePickerDialog(getContext(), TimePickerDialog.THEME_HOLO_DARK, listener, hour, minute, DateFormat.is24HourFormat(getContext()));
        }
        else {
            return new TimePickerDialog(getContext(), TimePickerDialog.THEME_HOLO_LIGHT, listener, hour, minute, DateFormat.is24HourFormat(getContext()));
        }
    }

    public void setListener(TimePickerDialog.OnTimeSetListener listener) {
        this.listener = listener;
    }

}
