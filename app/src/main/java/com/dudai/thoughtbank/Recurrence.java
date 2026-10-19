package com.dudai.thoughtbank;

import android.content.Context;

import java.text.DateFormatSymbols;
import java.util.Calendar;

/** Repeat rules for reminders: none / daily / weekly (chosen days) / monthly. */
public final class Recurrence {

    public static final int NONE = 0;
    public static final int DAILY = 1;
    public static final int WEEKLY = 2;
    public static final int MONTHLY = 3;

    private Recurrence() {}

    /** Bit for a Calendar.DAY_OF_WEEK value (1 = Sunday … 7 = Saturday). */
    public static int bit(int dayOfWeek) {
        return 1 << (dayOfWeek - 1);
    }

    private static boolean matches(int days, Calendar c) {
        return (days & bit(c.get(Calendar.DAY_OF_WEEK))) != 0;
    }

    /** Earliest occurrence at or after base that is also after now. */
    public static long first(long base, int repeat, int days, long now) {
        if (repeat == NONE) return base;
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(base);
        if (repeat == WEEKLY && days != 0) {
            while (!matches(days, c)) c.add(Calendar.DAY_OF_MONTH, 1);
        }
        while (c.getTimeInMillis() <= now) step(c, repeat, days);
        return c.getTimeInMillis();
    }

    /** Next occurrence strictly after current, and after now. */
    public static long next(long current, int repeat, int days, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(current);
        do {
            step(c, repeat, days);
        } while (c.getTimeInMillis() <= now);
        return c.getTimeInMillis();
    }

    private static void step(Calendar c, int repeat, int days) {
        switch (repeat) {
            case WEEKLY:
                if (days == 0) {
                    c.add(Calendar.DAY_OF_MONTH, 7);
                } else {
                    do {
                        c.add(Calendar.DAY_OF_MONTH, 1);
                    } while (!matches(days, c));
                }
                break;
            case MONTHLY:
                c.add(Calendar.MONTH, 1);
                break;
            default: // DAILY
                c.add(Calendar.DAY_OF_MONTH, 1);
                break;
        }
    }

    /** Human-readable rule, e.g. "כל יום ב-08:00". */
    public static String describe(Context ctx, int repeat, int days, long at) {
        String time = Formats.time(at);
        switch (repeat) {
            case DAILY:
                return ctx.getString(R.string.repeat_daily, time);
            case WEEKLY:
                return ctx.getString(R.string.repeat_weekly, shortDayNames(days), time);
            case MONTHLY: {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(at);
                return ctx.getString(R.string.repeat_monthly, c.get(Calendar.DAY_OF_MONTH), time);
            }
            default:
                return "";
        }
    }

    private static String shortDayNames(int days) {
        String[] names = DateFormatSymbols.getInstance().getShortWeekdays();
        StringBuilder sb = new StringBuilder();
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            if ((days & bit(d)) != 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(names[d].replace("יום ", ""));
            }
        }
        return sb.toString();
    }

    /** Full weekday names, index 0 = Sunday. */
    public static String[] fullDayNames() {
        String[] w = DateFormatSymbols.getInstance().getWeekdays();
        String[] result = new String[7];
        for (int i = 0; i < 7; i++) result[i] = w[i + 1];
        return result;
    }
}
