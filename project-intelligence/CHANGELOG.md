# Changelog — FocusLock

All notable changes to the FocusLock application and its architectural systems are documented in this file.

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
