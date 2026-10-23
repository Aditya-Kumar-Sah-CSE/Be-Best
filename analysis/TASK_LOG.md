# Task Log & Execution History — FocusLock

This log tracks technical tasks, bug fixes, refactorings, and intelligence setups executed on the FocusLock codebase.

---

### [TASK-009] Fix Blocking Activity Launch & No-Redirection Over Restricted Apps (Instagram)
- **Date**: 2026-10-23
- **Objective**: Fix bug where restricted apps (like Instagram) could be opened or re-opened from the launcher without redirection to FocusLock's Blocking Screen.
- **Root Causes**:
  1. `BlockingActivity` used `taskAffinity=""` and `launchMode="singleTop"` in `AndroidManifest.xml`. Empty affinity prevented Android WindowManager from elevating the blocking task over newly active foreground applications when launched from a background AccessibilityService.
  2. Missing `FLAG_ACTIVITY_REORDER_TO_FRONT` and Android 14+ (API 34+) `ActivityOptions` with background activity start mode allowed.
  3. When OS Background Activity Launch (BAL) or OEM battery/window restrictions silently suppressed `startActivity`, no exception was thrown, marking `activityStarted = true` and bypassing displacement fallbacks.
  4. Rapid window transitions during Instagram app startup triggered the 250ms debounce before `BlockingActivity` surfaced, dropping all subsequent launch requests and leaving Instagram in the foreground.
  5. `AppDetailsViewModel.updateLimit()` hardcoded `updateBlockedPackage(pkg, isBlocked = false)`, unblocking memory caches even when the user lowered the limit below tracked usage.
  6. Neither `AppDetailsViewModel` nor `AppSelectionViewModel` invoked `refreshBlockedPackagesCache()` on limit or protection changes, keeping in-memory blocked caches stale.
  7. In `refreshBlockedPackagesCache()`, applications with a `0L` daily limit or apps without an existing Room `daily_usage` entity evaluated to unblocked.
- **Key Changes**:
  1. `AndroidManifest.xml`: Configured `BlockingActivity` with `android:launchMode="singleTask"` and dedicated `android:taskAffinity="com.focuslock.app.blocking"`.
  2. `BlockingActivity.kt`: Added `setShowWhenLocked(true)` and `setTurnScreenOn(true)` in `onCreate()`. Maintained visible state across `onPause` for transient window transitions and reset in `onStop`/`onDestroy`. Updated `onNewIntent` to refresh visible state.
  3. `FocusLockAccessibilityService.kt`:
     - Added `FLAG_ACTIVITY_REORDER_TO_FRONT` and Android 14+ `ActivityOptions.makeBasic()` with background activity launch permission.
     - Synchronized `currentForegroundPackage = packageName` on fast-block detections.
     - Fixed debounce logic in `launchBlockScreen`: only debounce if `BlockingActivity.isBlockingScreenVisible` is already true.
     - Added 250ms safety confirmation verification coroutine: if `BlockingActivity` did not surface and the restricted app is still in the foreground, immediately displaces to Home and presents full-screen blocking notification.
     - Updated `refreshBlockedPackagesCache()` to evaluate 0-second limits as immediately blocked and query `queryAccurateUsageSeconds` if a Room record is not yet present.
  4. `AppDetailsViewModel.kt` & `AppSelectionViewModel.kt`:
     - Replaced hardcoded unblocking with `FocusLockAccessibilityService.refreshBlockedPackagesCache()`.
  5. `ExternalAppBlockingTest.kt`:
     - Added regression unit tests `testZeroSecondLimit_InstantlyBlocked()` and `testBlockingIntentFlagsContracts()`.
- **Verification**:
  - `testDebugUnitTest` passed (all unit tests passed, BUILD SUCCESSFUL in 4m 8s).
  - `assembleDebug` passed (BUILD SUCCESSFUL in 6m 17s).

---

