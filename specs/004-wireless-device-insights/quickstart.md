# Quickstart: v0.04 无线发现与设备分析增强

本文是实施与验收入口，不代替后续 `tasks.md`。在生成并批准 Tasks 前不得修改业务代码。

## 1. 前置检查

```powershell
$env:JAVA_HOME='C:\Users\Richard\.gradle\sheen-jdk21'
Get-Content .specify\feature.json
git status --short
```

确认 active feature 为 `specs/004-wireless-device-insights`，并依次阅读：

1. `.specify/memory/constitution.md`
2. `specs/004-wireless-device-insights/spec.md`
3. `specs/004-wireless-device-insights/plan.md`
4. `specs/004-wireless-device-insights/research.md`
5. `specs/004-wireless-device-insights/contracts/*.md`
6. 获批后的 `specs/004-wireless-device-insights/tasks.md`

实施前还必须核对 [ADR 0007](../../docs/adr/0007-wireless-discovery-and-local-pairing-lifecycle.md) 与 [权限矩阵](../../docs/权限矩阵.md)。

## 2. 推荐实施顺序

1. 在 `:core:adb` 增加项目自有 discovery/pairing model、generation/session guard、NSD 平台适配和 QR payload；先以 fake adapter 覆盖状态机、去重、取消、超时与清理。
2. 扩展 `:feature:devices` 的 mode selector、二维码/配对码、本机模式和发现列表；保留手动地址入口与 Session 替换确认。
3. 在 `:app` 登记四项批准权限与唯一非导出 `shortService`，实现通知/RemoteInput/锁屏/2 分钟 deadline；feature 只通过项目自有 coordinator 交互。
4. 先完成 APK parser 依赖审查与 Android 运行时 smoke test，再实现有界元数据 enrichment；审查失败立即走 contract 规定的替代/降级，不先扩展 UI。
5. 扩展 apps 名称/图标/双字段搜索，再完成 process association、structured logcat 和组合筛选。
6. 运行全量自动化、merged manifest 检查和真机矩阵；分别报告功能、真机与发布合规。

## 3. 自动化验证

```powershell
$env:JAVA_HOME='C:\Users\Richard\.gradle\sheen-jdk21'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

重点断言：

- QR payload 转义/随机材料/终态清理；六位 ASCII 数字校验。
- discovery 只包含两个 TLS service type；重复、丢失、网络切换、迟到回调、IPv4/IPv6。
- LAN 10 秒停止；local window 2 分钟停止；锁屏通知没有输入 action；旧 token/锁屏/超时提交被拒绝。
- 通知权限拒绝/关闭/OEM 不兼容始终保留应用内输入。
- APK 超限、损坏、缺资源、split-only、取消和 Session 变化不泄漏字节或旧结果。
- 名称/包名 OR 搜索与 enabled filter 交集；同名应用显示包名。
- Logcat threadtime parser、UNPARSED/STDERR、各筛选和多条件 AND；进程关联 UNKNOWN/MULTIPLE 不误归属。
- 既有设备、应用操作、文件、Logcat 最近历史/跟随/导出测试继续通过。

## 4. Manifest 检查

merged manifest 应只有本功能新增的：

- `android.permission.POST_NOTIFICATIONS`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.CHANGE_WIFI_MULTICAST_STATE`
- `android.permission.ACCESS_NETWORK_STATE`

以及既有 `INTERNET`。不得出现相机、定位、`NEARBY_WIFI_DEVICES`、存储、开机广播、忽略电池优化或无障碍权限。Service 必须 `exported=false` 且 `foregroundServiceType=shortService`。

## 5. 真机/受控网络验收

| 场景 | 覆盖 |
|---|---|
| Android 11–16 QR | 被控端系统扫描控制端 QR；成功/错误/取消/过期；QR 不支持时回退六位码 |
| 本机配对 | 默认配对码；5 秒内发现/未发现/受限；通知提交与应用内提交结果一致 |
| 通知降级 | Android 13+ 允许/拒绝/关闭通知；至少两种 OEM 自定义通知样式；显示原生样式建议与应用内入口 |
| 锁屏 | 锁屏通知隐私化且无输入 action；解锁后在有效窗口内出现输入；旧 token/过期提交失败 |
| 生命周期 | success/cancel/service lost/timeout/Session change 后 3 秒内停止；后台无 LAN/Logcat/file 任务被服务延长 |
| LAN discovery | 15 个 pairing/connect 服务、重复广播、同名、离线、端口变化、IPv4/IPv6、网络切换；无端口扫描 |
| 应用 | 200 个第三方应用、中英文/同名/缺名/缺图标/超大 APK；10 秒内名称/图标或明确降级，包名始终正确 |
| 诊断 | PID 复用、多进程、共享 UID、进程退出、缺字段、10k/4MiB 截断、组合筛选 1 秒内更新 |

证据必须使用合成/脱敏名称、地址、包名和日志；不得把真实配对材料、端点、应用上下文或 Logcat 写入仓库。

## 6. 发布门禁

Debug 构建与真机通过不等于可发布。`spake2-java 1.0.5` 的 GPL-3.0-or-later 冲突在 ADR 0004 的门禁关闭前仍阻断发布；ZXing core 与新 APK parser 也必须完成许可证、传递依赖、NOTICE、维护与移除路径登记后才能随构建交付。
