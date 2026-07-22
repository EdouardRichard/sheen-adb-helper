# Data Model: v0.04 无线发现与设备分析增强

所有对象均为项目自有不可变 Kotlin 值对象。未特别注明的运行态对象只存在于当前进程；包含 Session 或 generation 的结果只有与当前值完全相同时才可交付 UI。

## 1. WirelessDiscoverySession

| 字段 | 类型 | 规则 |
|---|---|---|
| `generation` | `Long` | 每次开始/停止递增；迟到回调不匹配即丢弃 |
| `mode` | `LAN_FOREGROUND / LOCAL_PAIRING` | 普通局域网只可前台；本机配对可进入 2 分钟短服务 |
| `networkKey` | opaque value | 仅用于本次去重和网络变化检测，不日志/持久化 |
| `startedAt` / `deadline` | monotonic time | LAN 默认 10 秒；local 最大 2 分钟 |
| `phase` | state enum | `IDLE / STARTING / DISCOVERING / STOPPING / STOPPED / FAILED` |
| `services` | list | 只含当前 generation 的服务观察 |

转换：`IDLE → STARTING → DISCOVERING → STOPPING → STOPPED`；任意活动态可因权限/平台/网络错误进入 `FAILED`，并执行与 `STOPPED` 相同的资源清理。停止后不可恢复，只能新建 generation。

## 2. WirelessServiceObservation

| 字段 | 类型 | 规则 |
|---|---|---|
| `observationId` | opaque | 基于当前 network、service type、service name 的进程内键 |
| `serviceType` | `PAIRING / CONNECT` | 仅映射 `_adb-tls-pairing._tcp`、`_adb-tls-connect._tcp` |
| `serviceName` | sensitive string | 不日志、不持久化；QR 流必须与预期 instance 精确匹配 |
| `addresses` | list of address values | 同时支持 IPv4/IPv6，不用字符串拼 endpoint |
| `port` | `Int` | 1..65535；解析前不可连接 |
| `status` | enum | `DISCOVERED / RESOLVING / RESOLVED / LOST / UNREACHABLE / FAILED` |
| `verifiedDeviceId` | nullable opaque | 仅来自成功 TLS/Session 验证；名称/IP/端口不能生成 |
| `lastSeenAt` | monotonic time | 用于移除过期条目；不持久化 |

同一 observation 键更新而非重复追加。PAIRING 与 CONNECT 只有 `verifiedDeviceId` 相同才合并成一个展示设备，否则保持两个标记清楚的条目。

## 3. PairingAttempt

| 字段 | 类型 | 规则 |
|---|---|---|
| `attemptId` | random opaque token | 不可复用；用于拒绝旧通知/回调 |
| `method` | `QR / SIX_DIGIT_CODE` | 本机默认 `SIX_DIGIT_CODE` |
| `target` | optional service observation | 端点只在核心层解析后使用 |
| `secret` | `CharArray`/ephemeral bytes | QR 密码或六位码；不使用普通持久 String 跨层传播 |
| `expiresAt` | monotonic time | 到期后提交无效 |
| `phase` | enum | 见下方 |
| `error` | structured safe error | 不包含 secret、真实端点、service name 或原始异常正文 |

状态：`PREPARING → WAITING_FOR_TARGET/WAITING_FOR_CODE → PAIRING → SUCCEEDED`。可从所有非终态进入 `CANCELLED / EXPIRED / FAILED / UNSUPPORTED`。进入任一终态必须清零 secret、释放二维码 bitmap 和撤销相关通知；终态不可转回活动态。

## 4. LocalPairingWindow

| 字段 | 类型 | 规则 |
|---|---|---|
| `windowId` | random opaque token | 与 PendingIntent token 一致；终止后失效 |
| `attemptId` | value | 绑定唯一 PairingAttempt |
| `deadline` | monotonic time | 不晚于启动后 2 分钟 |
| `notificationState` | enum | `HIDDEN / PRIVATE_LOCKED / INPUT_READY / INPUT_UNAVAILABLE / RESULT` |
| `deviceUnlocked` | Boolean | 只用于决定是否发布输入 action；提交时重新读取系统状态 |
| `pairingService` | nullable observation | 变化/消失即拒绝旧提交并停止窗口 |

