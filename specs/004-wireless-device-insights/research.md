# Phase 0 Research: v0.04 无线发现与设备分析增强

## 1. Android 无线调试二维码

**Decision**: 主控端显示标准负载 `WIFI:T:ADB;S:<service-instance>;P:<password>;;`，被控端使用系统“无线调试 → 使用二维码配对设备”扫描。被控端随后公布 `_adb-tls-pairing._tcp`；主控端按 QR 中的 service instance 精确匹配、解析端点，再作为客户端调用 `Kadb.pair(host, port, password)`。

**Rationale**: AOSP 主机端和 Android Studio 都采用这一角色分工。Kadb 2.1.1 的公开 `pair` 接口接受任意字符串密码，不限制六位数字，适合承接 QR 随机密码；但仍需 Android 11–16 真机互操作验证。

**Alternatives considered**:

- 主控端扫描被控端二维码：与本需求及 Android 无线调试标准流程相反，并会新增相机权限，拒绝。
- 主控端自行提供 pairing server：不符合目标系统扫码后启动/公布配对服务的 AOSP 流程，拒绝。
- 仅复用六位配对码：无法满足二维码入口，保留为默认本机方式和通用回退。

**Security/lifecycle**: service instance、随机密码、二维码字符串和 bitmap 都是敏感临时材料；仅驻留内存，不进 SavedState、剪贴板、日志、诊断或截图证据。成功、失败、取消、超时、页面离开或进程终止时使其失效并清除引用。

**QR encoder dependency**: 采用 `com.google.zxing:core:3.5.4` 的 QR writer，只把 `BitMatrix` 转为 Compose/Android 可显示像素；不引入 Android scanner、camera integration 或 JavaSE artifact。Maven Central POM 显示该 core artifact 为 Apache-2.0，运行时无传递依赖（JUnit 仅 test scope）；3.5.4 发布于 2025-11，core 仍列为 active，但上游明确不承诺路线图，因此维护风险评为中等。项目通过自有 `QrMatrixEncoder` 隔离，未来可替换为项目自有编码器。实施时仍须登记许可证/NOTICE、R8 体积和移除路径。

**Primary references**:

