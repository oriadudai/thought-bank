package com.dudai.thoughtbank;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Sets / cancels the system alarms for a thought's reminder.
 * Request codes per thought: id*4 + 0 = reminder, +1 = snooze, +2/+3 = notification buttons.
 */
public final class ReminderScheduler {

    public static final String EXTRA_ID = "thought_id";
    public static final String EXTRA_SNOOZE = "snooze";

    private ReminderScheduler() {}

    /** @return true if an exact alarm was set, false if it fell back to an inexact one. */
    public static boolean schedule(Context ctx, Thought t) {
        return setAlarm(ctx, alarmIntent(ctx, t.id, false), t.remindAt);
    }

    /** One-off "remind me again later", independent of the regular schedule. */
    public static void snooze(Context ctx, long id, long at) {
        setAlarm(ctx, alarmIntent(ctx, id, true), at);
    }

    public static void cancel(Context ctx, long id) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(alarmIntent(ctx, id, false));
        am.cancel(alarmIntent(ctx, id, true));
    }

    static boolean setAlarm(Context ctx, PendingIntent pi, long at) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (at <= System.currentTimeMillis()) {
            am.cancel(pi);
            return true;
        }
        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
        return exact;
    }

    private static PendingIntent alarmIntent(Context ctx, long id, boolean snooze) {
        Intent intent = new Intent(ctx, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_SNOOZE, snooze);
        return PendingIntent.getBroadcast(ctx, (int) (id * 4 + (snooze ? 1 : 0)), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
