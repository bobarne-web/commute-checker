# Commute Checker

An Android app for Galaxy S25 Ultra that automatically checks travel times when you connect to Android Auto, and shows a color-coded dashboard on the car screen.

## Features

- **Auto-detect Android Auto connection** — triggers when you plug your phone into your car's USB port
- **Multiple destinations** — save as many places as you like (Home, Work, Truckee, Reno, …) and check travel time to any of them
- **Per-destination watches** — each destination has its own active days, time window, on/off toggle, and seasonal active-months
- **Color-coded delays** — each route shows **green** when on time and **red** with the extra delay minutes when traffic exceeds your threshold (default 3 min)
- **Travel from wherever you are** — times are computed from your current location, so a road closure (e.g. Graeagle → Truckee) shows up as a big delay before you commit to the drive
- **Seasonal watches** — e.g. set the Truckee watch to summer only (May–Oct) so it goes quiet in winter
- **Car-screen dashboard** — a color-coded list of all relevant routes appears on Android Auto
- **Live traffic data** — uses the Google Maps Directions API with real-time traffic

## How It Works

1. You plug your Galaxy S25 Ultra into your car via USB (Android Auto)
2. The app detects the USB power connection and gets your GPS location
3. It figures out which saved **Place** you're at (or "on the road")
4. It evaluates every **Watch** and runs the ones whose day / time / season match right now (skipping any whose destination is where you already are)
5. For each, it fetches travel time from your current location and computes the traffic delay
6. A color-coded summary appears as a notification and on the Android Auto car screen

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
4. Enter the key in the app's Settings screen, or add it to `local.properties`:
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

## Android Auto Testing

For development and testing with sideloaded APKs, you need to enable **Unknown Sources** in Android Auto:

1. Enable Developer Mode in Android Auto:
   - Open Android Auto on your phone
   - Go to Settings > About
   - Tap "Version" 10 times to enable Developer Mode
   - Go back to Settings and you'll see "Developer settings"

2. Enable Unknown Sources:
   - In Developer settings, enable "Unknown sources"
   - This allows sideloaded/debug apps to appear in Android Auto

3. Testing the App:
   - Connect your phone to your car via USB
   - The app should appear in Android Auto
   - Use the "Test Commute Check Now" button in the phone app to trigger notifications
   - Notifications should appear both on your phone and on the car display

**Note**: The app uses a template surface for the car dashboard and notifications for travel time alerts. For production deployment, the app would need to be published to the Play Store as Android Auto only officially supports apps from the store for the launcher.

## Configuration

After installing, open the app and:

1. **Manage Places** — add Home, Work, and any destinations you check often (Truckee, Reno, …). Name each, set a radius, and pick it on the map.
2. **Manage Commute Watches** — add a watch per destination:
   - Pick the destination Place
   - Choose active days (Every day / Weekdays / custom)
   - Set the time window (12-hour AM/PM)
   - Choose a season (All year / Summer only / custom months)
   - Toggle it on/off
3. **Settings** — set your traffic **delay threshold** (default 3 min) and Google Maps API key.

## Architecture

- **`AndroidAutoReceiver`** — BroadcastReceiver listening for USB power connect/disconnect
- **`CommuteCheckService`** — Foreground service that runs the check engine and posts the notification
- **`CommuteEngine`** — Shared logic: detect current place, evaluate active watches, fetch travel times in parallel, compute delays. Used by both the service and the car screen.
- **`DirectionsApiClient`** — OkHttp-based client for the Google Maps Directions API
- **`CommuteScreen` / `CommuteCarAppService`** — the Android Auto car-screen dashboard
- **`NotificationHelper`** — notification channels and color-coded travel-time summary
- **`PreferencesManager`** — SharedPreferences wrapper storing Places, Watches, and settings
- **`PlacesActivity` / `WatchesActivity` / `WatchEditorActivity`** — phone UI for managing places and watches
- **`LocationPickerActivity`** — Google Maps-based location selector

## Permissions

- **Fine/Coarse Location** — to determine which place you're at
- **Background Location** — to check location when triggered by USB connection
- **Internet** — to call the Directions API
- **Foreground Service (Location)** — to run the commute check
- **Post Notifications** — to show travel time results
- **Receive Boot Completed** — to ensure receivers are active after reboot
