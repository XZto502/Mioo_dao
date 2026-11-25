# MiooDao (喵岛)

[简体中文](README.md) | **English**

[![Android CI](https://github.com/XZto502/Mioo_dao/actions/workflows/android-ci.yml/badge.svg)](https://github.com/XZto502/Mioo_dao/actions/workflows/android-ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/XZto502/Mioo_dao)](https://github.com/XZto502/Mioo_dao/releases)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)](https://developer.android.com/jetpack/compose)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

An open-source, offline-first Android client for **X-Island (`nmbxd.com`)**, built from scratch with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **Hilt**, and **WorkManager** (162 Kotlin source files, 43 unit test files, 29 tagged releases).

---

## Engineering Highlights

### 1. 120Hz UI & Performance Engineering
- **Staged Viewport Measurement**: Progressively measures initial `LazyColumn` viewports so cold-start and drawer opens stay responsive ([`InitialListViewport.kt`](app/src/main/java/com/mioo/dao/ui/components/InitialListViewport.kt)).
- **Off-Main-Thread HTML Prewarming**: Parses and caches thread/reply rich text off the UI thread ahead of rendering ([`HtmlPrewarmBridge.kt`](app/src/main/java/com/mioo/dao/ui/components/HtmlPrewarmBridge.kt)).
- **Flicker-Free Board Switching & Slot-Pool Reuse**: Keeps a single mounted `LazyColumn` slot pool across board switches with a solid `readingBackground()` veil and GPU-only reveal, avoiding blank-screen strobing or teardown jank ([`ForumScreen.kt`](app/src/main/java/com/mioo/dao/ui/screens/forum/ForumScreen.kt)).
- **Responsive List Motion**: Tuned scroll/fling physics and tap-during-slow-fling handling ([`ResponsiveListMotion.kt`](app/src/main/java/com/mioo/dao/ui/components/ResponsiveListMotion.kt)).

### 2. Offline-First Storage & Schema Evolution
- **13 Room Schema Versions**: Non-destructive database migrations with exported Room schemas for verification ([`DatabaseModule.kt`](app/src/main/java/com/mioo/dao/di/DatabaseModule.kt), [`app/schemas`](app/schemas/com.mioo.dao.data.local.AppDatabase)).
- **SWR Cloud-Feed Caching & Index Repair**: Stale-while-revalidate folder caching paired with background index maintenance ([`CloudFeedIndexWorker.kt`](app/src/main/java/com/mioo/dao/notification/CloudFeedIndexWorker.kt)).
- **Offline Thread Reader & Background Sync**: Full-thread offline downloads with live progress/transfer estimation, instant cached reopening, and conservative background updates ([`OfflineThreadDownloader.kt`](app/src/main/java/com/mioo/dao/data/repository/OfflineThreadDownloader.kt), [`OfflineUpdateWorker.kt`](app/src/main/java/com/mioo/dao/notification/OfflineUpdateWorker.kt)).

### 3. Multi-Channel Distribution & Verified Updates
- **3-Track Release Pipeline**: Isolated `release` (`com.mioo.dao`), `preview` (public pre-release with release signing), and `beta` (`com.mioo.dao.beta` parallel install) variants using 99-tail `versionCode` ordering ([`AppVersion.kt`](app/src/main/java/com/mioo/dao/utils/AppVersion.kt), [`UpdateChannel.kt`](app/src/main/java/com/mioo/dao/utils/UpdateChannel.kt)).
- **Mirror Fallback & Pre-Install Verification**: Multi-mirror `version.json` fallback for stable updates, 100-release lookup for beta channels, and pre-install verification of APK byte size, SHA-256 digest, package name, `versionCode`, and signing certificate ([`UpdateRepository.kt`](app/src/main/java/com/mioo/dao/data/repository/UpdateRepository.kt), [`UpdateDownloader.kt`](app/src/main/java/com/mioo/dao/utils/UpdateDownloader.kt)).

### 4. Testing, CI & Network Diagnostics
- **43 JVM Unit Test Files**: Covering pagination, Room/offline policies, draft migration, update channel routing, header contrast sampling, link parsing, and doodle save policy ([`app/src/test`](app/src/test/java/com/mioo/dao)).
- **Hardened GitHub Actions CI**: Pinned action SHAs running Kotlin compilation, unit tests, Android Lint, and R8-shrunk APK verification ([`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)).
- **Built-In Network Diagnostics**: On-device diagnostic runner for X-Island DNS, TLS, and endpoint latency/connectivity checks ([`XIslandNetworkDiagnosticsRunner.kt`](app/src/main/java/com/mioo/dao/data/network/XIslandNetworkDiagnosticsRunner.kt)).

---

## Key Features

- **Reading & Media**: Board/timeline browsing with source board badges, thread detail reader with quote popups and inline preview cards, full-screen image viewer with default fit-width for long strips and pan fling momentum.
- **Posting & Tools**: New thread & reply composer, automatic per-thread draft box, kaomoji & dice panels, built-in **Doodle Pad** supporting manual gallery save and auto-save on confirm.
- **Organization & Offline**: Multi-cookie management with QR scan import, local bookmarks & cloud feed UUID folders with OP content search, keyword/thread/userhash blocking, browsing history, and offline thread management.
- **Customization & System**: Material 3 dynamic/custom themes, optional frosted glass & custom top bar with per-region auto-contrast, deep link handling (`nmbxd.com`), Chrome Custom Tabs web search, backup/restore, and in-app update checks.

---

## Tech Stack

- **Language & UI**: Kotlin, Jetpack Compose, Material 3
- **Architecture & DI**: MVVM + Repository pattern, Hilt, Coroutines / Flow
- **Networking & Images**: Retrofit, OkHttp, Moshi, Coil
- **Persistence & Background**: Room (KSP), DataStore Preferences, WorkManager
- **Browser Integration**: AndroidX Browser (Chrome Custom Tabs)

---

## Project Structure

```text
Mioo_dao/
├── .github/workflows/          # Android CI workflow (compile, test, lint, R8 beta)
├── app/
│   ├── schemas/                # Exported Room schema JSONs (v1–v13)
│   └── src/
│       ├── main/java/com/mioo/dao/
│       │   ├── data/           # API services, Room DAOs/entities, repositories, network diagnostics
│       │   ├── di/             # Hilt modules & Room migrations
│       │   ├── notification/   # WorkManager workers & subscription notifier
│       │   ├── ui/             # Compose screens, ViewModels, shared components, theme, navigation
│       │   └── utils/          # Versioning, update channels/downloader, link parser, image tools
│       └── test/java/com/mioo/dao/ # 43 JVM unit test files
└── docs/                       # CI, update channel, and refactoring notes
```

---

## Build & Test

### Prerequisites
- **JDK**: 17+
- **Android SDK**: `compileSdk 34`, `minSdk 26`
- **IDE**: Recent Android Studio (or Gradle CLI)

### Commands

```bash
git clone https://github.com/XZto502/Mioo_dao.git
cd Mioo_dao

# Run JVM unit tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build debug or parallel-installable beta APK
./gradlew assembleDebug
./gradlew assembleBeta
```

Debug APKs are generated under `app/build/outputs/apk/debug/`. Prebuilt releases are published on [GitHub Releases](https://github.com/XZto502/Mioo_dao/releases).

---

## Documentation

- [CI Workflow Guide](docs/ci.md)
- [Release & Beta Update Channels](docs/update-channels.md)
- [1.1.6 Refactoring Notes](docs/refactoring-1.1.6.md)

---

## License

Licensed under the [GNU General Public License v3.0 (GPL v3)](LICENSE). Any modifications or derivative works must remain open-source under the same license.
