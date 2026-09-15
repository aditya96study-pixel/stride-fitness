# Stride

A private running, weight, nutrition and hydration tracker for Android. Everything
is stored on the phone — no account, no server, no analytics, and nothing running
in the background except while a run is actually being recorded.

---

## Getting the APK

### Option A — let GitHub build it (nothing to install)

1. Create a new **empty** repository on github.com (private is fine). Do not add a
   README or .gitignore — the upload works best into an empty repo.
2. On the new repo's page click **uploading an existing file**, then drag this
   entire project folder in. Wait for every file to finish, then commit.
3. Open the **Actions** tab. A run called *Build APK* starts on its own; it takes
   roughly four minutes.
4. When it turns green, open the run and download the **stride-apk** artifact at
   the bottom. It contains `stride.apk`.

### Option B — build it locally

Android Studio is not required; a JDK and the command-line tools are enough.

```bash
# needs JDK 17 and the Android SDK (ANDROID_HOME set, platform 35 + build-tools 35)
./gradlew assembleRelease
# the APK lands in app/build/outputs/apk/release/app-release.apk
```

With Android Studio installed, just open the folder and press Run.

### Installing on the phone

The APK is signed with the standard debug key, so Android treats it as an app from
an unknown source. Copy it to the phone, tap it, and allow installation when
prompted. That prompt appears once.

---

## What is in it

**Running.** Distance, live pace, average pace, speed, climb, calories and
per-kilometre splits, with the route drawn on an OpenStreetMap map afterwards.
Location comes from Google's fused provider at the highest accuracy setting,
which blends GPS with the phone's other sensors.

Raw GPS is noisy — fixes drift while you stand still and occasionally jump — and
unfiltered noise is why a phone often claims 5.4 km for a 5.0 km run. Three stages
clean it up:

1. fixes reported less accurate than your chosen limit are discarded outright, as
   is anything implying a speed no runner reaches;
2. a Kalman filter smooths latitude and longitude, weighting each fix by its own
   reported accuracy;
3. a two-metre deadband stops standing still from accumulating distance.

Calories use the ACSM walking and running equations, which take speed, gradient
and your logged body weight, rather than a flat kcal-per-kilometre figure. You can
override the number afterwards.

**Weight, food, water, workouts.** Each has its own screen with a day stepper, so
a missed day can be filled in later. Nothing assumes daily entry.

**Charts.** Every chart holds your whole history and supports:

- **pinch** to zoom the time axis around your fingers
- **touch and hold** to snap a crosshair to the nearest day and read the exact
  value; slide sideways to scan
- **flick sideways** to pan
- **double tap** to return to the default window

Date labels adapt as you zoom — individual days, then weeks, months and quarters.
Gaps in the weight line are drawn dashed rather than pretending the trend
continued through them.

**Energy balance.** The Today screen shows calories eaten against calories burned,
where burned is your resting metabolism (Mifflin–St Jeor) times 1.2 for ordinary
daily living, plus exercise you actually logged. Exercise is never double-counted
through an activity multiplier.

**Reminders.** Set as many as you like, each with its own time, days of the week
and subject. They are ordinary alarms — the app is not resident between them — and
tapping one opens the matching logging screen.

**Export.** One CSV with every weight reading, meal, glass of water, workout and
run.

---

## Battery and permissions

- Location is requested the first time you press **Start run**, never at launch.
- A foreground service exists only while a run is recording, so sampling stays
  steady with the screen off. It stops completely when you finish or discard.
- No background location permission is requested, and none is needed.
- Map tiles are fetched only on the run detail screen, never while running.

---

## Project layout

```
app/src/main/java/com/aditya/stride/
  data/        Room entities, DAOs, repository, profile store
  tracking/    foreground service, GPS filtering, ACSM calorie maths
  notify/      reminder alarms, notification channels, boot rescheduling
  export/      CSV export
  ui/
    components/  the zoomable chart, route views, shared widgets
    screens/     dashboard, run, log, trends, profile, reminders
    theme/       colours, typography
    vm/          view models
```

Series colours were validated against the dark chart surface for lightness,
chroma, colour-vision-deficiency separation and contrast; do not re-order or
substitute them without re-running that check.
