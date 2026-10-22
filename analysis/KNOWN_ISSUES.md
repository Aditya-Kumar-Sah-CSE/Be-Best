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
