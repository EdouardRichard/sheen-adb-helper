# Phase 0 Research: v0.03 设备文件与日志诊断

## Decision 1: 使用 Kadb 原生 ADB Sync

**Decision**: 扩展 `:core:adb` 适配器，使用 Kadb 2.1.1 的 `openSync()`、`list/stat/lstat`、`send(Source)` 和 `recv(Sink)`；Feature 不接触 Kadb、Okio 或 Shell 命令。对仅提供 Sync v1 的旧版 adbd，在 Core 协议边界过滤 LIST 返回的 `.`/`..`，避免这些协议目录项进入安全子路径构造并被误映射为协议不兼容。

**Rationale**: Sync v2 提供 64 位 stat/list，v1/v2 都以最大 64 KiB DATA 帧流动，累计使用 `Long` 即可支持至少 2 GiB。当前固定依赖已经包含该能力，不需要新增协议或依赖。[AOSP Sync protocol](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/file_sync_protocol.h)；[Kadb 2.1.1 Sync source](https://github.com/flyfishxu/Kadb/blob/2.1.1/kadb/src/commonMain/kotlin/com/flyfishxu/kadb/stream/AdbSyncStream.kt)

**Alternatives considered**: Shell `cat/dd` 缺少可靠双向 stdin、Sync 失败帧和元数据；自行复制 AOSP Sync 扩大协议维护面；Kadb `File` 重载无法直接适配 SAF，故使用流重载。

## Decision 2: 远端路径、目录与符号链接

**Decision**: 默认依次探测 `/sdcard`、`/storage/self/primary`、`/storage/emulated/<current-user>`，以候选 LIST 成功作为“可列出”判据，避免 Sync v1 只能 `lstat` 时把旧设备常见的 `/sdcard` 链接误判为不可浏览；文件页同时提供明确的“设备根目录”入口与面包屑导航，使用户无需输入原始路径即可进入其他可访问位置。当前位置使用无按钮内边距空隙的连续 `/目录名/子目录名` 片段显示。路径必须绝对、无 NUL、UTF-8 不超过 Sync 的 1,024 字节；子路径只能由已验证条目名构造。目录上限 10,000 项，超限返回完整错误而非部分列表。v2 使用 `lstatV2/statV2` 的 mode 与 `(dev, ino)` 识别链接及祖先循环；无 v2 时只在 `:core:adb` 使用受控 `readlink/stat` 探测，结果不可可靠解析即拒绝。

**Rationale**: Sync 元数据不受区域设置、空格或换行文件名影响；设备/ inode 身份能识别不同文本路径指向的同一目录。Kadb 会先物化列表，因此短超时、10,000 项检查和取消关闭子流均为必要防护。

**Alternatives considered**: `ls` 文本解析不可靠；只比较字符串无法识别链接循环；隐藏全部链接违反规格。

## Decision 3: 大文件、进度、超时与取消

**Decision**: 大小、进度和累计字节全部使用 `Long`，固定 64 KiB 分块，不整文件入内存。传输没有固定总时长，使用 30 秒无字节进展看门狗；list/stat/准备/提交/清理使用有限超时。取消、后台、超时、断开或 Session 切换先关闭 Sync 子流，再在不可取消 IO 清理暂存项；子流 3 秒内无法释放时关闭旧 Session 兜底。成功前比较源传输前后可靠的 size/mtime/identity 与实际字节数；元数据不足时使用流式 SHA-256 前后复核（远端仅通过 Core 的受控 `sha256sum` 能力），无法复核则返回 `INTEGRITY_UNAVAILABLE` 而不宣称成功。摘要值只在内存比较，不记录或导出。

**Rationale**: 固定总超时会形成隐含文件大小上限；仅中断线程不能保证 Socket/Okio 阻塞及时退出。Session mutex 只用于快照和状态更新，不跨整个传输持有，文件浏览才能继续。稳定元数据优先避免重复读取，摘要仅作为无法可靠判断来源变化时的兼容性回退，以满足“成功结果逐字节一致”和源变化不得成功。

**Alternatives considered**: 固定 30 分钟总超时、不关闭子流等待自然返回、取消即总是断开连接均不满足规格或资源最小化。

### Sync v1 旧设备早退与错误归因补充

**Decision**: 远端摘要、提交和清理使用 `:core:adb` 注入的 I/O dispatcher，禁止同步 Shell 写入从调用方主线程直达 Socket。Sync v1 在首次 SEND/RECV 尚未消费或写入任何文件字节、且异常可确认是远端子流关闭时，重新打开 Sync 子流并最多重试一次；已有进度、Sync FAIL、无进展超时、取消以及主控端 InputStream/OutputStream 失败均不重试。协议适配器分别包装主控端来源和目标流异常，Session 层再将远端权限/路径拒绝、真实断流与本地 I/O 映射为不同结构化错误。

**Rationale**: 真机日志证明上传的远端 `mv/rm` 曾在主线程执行，其中清理异常逃逸导致应用退出；下载日志中的通用 `IOException` 又被统一映射为会话关闭，掩盖了本地 SAF 与 Sync FAIL。零进度、单次重试不会重复部分写入，可兼容旧 adbd 在首个 Sync 子流上的早退，同时保持取消和非幂等边界可预测。

**Alternatives considered**: 仅捕获主线程异常无法恢复上传；对全部 `IOException` 重试会重复部分写入并掩盖权限/本地存储失败；改用 Shell `cat/dd` 会绕开既有 Sync 安全与取消契约，因此均不采用。

**Capability correction**: Kadb 2.1.1 的列表/元数据分别由 `ls_v2`、`stat_v2` 决定，SEND/RECV 则独立由 `sendrecv_v2` 决定。Core 必须保留独立的列表版本与传输版本；旧设备即使支持 v2 列表/元数据，只要没有 `sendrecv_v2`，零字节安全重试仍按 Sync v1 生效。以列表版本推断传输版本会使混合能力设备错误跳过兼容路径。

## Decision 4: 暂存、冲突和安全提交

**Decision**: 任何写入前先完成冲突选择，默认取消。远端上传写同目录随机 `.sheen-<token>.part`，完成与 stat 复核后再 rename；覆盖前再次确认目标，自动重命名采用有界 `name (n).ext` 查找。SAF 输出同样使用任务暂存文档/目录；覆盖使用“旧目标改备份名 → 暂存改最终名 → 删除备份”，失败尽力回滚并报告 `CleanupFailed`。provider 缺少 create/write/delete/rename 能力时不提供不安全覆盖。

**Rationale**: 直接 Sync SEND 或以 `wt` 打开最终目标会在失败时截断已有文件；平台不保证任意 DocumentsProvider 具备原子替换。[AOSP SYNC.TXT](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/android13-mainline-tethering-release/SYNC.TXT)

**Alternatives considered**: 直接写最终路径、假设 provider 原子替换、把 2 GiB 文件先缓存到应用私有目录均被拒绝。

## Decision 5: 主控端使用 SAF 目录树

**Decision**: 上传源用 `OpenDocument`；普通下载和 APK 提取用 `OpenDocumentTree`。`:core:data` 对上传来源仅使用通用 `OpenableColumns`、`ContentResolver.getType()` 与可重开输入流，不假设来源 Provider 暴露 DocumentsContract flags/mtime；对输出树使用 `DocumentsContract`，并严格区分树根 URI 与 `tree/.../document/...` 子文档 URI，所有能力、写入、重命名和删除都作用于真实子文档。网关提供流、暂存、提交与回滚，不持久化 URI grant，不新增权限或 DocumentFile 依赖。

**Rationale**: `ACTION_CREATE_DOCUMENT` 不能覆盖而会自动改名，无法实现规格要求的覆盖/自动重命名/取消；目录树授权可在用户选择的最小范围内处理冲突和多 APK 集合。[Android Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)；[DocumentsContract](https://developer.android.com/reference/android/provider/DocumentsContract)

**Alternatives considered**: `CreateDocument`、放宽安全提交能力、把树内子文档重新解释为树根、所有文件访问/媒体权限、直接路径和持久化授权均不采用。Android 11+ 不允许选择内部存储根、可靠 SD 卡根或 Downloads 根，UI 与验收必须说明选择子目录，不能以扩大权限绕过。

## Decision 6: 完整 APK 提取

**Decision**: `:core:adb` 提供独立的当前用户只读 APK 候选快照，包含设备报告的全部已安装包，但只允许选择 `pm path` 可读取且能可靠解析完整组成的目标；不得复用或放宽历史 `listApplications()` 的第三方管理策略。核心在同 Session 复核目标后执行受控 `pm path --user <id> <package>`（必要时同语义的 `cmd package path`），严格解析基础 APK 和全部 split，组成上限 256。开始前/结束后复核路径集合与 stat；变化、缺失、重复或任一拉取失败均删除暂存集合并返回不完整。单 APK 输出 `{package}.apk`，多 APK 输出 `{package}-apks/` 并保留安全 basename。

**Rationale**: 规格没有把提取范围限制为第三方应用；独立只读快照可覆盖设备允许读取的系统与第三方包，而不会改变现有应用管理的修改边界。AOSP `pm path` 依次输出 `sourceDir` 与全部 `splitSourceDirs`，比扫描 `/data/app` 或只解析 `pm list packages -f` 更完整且保持当前用户/包边界。[AOSP PackageManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/pm/PackageManagerShellCommand.java)

**Alternatives considered**: 只提取 base、扫描 `/data/app`、打包 zip/apks 均与规格或最小范围不符。

## Decision 7: 跨页面任务与模块所有权

**Decision**: 新建 `:feature:files`，由 Activity 级 `FilesViewModel` 聚合浏览、传输、APK 选择、冲突和任务状态；历史 `:feature:apps` 不改语义。应用内切换页面不取消；`MainActivity.onStop()` 且非配置变更时取消。根 Scaffold 展示不含真实路径/包名/URI 的任务摘要。SAF picker 返回前不获取租约或启动 I/O。

**Rationale**: Activity ViewModel 可跨页面和配置变更保留任务；单 Activity 无需新增 lifecycle-process、Service 或 WorkManager。`:app` 只创建实例、转发生命周期并装配 Feature UI。

**Alternatives considered**: 页面级 ViewModel 会在导航时中断；Application/GlobalScope 所有权不清；后台服务扩大范围；修改 Apps Feature 会引入重复任务状态或 Feature 依赖。

## Decision 8: Session 级长任务互斥

**Decision**: `:core:adb` 维护原子、Session-scoped、token 化且幂等释放的独占租约：`FILE_TRANSFER`、`APK_EXTRACTION`、`LOGCAT`。文件浏览/stat/应用列表不占租约。冲突返回 `OPERATION_CONFLICT`，不停止现有任务；APK 整个“发现—拉取—复核”持有同一租约。

**Rationale**: 互斥必须与 Session 快照原子关联，Feature 布尔值或 app 调度存在竞态。能力提示 flow 结束并释放租约，用户选择降级时重新获取。

**Alternatives considered**: Feature 间协调、全 Session 大锁、自动取消旧任务均被拒绝。

## Decision 9: Logcat 两阶段默认模式

**Decision**: Core 先从被控端取得严格数字 epoch 游标，再执行有界历史 dump，随后从同一游标 follow。命令使用相同 buffer/level 和 `epoch + usec + printable` 输出；Core 解析后向 Feature 提供结构化事件并格式化为既有 threadtime 展示。默认边界：5 分钟、设备端最近 512 条、Core 历史 1 MiB、单行 64 KiB、历史/探测 2 秒、初始化总预算 3 秒；持续 follow 无空闲读超时，既有 Feature 10,000 行/4 MiB 与可见 100 条不变。

**Rationale**: 两阶段能明确发出历史完成或空历史；单个持续 `-T` 流在空缓冲时无法区分历史尚未到达与确实为空。设备游标避免主控/被控墙钟偏差。[Android Logcat](https://developer.android.com/tools/logcat)；[AOSP logcat implementation](https://android.googlesource.com/platform/system/logging/+/refs/heads/main/logcat/logcat.cpp)

**Alternatives considered**: 单个 `-T`、以最后一条历史作游标、注入标记、继续无界追赶均被拒绝。

## Decision 10: Logcat 去重、探测与降级

**Decision**: dump 期间游标后的记录进入最多 512 项的 exact-record fingerprint 多重集；follow 只按计数抑制已确认重放，窗口结束即清空，不按粗时间或纯文本去重。运行时行为探测验证设备游标、`-t`、`-T`、格式与所有选择 buffer。能力不足时发 `CapabilityLimited` 并结束，等待用户选择：`FOLLOW_ONLY` 以 `-d -t 1` 尾记录 fingerprint + `-T 1` 跟随；`HISTORY_ONLY` 使用 512 条/1 MiB，有设备时钟则过滤 5 分钟，否则明确仅按容量有界。

**Rationale**: 时间 tail 包含边界且日志可能轻微乱序，多重集可防重复又不误删合法的相同消息。行为探测比解析 ROM 本地化帮助文本可靠。

**Alternatives considered**: 游标加一微秒、上一行文本去重、固定时间窗去重、静默自动降级和普通无参数 logcat 均被拒绝。

## Decision 11: 权限、依赖、隐私与验证

**Decision**: Manifest 继续只有 `INTERNET`；不新增依赖、持久化、通知、服务或后台组件。诊断只记录阶段、结果、技术代码、计数和脱敏目标，不记录真实路径、包名、URI、文件内容、命令输出或 Logcat。自动化、构建、Manifest、provider、真机与发布合规分别报告；ADR 0004 的许可证阻断继续保留。

**Rationale**: 这直接满足宪法和权限矩阵，并避免把功能完成错误等同于发布合规完成。

**Alternatives considered**: 为便利新增存储/通知权限、原始调试日志或用新库替代现有平台能力均无必要。
