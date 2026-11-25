# Android CI

GitHub Actions 的 `Android CI` 工作流在分支推送、Pull Request 与手动触发时运行。同一分支的新提交会自动取消上一轮未完成的检查。

## 检查内容

- `compileDebugKotlin`：校验 Kotlin 编译。
- `testDebugUnitTest`：运行全部 JVM 单元测试。
- `lintDebug`：运行 Android Lint 静态检查（存在 Error 时失败，Warning 保留在报告中）。
- `assembleBeta`：上述检查通过后构建启用 R8 混淆与资源压缩的测试版 APK。

工作流使用仓库自带的 Gradle Wrapper、Temurin JDK 17 与 Android SDK 34，无需配置签名 Secrets；第三方 Actions 均固定到具体提交 SHA。

## 构建产物与报告

每次运行结束后可在 GitHub Actions 页面下载对应产物：

- `android-reports-*`：单元测试 XML/HTML 与 Lint 报告（失败时同样上传，保留 14 天）。
- `beta-ci-*`：CI 构建的 `app-beta.apk` 与 R8 `mapping.txt`（保留 7 天）。

> 说明：CI 产物使用临时调试签名，仅用于自动化验证与预览测试，不会自动发布 Release，也不能覆盖已安装的正式签名安装包。

## 对应命令

```bash
./gradlew compileDebugKotlin testDebugUnitTest lintDebug --continue
./gradlew assembleBeta
```
