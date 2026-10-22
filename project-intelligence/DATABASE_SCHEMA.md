# Database Schema & Room Persistence — FocusLock

## 1. Database Overview

- **Database Name:** `focuslock_local.db`
- **Location:** On-device internal app sandbox (`/data/data/com.focuslock.app/databases/`)
- **Current Version:** `4`
- **Export Schema:** `false`
- **Configuration Class:** [`FocusLockDatabase.kt`](file:///d:/Be%20Best/app/src/main/java/com/focuslock/app/data/local/FocusLockDatabase.kt)

---

## 2. Table Definitions & Entities

### 2.1 `protected_apps` (`ProtectedAppEntity`)
Stores all applications tracked by FocusLock and their daily quota configurations.

| Column | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `packageName` | `TEXT` | `NO` | - | **Primary Key**. Package identifier (e.g. `com.instagram.android`). |
| `displayName` | `TEXT` | `NO` | `""` | User-friendly app label (e.g. "Instagram"). |
| `dailyLimitSeconds` | `INTEGER` | `NO` | `1800` | Configured daily limit in seconds (default 30 minutes). |
| `enabled` | `INTEGER` (Boolean) | `NO` | `1` | Whether enforcement and active tracking are active. |
| `category` | `TEXT` | `NO` | `'OTHER'` | Category name (`SOCIAL`, `VIDEO`, `GAMING`, `MESSAGING`, `PRODUCTIVITY`, `OTHER`). |
| `addedAt` | `INTEGER` | `NO` | System time | Millisecond timestamp when app was first protected. |

---

### 2.2 `daily_usage` (`DailyUsageEntity`)
Stores aggregated daily foreground screen time per application per local calendar day.

| Column | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | `NO` | `0` | **Primary Key** (Auto-generate). |
| `packageName` | `TEXT` | `NO` | - | App package identifier. |
| `localDate` | `TEXT` | `NO` | - | Date string in format `yyyy-MM-dd`. |
| `timezoneId` | `TEXT` | `NO` | - | Timezone ID (e.g. `Asia/Kolkata`). |
| `usedSeconds` | `INTEGER` | `NO` | `0` | Total reconciled foreground usage in seconds. |
| `quotaSeconds` | `INTEGER` | `NO` | `1800` | Snapshot of configured quota for that calendar day. |
| `isBlocked` | `INTEGER` (Boolean) | `NO` | `0` | Whether usage has reached/exceeded `quotaSeconds`. |
| `emergencyUsed` | `INTEGER` (Boolean) | `NO` | `0` | Whether the single 30s emergency bypass was consumed. |
| `emergencyStartedAt` | `INTEGER` | `NO` | `0` | Epoch millis when emergency access started. |
| `emergencyExpiresAt` | `INTEGER` | `NO` | `0` | Epoch millis when emergency access ends. |
| `emergencyElapsedRealtimeAtStart` | `INTEGER` | `NO` | `0` | Monotonic elapsed realtime when emergency started. |
| `sessionCount` | `INTEGER` | `NO` | `0` | Total number of discrete app sessions recorded. |
| `longestSessionSeconds` | `INTEGER` | `NO` | `0` | Duration of longest single session in seconds. |
| `firstActivityAt` | `INTEGER` | `NO` | `0` | Millis timestamp of first launch of the day. |
| `lastActivityAt` | `INTEGER` | `NO` | `0` | Millis timestamp of most recent launch of the day. |
| `approximateScrollCount` | `INTEGER` | `NO` | `0` | Approximate vertical scroll interactions observed. |
| `activitySource` | `TEXT` | `NO` | `'USAGE_EVENTS'` | Data origin (`'USAGE_EVENTS'`, `'ACCESSIBILITY_ACTIVE'`). |
| `activityConfidence` | `TEXT` | `NO` | `'EXACT'` | Metric confidence (`'EXACT'`, `'ESTIMATED'`). |
| `updatedAt` | `INTEGER` | `NO` | Current millis | Timestamp of last database record update. |

**Indexes:**
- Unique composite index on `(packageName, localDate)` prevents duplicate daily rows.

---

### 2.3 `usage_sessions` (`UsageSessionEntity`)
Detailed individual foreground sessions reconstructed from `UsageEvents`.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `INTEGER` (PK auto) | Unique session ID. |
| `packageName` | `TEXT` | App package identifier. |
| `localDate` | `TEXT` | Date string `yyyy-MM-dd`. |
| `sessionStart` | `INTEGER` | Start epoch milliseconds. |
| `sessionEnd` | `INTEGER` | End epoch milliseconds. |
| `durationSeconds` | `INTEGER` | Foreground session duration in seconds. |

---

### 2.4 `focus_sessions` (`FocusSessionEntity`)
Tracks deliberate Pomodoro-style Focus Sessions.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `INTEGER` (PK auto) | Unique session ID. |
| `name` | `TEXT` | User session label (e.g. "Deep Work"). |
| `startedAt` | `INTEGER` | Start epoch timestamp. |
| `plannedEndAt` | `INTEGER` | Scheduled end epoch timestamp. |
| `actualEndAt` | `INTEGER` | Actual termination epoch timestamp. |
| `isCompleted` | `INTEGER` | Whether completed without early exit. |
| `blockedAppsJson` | `TEXT` | JSON list of packages restricted during session. |

---

### 2.5 `protection_events` (`ProtectionEventEntity`)
Audit log for security and quota events (tampering attempts, midnight resets, emergency uses).

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `INTEGER` (PK auto) | Unique event ID. |
| `eventType` | `TEXT` | Event tag (e.g. `TIMEZONE_CHANGED`, `EMERGENCY_USED`). |
| `details` | `TEXT` | Contextual explanation. |
| `timestamp` | `INTEGER` | Milliseconds timestamp. |

---

### 2.6 `user_settings` (`UserSettingsEntity`)
Stores global user preferences and security configurations.

| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | `INTEGER` (PK, fixed=1) | Singleton settings row. |
| `hardcoreModeEnabled` | `INTEGER` | If true, enables anti-tampering friction cooldowns. |
| `cooldownDurationMinutes` | `INTEGER` | Delay duration before settings can be changed (default 1 min). |
| `pinHash` | `TEXT` | Salted PBKDF2 hash of user PIN (or empty). |
| `pinSalt` | `TEXT` | Base64-encoded 16-byte random salt. |
| `floatingTimerEnabled` | `INTEGER` | Whether the floating screen-time pill overlay is enabled. |
| `themeMode` | `TEXT` | `'SYSTEM'`, `'LIGHT'`, or `'DARK'`. |

---

## 3. Database Migrations

### Migration 2 ➔ 3 (`MIGRATION_2_3`)
Adds emergency session persistence columns to `daily_usage`:
```sql
ALTER TABLE daily_usage ADD COLUMN emergencyUsed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN emergencyStartedAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN emergencyExpiresAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN emergencyElapsedRealtimeAtStart INTEGER NOT NULL DEFAULT 0;
```

### Migration 3 ➔ 4 (`MIGRATION_3_4`)
Adds analytics and session fidelity columns to `daily_usage`:
```sql
ALTER TABLE daily_usage ADD COLUMN sessionCount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN longestSessionSeconds INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN firstActivityAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN lastActivityAt INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN approximateScrollCount INTEGER NOT NULL DEFAULT 0;
ALTER TABLE daily_usage ADD COLUMN activitySource TEXT NOT NULL DEFAULT 'USAGE_EVENTS';
ALTER TABLE daily_usage ADD COLUMN activityConfidence TEXT NOT NULL DEFAULT 'EXACT';
```
