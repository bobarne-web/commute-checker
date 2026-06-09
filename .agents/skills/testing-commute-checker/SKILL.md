---
name: testing-commute-checker
description: Test the Commute Checker Android app end-to-end on an emulator. Use when verifying UI changes, travel time checks, or Android Auto integration.
---

# Testing Commute Checker

## Prerequisites

- Android SDK with emulator, platform-tools, and build-tools
- An AVD (Android Virtual Device) — API 34 recommended for stability (API 37 can be sluggish)
- Google Maps Directions API key in `local.properties` as `MAPS_API_KEY=<key>`

## Devin Secrets Needed

- `MAPS_API_KEY` — Google Maps Directions API key (baked into APK at build time)

## Environment Setup

```bash
# Build the APK
cd /home/ubuntu/repos/commute-checker
./gradlew assembleDebug

# Start emulator (use API 34 AVD for best performance)
~/android-sdk/emulator/emulator -avd <avd_name> -no-snapshot -no-boot-anim -gpu swiftshader_indirect &

# Wait for boot
~/android-sdk/platform-tools/adb wait-for-device
~/android-sdk/platform-tools/adb shell getprop sys.boot_completed  # should return 1

# Install APK
~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk

# Grant permissions
~/android-sdk/platform-tools/adb shell pm grant com.commutecheck.app android.permission.ACCESS_FINE_LOCATION
~/android-sdk/platform-tools/adb shell pm grant com.commutecheck.app android.permission.ACCESS_COARSE_LOCATION
~/android-sdk/platform-tools/adb shell pm grant com.commutecheck.app android.permission.POST_NOTIFICATIONS

# Launch app
~/android-sdk/platform-tools/adb shell am start -n com.commutecheck.app/.ui.MainActivity
```

## GPS Simulation

Set fake GPS coordinates via adb (note: longitude comes first):

```bash
# Set to Work location (USA Parkway)
~/android-sdk/platform-tools/adb emu geo fix -119.4776 39.5374

# Set to Home location (South Sierra Street)
~/android-sdk/platform-tools/adb emu geo fix -119.7960 39.5097
```

## Key Test Flows

### 1. Places CRUD
- Main screen → MANAGE PLACES → ADD PLACE → enter name/radius → Pick on map → Use Current / tap map → Confirm
- Verify place appears in list with coords and radius
- Edit and Delete buttons work

### 2. Watch Configuration
- Main screen → MANAGE COMMUTE WATCHES → ADD WATCH
- Set name, destination, days, time window, season
- Test day toggle colors: selected = purple, unselected = grey
- Test "Always check" checkbox: should dim and disable days/time/season
- Save and verify watch list description

### 3. Travel Time Check
- Set GPS to a known place location
- Tap TEST COMMUTE CHECK NOW on main screen
- Check notification shade for travel time results
- Green = on time, Red with "+X min" = delayed beyond threshold

### 4. Navigation / Back Buttons
- Verify action bar with ← back arrow on Places, Watches, and WatchEditor screens
- Tapping ← should return to parent screen

## Checking Results via ADB

```bash
# Check which activity is on top
adb shell "dumpsys activity activities | grep topResumedActivity"

# Check notifications
adb shell dumpsys notification --noredact | grep -A 5 "commutecheck"

# Check logcat for API errors
adb logcat -d | grep -i -E "(CommuteEngine|DirectionsApi|REQUEST_DENIED)"
```

## Known Issues & Workarounds

### API Key Restrictions
The Google Maps API key may have "Android apps" restrictions that block emulator requests. The error in logcat will be:
```
Directions API error: REQUEST_DENIED - The provided API key is expired.
```
**Workaround:** Temporarily remove the "Application restrictions" on the API key in Google Cloud Console → APIs & Services → Credentials. Re-add restrictions after testing.

### Emulator Performance
- API 37 emulators can be very sluggish — taps may take 5-10 seconds to register
- API 34 is recommended for smoother testing
- If the emulator freezes, use `adb shell input tap X Y` for more reliable interaction
- Cold boot (`emulator -no-snapshot`) is more reliable than quick boot for testing

### Embedded vs Standalone Emulator
- When the emulator is embedded in Android Studio, Extended Controls (GPS/Location) may not be accessible via the UI
- Use `adb emu geo fix` commands instead for GPS simulation
- Or undock the emulator: File → Settings → Tools → Emulator → uncheck "Launch in Running Devices tool window"

## App Architecture (for context)

- `MainActivity` — main dashboard with Places/Watches counts and test button
- `PlacesActivity` — CRUD for GPS locations
- `WatchesActivity` — list of travel-time rules
- `WatchEditorActivity` — configure destination, days, time, season, always-check
- `SettingsActivity` — delay threshold and API key fallback
- `CommuteEngine` — core logic: GPS → place matching → Directions API → delay calc
- `NotificationHelper` — posts green/red notifications
- `CommuteScreen` — Android Auto car screen (ListTemplate with colored spans)
