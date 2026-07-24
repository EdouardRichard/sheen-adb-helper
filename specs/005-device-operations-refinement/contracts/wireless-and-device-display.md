# Contract: Wireless Pairing and Device Display

## 1. Core discovery request

```text
WirelessDiscoverySourceRequest
  generation: Long
  mode: LAN_SCAN | LOCAL_PAIRING
  deadlineMonotonic: Duration
  currentNetwork: project-owned network identity?
```

Contract rules:

- `LOCAL_PAIRING` 的截止时间由唯一配对窗口创建，最大为开始后 2 分钟；adapter 不得覆盖为 10 秒。
- `LAN_SCAN` 保持现有 10 秒策略。
- 同一 `generation` 只能有一个 source；`stop(generation)` 必须幂等并在 3 秒内释放 NSD listener、计时器和通知关联。
- 不对外暴露 `NsdServiceInfo`、Socket、原始 TXT 字节或平台异常。

## 2. Start and notification ordering

```text
User click
  → acquire/reuse LocalPairingWindow
  → emit PROMPT_ENABLE_DEBUGGING
  → start visible short service + notification
  → begin NSD discovery
```

- 通知能力可用时，点击后 1 秒内必须出现“请开启无线调试。”与“停止”。
- NSD 初始化失败不得阻止先显示启动状态；随后用当前操作错误结束窗口。
- 通知不可用时，同一 ViewModel 状态驱动应用内提示、停止和输入，不把通知限制映射为配对错误。
- 重复点击只复用活动 `windowId` 或明确重启同一窗口；不得并行。

## 3. Pairing input

```text
submitPairingCode(windowId, inputNonce, sixDigitCode)
```

Accept only when:

1. `windowId` 仍活动；
2. 设备已解锁；
3. nonce 对应当前已重新确认的 pairing endpoint；
4. 输入严格为 6 位数字；
5. 当前没有另一个提交在执行。

无论成功、失败、取消或过期，调用方都必须清空输入对象；错误结果不得回显配对码或端点。

## 4. Discovery observation and display

```text
WirelessServiceObservation
  serviceIdentity
  optionalDisplayName
  displayNameSource: VERIFIED_AFTER_CONNECTION | UNAVAILABLE
  optionalNameIdentityReference
  addressFamily: IPv4 | IPv6
  normalizedAddress
  port
  serviceType
  expiry
```

原始 ADB mDNS/NSD 观察的 `displayNameSource` 必须为 `UNAVAILABLE`。`optionalDisplayName` 只有在连接后通过既有 overview 属性取得设备型号、来源为 `VERIFIED_AFTER_CONNECTION`、`optionalNameIdentityReference` 等于当前观察的 `VerifiedWirelessDeviceId`，并通过以下校验时存在：

- 去除首尾 Unicode 空白后非空；
- 最多 80 个 Unicode 码点；
- 不含 ISO 控制字符；
- 不含双向控制字符 U+061C、U+200E–U+200F、U+202A–U+202E、U+2066–U+2069；
- 不是从 service instance、GUID/序列样式字符串、host、IP 或反向解析猜测。

任一校验失败时必须将来源和名称整体降级为 `UNAVAILABLE`/空值并继续显示完整端点，不得截断或替换字符后展示名称。

UI rendering:

```text
[optionalDisplayName + newline]
{IPv4|IPv6} · {完整地址} · {1..65535}
```

设备名不参与去重；名称身份关联失效时立即降级为无名称行。IPv6 地址作为独立字段渲染，不通过按冒号拆分字符串取得端口。
