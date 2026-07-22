# ADR 0007：无线调试发现与本机配对生命周期

- 状态：Accepted
- 日期：2026-07-22
- 决策者：项目负责人（已批准通知、2 分钟短时前台服务、`CHANGE_WIFI_MULTICAST_STATE` 与 `ACCESS_NETWORK_STATE`）
- 关联规格：[`specs/004-wireless-device-insights/spec.md`](../../specs/004-wireless-device-insights/spec.md)

## 背景

v0.04 需要显示二维码供被控端系统扫描、自动发现局域网 ADB 无线调试服务，并允许用户在同一台设备的系统无线调试设置与本应用之间切换时从通知输入六位配对码。普通前台页面生命周期不足以承接短暂切换，但长期后台发现、端口扫描或额外网络权限会扩大能力边界。

Android 11–16 的 NSD API 存在兼容差异：旧版本通常需要在发现期间持有 Wi-Fi multicast lock，新版本可以按当前 `Network` 发现并在 API 34+ 返回多地址。Android 13+ 通知需要运行时授权；Android 14+ 对短时前台服务提供 `shortService` 类型与系统超时。锁屏 RemoteInput 的 UI 是否出现还会受 OEM 样式影响。

## 决策

### 1. 发现范围

- 只通过 `NsdManager` 发现 `_adb-tls-pairing._tcp` 和 `_adb-tls-connect._tcp`。
- 不发现旧式 `_adb._tcp`，不枚举子网，不主动探测任何主机/端口。
- 普通局域网发现仅在页面前台运行，默认 10 秒；离页、后台、取消或网络变化即停止。
- API 30–32 在发现窗口内临时持有 `WifiManager.MulticastLock`；API 33+ 绑定当前 `Network`，API 34+ 使用多地址解析。所有 callback、锁和 coroutine 都在结束路径释放。
- 完全重复服务按当前 network/type/name 去重；配对与连接服务只有在后续 TLS/Session 身份可靠相同时才合并。名称、IP 和端口不构成设备身份。

### 2. QR 与客户端角色

- 控制端生成 `WIFI:T:ADB;S:<service-instance>;P:<password>;;` 并显示二维码；被控端系统无线调试扫描。
- 被控端公布匹配的 pairing service 后，控制端作为 Kadb pairing client 发起配对。
- 配对完成后刷新 connect services；当前 Kadb 不提供可靠 peer GUID 时不自动猜测对应连接项。
- QR service name、password、payload 和 bitmap 仅内存，所有终态清理。

### 3. 本机短时前台服务

- `:app` 声明一个 `exported=false` 且 `foregroundServiceType="shortService"` 的 Service，仅用于本机配对窗口。
- Activity 可见时启动，5 秒内进入前台；应用硬截止 2 分钟，API 34+ 同时处理系统 `onTimeout`；`START_NOT_STICKY`。
- success、cancel、pairing service lost/changed、deadline、Session change 或系统 timeout 后立即停止发现、撤销通知并清除 secret，目标是 3 秒内完成。
- Service 不承载普通 LAN discovery、文件、Logcat、应用元数据或其他后台任务；不自启、不监听开机、不申请电池优化豁免。

### 4. 通知、锁屏与降级

- Android 13+ 仅在用户进入本机通知配对时请求 `POST_NOTIFICATIONS`。拒绝、通知关闭或 OEM inline reply 不兼容均回退应用内输入，不阻断配对。
- 通知使用独立 channel、`VISIBILITY_PRIVATE`、脱敏 `publicVersion`、local-only；不显示配对码、真实端点或设备身份。
- 锁屏时通知不包含 RemoteInput action；解锁后才更新为可输入。提交 PendingIntent 显式、一次性、mutable、带随机 token，API 31+ 要求认证；Service 再校验解锁、token、deadline、端点与六位 ASCII 数字。
- OEM 样式不兼容时提示切换为系统原生通知样式，但不保证能解决所有 ROM 限制，并始终展示应用内入口。

### 5. 权限

新增并批准：

- `POST_NOTIFICATIONS`：Android 13+ 运行时，按需；拒绝回退应用内输入。
- `FOREGROUND_SERVICE`：普通权限；只支持上述最长 2 分钟 short service。
- `CHANGE_WIFI_MULTICAST_STATE`：普通权限；只在 API 30–32 活动 discovery 窗口持有 multicast lock。
- `ACCESS_NETWORK_STATE`：普通权限；用于读取当前 network、绑定发现和在网络变化时停止旧 generation。

不声明相机、位置、`NEARBY_WIFI_DEVICES`、存储、开机广播、忽略电池优化或未来本地网络权限。targetSdk 或平台政策变化时重新走规格、权限矩阵和 ADR。

## 影响

### 正面

- 自动发现严格限于系统公开的 ADB TLS 服务，符合最小能力原则。
- 本机配对跨设置切换可用且有硬时限，不建立后台常驻架构。
- 锁屏和 OEM 降级是显式状态，不依赖不可保证的通知 UI 行为。
- API 差异隐藏在 `:core:adb` 平台适配器，feature 不接触网络/ADB 原始类型。

### 代价与风险

- 需要四项 Manifest 权限、一个 Service 和 API 30–36 兼容测试。
- OEM 可能不显示 inline reply；用户必须使用应用内输入。
- 网络隔离/VPN/ROM 策略可让 NSD 无结果；手动端点入口仍必需。
- Kadb 未暴露可靠配对 peer 身份时不能自动把 pairing/connect 广播合并，列表会更保守。

## 被拒绝方案

- 子网/端口扫描：扩大网络探测面且超出规格。
- 常驻后台发现或 WorkManager 循环：超过 2 分钟授权与即时交互需求。
- 锁屏直接显示输入 action：不能跨 OEM 保证解锁前完全不可输入。
- 申请位置/Nearby/相机/电池豁免：当前目标 API 与角色不需要，违反最小权限。
- 按 service name/IP/port 自动合并设备：可能误归属并错误连接。

## 验证与回滚

- 自动化覆盖 generation、stop cleanup、去重、锁屏 action、token/deadline、权限拒绝与应用内回退。
- merged Manifest 必须与权限矩阵完全一致；真机覆盖 Android 11–16 和至少两种 OEM 通知样式。
- 若 short service 或通知在目标平台不可可靠工作，回滚 Service/通知入口并保留前台应用内本机扫描/输入；不得以延长后台时间或扩大权限补救。
