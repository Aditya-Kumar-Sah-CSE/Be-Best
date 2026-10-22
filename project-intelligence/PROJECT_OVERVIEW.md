# Project Overview — FocusLock

## 1. Executive Summary & Application Purpose

**FocusLock** is a local-first, production-grade Android Digital Wellbeing, Intentional Productivity, and App Blocker application.
Its primary objective is to empower users to reclaim control over their screen time by enforcing hard daily quotas on high-distraction applications (social media like Instagram, YouTube, Reddit, gaming apps), providing dynamic 30-minute continuous screen break alerts, persistent floating screen-time timer overlays, and tamper-resistant settings guarded by cryptographic PIN authentication.

Unlike cloud-dependent blockers or simplistic timer apps, FocusLock operates strictly on-device, prioritizing user privacy, resilience against process termination, and battery efficiency.

---

## 2. Technology Stack & Tooling

| Component | Technology | Version / Specification |
| :--- | :--- | :--- |
| **Primary Language** | Kotlin | `2.0.21` |
| **UI Framework** | Jetpack Compose + Material 3 | Compose BOM `2024.09.00` |
| **Android Gradle Plugin** | AGP | `8.7.1` |
| **Compile SDK** | Android 15 (VanillaIceCream) | API `35` |
| **Minimum SDK** | Android 8.0 (Oreo) | API `26` |
| **Target SDK** | Android 15 | API `35` |
| **Java Target** | Java SE 17 | `JavaVersion.VERSION_17` / JVM `17` |
| **Local Persistence** | AndroidX Room + SQLite | Version `2.6.1` (KSP compiler) |
| **Security & Crypto** | AndroidX Security Crypto | `1.1.0-alpha06` (EncryptedSharedPreferences, PBKDF2WithHmacSHA256) |
| **Asynchronous Engine** | Kotlin Coroutines | `kotlinx-coroutines-android:1.8.1` |
| **Navigation** | Navigation Compose | `androidx.navigation:navigation-compose:2.8.2` |
| **Unit Testing** | JUnit 4 + Coroutines Test | `junit:4.13.2`, `kotlinx-coroutines-test:1.8.1` |

---

## 3. Project Structure & Directory Layout

The workspace root is organized as follows:

```
FocusLock (d:\Be Best\)
├── .agents/
│   └── rules/
│       └── project-intelligence.md       # Antigravity project-level rules
├── .agent/
│   └── rules/
│       └── project-intelligence.md       # Alternative project rules mirror
├── project-intelligence/                 # Central Project Intelligence documentation
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml       # Permissions, Services, Receivers, Activities
│   │   │   ├── java/com/focuslock/app/   # Core Kotlin source code
│   │   │   │   ├── FocusLockApp.kt       # Application class (DI root, DB init, notification channels)
│   │   │   │   ├── core/                 # Shared foundations: design system, security, update, usage, util
│   │   │   │   ├── data/                 # Local Room DB, Entities, DAOs, Repositories
│   │   │   │   ├── domain/               # Domain models, QuotaEngine, UseCases
│   │   │   │   ├── presentation/         # Activities, Compose Screens, ViewModels, Navigation
│   │   │   │   └── service/              # AccessibilityService, Receivers
│   │   │   └── res/                      # Values (themes, colors, strings), XML configs, drawables
│   │   └── test/java/com/focuslock/app/  # Unit tests (QuotaEngine, Blocking, Overlay, Update, Analytics)
│   ├── build.gradle.kts                  # App module Gradle configuration
│   └── proguard-rules.pro                # R8 / ProGuard rules
├── gradle/
│   ├── libs.versions.toml                # Centralized Gradle version catalog
│   └── wrapper/gradle-wrapper.properties # Gradle wrapper (v8.9+)
├── scripts/
│   └── check-doc-staleness.ps1           # Git-based documentation staleness detector
├── release/                              # Release APK output directory
├── release-metadata.json                 # Update tracking & release manifest
├── build.gradle.kts                      # Root Gradle build script
└── settings.gradle.kts                   # Root project & plugin repositories
```

---

## 4. Main Application Entry Points

1. **`com.focuslock.app.FocusLockApp`**:
   - Extends `android.app.Application`.
   - Single source for singleton instances: Room `FocusLockDatabase`, `AppRepository`, `UsageRepository`, `QuotaEngine`, `RealUsageTracker`, and `PinSecurityManager`.
   - Initializes notification channels and auto-provisions default suggested distractions (Instagram, YouTube, etc.) with 30-minute daily limits on first run.

2. **`com.focuslock.app.presentation.MainActivity`**:
   - Primary user launcher activity (`android.intent.action.MAIN`, `android.intent.category.LAUNCHER`).
   - Hosts `FocusLockNavGraph` for Compose navigation across Onboarding, Dashboard, App List, App Details, Analytics, Focus Mode, and Settings.
   - Hosts the silent in-app update check and dialog overlay.

3. **`com.focuslock.app.service.FocusLockAccessibilityService`**:
   - Core background monitoring engine bound by Android OS.
   - Monitors window focus changes (`TYPE_WINDOW_STATE_CHANGED`) to detect when protected apps are opened and immediately launches the blocking overlay if daily limits are exhausted.

4. **`com.focuslock.app.presentation.BlockingActivity`**:
   - Fullscreen lock overlay (`Theme.FocusLock.Blocking`) displayed whenever an exhausted application is opened.
   - Displays daily limit exhausted message, midnight countdown, emergency bypass button, and "Return Home" action.

5. **`com.focuslock.app.core.overlay.FloatingTimerService`**:
   - Foreground service running a system-wide floating draggable pill displaying active app usage time.
