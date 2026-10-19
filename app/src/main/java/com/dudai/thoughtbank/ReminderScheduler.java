package com.dudai.thoughtbank;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Sets / cancels the system alarm that fires a reminder notification. */
public final class ReminderScheduler {

    public static final String EXTRA_ID = "thought_id";

    private ReminderScheduler() {}

    /** @return true if an exact alarm was set, false if it fell back to an inexact one. */
    public static boolean schedule(Context ctx, Thought t) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = pendingIntent(ctx, t.id);

        if (t.remindAt <= System.currentTimeMillis()) {
            am.cancel(pi);
            return true;
        }

        boolean exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms();
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.remindAt, pi);
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.remindAt, pi);
        }
        return exact;
    }

    public static void cancel(Context ctx, long id) {
        ctx.getSystemService(AlarmManager.class).cancel(pendingIntent(ctx, id));
    }

    private static PendingIntent pendingIntent(Context ctx, long id) {
        Intent intent = new Intent(ctx, ReminderReceiver.class).putExtra(EXTRA_ID, id);
        // requestCode = thought id, so every thought has its own alarm
        return PendingIntent.getBroadcast(ctx, (int) id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
