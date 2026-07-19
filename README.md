<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="SHNF Trails icon"/>
  <h1>SHNF Trails</h1>
  <p><strong>Sam Houston National Forest — MUT Trail Status Widget for Android</strong></p>
  <p>
    <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Android 10+"/>
    <img src="https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
    <img src="https://img.shields.io/badge/WorkManager-2.9-0D47A1" alt="WorkManager"/>
    <img src="https://img.shields.io/badge/License-MIT-lightgrey" alt="MIT License"/>
  </p>
</div>

---

A lightweight Android app and home screen widget that checks the status of the **Multi-Use Trail East (MUT-East)** and **Multi-Use Trail West (MUT-West)** in Sam Houston National Forest at [samhoustontrails.com](https://www.samhoustontrails.com) — no browser tab required.

The widget shows trail sign icons colored **green** (open), **red** (closed), or **grey** (unknown/stale), updating automatically in the background without draining your battery.

---

## Screenshots

<p align="center">
  <img src="docs/screenshots/app_screen.png"     width="180" alt="App — status cards and timer"/>
  &nbsp;&nbsp;
  <img src="docs/screenshots/widget_home.png"    width="180" alt="Widget on home screen"/>
  &nbsp;&nbsp;
  <img src="docs/screenshots/widget_picker.png"  width="180" alt="Widget picker with preview"/>
  &nbsp;&nbsp;
  <img src="docs/screenshots/app_drawer.png"     width="180" alt="App icon in launcher"/>
</p>

---

## Features

| | |
|---|---|
| 🪧 **Trail sign widget** | Home screen widget with **E** / **W** flags colored by live status |
| 🔴🟢⚫ **Status colors** | Green = open · Red = closed · Grey = unknown or data stale |
| 🔄 **Background auto-refresh** | WorkManager fires only when network is up and battery is not low |
| ⚙️ **Configurable interval** | 15 min · 30 min · 1 h · 2 h · 4 h |
| ⏱️ **Live timer** | Last-checked age + countdown to next check, refreshing every 30 s |
| 📌 **One-tap widget placement** | "Add Widget to Home Screen" triggers the system pin-widget dialog |
| 🌐 **Quick link** | Opens [samhoustontrails.com](https://www.samhoustontrails.com) in your default browser |
| 🌲 **Adaptive icon** | Forest / SHNF scene; follows your launcher's icon shape |
| 📐 **Responsive widget** | Bitmap redraws at actual cell dimensions; supports resize |

---

## Installing on Your Phone (No Play Store)

Because this app is not published on Google Play, you install it by downloading the APK file directly — a process called **sideloading**. It takes about 2 minutes and only requires tapping through a few Android prompts.

### Step 1 — Download the APK

On your Android phone, open your browser and go to the [**latest release page**](https://github.com/limonspb/SHNF-Trails/releases/latest).

Tap **`SHNF-Trails.apk`** to download it. Your browser may show a warning like *"This type of file can harm your device"* — tap **Download anyway**. This is a standard Android warning shown for all APK files, not specific to this app.

---

### Step 2 — Allow Installation from Unknown Sources

Android blocks apps from outside the Play Store by default. You need to grant your browser (or file manager) permission to install APKs once.

**Android 8 and newer (most phones):**

1. When you tap the downloaded APK, Android will say **"For your security, your phone is not allowed to install unknown apps from this source."**
2. Tap **Settings** in that dialog.
3. Toggle **Allow from this source** to ON.
4. Tap the back button — the install screen will appear automatically.

**What this setting actually does:**  
It only allows the *specific app you just used* (e.g., Chrome, Firefox, your Downloads app) to install APKs. It does **not** open a global security hole. Other apps on your phone are unaffected.

> ⚠️ **Reverse it after installing (recommended):**  
> Go to **Settings → Apps → [your browser, e.g. Chrome] → Install unknown apps** and toggle it back **OFF**. This limits your exposure to accidentally installing malicious APKs in the future.

---

### Step 3 — Install the App

After granting the permission, Android shows the standard app install screen listing the permissions the app requests. SHNF Trails only asks for:

- **Internet access** — to check trail status from samhoustontrails.com

Tap **Install**. The app will appear in your app drawer as **SHNF Trails** 🌲.

---

### Step 4 — Add the Widget to Your Home Screen

1. Open **SHNF Trails** from your app drawer.
2. Tap **"Add Widget to Home Screen"** at the bottom of the screen.
3. Android will ask you where to place it — tap a home screen location or drag it where you want.
4. Done! The widget will show trail status and update automatically in the background.

> If the "Add Widget" button doesn't work on your launcher, long-press an empty area of your home screen → tap **Widgets** → find **SHNF Trails** → drag it to your screen.

---

### Troubleshooting

| Problem | Fix |
|---------|-----|
| Widget shows grey flags | No data yet — open the app and tap **Check Now** |
| "Couldn't add widget" on Samsung | Restart the phone after installing, then try adding the widget again |
| App says "Checking…" forever | Make sure you have an internet connection |
| Widget not updating automatically | Open the app, make sure **Auto-refresh status** is turned ON |

---

## ⚠️ Disclaimer

> **SHNF Trails is an independent, unofficial app made by a hiker for personal use.**
>
> - This app is **not affiliated with, endorsed by, or made in coordination with** the Sam Houston National Forest, the USDA Forest Service, the Lone Star Hiking Trail Club, or any other government agency or trail organization.
> - Trail status information is read from [samhoustontrails.com](https://www.samhoustontrails.com) and may be delayed, incorrect, or unavailable. **Always verify trail conditions through official channels before heading out.**
> - The authors and contributors accept **no liability** for decisions made based on the information shown by this app — including but not limited to injury, property damage, or wasted trips.
> - Use of this app is entirely at your own risk.

---

## How It Works

```
samhoustontrails.com
        │
        ▼
  TrailScraper          OkHttp + Jsoup
  (homepage first;      parses text around
   /closed-trail-status "MUT-East" / "MUT-West"
   only if needed)      for OPEN / CLOSED keywords
        │
        ▼
  StatusStore           SharedPreferences
  (persists statuses    marks data stale after
   + last-check time)   2 h of no updates
        │
        ▼
  Widget + App UI       RemoteViews bitmap drawn
                        with Canvas at actual
                        cell pixel dimensions
```

**Battery efficiency:**
- The scraper fetches the homepage first; the second URL is only requested if one or both statuses are still unknown after parsing the first page.
- WorkManager constraints: `NetworkType.CONNECTED` + `requiresBatteryNotLow(true)`.
- No polling — a single periodic job, rescheduled only when the user changes the interval.

---

## Project Structure

```
app/src/main/
├── java/com/trailwidget/
│   ├── TrailScraper.kt          # HTTP fetch + HTML parse → TrailStatuses
│   ├── StatusStore.kt           # SharedPrefs wrapper: status, timestamp, settings
│   ├── TrailUpdateWorker.kt     # WorkManager Worker — periodic background fetch
│   ├── TrailWidgetProvider.kt   # AppWidgetProvider — bitmap widget + WorkManager schedule
│   └── MainActivity.kt          # App UI: status cards, timer, settings, pin-widget
│
└── res/
    ├── layout/
    │   ├── activity_main.xml    # Main screen layout (dark green theme)
    │   └── widget_layout.xml   # Widget RemoteViews root
    ├── drawable/
    │   ├── widget_preview.png   # Static widget preview shown in picker
    │   └── ic_launcher_foreground.png  # Adaptive icon foreground (forest scene)
    ├── xml/widget_info.xml      # Widget metadata (API 29 baseline)
    ├── xml-v31/widget_info.xml  # Widget metadata override (API 31+ cell targeting)
    └── mipmap-*/ic_launcher*    # App icon at all densities
```

---

## Building from Source

### Prerequisites

| Tool | Minimum version |
|------|----------------|
| JDK | 11 |
| Android SDK | API 34 (compile), API 29 (min run) |
| Gradle | 8.4 (wrapper included) |

### Build

```bash
git clone https://github.com/YOUR_USERNAME/shnf-trails.git
cd shnf-trails
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech Stack

| Concern | Library / API |
|---------|--------------|
| HTTP client | [OkHttp 4](https://square.github.io/okhttp/) |
| HTML parsing | [Jsoup](https://jsoup.org/) |
| Background work | [AndroidX WorkManager 2.9](https://developer.android.com/jetpack/androidx/releases/work) |
| Widget rendering | `AppWidgetProvider` + `Canvas` bitmap drawn at runtime |
| UI | `AppCompatActivity`, `SwitchCompat`, `Spinner`, `ProgressBar` |
| Min SDK | Android 10 (API 29) |
| Language | Kotlin |

---

## Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

That's it — no location, no notifications, no background-location.

---

## Contributing

Pull requests welcome. Please keep the battery-efficiency contract intact:

- Don't add unnecessary network requests.
- Don't introduce polling loops or `AlarmManager` wakeups.
- Keep the scraper logic in `TrailScraper` and storage logic in `StatusStore` — don't scatter it across the codebase.

---

## License

```
MIT License

Copyright (c) 2025 SHNF Trails Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">
  <sub>Built for hikers, by hikers. 🌲 Unofficial — not affiliated with the USDA Forest Service, Sam Houston National Forest, or the Lone Star Hiking Trail Club.</sub>
</div>
