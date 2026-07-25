# Phase 1 Data Model: v0.1 菜单与连接页 UI

## 导航与语言

```text
UiLanguage = ZH_CN | EN_US
Destination = CONNECTION | FILES | APPS | PROCESSES | SHELL | LOGCAT
DrawerState = CLOSED | OPEN
```

`UiLanguage` 持久化为现有 profile DataStore 的单一键，缺失或未知值回退 `ZH_CN`。`SettingsViewModel` 提供读取流、设置语言和清除本地数据时的重置动作。`Destination` 是表现层路由；文件、应用、进程、Shell、日志页面的现有状态模型不迁移。

## 连接页状态

```text
ConnectionPageUiState {
  endpointInput: String
  connection: Unconnected | Connecting | Connected | Disconnecting | Failed
  connectedSessionId: SessionId?
  endpoint: Endpoint?
  deviceLabel: String?
  overview: DeviceOverviewState
  discovery: DiscoveryPresentation
  pairing: PairingPresentation
  quickAction: QuickActionUiState
  notice: LocalizedNotice?
}
```

`DeviceOverviewState` 复用 `feature:overview` 的 session-aware 数据：`Loading`、`Ready(DeviceOverview)`、`Unavailable`、`Error`。数值缺失必须显示“未知/Unknown”，不得伪造零值。连接、断开或 session 替换时清除旧 session 的概览、发现结果选择和快捷操作进度。

`DiscoveryPresentation` 保留当前 10 秒扫描、取消、空结果、失败和选中设备确认；下拉刷新只触发新一代 `refreshDiscovery()`，不改变扫描协议。`PairingPresentation` 保留二维码、6 位配对码和本机配对的现有阶段与错误映射。

## 快捷操作状态

```text
QuickActionKind = SCREENSHOT | SCREEN_RECORD | REBOOT

QuickActionUiState =
  Idle
  | Confirming(kind, sessionId)
  | Running(kind, sessionId, progress, startedAt, bytesWritten)
  | AwaitingExport(kind, sessionId, artifact)
  | Exporting(kind, sessionId, artifact)
  | Succeeded(kind, sessionId, destinationName?)
  | Cancelled(kind, reason)
  | Failed(kind, reason, technicalCode?)
  | ResultUnknown(kind, sessionId, reason)
```

`artifact` 是不暴露原始路径的 opaque app-private artifact handle，包含类型、大小、校验/格式状态和过期时间；UI 只能把导出事件交给 `QuickActionUseCase`，由 Use Case 通过 `core:data` 项目自有端口完成导出。录屏 `progress` 同时受 5 分钟和 256 MiB 限制，达到任一上限进入 `AwaitingExport` 或 `Failed`（取决于完整性校验），不得开始第二段。

## ADB 项目模型

`core:adb` 新增 project-owned 类型（名称可在任务阶段调整，但语义固定）：

- `QuickActionCapabilities`: 被控端截图、录屏、重启能力及 unsupported/policy reason。
- `ScreenshotCaptureRequest`、`ScreenRecordRequest`、`RebootRequest`：均含 `expectedSessionId`、超时/取消上下文和不记录敏感字段的选项。
- `QuickActionResult<CaptureMetadata>`：`Success`、`Failure(AdbError)`、`Cancelled`、`StaleSession`、`ResultUnknown`；ADB 结果不携带 `core:data` artifact、File 或 URI。
- `QuickActionProgress`: 阶段、字节数、elapsed、size/time limit。
- `QuickActionLease`：全局快捷操作 lease；与文件传输、APK 提取、Logcat 互斥。

原始 shell 命令、remote temp 路径、Socket/TLS 类型和 Kadb 细节仅存在 `core:adb` 内部。被控端截图/录屏输出只写入项目自有 `AdbCaptureSink`；`:core:adb` 不拥有 app-private artifact，也不能返回 UI 可操作的 `File`、`ContentResolver` 或 URI。`feature:overview` 内部适配器将该端口桥接到 `core:data` 的项目自有 `ArtifactSink`，不得向 ViewModel/UI 暴露协议字节流或平台类型。

## SAF 导出模型

`core:data` 增加 `SafBinaryExportRequest(sourceArtifact, targetUri, displayName, mimeType)` 与结构化 `SafBinaryExportResult`（成功、用户取消、目标不可写、源为空/格式无效、空间/IO、清理失败）。目标 URI 只能来自用户 `CreateDocument` 结果。导出采用固定大小 chunk、源大小校验、失败时 best-effort 关闭和删除主控端 temp；不在用户 tree 中建立 quick-action provider-side `.part` 临时文件。

`feature:overview` 的 `QuickActionUseCase` 负责创建 `core:data` artifact/sink、调用 `core:adb` typed capture API、提交校验、导出并返回 opaque artifact reference 与结构化用例结果；ViewModel 把结果归约为 `QuickActionUiState`，不持有 sink 或平台类型。app 只构造/注入 Use Case，并把系统文件选择结果转换为项目自有 `ExportDestination` 后转交，不拥有上述业务编排。

## 生命周期与不变量

1. 全局最多一个 active ADB Session；每个 quick action 的 `sessionId` 必须与当前连接一致。
2. `SCREENSHOT`、`SCREEN_RECORD`、`REBOOT` 及已有长时操作不能并发；冲突立即产生可本地化提示。
3. Session 切换、断开、取消、超时、进程退出和失败都释放 lease 并清理 temp。
4. 重启只产生“已请求/结果未知”或发送失败，不自动重连。
5. 历史 profile 仍由现有 schema 保存；菜单在线标记由当前 endpoint 与 profile 派生，不新增字段。
