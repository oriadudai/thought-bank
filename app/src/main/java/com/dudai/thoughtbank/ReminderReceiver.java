package com.dudai.thoughtbank;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Fired by AlarmManager at reminder time: shows the thought as a notification. */
public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, 0);
        Thought t = ThoughtDb.get(ctx).get(id);
        if (t == null) return; // deleted in the meantime

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Notifications.ensureChannel(ctx);

        // Tapping the notification opens the thought (with the main list behind it)
        Intent open = new Intent(ctx, EditActivity.class).putExtra(EditActivity.EXTRA_ID, id);
        PendingIntent contentIntent = TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(open)
                .getPendingIntent((int) id,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(ctx, Notifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notification_title))
                .setContentText(t.text)
                .setStyle(new Notification.BigTextStyle().bigText(t.text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();

        ctx.getSystemService(NotificationManager.class).notify((int) id, n);
    }
}
