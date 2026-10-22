# Feature Map & Implementation Catalog — FocusLock

This document details the major features implemented in FocusLock, their entry points, data flows, permissions, automated tests, and known boundaries.

---

## 1. Daily App Quotas & Instant Accessibility-Based Blocking

| Attribute | Specification |
| :--- | :--- |
| **Description** | Intercepts launch of distraction apps (Instagram, YouTube, etc.) when daily quota is exhausted; displays fullscreen lockscreen. |
| **User Entry Point** | App Selection Screen (`AppSelectionScreen.kt`) & App Details Screen (`AppDetailsScreen.kt`). |
| **Runtime Entry Point** | `FocusLockAccessibilityService.onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)`. |
| **Key Source Files** | `FocusLockAccessibilityService.kt`, `QuotaEngine.kt`, `BlockingActivity.kt`, `ProtectedAppDao.kt`, `UsageDao.kt`. |
| **Data Dependencies** | `protected_apps` (daily limits), `daily_usage` (used seconds and exhausted state). |
| **Related Permissions** | `BIND_ACCESSIBILITY_SERVICE`, `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`. |
| **Automated Tests** | `ExternalAppBlockingTest.kt`, `QuotaEngineTest.kt`. |
| **Known Limitations** | Android third-party apps cannot hook the launcher icon click *before* the OS creates the intent; blocking redirects as soon as the package is foregrounded. |

---

## 2. Ground-Truth Screen Time & Session Analytics

| Attribute | Specification |
| :--- | :--- |
| **Description** | Accurately calculates real daily usage, discrete sessions, longest session, and scroll interactions using Android system events. |
| **User Entry Point** | Dashboard Screen (`DashboardScreen.kt`) & Analytics Screen (`AnalyticsScreen.kt`). |
| **Runtime Entry Point** | `RealUsageTracker.syncPackageUsage(packageName)`. |
| **Key Source Files** | `RealUsageTracker.kt`, `UsageRepository.kt`, `UsageDao.kt`, `InstagramPackageHelper.kt`. |
| **Data Dependencies** | `usage_sessions`, `daily_usage`. |
| **Related Permissions** | `PACKAGE_USAGE_STATS`. |
| **Automated Tests** | `InstagramAndSessionTrackingTest.kt`, `AnalyticsCalculationTest.kt`. |
| **Known Limitations** | `UsageEvents` queries require 200ms–1000ms depending on device event log size; backgrounded so UI remains responsive. |

---

## 3. Universal System-Wide Floating Screen-Time Timer Overlay

| Attribute | Specification |
| :--- | :--- |
| **Description** | Draggable, floating AMOLED pill overlaying all apps to provide continuous visual mindfulness of current screen time. |
| **User Entry Point** | Settings Screen (`SettingsScreen.kt`) toggle. |
| **Runtime Entry Point** | `FloatingTimerService.onStartCommand()`, `FloatingTimerManager.show()`. |
| **Key Source Files** | `FloatingTimerManager.kt`, `FloatingTimerService.kt`. |
| **Data Dependencies** | In-memory timer state; `UserSettingsEntity.floatingTimerEnabled`. |
| **Related Permissions** | `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`. |
| **Automated Tests** | `FloatingTimerOverlayTest.kt`. |
| **Known Limitations** | Requires user to manually grant "Display over other apps" special permission. |

---

## 4. 30-Minute Continuous Screen Break Reminder

| Attribute | Specification |
| :--- | :--- |
| **Description** | Monitors continuous screen-on time without a break; alerts user at 30 minutes with notification and optional fullscreen prompt. |
| **User Entry Point** | Settings Screen & Notification Action Receiver (`BreakReminderActionReceiver.kt`). |
| **Runtime Entry Point** | `FocusLockAccessibilityService.startContinuousBreakMonitor()`, `BreakReminderHelper`. |
| **Key Source Files** | `BreakReminderHelper.kt`, `BreakAlertActivity.kt`, `BreakReminderActionReceiver.kt`. |
| **Data Dependencies** | In-memory continuous screen-on timestamps (`continuousScreenStartElapsed`). |
| **Related Permissions** | `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. |
| **Automated Tests** | `BreakReminderTest.kt`. |
| **Known Limitations** | Turning the screen off for >= 5 minutes resets the continuous timer. |

---

## 5. Pomodoro Focus Mode Sessions

| Attribute | Specification |
| :--- | :--- |
| **Description** | Timed focus blocks (e.g. 25m, 50m) that temporarily block all protected apps regardless of daily quota remaining. |
| **User Entry Point** | Focus Mode Screen (`FocusModeScreen.kt`). |
| **Runtime Entry Point** | `FocusSessionDao.getActiveSession()`, checked within `QuotaEngine.evaluateQuota()`. |
| **Key Source Files** | `FocusModeScreen.kt`, `FocusModeViewModel.kt`, `FocusSessionDao.kt`, `QuotaEngine.kt`. |
| **Data Dependencies** | `focus_sessions` table. |
| **Related Permissions** | None additional. |
| **Automated Tests** | `QuotaEngineTest.kt`. |
| **Known Limitations** | Focus session override pauses when time reaches `plannedEndAt`. |

---

## 6. Hardcore Mode & Cryptographic PIN Security

| Attribute | Specification |
| :--- | :--- |
| **Description** | Prevents impulsive unblocking or limit increases via salted PBKDF2 PIN verification and a mandatory 60-second cooldown timer. |
| **User Entry Point** | Settings Screen (`SettingsScreen.kt`). |
| **Runtime Entry Point** | `PinSecurityManager.verifyPin()`, `HardcorePolicyUseCase.canModifyQuota()`. |
| **Key Source Files** | `PinSecurityManager.kt`, `HardcorePolicyUseCase.kt`, `UserSettingsDao.kt`. |
| **Data Dependencies** | `user_settings` table (`pinHash`, `pinSalt`, `hardcoreModeEnabled`). |
| **Related Permissions** | None (uses Android Keystore / `EncryptedSharedPreferences`). |
| **Automated Tests** | `QuotaEngineTest.kt`. |
| **Known Limitations** | User must remember their PIN; no cloud reset option exists due to 100% local privacy design. |

---

## 7. In-App Direct APK Updater (GitHub Releases)

| Attribute | Specification |
| :--- | :--- |
| **Description** | Background or on-demand check for new GitHub releases, downloads APK, verifies SHA-256 and signing key, prompts installation. |
| **User Entry Point** | MainActivity startup (silent check) or Settings "Check for Updates" button. |
| **Runtime Entry Point** | `AppUpdateManager.checkForUpdates()`. |
| **Key Source Files** | `AppUpdateManager.kt`, `ApkSecurityValidator.kt`, `UpdateDialog.kt`. |
| **Data Dependencies** | Remote `release-metadata.json` on GitHub. |
| **Related Permissions** | `INTERNET`, `REQUEST_INSTALL_PACKAGES`. |
| **Automated Tests** | `AppUpdateManagerTest.kt`, `UpdaterPipelineTest.kt`. |
| **Known Limitations** | Installation requires user confirmation via Android Package Installer dialog. |
