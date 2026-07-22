# Data Model: v0.03 设备文件与日志诊断

所有实体均为进程内值对象或用户授权目标的短期句柄；除用户明确写入的文件/APK 外，不新增持久化记录。

## RemotePathEntry

- `sessionId: String`：条目所属 Session。
- `absolutePath: String`：核心验证后的绝对路径，仅在内存业务对象中使用。
- `displayName: String`：目录列表显示名。
- `kind: FILE | DIRECTORY | SYMLINK | OTHER`。
- `sizeBytes: Long?`：仅在元数据可靠时提供。
- `modifiedAtEpochSeconds: Long?`。
- `mode: Int?`：Core 内部用于类型/能力判断，不进入诊断。
- `linkTargetStatus: NOT_LINK | ACCESSIBLE_FILE | ACCESSIBLE_DIRECTORY | DENIED | MISSING | LOOP | UNKNOWN`。
- `identity: (deviceId, inode)?`：用于祖先循环检测；缺失时必须通过可靠降级探测，否则拒绝跟随链接。

**Validation**: 绝对路径；无 NUL；UTF-8 ≤1,024 字节；条目名不得包含 `/`；Session 必须仍为当前；链接目标必须可达且不在祖先身份集合中。

## RemoteDirectorySnapshot

- `sessionId`、`directory: RemotePathEntry`、`entries: List<RemotePathEntry>`。
- `sourceCapabilities: STAT_V2 | LIST_V2 | SYNC_V1_DEGRADED`。
- `loadedAtMonotonic`：只用于过期/Session 判断。

**Validation**: 最多 10,000 项；超限整体失败，不返回不完整列表。空列表与权限拒绝是不同结果。

## ExclusiveAdbOperationLease

- `token`、`sessionId`、`kind: FILE_TRANSFER | APK_EXTRACTION | LOGCAT`。
- 状态：`ACQUIRED → RELEASED`，释放幂等。

**Rules**: 一个 Session 最多一个独占租约；浏览/stat/应用列表不占租约；Session 变化使旧租约失效并触发资源关闭；冲突不改变现有租约。

## FileTransferTask

- `taskId`、`sessionId`。
- `kind: UPLOAD | DOWNLOAD`。
- `source`、`destination`：仅保留安全显示名；真实路径/URI 位于短期句柄。
- `conflictPolicy: CANCEL | OVERWRITE | AUTO_RENAME`，默认 `CANCEL`。
- `transferredBytes: Long`、`totalBytes: Long?`。
- `stage`、`error?`、`cleanupTarget?`。

### State transitions

```text
IDLE
  → PREPARING
  → AWAITING_CONFLICT → PREPARING
  → TRANSFERRING
  → VERIFYING
  → COMMITTING
  → SUCCEEDED

PREPARING | AWAITING_CONFLICT | TRANSFERRING | VERIFYING | COMMITTING
  → CANCELLED | FAILED
  → CLEANING
  → CANCELLED | FAILED | CLEANUP_FAILED
```

**Rules**: 写入前确认冲突；目标先暂存；成功前重新确认 Session、源稳定性和目标；30 秒无字节进展超时；无固定总时长；终态保持到用户关闭或 Session 清理。

## SafStagedTarget

- `treeUri`、`temporaryUri`、`finalDisplayName`、`mimeType`。
- `capabilities: CREATE + WRITE + DELETE + RENAME`。
- `originalUri?`、`backupUri?`：覆盖事务的回滚句柄。
- 状态：`CREATED → WRITING → READY → COMMITTED | ROLLED_BACK | CLEANUP_FAILED`。

**Rules**: URI 不写诊断、不持久化；只删除当前任务创建的暂存/备份；provider 能力不足时在写入前失败。

## ApkCandidateSnapshot

- `sessionId`、`userId`。
- `candidates: List<ApkCandidate>`，最多 20,000 项。
- `ApkCandidate` 包含 `packageName`、`packageKind: SYSTEM | THIRD_PARTY | UNKNOWN` 和 `pathCapability: READABLE | UNREADABLE | UNKNOWN`。

**Rules**: 这是 APK 提取专用的只读当前用户快照，可包含系统和第三方包；不携带管理操作权限，不改变历史应用管理快照。只有设备报告、包名校验通过且 `pm path` 可可靠解析的候选允许开始提取。

## ApkInventory / ApkExtractionTask

### ApkInventory

- `sessionId`、`userId`、`packageName`（敏感、仅内存）。
- `components: List<ApkComponent>`，1..256 项。

### ApkComponent

- `role: BASE | SPLIT`、`remotePath`、`safeBasename`。
- `sizeBytes: Long?`、`modifiedAt`、`identity?`。

### ApkExtractionTask

- 继承文件任务的进度、冲突和清理语义。
- `expectedComponents`、`completedComponents`、`outputKind: SINGLE_APK | APK_DIRECTORY`。
- 状态使用文件任务状态机，但整个发现—传输—复核期间持有 `APK_EXTRACTION` 租约。

**Validation**: 第一项必须为 base；路径唯一、basename 唯一、绝对且以 `.apk` 结束；开始和结束的组成集合/stat 必须一致；任一失败整体不成功并清理暂存集合。

## LogcatCapture

- `sessionId`、`mode: RECENT_AND_FOLLOW | FOLLOW_ONLY | HISTORY_ONLY`。
- `minimumLevel`、`buffers`：沿用固定枚举且非空。
- `deviceCursorEpochNanos?`。
- `historyWindowSeconds = 300`、`historyLineLimit = 512`、`historyByteLimit = 1 MiB`、`decodedLineLimit = 64 KiB`。
- `historyTruncated`、`degradedReason?`。
- `handoffFingerprintMultiset`：最多 512 项，只在交接窗口内存活。

### Stream events

- `Line(entry, origin: HISTORY | FOLLOW)`。
- `HistoryReady(lineCount, truncated, empty)`。
- `Following`。
- `CapabilityLimited(reason, availableFallbacks)`。
- `Failed(error)`、`Cancelled`。

### State transitions

```text
IDLE → PROBING → HISTORY → FOLLOWING → STOPPED
                  └──────→ COMPLETED        # HISTORY_ONLY
PROBING → CAPABILITY_LIMITED → IDLE          # 等待用户选择后重新获取租约
任一活动状态 → FAILED | CANCELLED | SESSION_INVALID
```

**Rules**: 历史/探测 2 秒、初始化总预算 3 秒；持续 follow 无空闲超时但受页面/Session/用户生命周期约束；能力不足不自动降级；原始内容和 fingerprint 不进入诊断事件。

## Structured Errors

统一区分：`PERMISSION_DENIED`、`PATH_NOT_FOUND`、`PATH_TOO_LONG`、`UNSUPPORTED_TYPE`、`SYMLINK_DENIED/MISSING/LOOP`、`DIRECTORY_CAPACITY_EXCEEDED`、`SPACE_INSUFFICIENT`、`CONFLICT`、`OPERATION_CONFLICT`、`PROVIDER_UNSUPPORTED`、`SOURCE_CHANGED`、`INTEGRITY_UNAVAILABLE`、`APK_INCOMPLETE`、`NO_PROGRESS_TIMEOUT`、`STARTUP_TIMEOUT`、`SESSION_INVALID`、`STREAM_CLOSED`、`CANCELLED`、`CLEANUP_FAILED`、`LOGCAT_CAPABILITY_LIMITED`。

错误只包含用户消息、下一步与稳定技术代码；不得携带真实路径、包名、URI、命令输出或 Logcat。
