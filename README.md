# 喵岛 (MiooDao)

[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)

X 岛 (nmbxd.com) 的第三方 Android 客户端。Compose + Material 3，GPL v3。

## 功能

- 板块浏览、串详情、引用查看、全屏看图
- 发串 / 回复，颜文字与骰子快捷面板
- 回复草稿按串自动保存
- 关键字 / 串 ID / 饼干屏蔽
- 多饼干、收藏订阅、浏览历史
- 毛玻璃与深浅色主题（可关）
- 扫码导入饼干、应用内更新检查

## 技术

Compose · Hilt · Retrofit / OkHttp / Moshi · Room · DataStore · Coil

## 构建

- JDK 17+
- Android SDK：`compileSdk 34`，`minSdk 26`
- Android Studio 较新版本即可

```bash
git clone https://github.com/XZto502/Mioo_dao.git
cd Mioo_dao
```

用 Android Studio 打开，同步 Gradle 后 Run。

或命令行：

```bash
./gradlew assembleDebug
```

APK 输出在 `app/build/outputs/apk/debug/`。

发布版见 [Releases](https://github.com/XZto502/Mioo_dao/releases)。

## 协议

[GPL v3](LICENSE)。修改或衍生作品需继续开源并使用相同协议。
