# Data Model: v0.05 设备操作体验与性能收敛

所有运行时实体默认仅驻留内存，并以 `sessionId`、请求代次或扫描窗口 ID 防止旧结果污染新状态。文档中的“未知”是显式值，不以 `0`、空字符串或猜测值替代。

## 1. LocalPairingWindow

| Field | Type | Rules |
|-------|------|-------|
| `windowId` | opaque ID | 每次新窗口唯一；重复点击复用活动窗口 |
| `startedAtMonotonic` | duration origin | 仅用于进程内截止计算 |
| `deadlineMonotonic` | duration | `started + 2 minutes`，不可延长 |
| `state` | `STARTING/SCANNING/PORT_AVAILABLE/SUBMITTING/FINISHED` | 单向状态转换 |
| `resolvedEndpoint` | optional sensitive value | 仅内存；提交前重新确认；不得进入通知公开文本/日志 |
| `inputNonce` | optional opaque token | 绑定当前窗口和当前端口，端口变化即失效 |
| `finishReason` | optional enum | `STOPPED/SUCCEEDED/TIMED_OUT/PORT_EXPIRED/CANCELLED/RESTARTED/ERROR` |

**Transitions**:

```text
STARTING → SCANNING → PORT_AVAILABLE → SUBMITTING → FINISHED
                    ↘ SCANNING
任一活动状态 ──停止/超时/错误──→ FINISHED
```

**Validation**:

- 同时最多一个活动窗口。
- 配对码为六位数字，只在提交瞬间存在于受保护输入对象中；完成、停止、失效或超时后清零。
- `FINISHED` 后任何通知/应用内提交均返回过期，不恢复扫描。

## 2. PairingNotificationState

| Field | Type | Rules |
|-------|------|-------|
| `windowId` | opaque ID | 必须指向活动窗口 |
| `phase` | `PROMPT_ENABLE_DEBUGGING/PAIRING_CODE_READY/SUBMITTING/RESULT` | 决定最简通知控件 |
| `publicText` | fixed resource | 初态“请开启无线调试。”；发现后“已检测到配对端口，请输入配对码：” |
| `stopEnabled` | Boolean | 活动状态始终为 true |
| `remoteInputEnabled` | Boolean | 仅已解锁、通知能力允许且端口仍有效 |

不得含真实端点、设备身份、配对码或密钥。通知动作携带 `windowId` 与一次性 nonce，防止陈旧 PendingIntent 生效。

## 3. WirelessDeviceDisplayEntry

| Field | Type | Rules |
|-------|------|-------|
| `serviceIdentity` | existing stable identity | 去重仍以既有可靠身份/端点规则为准 |
| `displayName` | optional sanitized text | 仅 `displayNameSource` 为可靠来源、身份关联通过且满足固定安全校验时存在 |
| `displayNameSource` | `VERIFIED_AFTER_CONNECTION/UNAVAILABLE` | 原始 ADB mDNS/NSD 观察固定为 `UNAVAILABLE` |
| `nameIdentityReference` | optional verified identity | 名称存在时必须证明与 `serviceIdentity` 指向同一设备；关联失效即清空名称 |
| `protocol` | `IPv4/IPv6` | 从已解析地址类型产生 |
| `address` | normalized address | IPv6 保持完整，不与端口拼接后再解析 |
| `port` | integer | 1..65535 |
| `expiresAt` | existing expiry | 展示调整不改变有效期 |

**Rendering**:

- 有名称：第一行 `displayName`，第二行 `protocol · address · port`。
- 无名称：唯一一行 `protocol · address · port`。
- 名称只用于展示，不参与唯一身份合并。
- 名称先去除首尾 Unicode 空白；结果必须非空且不超过 80 个 Unicode 码点，并且不含 ISO 控制字符或双向控制字符 U+061C、U+200E–U+200F、U+202A–U+202E、U+2066–U+2069。
- 任一名称安全条件失败时清空 `displayName`、设为 `UNAVAILABLE` 并显示完整端点；不得截断后展示。
- service instance、GUID/序列样式字符串、hostname、反向解析结果和地址不能填入 `displayName`。

## 4. ApplicationListEntry

| Field | Type | Rules |
|-------|------|-------|
| `packageName` | validated package ID | 必填且是操作身份 |
| `displayName` | resolved/unknown | 标签成功则文本，否则“无法解析应用名” |
| `userId` / `uid` | existing identity fields | 可供安全操作和进程关联使用，不必在列表展示 |

v0.05 对外模型不得包含 icon bytes、icon key、占位 icon 或 icon cache 状态。版本、安装器等既有内部身份数据可留作操作校验，但列表只展示应用名和包名。

## 5. ProcessSnapshot

| Field | Type | Rules |
|-------|------|-------|
| `sessionId` | session ID | 必须等于当前活跃 Session |
| `requestGeneration` | monotonic integer | 新刷新使旧刷新失效 |
| `sampleStartedAt` / `sampleEndedAt` | monotonic time | 默认间隔 500 ms |
| `entries` | list of `ProcessSnapshotEntry` | 可为空 |
| `status` | `LOADING/CONTENT/EMPTY/ERROR/CANCELLED/DISCONNECTED` | UI 主状态 |

### ProcessSnapshotEntry

| Field | Type | Rules |
|-------|------|-------|
| `identity` | `ProcessIdentity` | 执行终止时必须复核 |
| `applicationName` | resolved/unknown | 无可靠唯一关联时固定展示“无法解析应用名” |
| `associatedPackage` | optional package identity | 仅可靠关联时存在 |
| `processName` | text | 来自被控端进程清单，不做相似猜测 |
| `cpuPercent` | `AVAILABLE(value)/CALCULATING/UNKNOWN` | 0..100，一位小数 |
| `pssMiB` | `AVAILABLE(value)/UNKNOWN` | 非负，一位小数，不以 RSS 替代 |
| `parentPid` | positive integer/unknown | 0 可表示内核父级语义 |
| `pid` | positive integer | 与 identity 一致 |

