# Workout Tracker

Log your sets on your **Android phone** at the gym, then analyse your progress with charts on your **PC**, in the same app.
It's built with Kotlin + Compose Multiplatform, so the same code runs on Android and on desktop (Windows, macOS, Linux).

- **Works offline.** Everything is saved on the device first. Changes sync through your own Supabase project whenever there's a connection.
- **"How did it go?" + suggestions.** Rate each exercise Good / Okay / Bad. Next time the card shows last
  time's sets with that rating, plus a suggested weight × reps based on your rep range and recent ratings.
- **Fast logging.** Start from a routine (e.g. Push/Pull/Legs) or an empty workout. Weight and reps are pre-filled from your last set or from last time. Use the big −/+ buttons, then tap **Log set**. A rest timer runs between sets.
- **Progress & analysis:**
  - estimated 1RM, top weight, volume and max-reps charts per exercise
  - trend in kg/week
  - personal records
  - weekly volume
  - sets per muscle group
  - workout streak
  - PR stars in your history

## Screens

| Tab | What it's for |
| --- | --- |
| **Workout** | Start a workout, log sets, finish. |
| **History** | Past workouts by month. Tap one to fix or delete sets. |
| **Progress** | Charts and stats, with time ranges 1M, 3M, 6M, 1Y and All. Uses two columns on a PC. |
| **Library** | Exercises, including a one-tap starter set, and routines. |
| **Settings** | Supabase connection and sync status. |

## Setup

### 1. Supabase (one time)

1. Create a free project at [supabase.com](https://supabase.com) (or use your existing one).
2. Open **SQL Editor**, paste the contents of [`supabase/schema.sql`](supabase/schema.sql) and run it. It creates the tables and upgrades the old `exercises` table without losing data.
3. Go to **Project Settings → API** and copy the **Project URL** and the **anon public key**.

> This is a single-user app without login: anyone with your anon key can read and write your workout data.
> Keep the key private.

### 2. PC app

Download the Windows installer (`.msi`) from the latest [release](../../releases/latest), or run from source:

```bash
./gradlew :composeApp:run
```

You can also put the credentials in `local.properties` in the project root. They then become the defaults for any build made on that machine:

```properties
supabase.url=https://xyzcompany.supabase.co
supabase.key=your-anon-key
```

Otherwise open **Settings** in the app and paste the URL and key. Desktop data is stored in `~/.workout-tracker/workout.db`.

### 3. Android app

On your phone, download the APK from the latest release:
**[WorkoutTracker.apk](../../releases/latest/download/WorkoutTracker.apk)** (or see [Releases](../../releases)).
Open it to install. You'll need to allow installing from unknown sources.
Then open **Settings** in the app and paste the same URL and key. To build it yourself:

```bash
./gradlew :composeApp:assembleRelease   # composeApp/build/outputs/apk/release/
```

Or open the project in Android Studio and run the `composeApp` configuration.
The APK is signed with a fixed key (`composeApp/release.keystore`), so new builds install as an update over the old one.
Versions built before 1.1.0 used a random key. Uninstall those once, then reinstall and enter the Supabase settings again: your synced data downloads back.

## How suggestions work

Each exercise has a target rep range (default 8–12) and a weight step (default 2.5 kg), editable in
**Library → Exercises**. Based on the previous session's working sets (the sets at the top weight) and its rating:

| Last time | Suggestion |
| --- | --- |
| Max reps on every set, Good or Okay | Add one weight step and go back to the min reps |
| Good | Same weight, one more rep |
| Okay (or not rated) | Same weight, match your best set on every set |
| Bad | Repeat the same weight and reps |
| Bad and below the min reps | One weight step lighter |
| Bad twice in a row | Drop about 10% and rebuild |

Bodyweight exercises (logged with 0 kg) progress by reps.

> After updating the app, run `supabase/schema.sql` again. It adds the new columns and the `exercise_ratings` table.
> Until then, Settings shows "Supabase tables are out of date".

## Publishing a new version

1. Bump `versionCode`/`versionName` (and `packageVersion`) in `composeApp/build.gradle.kts`.
2. Push a tag: `git tag v1.2.0 && git push origin v1.2.0`.
3. GitHub Actions builds the app and creates a release with `WorkoutTracker.apk` and the `.msi` attached.

## How sync works

Every row (exercise, routine, workout, set) has a random UUID, so devices can create data offline without conflicts.
Local edits are marked *pending*. The app uploads them about 2 seconds after an edit, every 2 minutes, when the Android app comes back to the foreground, and when you tap the sync chip.
Then it downloads anything changed on the server since the last sync. A database trigger stamps changes with `server_updated_at` for this.
Deletes are soft deletes, so they sync too. If the same item is edited on two devices, the newest edit wins.

## Project structure

```
composeApp/src/
  commonMain/            shared code (all UI and logic)
    sqldelight/db/       local SQLite schema + queries
    kotlin/analytics/    pure progress calculations (1RM, trends, PRs, weekly stats)
    kotlin/repository/   offline-first repository, settings, sync (Supabase)
    kotlin/ui/           screens, components, charts, theme
  androidMain/           Android entry point (MainActivity)
  desktopMain/           desktop entry point (Main.kt)
  commonTest/ desktopTest/  unit tests, sync tests with a fake server, screenshot rendering
supabase/schema.sql      cloud database schema
```

Run the tests with:

```bash
./gradlew :composeApp:desktopTest
```

Screenshots of the main screens are rendered to `composeApp/build/screenshots/`.
