# Known Issues & Android OS Caveats — FocusLock

This document tracks confirmed issues, resolved bugs, root-cause analyses, and structural Android operating system constraints.

---

## 1. Resolved Historical Issues

### ISSUE-001: 500ms–2000ms Delay When Opening Exhausted Instagram
- **Reported:** User observed that opening Instagram when its daily limit was reached allowed Instagram's feed/reels to render for 1–2 seconds before the lock screen appeared, followed by a jarring flash of the home screen.
- **Root Cause:**
  1. `FocusLockAccessibilityService.checkAndEnforceBlock` ran `realUsageTracker.syncPackageUsage(packageName)` *before* checking whether the app was already blocked. `syncPackageUsage` iterated through tens of thousands of system `UsageEvents`, blocking execution for hundreds of milliseconds.
  2. `launchBlockScreen()` executed `performGlobalAction(GLOBAL_ACTION_HOME)` followed by a coroutine `delay(100L)` before calling `startActivity(BlockingActivity)`, causing the home launcher to visibly flash and risking background activity launch denial.
- **Resolution:**
  1. Implemented a synchronous in-memory `blockedPackagesCache` in `FocusLockAccessibilityService`. When an exhausted app is foregrounded, `launchBlockScreen()` is invoked immediately on the main thread in 0ms.
  2. Removed `GLOBAL_ACTION_HOME` from the primary path; `startActivity(intent)` is called directly with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`, instantly covering the restricted app.
  3. `performGlobalAction(GLOBAL_ACTION_HOME)` was retained strictly as an error fallback if `startActivity` is rejected by the OS.
- **Status:** **Resolved & Verified via unit tests**.

---

### ISSUE-002: Quota Erasure / Ghost Anomaly on Repeated Launches
- **Reported:** When users rapidly opened and closed a blocked app repeatedly, Room database used seconds occasionally reverted or reset to lower values.
- **Root Cause:** A race condition occurred where a partial session event reported lower elapsed milliseconds than the cumulative daily total already persisted in Room.
- **Resolution:** Updated `RealUsageTracker.syncPackageUsage()` and `QuotaEngine.recordUsage()` to take `maxOf(measuredSeconds, existing.usedSeconds)`.
- **Status:** **Resolved** (Commit `48bc175`).

---

### ISSUE-003: App Limit Bypass, Re-Launch Bug & Daily Reset Logic
- **Reported:** When Instagram reached its daily limit, repeated launcher taps, Recent Apps re-entry, app switching, or external links allowed interaction. `TimeChangeReceiver` wiped the blocked cache leaving it empty until screen toggled. Midnight reset did not proactively rollover without opening FocusLock. Fractional seconds were lost via `% 2L`.
- **Root Cause:**
  1. `BlockingActivity` alone did not displace the restricted app's task from the foreground when launched from Recent Apps or deep links.
  2. `TimeChangeReceiver` called `clearBlockedCache()` instead of refreshing, leaving `blockedPackagesCache` empty on time changes.
  3. No proactive background calendar date check existed for local midnight rollover without opening the app.
  4. Live usage tracking used `elapsedSecs % 2L` in session finish, dropping fractional seconds.
- **Resolution:**
  1. Added immediate foreground displacement via `performGlobalAction(GLOBAL_ACTION_HOME)` paired with instant `BlockingActivity` overlay launch.
  2. Implemented smart 250ms debouncer preventing duplicate Intent spam without dropping app displacement.
  3. Continuous 5-second calendar date monitor (`LocalDate`) triggers midnight rollover and unblocks apps for the new day automatically.
  4. Replaced cache wipe with `refreshBlockedPackagesCache()` in `TimeChangeReceiver` and `BootRecoveryReceiver` to prevent time-tamper bypasses.
  5. Implemented `EnforcementState` machine (`UNDER_LIMIT`, `LIMIT_REACHED`, `TEMPORARY_BYPASS_ACTIVE`, `DAILY_RESET_PENDING`) in `QuotaEngine`.
  6. Live usage accounting switched to monotonic delta seconds without fractional loss.
- **Status:** **Resolved & Verified via 18 comprehensive tests**.

---

## 2. Inherent Android OS Architectural Constraints

### CONSTRAINT-001: Launcher Icon Tap Pre-emption
- **Constraint:** Standard Android operating system security prohibits any non-root, non-OEM application from intercepting launcher icon taps *before* the operating system initiates the launch intent for that application.
- **Impact:** An app blocker cannot intercept launcher clicks before the target process begins opening; it must detect the package transition via `AccessibilityService.TYPE_WINDOW_STATE_CHANGED` immediately upon foregrounding and launch the blocking overlay.
- **Mitigation:** Zero-delay in-memory caching ensures the blocking overlay is brought to the top before the target app can render its interactive UI.

---

### CONSTRAINT-002: Background Activity Launch (BAL) Restrictions
- **Constraint:** Starting in Android 10 (API 29) and tightened in Android 14/15, background services cannot arbitrarily launch activities unless the app holds the `SYSTEM_ALERT_WINDOW` permission or the service has active window focus.
- **OEM Variations:** Xiaomi (MIUI/HyperOS), OPPO/Realme (ColorOS), and Vivo (FuntouchOS) impose aggressive background restrictions that may block `startActivity()` if the device display state is fluctuating or if overlay permissions are revoked.
- **Mitigation:**
  1. The app requests `SYSTEM_ALERT_WINDOW` during onboarding and settings.
  2. If `startActivity()` throws an exception, `launchBlockScreen()` automatically invokes `performGlobalAction(GLOBAL_ACTION_HOME)` to minimize the distraction app and fires a high-priority notification with a full-screen intent.

---

### CONSTRAINT-003: Meta Graph API Private Browsing Restrictions
- **Constraint:** The official Meta Graph API only exposes business/creator metrics (post impressions, follower counts). Meta strictly prohibits third-party apps from accessing personal browsing history, private reels watched, or DM contents.
- **Design Guarantee:** FocusLock never promises or attempts to extract private in-app content via APIs. All digital wellbeing and quota enforcement operates 100% locally on-device using Android OS `UsageStats` and `AccessibilityService` scroll detection.
