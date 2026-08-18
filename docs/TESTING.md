# Testing the Commute Checker (without your phone)

There are two layers to test:

- **A. Functional test on the emulator** — verifies Places, Watches, day/time/season rules, travel-time lookups, and the green/red delay coloring in the **notification**. This covers ~90% of the app and takes a few minutes. **Do this first.**
- **B. Car-screen test with the DHU** — the Desktop Head Unit renders the actual Android Auto car screen on your PC so you can see the color-coded dashboard. More setup, and Android Auto on an emulator is finicky.

---

## A. Functional test on the emulator (recommended first)

### 1. Open the project and pick a Google Play emulator
1. In Android Studio: **File → Open** the `commute-checker` folder, let Gradle sync.
2. **Tools → Device Manager → Create Device**. Pick a phone (e.g. **Pixel 8a**).
3. Choose a system image with the **Play Store icon** in the column (this gives Google Play services, needed for location + later Android Auto). Use **API 34 or 35**. Download it if needed, then **Finish**.
4. Start the emulator (▶ in Device Manager).

### 2. Confirm the API key is wired up
1. Open `local.properties` in the project root and confirm it has:
   ```
   MAPS_API_KEY=AIza...your key...
   ```
2. While testing on the emulator, the emulator's signature won't match your API-key restriction. In **Google Cloud Console → Credentials → your key**, temporarily set **Application restrictions = None** (re-tighten later). Keep **Directions API** + **Maps SDK for Android** enabled under API restrictions. Do not commit a real key.

### 3. Build & run
1. Select **app** in the run-config dropdown and your emulator as the target.
2. Click **▶ Run** (Shift+F10). The app installs and launches.
3. Complete first-run setup (API key if needed, Home, a destination, a watch covering now).
4. Grant **while-using** location and **Notifications** when prompted. Background location is explained later, only if automatic car/USB checks need it.

### 4. Give the emulator a GPS location
The app checks "which place am I at?", so set a fake location:
1. Click the **`...`** (Extended controls) on the emulator toolbar → **Location**.
2. Enter a **Work** latitude/longitude (e.g. find it on Google Maps → right-click → copy coords) → **Set Location**.

### 5. Add Places
1. In the app tap **Manage Places → Add Place**.
2. Name it `Work`, keep radius 500m, tap to pick it on the map (or use current location), **Save**.
3. Repeat for `Home` and `Truckee` (use any real coordinates so Directions has somewhere to route).

### 6. Add Watches
1. Tap **Manage Commute Watches → Add Watch**.
2. **To Home**: destination = Home, days = Every day, time window covering *now* (e.g. 12:00 AM–11:59 PM while testing), season = All year, enabled on. **Save**.
3. **To Truckee**: destination = Truckee, season = **Summer only** to see seasonal filtering (it will be skipped outside May–Oct).

### 7. Run a check
1. On the main screen tap **Test Check**.
2. The phone home should list each route as its own row (destination, duration, then an **ON TIME** or **DELAY +N min** badge — not a single emoji blob). The notification uses the same labels:
   - `To Home: 23 min  ON TIME` if extra traffic minutes are **below** the threshold
   - `To Truckee: 58 min  DELAY +14 min` if extra traffic minutes are **≥** the threshold (default 3)
