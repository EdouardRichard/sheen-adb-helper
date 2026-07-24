# Contract: Logcat Extraction, Save, and Share

## 1. Capture API

```text
startLogcatCapture(mode: SNAPSHOT | CONTINUOUS)
  → Flow<LogcatCaptureEvent>

stopLogcatCapture(captureId)
```

Events:

```text
Started(captureId, sessionId, mode)
Progress(elapsed, byteCount)
Chunk(projectOwnedUtf8Bytes)
Completed(stopReason, elapsed, byteCount)
Failed(structuredError)
```

Rules:

- 同时只有一个捕获，必须获取 `AdbExclusiveOperationKind.LOGCAT`。
- 所有事件携带隐式或显式 `captureId/sessionId`；Session 切换后旧事件被丢弃。
- `Chunk` 不暴露第三方 ADB 类型、原始命令或 Socket。
- 命令流完成、停止、取消、超时和异常都必须释放 exclusive lease 和 command substream，不关闭仍有效的主 Session。

## 2. Mode boundaries

### Snapshot

- 读取捕获开始时当前可读日志缓冲并自动结束。
- 最大 10 MiB；达到边界返回 `SIZE_LIMIT`，结果仍可保存/分享并明确被截断。
- 空结果返回 `EMPTY`，不伪造日志内容。

### Continuous

- 建立设备端起始游标，输出时过滤任何早于游标的记录。
- ROM 无法保证起始边界时返回 `UNSUPPORTED`，不得混入开始前日志。
- 用户停止、10 分钟或 10 MiB 任一先到即关闭。
- 自动停止原因分别为 `USER_STOPPED/TIME_LIMIT/SIZE_LIMIT`，不自动分片或继续。

## 3. In-memory result

- 捕获完成后在当前进程中保留最多 10 MiB UTF-8 字节。
- 新捕获开始、用户清除或 Session 失效时释放旧结果，除非已有独立 SAF 保存文件。
- 不进行级别筛选、应用/进程关联、异常识别、趋势分析或专用事件记录。

## 4. Save-to-directory

```text
select directory via OpenDocumentTree
→ acknowledge sensitive content
→ SafDocumentStore.prepareTarget(treeUri, generatedName, "text/plain")
→ write bounded payload
→ commit(AUTO_RENAME)
```

- 文件名：`sheen-logcat-{snapshot|continuous}-{yyyyMMdd-HHmmssZ}.txt`。
- 不默认覆盖同名文件；provider 不支持创建/重命名时返回结构化错误。
- 取消或失败清理 `.part`；成功后文件由用户管理。
- 不请求全盘存储权限，不自动记住超出既有 SAF 行为的目录授权。

## 5. Share

```text
acknowledge sensitive content
→ create one cache file under cacheDir/logcat-share/
→ FileProvider content URI
→ ACTION_SEND(text/plain, read-only temporary grant)
→ system chooser
```

- `FileProvider` 必须 `exported=false`、`grantUriPermissions=true`，path 配置只允许 `logcat-share/` 子目录。
- Intent 只包含本次文件，不含目录、其他缓存或持久 URI 权限。
- 状态按 `PREPARED → CHOOSER_OPENED → TARGET_SELECTED/CANCELLED/OUTCOME_UNKNOWN → EXPIRED/CLEANED` 转换；打开 chooser 或应用恢复前台不得报告成功。
- 只有平台回调明确报告目标选择时才进入 `TARGET_SELECTED`，且不得据此声称接收方已读取；明确报告取消时进入 `CANCELLED` 并立即清理。
- 平台不能观察目标选择或取消时进入 `OUTCOME_UNKNOWN`，使用中性 UI 状态；`TARGET_SELECTED`/`OUTCOME_UNKNOWN` 最多保留 1 小时以允许接收方读取。
- 分享目标拒绝文件且平台提供可观察失败信号时返回明确失败；平台没有提供接收/拒绝结果时必须进入 `OUTCOME_UNKNOWN`，不得从 chooser 返回或应用恢复前台推断成功或失败。
- 应用启动、新分享前和清除数据时幂等清理过期文件；不得在 chooser 返回瞬间删除已交付文件。
- 创建分享文件失败不得回退到未授权公共目录。

## 6. Structured errors

At minimum:

```text
NO_ACTIVE_SESSION
OPERATION_CONFLICT
CAPTURE_UNSUPPORTED
CAPTURE_TIMED_OUT
CAPTURE_CANCELLED
SESSION_DISCONNECTED
SIZE_LIMIT_REACHED
SAF_PERMISSION_DENIED
SAF_PROVIDER_UNSUPPORTED
SAF_SPACE_INSUFFICIENT
SAF_COMMIT_FAILED
SHARE_CACHE_FAILED
SHARE_TARGET_UNAVAILABLE
```

错误不得携带原始 Logcat 内容、真实端点或原始命令。
