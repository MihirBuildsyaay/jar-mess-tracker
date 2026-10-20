package com.jarmess.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalTime;

/** Re-arms the daily alarm after a reboot, app update or clock change. */
public final class SystemReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Reminder.schedule(c);
        // Phone was off at reminder time: ask now rather than skip the day.
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) && !LocalTime.now().isBefore(Reminder.time(c))) {
            Reminder.remind(c);
        }
    }
}