### ProcessIdentity

`sessionId + pid + startTimeTicks + uid + processName + observedGeneration`

任一安全字段缺失或执行前变化都会使终止请求失效。

## 6. ProcessTerminationRequest

| Field | Type | Rules |
|-------|------|-------|
| `requestId` | opaque ID | 单次授权 |
| `sessionId` | session ID | 不可跨 Session |
| `scope` | `SINGLE_PROCESS/WHOLE_APPLICATION_FORCE_STOP` | 用户每次显式选择；后者不是逐 PID 终止 |
| `targetProcess` | `ProcessIdentity` | 两种范围均保留发起条目 |
| `targetPackage` | optional package identity | 整个应用时必填且唯一可靠 |
| `confirmedProcessSet` | set of identities | force-stop 前用于身份/结果验证的快照，不限定 force-stop 只影响该集合 |
| `riskAcknowledged` | Boolean | 仅点击“我了解”后为 true |
| `forceStopImpactAcknowledged` | Boolean | 整个应用时必须为 true，表示已提示进程/服务/任务停止及后台恢复影响 |
| `createdAt` | monotonic time | 确认离开或目标刷新后失效 |

**State machine**:

```text
DRAFT → SCOPE_SELECTED → AWAITING_ACKNOWLEDGEMENT
      → VALIDATING → EXECUTING → VERIFYING → COMPLETED
任一执行前状态 ──取消/返回──→ CANCELLED（零命令）
```

## 7. ProcessTerminationResult

| Field | Type | Meaning |
|-------|------|---------|
| `outcome` | enum | `TERMINATED/PARTIAL/ALREADY_EXITED/POLICY_REJECTED/UNSUPPORTED/IDENTITY_CHANGED/UNKNOWN/CANCELLED/TIMED_OUT/DISCONNECTED` |
| `scope` | enum | 与请求一致 |
| `verifiedTerminated` | set of identity summaries | 只列执行后确认消失的目标 |
| `remaining` | set of identity summaries | 仍存活或新集合 |
| `messageCode` | safe project code | 不含原始命令/输出/真实端点 |

“命令已发送”不是 `TERMINATED`；必须有执行后快照证据。整个应用的 `TERMINATED` 表示 force-stop 后验证时没有同包活动进程，不承诺只影响确认集合，也不声称应用以后不会被用户显式重新启动。

## 8. LogcatCapture

| Field | Type | Rules |
|-------|------|-------|
| `captureId` | opaque ID | 单次捕获唯一 |
| `sessionId` | session ID | 捕获和保存任务都校验 |
| `mode` | `SNAPSHOT/CONTINUOUS` | 互斥 |
| `state` | `READY/CAPTURING/STOPPING/COMPLETE/FAILED` | 同时只允许一个活动捕获 |
| `startedAt` | monotonic + UTC label time | 单调时间控边界，UTC 只用于文件名 |
| `elapsed` | duration | 持续模式展示 |
| `byteCount` | long | 0..10 MiB |
| `payload` | bounded UTF-8 bytes | 默认仅内存，不自动持久化 |
| `stopReason` | optional enum | `SNAPSHOT_COMPLETE/USER_STOPPED/TIME_LIMIT/SIZE_LIMIT/EMPTY/CANCELLED/TIMED_OUT/DISCONNECTED/UNSUPPORTED/ERROR` |

快照和持续模式均不得超过 10 MiB；持续模式不得超过 10 分钟。完成后不自动开始下一段。

## 9. LogcatOutputRequest

### SavedLogRequest

| Field | Type | Rules |
|-------|------|-------|
| `captureId` | ID | 必须引用完成且仍有效的捕获 |
| `treeUri` | SAF tree URI | 用户本次明确选择 |
| `displayName` | generated name | 含模式与 UTC 时间；冲突自动改名 |
| `sensitiveContentAcknowledged` | Boolean | 离开应用前必须确认 |

### SharedLogLease

| Field | Type | Rules |
|-------|------|-------|
| `captureId` | ID | 只对应本次文件 |
| `cachePathToken` | opaque token | 不向 Feature 暴露绝对路径 |
| `contentUri` | FileProvider URI | 只读、临时授权 |
| `createdAt` / `expiresAt` | time | 最长 1 小时 |
| `state` | `PREPARED/CHOOSER_OPENED/TARGET_SELECTED/CANCELLED/OUTCOME_UNKNOWN/EXPIRED/CLEANED` | 仅按平台可观察信号转换；清理幂等 |

**Share state rules**:

- 创建文件后为 `PREPARED`；发出 chooser 后为 `CHOOSER_OPENED`，此状态不是分享成功。
- 只有平台回调明确报告目标选择时才进入 `TARGET_SELECTED`，且不推断接收方已读取。
- 平台明确报告取消时进入 `CANCELLED` 并立即清理。
- 平台无法观察目标选择或取消时进入 `OUTCOME_UNKNOWN`；UI 使用中性文案，文件按最长 1 小时租约清理。
- `TARGET_SELECTED`/`OUTCOME_UNKNOWN` 到期后进入 `EXPIRED`，随后幂等进入 `CLEANED`。

## 10. CurrentOperationFeedback

每个当前流程保留自己的 `loading/success/failure/cancelled/timeout/disconnected/unsupported/unknown` 状态。不存在全局 `AdbDiagnosticEvent`、历史列表、清除动作、缓存容量或跨操作查询关系。
