package com.jarmess.tracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** One row per day (jars picked up, dinner at mess) plus prices per month. UNSET = not answered yet. */
final class Db extends SQLiteOpenHelper {
    static final int UNSET = -1;

    private static Db instance;

    static synchronized Db get(Context c) {
        if (instance == null) instance = new Db(c.getApplicationContext());
        return instance;
    }

    private Db(Context c) {
        super(c, "tracker.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (day TEXT PRIMARY KEY, jars INTEGER NOT NULL DEFAULT -1, dinner INTEGER NOT NULL DEFAULT -1)");
        db.execSQL("CREATE TABLE prices (month TEXT PRIMARY KEY, jar REAL NOT NULL, dinner REAL NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /** {jars, dinner}; dinner is 1 = ate at mess, 0 = didn't. Either may be UNSET. */
    int[] entry(LocalDate day) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT jars, dinner FROM entries WHERE day = ?", new String[]{day.toString()})) {
            return c.moveToFirst() ? new int[]{c.getInt(0), c.getInt(1)} : new int[]{UNSET, UNSET};
        }
    }

    void setJars(LocalDate day, int jars) {
        setField(day, "jars", jars);
    }

    void setDinner(LocalDate day, int dinner) {
        setField(day, "dinner", dinner);
    }

    private void setField(LocalDate day, String column, int value) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT OR IGNORE INTO entries (day) VALUES (?)", new Object[]{day.toString()});
        db.execSQL("UPDATE entries SET " + column + " = ? WHERE day = ?", new Object[]{value, day.toString()});
    }

    void setEntry(LocalDate day, int jars, int dinner) {
        ContentValues v = new ContentValues();
        v.put("day", day.toString());
        v.put("jars", jars);
        v.put("dinner", dinner);
        getWritableDatabase().insertWithOnConflict("entries", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void deleteEntry(LocalDate day) {
        getWritableDatabase().delete("entries", "day = ?", new String[]{day.toString()});
    }

    Map<LocalDate, int[]> month(YearMonth m) {
        Map<LocalDate, int[]> out = new HashMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT day, jars, dinner FROM entries WHERE day LIKE ?", new String[]{m + "-%"})) {
            while (c.moveToNext()) out.put(LocalDate.parse(c.getString(0)), new int[]{c.getInt(1), c.getInt(2)});
        }
        return out;
    }

    TreeMap<LocalDate, int[]> all() {
        TreeMap<LocalDate, int[]> out = new TreeMap<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT day, jars, dinner FROM entries", null)) {
            while (c.moveToNext()) out.put(LocalDate.parse(c.getString(0)), new int[]{c.getInt(1), c.getInt(2)});
        }
        return out;
    }

    List<YearMonth> months() {
        List<YearMonth> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT substr(day, 1, 7) FROM entries", null)) {
            while (c.moveToNext()) out.add(YearMonth.parse(c.getString(0)));
        }
        return out;
    }

    /**
     * {jar, dinner} price for a month: its own, else the latest earlier month's, else the earliest
     * later one. Null if no price was ever set.
     */
    double[] prices(YearMonth m) {
        SQLiteDatabase db = getReadableDatabase();
        String[] lookups = {
                "SELECT jar, dinner FROM prices WHERE month <= ? ORDER BY month DESC LIMIT 1",
                "SELECT jar, dinner FROM prices WHERE month > ? ORDER BY month ASC LIMIT 1"};
        for (String sql : lookups) {
            try (Cursor c = db.rawQuery(sql, new String[]{m.toString()})) {
                if (c.moveToFirst()) return new double[]{c.getDouble(0), c.getDouble(1)};
            }
        }
        return null;
    }

    void setPrices(YearMonth m, double jar, double dinner) {
        ContentValues v = new ContentValues();
        v.put("month", m.toString());
        v.put("jar", jar);
        v.put("dinner", dinner);
        getWritableDatabase().insertWithOnConflict("prices", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    Summary summary(YearMonth m) {
        Summary s = new Summary();
        for (int[] e : month(m).values()) {
            if (e[0] > 0) s.jars += e[0];
            if (e[1] == 1) s.dinners++;
        }
        double[] p = prices(m);
        if (p != null) {
            s.jarPrice = p[0];
            s.dinnerPrice = p[1];
        }
        return s;
    }

    static final class Summary {
        int jars, dinners;
        double jarPrice, dinnerPrice;

        double jarCost() {
            return jars * jarPrice;
        }

        double dinnerCost() {
            return dinners * dinnerPrice;
        }

        double total() {
            return jarCost() + dinnerCost();
        }
    }
}
