package com.dudai.thoughtbank;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class Formats {

    private Formats() {}

    static String dateTime(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}