### [TASK-008] Fix Background Screen-Off Timers (30m Continuous Screen Time & 5m Rest Timer Stop After 5m Screen Off)
- **Date**: 2026-10-23
- **Objective**: Ensure that when the phone screen is turned off, both the 30-minute continuous screen timer and the 5-minute rest timer stop and reset cleanly after 5 minutes, preventing background accumulation (e.g. 147:47 timer bug).
- **Root Causes**:
  1. `BreakReminderHelper.resetSession()` unconditionally marked `KEY_IS_SCREEN_ON = true` and `sessionStart = nowElapsed` when called from background `completeBreak()`, treating background screen-off time as active screen usage.
  2. `onScreenOn()` early-returned when `isScreenOn == true`, bypassing break completion checks and grace-period expiration checks.
  3. `getContinuousScreenUsageMillis()` calculated `nowElapsed - sessionStart` without checking if screen was interactive (`PowerManager.isInteractive`).
  4. 5-min grace expiration only used `Handler.postDelayed()`, which never ran during CPU deep sleep / Doze mode.
  5. `FloatingTimerService` and `FocusLockAccessibilityService` called `onScreenOn()` on background restarts even when the device was locked.
  6. `BreakAlertActivity.kt` continue button called `dismiss()` instead of resetting the continuous screen timer.
- **Key Changes**:
  1. `BreakReminderHelper.kt`:
     - Added `isScreenInteractive()` using `PowerManager.isInteractive` with test overrides.
     - `resetSession()` respects screen interactive state: sets `KEY_IS_SCREEN_ON = false` and `sessionStart = 0L` when screen is off.
     - `getContinuousScreenUsageMillis()` returns `0L` when screen is off for >= 5m and never accumulates screen-off time.
     - Added `ACTION_SCREEN_OFF_EXPIRED` with `scheduleScreenOffGraceAlarm()` via `AlarmManager.RTC_WAKEUP` / `setExactAndAllowWhileIdle()` to reliably stop both the 30m continuous screen time and 5m break timer in the background after 5 minutes screen off.
     - `onScreenOn()` evaluates break completion and grace expiration before early return.
  2. `BreakAlertActivity.kt`:
     - Header updates to "🎉 Wellness Break Completed" and "You're refreshed and ready!" when break completes.
     - "Break Complete! Continue ✓" button calls `BreakReminderHelper.resetSession()` to cleanly reset the continuous timer to 0:00.
  3. `FloatingTimerService.kt` & `FocusLockAccessibilityService.kt`:
     - Check `PowerManager.isInteractive` before calling `onScreenOn()` or showing the floating overlay.
  4. `BreakReminderActionReceiver.kt` & `AndroidManifest.xml`:
     - Added `ACTION_SCREEN_OFF_EXPIRED` broadcast handler.
  5. `BreakReminderTest.kt`:
     - Added unit tests for screen off >= 5m stopping both 30m timer and 5m break timer, and background screen-off time never accumulating.
- **Verification**:
  - `testDebugUnitTest` passed (all 123+ tests passed in 30s).
  - `assembleDebug` passed (BUILD SUCCESSFUL in 1m 4s).

---

### [TASK-007] Critical Bug Fix: Warnings System, Reliable Daily Limit Enforcement, and Screen-Off Break Countdown
- **Date**: 2026-10-23
- **Objective**: Fix 3 production-critical bugs:
  1. 10-minute and 5-minute warnings do not appear.
  2. Daily app limit is not enforced reliably (hours of use allowed, reopening Instagram restores access).
  3. 5-minute break timer stops when the screen turns off.
