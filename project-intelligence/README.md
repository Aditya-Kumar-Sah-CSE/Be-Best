# FocusLock — Project Intelligence System

Welcome to the **Project Intelligence System** for FocusLock. This directory serves as the persistent, authoritative knowledge base for the architecture, dependencies, data flows, permissions, database schema, build processes, and task history of the FocusLock Android application.

---

## 📌 Mission & Mandatory Agent Workflow

The primary goal of this intelligence system is to **eliminate repeated whole-project re-analysis** across future tasks while preserving 100% technical accuracy and safety.

### ⚠️ Critical Rule: Source Code is Authoritative
> **IMPORTANT:**
> Documentation is an accelerating map, **not** a substitute for source code. If any detail in these documents conflicts with the actual source code, **the source code is the sole authoritative source of truth**. Never make destructive changes, database migrations, security alterations, or permission claims based solely on cached documentation without verifying the current code.

### 🔄 The Standard Task Lifecycle

Every future coding or debugging task must follow this 6-step loop:

```
┌─────────────────────────────────┐
│ 1. Read project-intelligence/   │
│    README.md & FEATURE_MAP.md   │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 2. Trace FILE_DEPENDENCIES.md   │
│    & FILE_INDEX.md              │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 3. Inspect Only Relevant        │
│    Target Files & Immediate SDK │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 4. Implement Change & Validate  │
│    via Unit Tests / Gradle      │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 5. Update Affected Intelligence │
│    Docs (Schema, Perms, Map)    │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│ 6. Append TASK_LOG.md           │
│    & CHANGELOG.md               │
└─────────────────────────────────┘
```

---

## 🗺️ Intelligence Documents Index

| Document | Purpose & Content | When to Consult |
| :--- | :--- | :--- |
| [**PROJECT_OVERVIEW.md**](PROJECT_OVERVIEW.md) | High-level application purpose, tech stack, SDK requirements, module structure, and third-party libraries. | Starting a new feature, reviewing project boundaries or SDK versions. |
| [**ARCHITECTURE.md**](ARCHITECTURE.md) | Clean Architecture + MVVM layers, data flow diagrams, background service lifecycles, and core architectural decisions. | Designing cross-layer features, modifying data pipelines or services. |
| [**FILE_INDEX.md**](FILE_INDEX.md) | Structured index of all 64+ Kotlin source files, their responsibilities, key symbols, and affected features. | Locating which source files to edit without scanning the directory tree. |
| [**FILE_DEPENDENCIES.md**](FILE_DEPENDENCIES.md) | Verified relationships between tracking, quota engine, accessibility, Room DB, overlay service, and Compose UI. | Understanding the blast radius and callers before modifying a class. |
| [**DATABASE_SCHEMA.md**](DATABASE_SCHEMA.md) | Room Database v4 schema, entities, indexes, DAOs, migrations (MIGRATION_2_3, MIGRATION_3_4), and retention rules. | Adding entities, modifying columns, debugging SQLite queries, or writing migrations. |
| [**ANDROID_PERMISSIONS.md**](ANDROID_PERMISSIONS.md) | All declared permissions (`PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE`, `SYSTEM_ALERT_WINDOW`, etc.), code callers, and OS restrictions. | Modifying background behaviors, accessibility events, overlays, or installer intents. |
| [**FEATURE_MAP.md**](FEATURE_MAP.md) | Granular breakdown of each core feature (App Blocking, Usage Tracking, Floating Timer, Break Reminder, In-App Updater). | Working on a specific user-facing feature. |
| [**BUILD_AND_TEST.md**](BUILD_AND_TEST.md) | Verified Gradle build commands, test runners, JDK 17 requirements, signing configs, and APK outputs. | Compiling, executing tests, or preparing release APKs. |
| [**KNOWN_ISSUES.md**](KNOWN_ISSUES.md) | Confirmed defects, root cause analyses, Android OS limitations (OEM BAL restrictions, UsageStats delays), and status. | Diagnosing bugs, preventing regressions on Xiaomi/ColorOS/Samsung devices. |
| [**CHANGELOG.md**](CHANGELOG.md) | Chronological record of significant codebase and architectural milestones. | Understanding why and when historical changes occurred. |
| [**TASK_LOG.md**](TASK_LOG.md) | Compact operational log of recent tasks, files modified, test outcomes, and updated documents. | Tracking work history and handover between agent sessions. |

---

## 🛠️ Automated Staleness Checking

To check if any source files have been modified since the documentation was last refreshed, run:
```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-doc-staleness.ps1
```
This inspects `git status` / `git diff` against known file mappings and highlights intelligence documents that may need updating.
