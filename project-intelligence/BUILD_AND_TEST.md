# Build & Test Engineering Guide — FocusLock

This document details verified build commands, test suites, environment requirements, and artifact locations for FocusLock.

---

## 1. Build Environment Requirements

| Requirement | Supported Version / Location |
| :--- | :--- |
| **Operating System** | Windows 10/11 (PowerShell environment) |
| **JDK (Java Home)** | Java SE 17 (`C:\Program Files\Android\Android Studio\jbr`) |
| **Android SDK** | `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35` |
| **Gradle Wrapper** | Gradle `8.9+` (invoked via `.\gradlew.bat`) |

> **Environment Setup Note:**
> Before running Gradle commands in a fresh PowerShell terminal, ensure `JAVA_HOME` points to the JDK 17 runtime:
> ```powershell
> $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
> ```

---

## 2. Verified Gradle Commands

### 2.1 Run All Unit Tests
Executes all local JVM test suites across Domain, Core, Service, and Presentation:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest
```
*Average execution time: ~45–60 seconds.*

### 2.2 Run a Specific Test Suite
Target individual test classes to speed up verification cycles:
```powershell
# External app blocking and 1-minute Instagram limit lifecycle
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --tests "com.focuslock.app.service.ExternalAppBlockingTest"

# QuotaEngine authorative state and reset tests
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --tests "com.focuslock.app.domain.engine.QuotaEngineTest"

# In-app update integrity tests
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest --tests "com.focuslock.app.core.update.AppUpdateManagerTest"
```

### 2.3 Build Debug APK
Compiles the application with debug logging enabled and signs using the bundled keystore:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
```
**Output location:**
`app/build/outputs/apk/debug/app-debug.apk`

### 2.4 Build Release APK
Compiles and signs the release binary:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleRelease
```
**Output location:**
`app/build/outputs/apk/release/app-release.apk` (or copied to `release/FocusLock-v1.2.0.apk`)

---

## 3. Automated Test Suites Catalog

| Test Suite File | Tested Components | Key Test Scenarios |
| :--- | :--- | :--- |
| `ExternalAppBlockingTest.kt` | `FocusLockAccessibilityService`, `QuotaEngine` | 1-minute Instagram limit, 30-minute default limits, repeated app opening, emergency 30s bypass, service restart persistence, midnight reset unblocking. |
| `QuotaEngineTest.kt` | `QuotaEngine`, Room DAOs, `TimeProvider` | Threshold evaluations (ALLOWED, WARNING, EXHAUSTED, BLOCKED_UNTIL_RESET), emergency timer countdown, midnight quota rollover, hardcore mode cooldowns. |
| `FloatingTimerOverlayTest.kt` | `FloatingTimerManager`, `FloatingTimerService` | Pill visibility, dragging coordinates, permission checks, timer formatting. |
| `AppUpdateManagerTest.kt` | `AppUpdateManager`, `ApkSecurityValidator` | GitHub release JSON parsing, version comparisons, hash verification, dismiss session state. |
| `UpdaterPipelineTest.kt` | `ApkSecurityValidator` | Package name tampering detection, signature verification, corrupt byte detection. |
| `InstagramAndSessionTrackingTest.kt` | `InstagramPackageHelper`, `RealUsageTracker` | Recognition of `com.instagram.android`, `.lite`, `.threadsapp`, session grouping from usage events. |
| `BreakReminderTest.kt` | `BreakReminderHelper` | 30-minute continuous screen detection, snooze action, dismiss action, screen off reset. |
| `TimeUtilsTest.kt` | `TimeUtils`, `SystemTimeProvider` | Midnight countdown calculations, date string format parsing, duration formatters. |
| `AnalyticsCalculationTest.kt` | `AnalyticsViewModel`, `UsageRepository` | Weekly total calculations, daily average screen time, top distracted apps ranking. |

---

## 4. Signing Configuration

Configured in `app/build.gradle.kts`:
- **Keystore File:** `app/focuslock_keystore.jks`
- **Key Alias:** `focuslock`
- **Signing Scheme:** V1 (Jar), V2 (Full APK), V3 (Key Rotation) enabled.
