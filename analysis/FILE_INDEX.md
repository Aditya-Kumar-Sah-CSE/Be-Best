# File Index & Class Directory — FocusLock

This directory indexes all key source files in `app/src/main/java/com/focuslock/app/`, mapping each class to its architectural role and responsibility.

---

## 1. Application Root & Dependency Injection

| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `FocusLockApp.kt` | `class FocusLockApp : Application` | Singleton dependency injection container, database initialisation, notification channels, default app provisioning. |

---

## 2. Core Foundations (`core/`)

### Crypto & Security (`core/crypto/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `PinSecurityManager.kt` | `class PinSecurityManager` | PBKDF2WithHmacSHA256 salted PIN hashing, verification, secure storage via EncryptedSharedPreferences. |

### Design System (`core/design/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `Color.kt` | Palette constants | AMOLED black theme, neon accents, Material 3 color definitions. |
| `Theme.kt` | `FocusLockTheme` | Composable Material 3 theme wrapper supporting dark mode. |
| `Type.kt` | Typography tokens | Material 3 typography definitions. |

### Overlay System (`core/overlay/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `FloatingTimerManager.kt` | `class FloatingTimerManager` | Manages WindowManager layout params and draggable floating timer view. |
| `FloatingTimerService.kt` | `class FloatingTimerService : Service` | Foreground service running the persistent floating timer pill. |

### In-App Update Engine (`core/update/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `AppUpdateManager.kt` | `class AppUpdateManager` | GitHub releases API checker, download manager, and update trigger. |
| `ApkSecurityValidator.kt` | `class ApkSecurityValidator` | Validates APK SHA-256 hash and package signature against running app before install. |
| `UpdateState.kt` | `sealed interface UpdateState` | Sealed states: `Idle`, `Checking`, `Available`, `Downloading`, `ReadyToInstall`, `Error`. |

### Usage Tracking (`core/usage/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `RealUsageTracker.kt` | `class RealUsageTracker` | Queries `UsageStatsManager.queryEvents()` to measure exact foreground session duration. |

### Wellness & Audio Engine (`core/wellness/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `WellnessPreferences.kt` | `class WellnessPreferences` | Preferences for alert audio/video URIs, volume, and vibration toggles. |
| `WellnessSoundManager.kt` | `class WellnessSoundManager` | Plays pleasant synthesized ambient chimes, custom songs, and TTS voice prompts. |

### Core Utilities (`core/util/`)
| File Path | Primary Class / Type | Responsibility |
| :--- | :--- | :--- |
| `InstagramPackageHelper.kt` | `object InstagramPackageHelper` | Identifies and matches Instagram package variations (`com.instagram.android`, `lite`). |
| `TimeUtils.kt` | `object TimeUtils` | Date formatting, midnight epoch calculations, seconds-to-string formatters. |
| `BreakReminderHelper.kt` | `class BreakReminderHelper` | 30-minute monotonic continuous screen timer, 5-min break tracker, and alarm dispatcher. |
| `BreakReminderActionReceiver.kt` | `class BreakReminderActionReceiver` | Handles notification actions (Take Break, Snooze 5m, Dismiss). |

---

## 3. Data Layer (`data/`)

### Entities (`data/local/entity/`)
| File Path | Room Entity | Table Name | Purpose |
| :--- | :--- | :--- | :--- |
| `ProtectedAppEntity.kt` | `ProtectedAppEntity` | `protected_apps` | Tracks apps under quota, daily limits, category, and active status. |
| `DailyUsageEntity.kt` | `DailyUsageEntity` | `daily_usage` | Daily usage records per app (used seconds, session count, longest session). |
| `UsageSessionEntity.kt` | `UsageSessionEntity` | `usage_sessions` | Discrete foreground sessions with start and end timestamps. |
| `FocusSessionEntity.kt` | `FocusSessionEntity` | `focus_sessions` | Pomodoro focus blocks with target duration and completion state. |
| `UserSettingsEntity.kt` | `UserSettingsEntity` | `user_settings` | Hardcore mode, PIN hash/salt, cooldown configs, floating timer toggle. |

