# FocusLock Project Analysis & Intelligence Index

Welcome to the **FocusLock Persistent Project Analysis System**. This directory acts as the central technical reference, architecture guide, and memory bank for all AI assistants and developers working on the FocusLock codebase.

---

## 1. Directory Structure & Document Navigation

| File | Primary Focus | When to Consult |
| :--- | :--- | :--- |
| **[`PROJECT_OVERVIEW.md`](file:///d:/Be%20Best/analysis/PROJECT_OVERVIEW.md)** | Executive summary, tech stack, app lifecycle, key capabilities. | Initial onboarding, stack verification, build SDK questions. |
| **[`ARCHITECTURE.md`](file:///d:/Be%20Best/analysis/ARCHITECTURE.md)** | Clean Architecture layers, MVI/MVVM, data flow, reactive streams. | Adding screens, refactoring services, modifying ViewModels. |
| **[`FILE_INDEX.md`](file:///d:/Be%20Best/analysis/FILE_INDEX.md)** | Exhaustive index of every source file with primary purpose. | Locating target classes without running expensive global scans. |
| **[`DEPENDENCIES.md`](file:///d:/Be%20Best/analysis/DEPENDENCIES.md)** | Internal module graph, external libraries, version catalog. | Upgrading libraries, analyzing class coupling, dependency injection. |
| **[`DATABASE_SCHEMA.md`](file:///d:/Be%20Best/analysis/DATABASE_SCHEMA.md)** | Room entities, schemas, relations, migrations (1 ➔ 4). | Modifying local tables, writing DAOs, planning migrations. |
| **[`SECURITY_AND_PERMISSIONS.md`](file:///d:/Be%20Best/analysis/SECURITY_AND_PERMISSIONS.md)** | Manifest permissions, runtime security, PBKDF2 PIN encryption. | Handling permission requests, Hardcore mode, crypto logic. |
| **[`FEATURE_STATUS.md`](file:///d:/Be%20Best/analysis/FEATURE_STATUS.md)** | Feature catalogue, implementation status, automated tests. | Checking existing features, testing edge cases, quota engines. |
| **[`BUILD_AND_TEST.md`](file:///d:/Be%20Best/analysis/BUILD_AND_TEST.md)** | Gradle commands, JDK configuration, test suites, CI checks. | Running unit tests, debugging build errors, creating APKs. |
| **[`KNOWN_ISSUES.md`](file:///d:/Be%20Best/analysis/KNOWN_ISSUES.md)** | Known OS edge cases, OEM battery killers, active technical debt. | Diagnosing mysterious background deaths or UI quirks. |
| **[`TASK_LOG.md`](file:///d:/Be%20Best/analysis/TASK_LOG.md)** | Chronological log of recent changes, verification results, and diffs. | Reviewing what was recently changed and verified. |

---

## 2. Mandatory AI Assistant Workflow

Whenever an agent is tasked with modifying, refactoring, or extending FocusLock:

1. **Step 1 — Read Analysis First**:
   - Consult [`PROJECT_OVERVIEW.md`](file:///d:/Be%20Best/analysis/PROJECT_OVERVIEW.md) and [`FEATURE_STATUS.md`](file:///d:/Be%20Best/analysis/FEATURE_STATUS.md) to understand current capabilities.
   - Use [`FILE_INDEX.md`](file:///d:/Be%20Best/analysis/FILE_INDEX.md) and [`DEPENDENCIES.md`](file:///d:/Be%20Best/analysis/DEPENDENCIES.md) to isolate target files.
2. **Step 2 — Code is Authoritative**:
   - Analysis documentation provides fast routing, but **the actual source code is the ultimate source of truth**.
   - If documentation ever diverges from Kotlin source code, verify against code and update the analysis.
3. **Step 3 — Minimal & Maintainable Changes**:
   - Prefer surgical, well-tested edits.
   - Reuse existing utilities (e.g., `RealUsageTracker`, `TimeUtils`, `InstagramPackageHelper`).
4. **Step 4 — Automated Verification**:
   - Run unit tests: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest`.
   - Never declare completion without passing build and tests.
5. **Step 5 — Synchronize Intelligence**:
   - Update affected documents in `analysis/` (e.g. database schema, feature status, changelog).
   - Log the completed task in [`TASK_LOG.md`](file:///d:/Be%20Best/analysis/TASK_LOG.md).
