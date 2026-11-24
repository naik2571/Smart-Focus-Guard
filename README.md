# Smart Focus Guard

> **Hardware-aware digital detox and app usage controller without Accessibility permissions.**

An enterprise-grade, privacy-first Android application developed in modern Kotlin and Jetpack Compose/Views. Built with strict adherence to modern Android architecture, lifecycle awareness, and hardware sensor efficiency.

---

## 🚀 Key Features

- App usage monitoring leveraging official Android `UsageStatsManager`.
- Real-time floating warning overlay and soft-lockout when daily app limits are breached.
- Strict Zero Accessibility requirement — purely permission-compliant.
- Persistent foreground monitoring service with low CPU footprint.

---

## 🛠️ Hardware & Sensor Architecture

* **Sensors & APIs:** `UsageStatsManager, ActivityManager, System Alert Overlay`
* **Declared Permissions:** `PACKAGE_USAGE_STATS, SYSTEM_ALERT_WINDOW, FOREGROUND_SERVICE`
* **Target SDK:** Android 14 (API 34)
* **Minimum SDK:** Android 8.0 (API 26+)
* **Architecture:** MVVM / Unidirectional Data Flow (UDF) with Coroutines & StateFlow

---

## 🔒 Privacy & Permission Transparency

This application is engineered under the **Zero Unnecessary Permissions** doctrine:
* **No Hidden Trackers:** Contains zero advertising SDKs or third-party behavioral analytics.
* **No Accessibility Privilege Misuse:** All background operations utilize sanctioned Android APIs (`WorkManager`, `ForegroundService`, `JobScheduler`).
* **Air-Gapped Operation:** Runs locally on your device hardware without requiring external cloud servers.

---

## 📦 Building and Running

### Prerequisites
* Android Studio Iguana / Jellyfish / Ladybug or newer
* Android SDK 34
* JDK 17+

### Build APK via CLI
```bash
# Clone the repository
git clone https://github.com/naik2571/Smart-Focus-Guard.git
cd Smart-Focus-Guard

# Build Debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Licensed under the [Apache License 2.0](LICENSE).
