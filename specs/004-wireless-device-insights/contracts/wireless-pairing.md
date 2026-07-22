# Contract: Wireless Discovery and Pairing

## Public core boundary

`AdbSessionManager`（或其项目自有协作者）向 feature/app 暴露以下语义，具体 Kotlin 命名可在实现任务中细化，但不得暴露 `NsdManager`、Kadb、Socket、`NsdServiceInfo` 或原始 Shell：

- `observeWirelessServices(mode, timeout): Flow<AdbOperationResult<WirelessDiscoveryState>>`
- `createQrPairingAttempt(timeout): AdbOperationResult<QrPairingMaterial>`
- `pairWithDiscoveredService(observationId, secret, expectedAttemptId): AdbOperationResult<PairingResult>`
- `cancelPairing(attemptId)`

所有 API 都必须有 generation/attempt/session 归属、有限 timeout、取消和确定性 cleanup。`pairWithDiscoveredService` 接收可清零的 secret 容器，结束后无论结果都清除。

## Discovery invariants

1. 只注册 `_adb-tls-pairing._tcp`、`_adb-tls-connect._tcp`。
2. 不注册 `_adb._tcp`，不枚举子网，不向未公布服务的主机探测端口。
3. 普通 LAN discovery 只在页面前台，默认 10 秒；local pairing 可由已批准 short service 延长，但不超过 2 分钟。
4. stop 必须注销 discovery/resolve callback、释放 MulticastLock、取消 child coroutine，并让该 generation 的迟到结果无效。
5. 地址作为类型值处理，支持 IPv4/IPv6 和当前 Network 路由；不通过字符串拼接创建 endpoint。
6. 完全重复观察按 `(network,type,name)` 去重；PAIRING/CONNECT 只按 verified identity 合并。

## QR contract

- 格式：`WIFI:T:ADB;S:<service-instance>;P:<password>;;`。
- QR 只供被控端系统扫描；控制端不请求相机。
- 只接受与 `S` 精确相同且仍在有效期的 pairing service。
- 成功配对后刷新 CONNECT 服务；若无法可靠关联，不自动选择/连接任何结果。
- material 不进入 SavedState、持久化、剪贴板、日志或诊断。

## Code-pairing contract

- 接受六位 ASCII 数字，提交后 UI 和通知输入立即清除。
- 目标端点必须来自仍有效的 resolved pairing observation 或现有手动端点解析器。
- 本机模式默认 code pairing；QR 与手动 code pairing 都复用同一最终 Kadb client path。

## Local notification contract

- `:app` 启动唯一非导出 `shortService`；Service 只做 local pairing discovery/notification orchestration，核心发现/配对仍委托 `:core:adb`。
- 锁屏：private/redacted notification，无 RemoteInput action。
- 解锁且端点有效：更新为带 RemoteInput 的通知；PendingIntent explicit、one-shot、mutable、tokenized，API 31+ authentication required。
- 接收端再次验证解锁、token、deadline、六位码、端点仍有效；任一失败不调用 pair。
- 通知权限拒绝/关闭/OEM 不兼容：状态转为 input unavailable，应用内入口保持可用并显示切换原生通知样式建议。
- 结束条件：success、failure terminal、cancel、service lost、deadline、Session change、`onTimeout`；3 秒内停止发现/服务并移除通知。

## Single-session behavior

配对本身不创建第二个活动 Session。连接发现结果前，如已有其他活动 Session，必须向用户显示明确替换确认；未确认不得断开或自动连接。所有旧 Session/generation 结果必须丢弃。
