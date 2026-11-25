# 喵岛 (MiooDao)

**简体中文** | [English](README_en.md) *(Open-source, offline-first Jetpack Compose Android client)*

[![Android CI](https://github.com/XZto502/Mioo_dao/actions/workflows/android-ci.yml/badge.svg)](https://github.com/XZto502/Mioo_dao/actions/workflows/android-ci.yml)
[![Latest Release](https://img.shields.io/github/v/release/XZto502/Mioo_dao)](https://github.com/XZto502/Mioo_dao/releases)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg)](https://developer.android.com/jetpack/compose)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

X 岛（`nmbxd.com`）第三方开源、离线优先 Android 客户端，基于 **Kotlin**、**Jetpack Compose**、**Material 3**、**Room**、**Hilt** 与 **WorkManager** 构建（全库 162 个 Kotlin 源码文件、43 个单元测试文件、29 个发布版本）。

---

## 工程亮点

### 1. 120Hz 流畅度与性能工程
- **渐进式首屏测量**：对 `LazyColumn` 首屏进行分段测量，降低冷启动与抽屉打开时的首帧阻塞（[`InitialListViewport.kt`](app/src/main/java/com/mioo/dao/ui/components/InitialListViewport.kt)）。
- **离屏 HTML 预解析**：在后台线程预解析并缓存串内容与回复富文本，减少滚动时主线程排版耗时（[`HtmlPrewarmBridge.kt`](app/src/main/java/com/mioo/dao/ui/components/HtmlPrewarmBridge.kt)）。
- **切换板块防闪烁与列表池复用**：切版时保留单实例 `LazyColumn` 缓存池不拆重建，配合纯色 `readingBackground()` 遮罩与 GPU 淡出揭幕，兼顾视觉一致性与滑动性能（[`ForumScreen.kt`](app/src/main/java/com/mioo/dao/ui/screens/forum/ForumScreen.kt)）。
- **响应性滑动与惯性点击**：调优列表惯性参数并支持慢速滑动中的直接点击响应（[`ResponsiveListMotion.kt`](app/src/main/java/com/mioo/dao/ui/components/ResponsiveListMotion.kt)）。

### 2. 离线优先存储与数据库演进
- **Room 13 个版本的非破坏性迁移**：完整保留历史版本升级路径并导出 Schema 用于校验（[`DatabaseModule.kt`](app/src/main/java/com/mioo/dao/di/DatabaseModule.kt)、[`app/schemas`](app/schemas/com.mioo.dao.data.local.AppDatabase)）。
- **SWR 云端收藏缓存与索引修复**：采用 Stale-While-Revalidate 策略秒切云端收藏夹，并支持后台自动修复搜索索引（[`CloudFeedIndexWorker.kt`](app/src/main/java/com/mioo/dao/notification/CloudFeedIndexWorker.kt)）。
- **离线阅读与后台保守更新**：支持全串离线下载（带实时进度与流量估算）、已缓存离线串秒开，以及后台保守增量更新（[`OfflineThreadDownloader.kt`](app/src/main/java/com/mioo/dao/data/repository/OfflineThreadDownloader.kt)、[`OfflineUpdateWorker.kt`](app/src/main/java/com/mioo/dao/notification/OfflineUpdateWorker.kt)）。

### 3. 多通道分发与安全更新校验
- **三轨道发布体系**：基于 99 尾号 `versionCode` 排序区分 `release`（正式包 `com.mioo.dao`）、`preview`（同签名公开测试包）与 `beta`（独立并存测试包 `com.mioo.dao.beta`）（[`AppVersion.kt`](app/src/main/java/com/mioo/dao/utils/AppVersion.kt)、[`UpdateChannel.kt`](app/src/main/java/com/mioo/dao/utils/UpdateChannel.kt)）。
- **多镜像回退与安装前校验**：正式通道支持多镜像 `version.json` 回退，测试通道支持拉取最近 100 条 Releases，并在调起安装前校验 APK 大小、SHA-256 摘要、包名、`versionCode` 与签名证书一致性（[`UpdateRepository.kt`](app/src/main/java/com/mioo/dao/data/repository/UpdateRepository.kt)、[`UpdateDownloader.kt`](app/src/main/java/com/mioo/dao/utils/UpdateDownloader.kt)）。

### 4. 测试、CI 与网络诊断
- **43 个 JVM 单元测试文件**：覆盖分页协调、离线策略、草稿迁移、更新分流、顶栏自动对比度采样、深链解析与涂鸦保存策略等（[`app/src/test`](app/src/test/java/com/mioo/dao)）。
- **GitHub Actions CI**：固定 Action SHA，每次提交自动执行编译、单元测试、Android Lint 与 R8 混淆构建（[`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)）。
- **内置网络诊断**：支持直接在客户端内检测 X 岛 DNS、TLS 与各接口连通性及延迟（[`XIslandNetworkDiagnosticsRunner.kt`](app/src/main/java/com/mioo/dao/data/network/XIslandNetworkDiagnosticsRunner.kt)）。

---

## 核心功能

- **阅读与看图**：板块与时间线浏览（时间线展示来源板块标签）、串详情、引用弹窗与正文内联预览卡片、全屏看图（长图默认满宽适配与缩放拖拽惯性）。
- **发帖与创作工具**：发串与回复编辑、按串自动保存草稿箱、颜文字与骰子快捷面板、内置**涂鸦板**（支持手动保存到相册及「完成」时自动保存到相册）。
- **收藏、订阅与离线**：多饼干切换与扫码导入、本地收藏夹与云端订阅 UUID（支持 PO 主内容搜索）、关键词 / 串 ID / 饼干屏蔽、浏览历史与离线串管理。
- **个性化与系统能力**：Material 3 动态/自定义配色与深浅色主题、可选毛玻璃及自定义顶栏无遮罩分区自动对比度、X 岛深链直接打开、Chrome Custom Tabs 站内搜索、备份恢复与更新通道切换。

---

## 技术栈

- **语言与界面**：Kotlin · Jetpack Compose · Material 3
- **架构与依赖注入**：MVVM + Repository 分层 · Hilt · Coroutines / Flow
- **网络与图片**：Retrofit · OkHttp · Moshi · Coil
- **存储与后台任务**：Room (KSP) · DataStore Preferences · WorkManager
- **浏览器集成**：AndroidX Browser (Chrome Custom Tabs)

---

## 目录结构

```text
Mioo_dao/
├── .github/workflows/          # Android CI 工作流（编译、单测、Lint、R8 测试构建）
├── app/
│   ├── schemas/                # 导出的 Room 数据库 Schema（v1–v13）
│   └── src/
│       ├── main/java/com/mioo/dao/
│       │   ├── data/           # API 接口、Room DAO/实体、Repository、网络诊断
│       │   ├── di/             # Hilt 注入模块与 Room 迁移规则
│       │   ├── notification/   # WorkManager 后台任务与订阅提醒
│       │   ├── ui/             # Compose 页面、ViewModel、公共组件、主题与导航
│       │   └── utils/          # 版本排序、更新通道与校验、链接解析、图片处理
│       └── test/java/com/mioo/dao/ # 43 个 JVM 单元测试文件
└── docs/                       # CI、更新通道与重构说明文档
```

---

## 构建与测试

### 环境要求
- **JDK**：17+
- **Android SDK**：`compileSdk 34`，`minSdk 26`
- **IDE**：较新版本 Android Studio（或直接使用 Gradle 命令行）

### 常用命令

```bash
git clone https://github.com/XZto502/Mioo_dao.git
cd Mioo_dao

# 运行 JVM 单元测试
./gradlew testDebugUnitTest

# 运行 Android Lint
./gradlew lintDebug

# 构建 Debug 包或可与正式版共存的 Beta 包
./gradlew assembleDebug
./gradlew assembleBeta
```

Debug 安装包输出路径为 `app/build/outputs/apk/debug/`。预编译安装包请见 [GitHub Releases](https://github.com/XZto502/Mioo_dao/releases)。

---

## 相关文档

- [CI 工作流说明](docs/ci.md)
- [正式版与测试版更新通道](docs/update-channels.md)

---

## 开源协议

基于 [GNU General Public License v3.0 (GPL v3)](LICENSE) 开源。任何修改或衍生作品需继续开源并使用相同协议。
