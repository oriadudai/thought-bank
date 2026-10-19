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
    private static final String[] COLUMNS = {"id", "text", "created", "remind_at"};
    private static ThoughtDb instance;

    public static synchronized ThoughtDb get(Context ctx) {
        if (instance == null) instance = new ThoughtDb(ctx.getApplicationContext());
        return instance;
    }

    private ThoughtDb(Context ctx) {
        super(ctx, "thoughts.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "text TEXT NOT NULL, "
                + "created INTEGER NOT NULL, "
                + "remind_at INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema changes go here.
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
        List<Thought> list = query("id=?", new String[]{String.valueOf(id)});
        return list.isEmpty() ? null : list.get(0);
    }

    /** All thoughts, newest first, optionally filtered by a search string. */
    public List<Thought> getAll(String search) {
        if (search == null || search.isEmpty()) return query(null, null);
        return query("text LIKE ?", new String[]{"%" + search + "%"});
    }

    /** Thoughts whose reminder is still in the future. */
    public List<Thought> getUpcoming(long now) {
        return query("remind_at > ?", new String[]{String.valueOf(now)});
    }

    private List<Thought> query(String selection, String[] args) {
        List<Thought> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, COLUMNS, selection, args,
                null, null, "created DESC")) {
            while (c.moveToNext()) {
                Thought t = new Thought();
                t.id = c.getLong(0);
                t.text = c.getString(1);
                t.created = c.getLong(2);
                t.remindAt = c.getLong(3);
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
        return cv;
    }
}
