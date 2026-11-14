# 正式版与测试版更新通道

用户在「应用设置 → 更新通道」选择正式版或测试版，默认正式版。
选择保存在本地并进入备份。切换不触发安装或降级，不会清空数据。
自动检查和「更多 → 检查更新」共同使用此选择；关闭第三方服务后不会请求更新服务器。

正式通道继续使用 `version.json` 与正式发行版后备地址。
测试通道读取仓库最近 100 个 GitHub Releases，包含正式版及预发行版，
按应用版本规则选择最高版本，不依赖创建顺序。草稿、不合法版本及无兼容 APK 的发行版会被跳过。
测试通道中正式版高于同版本 beta，所以测试版转正后也会收到更新。
切回正式通道后，如果当前测试版本更高，会等待后续正式版本，不尝试降级安装。

## 发布可覆盖正式版的测试发行包

1. 推进 `versionCode`，保证大于此前公开分发的所有安装包。
2. 同步调整 `preview` 的 `versionNameSuffix`，例如 `-beta.7`。
3. 配置与正式发行版完全相同的 `RELEASE_*` 签名证书。
4. 执行 `./gradlew assemblePreview`，获得 `app/build/outputs/apk/preview/app-preview.apk`。
5. 在 GitHub 创建对应版本的 Release，例如 `v1.1.7-beta.7`，勾选 **Pre-release**，上传 `app-preview.apk`。

公开测试发行包保持正式包名 `com.mioo.dao` 与正式签名，支持覆盖更新。
正式发行版仍上传 `app-release.apk` 并更新 `version.json`。
`preview` 打包必须提供正式签名配置，防止意外用调试签名公开分发。

## 独立开发测试包

原 `beta` 构建继续使用 `com.mioo.dao.beta`，与正式版共存。
此包只接收同名附件 `app-beta.apk`，绝不下载正式包或公开测试包。
它不能通过切换通道转换为正式包；切换只决定是否接收预发行版更新。
CI 产物使用临时调试证书，不能用于公开测试通道覆盖更新。

工作流不会自动发布测试版，仍需作者明确创建发行版并上传安装包。

发行版及预发行版字段说明见 [GitHub Releases API 文档](https://docs.github.com/en/rest/releases/releases#list-releases)。
