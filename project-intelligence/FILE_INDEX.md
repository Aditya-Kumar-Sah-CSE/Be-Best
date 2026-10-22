# File Index & Source Catalog — FocusLock

This catalog indexes the primary source files across the FocusLock codebase. It provides relative paths, core responsibilities, key classes/functions, dependencies, and affected features.

---

## 1. Application & Service Layer (`service/`)

| Relative Path | Responsibility | Key Classes & Methods | Key Dependencies | Affected Features |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/java/com/focuslock/app/FocusLockApp.kt` | Application entry point; initializes Room DB, repositories, engine, notification channels, and default distractions. | `class FocusLockApp : Application` | `FocusLockDatabase`, `AppRepositoryImpl`, `QuotaEngine`, `RealUsageTracker` | Whole application startup, DB injection, notification channels |
| `app/src/main/java/com/focuslock/app/service/FocusLockAccessibilityService.kt` | Monitors foreground window state transitions, detects protected apps, maintains in-memory blocked cache, triggers blocking overlay, tracks screen-on duration. | `class FocusLockAccessibilityService`, `onAccessibilityEvent()`, `launchBlockScreen()`, `checkAndEnforceBlock()`, `refreshBlockedPackagesCache()` | `QuotaEngine`, `RealUsageTracker`, `BlockingActivity`, `FloatingTimerManager` | Real-time app blocking, zero-lag interception, scroll tracking, screen break monitor |
| `app/src/main/java/com/focuslock/app/service/BootRecoveryReceiver.kt` | Listens to device boot completion (`BOOT_COMPLETED`, `QUICKBOOT_POWERON`) to re-arm midnight resets and restore floating overlays. | `class BootRecoveryReceiver : BroadcastReceiver` | `QuotaEngine`, `FloatingTimerService` | Reboot recovery, midnight quota consistency |
| `app/src/main/java/com/focuslock/app/service/TimeChangeReceiver.kt` | Detects system clock or timezone manipulation (`TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED`) and clears stale blocked caches. | `class TimeChangeReceiver : BroadcastReceiver` | `QuotaEngine`, `FocusLockAccessibilityService` | Anti-tampering against clock rewinding, midnight transition |

---

## 2. Domain Layer (`domain/`)

| Relative Path | Responsibility | Key Classes & Methods | Key Dependencies | Affected Features |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/java/com/focuslock/app/domain/engine/QuotaEngine.kt` | Pure domain quota calculator; evaluates protection states, starts emergency sessions, checks midnight reset eligibility. | `class QuotaEngine`, `evaluateQuota()`, `startEmergencySession()`, `recordUsage()`, `checkAndPerformMidnightReset()` | `ProtectedAppDao`, `UsageDao`, `TimeProvider`, `PermissionChecker` | Quota enforcement, emergency 30s unlock, daily reset |
| `app/src/main/java/com/focuslock/app/domain/model/AppCategory.kt` | Categorization model for installed apps (Social, Video, Gaming, Messaging, Productivity, Other). | `enum class AppCategory`, `inferCategory(packageName)` | None | App discovery, category filtering, default distraction heuristics |
| `app/src/main/java/com/focuslock/app/domain/model/ProtectionHealth.kt` | Models the operational health of FocusLock enforcement (missing permissions, battery restrictions). | `data class ProtectionHealth`, `enum class HealthStatus` | None | Dashboard health indicator, permission status |
| `app/src/main/java/com/focuslock/app/domain/usecase/EvaluateBlockStatusUseCase.kt` | Domain use case wrapping `QuotaEngine.evaluateQuota`. | `class EvaluateBlockStatusUseCase` | `QuotaEngine` | App blocking validation |
| `app/src/main/java/com/focuslock/app/domain/usecase/HardcorePolicyUseCase.kt` | Enforces cooldown delay policy for increasing quotas or unlocking in Hardcore Mode. | `class HardcorePolicyUseCase`, `canModifyQuota()`, `startCooldown()` | `UserSettingsDao` | Hardcore Mode anti-tampering |
| `app/src/main/java/com/focuslock/app/domain/usecase/ResetMidnightQuotaUseCase.kt` | Encapsulates daily midnight boundary transitions. | `class ResetMidnightQuotaUseCase` | `QuotaEngine` | Daily quota resets |
| `app/src/main/java/com/focuslock/app/domain/usecase/TrackUsageUseCase.kt` | Use case for recording incremental foreground durations. | `class TrackUsageUseCase` | `QuotaEngine` | Active session logging |

---

## 3. Data Layer (`data/`)

