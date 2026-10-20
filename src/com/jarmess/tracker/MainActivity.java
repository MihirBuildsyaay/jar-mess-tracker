package com.jarmess.tracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/** One screen: month total, the day-by-day log, and a table of every month. */
public final class MainActivity extends Activity {
    private static final int REQ_EXPORT = 1;
    private static final int REQ_NOTIFICATIONS = 2;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int BG = 0xFFF1F5F9, CARD = Color.WHITE, INK = 0xFF0F172A, MUTED = 0xFF64748B,
            LINE = 0xFFE2E8F0, BRAND = 0xFF0E7490, WATER = 0xFF0284C7, FOOD = 0xFFEA580C,
            WARN = 0xFFB45309, YES = 0xFF15803D;

    /** Table columns: day/month, jars, dinner, money. */
    private static final float[] WEIGHTS = {1.5f, 0.9f, 1.1f, 1.2f};
    private static final int[] ALIGN = {Gravity.START, Gravity.CENTER, Gravity.CENTER, Gravity.END};

    private Db db;
    private YearMonth shown = YearMonth.now();
    private ScrollView scroll;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = Db.get(this);
        Reminder.markFirstUse(this);
        if (state != null) shown = YearMonth.parse(state.getString("shown", shown.toString()));

        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.addView(header());
        scroll = new ScrollView(this);
        content = column();
        content.setPadding(dp(16), dp(8), dp(16), dp(32));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(root);

        Reminder.schedule(this);
        if (state == null) {
            if (db.prices(shown) == null) editPrices(this::askNotificationPermission);
            else askNotificationPermission();
            openDayFrom(getIntent());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openDayFrom(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    /** Also refresh after the notification shade closes: answers may have been tapped there. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("shown", shown.toString());
    }

    /** Tapping the notification opens that day's editor. */
    private void openDayFrom(Intent intent) {
        String day = intent.getStringExtra(Reminder.EXTRA_DAY);
        if (day == null) return;
        intent.removeExtra(Reminder.EXTRA_DAY);
        LocalDate d = LocalDate.parse(day);
        shown = YearMonth.from(d);
        editDay(d);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    // ---- Screen ----

    private void render() {
        content.removeAllViews();
        content.addView(monthBar());
        content.addView(summaryCard());
        content.addView(daysCard());
        content.addView(monthsCard());
    }

    private void showMonth(YearMonth m) {
        shown = m;
        render();
        scroll.scrollTo(0, 0);
    }

    private View header() {
        LinearLayout bar = row();
        bar.setBackgroundColor(BRAND);
        bar.setPadding(dp(16), dp(14), dp(4), dp(14));
        LinearLayout titles = column();
        titles.addView(text("Jar & Mess", 20, Color.WHITE, true));
        titles.addView(text("Water jar + mess dinner tracker", 13, 0xCCFFFFFF, false));
        bar.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(headerAction("Export", v -> export()));
        bar.addView(headerAction("Settings", v -> settings()));
        return bar;
    }

    private TextView headerAction(String label, View.OnClickListener l) {
        TextView t = text(label, 14, Color.WHITE, true);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        tappable(t, l);
        return t;
    }

    private View monthBar() {
        boolean latest = !shown.isBefore(YearMonth.now());
        LinearLayout bar = row();
        bar.setPadding(0, dp(4), 0, 0);
        TextView title = text(Fmt.month(shown), 18, INK, true);
        title.setGravity(Gravity.CENTER);
        bar.addView(arrow("‹", true, v -> showMonth(shown.minusMonths(1))));
        bar.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        bar.addView(arrow("›", !latest, v -> showMonth(shown.plusMonths(1))));
        return bar;
    }

    private TextView arrow(String s, boolean enabled, View.OnClickListener l) {
        TextView t = text(s, 30, enabled ? BRAND : LINE, false);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setMinHeight(dp(48));
        if (enabled) tappable(t, l);
        return t;
    }

    private View summaryCard() {
        Db.Summary s = db.summary(shown);
        boolean priced = db.prices(shown) != null;
        LinearLayout card = card();
        card.addView(costLine("💧 Water jars", s.jars, s.jarPrice, s.jarCost(), WATER));
        card.addView(costLine("🍽 Mess dinners", s.dinners, s.dinnerPrice, s.dinnerCost(), FOOD));
        card.addView(divider());

        LinearLayout total = row();
        total.setPadding(0, dp(8), 0, 0);
        total.addView(text("Total expense", 16, INK, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        total.addView(text(Fmt.rs(s.total()), 24, INK, true));
        card.addView(total);

        TextView prices = text(priced
                ? "Price: " + Fmt.rs(s.jarPrice) + " per jar · " + Fmt.rs(s.dinnerPrice) + " per dinner  ✎"
                : "Set the jar and dinner prices  ✎", 13, BRAND, !priced);
        prices.setPadding(0, dp(10), 0, dp(2));
        tappable(prices, v -> editPrices(null));
        card.addView(prices);

        List<LocalDate> missing = missingDays(shown);
        if (!missing.isEmpty()) {
            TextView warn = text("⚠ " + missing.size() + (missing.size() == 1 ? " day" : " days")
                    + " not filled in yet — tap to fill", 13, WARN, true);
            warn.setPadding(0, dp(8), 0, dp(2));
            tappable(warn, v -> editDay(missing.get(0)));
            card.addView(warn);
        }
        return card;
    }

    private View costLine(String label, int count, double price, double cost, int color) {
        LinearLayout line = row();
        line.setPadding(0, dp(6), 0, dp(6));
        TextView big = text(String.valueOf(count), 26, color, true);
        big.setMinWidth(dp(56));
        line.addView(big);
        LinearLayout mid = column();
        mid.addView(text(label, 15, INK, true));
        mid.addView(text(count + " × " + Fmt.rs(price), 13, MUTED, false));
        line.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));
        line.addView(text(Fmt.rs(cost), 17, INK, true));
        return line;
    }

    /** Days from first use up to yesterday that are missing an answer. */
    private List<LocalDate> missingDays(YearMonth m) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate from = Reminder.since(this);
        LocalDate to = LocalDate.now().minusDays(1);
        if (from.isBefore(m.atDay(1))) from = m.atDay(1);
        if (to.isAfter(m.atEndOfMonth())) to = m.atEndOfMonth();
        Map<LocalDate, int[]> logged = db.month(m);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int[] e = logged.get(d);
            if (e == null || e[0] == Db.UNSET || e[1] == Db.UNSET) out.add(d);
        }
        return out;
    }

    private View daysCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Daily log", "Tap a day to add or change it"));
        card.addView(tableRow(heading("DAY"), heading("JARS"), heading("DINNER"), heading("COST")));

        Db.Summary prices = db.summary(shown);
        Map<LocalDate, int[]> logged = db.month(shown);
        LocalDate today = LocalDate.now();
        LocalDate last = shown.atEndOfMonth().isAfter(today) ? today : shown.atEndOfMonth();
        int[] none = {Db.UNSET, Db.UNSET};
        for (LocalDate d = last; !d.isBefore(shown.atDay(1)); d = d.minusDays(1)) {
            int[] e = logged.containsKey(d) ? logged.get(d) : none;
            card.addView(divider());
            card.addView(dayRow(d, e, prices.jarPrice, prices.dinnerPrice));
        }
        return card;
    }

