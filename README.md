# 喵岛 (MiooDao) 

[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

**喵岛 (MiooDao)** 是一款专门为匿名版 X岛 (nmbxd.com) 设计的、具备极致美感与现代交互体验的第三方 Android 客户端。

本项目遵循 **GPL v3** 开源协议，基于 Jetpack Compose 与最新的 Material Design 3 设计语言构建，辅以精致的毛玻璃玻璃拟态（Glassmorphism）和流动气泡背景，力求为岛民提供流畅、沉浸、高级的刷岛体验。

---

## ✨ 核心特性

- 🎨 **拟态毛玻璃 (Glassmorphism) 主题**
  - 支持一键开关的 **M3 毛玻璃融合界面**，由 `dev.chrisbanes.haze` 强力驱动。
  - 拥有极其自然的慢速漂移 **流光渐变背景**，光晕在底层静谧漂移，告别枯燥单色。

- 💬 **流畅的输入与颜文字键盘**
  - 内嵌微信/Telegram 式交互的 **快捷颜文字（Kaomoji）快速输入面板**。
  - 智能焦点与软键盘避让：点击笑脸呼起面板时自动收起系统键盘，点击输入框时光标自动定位聚焦并隐藏面板，防止输入框被遮挡或顶飞。

- 📝 **智能自动草稿箱 (Draft Box)**
  - **静默实时保存**：在回复框或发帖弹窗中输入时，系统会自动在后台保存草稿。
  - **单串独立恢复**：回复框草稿与具体的帖子（Thread ID）自动绑定，即使切换页面或手势返回，下次进入该串时未提交内容依然在。
  - **发送自动清空**：内容提交成功后自动清除该草稿，干净无残留。

- 🛡️ **强力多维关键字屏蔽 (Filter)**
  - 支持 **关键字屏蔽**、**整串 ID 屏蔽** 和 **发言饼干（UserHash）屏蔽**。
  - 一处屏蔽，全局生效。板块帖子列表、全站时间线（Timeline）、串内回复列表会自动过滤带有不良/不感兴趣关键字或特定饼干的卡片。
  - 提供专门的设置面板，支持一键输入新增屏蔽关键字和垃圾桶快捷删除。

- 📸 **丝滑的媒体与引用查看**
  - **全屏图片查看器**：支持双击放大、手势捏合缩放、双指拖拽的高性能大图浏览组件。
  - **引用悬浮卡片 (RefPopup)**：点击 `>>No.xxxx` 自动弹出优雅的引用贴卡片，支持嵌套多层弹窗，无需反复跳转。

- 📷 **便捷工具**
  - 纯净的 **二维码扫描器**：针对扫描饼干（Cookies）进行定制，强制竖屏扫描且已去除提示音，无感且优雅。

---

## 🛠️ 技术栈

- **构建系统**: Gradle Kotlin DSL
- **UI 框架**: Jetpack Compose (1.7+), Material Design 3
- **依赖注入**: Dagger Hilt
- **网络层**: Retrofit, OkHttp, Moshi (用于 API 解析)
- **本地存储**:
  - **Room Database**: 存储历史记录、本地收藏/订阅
  - **DataStore**: 管理系统设置（毛玻璃、暗色模式、多饼干列表、屏蔽设置等）
- **图片加载**: Coil (支持 GIF/网络图片渲染)

---

## 🚀 编译与开发

### 环境要求
- **Android Studio**: Ladybug / Koala 或更新版本
- **JDK**: Version 17 或更高
- **Android SDK**: compileSdk 35, minSdk 31

### 编译步骤
1. 克隆本项目：
   ```bash
   git clone https://github.com/XZto502/Mioo_dao.git
   cd Mioo_dao
   ```
2. 使用 Android Studio 打开项目。
3. 等待 Gradle 同步完成后，点击 **Run** 即可在您的设备或模拟器上运行。

---

## 📄 开源协议

本项目采用 **[GPL v3](LICENSE)** 开源协议。所有基于本项目修改或衍生出的作品，均需保持开源并使用相同协议分发。
