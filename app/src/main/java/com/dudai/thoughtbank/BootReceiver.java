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
        for (Thought t : ThoughtDb.get(ctx).getUpcoming(System.currentTimeMillis())) {
            ReminderScheduler.schedule(ctx, t);
        }
    }
}
