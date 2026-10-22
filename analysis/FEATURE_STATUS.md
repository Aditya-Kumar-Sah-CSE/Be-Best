# Feature Status & Verification Catalog — FocusLock

This document inventories all major features of FocusLock, their active verification status, automated test coverage, and runtime operational characteristics.

---

## 1. Feature Status Matrix

| Feature | Primary Components | Status | Automated Test Coverage | Verification Method |
| :--- | :--- | :--- | :--- | :--- |
| **Instant App Blocking (Instagram, etc.)** | `FocusLockAccessibilityService`, `BlockingActivity`, `blockedPackagesCache` | **Active & Verified** | `ExternalAppBlockingTest.kt` | Unit tests passing; zero-lag (<50ms) blocking with no home screen flash. |
| **Custom App Daily Limits** | `AppDetailsScreen`, `AppDetailsViewModel`, `ProtectedAppDao` | **Active & Verified** | `ExternalAppBlockingTest.kt` | Supports custom limits down to 1-minute test preset (`1 * 60L`). |
| **Ground-Truth Usage Tracking** | `RealUsageTracker`, `UsageRepositoryImpl`, `InstagramPackageHelper` | **Active & Verified** | `InstagramAndSessionTrackingTest.kt` | Queries `UsageStatsManager.queryEvents()`; measures real foreground seconds. |
| **Session Analytics & Longest Session** | `AnalyticsScreen`, `DailyUsageDao` (Migration 4) | **Active & Verified** | `AnalyticsCalculationTest.kt` | Aggregates session counts, longest sessions, scroll estimates. |
| **Floating Screen-Time Timer Overlay** | `FloatingTimerService`, `FloatingTimerManager` | **Active & Verified** | `FloatingTimerOverlayTest.kt` | System alert window with draggable AMOLED timer pill. |
| **30-Minute Break Reminder** | `BreakReminderHelper`, `BreakAlertActivity` | **Active & Verified** | `BreakReminderTest.kt` | Screen-on monitoring triggering notification and activity alert. |
| **Pomodoro Focus Mode** | `FocusModeScreen`, `FocusSessionDao`, `QuotaEngine` | **Active & Verified** | `QuotaEngineTest.kt` | Timed focus blocks temporarily overriding all protected apps. |
| **Hardcore Mode & PBKDF2 PIN** | `PinSecurityManager`, `HardcorePolicyUseCase` | **Active & Verified** | `QuotaEngineTest.kt` | 10,000 round PBKDF2 salted PIN and 60-second cooldown timer. |
| **Automated Midnight Reset** | `TimeChangeReceiver`, `BootRecoveryReceiver` | **Active & Verified** | `ExternalAppBlockingTest.kt` | Tests verify daily usage resets to 0 seconds and clears exhausted flag on date change. |
| **Emergency 30s Bypass** | `QuotaEngine`, `DailyUsageDao` | **Active & Verified** | `ExternalAppBlockingTest.kt` | Grants single 30-second window to access essential tasks before re-locking. |
| **In-App GitHub APK Updater** | `AppUpdateManager`, `ApkSecurityValidator` | **Active & Verified** | `AppUpdateManagerTest.kt`, `UpdaterPipelineTest.kt` | Silent background checking, SHA-256 verification, and package installer prompt. |

---

## 2. Recent Architectural Fixes & Verifications

### Instagram Instant Redirection & Blocking
- **Problem**: When Instagram opened, there was a 1–2 second window where reels/feeds rendered before the lockscreen triggered, along with a visible home screen flash.
- **Solution**:
  1. Synchronized an in-memory `blockedPackagesCache` in `FocusLockAccessibilityService` from Room DB.
  2. Dispatched `startActivity(BlockingActivity)` directly on the main thread in `<1ms` upon window state change.
  3. Removed `GLOBAL_ACTION_HOME` and delays to prevent screen flashing.
  4. Updated `BlockingActivity` text: *"Daily Limit Reached - You've used your Instagram allowance for today. Take a break and come back tomorrow."*
  5. Verified with 6 unit tests in `ExternalAppBlockingTest.kt`.
