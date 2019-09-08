package com.umbrellareminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootCompletedIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        //restarting the service if the device has been rebooted
        if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
            Intent pushIntent = new Intent(context, AlarmReceiver.class);
            context.startService(pushIntent);
        }
    }
}
