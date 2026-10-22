# Changelog — FocusLock

All notable changes to the FocusLock application, its architectural systems, and documentation are documented in this file.

---

## [v1.2.5] — 2026-10-23

### Critical Production Bug Fixes & Reliability Enhancements
- **Bug 1 — 10-Minute and 5-Minute Warnings System Implemented & Persisted**:
  - **Root Cause**: Zero notification dispatch logic or database delivery tracking existed for daily quota warnings; `QuotaEngine` had only an unused 80% status without notification channels, persistence, or delivery tracking.
  - **Database Migration (v4 -> v5)**: Added `MIGRATION_4_5` in `FocusLockDatabase` adding `warning10Triggered` and `warning5Triggered` columns to `DailyUsageEntity` and updated `UsageDao` to mark delivery atomically.
  - **Dynamic Threshold Calculation**: Evaluates warnings based on actual configured quota:
    - 10-Minute Warning: `remainingTime <= 600s` (e.g. 20:00 of 30:00 quota).
    - 5-Minute Warning: `remainingTime <= 300s` (e.g. 25:00 of 30:00 quota).
    - Block Enforced: `remainingTime <= 0s` (e.g. 30:00).
  - **At-Most-Once Delivery Guarantee**: Warnings are delivered at most once per app per local calendar day, persisted across process deaths, service restarts, app switching, and reboots.
  - **Startup Immediate Delivery**: If FocusLock starts when usage has already crossed a warning threshold, warnings are dispatched immediately without requiring prior thresholds.
  - **Permission Check & Non-Silent Fallback**: Uses `NotificationManagerCompat.areNotificationsEnabled()`; if notifications are disabled/restricted, safely logs and triggers the on-screen `FloatingTimerManager` overlay alert as fallback.
  - **Local Midnight Reset**: Automatically resets `warning10Triggered` and `warning5Triggered` flags at 12:00 AM local midnight.

- **Bug 2 — Inconsistent Daily App Limit Enforcement & Instagram Keyboard Bypass Fix**:
  - **Root Cause**:
    1. Input Method Editors (soft keyboards like Gboard, Samsung Keyboard, SwiftKey) were not excluded from accessibility window monitoring. Typing in Instagram dispatched accessibility events with IME package names, resetting `currentForegroundPackage` to null and cancelling the active monitoring coroutine ticker. When keyboard was dismissed, Android did not fire another window transition event, allowing hours of untracked usage.
    2. Accessibility monitoring loop did not exit immediately upon blocking an app, causing redundant loop evaluations.
    3. `RealUsageTracker.reconcileWithAndroidUsageStats` compared against stale `existing.quotaSeconds` instead of updated limits and could reset `isBlocked`.
  - **IME Exclusion Filter**: Added `isInputMethodOrTransientOverlay` recognizing IME packages, prevent keyboard appearances from interrupting target app monitoring tickers.
  - **Immediate Loop Termination**: Added immediate `break` in `FocusLockAccessibilityService` foreground monitoring loop as soon as blocking screen is triggered.
  - **Authoritative Quota & Block State Preservation**: Reconciliations now preserve `isBlocked = existing.isBlocked || (reconciledSeconds >= quota)` and retain configured quota.
  - **Persistent Blocking Enforcement**: Repeated launch attempts and app switches re-check persisted Room database state and enforce blocking screen immediately until midnight reset.

