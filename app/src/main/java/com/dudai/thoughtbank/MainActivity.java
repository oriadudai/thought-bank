package com.dudai.thoughtbank;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Main screen: searchable list of all thoughts + "add" button. */
public class MainActivity extends Activity {

    private ThoughtDb db;
    private ThoughtAdapter adapter;
    private EditText search;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = ThoughtDb.get(this);
        Notifications.ensureChannel(this);
        BootReceiver.rescheduleAll(this); // safety net, e.g. after a force-stop

        ListView list = findViewById(R.id.list);
        list.setEmptyView(findViewById(R.id.empty));
        adapter = new ThoughtAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) ->
                startActivity(new Intent(this, EditActivity.class)
                        .putExtra(EditActivity.EXTRA_ID, id)));

        findViewById(R.id.add).setOnClickListener(v ->
                startActivity(new Intent(this, EditActivity.class)));

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

    private void reload() {
        adapter.setItems(db.getAll(search.getText().toString().trim()));
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    /** List adapter: one card per thought. */
    static class ThoughtAdapter extends BaseAdapter {
        private final Context ctx;
        private final LayoutInflater inflater;
        private List<Thought> items = new ArrayList<>();

        ThoughtAdapter(Context ctx) {
            this.ctx = ctx;
            this.inflater = LayoutInflater.from(ctx);
        }

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
                    : inflater.inflate(R.layout.item_thought, parent, false);
            Thought t = getItem(position);

            ((TextView) v.findViewById(R.id.item_text)).setText(t.text);
            ((TextView) v.findViewById(R.id.item_date)).setText(Formats.dateTime(t.created));

            TextView reminder = v.findViewById(R.id.item_reminder);
            if (t.remindAt == 0) {
                reminder.setVisibility(View.GONE);
            } else {
                reminder.setVisibility(View.VISIBLE);
                boolean upcoming = t.remindAt > System.currentTimeMillis();
                reminder.setText(ctx.getString(
                        upcoming ? R.string.item_reminder_upcoming : R.string.item_reminder_done,
                        Formats.dateTime(t.remindAt)));
                reminder.setTextColor(ctx.getColor(upcoming ? R.color.accent : R.color.text_secondary));
            }
            return v;
        }
    }
}
