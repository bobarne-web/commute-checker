# Commute Checker

An Android app for Galaxy S25 Ultra that automatically checks travel times when you connect to Android Auto.

## Features

- **Auto-detect Android Auto connection** — triggers when you plug your phone into your car's USB port
- **Location-aware routing** — checks travel time to home when at work, and to work when at home
- **Smart scheduling** — configure which days and times to check home→work travel (for when you rarely go into the office)
- **Work→Home always on** — always shows travel time home when you're leaving work
- **Live traffic data** — uses Google Maps Directions API with real-time traffic
- **Configurable geofence radius** — adjust how close you need to be to be considered "at" a location
- **Map-based location picker** — tap the map or use your current location to set home/work

## How It Works

1. You plug your Galaxy S25 Ultra into your car via USB (Android Auto)
2. The app detects the USB power connection
3. It checks your GPS location against your saved home and work locations
4. Based on where you are and the current day/time schedule:
   - **At work** → shows travel time to home (always, unless disabled)
   - **At home** → shows travel time to work (only on scheduled days/times)
   - **Elsewhere** → skips the check
5. A notification appears with the travel time including traffic conditions

## Setup

### Prerequisites

- Android Studio Hedgehog or later
- Google Maps API key with **Directions API** enabled
- Galaxy S25 Ultra (or any Android 10+ device)

### Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project or select an existing one
3. Enable the **Directions API** and **Maps SDK for Android**
4. Create an API key and restrict it to your app's package name
5. Enter the key in the app's Settings screen, or add it to `local.properties`:
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

## Configuration

After installing, open the app and configure:

1. **Home Location** — tap the map or use "Use Current" to set your home
2. **Work Location** — same for your workplace
3. **Home→Work Schedule** — select which days and the time window for checking travel to work
4. **Work→Home** — toggle to always check (enabled by default)
5. **Detection Radius** — how close (in meters) to be considered "at" a location (default: 500m)
6. **Google Maps API Key** — enter your Directions API key

## Architecture

- **`AndroidAutoReceiver`** — BroadcastReceiver listening for USB power connect/disconnect
- **`CommuteCheckService`** — Foreground service that gets location, determines if at home/work, checks schedule, and fetches travel time
- **`DirectionsApiClient`** — OkHttp-based client for Google Maps Directions API
- **`NotificationHelper`** — Handles notification channels and travel time display
- **`PreferencesManager`** — SharedPreferences wrapper for all settings
- **`LocationPickerActivity`** — Google Maps-based location selector

## Permissions

- **Fine/Coarse Location** — to determine if you're at home or work
- **Background Location** — to check location when triggered by USB connection
- **Internet** — to call the Directions API
- **Foreground Service (Location)** — to run the commute check
- **Post Notifications** — to show travel time results
- **Receive Boot Completed** — to ensure receivers are active after reboot
