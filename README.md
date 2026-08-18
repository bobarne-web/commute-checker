# Commute Checker

An Android app for Galaxy S25 Ultra that automatically checks travel times when you connect to Android Auto, and shows a labeled delay dashboard on the car screen.

## Features

- **Android Auto / car-mode trigger** — a check runs when the phone joins a car session. USB power is an optional last-resort fallback with debounce and a 10-minute cooldown.
- **Multiple destinations** — save as many places as you like (Home, Work, Truckee, Reno, …) and check travel time to any of them
- **Per-destination watches** — each destination has its own active days, time window, on/off toggle, and seasonal active-months
- **Glanceable delays** — each route is labeled **ON TIME** or **DELAY +N min**. Color is extra contrast, not the only signal. A route is delayed when extra traffic minutes are **at least** your threshold (default 3; `delay ≥ threshold`).
- **Travel from wherever you are** — times are computed from your current location, so a road closure (e.g. Graeagle → Truckee) shows up as a big delay before you commit to the drive
- **Seasonal watches** — e.g. set the Truckee watch to summer only (May–Oct) so it goes quiet in winter
- **Car-screen dashboard** — last-cached results appear immediately, then live traffic refreshes
- **Live traffic data** — uses the Google Maps Directions API with real-time traffic

## How It Works

1. The phone connects to Android Auto / car mode (or, if you enable it, USB power after a short debounce)
2. The app gets your GPS location
3. It figures out which saved **Place** you're at (or "on the road")
4. It evaluates every **Watch** and runs the ones whose day / time / season match right now (skipping any whose destination is where you already are)
5. For each, it fetches travel time from your current location and computes the traffic delay
6. **ON TIME** / **DELAY +N min** appears as a notification, on the phone home list, and on the Android Auto car screen

## Concepts

- **Place** — a named location with coordinates and a detection radius. Used both to detect where you are and as a destination.
- **Watch** — a rule that checks travel time to a destination Place when the car starts. Each watch has:
  - Active **days** (default every day)
  - A **time window** (e.g. a 3-hour morning window)
  - A master **on/off** toggle
  - **Active months** for seasonal use (All year, Summer only, or custom)

## Setup

### Prerequisites

- Android Studio Hedgehog or later
- Google Maps API key with **Directions API** + **Maps SDK for Android** enabled
- Galaxy S25 Ultra (or any Android 10+ device)
- Android Auto compatible vehicle or head unit

### Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Enable the **Directions API** and **Maps SDK for Android**
3. Create an API key and restrict it to your app's package name + SHA-1
4. Enter the key in first-run setup or Settings. You may also add it to `local.properties` (never commit this file):
   ```
   MAPS_API_KEY=your_api_key_here
   ```

### Building

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Installing

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

On first launch the app walks through API key (if needed), Home, a first destination, and a watch whose time window covers now.

## Android Auto Testing

**Important:** This is a personal app. We do not publish it to Play from this repo.

For the app to appear in the Android Auto **launcher** on a physical car display, Google requires distribution through Play (**Internal Test**, **Internal App Sharing**, or production). A sideloaded or local debug APK typically will **not** appear in a real vehicle launcher. That is an Android Auto restriction, not something this project can bypass.

- **Debug builds** allow any car host (DHU / Unknown sources).
- **Release builds** use the official Android Auto / AAOS host allow-list (`HostValidator` + `hosts_allowlist_sample`).

#### For Development Testing (Desktop Head Unit):

1. Enable Developer Mode in Android Auto (tap Version 10 times), then enable **Unknown sources**.
2. Install the Android Auto Desktop Head Unit from SDK Manager.
3. Run `adb forward tcp:5277 tcp:5277` and launch the DHU.

#### For Physical Car Testing:

Use Play **Internal Test** or **Internal App Sharing** if you need the car launcher icon. **Notifications** still work on a physical car when the app is sideloaded, even if the launcher hides it. Use **Test Commute Check Now** to verify notification delivery.

USB power is **off** unless you turn on **Trigger on USB power** in Settings (debounced, 10-minute cooldown).

## Configuration

After first-run setup you can still:

1. **Manage Places** — add more destinations. Editing a place on the map centers on the saved pin.
2. **Manage Commute Watches** — change days, the 12-hour time window, and season.
3. **Settings** — delay threshold (inclusive), masked API key, optional USB trigger.

Background location is requested only when automatic car/USB checks need it — not during first-run map picking.

## Architecture

- **`AndroidAutoReceiver`** — car-mode / car-connection broadcasts, plus optional USB power with debounce
- **`CarConnectionMonitor`** — observes `CarConnection` while the process is alive
- **`CommuteTrigger`** — shared cooldown before starting `CommuteCheckService`
- **`CommuteCheckService`** — Foreground service that runs the check engine and posts the notification
- **`CommuteEngine` / `DelayMath`** — place detection, watch evaluation, travel times, inclusive delay threshold
- **`DirectionsApiClient`** — OkHttp-based client for the Google Maps Directions API
- **`CommuteScreen` / `CommuteCarAppService`** — car dashboard (cached rows first, then refresh)
- **`NotificationHelper`** — notification channels and ON TIME / DELAY summary
- **`PreferencesManager`** — SharedPreferences for Places, Watches, and settings
- **`SetupActivity`** — first-run API key, Home, destination, watch covering now
- **`PlacesActivity` / `WatchesActivity` / `WatchEditorActivity`** — phone UI for places and watches
- **`LocationPickerActivity`** — Google Maps-based location selector

## Permissions

- **Fine/Coarse Location** — to pick places and to determine which place you're at
- **Background Location** — only for automatic checks when Android Auto (or opted-in USB) fires and the phone UI is not open
- **Internet** — to call the Directions API
- **Foreground Service (Location)** — to run the commute check
- **Post Notifications** — to show travel time results
- **Receive Boot Completed** — to ensure receivers are active after reboot
