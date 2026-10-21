# FocusLock — Technical Architecture & Engineering Specifications

FocusLock is a production-grade, local-first Android Digital Wellbeing & App Blocker application engineered in Kotlin and Jetpack Compose. Its mission is to curb social media and gaming addictions through hard daily quotas, resilient tracking, and intentional friction.

---

## 1. Core Architectural Overview

FocusLock is built adhering strictly to Clean Architecture and MVVM principles.

```
                    ┌───────────────────────────────┐
                    │      Presentation Layer       │
                    │   Compose UI / Activities     │
                    │   (Blocking, Dashboard, Apps) │
                    └───────────────┬───────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │         Domain Layer          │
                    │  UseCases, Policies, Health   │
                    └───────────────┬───────────────┘
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │          Data Layer           │
                    │  Room DB, Local Repositories  │
                    └───────┬───────────────┬───────┘
                            │               │
                            ▼               ▼
            ┌───────────────────┐       ┌───────────────────────┐
            │  Room Database    │       │ Android OS Framework  │
            │  5 SQLite Tables  │       │ UsageStats / Service  │
            └───────────────────┘       └───────────────────────┘
```

---

## 2. Tracking & Blocking Engine

### 2.1 Multi-Source Tracking Strategy
Android OS does not provide a single unified API that guarantees real-time foreground interception with zero battery penalty. FocusLock solves this with two complementary sources:
1. **AccessibilityService (`FocusLockAccessibilityService`):**
   - Subscribes to `TYPE_WINDOW_STATE_CHANGED`.
   - Intercepts foreground application transitions within milliseconds.
   - Calculates real-time session duration using `SystemClock.elapsedRealtime()`.
2. **UsageStatsManager (`UsageStatsManager`):**
   - Reconciles total time in foreground periodically.
   - Prevents duplicate counting by taking maximum reconciled duration.

### 2.2 Instant Block Enforcement
When a window state change occurs:
1. Package name is checked against `protected_apps`.
2. If `enabled == true`, query `daily_usage` for today's calendar date.
3. If `usedSeconds >= quotaSeconds`:
   - Immediately launch `BlockingActivity` with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_NO_ANIMATION`.
   - The user cannot interact with the blocked application.

---

## 3. Quota & Midnight Reset Logic

A naive 24-hour timer fails when a device restarts, goes to sleep, or changes timezones. FocusLock resolves this with a calendar-day boundary policy:
- Local date string format: `yyyy-MM-dd` in current `TimeZone.getDefault()`.
- Unique composite index on `(packageName, localDate)`.
- Upon any transition, boot completion (`BootRecoveryReceiver`), or time change (`TimeChangeReceiver`), the calendar date is verified.
- If date has advanced, new quota records are lazily or eagerly created with `usedSeconds = 0`.

---

## 4. Hardcore Mode & Security Anti-Tampering

- **PIN Security:** 
  - Standard SHA-256 is vulnerable to brute force. FocusLock implements `PBKDF2WithHmacSHA256` with 10,000 iterations and a 16-byte cryptographically secure random salt (`SecureRandom`).
  - Constant-time byte array comparison prevents timing attack vectors.
- **Friction Delay Cooldown:**
  - In Hardcore Mode, an unlock request starts a mandatory 60-second cooldown timer.
  - Limits cannot be increased impulsively while emotional cravings are highest.

---

## 5. Local-First Privacy Verification

FocusLock does not contain any analytics tracking SDKs, cloud backends, or network calls:
- All usage records reside in `focuslock_local.db` inside the Android app sandbox.
- Accessibility service disables window content retrieval (`canRetrieveWindowContent="false"`).
- Zero text, keystrokes, or screen contents are read or transmitted.