| Relative Path | Responsibility | Key Classes & Methods | Key Dependencies | Affected Features |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/java/com/focuslock/app/data/local/FocusLockDatabase.kt` | Room Database class (v4); configures entities, type converters, and migrations (`MIGRATION_2_3`, `MIGRATION_3_4`). | `abstract class FocusLockDatabase : RoomDatabase` | `ProtectedAppDao`, `UsageDao`, `FocusSessionDao`, `UserSettingsDao` | Persistence, SQLite schema, Room migrations |
| `app/src/main/java/com/focuslock/app/data/local/entity/DailyUsageEntity.kt` | Entity for daily aggregated app usage by date (`(packageName, localDate)` unique index). | `data class DailyUsageEntity` | Room `@Entity` | Screen time tracking, quota status, emergency state |
| `app/src/main/java/com/focuslock/app/data/local/entity/ProtectedAppEntity.kt` | Stores protected app metadata, daily limit in seconds, enabled toggle, and category. | `data class ProtectedAppEntity` | Room `@Entity` | App protection list, limit configuration |
| `app/src/main/java/com/focuslock/app/data/local/entity/FocusSessionEntity.kt` | Tracks active and historical Pomodoro focus sessions. | `data class FocusSessionEntity` | Room `@Entity` | Focus Mode sessions |
| `app/src/main/java/com/focuslock/app/data/local/entity/UserSettingsEntity.kt` | Persists user preferences (Hardcore Mode, PIN hash, theme mode). | `data class UserSettingsEntity` | Room `@Entity` | App settings, security |
| `app/src/main/java/com/focuslock/app/data/local/dao/UsageDao.kt` | Data Access Object for querying daily usage, session histories, and scroll counts. | `interface UsageDao` | Room `@Dao` | Dashboard stats, analytics, quota evaluation |
| `app/src/main/java/com/focuslock/app/data/local/dao/ProtectedAppDao.kt` | Data Access Object for managing configured apps and daily limits. | `interface ProtectedAppDao` | Room `@Dao` | App list, limit updates, distraction provisioning |
| `app/src/main/java/com/focuslock/app/data/repository/AppRepository.kt` | Repository combining PackageManager installed app discovery with Room protected apps. | `interface AppRepository`, `class AppRepositoryImpl` | `ProtectedAppDao`, `PackageManager` | App selection, limit editing |
| `app/src/main/java/com/focuslock/app/data/repository/UsageRepository.kt` | High-level repository for usage statistics, analytics aggregation, and event logging. | `interface UsageRepository`, `class UsageRepositoryImpl` | `UsageDao` | Dashboard analytics, charts |

---

## 4. Core Foundations (`core/`)

| Relative Path | Responsibility | Key Classes & Methods | Key Dependencies | Affected Features |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/java/com/focuslock/app/core/usage/RealUsageTracker.kt` | Ground-truth usage tracker querying Android `UsageStatsManager` / `UsageEvents`. | `class RealUsageTracker`, `syncPackageUsage()`, `extractSessionsFromEvents()` | `UsageStatsManager`, `UsageDao`, `TimeProvider` | Ground-truth screen time calculation |
| `app/src/main/java/com/focuslock/app/core/usage/InstagramPackageHelper.kt` | Identifies and validates installed Instagram packages (`com.instagram.android`, `.lite`, `.threadsapp`). | `object InstagramPackageHelper`, `isInstagramPackage()` | `PackageManager` | Instagram distraction handling, labels |
| `app/src/main/java/com/focuslock/app/core/overlay/FloatingTimerManager.kt` | Coordinates the draggable system-wide floating timer overlay window via `WindowManager`. | `object FloatingTimerManager`, `show()`, `hide()`, `updateUsageTime()`, `hasOverlayPermission()` | `WindowManager`, `FloatingTimerService` | Universal floating screen time pill |
| `app/src/main/java/com/focuslock/app/core/overlay/FloatingTimerService.kt` | Foreground service hosting the persistent floating timer pill. | `class FloatingTimerService : Service` | NotificationManager, `FloatingTimerManager` | Floating overlay persistence |
| `app/src/main/java/com/focuslock/app/core/security/PinSecurityManager.kt` | Manages salted PBKDF2 PIN hashing and verification. | `class PinSecurityManager`, `setPin()`, `verifyPin()`, `isPinSet()` | `EncryptedSharedPreferences`, `SecretKeyFactory` | App lock, anti-tampering |
| `app/src/main/java/com/focuslock/app/core/update/AppUpdateManager.kt` | Fetches release metadata from GitHub, downloads APK, verifies hash/signing, prompts install. | `class AppUpdateManager`, `checkForUpdates()`, `downloadAndVerifyUpdate()` | `HttpURLConnection`, `ApkSecurityValidator` | In-app updates |
| `app/src/main/java/com/focuslock/app/core/update/ApkSecurityValidator.kt` | Validates APK package name, version, SHA-256 hash, and signing certificate before install. | `object ApkSecurityValidator`, `validateApk()` | `PackageManager`, `MessageDigest` | Safe update installation |
| `app/src/main/java/com/focuslock/app/core/util/BreakReminderHelper.kt` | Monitors continuous screen-on duration; triggers notifications and fullscreen alerts at 30 minutes. | `object BreakReminderHelper`, `onScreenOn()`, `onScreenOff()`, `checkAndTriggerReminder()` | `NotificationManager`, `BreakAlertActivity` | 30-minute break alert |
| `app/src/main/java/com/focuslock/app/core/util/TimeUtils.kt` | System and relative time calculations, midnight countdown formatters, duration string formatters. | `object TimeUtils`, `interface TimeProvider`, `class SystemTimeProvider` | `java.time.*` | Midnight countdown, date formatting |
| `app/src/main/java/com/focuslock/app/core/designsystem/Theme.kt` | Jetpack Compose theme definition with 3D AMOLED palette, dynamic colors, typography. | `@Composable fun FocusLockTheme` | Compose Material 3 | Visual styling across all screens |
| `app/src/main/java/com/focuslock/app/core/designsystem/Components.kt` | Reusable design system components (buttons, cards, progress rings, top bars). | `FocusCard`, `UsageProgressRing`, `AnimatedPrimaryButton` | Compose Foundation / Material 3 | UI layout consistency |

