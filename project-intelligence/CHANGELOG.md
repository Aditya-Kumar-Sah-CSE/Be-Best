# Changelog — FocusLock

All notable changes to the FocusLock application and its architectural systems are documented in this file.

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
  - Intercepts launcher icon taps, Recent Apps overview, repeated launcher taps, app switching, and external links.
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
  - App switching across Instagram, YouTube, WhatsApp, Chrome, games never resets continuous timer.
  - Turning the screen OFF cleanly resets session; turning screen ON starts fresh 30-minute session.
  - Pleasant harmonic chime and warm spoken prompt: *"Please, sir! It's time to take care of yourself. Go for a short walk, rest your eyes, and drink a glass of water."*
  - Custom audio and video file pickers via Storage Access Framework (`OpenDocument`) with persistable URI permission handling.
  - High-fidelity 3D-styled animated walking character and drinking water glass Canvas animations, plus custom video loop player fallback.
  - 3 clear user actions: "Take a 5-Minute Break", "Remind Me in 5 Minutes" (Snooze), and "Dismiss".
  - Floating top timer displaying synchronized live countdown/usage (`◷ 00:00 / 30:00`) and active break status (`☕ Break 05:00`).
  - Comprehensive wellness customization in `SettingsScreen` (audio on/off, volume slider, song/video pickers, vibration toggle, and instant preview/test).

---

## [v1.2.1] — 2026-10-22

### Added
- **Project Intelligence System**: Created structured, persistent documentation in `project-intelligence/` covering architecture, dependencies, database schema, permissions, feature map, build commands, known issues, changelog, and task logs.
- **Project-Level Rules**: Installed `.agents/rules/project-intelligence.md` and `.agent/rules/project-intelligence.md` to guide future agent workflows.
- **Git-Based Staleness Script**: Added `scripts/check-doc-staleness.ps1` to detect modified source files and highlight potentially affected intelligence documents.
- **1-Minute Instagram Daily Limit Preset**: Added `1 * 60L` option to `AppDetailsScreen` to enable fast testing and micro-allowances.
- **Automated Lifecycle Unit Tests**: Added unit tests in `ExternalAppBlockingTest.kt` covering 1-minute Instagram limits, persistence in Room DB, reopening resilience, 30-second emergency bypass, and midnight resets.

### Fixed
- **Instant Zero-Lag App Blocking**: Fixed a 1–2 second delay where Instagram opened and rendered before the blocking screen appeared. Replaced pre-evaluation usage event scans with a synchronous in-memory `blockedPackagesCache` in `FocusLockAccessibilityService`.
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