- **Bug 3 — 5-Minute Break Timer Across Screen-Off & Background Execution**:
  - **Root Cause**:
    1. `BreakReminderHelper.onScreenOff()` and `expireSessionIfGracePeriodOver()` explicitly deleted `KEY_BREAK_ACTIVE` and `KEY_BREAK_START_ELAPSED` from SharedPreferences when screen turned off.
    2. `BreakAlertActivity.kt` relied on an in-memory Compose `LaunchedEffect` decrementing an integer instead of deriving remaining time from monotonic deadlines.
    3. No AlarmManager or background mechanism existed to fire completion when the screen was off or device was in deep sleep.
  - **Persistent Monotonic & Wall-Clock Deadlines**: Added `KEY_BREAK_START_WALL`, `KEY_BREAK_DEADLINE_WALL`, `KEY_BREAK_BOOT_SESSION_ID`, and `KEY_BREAK_COMPLETED` in `BreakReminderHelper`.
  - **Authoritative Elapsed Time Countdown**: `getRemainingBreakSeconds()` derives remaining time from `clock.elapsedRealtime()` / `currentTimeMillis()`. Screen-off time continues the break countdown accurately.
  - **AlarmManager Completion Scheduling**: Added `scheduleBreakCompletionAlarm()` using `AlarmManager.RTC_WAKEUP` / `setExactAndAllowWhileIdle()` targeting `BreakReminderActionReceiver.ACTION_BREAK_COMPLETE`.
  - **Reboot Recovery**: `onDeviceReboot()` validates wall-clock difference: if elapsed wall time < 5 minutes, monotonic timestamps and alarm are restored; if >= 5 minutes, break completes immediately.
  - **Idempotent Completion**: `completeBreak()` completes atomically, cancels pending alarms, and posts `Break Complete! 🎉` notification exactly once.
  - **Strict Decoupling**: 5-minute break timer is completely decoupled from 30-minute continuous screen wellness timer. Wellness session reset does not erase active breaks.

- **Test Suite**:
  - Full test suite passing (121+ tests including boundary conditions, at-most-once delivery, keyboard IME overlay safety, repeated launch prevention, break screen-off countdown, and reboot recovery).

---

## [v1.2.4] — 2026-10-22