- **Root Causes & Solutions**:
  1. **Bug 1 (Warnings)**:
     - *Cause*: No warning notification dispatcher or DB persistence existed.
     - *Fix*: Added `MIGRATION_4_5` altering `daily_usage` with `warning10Triggered` and `warning5Triggered`. Implemented dynamic warning threshold checks in `QuotaEngine` (`remainingTime <= 10m` and `<= 5m`) with at-most-once delivery guarantee per local calendar day, immediate evaluation on startup, and `NotificationManagerCompat` permission checking with floating overlay alert fallback.
  2. **Bug 2 (Limit Enforcement & Keyboard Bypass)**:
     - *Cause*: Input Method Editors (soft keyboards) were not excluded, causing typing in Instagram to reset `currentForegroundPackage` and kill the monitoring ticker. Furthermore, monitoring loop didn't break on block, and `reconcileWithAndroidUsageStats` compared against stale limits.
     - *Fix*: Added `isInputMethodOrTransientOverlay` recognizing IME packages. Added immediate `break` in accessibility loop upon blocking. Reconciliations preserve `isBlocked = existing.isBlocked || (reconciledSeconds >= quota)`.
  3. **Bug 3 (5-Minute Break Across Screen Off)**:
     - *Cause*: `BreakReminderHelper.onScreenOff()` and `expireSessionIfGracePeriodOver()` deleted `KEY_BREAK_ACTIVE` and `KEY_BREAK_START_ELAPSED` from SharedPreferences. `BreakAlertActivity.kt` decremented an in-memory integer.
     - *Fix*: Added persistent monotonic & wall-clock deadline timestamps, derived remaining time from elapsed monotonic time, scheduled `AlarmManager.RTC_WAKEUP` exact completion alarm, restored break across device reboot using wall diff, and made `completeBreak()` idempotent firing notification once. Decoupled break timer from 30m continuous screen wellness timer.
