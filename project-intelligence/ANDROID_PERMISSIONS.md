# Android Permissions & Security Specifications — FocusLock

This document details every Android permission declared, requested, and utilized by FocusLock, distinguishing compile-time manifest declarations from runtime/special-access user approvals.

---

## 1. Master Permissions Matrix

| Permission Name | Type / Protection Level | Granted Via | Code Caller / Path | Required For |
| :--- | :--- | :--- | :--- | :--- |
| `android.permission.PACKAGE_USAGE_STATS` | Protected / Special App Access | User toggles in system "Usage Access" settings (`Settings.ACTION_USAGE_ACCESS_SETTINGS`) | `RealUsageTracker.syncPackageUsage()`, `UsageStatsManager` | Reconciling ground-truth foreground screen time and session events |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | System Signature / Service Bind | User enables in system "Accessibility" settings (`Settings.ACTION_ACCESSIBILITY_SETTINGS`) | `FocusLockAccessibilityService` | Intercepting window transitions (`TYPE_WINDOW_STATE_CHANGED`) to trigger instant app blocking |
| `android.permission.SYSTEM_ALERT_WINDOW` | Special App Access | User grants in system "Display over other apps" settings (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`) | `FloatingTimerManager`, `FloatingTimerService`, `WindowManager` | Displaying the system-wide floating screen-time pill overlay; starting activities from background |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal (Auto-granted at install) | Manifest declaration | `BootRecoveryReceiver` | Validating quotas and re-initializing background monitors after device restart |
| `android.permission.POST_NOTIFICATIONS` | Runtime Permission (Android 13+ / API 33+) | Dynamic runtime request via `ActivityResultLauncher` | `NotificationManager`, `FocusLockApp.createNotificationChannels()` | Showing quota warnings, 30-min break alerts, and in-app update notifications |
| `android.permission.FOREGROUND_SERVICE` | Normal | Manifest declaration | `FloatingTimerService` | Keeping persistent floating timer alive in foreground |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Normal (Android 14+ / API 34+) | Manifest declaration with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` | `FloatingTimerService` | Compliant foreground service declaration on Android 14+ |
| `android.permission.REQUEST_INSTALL_PACKAGES` | Special App Access (Android 8.0+) | User prompts via system package installer intent | `AppUpdateManager.promptInstall()` | Installing verified in-app APK updates downloaded from GitHub releases |
| `android.permission.INTERNET` | Normal | Manifest declaration | `AppUpdateManager.checkForUpdates()`, `downloadApk()` | Strictly used for checking release metadata and downloading genuine APK updates |

---

## 2. AccessibilityService Configuration (`accessibility_service_config.xml`)

Located in [`app/src/main/res/xml/accessibility_service_config.xml`](file:///d:/Be%20Best/app/src/main/res/xml/accessibility_service_config.xml):

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagRetrieveInteractiveWindows|flagIncludeNotImportantViews"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="50" />
```

### Privacy & Scope Guarantees
- FocusLock does **not** capture keystrokes, passwords, or text contents.
- `TYPE_VIEW_SCROLLED` is strictly used to count vertical scroll events in distraction apps (e.g. reels/shorts) to calculate approximate scroll velocity without inspecting any view text or image data.
- System packages (`com.android.systemui`, `com.android.settings`, launchers, permission controllers) are strictly excluded from interception to prevent redirect loops.

---

## 3. Background Activity Launch (BAL) Restrictions & OEM Caveats

Starting with Android 10 (API 29) and tightened in Android 14/15, background services cannot arbitrarily launch activities (`startActivity`) unless specific exemptions apply:

1. **`SYSTEM_ALERT_WINDOW` Exemption**:
   Apps that have been granted the `SYSTEM_ALERT_WINDOW` ("Display over other apps") permission are officially permitted by Android framework to launch activities from background services.
2. **AccessibilityService Window Binding Exemption**:
   An active AccessibilityService processing an `AccessibilityEvent` possesses temporary window focus and user-interaction context.
3. **OEM Aggressive Killing (Xiaomi / ColorOS / Realme / Samsung)**:
   - Some vendors restrict background activity starts even for accessibility services unless "Display pop-up windows while running in the background" is toggled in OEM settings.
   - **FocusLock Fallback:** In [`FocusLockAccessibilityService.launchBlockScreen`](file:///d:/Be%20Best/app/src/main/java/com/focuslock/app/service/FocusLockAccessibilityService.kt), if `startActivity()` fails or is blocked, the service executes `performGlobalAction(GLOBAL_ACTION_HOME)` to immediately move the restricted app out of the foreground to the home screen, and posts a high-priority notification with a full-screen intent (`setFullScreenIntent(pendingIntent, true)`).
