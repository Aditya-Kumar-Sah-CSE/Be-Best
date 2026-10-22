# Database Schema & Room Migrations — FocusLock

FocusLock utilizes **AndroidX Room (SQLite)** as its persistent local storage engine.
- **Database Class**: `com.focuslock.app.data.local.FocusLockDatabase`
- **Database Name**: `focuslock.db`
- **Current Version**: `4`

---

## 1. Table Definitions & Schemas

### 1. `protected_apps`
Stores user-configured rules, daily limits, and categories for protected applications.

| Column | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `packageName` | `TEXT` | NO | PRIMARY KEY | Unique application package identifier (e.g., `com.instagram.android`). |
| `appName` | `TEXT` | NO | - | Display name of the application. |
| `dailyLimitSeconds` | `INTEGER` | NO | `1800` (30m) | Configured daily quota in seconds (supports 60s micro-preset). |
| `isProtected` | `INTEGER` | NO | `1` (true) | Boolean flag indicating whether quota is actively enforced. |
| `category` | `TEXT` | NO | `'SOCIAL'` | App category (`SOCIAL`, `ENTERTAINMENT`, `PRODUCTIVITY`, `GAMES`, `OTHER`). |
| `createdAt` | `INTEGER` | NO | Timestamp | Epoch millisecond timestamp of creation. |

---

### 2. `daily_usage`
Tracks cumulative daily usage, session metrics, and quota exhaustion flags per package and local date.

| Column | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `packageName` | `TEXT` | NO | PK (Composite) | Target application package. |
| `date` | `TEXT` | NO | PK (Composite) | Local ISO-8601 date string (`YYYY-MM-DD`). |
| `usedSeconds` | `INTEGER` | NO | `0` | Cumulative foreground seconds measured today. |
| `isExhausted` | `INTEGER` | NO | `0` (false) | Set to `1` when `usedSeconds >= dailyLimitSeconds`. |
| `sessionCount` | `INTEGER` | NO | `0` | Number of discrete app open events recorded. *(Added in Migration 4)* |
| `longestSessionSeconds` | `INTEGER` | NO | `0` | Longest uninterrupted foreground duration in seconds. *(Added in Migration 4)* |
| `firstActivityAt` | `INTEGER` | YES | `NULL` | Timestamp of first open event today. *(Added in Migration 4)* |
| `lastActivityAt` | `INTEGER` | YES | `NULL` | Timestamp of most recent activity today. *(Added in Migration 4)* |
| `approximateScrollCount` | `INTEGER` | NO | `0` | Estimated user scrolls during sessions. *(Added in Migration 4)* |
| `activitySource` | `TEXT` | NO | `'UNKNOWN'` | Tracking source (`USAGE_STATS_API`, `ACCESSIBILITY`, `SYSTEM`). *(Added in Migration 4)* |
| `activityConfidence` | `REAL` | NO | `1.0` | Confidence metric of tracking accuracy (0.0 to 1.0). *(Added in Migration 4)* |
| `emergencyUsed` | `INTEGER` | NO | `0` (false) | Flag indicating if emergency bypass was triggered today. *(Added in Migration 3)* |
| `emergencyStartedAt` | `INTEGER` | YES | `NULL` | Timestamp when emergency bypass began. *(Added in Migration 3)* |
| `emergencyExpiresAt` | `INTEGER` | YES | `NULL` | Timestamp when emergency bypass expires. *(Added in Migration 3)* |
| `emergencyElapsedRealtimeAtStart` | `INTEGER` | YES | `NULL` | Monotonic clock timestamp of emergency start. *(Added in Migration 3)* |

---

### 3. `usage_sessions`
Discrete foreground sessions recorded for granular analytics.

| Column | Type | Nullable | Description |
| :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | NO (PK AUTOINCREMENT) | Unique session identifier. |
| `packageName` | `TEXT` | NO | Target package name. |
| `date` | `TEXT` | NO | ISO date string (`YYYY-MM-DD`). |
| `startTime` | `INTEGER` | NO | Epoch millisecond of session start. |
| `endTime` | `INTEGER` | NO | Epoch millisecond of session end. |
| `durationSeconds` | `INTEGER` | NO | Total duration in seconds. |

---

### 4. `focus_sessions`
Pomodoro focus blocks that temporarily override app usage permissions.

| Column | Type | Nullable | Description |
| :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | NO (PK AUTOINCREMENT) | Unique focus block identifier. |
| `startTime` | `INTEGER` | NO | Epoch millisecond start time. |
| `plannedEndAt` | `INTEGER` | NO | Planned target end time. |
| `actualEndAt` | `INTEGER` | YES | Actual completion timestamp. |
| `isCompleted` | `INTEGER` | NO | Whether session was completed without cancellation. |
| `targetDurationSeconds` | `INTEGER` | NO | Target session duration. |

---

### 5. `user_settings`
Global application settings and security configuration.

| Column | Type | Nullable | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `INTEGER` | NO (PK) | `1` | Singleton configuration row ID. |
| `hardcoreModeEnabled` | `INTEGER` | NO | `0` | When enabled, prevents limit changes without cooldown & PIN. |
| `pinHash` | `TEXT` | YES | `NULL` | PBKDF2WithHmacSHA256 salted hash of security PIN. |
| `pinSalt` | `TEXT` | YES | `NULL` | Base64-encoded cryptographic salt. |
| `floatingTimerEnabled` | `INTEGER` | NO | `0` | Toggle for floating screen timer overlay. |
| `breakReminderEnabled` | `INTEGER` | NO | `1` | Toggle for 30-minute break reminder alert. |

---

## 2. Room Migration History

1. **Migration 1 ➔ 2**: Added `focus_sessions` table for Pomodoro blocks.
2. **Migration 2 ➔ 3**: Added emergency bypass tracking columns (`emergencyUsed`, `emergencyStartedAt`, `emergencyExpiresAt`, `emergencyElapsedRealtimeAtStart`) to `daily_usage`.
3. **Migration 3 ➔ 4**: Added detailed session metrics (`sessionCount`, `longestSessionSeconds`, `firstActivityAt`, `lastActivityAt`, `approximateScrollCount`, `activitySource`, `activityConfidence`) to `daily_usage`.
