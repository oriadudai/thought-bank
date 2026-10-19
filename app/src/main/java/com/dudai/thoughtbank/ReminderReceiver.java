package com.dudai.thoughtbank;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Handles all alarms and notification buttons. */
public class ReminderReceiver extends BroadcastReceiver {

    static final String ACTION_REMIND = "com.dudai.thoughtbank.REMIND";
    static final String ACTION_SNOOZE = "com.dudai.thoughtbank.SNOOZE";
    static final String ACTION_STOP = "com.dudai.thoughtbank.STOP";
    static final String ACTION_INSIGHT = "com.dudai.thoughtbank.INSIGHT";

    private static final long HOUR = 60 * 60 * 1000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) action = ACTION_REMIND;

        if (ACTION_INSIGHT.equals(action)) {
            InsightStream.fire(ctx);
            return;
        }

        long id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0);
        ThoughtDb db = ThoughtDb.get(ctx);
        Thought t = db.get(id);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        long now = System.currentTimeMillis();

        switch (action) {
            case ACTION_SNOOZE:
                nm.cancel((int) id);
                if (t != null) ReminderScheduler.snooze(ctx, id, now + HOUR);
                break;

            case ACTION_STOP:
                nm.cancel((int) id);
                if (t != null) {
                    ReminderScheduler.cancel(ctx, id);
                    t.repeat = Recurrence.NONE;
                    t.remindAt = 0;
                    db.update(t);
                }
                break;

            default: // ACTION_REMIND
                if (t == null) return;
                boolean snooze = intent.getBooleanExtra(ReminderScheduler.EXTRA_SNOOZE, false);
                if (!snooze && t.repeat != Recurrence.NONE) {
                    t.remindAt = Recurrence.next(t.remindAt, t.repeat, t.repeatDays, now);
                    db.update(t);
                    ReminderScheduler.schedule(ctx, t);
                }
                Notifications.showReminder(ctx, t);
                break;
        }
    }
}
