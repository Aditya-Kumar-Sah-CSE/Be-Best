# Security & Android Permissions Architecture — FocusLock

## 1. Android Manifest Permissions

FocusLock requires specific privileged Android permissions to enforce app limits, monitor screen time, and maintain reliable operation without being terminated by OS battery optimizations:

| Permission Name | Protection Level | Purpose in FocusLock | Fallback / Graceful Handling |
| :--- | :--- | :--- | :--- |
| `android.permission.PACKAGE_USAGE_STATS` | Special App Access (`android.permission-group.COSTS`) | Allows `RealUsageTracker` to read `UsageStatsManager` events and compute exact foreground session times. | Prompted during onboarding; features operate in degraded mode if missing. |
| `android.permission.SYSTEM_ALERT_WINDOW` | Special App Access (`ACTION_MANAGE_OVERLAY_PERMISSION`) | Required for `FloatingTimerService` to draw the floating screen timer over other apps. | Floating timer toggle disabled if permission is ungranted. |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Signature / System | Binds `FocusLockAccessibilityService` to listen to window events and intercept app launches instantaneously. | Core blocking relies on this; user is guided to Accessibility Settings. |
| `android.permission.FOREGROUND_SERVICE` | Normal | Allows running background services for persistent monitoring and timer overlays. | Always declared in manifest. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Normal (Android 14+) | Declares specialized use-case for the floating screen time timer pill. | Meets Google Play Android 14 policy standards. |
| `android.permission.POST_NOTIFICATIONS` | Runtime (Android 13+) | Required to post break reminders, quota warning alerts, and foreground service status. | Requested via runtime permission dialog on Android 13+. |
| `android.permission.VIBRATE` | Normal | Allows gentle rhythmic vibration when the 30-minute continuous wellness reminder fires. | Automatically granted by Android. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Normal | Triggers `BootRecoveryReceiver` on device reboot to re-register monitors and reset daily quotas. | Automatic on reboot. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special App Access | Prompts user to whitelist FocusLock from aggressive OEM task-killers (Xiaomi, Samsung, Huawei). | Optional setting with guide dialog. |
| `android.permission.QUERY_ALL_PACKAGES` | Broad App Access | Allows `AppRepositoryImpl` to enumerate installed user apps for the selection screen. | Filtered to non-system launcher apps. |
| `android.permission.INTERNET` | Normal | Required exclusively for checking GitHub releases (`AppUpdateManager`) and downloading APK updates. | Handled gracefully with offline state when disconnected. |
| `android.permission.REQUEST_INSTALL_PACKAGES` | Special App Access | Triggers the system package installer when a validated APK update is ready to install. | Directs user to grant unknown apps installation permission. |

---

## 2. Cryptographic Security & Hardcore Mode

### PBKDF2WithHmacSHA256 Implementation
- **Class**: `com.focuslock.app.core.crypto.PinSecurityManager`
- **Algorithm**: `PBKDF2WithHmacSHA256`
- **Iterations**: 10,000 rounds
- **Salt Generation**: 16-byte cryptographically secure random salt (`SecureRandom`) per PIN.
- **Key Storage**: Salts and hashed digests are stored locally in `EncryptedSharedPreferences` backed by the Android Keystore (`MasterKey`).

### Tamper-Resistance Policy
- When **Hardcore Mode** is active:
  - Daily quota limits cannot be increased without authenticating with the PIN.
  - Emergency bypass durations are strictly limited to 30 seconds once per day.
  - A mandatory 60-second cooldown countdown must elapse before sensitive setting changes can be saved.
  - Disabling FocusLock requires entering the PIN.