### Critical Fix
- **Instagram Closes Instead of Showing Daily Limit Screen (Fixed)**:
  - **Root Cause**: In `FocusLockAccessibilityService`, `performGlobalAction(GLOBAL_ACTION_HOME)` was called directly before `launchBlockScreen(packageName, ...)`. Android WindowManager processed the asynchronous Home command simultaneously with `BlockingActivity`, immediately minimizing `BlockingActivity` right as it was started and returning the user to the Home launcher.
  - **Fix**: Removed all premature `GLOBAL_ACTION_HOME` calls before `launchBlockScreen`. `BlockingActivity` is now launched directly on top of the restricted foreground application with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`.
  - **Fallback Safety**: `performGlobalAction(GLOBAL_ACTION_HOME)` and high-priority full-screen notifications are now preserved strictly as a safety fallback if `startActivity` fails or encounters Android Background Activity Launch (BAL) restrictions.
  - **Intent Flags**: Removed conflicting `FLAG_ACTIVITY_REORDER_TO_FRONT`.
  - **Blocking UI**: Updated `BlockingScreen` to display:
    - `"You've reached your daily limit."`
    - `"$appName: ${limitMinutes}-minute daily limit."`
    - `"Your access will reset at local midnight."`
    - Authoritative tracked usage vs configured limit (e.g. `"Tracked usage: 3h 0m of 30m"`).
  - **Monitoring Health Checks**: Added `checkMonitoringHealth()` to `FocusLockAccessibilityService` checking Usage Access permission on service connection and alerting the user via notifications if permissions are disabled.
  - **Test Suite**: Added targeted tests in `ExternalAppBlockingTest` for external intent re-entry, permission revocation, and 3-hour boundary condition enforcement (total 21 comprehensive tests passing).

---

## [v1.2.3] — 2026-10-22

### Fixed & Enhanced
- **App Re-Launch & Recent Apps Bypass Fix**:
  - Added immediate foreground displacement via `performGlobalAction(GLOBAL_ACTION_HOME)` in `FocusLockAccessibilityService` when restricted apps are foregrounded.
  - Prevents bypasses through Home button relaunch, Recent Apps overview, repeated launcher taps, app switching, and external links.
  - Smart 250ms debouncer prevents duplicate `BlockingActivity` launches without ever dropping app displacement.
- **Accurate Foreground Usage Accounting**:
  - Eliminated fractional loss and `% 2L` truncation; exact foreground seconds are tracked monotonically and reconciled with `UsageStatsManager` via `maxOf(measuredSeconds, existing.usedSeconds)`.
- **Enforcement State Machine**:
  - Added `EnforcementState` (`UNDER_LIMIT`, `LIMIT_REACHED`, `TEMPORARY_BYPASS_ACTIVE`, `DAILY_RESET_PENDING`) in `QuotaEngine`.
  - Enforced that `LIMIT_REACHED` persists across app switching, service restarts, and phone reboots.
- **Reliable Local Midnight Rollover**:
  - Background monitor checks local calendar date (`LocalDate`) every 5 seconds.
  - Automatically resets quotas, seeds fresh day records (`usedSeconds = 0`), and clears exhausted state at 12:00 AM.
  - Fixed `TimeChangeReceiver` to re-seed cache instead of clearing it, preventing timezone/time tamper bypasses.
  - Updated `BlockingActivity` to display restored status and an "Open App" button after midnight.
- **Persistent 30-Minute Wellness Timer with 5-Minute Screen-Off Grace Period**:
  - Upgraded `BreakReminderHelper` with `WellnessClock` abstraction and exact monotonic elapsed tracking.
  - Screen OFF starts a 5-minute grace period with persisted screen-off timestamps and scheduled expiration task.
  - Screen ON within 5 minutes resumes the previous session, preserving the exact accumulated screen-on duration and remaining countdown.
  - Screen OFF for 5 minutes or longer expires the session and starts a fresh 30-minute session.
  - Screen-off duration does NOT count toward the 30-minute usage threshold.
  - Process recreation and service restarts during the grace period restore session state safely from persisted storage.
  - Device reboots validate wall-clock timestamps: if reboot completes within 5 minutes of screen-off, the grace period is restored safely; otherwise session resets.
  - App switching continues continuous session seamlessly without resetting.
  - Floating top timer pill remains synchronized with the single source of truth (`◷ MM:SS / 30:00`).
  - Added 14 comprehensive deterministic unit tests in `BreakReminderTest`.
- **Test Suite**:
  - Added 18 comprehensive tests in `ExternalAppBlockingTest` validating all bypass and rollover scenarios.
  - Added 14 deterministic tests in `BreakReminderTest` with clock injection covering all 12 grace period and boundary conditions.

---

## [v1.2.2] — 2026-10-22

### Added
- **Continuous 30-Minute Screen Wellness Reminder System**:
  - Monotonic continuous screen-on session tracking (`BreakReminderHelper`).
  - App switching across Instagram, YouTube, WhatsApp, Chrome, games never resets the continuous timer.
  - Turning the screen OFF cleanly resets the session; turning the screen ON starts a fresh 30-minute session.
  - Pleasant harmonic chime and warm spoken prompt: *"Please, sir! It's time to take care of yourself. Go for a short walk, rest your eyes, and drink a glass of water."*
  - Custom audio and video file pickers via Storage Access Framework (`OpenDocument`) with persistable URI permission handling.
  - High-fidelity 3D-styled animated walking character and drinking water glass Canvas animations, plus custom video loop player fallback.
  - 3 clear user actions: "Take a 5-Minute Break", "Remind Me in 5 Minutes" (Snooze), and "Dismiss".
  - Floating top timer displaying synchronized live countdown/usage (`◷ 00:00 / 30:00`) and active break status (`☕ Break 05:00`).
  - Comprehensive wellness customization in `SettingsScreen` (audio on/off, volume slider, song/video pickers, vibration toggle, and instant preview/test).

---

## [v1.2.1] — 2026-10-22

### Added
- **Standardized `analysis/` Intelligence Directory**: Deployed project analysis and architecture documentation suite:
  - `analysis/README.md`
  - `analysis/PROJECT_OVERVIEW.md`
  - `analysis/ARCHITECTURE.md`
  - `analysis/FILE_INDEX.md`
  - `analysis/DEPENDENCIES.md`
  - `analysis/DATABASE_SCHEMA.md`
  - `analysis/SECURITY_AND_PERMISSIONS.md`
  - `analysis/FEATURE_STATUS.md`
  - `analysis/BUILD_AND_TEST.md`
  - `analysis/KNOWN_ISSUES.md`
  - `analysis/TASK_LOG.md`
- **Global & Project Rule Alignment**: Updated `AGENTS.md`, `.agents/rules/project-intelligence.md`, and `.agent/rules/project-intelligence.md` with:
  - Multilingual response rules (Simple Hinglish).
  - Senior Expert Roles orchestration (UI/UX, Android, Frontend, Backend, DevOps, DB, QA, Architect).
  - Mandatory project analysis protocol & incremental updates.
  - Strict 5-point final response schema (`DONE`, `FILES`, `VERIFICATION`, `DOCS`, `BLOCKERS`).
- **1-Minute Instagram Daily Limit Preset**: Added micro-allowance option to `AppDetailsScreen.kt` for rapid testing and zero-tolerance quotas.
- **Automated Lifecycle Unit Tests**: Added unit tests in `ExternalAppBlockingTest.kt` verifying 1-minute limits, Room persistence, reopening resilience, 30-second emergency bypass, and midnight resets.

### Fixed
- **Instant Zero-Lag App Blocking**: Fixed a 1–2 second delay where Instagram opened and rendered before the blocking screen appeared. Replaced pre-evaluation usage event scans with a synchronous in-memory `blockedPackagesCache` in `FocusLockAccessibilityService.kt`.
- **Eliminated Home Screen Flash**: Removed the `GLOBAL_ACTION_HOME` + `delay(100L)` race condition, launching `BlockingActivity` directly on top of the restricted app.
- **Blocking Screen UI Wording**: Updated title to "Daily Limit Reached" and added clear message: *"You've used your $appName allowance for today. Take a break and come back tomorrow."*

---

## [v1.2.0] — 2026-10-22

### Added
- **Instagram & Cross-App Analytics**: Reconstructed discrete sessions, longest session durations, and scroll count estimation in `daily_usage`.
- **Room Migration 3 ➔ 4**: Added `sessionCount`, `longestSessionSeconds`, `firstActivityAt`, `lastActivityAt`, `approximateScrollCount`, `activitySource`, and `activityConfidence` columns to `daily_usage`.

---

## [v1.1.0] — 2026-10-21

### Added
- **Universal Floating Screen-Time Timer Overlay**: Added draggable floating AMOLED pill overlaying all apps via `FloatingTimerService` and `FloatingTimerManager`.
- **30-Minute Continuous Screen Break Alert**: Added screen-on monitor triggering notifications and `BreakAlertActivity` after 30 minutes of uninterrupted screen time.
- **Room Migration 2 ➔ 3**: Added emergency session tracking columns (`emergencyUsed`, `emergencyStartedAt`, `emergencyExpiresAt`, `emergencyElapsedRealtimeAtStart`).

---

## [v1.0.1] — 2026-10-20

### Fixed
- **Quota Erasure on Repeated App Launches**: Solved an anomaly where rapidly reopening blocked apps caused partial session metrics to overwrite total daily usage. Enforced `maxOf(measuredSeconds, existing.usedSeconds)`.

---

## [v1.0.0] — 2026-10-18

### Initial Release
- Core digital wellbeing architecture with Room DB, Clean Architecture, and MVVM.
- AccessibilityService-based app detection and fullscreen `BlockingActivity`.
- 30-minute daily limits on default distractions (Instagram, YouTube, Reddit).
- Hardcore Mode with PBKDF2 salted PIN security and 60-second cooldown timers.
- Local midnight quota reset logic with `BootRecoveryReceiver` and `TimeChangeReceiver`.