- [AOSP ADB Wi-Fi design](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md)
- [Android Studio WiFiPairingServiceImpl](https://android.googlesource.com/platform/tools/adt/idea/+/mirror-goog-studio-main/android-adb/src/com/android/tools/idea/adb/wireless/WiFiPairingServiceImpl.kt)
- [AOSP Settings AdbQrcodeScannerFragment](https://android.googlesource.com/platform/packages/apps/Settings/+/f445e1018d6db47c984af2b51cf4827f0d264adb/src/com/android/settings/development/AdbQrcodeScannerFragment.java)
- [Kadb 2.1.1 source](https://github.com/flyfishxu/Kadb/tree/2.1.1)
- [ZXing core 3.5.4 on Maven Central](https://central.sonatype.com/artifact/com.google.zxing/core/3.5.4)
- [ZXing source and Apache-2.0 license](https://github.com/zxing/zxing)

## 2. 局域网与本机服务发现

**Decision**: 使用平台 `NsdManager`，仅发现 `_adb-tls-pairing._tcp` 和 `_adb-tls-connect._tcp`。每次普通局域网扫描以前台 10 秒窗口运行；离开页面、进入后台、取消或网络变化即停止。API 30–32 使用 legacy 回调并在扫描期持有 `WifiManager.MulticastLock`；API 33+ 绑定当前 `Network`，API 34+ 采用多地址解析能力。

**Rationale**: 这是 Android 无线调试公开的 mDNS 服务语义，可以发现已明确公开服务的设备，同时不扩大为子网侦察。`CHANGE_WIFI_MULTICAST_STATE` 与 `ACCESS_NETWORK_STATE` 都是安装时普通权限，分别用于旧平台可靠接收组播和绑定/跟踪当前网络；用户已在 Plan 阶段批准。

**Identity rule**: 完全重复的 service observation 以 `(network identity, service type, service name)` 去重。配对/连接服务不能仅因名称、地址或端口相似而合并；Kadb 当前配对返回值不暴露 peer GUID，因此默认保持独立，只有后续 TLS/Session 验证提供可靠身份时才合并。

**Alternatives considered**:

- 扫描子网与常见端口：违反批准范围和最小访问原则，拒绝。
- 引入第三方 mDNS 库：平台 API 足够，增加依赖与维护面没有必要，拒绝。
- 声明位置、`NEARBY_WIFI_DEVICES` 或未来 `ACCESS_LOCAL_NETWORK`：targetSdk 36 的本实现无需这些权限；不预埋未来权限。未来 targetSdk/平台变化须重新评审。

**Primary references**:

- [Android NsdManager API](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [Android local network permission](https://developer.android.com/privacy-and-security/local-network-permission)
- [AOSP ADB Wi-Fi design](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md)

## 3. 本机短时前台服务与通知内输入

**Decision**: 使用一个 `android:foregroundServiceType="shortService"` 的非导出 Service。Activity 可见时启动并在 5 秒内进入前台，应用硬截止 2 分钟；API 34+ 同时实现系统 `onTimeout` 安全终止。返回 `START_NOT_STICKY`，所有终态停止发现、清除输入并移除通知。

通知 channel 专用于本机配对，使用 `VISIBILITY_PRIVATE`、redacted `publicVersion`、`setLocalOnly(true)` 和立即前台行为。Android 13+ 按需请求 `POST_NOTIFICATIONS`；拒绝不会阻止 FGS，但通知抽屉入口可能不可见，因此应用内输入始终存在。

**Lockscreen decision**: 锁屏时发布无 `RemoteInput` action 的通知，解锁后才替换成带输入动作的通知。提交 PendingIntent 显式指向非导出 Service、一次性且含随机 session token；Android 31+ 设置 authentication required，接收端仍用 `KeyguardManager` 再检查。这样满足“解锁前不能输入或提交”，而不是依赖 OEM 是否在锁屏展示编辑器。

**Validation**: 仅接受六位 ASCII 数字；超时、token 不匹配、目标端点已变化、设备仍锁定或 Session 已变化均拒绝。配对码从通知取得后只传入同一核心配对状态机，并在 `finally` 清零字符数组。

**Alternatives considered**:

- 后台常驻 Service：超出批准的 2 分钟窗口，拒绝。
- WorkManager：不适合即时 NSD 与用户交互，也不能替代 FGS 时序，拒绝。
- 锁屏直接提供 RemoteInput：系统只能可靠保证认证后提交，不能跨 OEM 保证编辑器完全不可用，拒绝。

**Primary references**:

- [Android foreground service types: short service](https://developer.android.com/develop/background-work/services/fgs/service-types#short)
- [Android notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [RemoteInput API](https://developer.android.com/reference/android/app/RemoteInput)
- [PendingIntent authentication requirement](https://developer.android.com/reference/android/app/PendingIntent.Builder#setAuthenticationRequired(boolean))

## 4. 远端应用名与图标

**Decision**: 采用两阶段、可降级的当前 Session 内元数据加载：先用现有包管理 Shell 快速返回包名/状态，再按受控包路径逐个读取基础 APK 到有界内存，使用隔离的 `apk-parser` 适配器解析 locale 对应的 label 与图标。单 APK 32 MiB、单图标编码后 1 MiB、图标 LRU 总计 16 MiB、整批 enrichment 10 秒；任何超限、权限、split-only、资源引用或解析问题都保留包名并返回明确降级，不落盘、不重试风暴。

**Rationale**: AOSP `pm list packages` 只提供包名/路径等字段，不能直接返回已解析 label/icon；控制端的 Android `PackageManager` 也不能读取另一台设备的资源。受限读取基础 APK 是无需在被控端安装 helper、无需主控端存储权限且可保持纯本地的可行路径。渐进返回保证 200 应用场景先可用，元数据到达时 UI 和搜索即时更新。

**Dependency review**:

- `net.dongliu:apk-parser:2.6.10`，BSD-2-Clause，功能仅用于解析 AndroidManifest/resources/icon；上游仓库已于 2020-09 归档，维护风险高，不能把“可以解析样例 APK”当作可直接交付的结论。
- 通过项目自有 `ApplicationMetadataParser` 接口隔离；实施前必须验证发布 POM、LICENSE、传递依赖、Android API 30 运行时和恶意/损坏 ZIP 防护。不得因方便引入未批准的 Bouncy Castle/JSR 注解运行时依赖；可选依赖应排除。
- 移除路径：替换为项目自有的最小二进制资源解析器，或保留包名与明确 `UNSUPPORTED` 降级；UI 与 manager contract 不依赖第三方类型。

**Alternatives considered**:

- 在被控端安装/推送 helper：会修改目标设备并扩大执行面，规格未授权，拒绝。
- 把全部 APK 缓存到磁盘：违反默认仅内存和敏感上下文边界，拒绝。
- 只读 `dumpsys package`：通常只有资源 ID，不能稳定解析本地化名称/图标，不能满足需求。
- 从网络商店查询：违反纯本地、隐私和无后端原则，拒绝。

**Primary references**:

- [AOSP PackageManagerShellCommand](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java)
- [apk-parser repository and license](https://github.com/hsiafan/apk-parser)
- [Maven Central apk-parser 2.6.10](https://central.sonatype.com/artifact/net.dongliu/apk-parser/2.6.10)

## 5. Logcat/进程基础分析

**Decision**: 沿用既有前台、用户启动的有界 Logcat 流，将 threadtime 文本解析为结构化字段，并将进程快照与当前应用快照通过可靠身份字段关联。筛选条件采用 AND 交集语义；解析失败保留原始行并标 `UNPARSED`，关联不唯一时标 `MULTIPLE`/`UNKNOWN`。

**Rationale**: 这能完成用户批准的基础级别/tag/keyword/PID/process/app 筛选，同时保持当前 10,000 行或 4 MiB 内存边界、最新 100 条可见窗口和单 Session 生命周期。不会引入新的采集生命周期或把推断冒充事实。

**Alternatives considered**:

- 崩溃/ANR 自动识别、资源趋势和跨时段分析：超出首版批准深度，推迟。
- PID 单字段关联：存在 PID 复用、多进程和快照竞态，拒绝。
- 后台持续 Logcat：违反既有生命周期与范围，拒绝。

## 6. 已知门禁

- Kadb 的传递依赖 `spake2-java 1.0.5` 为 GPL-3.0-or-later，不在仓库白名单。该问题是既有发布阻断项；本计划可以实施和验证，但在 ADR 0004 的解决条件完成前不得声称可对外发布。
- QR 使用 Kadb 任意字符串密码、ZXing 输出与系统 scanner 互操作、NSD API 兼容路径、`apk-parser` Android 运行时与性能都必须通过自动化加 Android 11–16 真机验证；失败时按上述明确降级/移除路径处理，不能隐藏能力限制。