`PRIVATE_LOCKED` 不含 RemoteInput action。只有 `INPUT_READY` 可生成一次性输入 PendingIntent。权限拒绝、通知关闭或 OEM 不兼容转 `INPUT_UNAVAILABLE`，但应用内输入继续使用同一 PairingAttempt。

## 5. ApplicationPresentation

| 字段 | 类型 | 规则 |
|---|---|---|
| `packageName` | String | 必有、保持正确；敏感上下文，不日志/持久化 |
| `userId` | Int | 沿用当前用户范围 |
| `displayName` | nullable String | 按控制端 locale 解析；不可可靠获取则 null |
| `icon` | nullable `ApplicationIconPayload` | 进程内有界字节，不使用第三方/Compose 类型作为核心契约 |
| `metadataStatus` | enum | `PENDING / AVAILABLE / UNAVAILABLE / TOO_LARGE / PARSE_FAILED / SESSION_CHANGED` |
| `enabledState`、版本/安装器 | existing fields | 保留既有语义和降级 |

`ApplicationIconPayload` 包含编码格式、宽高、字节和 fallback 类型；编码字节最大 1 MiB，全局 LRU 最大 16 MiB。包名先交付，元数据按包逐步替换；Session 变化清空全部条目和图标缓存。

搜索规范：trim 后进行 locale-stable case-insensitive 包含匹配，条件为 `packageName contains query OR displayName contains query`。`displayName == null` 时包名搜索仍完整可用；元数据到达后重新计算。同名条目必须同时展示包名。

## 6. ProcessAnalysisEntry

| 字段 | 类型 | 规则 |
|---|---|---|
| `snapshotGeneration` | Long | 防止与旧应用/日志快照混合 |
| `pid` / `name` / `uid` / `state` / memory | existing fields | 原始字段缺失保持 null |
| `applicationAssociation` | association value | `VERIFIED(package) / MULTIPLE(packages) / UNKNOWN(reason)` |
| `capability` | enum set | 标出设备未提供的字段或解析降级 |

关联只在 Session、快照代次、UID/进程身份字段满足契约时为 `VERIFIED`。PID 复用、进程退出、共享 UID、多进程或字段缺失不能强行唯一归属。

## 7. StructuredLogcatRecord

| 字段 | 类型 | 规则 |
|---|---|---|
| `sequence` | Long | 当前流单调递增，用于稳定顺序 |
| `timestamp` | nullable parsed time | 解析失败为 null |
| `pid` / `tid` | nullable Int | 缺失/非法为 null |
| `level` | nullable LogcatLevel | 未知级别为 null |
| `tag` | nullable String | 保留受内存边界保护 |
| `message` | String | 敏感日志内容，仅内存 |
| `rawText` | String | 兼容复制/导出；计入现有字节上限 |
| `parseStatus` | `PARSED / UNPARSED / STDERR` | 不把 stderr 或未知格式伪装为正常记录 |
| `association` | association value | 仅根据同 Session 可靠进程快照推导 |

`DiagnosticFilter` 可包含 minimum/exact levels、tag query、keyword、PID set、process identity、application package。所有非空条件必须同时满足。过滤只改变可见视图，不改变有界原始缓冲；暂停、清除、导出继续沿用既有语义。

## 8. Structured Errors

新增错误至少覆盖：`DiscoveryUnsupported`、`DiscoveryNetworkUnavailable`、`DiscoveryPermissionUnavailable`、`DiscoveryTimeout`、`ServiceResolutionFailed`、`PairingExpired`、`PairingSecretInvalid`、`PairingTargetChanged`、`NotificationInputUnavailable`、`DeviceLocked`、`ApplicationMetadataUnavailable`、`ApplicationMetadataTooLarge`、`ApplicationMetadataParseFailed`、`LogcatParseDegraded`。

错误面向 UI 只携带安全分类和可本地化建议；不得携带真实 IP、service name、二维码/配对码、包名上下文、Shell 输出或 Logcat 内容。