3. Change the emulator location to your **Home** coords and Test Check again — now "To Home" should be skipped (you're already there) and "To Work" appears if you added that watch.

> The comparison is inclusive: `delayMinutes >= threshold`. Threshold `0` marks every successful route as DELAY, including 0 extra minutes.

This confirms the whole engine: place detection, day/time/season gating, parallel travel-time lookups, delay math, and color coding.

---

## B. Car-screen test with the Desktop Head Unit (DHU)

The DHU is a Google tool (not something I build — you install it from the SDK Manager). It shows the real Android Auto car UI on your PC.

### 1. Install the DHU
1. **Android Studio → Settings → Languages & Frameworks → Android SDK → SDK Tools** tab.
2. Check **Android Auto Desktop Head Unit Emulator** → **Apply** (downloads to `…/Android/Sdk/extras/google/auto/`).

### 2. Get Android Auto onto the emulator
Android Auto must be present and in developer mode:
1. On the Play-Store emulator, open **Play Store**, sign in with a Google account, search **Android Auto**, install/update it.
2. Open the **Android Auto** app (or **Settings → Apps → Android Auto**). Scroll to the bottom and tap the **version** line ~10 times to unlock **Developer settings**.
3. In Android Auto's **⋮ → Developer settings**, enable **Unknown sources** (so it can see our sideloaded app) and tap **Start head unit server**.

### 3. Connect the DHU to the emulator
In a terminal:
```bash
# point adb at the emulator and forward the head-unit port
adb forward tcp:5277 tcp:5277

# launch the DHU (Windows: desktop-head-unit.exe)
cd "%LOCALAPPDATA%\Android\Sdk\extras\google\auto"     # Windows
./desktop-head-unit                                     # macOS/Linux
```
A car-head-unit window opens.

### 4. Open Commute Checker on the car screen
1. In the DHU, open the **app launcher** (grid icon).
2. Our app should appear in the launcher (requires "Unknown sources" enabled in Android Auto developer settings). Tap **Commute Checker**.
3. You'll see last-cached rows immediately (if a check has run), then a live refresh. Each row is labeled **ON TIME** or **DELAY +N min**, plus a **Refresh** button.
4. Change the emulator's GPS location (Extended controls → Location) and tap **Refresh** to see the routes recompute from the new origin.

### Important: Physical Car Testing Limitations

This project does not publish to Play. **Apps will NOT appear in physical car launchers when sideloaded.** Android Auto requires Play distribution:
- Google Play Store (production)
- Internal App Sharing (for testing)
- Internal Test Track (for testing)

Debug builds use an open host validator so the DHU works. Release builds allow only the official Android Auto hosts. **Notifications** still work on physical cars even if the launcher doesn't show the app. To test notifications:
1. Use the "Test Commute Check Now" button in the phone app
2. Verify notifications appear on your phone
3. When connected to Android Auto, notifications should also appear on the car display

### Android Auto Implementation Notes

The app uses a **template surface** for the car dashboard and **notifications** for travel time alerts. The app does not specify a category (e.g., POI, Navigation) since it's a utility app that doesn't fit neatly into Android Auto's approved categories. This approach:

- Allows the app to provide a full car-screen dashboard via the template surface
- Ensures notifications appear reliably on both phone and car display
- Works for development/testing with "Unknown sources" enabled
- For production, would need Play Store publication for official launcher support

### Troubleshooting
- **App not in the DHU launcher** → make sure **Unknown sources** is on in Android Auto developer settings, and that the app installed successfully.
- **App not in physical car launcher** → This is expected for sideloaded apps. Android Auto requires Play Store distribution for launcher visibility. Use Internal App Sharing for testing.
- **Notifications not showing on car** → Check notification permissions and ensure the app is allowed to show notifications. Test with "Test Commute Check Now" button.
- **DHU won't connect** → re-run `adb forward tcp:5277 tcp:5277`, confirm `adb devices` lists the emulator, and that you tapped **Start head unit server**.
- **Blank map / no travel times** → API key restriction (set Application restrictions = None while testing) or the Directions/Maps APIs aren't enabled.
- **Android Auto refuses to run on the emulator** → this is the finicky part; the 100% reliable path is the DHU against a **physical phone** with Android Auto in developer mode. Same DHU steps, just skip the Play-Store-on-emulator part.

---

## What can't be tested off a real car
- Auto-trigger on Android Auto / car-mode connect (`CarConnectionMonitor` / `AndroidAutoReceiver`) — on the bench you trigger checks with **Test Check** / **Refresh**.
- Optional USB last-resort trigger — off by default; when enabled it debounces 5 seconds and shares a 10-minute cooldown with the car-connection path.
