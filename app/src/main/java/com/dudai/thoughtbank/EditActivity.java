package com.dudai.thoughtbank;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/** Create / edit a single thought and its reminder. */
public class EditActivity extends Activity {

    public static final String EXTRA_ID = "thought_id";
    private static final String STATE_REMIND_AT = "remind_at";

    private ThoughtDb db;
    private Thought thought;
    private EditText textInput;
    private TextView reminderLabel;
    private View clearButton;

    private long remindAt;
    private String originalText;
    private long originalRemindAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        db = ThoughtDb.get(this);
        textInput = findViewById(R.id.text);
        reminderLabel = findViewById(R.id.reminder_label);
        clearButton = findViewById(R.id.clear);

        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        thought = id > 0 ? db.get(id) : null;
        if (thought == null) thought = new Thought();

        originalText = thought.text;
        originalRemindAt = thought.remindAt;

        // Text shared from another app (Share → בנק מחשבות)
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) thought.text = shared;
        }

        if (savedInstanceState == null) {
            textInput.setText(thought.text);
            textInput.setSelection(textInput.length());
            remindAt = thought.remindAt;
        } else {
            remindAt = savedInstanceState.getLong(STATE_REMIND_AT, thought.remindAt);
        }

        setTitle(thought.id > 0 ? R.string.edit_thought : R.string.new_thought);
        ActionBar bar = getActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);

        // Opened from the notification → dismiss it
        if (thought.id > 0) {
            getSystemService(NotificationManager.class).cancel((int) thought.id);
        }

        findViewById(R.id.quick_hour).setOnClickListener(v ->
                setReminder(System.currentTimeMillis() + 60L * 60 * 1000));
        findViewById(R.id.quick_tonight).setOnClickListener(v -> setReminder(nextOccurrence(20, 0)));
        findViewById(R.id.quick_tomorrow).setOnClickListener(v -> setReminder(tomorrowAt(9, 0)));
        findViewById(R.id.pick).setOnClickListener(v -> pickDateTime());
        clearButton.setOnClickListener(v -> setReminder(0));
        findViewById(R.id.save).setOnClickListener(v -> save());

        View delete = findViewById(R.id.delete);
        if (thought.id > 0) {
            delete.setOnClickListener(v -> confirmDelete());
        } else {
            delete.setVisibility(View.GONE);
        }

        updateReminderLabel();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_REMIND_AT, remindAt);
    }

    // ---------- reminder ----------

    private void setReminder(long when) {
        if (when != 0 && when <= System.currentTimeMillis()) {
            toast(R.string.time_passed);
            return;
        }
        remindAt = when;
        updateReminderLabel();
    }

    private void updateReminderLabel() {
        if (remindAt == 0) {
            reminderLabel.setText(R.string.reminder_none);
            clearButton.setVisibility(View.GONE);
        } else {
            boolean upcoming = remindAt > System.currentTimeMillis();
            reminderLabel.setText(getString(
                    upcoming ? R.string.reminder_at : R.string.reminder_passed,
                    Formats.dateTime(remindAt)));
            clearButton.setVisibility(View.VISIBLE);
        }
    }

    private void pickDateTime() {
        final Calendar c = Calendar.getInstance();
        if (remindAt > System.currentTimeMillis()) {
            c.setTimeInMillis(remindAt);
        } else {
            c.add(Calendar.HOUR_OF_DAY, 1);
            c.set(Calendar.MINUTE, 0);
        }

        DatePickerDialog dateDialog = new DatePickerDialog(this, (dp, year, month, day) -> {
            c.set(year, month, day);
            new TimePickerDialog(this, (tp, hour, minute) -> {
                c.set(Calendar.HOUR_OF_DAY, hour);
                c.set(Calendar.MINUTE, minute);
                c.set(Calendar.SECOND, 0);
                c.set(Calendar.MILLISECOND, 0);
                setReminder(c.getTimeInMillis());
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(this)).show();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dateDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dateDialog.show();
    }

    /** Today at hh:mm, or tomorrow if that time already passed. */
    private static long nextOccurrence(int hour, int minute) {
        Calendar c = atTime(Calendar.getInstance(), hour, minute);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    private static long tomorrowAt(int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 1);
        return atTime(c, hour, minute).getTimeInMillis();
    }

    private static Calendar atTime(Calendar c, int hour, int minute) {
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    // ---------- save / delete ----------

    private void save() {
        String text = textInput.getText().toString().trim();
        if (text.isEmpty()) {
            toast(R.string.empty_thought);
            return;
        }
        thought.text = text;
        thought.remindAt = remindAt;

        if (thought.id == 0) {
            thought.created = System.currentTimeMillis();
            thought.id = db.insert(thought);
        } else {
            db.update(thought);
        }

        if (remindAt > System.currentTimeMillis()) {
            if (!ReminderScheduler.schedule(this, thought)) toast(R.string.exact_alarm_off);
        } else {
            ReminderScheduler.cancel(this, thought.id);
        }
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    ReminderScheduler.cancel(this, thought.id);
                    db.delete(thought.id);
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Back = save automatically if something changed, so a thought is never lost. */
    @Override
    public void onBackPressed() {
        String text = textInput.getText().toString().trim();
        boolean changed = !text.equals(originalText.trim()) || remindAt != originalRemindAt;
        if (changed && !text.isEmpty()) {
            toast(R.string.saved);
            save();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
