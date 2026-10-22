# Be-Best

<div align="center">

# 🔒 FocusLock — Production-Ready Android Digital Wellbeing & App Blocker

**Take back control of your screen time. Set hard daily limits on social media and gaming addictions with 3D modern AMOLED design, live midnight countdowns, and tamper-resistant enforcement.**

[![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Architecture](https://img.shields.io/badge/Architecture-Clean_+_MVVM-FF6F00?style=for-the-badge)](https://developer.android.com/topic/architecture)
[![Database](https://img.shields.io/badge/Local_DB-Room-27AE60?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Privacy](https://img.shields.io/badge/Privacy-100%25_Local--First-00F5FF?style=for-the-badge)](https://github.com/Aditya-Kumar-Sah-CSE/Be-Best)

</div>

---

## 📱 3D Modern Design & Visual Experience

FocusLock is designed around a **high-end 3D AMOLED Dark Aesthetic**, featuring glowing iridescent organic squircle badges, fluid glassmorphic cards, and live countdowns.

```
       ┌───────────────────────────────┐
       │             PHONE             │
       │                               │
       │           ( 🔒 )              │
       │    [ 3D Iridescent Pebble ]   │
       │                               │
       │         Stay focused!         │
       │                               │
       │     Your future self will     │
       │          thank you.           │
       │                               │
       │    [ Unlocks in 08:29:42 ]    │
       │                               │
       │   "This app is blocked because│
       │  you're in a fluid focus block"│
       │                               │
       │  [ Go to Home ] [ Dashboard ] │
       └───────────────────────────────┘
```

---

## 🎯 Core Use Case

1. **Daily Allowance Set:** User assigns a 30-minute daily allowance for Instagram, YouTube, Reddit, or Games.
2. **Foreground Tracking:** As Instagram is used, `FocusLockAccessibilityService` tracks session time in milliseconds.
3. **Graceful Warning:** At 25 minutes, warning alerts inform the user of 5 minutes remaining.
4. **Hard Lock Screen:** Once 30 minutes is reached, Instagram is immediately intercepted. Displays *"You've reached your daily limit"*, the exact configured quota, tracked usage, and live local midnight reset timer directly over the restricted application without closing to launcher.
5. **Live Countdown:** The 3D lock screen displays the exact live countdown until the next local midnight reset (`Unlocks in 08:29:42`).
6. **Midnight Quota Reset:** At 00:00:00 of the new calendar day, quota resets automatically.
7. **Continuous Screen Wellness Reminder:** Monotonic 30-minute screen-on timer with a 5-minute screen-off grace period (preserves exact remaining countdown if screen turns back ON < 5m; starts fresh session if screen off >= 5m; screen-off time never counts as screen-on usage). Complete with 3D animated walking/water visuals, pleasant chime, and floating timer pill.

---

## 🏗️ Architecture & Modules Breakdown

The project is structured under `com.focuslock.app` using **Clean Architecture + MVVM**:

```
FocusLock Android App (com.focuslock.app)
│
├── core/
│   ├── designsystem/          # 3D AMOLED Theme, Iridescent Glows, Glassmorphic Card
│   ├── security/              # PBKDF2 Salted PIN Hasher & Hardcore Cooldown
│   └── util/                  # TimeUtils (Midnight reset, countdowns) & Battery Helper
│
├── data/
│   ├── local/
│   │   ├── FocusLockDatabase  # AndroidX Room Database (version 1)
│   │   ├── dao/               # ProtectedAppDao, UsageDao, FocusSessionDao
│   │   └── entity/            # ProtectedApp, UsageSession, DailyUsage, FocusSession, ProtectionEvent
│   └── repository/            # AppRepositoryImpl, UsageRepositoryImpl
│
├── domain/
│   ├── model/                 # AppCategory, ProtectionHealth
│   └── usecase/               # TrackUsageUseCase, EvaluateBlockStatusUseCase, ResetMidnightQuotaUseCase, HardcorePolicyUseCase
│
├── service/
│   ├── FocusLockAccessibilityService # Foreground interception & window detection
│   ├── BootRecoveryReceiver          # Automatic quota validation after device reboot
│   └── TimeChangeReceiver            # Mitigation against system clock manipulation
│
└── presentation/
    ├── BlockingActivity       # 3D AMOLED Fullscreen Blocker (Match with Visual Reference)
    ├── MainActivity           # Jetpack Compose Navigation Host
    ├── onboarding/            # 4-step progressive onboarding & permission guide
    ├── dashboard/             # 3D progress rings, controlled app cards, stats
    ├── apps/                  # Installed apps discovery, category filters, custom limit slider
    ├── focus/                 # Pomodoro focus session timer (25m / 50m / custom)
    └── settings/              # Hardcore anti-tampering mode & PIN lock
```

---

## 🗄️ Room Database Schema

FocusLock persists state locally across 5 normalized SQLite tables:

| Table | Primary Key | Description |
| :--- | :--- | :--- |
| **`protected_apps`** | `packageName` | Selected apps, display names, daily limits (default 1800s), category, enabled status. |
| **`usage_sessions`** | `id` (Auto-increment) | Individual foreground session spans (`startedAt`, `endedAt`, `durationSeconds`). |
| **`daily_usage`** | `(packageName, localDate)` | Today's aggregate usage, quota, and persistent `isBlocked` flag. |
| **`focus_sessions`** | `id` (Auto-increment) | Pomodoro deep-work blocks with timestamps and completion status. |
| **`protection_events`** | `id` (Auto-increment) | Audit log for reboots, timezone changes, and bypass detection. |

---

## 🛡️ Security & Privacy Guardrails

- **Zero Cloud / 100% Local-First:** No accounts, no external tracking SDKs, no network analytics.
- **PIN Hardening:** Passwords hashed with `PBKDF2WithHmacSHA256` using 10,000 rounds and unique per-user 16-byte random salts.
- **Hardcore Anti-Tampering:** Modifying daily limits requires passing a 60-second friction cooldown and PIN check.
- **Accessibility Privacy:** Window content scraping is explicitly disabled (`canRetrieveWindowContent="false"`). Keystrokes, passwords, and private chats are never accessed.

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or later
- **JDK 17 or 21**
- **Android SDK API 26+** (compileSdk 35)

### Running in Android Studio
1. Clone this repository:
   ```bash
   git clone https://github.com/Aditya-Kumar-Sah-CSE/Be-Best.git
   ```
2. Open the project in Android Studio.
3. Gradle will sync automatically using the included Gradle 8.9 wrapper and dependencies.
4. Select an emulator or connected physical device and click **Run (Shift+F10)**.

### Live 3D Interactive Web Simulator
To test and experience the 3D mobile lock screen, fluid animations, and scenario simulations right now in your browser:
- Open [`simulator/index.html`](file:///d:/Be%20Best/simulator/index.html) in any web browser!

---

## 📋 Complete 8-Phase Roadmap

1. ✅ **Phase 1: Foundation** — Kotlin 2.0, Jetpack Compose, Clean Architecture & Room setup.
2. ✅ **Phase 2: App Discovery** — App listing, categories, custom daily limits.
3. ✅ **Phase 3: Usage Engine** — Foreground session calculation & UsageStats reconciliation.
4. ✅ **Phase 4: Quota & Reset** — 30m default quotas, local midnight reset boundary.
5. ✅ **Phase 5: 3D Blocker** — Accessibility interception & AMOLED lock screen with live countdown.
6. ✅ **Phase 6: Security** — Hardcore mode, PBKDF2 PIN, boot & time change receivers.
7. ✅ **Phase 7: Analytics & Focus** — Pomodoro timer, progress metrics, and permission health check.
8. 🔄 **Phase 8: Release Testing** — OEM battery exemption validations and Google Play policy review.

---

## 📄 License
This project is licensed under the Apache 2.0 License.