    private View dayRow(LocalDate d, int[] e, double jarPrice, double dinnerPrice) {
        LocalDate today = LocalDate.now();
        // Blank answers are flagged orange only for past days the app was around for.
        int blank = d.isBefore(today) && !d.isBefore(Reminder.since(this)) ? WARN : MUTED;
        TextView name = text(Fmt.dayShort(d) + (d.equals(today) ? " · Today" : ""), 15, INK, d.equals(today));
        TextView jars = e[0] == Db.UNSET ? text("—", 15, blank, true)
                : text(String.valueOf(e[0]), 15, e[0] > 0 ? WATER : MUTED, e[0] > 0);
        TextView dinner = e[1] == Db.UNSET ? text("—", 15, blank, true)
                : e[1] == 1 ? text("✓ Yes", 15, YES, true) : text("✗ No", 15, MUTED, false);
        boolean any = e[0] != Db.UNSET || e[1] != Db.UNSET;
        double cost = Math.max(e[0], 0) * jarPrice + (e[1] == 1 ? dinnerPrice : 0);
        TextView money = text(any ? Fmt.rs(cost) : "", 15, INK, false);

        LinearLayout r = tableRow(name, jars, dinner, money);
        tappable(r, v -> editDay(d));
        return r;
    }

    private View monthsCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Every month", "Tap a month to see its days"));
        card.addView(tableRow(heading("MONTH"), heading("JARS"), heading("DINNERS"), heading("TOTAL")));
        for (YearMonth m : months()) {
            Db.Summary s = db.summary(m);
            boolean current = m.equals(shown);
            int ink = current ? BRAND : INK;
            card.addView(divider());
            LinearLayout r = tableRow(
                    text(Fmt.monthShort(m), 15, ink, current),
                    text(String.valueOf(s.jars), 15, ink, current),
                    text(String.valueOf(s.dinners), 15, ink, current),
                    text(Fmt.rs(s.total()), 15, ink, true));
            tappable(r, v -> showMonth(m));
            card.addView(r);
        }
        return card;
    }

    /** Months with data plus the current month, newest first. */
    private TreeSet<YearMonth> months() {
        TreeSet<YearMonth> all = new TreeSet<>(Collections.reverseOrder());
        all.addAll(db.months());
        all.add(YearMonth.now());
        return all;
    }

    // ---- Dialogs ----

    private void editDay(LocalDate day) {
        int[] e = db.entry(day);
        int[] jars = {Math.max(e[0], 0)};

        LinearLayout box = column();
        box.setPadding(dp(24), dp(8), dp(24), dp(4));
        box.addView(text("💧 Water jars picked up", 15, INK, true));
        TextView count = text(String.valueOf(jars[0]), 26, WATER, true);
        count.setGravity(Gravity.CENTER);
        LinearLayout stepper = row();
        stepper.setPadding(0, dp(8), 0, dp(18));
        stepper.addView(stepButton("−", v -> {
            if (jars[0] > 0) count.setText(String.valueOf(--jars[0]));
        }));
        stepper.addView(count, new LinearLayout.LayoutParams(dp(64), WRAP));
        stepper.addView(stepButton("+", v -> {
            if (jars[0] < 20) count.setText(String.valueOf(++jars[0]));
        }));
        box.addView(stepper);

        box.addView(text("🍽 Dinner at the mess", 15, INK, true));
        RadioGroup dinner = new RadioGroup(this);
        dinner.setOrientation(RadioGroup.HORIZONTAL);
        dinner.setPadding(0, dp(6), 0, 0);
        RadioButton yes = radio("Yes");
        RadioButton no = radio("No");
        RadioGroup.LayoutParams gap = new RadioGroup.LayoutParams(WRAP, WRAP);
        gap.rightMargin = dp(32);
        dinner.addView(yes, gap);
        dinner.addView(no);
        if (e[1] == 1) dinner.check(yes.getId());
        else if (e[1] == 0) dinner.check(no.getId());
        box.addView(dinner);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(Fmt.dayLong(day))
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    int checked = dinner.getCheckedRadioButtonId();
                    int ate = checked == yes.getId() ? 1 : checked == no.getId() ? 0 : Db.UNSET;
                    db.setEntry(day, jars[0], ate);
                    Reminder.dismissIfAbout(this, day);
                    render();
                });
        if (e[0] != Db.UNSET || e[1] != Db.UNSET) {
            b.setNeutralButton("Clear", (d, w) -> {
                db.deleteEntry(day);
                render();
            });
        }
        b.show();
    }

    private void editPrices(Runnable then) {
        double[] p = db.prices(shown);
        EditText jar = priceField(p == null ? "" : Fmt.num(p[0]));
        EditText dinner = priceField(p == null ? "" : Fmt.num(p[1]));

        LinearLayout box = column();
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(text("💧 Price of one water jar (₹)", 14, MUTED, false));
        box.addView(jar);
        TextView dinnerLabel = text("🍽 Price of one mess dinner (₹)", 14, MUTED, false);
        dinnerLabel.setPadding(0, dp(12), 0, 0);
        box.addView(dinnerLabel);
        box.addView(dinner);
        TextView note = text("Used for " + Fmt.month(shown) + " and the months after it, until you change it again.",
                12, MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("Prices")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    db.setPrices(shown, amount(jar), amount(dinner));
                    render();
                })
                .setOnDismissListener(d -> {
                    if (then != null) then.run();
                })
                .show();
    }

    private void settings() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("⏰ Daily reminder at " + Fmt.time(Reminder.time(this)) + " — change");
        actions.add(this::pickTime);
        labels.add("🔔 Send a test reminder now");
        actions.add(() -> {
            Reminder.test(this);
            toast("Pull down your notifications");
        });
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            labels.add("⚠ Notifications are off — turn them on");
            actions.add(() -> open(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
        }
        if (!Reminder.canExact(this)) {
            labels.add("⚠ Allow the reminder to ring on time");
            actions.add(() -> open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()))));
        }
        if (!getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())) {
            labels.add("🔋 Stop battery saver from blocking the reminder");
            actions.add(() -> open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()))));
        }
        labels.add("₹ Prices for " + Fmt.month(shown));
        actions.add(() -> editPrices(null));
        labels.add("📄 Export everything to a CSV file (Excel / Sheets)");
        actions.add(this::export);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void pickTime() {
        LocalTime t = Reminder.time(this);
        new TimePickerDialog(this, (v, hour, minute) -> {
            Reminder.setTime(this, hour, minute);
            toast("Reminder set for " + Fmt.time(LocalTime.of(hour, minute)) + " every day — it will ask about "
                    + (hour >= 18 ? "today" : "yesterday"));
        }, t.getHour(), t.getMinute(), false).show();
    }

    // ---- Export ----

    private void export() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("text/csv")
                .putExtra(Intent.EXTRA_TITLE, "jar-mess-log-" + LocalDate.now() + ".csv");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (ActivityNotFoundException e) {
            toast("No file picker on this phone");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != REQ_EXPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            out.write(csv().getBytes(StandardCharsets.UTF_8));
            toast("Exported ✓");
        } catch (Exception e) {
            toast("Export failed: " + e.getMessage());
        }
    }

    private String csv() {
        StringBuilder sb = new StringBuilder("Monthly summary\n")
                .append("Month,Water jars,Price per jar,Jar cost,Mess dinners,Price per dinner,Dinner cost,Total\n");
        for (YearMonth m : months()) {
            Db.Summary s = db.summary(m);
            sb.append(Fmt.month(m)).append(',')
                    .append(s.jars).append(',').append(Fmt.num(s.jarPrice)).append(',').append(Fmt.num(s.jarCost())).append(',')
                    .append(s.dinners).append(',').append(Fmt.num(s.dinnerPrice)).append(',').append(Fmt.num(s.dinnerCost())).append(',')
                    .append(Fmt.num(s.total())).append('\n');
        }
        sb.append("\nDaily log\nDate,Day,Water jars,Dinner at mess\n");
        for (Map.Entry<LocalDate, int[]> row : db.all().entrySet()) {
            int[] e = row.getValue();
            sb.append(row.getKey()).append(',')
                    .append(row.getKey().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)).append(',')
                    .append(e[0] == Db.UNSET ? "" : String.valueOf(e[0])).append(',')
                    .append(e[1] == Db.UNSET ? "" : e[1] == 1 ? "Yes" : "No").append('\n');
        }
        return sb.toString();
    }

    // ---- View helpers ----

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(16));
        c.setBackground(bg);
        c.setElevation(dp(1));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View cardTitle(String title, String hint) {
        LinearLayout box = column();
        box.setPadding(0, 0, 0, dp(6));
        box.addView(text(title, 17, INK, true));
        box.addView(text(hint, 13, MUTED, false));
        return box;
    }

    private LinearLayout tableRow(TextView... cells) {
        LinearLayout r = row();
        r.setPadding(0, dp(10), 0, dp(10));
        for (int i = 0; i < cells.length; i++) {
            cells[i].setGravity(ALIGN[i] | Gravity.CENTER_VERTICAL);
            r.addView(cells[i], new LinearLayout.LayoutParams(0, WRAP, WEIGHTS[i]));
        }
        return r;
    }

    private TextView heading(String s) {
        TextView t = text(s, 11, MUTED, true);
        t.setLetterSpacing(0.06f);
        return t;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(1) / 2)));
        return v;
    }

    private TextView text(CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView stepButton(String s, View.OnClickListener l) {
        TextView t = text(s, 22, BRAND, true);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), LINE);
        t.setBackground(bg);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        tappable(t, l);
        return t;
    }

    private RadioButton radio(String label) {
        RadioButton r = new RadioButton(this);
        r.setId(View.generateViewId());
        r.setText(label);
        r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        return r;
    }

    private EditText priceField(String value) {
        EditText f = new EditText(this);
        f.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        f.setText(value);
        f.setHint("0");
        return f;
    }

    private static double amount(EditText f) {
        try {
            return Math.max(0, Double.parseDouble(f.getText().toString().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void tappable(View v, View.OnClickListener l) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        v.setForeground(getDrawable(tv.resourceId));
        v.setOnClickListener(l);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            toast("Not available on this phone");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
