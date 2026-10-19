package com.dudai.thoughtbank;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * Random insights sent during waking hours.
 * - Interval: random, between 0.5x and 1.5x the chosen average (default ~3 hours).
 * - Fair rotation: picks at random from the least-recently-shown third of the insights.
 */
final class InsightStream {

    private static final String PREFS = "settings";
    private static final String K_ENABLED = "stream_enabled";
    private static final String K_START = "stream_start_hour";
    private static final String K_END = "stream_end_hour";
    private static final String K_AVG = "stream_avg_hours";
    private static final String K_NEXT = "stream_next";

    private static final int REQUEST_CODE = 0x7FFF0000;
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private static final Random random = new Random();

    private InsightStream() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context ctx) { return prefs(ctx).getBoolean(K_ENABLED, true); }
    static int startHour(Context ctx) { return prefs(ctx).getInt(K_START, 9); }
    static int endHour(Context ctx) { return prefs(ctx).getInt(K_END, 21); }
    static int avgHours(Context ctx) { return prefs(ctx).getInt(K_AVG, 3); }

    static void save(Context ctx, boolean enabled, int start, int end, int avg) {
        prefs(ctx).edit()
                .putBoolean(K_ENABLED, enabled)
                .putInt(K_START, start)
                .putInt(K_END, end)
                .putInt(K_AVG, avg)
                .putLong(K_NEXT, 0)   // re-plan with the new settings
                .apply();
        ensureScheduled(ctx);
    }

    /** Makes sure the next alarm is set. Keeps an already-planned time instead of re-rolling it. */
    static void ensureScheduled(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = pendingIntent(ctx);
        if (!enabled(ctx)) {
            am.cancel(pi);
            return;
        }
        long now = System.currentTimeMillis();
        long next = prefs(ctx).getLong(K_NEXT, 0);
        if (next <= now) {
            next = planNext(ctx, now);
            prefs(ctx).edit().putLong(K_NEXT, next).apply();
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
    }

    /** Called when the alarm fires: show one insight, plan the next. */
    static void fire(Context ctx) {
        prefs(ctx).edit().putLong(K_NEXT, 0).apply();
        if (enabled(ctx)) {
            Thought t = pickFair(ctx);
            if (t != null) {
                t.lastShown = System.currentTimeMillis();
                ThoughtDb.get(ctx).update(t);
                Notifications.showInsight(ctx, t);
            }
        }
        ensureScheduled(ctx);
    }

    /** Random choice among the least-recently-shown third (at least one). */
    private static Thought pickFair(Context ctx) {
        List<Thought> all = ThoughtDb.get(ctx).getInsightsByLastShown();
        if (all.isEmpty()) return null;
        int pool = Math.max(1, all.size() / 3);
        return all.get(random.nextInt(pool));
    }

    /** now + random(0.5..1.5 × average), moved into the waking-hours window if needed. */
    private static long planNext(Context ctx, long now) {
        int start = startHour(ctx);
        int end = Math.max(endHour(ctx), start + 1);
        long avg = avgHours(ctx) * HOUR;
        long candidate = now + (long) (avg * (0.5 + random.nextDouble()));

        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(candidate);
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour >= start && hour < end) return candidate;

        // Outside waking hours: next morning (or this morning, if it's still before start),
        // at a random point within the first half of an interval after the window opens.
        Calendar morning = Calendar.getInstance();
        morning.setTimeInMillis(candidate);
        if (hour >= end) morning.add(Calendar.DAY_OF_MONTH, 1);
        morning.set(Calendar.HOUR_OF_DAY, start);
        morning.set(Calendar.MINUTE, 0);
        morning.set(Calendar.SECOND, 0);
        morning.set(Calendar.MILLISECOND, 0);
        long result = morning.getTimeInMillis() + (long) (random.nextDouble() * avg / 2);
        return Math.max(result, now + MINUTE);
    }

    private static PendingIntent pendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_INSIGHT);
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
