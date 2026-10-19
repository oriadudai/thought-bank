package com.dudai.thoughtbank;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Export / import of all thoughts as a CSV file.
 * UTF-8 with BOM so Excel and Google Sheets show Hebrew correctly.
 * Columns are matched by header name, so reordering columns in a spreadsheet is fine.
 */
final class CsvBackup {

    private static final String[] HEADER =
            {"text", "category", "created", "reminder", "repeat", "repeat_days", "done", "pinned"};
    private static final String[] REPEAT_NAMES = {"none", "daily", "weekly", "monthly"};
    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm",
            "dd.MM.yyyy HH:mm:ss", "dd.MM.yyyy HH:mm"};
    private static final String LABEL_INSIGHT = "תובנה";
    private static final String LABEL_IDEA = "רעיון";

    private CsvBackup() {}

    // ---------- export ----------

    static void write(List<Thought> thoughts, OutputStream out) throws IOException {
        Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        w.write('﻿');
        writeRow(w, HEADER);
        SimpleDateFormat f = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        for (Thought t : thoughts) {
            int repeat = (t.repeat >= 0 && t.repeat < REPEAT_NAMES.length) ? t.repeat : 0;
            writeRow(w, new String[]{
                    t.text,
                    t.category == Thought.CAT_IDEA ? LABEL_IDEA : LABEL_INSIGHT,
                    f.format(new Date(t.created)),
                    t.remindAt > 0 ? f.format(new Date(t.remindAt)) : "",
                    REPEAT_NAMES[repeat],
                    String.valueOf(t.repeatDays),
                    t.done ? "1" : "0",
                    t.pinned ? "1" : "0"});
        }
        w.flush();
    }

    private static void writeRow(Writer w, String[] cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) w.write(',');
            w.write('"');
            w.write(cells[i].replace("\"", "\"\""));
            w.write('"');
        }
        w.write("\r\n");
    }

    // ---------- import ----------

    static List<Thought> read(InputStream in) throws IOException {
        String content = readAll(in);
        if (content.startsWith("﻿")) content = content.substring(1);
        List<List<String>> rows = parse(content);
        List<Thought> result = new ArrayList<>();
        if (rows.isEmpty()) return result;

        Map<String, Integer> col = new HashMap<>();
        List<String> header = rows.get(0);
        for (int i = 0; i < header.size(); i++) {
            col.put(header.get(i).trim().toLowerCase(Locale.US), i);
        }
        if (!col.containsKey("text")) throw new IOException("No 'text' column");

        long now = System.currentTimeMillis();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Thought t = new Thought();
            t.text = cell(row, col, "text").trim();
            if (t.text.isEmpty()) continue;

            String cat = cell(row, col, "category").trim();
            t.category = (cat.contains(LABEL_IDEA) || cat.equalsIgnoreCase("idea") || cat.equals("1"))
                    ? Thought.CAT_IDEA : Thought.CAT_INSIGHT;
            t.created = parseDate(cell(row, col, "created"), now);
            t.remindAt = parseDate(cell(row, col, "reminder"), 0);
            t.repeat = indexOf(REPEAT_NAMES, cell(row, col, "repeat").trim().toLowerCase(Locale.US));
            t.repeatDays = parseInt(cell(row, col, "repeat_days"));
            t.done = isTrue(cell(row, col, "done"));
            t.pinned = isTrue(cell(row, col, "pinned"));
            if (t.remindAt == 0) t.repeat = Recurrence.NONE;
            result.add(t);
        }
        return result;
    }

    /** RFC 4180 parser: quoted cells may contain commas, quotes ("") and newlines. */
    static List<List<String>> parse(String s) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            addRow(rows, row);
        }
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean blank = row.size() == 1 && row.get(0).trim().isEmpty();
        if (!blank) rows.add(row);
    }

    private static String cell(List<String> row, Map<String, Integer> col, String name) {
        Integer i = col.get(name);
        return (i == null || i >= row.size()) ? "" : row.get(i);
    }

    private static long parseDate(String s, long fallback) {
        s = s.trim();
        if (s.isEmpty()) return fallback;
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
            f.setLenient(false);
            try {
                return f.parse(s).getTime();
            } catch (ParseException ignored) {
                // try the next pattern
            }
        }
        return fallback;
    }

    private static int indexOf(String[] names, String value) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(value)) return i;
        return Recurrence.NONE;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isTrue(String s) {
        s = s.trim();
        return s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("TRUE");
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
