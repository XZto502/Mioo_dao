# 喵岛维护入口

业务源码位于 `app/src/main/java/com/mioo/dao`。以浏览一个串为例，网络接口在 `data/api/XdApiService.kt`，数据获取和缓存策略从 `data/repository/ThreadRepository.kt` 查起，分页和恢复状态在 `ui/screens/thread/ThreadViewModel.kt`，列表呈现与手势在同目录的 `ThreadScreen.kt`。版面首页对应 ForumRepository、ForumViewModel 和 forum 目录。收藏订阅从 FeedRepository 查起，更新选择从 UpdateRepository 查起。

设置持久化从 `data/local/SettingsDataStore.kt` 查起，阅读进度查 ProgressDao 和 ThreadProgressEntity，数据库版本与迁移查 AppDatabase。返回时页面的生命周期还受 `ui/navigation/NavGraph.kt` 管理。

## 三个容易退化的行为

- **退出后立即滑动首页。** 隐藏页的释放与首页滑动会争用主线程。提交 `1e6fae9` 将清理延后到滚动间隙。修改导航时，从长串返回后立即甩动列表，再等待静止，分别检查滑动长帧和页面最终释放。
- **冷启动首屏。** 分阶段填充视口可以减少首次布局的工作，但内容尚未就绪时测量可能错过真正的首屏。参见 `adc0f41`、`2c66e2e` 和 InitialListViewportTest。验证时分别记录 Activity 显示和第一张有串号的卡片出现的时间。
- **列表边缘与横纵手势。** `6be0cf7` 移除了竞争的边缘拉伸，`715bc03` 修正纵向滚动后横滑分页的仲裁。修改前后都要滑到列表边缘，再反向接住惯性滚动，并在停止后横滑切页。

## 喵岛真机回归

仓库已有 `benchmark/src/main/java/com/mioo/dao/benchmark/StartupAndScrollBenchmark.kt`，涵盖冷启动、首页连续滑动、慢拖后连续快滑并反向打断、串详情阅读、返回首页立即滑动，目标包为 **com.mioo.dao**。请在专用测试设备运行该模块。保有用户正式安装的手机上，日常测试使用独立 beta。

以改动前的喵岛测试版为基线。在同一设备上，分别用改动前后的构建打开相同版面或同一长串，记录提交号、串号及实际刷新率。测试冷启首滑、连续快滑、慢拖、反向打断、打开详情再返回。

确认卡片已加载后，在每个滑动场景前执行 `dumpsys gfxinfo com.mioo.dao.beta reset`，结束后保存 `dumpsys gfxinfo com.mioo.dao.beta framestats`。按场景记录有效帧数、卡顿率、超时帧及 P50/P90/P95/P99。零帧或离开目标页面的轮次作废。验证惯性滑动手感时，另记相同手势松手后的位移和停稳时间。
