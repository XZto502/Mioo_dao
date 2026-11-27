# 开发与验证

- 从 [架构与性能决策](docs/architecture.md) 找到状态归属和对应回归场景。从 [更新渠道](docs/update-channels.md) 核对产物用途。
- 日常真机验证构建 `:app:assembleBeta`，安装前检查 APK 包名为 `com.mioo.dao.beta`。debug、preview 和当前 macrobenchmark 目标使用正式包名。
- 一次提交解决一个逻辑问题。滚动优化保留原有视觉与交互，用同设备、同内容和同手势记录改前改后数据。公共论坛上只做浏览验证。
- 常规检查见 [CI 命令](docs/ci.md)。签名材料、密码及本机恢复位置保存在仓库外。
