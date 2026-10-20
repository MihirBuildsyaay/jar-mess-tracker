package com.jarmess.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;

/** Fires at reminder time, and receives the answer buttons tapped on the notification. */
public final class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String action = String.valueOf(i.getAction());
        if (Reminder.ACTION_REMIND.equals(action)) {
            Reminder.schedule(c);
            Reminder.remind(c);
            return;
        }

        String day = i.getStringExtra(Reminder.EXTRA_DAY);
        if (day == null) return;
        LocalDate d = LocalDate.parse(day);
        int value = i.getIntExtra(Reminder.EXTRA_VALUE, 0);
        if (Reminder.ACTION_JAR.equals(action)) Db.get(c).setJars(d, value);
        else if (Reminder.ACTION_DINNER.equals(action)) Db.get(c).setDinner(d, value);
        else return;
        Reminder.advance(c, d);
    }
}
