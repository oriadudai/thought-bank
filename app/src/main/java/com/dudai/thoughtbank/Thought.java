package com.dudai.thoughtbank;

/** One saved thought. remindAt = 0 means no reminder. */
public class Thought {
    public long id;          // 0 = not saved yet
    public String text = "";
    public long created;     // epoch millis
    public long remindAt;    // epoch millis, 0 = none
}
