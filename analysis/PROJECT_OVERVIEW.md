# Project Overview — FocusLock

## 1. Executive Summary & Application Purpose

**FocusLock** is a local-first, privacy-focused Android Digital Wellbeing and Productivity application. Its primary mission is to empower users to reclaim control over their time and focus by enforcing hard daily quotas on high-distraction apps (e.g. Instagram, YouTube, Reddit, TikTok, X/Twitter), providing persistent floating screen-time timer overlays, 30-minute continuous screen break alerts, and tamper-resistant Hardcore Mode guarded by PBKDF2 cryptographic PIN security.

Unlike cloud-dependent screen time managers, FocusLock works 100% on-device with zero telemetry, zero mandatory cloud accounts, and minimal battery overhead.

---

## 2. Technology Stack & Specifications

| Component | Technology | Specification / Version |
| :--- | :--- | :--- |
| **Language** | Kotlin | `2.0.21` |
| **UI Framework** | Jetpack Compose + Material 3 | Compose BOM `2024.09.00` |
| **Build System** | Gradle (Kotlin DSL) + AGP | AGP `8.7.1`, Gradle Wrapper `8.9` |
| **Compile SDK / Target SDK** | Android 15 (VanillaIceCream) | API `35` |
| **Min SDK** | Android 8.0 (Oreo) | API `26` |
| **JDK Requirement** | Java SE 17 | `C:\Program Files\Android\Android Studio\jbr` |
| **Local Database** | AndroidX Room with KSP | Room `2.6.1` (SQLite 3) |
| **Security & Crypto** | AndroidX Security Crypto | `1.1.0-alpha06` (PBKDF2WithHmacSHA256, EncryptedSharedPreferences) |
| **Async Engine** | Kotlin Coroutines & Flow | `1.8.1` |
| **Navigation** | Navigation Compose | `2.8.2` |
| **Testing** | JUnit 4 + Coroutines Test | `junit:4.13.2`, `kotlinx-coroutines-test:1.8.1` |

---

## 3. Directory Layout

```
FocusLock (d:\Be Best\)
├── .agents/
│   └── rules/
│       └── project-intelligence.md       # Antigravity project rules
├── analysis/                             # Persistent Project Intelligence
│   ├── README.md                         # Navigation & quick links
│   ├── PROJECT_OVERVIEW.md               # Overview & tech stack
│   ├── ARCHITECTURE.md                   # Clean Architecture & data flow
│   ├── FILE_INDEX.md                     # File directory & mapping
│   ├── DEPENDENCIES.md                   # Internal & external dependencies
│   ├── DATABASE_SCHEMA.md                # Room entities & migrations
│   ├── SECURITY_AND_PERMISSIONS.md       # Permissions & security architecture
│   ├── FEATURE_STATUS.md                 # Feature catalogue & verification
│   ├── BUILD_AND_TEST.md                 # Gradle commands & test workflows
│   ├── KNOWN_ISSUES.md                   # Known boundaries & edge cases
│   └── TASK_LOG.md                       # Chronological task history
├── AGENTS.md                             # Global project instructions
├── CHANGELOG.md                          # Release & change history
├── README.md                             # Repository overview
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml       # Permissions, Services, Activities
│   │   │   ├── java/com/focuslock/app/   # Application source code
│   │   │   │   ├── FocusLockApp.kt       # Application class (DI root, DB init)
│   │   │   │   ├── core/                 # Shared core utilities, design, crypto, usage
│   │   │   │   ├── data/                 # Room database, DAOs, Repositories
│   │   │   │   ├── domain/               # Domain models, QuotaEngine, UseCases
│   │   │   │   ├── presentation/         # Activities, Compose Screens, ViewModels
│   │   │   │   └── service/              # AccessibilityService, Receivers
│   │   │   └── res/                      # XML resources, strings, themes, drawables
│   │   └── test/java/com/focuslock/app/  # Unit tests
│   └── build.gradle.kts                  # App module build configuration
├── gradle/
│   └── libs.versions.toml                # Centralized version catalog
└── scripts/
    └── check-doc-staleness.ps1           # Git diff doc-staleness analyzer
```

---

## 4. Key Application Entry Points

1. **`com.focuslock.app.FocusLockApp`**:
   - Application singleton container providing manual dependency injection for `FocusLockDatabase`, `AppRepository`, `UsageRepository`, `QuotaEngine`, `RealUsageTracker`, and `PinSecurityManager`.
   - Provisions initial distraction apps with default 30-minute limits on first launch.
2. **`com.focuslock.app.presentation.MainActivity`**:
   - Single-Activity entry point for Jetpack Compose navigation (`FocusLockNavGraph`).
   - Handles onboarding, permission checks, app selection, dashboard analytics, focus mode, and settings.
3. **`com.focuslock.app.service.FocusLockAccessibilityService`**:
   - High-priority system service bound by Android OS.
   - Listens to `TYPE_WINDOW_STATE_CHANGED` events.
   - Leverages a low-latency, thread-safe in-memory `blockedPackagesCache` to launch `BlockingActivity` instantly (<50ms) without running expensive queries during app transitions.
4. **`com.focuslock.app.presentation.BlockingActivity`**:
   - Fullscreen lock overlay (`Theme.FocusLock.Blocking`) displayed when an exhausted app is foregrounded.
   - Displays clear messaging ("Daily Limit Reached", "You've used your $appName allowance for today. Take a break and come back tomorrow."), countdown to midnight, return home button, and 30-second emergency bypass.
5. **`com.focuslock.app.core.overlay.FloatingTimerService`**:
   - Foreground service running an AMOLED-black floating pill overlay on top of external applications.
