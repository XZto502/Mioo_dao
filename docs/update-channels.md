# 正式版与测试版更新通道

用户可在「应用设置 → 更新通道」中选择 **正式版** 或 **测试版**（默认正式版），选择保存在本地并随备份恢复；切换通道不会触发自动安装或降级，也不会清空本地数据。

自动检查与「更多 → 检查更新」共用此设置；关闭第三方服务授权后，应用不会向 GitHub 发起更新请求。

## 分流规则

- **正式通道（`release`）**：优先读取 `version.json` 及其镜像地址，兜底查询 GitHub `releases/latest`。
- **测试通道（`preview` / `beta`）**：读取仓库最近 100 条 GitHub Releases（含预发行版），按 99 尾号 `versionCode` 规则匹配最高可用版本（自动跳过草稿、非法版本号及无兼容 APK 的发行版）。
  - 同版本号下正式版权重高于测试版（`99` > `1..98`），因此测试版用户会在正式版发布后平滑升级至正式版。
  - 从测试通道切回正式通道时，若当前版本高于最新正式版，会等待后续更高版本的正式版，不尝试降级安装。

## 发行包类型

1. **正式发行版（`release`）**
   - 包名 `com.mioo.dao`，`versionCode` 尾号 `99`，产物为 `app-release.apk`，并同步更新 `version.json`。
2. **公开测试发行版（`preview`）**
   - 保持正式包名 `com.mioo.dao` 与正式签名（需配置 `RELEASE_*` 签名参数），`versionNameSuffix` 为 `-beta.N`。
   - 执行 `./gradlew assemblePreview` 生成 `app/build/outputs/apk/preview/app-preview.apk`，发布为 GitHub Pre-release 后支持覆盖正式版升级。
3. **独立并存测试版（`beta`）**
   - 包名 `com.mioo.dao.beta`，可与正式版并行安装，仅接收同名附件 `app-beta.apk`，不接收正式包或公开测试包更新。

发行版及预发行版字段格式参见 [GitHub Releases API 文档](https://docs.github.com/en/rest/releases/releases#list-releases)。
