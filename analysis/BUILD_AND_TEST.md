# Build & Test Engineering Reference — FocusLock

## 1. Environment Requirements & Prerequisites

- **Host OS**: Windows 10/11 (PowerShell environment).
- **JDK Installation**: Java SE 17 is located at:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
  ```
- **Android SDK Path**: `C:\Users\adity\AppData\Local\Android\Sdk` (configured in `local.properties`).
- **Android Gradle Plugin (AGP)**: `8.7.1`.
- **Gradle Version**: `8.9` (via `gradlew.bat`).

---

## 2. Standard Build & Verification Commands

### 1. Run Complete JVM Unit Test Suite
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest
```
Runs all 25+ unit tests covering QuotaEngine, Blocking logic, Overlay, In-App Updater, Analytics, and Break Reminders.

### 2. Run Targeted Unit Test Class
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --tests "com.focuslock.app.service.ExternalAppBlockingTest"
```

### 3. Compile Debug APK
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
```
Output artifact generated at: `app/build/outputs/apk/debug/app-debug.apk`.

### 4. Run Linter Verification
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat lintDebug
```

### 5. Check Documentation Staleness
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-doc-staleness.ps1
```
Scans git diff against intelligence files and flags documentation that may need updates.

---

## 3. Test Suite Inventory

| Test Class | Scope | What It Verifies |
| :--- | :--- | :--- |
| `ExternalAppBlockingTest.kt` | Service & Quota | 1-minute limits, immediate blocking, 30s emergency bypass, midnight resets. |
| `QuotaEngineTest.kt` | Domain | Focus mode overrides, quota calculations, hardcore mode rules. |
| `InstagramAndSessionTrackingTest.kt` | Usage Tracking | Package name parsing, session duration matching, duplicate prevention. |
| `AnalyticsCalculationTest.kt` | Analytics | Longest session duration, session counts, scroll estimates. |
| `FloatingTimerOverlayTest.kt` | Overlay | WindowManager parameter creation, touch drag event handlers. |
| `BreakReminderTest.kt` | Core Util | 30-minute threshold triggers, 5-minute screen off reset policy. |
| `AppUpdateManagerTest.kt` | Updater | Version string parsing, update availability detection, error states. |
| `UpdaterPipelineTest.kt` | Security | SHA-256 APK checksum verification, signature matching. |
