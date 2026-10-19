package com.dudai.thoughtbank;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Local SQLite storage. Everything stays on the phone. */
public class ThoughtDb extends SQLiteOpenHelper {

    private static final String TABLE = "thoughts";
    private static final String[] COLUMNS = {
            "id", "text", "created", "remind_at",
            "category", "repeat", "repeat_days", "done", "pinned", "last_shown"};
    private static final String LIST_ORDER = "pinned DESC, done ASC, created DESC";

    private static ThoughtDb instance;

    public static synchronized ThoughtDb get(Context ctx) {
        if (instance == null) instance = new ThoughtDb(ctx.getApplicationContext());
        return instance;
    }

    private ThoughtDb(Context ctx) {
        super(ctx, "thoughts.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "text TEXT NOT NULL, "
                + "created INTEGER NOT NULL, "
                + "remind_at INTEGER NOT NULL DEFAULT 0, "
                + "category INTEGER NOT NULL DEFAULT 0, "
                + "repeat INTEGER NOT NULL DEFAULT 0, "
                + "repeat_days INTEGER NOT NULL DEFAULT 0, "
                + "done INTEGER NOT NULL DEFAULT 0, "
                + "pinned INTEGER NOT NULL DEFAULT 0, "
                + "last_shown INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            String[] added = {"category", "repeat", "repeat_days", "done", "pinned", "last_shown"};
            for (String col : added) {
                db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + col + " INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    public long insert(Thought t) {
        return getWritableDatabase().insert(TABLE, null, values(t));
    }

    public void update(Thought t) {
        getWritableDatabase().update(TABLE, values(t), "id=?", new String[]{String.valueOf(t.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public Thought get(long id) {
        List<Thought> list = query("id=?", new String[]{String.valueOf(id)}, null, null);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Thoughts for the main list. category = -1 for all. */
    public List<Thought> getAll(String search, int category) {
        StringBuilder sel = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (category >= 0) {
            sel.append("category=?");
            args.add(String.valueOf(category));
        }
        if (search != null && !search.isEmpty()) {
            if (sel.length() > 0) sel.append(" AND ");
            sel.append("text LIKE ?");
            args.add("%" + search + "%");
        }
        return query(sel.length() == 0 ? null : sel.toString(),
                args.isEmpty() ? null : args.toArray(new String[0]), LIST_ORDER, null);
    }

    /** Thoughts whose reminder is still in the future. */
    public List<Thought> getUpcoming(long now) {
        return query("remind_at > ?", new String[]{String.valueOf(now)}, null, null);
    }

    /** All insights, least recently shown first (for the fair rotation). */
    public List<Thought> getInsightsByLastShown() {
        return query("category=? AND done=0", new String[]{String.valueOf(Thought.CAT_INSIGHT)},
                "last_shown ASC", null);
    }

    private List<Thought> query(String selection, String[] args, String order, String limit) {
        List<Thought> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, COLUMNS, selection, args,
                null, null, order != null ? order : LIST_ORDER, limit)) {
            while (c.moveToNext()) {
                Thought t = new Thought();
                t.id = c.getLong(0);
                t.text = c.getString(1);
                t.created = c.getLong(2);
                t.remindAt = c.getLong(3);
                t.category = c.getInt(4);
                t.repeat = c.getInt(5);
                t.repeatDays = c.getInt(6);
                t.done = c.getInt(7) == 1;
                t.pinned = c.getInt(8) == 1;
                t.lastShown = c.getLong(9);
                result.add(t);
            }
        }
        return result;
    }

    private static ContentValues values(Thought t) {
        ContentValues cv = new ContentValues();
        cv.put("text", t.text);
        cv.put("created", t.created);
        cv.put("remind_at", t.remindAt);
        cv.put("category", t.category);
        cv.put("repeat", t.repeat);
        cv.put("repeat_days", t.repeatDays);
        cv.put("done", t.done ? 1 : 0);
        cv.put("pinned", t.pinned ? 1 : 0);
        cv.put("last_shown", t.lastShown);
        return cv;
    }
}
