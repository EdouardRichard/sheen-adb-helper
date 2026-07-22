# Quickstart Validation: v0.03 设备文件与日志诊断

## 1. Prerequisites

- JDK/Android SDK 与仓库现有 Gradle 环境可用。
- 测试数据必须是合成目录、合成包或唯一标记日志；不得保存真实端点、包名上下文、Shell 输出或 Logcat。
- 主控端至少覆盖 Android 11/API 30 与 Android 16/API 36；被控端另需覆盖一台通过 `IP:5555` 授权且使用 Sync v1 的 Android 10 或更早设备，并按下方 ROM 矩阵补充厂商设备。
- 开始前确认 `.specify/feature.json` 指向 `specs/003-device-file-diagnostics`。

## 2. Automated and build gates

```powershell
.\gradlew.bat :core:adb:testDebugUnitTest :core:data:testDebugUnitTest :feature:files:testDebugUnitTest :feature:logcat:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat lintDebug
```

预期：所有命令退出码为 0。分别报告测试、构建和 lint；任一通过不得替代其他门禁。

真机复测不得只依据 `versionName` 判断制品是否最新；v0.03 最终版本号由 T070 在收口阶段更新。每轮复测前必须重新安装刚生成的 Debug APK，并确认设备端安装更新时间不早于 APK 生成时间：

```powershell
$apk = Get-Item .\app\build\outputs\apk\debug\app-debug.apk
adb install -r $apk.FullName
adb shell dumpsys package com.sheen.adbhelper | Select-String 'lastUpdateTime|versionCode|versionName'
$apk | Select-Object FullName, Length, LastWriteTime
Get-FileHash $apk.FullName -Algorithm SHA256
```

本地哈希仅用于确认本轮安装与复测制品一致，不写入仓库验收证据。若设备端 `lastUpdateTime` 早于 APK 的 `LastWriteTime`，该轮结果只能归因于旧制品，不得用于判断当前代码是否仍存在相同缺陷。

核心测试至少证明：

- Sync v1/v2、v1 `.`/`..` 过滤、v1 `/sdcard` 链接的 LIST 探测、64 KiB 分块、`Long` 越过 `Int.MAX_VALUE`、2 GiB 基线。
- 来源前后元数据稳定性、SHA-256 回退、来源变化和摘要能力不足不得提交成功。
- Session 切换、子流关闭、30 秒无进展、取消与清理。
- 10,000 项目录上限、1,024 字节路径、特殊字符、链接循环。
- APK base/split、重复/变化/缺失与整体回滚。
- SAF provider 能力、冲突、备份提交、回滚与清理失败。
- 文件/Logcat 原子租约竞争只能一个成功。
- Logcat 512 条/1 MiB/64 KiB、空历史、两阶段交接、多重集去重和显式降级。

## 3. Manifest and dependency gate

生成 merged Manifest 后确认只有 `android.permission.INTERNET`，且不存在存储/媒体、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、通知、前台服务、自启动或无障碍声明。确认 Version Catalog 与解析依赖没有新增项。

ADR 0004 的 `spake2-java 1.0.5` 许可证阻断仍须单独报告；本功能通过不等于发布合规通过。

## 4. File browsing scenarios

在合成设备目录中准备：空目录、无权限目录、普通文件、零字节文件、中文/空格/引号/换行名称、至少 2 GiB 文件、可达链接、失效链接和循环链接。

验证：

1. 默认进入第一个可列出的共享存储候选。
2. “设备根目录”入口和面包屑可进入当前 ADB 身份允许的其他路径，不需要输入 Shell 路径。
3. ≤1,000 项目录 95% 在 3 秒内完成或返回明确错误。
4. 空目录不显示为错误，无权限不显示为空。
5. 链接身份可见；可达非循环目标可进入/选择；拒绝失效、无权限和循环目标。
6. >10,000 项返回完整容量错误，不展示部分列表。
7. 当前路径显示为连续的 `/目录名/子目录名`，不得在分隔符和目录名之间出现由按钮布局造成的大段空隙。
8. 在 Android 10 或更早的 Sync v1 被控设备上通过 `IP:5555` 连接后，共享存储和子目录可浏览，且 `.`/`..` 不显示、不触发“协议不兼容”。

## 5. Bidirectional transfer scenarios

