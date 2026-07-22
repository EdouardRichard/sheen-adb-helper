# Contract: ADB 文件、APK 与 Logcat

本契约描述 `:core:adb` 对项目其他模块公开的自有接口语义；具体 Kadb、Okio、Sync 帧、Socket、Shell 命令和异常类型不得越界。

## Session and exclusive operation

- 所有方法接收或捕获 `expectedSessionId`，结果在发出前再次验证。
- `FILE_TRANSFER`、`APK_EXTRACTION`、`LOGCAT` 通过原子租约互斥；冲突返回 `OPERATION_CONFLICT`，现有任务不变。
- 租约释放幂等，必须覆盖成功、失败、取消、超时、异常、断开和 Session 切换。
- 目录浏览、路径 stat 和应用列表是短操作，不获取独占租约。

## Remote directory contract

`loadSharedStorageRoot(expectedSessionId)` 返回第一个可列出的共享存储候选或结构化失败。

- “可列出”以对候选执行 Sync LIST 成功为准，不要求 Sync v1 `lstat` 将 `/sdcard` 等链接报告为目录。
- Sync v1/v2 返回的 `.` 与 `..` 必须在 Core 协议边界过滤，不得进入条目模型、路径拼接、容量计数或 UI。

`listRemoteDirectory(directory, ancestorIdentities, expectedSessionId)` 返回完整 `RemoteDirectorySnapshot`：

- 不超过 10,000 项；超限不返回部分列表。
- 空目录、权限拒绝、路径消失、非目录和 Session 失效必须区分。
- 符号链接必须显示身份；只有可靠解析为可达文件/目录且不形成祖先循环时允许选择/进入。
- v1 元数据不可靠时字段为 `null` 或显式降级，不把 32 位溢出值表示为精确大小。
- 文件页的当前位置由已验证面包屑组成，显示必须紧凑连接为 `/目录名/子目录名`，同时保留祖先导航。

## Binary transfer contract

Core 方法只接收项目/Java 标准二进制源与目标，不接收 Android URI：

- `pullRemoteFile(remoteEntry, destinationStream, expectedSessionId, progress)`。
- `pushRemoteFile(sourceStream, sourceSize?, stagedRemotePath, expectedSessionId, progress)`。
- `commitRemoteUpload(stagedPath, finalPath, conflictPolicy, expectedSessionId)`。
- `cleanupRemoteStaging(stagedPath, expectedSessionId)`。

语义：

- Sync 子流最大 64 KiB 分块；累计字节为 `Long`。
- 30 秒无进展关闭子流并返回 `NO_PROGRESS_TIMEOUT`；没有固定总时长。
- 传输前后复核可靠的 size/mtime/identity 和实际字节数；元数据不足时以流式 SHA-256 前后复核，远端摘要命令只由 Core 构造。来源变化返回 `SOURCE_CHANGED`，摘要能力不足返回 `INTEGRITY_UNAVAILABLE`，二者均不得提交成功。摘要值只驻留内存且不得进入诊断或证据。
- 上传只写核心生成的同目录暂存名；最终 rename 和清理命令只能由 Core 的安全参数构造器生成。
- 远端摘要、rename 与清理命令必须在 Core 的 I/O dispatcher 执行；失败必须返回结构化提交/清理结果，不得向主线程抛出网络异常。
- Sync v1 仅允许对首次、零字节、可确认的远端子流关闭重新打开子流并重试一次；已有进度、本地流 I/O、Sync FAIL、取消和超时不得重试。
- 列表/元数据协议版本与 SEND/RECV 协议版本必须独立暴露；前者由 `ls_v2`/`stat_v2` 决定，后者仅由 `sendrecv_v2` 决定。传输重试不得读取列表版本。
- 主控端来源流、主控端目标流、远端权限/路径拒绝、Sync 子流关闭与 Session 关闭必须保留不同错误身份；不得把所有 `IOException` 归类为 Session 关闭。
- 取消首先关闭子流；3 秒仍未释放则关闭旧 Session，旧结果不得进入新 Session。
- 诊断事件只允许阶段、结果、技术代码、计数和脱敏目标。

## APK inventory contract

`listApkCandidates(expectedSessionId)` 返回最多 20,000 项的当前用户只读候选快照；它与历史第三方应用管理快照分离，不授予强停、启用、禁用或其他修改能力。

`resolveApplicationApks(packageName, expectedSessionId)`：

- 目标必须来自同 Session 的独立当前用户只读 APK 候选快照；候选可包含系统或第三方包，但必须由设备报告、`pm path` 可读且组成可可靠解析。该快照不得复用或改变历史应用管理的第三方修改策略。
- 解析 1..256 个唯一绝对 `.apk` 路径，第一项 base，其余 split；重复、缺失或不可读整体失败。
- 返回 `ApkInventory`，不得将包名或路径写入诊断。

提取方必须在全部拉取后再次解析 inventory 并复核组成与 stat；不一致返回 `APK_INCOMPLETE`，不得提交部分集合。

## Logcat stream contract

`streamLogcat(config, mode)` 返回 `Flow<LogcatStreamEvent>`：

- `RECENT_AND_FOLLOW`：探测 → 有界历史 → `HistoryReady` → `Following` → follow lines。
- `FOLLOW_ONLY`：以尾记录 fingerprint 建立边界后持续跟随。
- `HISTORY_ONLY`：有界历史后完成，不进入 follow。
- 默认限制为 300 秒、512 条、1 MiB、64 KiB 单行、2 秒历史/探测、3 秒初始化总预算。
- 历史和 follow 使用相同 buffer/level/格式；Core 解析 epoch/usec/printable 输出并提供既有 threadtime 展示文本。
- 交接只用最多 512 项的 exact-record fingerprint 多重集去重；不得按粗时间或纯文本丢弃。
- 不支持时先发 `CapabilityLimited` 并结束/释放租约；Feature 等用户明确选择后重新调用 fallback。
- follow 不设空闲读超时，但打开流、页面生命周期、用户停止、Session 与资源关闭均有界。

## Error mapping

Sync FAIL、errno、Shell 非零退出、provider 错误和协议异常必须映射为稳定的项目错误；异常消息、命令、输出、路径和内容不得直接传给 UI 或诊断。
