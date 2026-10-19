package com.dudai.thoughtbank;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Alarms are wiped on reboot and on app update, so re-create them. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            rescheduleAll(ctx);
        }
    }

    static void rescheduleAll(Context ctx) {
        long now = System.currentTimeMillis();
        ThoughtDb db = ThoughtDb.get(ctx);
        for (Thought t : db.getUpcoming(now)) {
            ReminderScheduler.schedule(ctx, t);
        }
        // Repeating reminders whose time passed while the phone was off: move to the next occurrence
        for (Thought t : db.getAll(null, -1)) {
            if (t.repeat != Recurrence.NONE && t.remindAt > 0 && t.remindAt <= now) {
                t.remindAt = Recurrence.first(t.remindAt, t.repeat, t.repeatDays, now);
                db.update(t);
                ReminderScheduler.schedule(ctx, t);
            }
        }
        InsightStream.ensureScheduled(ctx);
    }
}