---

## 5. Presentation Layer (`presentation/`)

| Relative Path | Responsibility | Key Classes & Methods | Key Dependencies | Affected Features |
| :--- | :--- | :--- | :--- | :--- |
| `app/src/main/java/com/focuslock/app/presentation/MainActivity.kt` | Single-activity host for Compose navigation; hosts update dialog overlay. | `class MainActivity : ComponentActivity` | `FocusLockNavGraph`, `AppUpdateManager` | App launch, main navigation |
| `app/src/main/java/com/focuslock/app/presentation/BlockingActivity.kt` | Fullscreen lockscreen displayed when an exhausted app is foregrounded. | `class BlockingActivity : ComponentActivity`, `BlockingScreen()` | `QuotaEngine`, `FocusLockAccessibilityService` | Daily limit enforcement screen, emergency bypass |
| `app/src/main/java/com/focuslock/app/presentation/breakalert/BreakAlertActivity.kt` | Fullscreen take-a-break prompt displayed after 30 minutes of continuous screen usage. | `class BreakAlertActivity : ComponentActivity` | `BreakReminderHelper` | 30-minute screen break alert |
| `app/src/main/java/com/focuslock/app/presentation/dashboard/DashboardScreen.kt` | Primary overview displaying controlled apps, daily usage progress rings, and quick actions. | `@Composable fun DashboardScreen`, `DashboardViewModel` | `UsageRepository`, `AppRepository`, `QuotaEngine` | Dashboard home screen |
| `app/src/main/java/com/focuslock/app/presentation/apps/AppSelectionScreen.kt` | Lists all installed launchable applications with search, category filtering, and protection toggles. | `@Composable fun AppSelectionScreen`, `AppSelectionViewModel` | `AppRepository` | Protected apps management |
| `app/src/main/java/com/focuslock/app/presentation/apps/AppDetailsScreen.kt` | Displays in-depth usage history for a single app, scroll count estimation, and daily limit preset buttons. | `@Composable fun AppDetailsScreen`, `AppDetailsViewModel` | `AppRepository`, `UsageRepository`, `FocusLockAccessibilityService` | Custom daily limits, app analytics |
| `app/src/main/java/com/focuslock/app/presentation/analytics/AnalyticsScreen.kt` | Visualizes weekly/daily screen time trends, most-used apps, and session breakdowns. | `@Composable fun AnalyticsScreen`, `AnalyticsViewModel` | `UsageRepository` | Screen time analytics |
| `app/src/main/java/com/focuslock/app/presentation/focus/FocusModeScreen.kt` | Pomodoro focus timer (25m / 50m / custom) that restricts all distractions during work sessions. | `@Composable fun FocusModeScreen`, `FocusModeViewModel` | `FocusLockDatabase` | Focus Mode sessions |
| `app/src/main/java/com/focuslock/app/presentation/settings/SettingsScreen.kt` | Configures Hardcore Mode, PIN lock, floating timer toggle, dark/light theme, and about section. | `@Composable fun SettingsScreen`, `SettingsViewModel` | `PinSecurityManager`, `FloatingTimerManager` | User settings, security configuration |
| `app/src/main/java/com/focuslock/app/presentation/onboarding/OnboardingScreen.kt` | First-launch onboarding walkthrough guiding users through granting Accessibility and Usage Access. | `@Composable fun OnboardingScreen` | `AndroidPermissionChecker` | Initial setup & permission onboarding |
| `app/src/main/java/com/focuslock/app/presentation/navigation/NavGraph.kt` | Defines Compose Navigation routes and transitions across screens. | `@Composable fun FocusLockNavGraph`, `sealed class Screen` | Navigation Compose | Inter-screen routing |
