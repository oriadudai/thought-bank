package com.dudai.thoughtbank;

/** One saved thought. remindAt = 0 means no reminder. */
public class Thought {
    public static final int CAT_INSIGHT = 0;  // תובנה
    public static final int CAT_IDEA = 1;     // רעיון

    public long id;            // 0 = not saved yet
    public String text = "";
    public long created;       // epoch millis
    public long remindAt;      // next reminder, epoch millis, 0 = none
    public int category = CAT_INSIGHT;
    public int repeat = Recurrence.NONE;
    public int repeatDays;     // weekly: bitmask, bit (dayOfWeek-1), Sunday = bit 0
    public boolean done;       // ideas only
    public boolean pinned;
    public long lastShown;     // last time shown by the random-insight stream
}
