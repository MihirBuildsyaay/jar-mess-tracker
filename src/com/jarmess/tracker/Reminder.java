package com.jarmess.tracker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;

/**
 * The daily alarm and its notification. The notification asks about one day at a time:
 * first the water jar, then the mess dinner, answered straight from the notification buttons.
 */
final class Reminder {
    static final String ACTION_REMIND = "com.jarmess.tracker.REMIND";
    static final String ACTION_JAR = "com.jarmess.tracker.JAR";
    static final String ACTION_DINNER = "com.jarmess.tracker.DINNER";
    static final String EXTRA_DAY = "day";
    static final String EXTRA_VALUE = "value";

    private static final String CHANNEL = "daily_check";
    private static final int NOTIFICATION_ID = 1;
    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    /** How many days back the reminder chases days that were never answered. */
    private static final int CATCH_UP_DAYS = 7;

    private Reminder() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static LocalTime time(Context c) {
        SharedPreferences p = prefs(c);
        return LocalTime.of(p.getInt("hour", 9), p.getInt("minute", 0));
    }

    static void setTime(Context c, int hour, int minute) {
        prefs(c).edit().putInt("hour", hour).putInt("minute", minute).apply();
        schedule(c);
    }

    /** Remembers the first day the app was opened, so earlier days never count as "not filled in". */
    static void markFirstUse(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains("since")) p.edit().putString("since", LocalDate.now().toString()).apply();
    }

    static LocalDate since(Context c) {
        String s = prefs(c).getString("since", null);
        return s == null ? LocalDate.now() : LocalDate.parse(s);
    }

    /** Arms the alarm for the next reminder time. Safe to call any number of times. */
    static void schedule(Context c) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.with(time(c));
        if (!next.isAfter(now)) next = next.plusDays(1);
        long at = next.toInstant().toEpochMilli();

        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_REMIND);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, FLAGS);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (canExact(c)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                return;
            } catch (SecurityException ignored) {
                // Permission revoked between the check and the call; fall back to inexact.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    static boolean canExact(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    /**
     * Oldest recent day with a missing answer, or null. A morning reminder covers up to yesterday
     * (dinner hasn't happened yet); one set for 6 PM or later also covers today.
     */
    static LocalDate pendingDay(Context c) {
        LocalDate today = LocalDate.now();
        LocalDate last = time(c).getHour() >= 18 ? today : today.minusDays(1);
        LocalDate from = since(c);
        if (from.isBefore(today.minusDays(CATCH_UP_DAYS))) from = today.minusDays(CATCH_UP_DAYS);
        for (LocalDate d = from; !d.isAfter(last); d = d.plusDays(1)) {
            int[] e = Db.get(c).entry(d);
            if (e[0] == Db.UNSET || e[1] == Db.UNSET) return d;
        }
        return null;
    }

    /** Morning alarm: ask about the oldest unanswered day, if any. */
    static void remind(Context c) {
        LocalDate d = pendingDay(c);
        if (d != null) ask(c, d, true);
    }

    /** After a button answer: next question for that day, then any other missed day, then a summary. */
    static void advance(Context c, LocalDate day) {
        int[] e = Db.get(c).entry(day);
        if (e[0] == Db.UNSET || e[1] == Db.UNSET) {
            ask(c, day, false);
            return;
        }
        LocalDate more = pendingDay(c);
        if (more != null) ask(c, more, false);
        else post(c, saved(c, day, e), false);
    }

    /** Settings > test: always asks about yesterday, even if it is already filled in. */
    static void test(Context c) {
        post(c, jarQuestion(c, LocalDate.now().minusDays(1)), true);
    }

    /** Removes the notification if it is asking about a day the user just filled in inside the app. */
    static void dismissIfAbout(Context c, LocalDate day) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        for (StatusBarNotification n : nm.getActiveNotifications()) {
            if (n.getId() == NOTIFICATION_ID && day.toString().equals(n.getNotification().extras.getString(EXTRA_DAY))) {
                nm.cancel(NOTIFICATION_ID);
            }
        }
    }

    private static void ask(Context c, LocalDate day, boolean fresh) {
        int[] e = Db.get(c).entry(day);
        post(c, e[0] == Db.UNSET ? jarQuestion(c, day) : dinnerQuestion(c, day), fresh);
    }

    private static Notification.Builder jarQuestion(Context c, LocalDate day) {
        return base(c, day)
                .setContentTitle("💧 Water jar · " + Fmt.day(day))
                .setContentText("Did you pick up a water jar " + Fmt.when(day) + "?")
                .addAction(answer(c, ACTION_JAR, day, 0, "No", 1))
                .addAction(answer(c, ACTION_JAR, day, 1, "1 jar", 2))
                .addAction(answer(c, ACTION_JAR, day, 2, "2 jars", 3));
    }

    private static Notification.Builder dinnerQuestion(Context c, LocalDate day) {
        return base(c, day)
                .setContentTitle("🍽 Mess dinner · " + Fmt.day(day))
                .setContentText("Did you have dinner at the mess " + Fmt.night(day) + "?")
                .addAction(answer(c, ACTION_DINNER, day, 1, "Yes", 4))
                .addAction(answer(c, ACTION_DINNER, day, 0, "No", 5));
    }

    private static Notification.Builder saved(Context c, LocalDate day, int[] e) {
        YearMonth m = YearMonth.from(day);
        Db.Summary s = Db.get(c).summary(m);
        String body = Fmt.month(m) + " so far: " + Fmt.jars(s.jars) + " · " + s.dinners
                + " mess dinners · " + Fmt.rs(s.total());
        return base(c, day)
                .setContentTitle("Saved ✓ " + Fmt.day(day) + ": " + Fmt.jars(e[0]) + ", "
                        + (e[1] == 1 ? "mess dinner" : "no mess dinner"))
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setTimeoutAfter(15_000);
    }

    private static Notification.Builder base(Context c, LocalDate day) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Daily check", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Morning question about the water jar and mess dinner");
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);

        Intent open = new Intent(c, MainActivity.class)
                .putExtra(EXTRA_DAY, day.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Bundle extras = new Bundle();
        extras.putString(EXTRA_DAY, day.toString());
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF0E7490)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(PendingIntent.getActivity(c, 0, open, FLAGS))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .addExtras(extras);
    }

    private static Notification.Action answer(Context c, String action, LocalDate day, int value, String label, int requestCode) {
        Intent i = new Intent(c, AlarmReceiver.class)
                .setAction(action)
                .putExtra(EXTRA_DAY, day.toString())
                .putExtra(EXTRA_VALUE, value);
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode, i, FLAGS);
        return new Notification.Action.Builder(Icon.createWithResource(c, R.drawable.ic_notification), label, pi).build();
    }

    private static void post(Context c, Notification.Builder b, boolean fresh) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        // A new morning's reminder must make a sound even if yesterday's is still showing.
        if (fresh) nm.cancel(NOTIFICATION_ID);
        nm.notify(NOTIFICATION_ID, b.build());
    }
}
