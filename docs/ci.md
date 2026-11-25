# 自动测试

GitHub Actions 的 `Android CI` 在分支推送、Pull Request 和手动运行时执行。
同一分支的新提交会取消上一轮未完成的检查。

## 检查内容

- `compileDebugKotlin`：检查 Kotlin 编译。
- `testDebugUnitTest`：执行全部 JVM 回归测试。
- `lintDebug`：检查 Android Lint，错误会阻止通过，警告保留在报告中。
- `assembleBeta`：前三项通过后构建启用 R8 的测试 APK。

使用项目自带 Gradle Wrapper、Temurin 17 和 Android SDK 34，不需要配置签名 Secrets。
Actions 固定到提交 SHA；升级时同时更新旁边的版本注释。

## 查看结果

在仓库 Actions 中打开运行记录：

- `android-reports-*`：测试 XML、HTML 和 Lint 报告，失败时也尝试上传，保留 14 天。
- `beta-ci-*`：测试 APK 和 R8 mapping，保留 7 天。

CI APK 使用运行器临时调试证书，仅用于 CI 验证。它与手机当前测试版签名不同，
不能作为已有测试版的覆盖升级包。正式分发继续使用本地指定证书构建。
CI 不发布 GitHub Release，也不上传云盘。

本地对应命令：

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest lintDebug --continue
.\gradlew.bat assembleBeta
```

此流程覆盖编译、逻辑回归、静态检查和 R8 打包，不包含真机触摸、帧率或预测性返回测试。
