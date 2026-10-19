package com.dudai.thoughtbank;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Main screen: category tabs, search, list of thoughts, "add" button. */
public class MainActivity extends Activity {

    private static final String STATE_FILTER = "filter";
    private static final int REQ_EXPORT = 10;
    private static final int REQ_IMPORT = 11;

    private ThoughtDb db;
    private ThoughtAdapter adapter;
    private EditText search;
    private TextView[] tabs;
    private int filter = -1; // -1 = all, otherwise Thought.CAT_*

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = ThoughtDb.get(this);
        Notifications.ensureChannel(this);
        BootReceiver.rescheduleAll(this); // safety net, e.g. after a force-stop
        if (savedInstanceState != null) filter = savedInstanceState.getInt(STATE_FILTER, -1);

        ListView list = findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.empty));
        adapter = new ThoughtAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, EditActivity.class).putExtra(EditActivity.EXTRA_ID, id)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            togglePin(adapter.getItem(position));
            return true;
        });

        findViewById(R.id.add).setOnClickListener(v -> {
            Intent i = new Intent(this, EditActivity.class);
            if (filter >= 0) i.putExtra(EditActivity.EXTRA_CATEGORY, filter);
            startActivity(i);
        });

        // Tab index 0 = all (-1), 1 = insights (0), 2 = ideas (1)
        tabs = new TextView[]{findViewById(R.id.tab_all), findViewById(R.id.tab_insights), findViewById(R.id.tab_ideas)};
        for (int i = 0; i < tabs.length; i++) {
            final int tabFilter = i - 1;
            tabs[i].setOnClickListener(v -> {
                filter = tabFilter;
                updateTabs();
                reload();
            });
        }
        updateTabs();

        search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { reload(); }
        });

        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_FILTER, filter);
    }

    private void reload() {
        adapter.setItems(db.getAll(search.getText().toString().trim(), filter));
    }

    private void updateTabs() {
        for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i - 1 == filter);
    }

    private void togglePin(Thought t) {
        t.pinned = !t.pinned;
        db.update(t);
        toast(t.pinned ? R.string.pinned_toast : R.string.unpinned_toast);
        reload();
    }

    private void setDone(Thought t, boolean done) {
        t.done = done;
        if (done) {
            ReminderScheduler.cancel(this, t.id);
            t.remindAt = 0;
            t.repeat = Recurrence.NONE;
        }
        db.update(t);
        reload();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    // ---------- menu: random insights settings ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_stream) {
            showStreamSettings();
            return true;
        }
        if (id == R.id.menu_export) {
            String name = "thought-bank-"
                    + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".csv";
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/csv")
                    .putExtra(Intent.EXTRA_TITLE, name);
            startActivityForResult(i, REQ_EXPORT);
            return true;
        }
        if (id == R.id.menu_import) {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "text/csv", "text/comma-separated-values", "text/plain",
                            "application/csv", "application/vnd.ms-excel"});
            startActivityForResult(i, REQ_IMPORT);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------- CSV backup ----------

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_EXPORT) exportTo(data.getData());
        else if (requestCode == REQ_IMPORT) importFrom(data.getData());
    }

    private void exportTo(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("No output stream");
            List<Thought> all = db.getAll(null, -1);
            CsvBackup.write(all, out);
            toast(getString(R.string.export_done, all.size()));
        } catch (Exception e) {
            toast(getString(R.string.export_failed));
        }
    }

    /** Adds thoughts from the file. Ones that already exist (same text + creation time) are skipped. */
    private void importFrom(Uri uri) {
        List<Thought> incoming;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("No input stream");
            incoming = CsvBackup.read(in);
        } catch (Exception e) {
            toast(getString(R.string.import_failed));
            return;
        }

        Set<String> existing = new HashSet<>();
        for (Thought t : db.getAll(null, -1)) existing.add(dedupeKey(t));
        int added = 0;
        int skipped = 0;
        for (Thought t : incoming) {
            if (existing.add(dedupeKey(t))) {
                t.id = db.insert(t);
                added++;
            } else {
                skipped++;
            }
        }
        BootReceiver.rescheduleAll(this);
        reload();
        toast(skipped == 0
                ? getString(R.string.import_done, added)
                : getString(R.string.import_done_skipped, added, skipped));
    }

    private static String dedupeKey(Thought t) {
        return (t.created / 1000) + "|" + t.text.trim();
    }

    private void showStreamSettings() {
        View v = getLayoutInflater().inflate(R.layout.dialog_stream, null);
        Switch enabled = v.findViewById(R.id.stream_switch);
        NumberPicker from = v.findViewById(R.id.picker_from);
        NumberPicker to = v.findViewById(R.id.picker_to);
        NumberPicker avg = v.findViewById(R.id.picker_avg);

        NumberPicker.Formatter hourFormat = value -> String.format(Locale.US, "%02d:00", value);
        from.setMinValue(0);
        from.setMaxValue(23);
        from.setFormatter(hourFormat);
        to.setMinValue(1);
        to.setMaxValue(24);
        to.setFormatter(hourFormat);
        avg.setMinValue(1);
        avg.setMaxValue(12);

        enabled.setChecked(InsightStream.enabled(this));
        from.setValue(InsightStream.startHour(this));
        to.setValue(InsightStream.endHour(this));
        avg.setValue(InsightStream.avgHours(this));

        new AlertDialog.Builder(this)
                .setTitle(R.string.stream_title)
                .setView(v)
                .setPositiveButton(R.string.save, (d, w) -> {
                    int start = from.getValue();
                    int end = to.getValue();
                    if (end <= start) {
                        toast(R.string.stream_bad_range);
                        return;
                    }
                    InsightStream.save(this, enabled.isChecked(), start, end, avg.getValue());
                    toast(R.string.saved);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ---------- list ----------

    /** One card per thought: category stripe, text, date, reminder, "done" box for ideas. */
    class ThoughtAdapter extends BaseAdapter {
        private List<Thought> items = new ArrayList<>();

        void setItems(List<Thought> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public Thought getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : getLayoutInflater().inflate(R.layout.item_thought, parent, false);
            Thought t = getItem(position);

            v.findViewById(R.id.item_stripe).setBackgroundTintList(ColorStateList.valueOf(
                    getColor(t.category == Thought.CAT_IDEA ? R.color.cat_idea : R.color.cat_insight)));
            v.findViewById(R.id.item_card).setAlpha(t.done ? 0.55f : 1f);

            TextView text = v.findViewById(R.id.item_text);
            text.setText(t.text);
            int flags = text.getPaintFlags();
            text.setPaintFlags(t.done ? flags | Paint.STRIKE_THRU_TEXT_FLAG : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);

            String date = Formats.dateTime(t.created);
            ((TextView) v.findViewById(R.id.item_date)).setText(t.pinned ? "📌 " + date : date);

            TextView reminder = v.findViewById(R.id.item_reminder);
            if (t.remindAt == 0) {
                reminder.setVisibility(View.GONE);
            } else {
                reminder.setVisibility(View.VISIBLE);
                if (t.repeat != Recurrence.NONE) {
                    reminder.setText("🔁 " + Recurrence.describe(MainActivity.this, t.repeat, t.repeatDays, t.remindAt));
                    reminder.setTextColor(getColor(R.color.accent));
                } else {
                    boolean upcoming = t.remindAt > System.currentTimeMillis();
                    reminder.setText(getString(
                            upcoming ? R.string.item_reminder_upcoming : R.string.item_reminder_done,
                            Formats.dateTime(t.remindAt)));
                    reminder.setTextColor(getColor(upcoming ? R.color.accent : R.color.text_secondary));
                }
            }

            CheckBox doneBox = v.findViewById(R.id.item_done);
            doneBox.setOnCheckedChangeListener(null);
            if (t.category == Thought.CAT_IDEA) {
                doneBox.setVisibility(View.VISIBLE);
                doneBox.setChecked(t.done);
                doneBox.setOnCheckedChangeListener((b, checked) -> setDone(t, checked));
            } else {
                doneBox.setVisibility(View.GONE);
            }
            return v;
        }
    }
}