- **Files Modified**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/focuslock/app/core/usage/RealUsageTracker.kt`
  - `app/src/main/java/com/focuslock/app/core/util/AndroidPermissionChecker.kt`
  - `app/src/main/java/com/focuslock/app/core/util/BreakReminderActionReceiver.kt`
  - `app/src/main/java/com/focuslock/app/core/util/BreakReminderHelper.kt`
  - `app/src/main/java/com/focuslock/app/data/local/FocusLockDatabase.kt`
  - `app/src/main/java/com/focuslock/app/data/local/dao/UsageDao.kt`
  - `app/src/main/java/com/focuslock/app/data/local/entity/DailyUsageEntity.kt`
  - `app/src/main/java/com/focuslock/app/data/repository/UsageRepository.kt`
  - `app/src/main/java/com/focuslock/app/domain/engine/QuotaEngine.kt`
  - `app/src/main/java/com/focuslock/app/presentation/breakalert/BreakAlertActivity.kt`
  - `app/src/main/java/com/focuslock/app/service/FocusLockAccessibilityService.kt`
  - `app/src/test/java/com/focuslock/app/core/util/BreakReminderTest.kt`
  - `app/src/test/java/com/focuslock/app/domain/engine/QuotaEngineTest.kt`
  - `app/src/test/java/com/focuslock/app/service/ExternalAppBlockingTest.kt`
- **Verification**:
  - `testDebugUnitTest` passed with 0 errors (121+ tests passed in 1m 1s).
  - `assembleDebug` passed with 0 errors (BUILD SUCCESSFUL in 52s).
- **Outcome**: Warnings deliver dynamically and reliably, exhausted apps remain strictly blocked across re-openings, and 5-minute break timer continues accurately across screen off and backgrounding.

---

### [TASK-006] Fix Instagram Close-Without-Redirect Bug & Enforcement Display
- **Date**: 2026-10-22
- **Objective**: Fix critical bug where Instagram closed/got pushed away upon reaching daily limit instead of showing FocusLock's Blocking Screen ("You've reached your daily limit").
- **Root Cause & Architectural Enhancements**:
  1. In `FocusLockAccessibilityService.kt`, `performGlobalAction(GLOBAL_ACTION_HOME)` was called directly before `launchBlockScreen()`. WindowManager executed the asynchronous Home command simultaneously, immediately minimizing `BlockingActivity` upon start.
  2. Removed premature `GLOBAL_ACTION_HOME` calls. `BlockingActivity` now launches directly on top of the restricted application. `GLOBAL_ACTION_HOME` is preserved strictly as a safety fallback if `startActivity` fails.
  3. Removed conflicting `FLAG_ACTIVITY_REORDER_TO_FRONT` intent flag.
  4. Updated `BlockingScreen` to display: "You've reached your daily limit.", "$appName: ${limitMinutes}-minute daily limit.", "Your access will reset at local midnight.", and actual tracked usage vs configured limit.
  5. Added `checkMonitoringHealth()` to `FocusLockAccessibilityService` checking Usage Access permission and alerting the user if disabled.
- **Files Modified**:
  - `app/src/main/java/com/focuslock/app/service/FocusLockAccessibilityService.kt`
  - `app/src/main/java/com/focuslock/app/presentation/BlockingActivity.kt`
  - `app/src/test/java/com/focuslock/app/service/ExternalAppBlockingTest.kt`
- **Verification**:
  - `testDebugUnitTest` passed with 0 failures (`BUILD SUCCESSFUL in 1m 25s`).
  - `assembleDebug` passed with 0 errors (`BUILD SUCCESSFUL in 35s`).
- **Outcome**: Instagram and protected apps display the full-screen blocking screen immediately without closing to the launcher.

---

### [TASK-005] Persistent 30-Minute Wellness Timer with 5-Minute Screen-Off Grace Period
- **Date**: 2026-10-22
- **Objective**: Implement 5-minute screen-off grace period for the 30-minute wellness timer. Screen ON within 5m resumes previous session preserving exact remaining time. Screen OFF >= 5m expires session and starts fresh 30m session on next screen ON. Screen-off time does not count toward 30m usage. Safe recovery across process restarts and device reboots.
- **Root Cause & Architectural Enhancements**:
  1. `BreakReminderHelper` previously reset continuous usage whenever screen turned off (with only a 3s misclick buffer).
  2. Sub-session accumulation and screen-off timestamps were not persisted across process restarts or reboots.
  3. `BootRecoveryReceiver` wiped the continuous break session indiscriminately on device reboot.
  4. Upgraded `BreakReminderHelper` with `WellnessClock` abstraction, monotonic session accumulation, scheduled delayed expiration, and reboot wall-clock validation.
- **Files Modified**:
  - `app/src/main/java/com/focuslock/app/core/util/BreakReminderHelper.kt`
  - `app/src/main/java/com/focuslock/app/service/BootRecoveryReceiver.kt`
  - `app/build.gradle.kts`
  - `app/src/test/java/com/focuslock/app/core/util/BreakReminderTest.kt`
- **Verification**:
  - All 14 tests in `BreakReminderTest` passed cleanly (`BUILD SUCCESSFUL in 50s`).
  - All unit tests across project passed (`testDebugUnitTest`).
  - Full Android debug build succeeded (`assembleDebug` in 1m 5s).
- **Outcome**: 100% deterministic 5-minute screen-off grace period, reliable reboot recovery, and exact remaining countdown preservation.

---

### [TASK-004] Fix FocusLock App Limit Bypass, Re-Launch Bug & Daily Reset Logic
- **Date**: 2026-10-22
- **Objective**: Fix restricted app re-launch bypasses (Instagram, YouTube) through launcher taps, Recent Apps, app switching, and process recreation; ensure daily quota resets strictly at local calendar midnight (`LocalDate`).
- **Root Cause**:
  1. `BlockingActivity` overlay did not displace the restricted app's task from foreground when opened from Recent Apps or deep links.
  2. `TimeChangeReceiver` wiped `blockedPackagesCache` leaving it empty until screen toggled.
  3. No proactive 5s background monitor existed for midnight rollover without opening FocusLock.
  4. Fractional seconds were lost via `% 2L` in session finish.
- **Files Modified**:
  - `app/src/main/java/com/focuslock/app/domain/engine/QuotaEngine.kt`
  - `app/src/main/java/com/focuslock/app/service/FocusLockAccessibilityService.kt`
  - `app/src/main/java/com/focuslock/app/presentation/BlockingActivity.kt`
  - `app/src/main/java/com/focuslock/app/service/TimeChangeReceiver.kt`
  - `app/src/main/java/com/focuslock/app/service/BootRecoveryReceiver.kt`
  - `app/src/test/java/com/focuslock/app/service/ExternalAppBlockingTest.kt`
  - `app/src/test/java/com/focuslock/app/core/update/AppUpdateManagerTest.kt`
- **Verification**: `testDebugUnitTest` passed (all 103 tests passed, including 18 dedicated blocking/reset tests), `assembleDebug` passed.
- **Outcome**: Complete elimination of bypasses, reliable local midnight rollover, and robust enforcement state machine.

---

### [TASK-003] Continuous 30-Minute Screen Wellness Reminder System & 3D Animations
- **Date**: 2026-10-22
- **Objective**: Upgrade the top timer and break reminder into a complete wellness reminder system:
  - Continuous 30-minute monotonic screen-on timer (app switches do not reset; screen-off resets session; fresh session on screen-on).
  - Pleasant alarm audio (synthesized ambient chime + TTS spoken guidance: *"Please, sir! It's time to take care of yourself. Go for a short walk, rest your eyes, and drink a glass of water."*).
  - Custom audio/song and video file pickers via Storage Access Framework with URI permission persistence.
  - 3D-styled animated walking character and drinking water glass Canvas animations, plus custom video loop player.
  - 3 clear user actions: "Take a 5-Minute Break", "Remind Me in 5 Minutes" (Snooze), and "Dismiss".
  - Floating top timer synchronized with break mode and continuous session countdown.
  - Customization settings in `SettingsScreen` with preview, volume, and vibration toggles.
- **Files Modified / Created**:
  - `app/src/main/java/com/focuslock/app/core/wellness/WellnessPreferences.kt`
  - `app/src/main/java/com/focuslock/app/core/wellness/WellnessSoundManager.kt`
  - `app/src/main/java/com/focuslock/app/core/util/BreakReminderHelper.kt`
  - `app/src/main/java/com/focuslock/app/core/util/BreakReminderActionReceiver.kt`
  - `app/src/main/java/com/focuslock/app/core/overlay/FloatingTimerManager.kt`
  - `app/src/main/java/com/focuslock/app/presentation/breakalert/BreakAlertActivity.kt`
  - `app/src/main/java/com/focuslock/app/presentation/breakalert/WellnessAnimations.kt`
  - `app/src/main/java/com/focuslock/app/presentation/settings/SettingsScreen.kt`
  - `app/src/test/java/com/focuslock/app/core/util/BreakReminderTest.kt`
- **Verification**: `testDebugUnitTest` passed (all unit tests passed), `BreakReminderTest` suite passed.
- **Outcome**: Successfully implemented complete continuous screen wellness system without breaking existing quotas or blocking.

### [TASK-002] Persistent Project Intelligence & Standardized Analysis Suite
- **Date**: 2026-10-22
- **Objective**: Deploy standardized `analysis/` documentation suite and integrate Global/Project rules (Hinglish communication, Expert Roles, Incremental analysis).
- **Files Created / Updated**:
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
  - `CHANGELOG.md`
  - `AGENTS.md`
  - `.agents/rules/project-intelligence.md`
  - `.agent/rules/project-intelligence.md`
- **Verification**: JVM test suite passed (25 tests passed), debug APK build passed (`assembleDebug`).
- **Outcome**: Complete project intelligence setup completed with zero application logic modifications.

---

### [TASK-001] Instagram Immediate Blocking & 1-Minute Quota Testing Preset
- **Date**: 2026-10-22
- **Objective**: Eliminate 1–2 second delay and home screen flash when opening Instagram after quota exhaustion; enforce strict daily limit with clear lockscreen wording.
- **Root Cause**: AccessibilityService performed asynchronous usage scans on window change and invoked `GLOBAL_ACTION_HOME` with synthetic delays.
- **Files Modified**:
  - `app/src/main/java/com/focuslock/app/service/FocusLockAccessibilityService.kt`
  - `app/src/main/java/com/focuslock/app/presentation/BlockingActivity.kt`
  - `app/src/main/java/com/focuslock/app/presentation/apps/AppDetailsScreen.kt`
  - `app/src/main/java/com/focuslock/app/presentation/apps/AppDetailsViewModel.kt`
  - `app/src/main/java/com/focuslock/app/service/TimeChangeReceiver.kt`
  - `app/src/test/java/com/focuslock/app/service/ExternalAppBlockingTest.kt`
- **Verification**:
  - `ExternalAppBlockingTest`: 6 tests passed (1-min limit, Room persistence, reopening resilience, 30s emergency, midnight reset).
  - Full suite: 25 tests passed (`testDebugUnitTest`).
- **Outcome**: Resolved delay, eliminated screen flash, and verified zero-lag blocking.