### DAOs (`data/local/dao/`)
| File Path | Interface | Purpose |
| :--- | :--- | :--- |
| `ProtectedAppDao.kt` | `interface ProtectedAppDao` | CRUD queries for protected apps; reactive `Flow<List<ProtectedAppEntity>>`. |
| `DailyUsageDao.kt` | `interface DailyUsageDao` | Queries and updates today's usage records and exhausted flags. |
| `UsageSessionDao.kt` | `interface UsageSessionDao` | Inserts and queries discrete usage sessions for analytics. |
| `FocusSessionDao.kt` | `interface FocusSessionDao` | Manages active and historical Pomodoro sessions. |
| `UserSettingsDao.kt` | `interface UserSettingsDao` | Manages global user configuration. |

### Database & Repositories (`data/local/`, `data/repository/`)
| File Path | Class / Interface | Purpose |
| :--- | :--- | :--- |
| `FocusLockDatabase.kt` | `abstract class FocusLockDatabase : RoomDatabase` | Main Room DB instance with migrations 1 ➔ 2 ➔ 3 ➔ 4. |
| `AppRepository.kt` | `interface AppRepository` | Interface for protected apps & system package enumeration. |
| `AppRepositoryImpl.kt` | `class AppRepositoryImpl` | Implementation caching apps and synchronizing with PackageManager. |
| `UsageRepository.kt` | `interface UsageRepository` | Interface for daily usage metrics and session histories. |
| `UsageRepositoryImpl.kt` | `class UsageRepositoryImpl` | Implementation updating and aggregating usage metrics. |

---

## 4. Domain Layer (`domain/`)

| File Path | Class / Type | Responsibility |
| :--- | :--- | :--- |
| `QuotaEngine.kt` | `class QuotaEngine` | Pure business logic: evaluates whether an app should be blocked based on quota, focus mode, and emergency bypass. |
| `EvaluateQuotaUseCase.kt` | `class EvaluateQuotaUseCase` | Executes quota evaluation and updates DB exhausted state. |
| `HardcorePolicyUseCase.kt` | `class HardcorePolicyUseCase` | Enforces PIN verification and 60-second cooldown timers before allowing limit increases. |

---

## 5. Presentation Layer (`presentation/`)

| File Path | Type | Responsibility |
| :--- | :--- | :--- |
| `MainActivity.kt` | `class MainActivity : ComponentActivity` | Hosts Compose root, navigation graph, and update checks. |
| `BlockingActivity.kt` | `class BlockingActivity : ComponentActivity` | Fullscreen lockscreen overlay displayed when limit is reached. |
| `BreakAlertActivity.kt` | `class BreakAlertActivity : ComponentActivity` | 30-minute wellness break alert activity with user actions. |
| `WellnessAnimations.kt` | `@Composable WalkingPerson3DAnimation, DrinkingWater3DAnimation` | 3D-styled animated walking character and drinking water glass canvas components. |
| `navigation/NavGraph.kt` | `@Composable FocusLockNavGraph` | Compose navigation controller defining routes for all screens. |
| `dashboard/DashboardScreen.kt` | `@Composable DashboardScreen` | Main dashboard displaying screen time, top apps, quick focus, and status. |
| `apps/AppSelectionScreen.kt` | `@Composable AppSelectionScreen` | Searchable list of installed apps to enable/disable protection. |
| `apps/AppDetailsScreen.kt` | `@Composable AppDetailsScreen` | Configure daily limits (including 1-min test preset) and view stats. |
| `analytics/AnalyticsScreen.kt` | `@Composable AnalyticsScreen` | Usage trends, session counts, and longest session metrics. |
| `focus/FocusModeScreen.kt` | `@Composable FocusModeScreen` | Pomodoro focus timer with blocking overrides. |
| `settings/SettingsScreen.kt` | `@Composable SettingsScreen` | Hardcore mode, PIN management, floating timer toggle, update checker. |

---

## 6. Background Services & Receivers (`service/`)

| File Path | Component | Purpose |
| :--- | :--- | :--- |
| `FocusLockAccessibilityService.kt` | `class FocusLockAccessibilityService : AccessibilityService` | System window monitor intercepting app opens and triggering `BlockingActivity`. |
| `TimeChangeReceiver.kt` | `class TimeChangeReceiver : BroadcastReceiver` | Handles midnight and time zone transitions, resetting daily quotas. |
| `BootRecoveryReceiver.kt` | `class BootRecoveryReceiver : BroadcastReceiver` | Re-initializes background alarms, monitors, and services on device boot. |
| `BreakReminderActionReceiver.kt` | `class BreakReminderActionReceiver : BroadcastReceiver` | Handles user actions ("Take Break", "Snooze") from break alert notifications. |
