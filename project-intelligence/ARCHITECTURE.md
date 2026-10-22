# Architecture & Data Flow — FocusLock

## 1. Architectural Pattern: Clean Architecture + MVVM

FocusLock strictly separates concerns into Presentation, Domain, Data, and System Service layers.

```
┌─────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                      │
│  Compose UI Components • ViewModels • Navigation Graph     │
│  MainActivity • BlockingActivity • BreakAlertActivity       │
└──────────────────────────────┬──────────────────────────────┘
                               │ Observes State / Dispatches Intents
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                        DOMAIN LAYER                         │
│  QuotaEngine • UseCases (TrackUsage, EvaluateBlockStatus)   │
│  Domain Models (AppCategory, ProtectionHealth)              │
│  TimeProvider abstraction (Zero Android Framework binding) │
└──────────────────────────────┬──────────────────────────────┘
                               │ Requests Data & Updates
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                         DATA LAYER                          │
│  AppRepositoryImpl • UsageRepositoryImpl                    │
│  FocusLockDatabase (Room v4) • DAOs (ProtectedApp, Usage)   │
└──────────────────────┬──────────────────────┬───────────────┘
                       │                      │
                       ▼                      ▼
           ┌──────────────────────┐  ┌────────────────────────┐
           │   SQLite Database    │  │ Android OS Framework   │
           │  (Local App Sandbox) │  │ UsageStatsManager       │
           └──────────────────────┘  └────────────────────────┘
```

---

## 2. Real-Time Tracking & Enforcement Pipeline

Tracking and blocking operate via two intertwined mechanisms: high-frequency real-time window detection and historical ground-truth reconciliation.

### Data Flow Diagram

```
User launches an App (e.g., Instagram)
          │
          ▼
AccessibilityService.onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)
          │
          ├───────────────────────────────────────────────────────┐
          ▼                                                       ▼
[isExcludedPackage?]                                    [blockedPackagesCache?]
  • Yes: Record previous session & exit                   • Contains pkg:
  • No: Proceed                                               ↳ launchBlockScreen() immediately (0ms)
          │                                                   ↳ BlockingActivity covers foreground
          ▼                                                   ↳ Exit
[New App Foregrounded?]
  • Save session elapsed duration for previous package
  • Start new session elapsed timestamp
          │
          ▼
serviceScope.launch {
  1. Evaluate Quota Fast (evaluateQuota(packageName) from Room)
     • If EMERGENCY_ACTIVE ➔ Keep timer active, permit usage
     • If isBlocked ➔ Cache in blockedPackagesCache ➔ launchBlockScreen()
  2. Background Reconciliation (RealUsageTracker.syncPackageUsage())
     • Query UsageStatsManager UsageEvents
     • Reconcile session durations into Room
     • If reCheck.isBlocked ➔ Cache & launchBlockScreen()
  3. Active Session Monitoring Ticker
     • Every 2 seconds: Record incremental delta to QuotaEngine
     • Every 10 seconds: Re-sync with UsageStatsManager
     • Check reCheck.isBlocked ➔ If exceeded, launchBlockScreen()
}
```

---

## 3. Key Architectural Decisions

### 3.1 Synchronous In-Memory Blocked Cache (`blockedPackagesCache`)
- **Problem:** Android AccessibilityService event dispatch occurs asynchronously across Binder IPC. If a service launches a coroutine, reads Room DB, and scans thousands of `UsageEvents` before deciding whether to block, 500ms–2000ms elapse during which the restricted app displays its UI.
- **Solution:** `FocusLockAccessibilityService` maintains a thread-safe `ConcurrentHashMap` set of packages that have already exhausted their quota for today. When `TYPE_WINDOW_STATE_CHANGED` fires, if the package is in `blockedPackagesCache` and no emergency session is active, `launchBlockScreen()` is invoked **synchronously on the main thread**, covering the app in 0ms.

### 3.2 Dual-Source Reconciliation (UsageEvents + Accessibility Active Ticker)
- **Problem:** `UsageStatsManager` events can lag by a few seconds on certain Android OEMs. Conversely, AccessibilityService can be killed by aggressive battery management.
- **Solution:**
  1. AccessibilityService keeps an active 2-second heartbeat ticker during foreground usage.
  2. `RealUsageTracker` queries ground-truth Android `UsageEvents` (MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND).
  3. The reconciled duration in `DailyUsageEntity` takes `maxOf(measuredSeconds, existing.usedSeconds)`, preventing quota loss across service restarts while strictly avoiding double-counting.

### 3.3 Calendar-Day Boundary Policy & Midnight Resets
- Quotas are strictly tied to a local date string (`yyyy-MM-dd`) in the device's default timezone, rather than a rolling 24-hour window.
- The `DailyUsageEntity` primary key index is `(packageName, localDate)`.
- When midnight occurs (handled by `TimeChangeReceiver`, `BootRecoveryReceiver`, or dashboard check), `checkAndPerformMidnightReset()` creates fresh usage entries with `usedSeconds = 0`, resetting emergency eligibility and clearing `blockedPackagesCache`.

### 3.4 30-Second Emergency Bypass Architecture
- When an app reaches its limit, users are granted exactly **one** 30-second emergency access window per calendar day.
- State transitions: `EXHAUSTED` (can use emergency) ➔ `EMERGENCY_ACTIVE` (30s window active) ➔ `BLOCKED_UNTIL_RESET` (permanently locked until 00:00:00).
- Timestamps are persisted in `DailyUsageEntity` (`emergencyStartedAt`, `emergencyExpiresAt`, `emergencyUsed = true`) to prevent bypass by killing or restarting the app.

### 3.5 Tamper-Resistant Security (Hardcore Mode & PIN)
- Settings and limit reductions can be locked behind a cryptographic PIN.
- Uses `PBKDF2WithHmacSHA256` with 10,000 iterations and a 16-byte random salt stored securely in `EncryptedSharedPreferences`.
- In Hardcore Mode, modifying quotas requires completing a non-cancellable 60-second cooldown timer to prevent impulsive override during dopamine cravings.
