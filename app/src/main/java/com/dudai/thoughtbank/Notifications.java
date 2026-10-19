package com.dudai.thoughtbank;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

final class Notifications {

    static final String CHANNEL_ID = "reminders";

    private Notifications() {}

    static void ensureChannel(Context ctx) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
}
