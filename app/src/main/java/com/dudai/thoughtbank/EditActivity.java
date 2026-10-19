package com.dudai.thoughtbank;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

/** Create / edit a thought: text, category, reminder (one-time or repeating), pin. */
public class EditActivity extends Activity {

    public static final String EXTRA_ID = "thought_id";
    public static final String EXTRA_CATEGORY = "category";

    private static final String PREFS = "settings";
    private static final String K_LAST_CATEGORY = "last_category";

    private static final String S_REMIND = "remind_at";
    private static final String S_REPEAT = "repeat";
    private static final String S_DAYS = "days";
    private static final String S_CATEGORY = "category";
    private static final String S_PINNED = "pinned";

    private ThoughtDb db;
    private Thought thought;

    private EditText textInput;
    private TextView reminderLabel;
    private TextView chipInsight;
    private TextView chipIdea;
    private Button repeatButton;
    private View clearButton;

    // Current (unsaved) state
    private long remindAt;
    private int repeat;
    private int repeatDays;
    private int category;
    private boolean pinned;

    // State when the screen opened, to detect changes on "back"
    private String originalText;
    private long originalRemindAt;
    private int originalRepeat;
    private int originalDays;
    private int originalCategory;
    private boolean originalPinned;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_edit);

        db = ThoughtDb.get(this);
        textInput = findViewById(R.id.text);
        reminderLabel = findViewById(R.id.reminder_label);
        chipInsight = findViewById(R.id.chip_insight);
        chipIdea = findViewById(R.id.chip_idea);
        repeatButton = findViewById(R.id.repeat);
        clearButton = findViewById(R.id.clear);

        long id = getIntent().getLongExtra(EXTRA_ID, 0);
        thought = id > 0 ? db.get(id) : null;
        if (thought == null) {
            thought = new Thought();
            int last = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(K_LAST_CATEGORY, Thought.CAT_INSIGHT);
            thought.category = getIntent().getIntExtra(EXTRA_CATEGORY, last);
        }

        originalText = thought.text;
        originalRemindAt = thought.remindAt;
        originalRepeat = thought.repeat;
        originalDays = thought.repeatDays;
        originalCategory = thought.category;
        originalPinned = thought.pinned;

        // Text shared from another app (Share → בנק מחשבות)
        if (Intent.ACTION_SEND.equals(getIntent().getAction())) {
            String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) thought.text = shared;
        }

        if (state == null) {
            textInput.setText(thought.text);
            textInput.setSelection(textInput.length());
            remindAt = thought.remindAt;
            repeat = thought.repeat;
            repeatDays = thought.repeatDays;
            category = thought.category;
            pinned = thought.pinned;
        } else {
            remindAt = state.getLong(S_REMIND);
            repeat = state.getInt(S_REPEAT);
            repeatDays = state.getInt(S_DAYS);
            category = state.getInt(S_CATEGORY);
            pinned = state.getBoolean(S_PINNED);
        }

        // A repeating reminder whose time already passed → show the next one
        long now = System.currentTimeMillis();
        if (repeat != Recurrence.NONE && remindAt > 0 && remindAt <= now) {
            remindAt = Recurrence.first(remindAt, repeat, repeatDays, now);
        }

        setTitle(thought.id > 0 ? R.string.edit_thought : R.string.new_thought);
        ActionBar bar = getActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);

        // Opened from a notification → dismiss it
        if (thought.id > 0) {
            getSystemService(NotificationManager.class).cancel((int) thought.id);
        }

        chipInsight.setOnClickListener(v -> {
            category = Thought.CAT_INSIGHT;
            updateUi();
        });
        chipIdea.setOnClickListener(v -> {
            category = Thought.CAT_IDEA;
            updateUi();
        });

        findViewById(R.id.quick_hour).setOnClickListener(v ->
                setReminder(System.currentTimeMillis() + 60L * 60 * 1000));
        findViewById(R.id.quick_tonight).setOnClickListener(v -> setReminder(nextOccurrence(20, 0)));
        findViewById(R.id.quick_tomorrow).setOnClickListener(v -> setReminder(tomorrowAt(9, 0)));
        findViewById(R.id.pick).setOnClickListener(v -> pickDateTime());
        repeatButton.setOnClickListener(v -> showRepeatDialog());
        clearButton.setOnClickListener(v -> {
            repeat = Recurrence.NONE;
            repeatDays = 0;
            setReminder(0);
        });
        findViewById(R.id.save).setOnClickListener(v -> save());

        View delete = findViewById(R.id.delete);
        if (thought.id > 0) {
            delete.setOnClickListener(v -> confirmDelete());
        } else {
            delete.setVisibility(View.GONE);
        }

        updateUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(S_REMIND, remindAt);
        outState.putInt(S_REPEAT, repeat);
        outState.putInt(S_DAYS, repeatDays);
        outState.putInt(S_CATEGORY, category);
        outState.putBoolean(S_PINNED, pinned);
    }

    // ---------- UI ----------

    private void updateUi() {
        chipInsight.setSelected(category == Thought.CAT_INSIGHT);
        chipIdea.setSelected(category == Thought.CAT_IDEA);

        String[] options = getResources().getStringArray(R.array.repeat_options);
        repeatButton.setText(getString(R.string.repeat_button, options[repeat]));

        long now = System.currentTimeMillis();
        if (remindAt == 0) {
            reminderLabel.setText(R.string.reminder_none);
            clearButton.setVisibility(View.INVISIBLE);
        } else {
            clearButton.setVisibility(View.VISIBLE);
            if (repeat != Recurrence.NONE) {
                reminderLabel.setText(getString(R.string.reminder_repeat,
                        Recurrence.describe(this, repeat, repeatDays, remindAt),
                        Formats.dateTime(remindAt)));
            } else {
                reminderLabel.setText(getString(
                        remindAt > now ? R.string.reminder_at : R.string.reminder_passed,
                        Formats.dateTime(remindAt)));
            }
        }
        invalidateOptionsMenu();
    }

    // ---------- reminder ----------

    private void setReminder(long when) {
        long now = System.currentTimeMillis();
        if (when != 0 && repeat != Recurrence.NONE) {
            when = Recurrence.first(when, repeat, repeatDays, now);
        }
        if (when != 0 && when <= now) {
            toast(R.string.time_passed);
            return;
        }
        remindAt = when;
        updateUi();
    }

    private void pickDateTime() {
        long now = System.currentTimeMillis();
        final Calendar c = Calendar.getInstance();
        if (remindAt > now) {
            c.setTimeInMillis(remindAt);
        } else {
            c.add(Calendar.HOUR_OF_DAY, 1);
            c.set(Calendar.MINUTE, 0);
        }

        // Daily / weekly: the day comes from the rule, so only the time is needed
        if (repeat == Recurrence.DAILY || repeat == Recurrence.WEEKLY) {
            pickTime(c);
            return;
        }

        DatePickerDialog dateDialog = new DatePickerDialog(this, (dp, year, month, day) -> {
            c.set(year, month, day);
            pickTime(c);
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dateDialog.getDatePicker().setMinDate(now - 1000);
        dateDialog.show();
    }

    private void pickTime(final Calendar c) {
        new TimePickerDialog(this, (tp, hour, minute) -> {
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            setReminder(c.getTimeInMillis());
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(this)).show();
    }

    private void showRepeatDialog() {
        String[] options = getResources().getStringArray(R.array.repeat_options);
        new AlertDialog.Builder(this)
                .setTitle(R.string.repeat_title)
                .setSingleChoiceItems(options, repeat, (d, which) -> {
                    d.dismiss();
                    if (which == Recurrence.WEEKLY) {
                        pickWeekDays();
                    } else {
                        applyRepeat(which, 0);
                    }
                })
                .show();
    }

    private void pickWeekDays() {
        String[] names = Recurrence.fullDayNames();
        final boolean[] checked = new boolean[7];
        int mask = repeatDays;
        if (mask == 0) {
            Calendar c = Calendar.getInstance();
            if (remindAt > 0) c.setTimeInMillis(remindAt);
            mask = Recurrence.bit(c.get(Calendar.DAY_OF_WEEK));
        }
        for (int i = 0; i < 7; i++) checked[i] = (mask & (1 << i)) != 0;

        new AlertDialog.Builder(this)
                .setTitle(R.string.pick_days)
                .setMultiChoiceItems(names, checked, (d, i, isChecked) -> checked[i] = isChecked)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    int m = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) m |= 1 << i;
                    if (m == 0) {
                        toast(R.string.no_days);
                        return;
                    }
                    applyRepeat(Recurrence.WEEKLY, m);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyRepeat(int newRepeat, int days) {
        repeat = newRepeat;
        repeatDays = newRepeat == Recurrence.WEEKLY ? days : 0;
        if (repeat != Recurrence.NONE) {
            if (remindAt == 0) {
                pickDateTime();          // a repeating reminder needs a time
            } else {
                setReminder(remindAt);   // re-align the existing time to the new rule
            }
        }
        updateUi();
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
        long now = System.currentTimeMillis();
        if (repeat != Recurrence.NONE && remindAt > 0) {
            remindAt = Recurrence.first(remindAt, repeat, repeatDays, now);
        }

        thought.text = text;
        thought.category = category;
        thought.pinned = pinned;
        thought.remindAt = remindAt;
        thought.repeat = remindAt == 0 ? Recurrence.NONE : repeat;
        thought.repeatDays = thought.repeat == Recurrence.WEEKLY ? repeatDays : 0;

        if (thought.id == 0) {
            thought.created = now;
            thought.id = db.insert(thought);
        } else {
            db.update(thought);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(K_LAST_CATEGORY, category).apply();

        if (remindAt > now) {
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
                    getSystemService(NotificationManager.class).cancel((int) thought.id);
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
        boolean changed = !text.equals(originalText.trim())
                || remindAt != originalRemindAt
                || repeat != originalRepeat
                || repeatDays != originalDays
                || category != originalCategory
                || pinned != originalPinned;
        if (changed && !text.isEmpty()) {
            toast(R.string.saved);
            save();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- menu: pin ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.edit, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem pin = menu.findItem(R.id.menu_pin);
        if (pin != null) pin.setTitle(pinned ? R.string.unpin : R.string.pin);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        if (id == R.id.menu_pin) {
            pinned = !pinned;
            invalidateOptionsMenu();
            toast(pinned ? R.string.pinned_toast : R.string.unpinned_toast);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
