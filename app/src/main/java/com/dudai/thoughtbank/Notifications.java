package com.dudai.thoughtbank;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;

final class Notifications {

    static final String CHANNEL_ID = "reminders";
    static final int INSIGHT_NOTIFICATION_ID = -100;
    private static final int INSIGHT_CONTENT_REQUEST = -100;

    private Notifications() {}

    static void ensureChannel(Context ctx) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    /** Reminder for a specific thought, with "snooze" and (for repeating ones) "stop" buttons. */
    static void showReminder(Context ctx, Thought t) {
        if (!canNotify(ctx)) return;
        ensureChannel(ctx);
        String title = ctx.getString(t.category == Thought.CAT_IDEA
                ? R.string.notif_title_idea : R.string.notif_title_insight);
        Notification.Builder b = builder(ctx, t, (int) t.id, title);
        b.addAction(action(ctx, ReminderReceiver.ACTION_SNOOZE, t.id, 2, R.string.snooze_hour));
        if (t.repeat != Recurrence.NONE) {
            b.addAction(action(ctx, ReminderReceiver.ACTION_STOP, t.id, 3, R.string.stop_repeat));
        }
        ctx.getSystemService(NotificationManager.class).notify((int) t.id, b.build());
    }

    /** A random insight from the stream. Replaces the previous one. */
    static void showInsight(Context ctx, Thought t) {
        if (!canNotify(ctx)) return;
        ensureChannel(ctx);
        Notification n = builder(ctx, t, INSIGHT_CONTENT_REQUEST,
                ctx.getString(R.string.insight_notif_title)).build();
        ctx.getSystemService(NotificationManager.class).notify(INSIGHT_NOTIFICATION_ID, n);
    }

    private static Notification.Builder builder(Context ctx, Thought t, int requestCode, String title) {
        Intent open = new Intent(ctx, EditActivity.class).putExtra(EditActivity.EXTRA_ID, t.id);
        PendingIntent content = TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(open)
                .getPendingIntent(requestCode,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(t.text)
                .setStyle(new Notification.BigTextStyle().bigText(t.text))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(content)
                .setAutoCancel(true);
    }

    private static Notification.Action action(Context ctx, String action, long id, int slot, int label) {
        Intent intent = new Intent(ctx, ReminderReceiver.class)
                .setAction(action)
                .putExtra(ReminderScheduler.EXTRA_ID, id);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) (id * 4 + slot), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(
                Icon.createWithResource(ctx, R.drawable.ic_notification),
                ctx.getString(label), pi).build();
    }

    private static boolean canNotify(Context ctx) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
}