对上传和下载分别验证：空文件、普通文件、至少 2 GiB 文件、特殊名称、大小未知来源、目标空间不足、30 秒无进展、断开、Session 切换、用户取消和应用进入后台。上述上传、下载至少在一台通过 `IP:5555` 授权且仅提供 Sync v1 的 Android 10 或更早被控设备上各完成一次，不得因版本低于 Android 11 拒绝。

冲突分别选择覆盖、自动重命名和取消；未选择时原目标必须不变。失败/取消不得留下成功外观的非完整结果；清理失败必须显示受影响目标。使用测试端外部 SHA-256 或逐字节比较验证所有成功结果与稳定源一致，哈希值不得进入仓库证据。

应用内切换页面时任务继续且全局摘要可返回/取消；Activity 真正进入后台时任务取消；配置变化不取消。

补充 SAF Provider 回归：上传来源至少覆盖一个只返回通用 `OpenableColumns`、不返回 DocumentsContract flags/mtime 的 Provider；下载至少覆盖系统 ExternalStorageProvider 创建的 `tree/.../document/...` 子文档 URI。两者均不得误报 IO 或安全写入能力不足。位于文件页面时全局摘要不显示“查看”，切换到其他页面后显示且可返回文件页面。

旧版 Sync v1 真机回归还需覆盖：首次零字节远端子流关闭后仅重试一次并成功；已有进度后断流不重试；本地来源/目标流失败不重试；远端权限/路径拒绝不显示为会话关闭；上传提交或清理失败不会触发主线程网络异常或 App 退出。失败证据只记录脱敏技术码和错误类别。

能力矩阵必须额外覆盖 `ls_v2 + stat_v2 + 无 sendrecv_v2` 的混合组合，确认目录仍使用 v2 元数据而下载/上传按 Sync v1 兼容路径执行。

## 6. APK extraction scenarios

使用合成的单 APK 测试应用、含多个 split 的测试应用，并至少选择一个设备允许读取的系统包验证只读候选范围：

1. 单 APK 输出一个 `{package}.apk`。
2. split 应用输出一个 `{package}-apks` 目录，组成与设备报告一一对应。
3. 选择后更新/卸载应用、重复 basename、任一组成失败或空间不足时整体不得成功，暂存集合被清理。
4. 诊断、测试输出和全局摘要不包含真实包名或 APK 路径。
5. 历史应用管理页面仍只显示和管理其既有第三方应用范围，APK 候选不得扩大该页面的修改权限。

## 7. Logcat scenarios

对每台矩阵设备使用唯一合成标记覆盖：空历史、开始前 5 分钟内、交接边界、开始后、日志洪泛、时区/设备时间跳变、buffer 拒绝、取消、断开和 Session 切换。

预期：

- 95% 在 3 秒内进入历史、空历史或能力降级状态。
- 默认历史最多 512 条/1 MiB，单行 64 KiB；UI 仍仅显示最新 100 条。
- 边界唯一标记恰好一次，follow 后标记至少一次；合法重复不被过度删除。
- 能力不足时不自动降级，用户可选择 follow-only 或 history-only；不可用选项不展示。
- 文件任务活动时 Logcat 不启动且不取消文件任务，反向竞争同理。
- 离开 Logcat 页面立即停止，原始日志不进入诊断或自动文件。

## 8. Minimum device/provider matrix

| Target | Coverage |
|---|---|
| Android 9/API 28 或 Android 10/API 29 AOSP/Pixel（被控端，`IP:5555`） | Sync v1 浏览/双向传输、`.`/`..`、`/sdcard` 链接，以及 APK/Logcat 能力探测与明确限制 |
| Android 11/API 30 AOSP/Pixel | 主控端最低平台、Sync/logcat 基线 |
| Android 13/API 33 Pixel | 中间平台基线 |
| Android 14+ Samsung One UI | 厂商 buffer/provider 策略 |
| Android 15+ HyperOS 或 ColorOS | 另一厂商路径、权限与 logcat 差异 |
| Android 16/API 36 AOSP/Pixel | 当前 target/compile 基线 |
| 系统 ExternalStorageProvider | SAF 创建、重命名、删除、覆盖回滚 |
| 至少一个能力受限 DocumentsProvider | 缺少 flags 时的开始前拒绝与换位置提示 |

每项证据只记录合成标记计数、时延、大小级别和结构化技术代码。自动化、构建、Manifest、provider、真机与发布合规分别给出结论。
