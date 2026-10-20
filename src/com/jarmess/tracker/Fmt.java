package com.jarmess.tracker;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Text formatting shared by the screen, the notifications and the CSV export. */
final class Fmt {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private Fmt() {
    }

    static String day(LocalDate d) {
        return d.format(DAY);
    }

    static String dayShort(LocalDate d) {
        return d.format(DAY_SHORT);
    }

    static String dayLong(LocalDate d) {
        return d.format(DAY_LONG);
    }

    static String month(YearMonth m) {
        return m.format(MONTH);
    }

    static String monthShort(YearMonth m) {
        return m.format(MONTH_SHORT);
    }

    static String time(LocalTime t) {
        return t.format(TIME);
    }

    /** "today", "yesterday" or "on Mon, 19 Oct". */
    static String when(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "today";
        if (d.equals(today.minusDays(1))) return "yesterday";
        return "on " + day(d);
    }

    /** "tonight", "last night" or "on Mon, 19 Oct". */
    static String night(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "tonight";
        if (d.equals(today.minusDays(1))) return "last night";
        return "on " + day(d);
    }

    static String jars(int n) {
        return n == 1 ? "1 jar" : n + " jars";
    }

    /** Plain number for CSV: "30" or "27.50". */
    static String num(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.US, "%.2f", v);
    }

    static String rs(double v) {
        return v == Math.rint(v)
                ? String.format(Locale.US, "₹%,d", (long) v)
                : String.format(Locale.US, "₹%,.2f", v);
    }
}
