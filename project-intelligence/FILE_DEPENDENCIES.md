# Component Dependencies & Interaction Maps — FocusLock

This document details the verified runtime and architectural relationships among FocusLock's components.

---

## 1. High-Level Dependency Graph

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION (Compose UI)                       │
│  MainActivity ───► NavGraph ───► Dashboard / AppDetails / Settings UI  │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Calls ViewModels
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        VIEWMODELS / PRESENTATION                       │
│  DashboardViewModel  AppDetailsViewModel  SettingsViewModel            │
└──────┬───────────────────────┬─────────────────────────┬───────────────┘
       │                       │                         │
       ▼                       ▼                         ▼
┌──────────────┐      ┌─────────────────┐       ┌─────────────────┐
│ UsageRepo    │      │ AppRepository   │       │ QuotaEngine     │
└──────┬───────┘      └────────┬────────┘       └────────┬────────┘
       │                       │                         │
       ▼                       ▼                         ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        ROOM LOCAL DATABASE (v4)                        │
│  FocusLockDatabase ──► UsageDao, ProtectedAppDao, UserSettingsDao       │
└───────────────────────────────────▲────────────────────────────────────┘
                                    │ Reads / Writes
       ┌────────────────────────────┼───────────────────────────┐
       │                            │                           │
┌──────┴────────────────────┐ ┌─────┴─────────────────────┐ ┌───┴─────────────┐
│ FocusLockAccessibilitySvc │ │ RealUsageTracker          │ │ FloatingTimerSvc│
│ • Detects foreground pkg  │ │ • Queries UsageEvents     │ │ • Overlays pill │
│ • Fast in-memory cache    │ │ • Reconciles sessions     │ │ • WindowManager │
│ • Launches BlockingAct    │ └───────────────────────────┘ └─────────────────┘
└──────────────┬────────────┘
               │ Launches
               ▼
┌───────────────────────────┐
│     BlockingActivity      │
│  • Covers foreground app  │
│  • One-time Emergency 30s │
│  • Returns user to Home   │
└───────────────────────────┘
```

---

## 2. Core Subsystem Relationship Details

### 2.1 Enforcement & Blocking Loop
- **`FocusLockAccessibilityService` ➔ `QuotaEngine`**:
  - The accessibility service evaluates application quotas using `FocusLockApp.instance.quotaEngine.evaluateQuota(packageName)`.
  - Incremental foreground time during an active session is committed via `quotaEngine.recordUsage(packageName, deltaSeconds)`.
- **`FocusLockAccessibilityService` ➔ `BlockingActivity`**:
  - When quota is exhausted (`evaluation.isBlocked == true`), `launchBlockScreen()` constructs an explicit Intent for `BlockingActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_REORDER_TO_FRONT`.
  - Fallback: If background activity starts are restricted, `performGlobalAction(GLOBAL_ACTION_HOME)` kicks the app to the home screen and displays a high-priority notification with full-screen intent.
- **`BlockingActivity` ➔ `FocusLockAccessibilityService`**:
  - When the user activates the 30-second emergency bypass, `BlockingActivity` calls `FocusLockAccessibilityService.updateBlockedPackage(packageName, isBlocked = false)` to permit access during the 30-second window.
  - When the 30 seconds expire, the accessibility ticker re-detects `BLOCKED_UNTIL_RESET`, re-adds the package to `blockedPackagesCache`, and re-displays `BlockingActivity`.

### 2.2 Ground-Truth Usage Reconciliation
- **`RealUsageTracker` ➔ `UsageStatsManager` & `UsageDao`**:
  - `RealUsageTracker.syncPackageUsage(packageName)` reads system `UsageEvents` between midnight and `currentTimeMillis()`.
  - It writes session records to `usage_sessions` and updates `DailyUsageEntity` via `usageDao.insertOrUpdateDailyUsage()`.
  - To prevent state erasure, it sets `usedSeconds = maxOf(measuredSeconds, existing.usedSeconds)`.
- **`FocusLockAccessibilityService` ➔ `RealUsageTracker`**:
  - Invokes `syncPackageUsage()` periodically (every 10 seconds during an active session, and when the user switches away to another app) to ensure exact alignment with OS metrics.

### 2.3 Universal Floating Timer Overlay
- **`FocusLockAccessibilityService` ➔ `FloatingTimerManager`**:
  - On every window transition, calls `FloatingTimerManager.ensureVisible(context)` to maintain the floating timer pill across apps.
- **`FloatingTimerService` ➔ `FloatingTimerManager`**:
  - The foreground service hosts the lifecycle of the overlay view attached via `WindowManager.addView(overlayView, layoutParams)`.
  - Requires `android.permission.SYSTEM_ALERT_WINDOW`.

### 2.4 Continuous Screen Break Monitor
- **`FocusLockAccessibilityService` ➔ `BreakReminderHelper`**:
  - Maintains `breakMonitorJob` polling every 5 seconds.
  - Tracks continuous screen-on time since last screen-off / lock.
  - When continuous screen-on time reaches 30 minutes, `BreakReminderHelper` triggers a notification and launches `BreakAlertActivity`.

### 2.5 Settings & Quota Editing Flow
- **`AppDetailsViewModel` ➔ `AppRepository` & `FocusLockAccessibilityService`**:
  - When user selects a new daily quota (e.g. 1m, 15m, 30m, 60m), `AppDetailsViewModel.updateLimit()` updates `ProtectedAppEntity` in Room.
  - Calls `FocusLockAccessibilityService.updateBlockedPackage(pkg, isBlocked = false)` so the new limit is immediately re-evaluated.
