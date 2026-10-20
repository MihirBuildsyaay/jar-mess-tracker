# Jar & Mess 💧🍽

> **Why I built this:** I was struggling to count how many water jars I refilled in a month (**₹40 per jar**) and how many days I actually had dinner at the mess (**₹90 per dinner**). At the end of every month I had no idea what I'd spent. So I made this small Android app: every morning it asks me two quick questions, and at month end it shows exactly how many jars, how many dinners, and how much money.
>
> It's built for **students** in hostels and PGs to manage the small daily expenses that quietly add up. Water jars and mess dinners are just the start. The project is meant to **grow to cover your other expenses too**: breakfast, milk, laundry, auto fare, whatever you pay for again and again. Fork it, add yours, and send it back.

---

## What it does

- **A reminder every morning at 9 AM.** It asks *"Did you pick up a water jar yesterday?"* (No / 1 jar / 2 jars), then *"Did you have dinner at the mess last night?"* (Yes / No). You answer straight from the notification, without opening the app.
- **Missed a day?** If your phone was off or you ignored the reminder, it asks about those days too (up to 7 days back).
- **Monthly totals:** jars × price, dinners × price, and the **total expense** for the month.
- **Daily log:** every day of the month in a simple table. Tap any day to fix it.
- **Every month at a glance:** a table of all your months, so you can compare.
- **Export to Excel / Google Sheets:** one CSV file with the monthly summary and the full daily log.
- **Your own prices:** set the jar and dinner price once. If a price changes, set it for that month and older months keep their old price.
- **Reminder time you choose.** A morning reminder asks about yesterday. One set for 6 PM or later asks about today.

Small (about 35 KB), works offline, no account, no ads, no internet permission. Your data stays on your phone.

## Download & install

1. Go to [**Releases**](../../releases/latest) and download `JarMess.apk` on your phone.
2. Open it and allow installing from your browser or file manager when Android asks. Play Protect may warn that it's an unknown app; that's because it isn't on the Play Store. Tap **Install anyway**.
3. Open **Jar & Mess**, enter your jar and dinner prices, and **allow notifications**.
4. In **Settings**, tap **Send a test reminder now** to check that notifications work.
5. On Xiaomi, Oppo, Vivo, Realme and similar phones, tap **Stop battery saver from blocking the reminder** in Settings, and turn on **Autostart** for the app in phone settings. Otherwise the phone may block the 9 AM reminder.

Requires Android 8.0 or newer.

> Your data lives only on the phone. Uninstalling deletes it, so use **Export** now and then as a backup.

## Make it yours: adding another expense

The app is plain Java with no libraries, so every part is easy to find. Today the two expenses are built in. Adding a third one (say, breakfast at ₹30) means touching these files:

| File | What to add |
|---|---|
| [`Db.java`](src/com/jarmess/tracker/Db.java) | A column in the `entries` table (bump the DB version and `ALTER TABLE` in `onUpgrade` so existing data is kept), a price in `prices`, and a count + cost in `Summary`. |
| [`Reminder.java`](src/com/jarmess/tracker/Reminder.java) | A question builder like `dinnerQuestion()` with its own `ACTION_…`, and a step in `ask()` / `advance()` so it's asked after the others. |
| [`AlarmReceiver.java`](src/com/jarmess/tracker/AlarmReceiver.java) | Handle the new `ACTION_…` and save the answer. |
| [`MainActivity.java`](src/com/jarmess/tracker/MainActivity.java) | A `costLine()` in the summary card, a column in the daily log, a control in `editDay()`, a price field in `editPrices()`, and a column in `csv()`. |

### Ideas / roadmap

- [ ] Add your own expense items from inside the app, with no code changes
- [ ] Monthly budget with a warning when you get close
- [ ] Charts of spending month by month
- [ ] Backup and restore (Google Drive / file)
- [ ] Split shared expenses with roommates
- [ ] Hindi and other languages

Pull requests and ideas are welcome. Open an issue and say which expense you want to track.

## Build from source

No Android Studio or Gradle needed. [`build.ps1`](build.ps1) runs the Android SDK tools directly (`aapt2 → javac → d8 → zipalign → apksigner`).

You need:
- **JDK 17**
- **Android SDK** with `build-tools;34.0.0` and `platforms;android-34` (install them with `sdkmanager` from the Android command-line tools)

```powershell
$env:JAVA_HOME    = "C:\path\to\jdk-17"
$env:ANDROID_HOME = "C:\path\to\android-sdk"
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

That produces `JarMess.apk`. The first build creates a signing key, `jarmess.keystore`. Keep it: updates only install over an existing app if they're signed with the same key. It's git-ignored, so it never gets committed.

### Project layout

```
AndroidManifest.xml   permissions, activity, receivers
build.ps1             build script
res/                  icons, colors, theme
src/com/jarmess/tracker/
  MainActivity.java   the screen: totals, daily log, months, dialogs, CSV export
  Reminder.java       daily alarm + notification questions
  AlarmReceiver.java  alarm fire + notification button answers
  SystemReceiver.java re-arms the alarm after reboot / update / clock change
  Db.java             SQLite storage (days + monthly prices)
  Fmt.java            dates and ₹ formatting
```

## License

[MIT](LICENSE): free to use, change and share.
