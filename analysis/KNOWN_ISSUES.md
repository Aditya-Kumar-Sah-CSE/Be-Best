# Known Issues & Android Platform Boundaries — FocusLock

This document records architectural boundaries, operating system quirks, OEM battery optimizations, and known edge cases in FocusLock.

---

## 1. Android OS System Boundaries

### 1. Launcher Icon Tap Interception Boundary
- **Description**: Android third-party apps cannot legally hook or intercept a user tapping an app icon on the home screen launcher *before* the OS initiates the app launch intent.
- **Mitigation**: FocusLock's `FocusLockAccessibilityService` catches the transition as soon as the target window is foregrounded (`TYPE_WINDOW_STATE_CHANGED`). With the in-memory `blockedPackagesCache`, the lockscreen launches synchronously in `<50ms`, preventing interaction with the blocked app.

### 2. OEM Battery Optimization & Process Termination
- **Description**: Manufacturers like Xiaomi (MIUI/HyperOS), Huawei (EMUI), Samsung (OneUI), and Oppo/Vivo kill background services and unbind AccessibilityServices if aggressive power saving is enabled.
- **Mitigation**: FocusLock implements `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, registers `BootRecoveryReceiver` to restart on reboot, and provides user guidance in Settings to disable OEM-specific battery restrictions.

### 3. UsageStatsManager Event Query Latency
- **Description**: `UsageStatsManager.queryEvents()` reads system disk event logs. On devices with heavy app switching, querying a 24-hour window can take 200ms–800ms.
- **Mitigation**: Foreground window checks in `FocusLockAccessibilityService` rely solely on the in-memory `blockedPackagesCache`. Heavy `queryEvents()` reads are offloaded to background coroutines via `Dispatchers.IO`.

---

## 2. Resolved Architectural Issues

1. **Quota Erasure on Rapid App Launches (Fixed v1.0.1)**:
   - *Issue*: Quickly opening and closing blocked apps created 0-second sessions that could overwrite valid recorded usage in Room.
   - *Fix*: UsageRepository enforces `maxOf(measuredSeconds, existing.usedSeconds)`.

2. **Home Screen Flashing & 1-2s Delay on Instagram (Fixed v1.2.1)**:
   - *Issue*: Previous code used `GLOBAL_ACTION_HOME` with a 100ms delay, causing screen flashes and letting Instagram feed render momentarily.
   - *Fix*: Removed synthetic delay, launched `BlockingActivity` directly over Instagram, and backed package lookups with an in-memory cache.

3. **App Limit Bypass & Re-Launch Bug (Fixed v1.2.3)**:
   - *Issue*: Repeated taps, Recent Apps re-entry, deep links, or app switching after limit exhaustion allowed brief interaction. Fractional seconds were lost via `% 2L`.
   - *Fix*: Monotonic exact second accumulation, `EnforcementState` machine (`UNDER_LIMIT`, `LIMIT_REACHED`, `TEMPORARY_BYPASS_ACTIVE`, `DAILY_RESET_PENDING`), and 5-second calendar date check.

4. **Instagram Closes Without Showing Blocking Screen (Fixed v1.2.4)**:
   - *Issue*: In v1.2.3, `performGlobalAction(GLOBAL_ACTION_HOME)` was called directly before `startActivity(BlockingActivity)`. The queued asynchronous Home command executed in WindowManager simultaneously with `BlockingActivity`, immediately minimizing `BlockingActivity` before rendering and dumping the user back on the Home launcher. The user saw Instagram close/push away, but never saw the "You've reached your daily limit" screen.
   - *Fix*:
     - Removed premature `GLOBAL_ACTION_HOME` calls before `launchBlockScreen`.
     - `BlockingActivity` launches directly over the restricted application via `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` with zero interference.
     - `performGlobalAction(GLOBAL_ACTION_HOME)` is preserved strictly as a safety fallback if `startActivity` fails or is blocked by background activity launch (BAL) restrictions.
     - Removed conflicting `FLAG_ACTIVITY_REORDER_TO_FRONT`.
     - Updated `BlockingScreen` UI to explicitly show: "You've reached your daily limit.", "$appName: ${limitMinutes}-minute daily limit.", "Your access will reset at local midnight.", and actual tracked usage vs configured limit.
     - Added monitoring health checks in `FocusLockAccessibilityService` to prompt the user if Usage Access permission is missing.

     - Live usage accounting switched to monotonic delta seconds without fractional loss.

4. **Wellness Screen-Off Grace Period & Monotonic Persistence (Fixed v1.2.3)**:
   - *Issue*: Any screen-off previously wiped out continuous screen usage immediately or after 3 seconds, causing frustrating session resets when turning the screen off momentarily. Rebooting device during break wiped state indiscriminately.
   - *Fix*:
     - Implemented 5-minute grace period in `BreakReminderHelper`.
     - Monotonic clock tracks exact screen-on segments and accumulates them without counting screen-off time.
     - Turning screen ON within 5 minutes resumes exact remaining countdown.
     - Turning screen OFF for >= 5 minutes expires session.
     - Device reboot verifies wall-clock elapsed time against 5-minute grace threshold, preserving session across reboots safely.
     - Clock abstraction allows 100% deterministic unit testing without real-time delays.

5. **Missing 10-Minute & 5-Minute Daily Quota Warnings (Fixed v1.2.5)**:
   - *Issue*: Daily quota approaching warnings did not appear.
   - *Root Cause*: Zero code existed to post quota warning notifications or persist warning delivery; `QuotaEngine` only had an unused 80% status.
   - *Fix*: Added `warning10Triggered` and `warning5Triggered` columns to Room `DailyUsageEntity` (DB migration 4->5); dynamically evaluates thresholds (`remainingTime <= 10m` and `<= 5m`); guarantees at-most-once delivery per local calendar day; evaluates immediately on startup; uses `NotificationManagerCompat` with floating overlay fallback; resets flags at local midnight.

6. **Inconsistent Quota Enforcement & Keyboard Bypass (Fixed v1.2.5)**:
   - *Issue*: Instagram could be used for hours past the daily limit.
   - *Root Cause*: Input Method Editors (soft keyboards like Gboard, SwiftKey) were not excluded, so typing in Instagram triggered window state changes with IME package names, resetting `currentForegroundPackage` to null and cancelling the active monitoring coroutine ticker. When keyboard was dismissed, Android did not fire another window transition event, allowing hours of untracked usage. Furthermore, accessibility monitoring loop did not exit immediately upon blocking an app, causing redundant loop evaluations.
   - *Fix*: Added `isInputMethodOrTransientOverlay` recognizing IME packages, preventing keyboard events from interrupting active monitoring tickers. Added immediate `break` in accessibility monitoring loop upon blocking. Reconciliations preserve `isBlocked = existing.isBlocked || (reconciledSeconds >= quota)`.

7. **5-Minute Break Timer Stopping When Screen Turns Off (Fixed v1.2.5)**:
   - *Issue*: 5-minute break timer stopped or reset when the screen turned off.
   - *Root Cause*: `BreakReminderHelper.onScreenOff()` and `expireSessionIfGracePeriodOver()` explicitly deleted `KEY_BREAK_ACTIVE` and `KEY_BREAK_START_ELAPSED` from SharedPreferences when screen turned off. `BreakAlertActivity.kt` relied on an in-memory Compose integer loop.
   - *Fix*: Persisted break start time and deadline (monotonic and wall-clock); countdown derived dynamically from elapsed time; `scheduleBreakCompletionAlarm()` uses `AlarmManager.RTC_WAKEUP`; `onDeviceReboot()` restores active break if wall diff < 5 min; `completeBreak()` is idempotent and fires completion notification once; strictly decoupled from 30-minute wellness timer.
