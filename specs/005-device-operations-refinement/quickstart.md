# Quickstart Validation: v0.05 设备操作体验与性能收敛

本指南用于实现完成后的端到端验证，不包含实现任务或完整测试代码。测试材料必须使用虚构端点、包名和日志内容，不把真机敏感值写入仓库或构建日志。

## 1. Prerequisites

- Windows PowerShell，仓库根目录 `D:\androidPorject\sheen-adb-helper`
- `JAVA_HOME=C:\Users\Richard\.gradle\sheen-jdk21`
- Android SDK 36
- API 30、33、36 模拟器用于兼容性覆盖，不要求三台现有真机恰好覆盖这些 API
- 三台现有设备：设备 A/B 为 Android 11+ 主控候选；设备 C 低于 Android 11，只在可建立 ADB 连接时作为被控兼容性目标，不能用于安装本应用或原生无线调试配对验收
- 一个普通多进程测试应用、一个短时进程、一个策略拒绝目标

在验证记录中只登记脱敏后的设备代号、Android 大版本、ROM 类别和实际角色，不记录真实端点、设备标识、包名上下文或日志内容。若 A/B 中某台不支持通知 RemoteInput 或无线调试，对应项目标为“不适用”，不得改由设备 C 冒充主控证据。

百分比性能指标统一采用 20 次满足前置条件的试验：可在 A/B 的适用角色之间分配，但必须记录分母、成功数和耗时。95% 表示 20 次中至少 19 次达标；若没有设备满足“至少 200 个应用”等前置条件，则该指标标记“未验证”，不得用更小样本或单次结果代替。

## 2. Automated validation

```powershell
$env:JAVA_HOME='C:\Users\Richard\.gradle\sheen-jdk21'
.\gradlew.bat :core:adb:testDebugUnitTest :core:data:testDebugUnitTest :core:ui:testDebugUnitTest :feature:devices:testDebugUnitTest :feature:overview:testDebugUnitTest :feature:shell:testDebugUnitTest :feature:processes:testDebugUnitTest :feature:logcat:testDebugUnitTest :feature:settings:testDebugUnitTest :feature:apps:testDebugUnitTest :feature:files:testDebugUnitTest :app:testDebugUnitTest --no-parallel --no-daemon
.\gradlew.bat :app:assembleDebug --no-parallel --no-daemon
```

Expected:

- 所有模块测试通过，Debug APK 可构建。
- NSD 模式/截止时间、Session 切换、取消和 listener 清理测试通过。
- 进程解析覆盖 CPU 双样本、PSS、PID 复用、部分终止、拒绝和未知结果。
- Logcat 覆盖两种模式、10 分钟/10 MiB、独占租约、SAF 失败和分享缓存清理。
- `git diff --check` 无空白错误。

## 3. Static retirement checks

对生产源码执行：

```powershell
rg -n "AdbDiagnosticEvent|diagnosticEvents|allIcons|StructuredLogcat|DiagnosticFilter" app core feature
rg -n "查看脱敏诊断事件|IPV4.*端口" app core feature
```

Expected:

- 第一条不命中已删除的生产能力；仅测试迁移中临时命中必须在交付前清零。
- 第二条不命中旧入口或旧端点文案。
- 历史归档/被取代 ADR 可保留有明确历史语境的文字。

## 4. Local pairing scenarios

1. 授权通知，点击“菜单 → 本机无线配对”。
2. 验证 1 秒内出现“请开启无线调试。”与“停止”，然后切换到系统设置。
3. 在第 10 秒以后才开启无线调试配对端口，验证扫描仍活动且 5 秒内更新为“已检测到配对端口，请输入配对码：”。
4. 验证锁屏不显示端点/配对码且不能直接提交；解锁后输入六位配对码。
5. 分别测试通知停止、应用内停止、成功、端口失效和 2 分钟超时；3 秒内通知/NSD 清理。
6. 拒绝通知权限后重复，验证应用内有同等状态、停止和输入，不反复索权。
7. 连续点击入口，确认只有一个窗口、一个通知和一个 NSD source。
8. 分别验证用户划除通知、扫描中撤销通知权限和设备重启；不得留下不可见扫描，返回应用后只显示明确终态或允许用户重新开始。

本节只在设备 A/B 的适用角色上执行；设备 C 不计入本机配对分母。SC-002 的 5 秒指标累计执行 20 次适用试验并记录至少 19 次是否达标。

## 5. Wireless device display

先使用连接后验证名称、无可靠名称、同名、IPv4 和 IPv6 的注入展示夹具验证：

- 具名设备两行显示；无名设备只显示端点行。
- 端点始终为 `协议 · IP · 端口`，完整 IPv6 不被错误拆分。
- 同名设备不因名称合并；端口变化和过期仍遵守既有身份规则。
- 名称模型保留 `VERIFIED_AFTER_CONNECTION/UNAVAILABLE` 来源；连接后设备型号只在与同一 `VerifiedWirelessDeviceId` 关联时显示，身份关联失效即降级为无名。
- service instance、GUID/序列样式字符串、hostname、反向解析结果和地址不会被猜成设备名；原始 ADB mDNS/NSD 观察默认为无名。
- 名称去除首尾 Unicode 空白；80 个 Unicode 码点可显示，81 个码点、ISO 控制字符及 U+061C、U+200E–U+200F、U+202A–U+202E、U+2066–U+2069 任一双向控制字符均整体降级为无名，不截断。

展示夹具只证明格式、去重和安全文本策略。另以模拟或真实成功连接验证 overview 型号只在关联同一 `VerifiedWirelessDeviceId` 后进入 `VERIFIED_AFTER_CONNECTION`；身份不匹配、连接失败和原始 NSD 观察均保持 `UNAVAILABLE`。

## 6. Application management

1. 在至少 200 个应用的适用被控设备加载列表，记录 20 次从进入页面到可操作状态的时间；若三台设备均不满足数量前置条件，SC-006 标记未验证。
2. 验证至少 19/20 次在 10 秒内可用，所有条目仅显示应用名和包名。
3. 解析失败条目显示“无法解析应用名”并保留正确包名。
4. 按应用名/包名搜索，并回归现有启动、停止、清除等已批准操作。
5. 用 profiler/测试探针确认图标提取、传输、解析、缓存和渲染次数均为 0。

## 7. Process management

1. 进入页面，验证字段：应用名、进程名、CPU%、PSS MiB、PPID、PID。
2. 点击刷新 20 次，确认同一 500 ms 区间计算且至少 19/20 次在 5 秒内完成；仅一个样本或 PSS 不可用时显示“计算中/未知”。
3. 对无可靠包关联进程确认应用名为“无法解析应用名”且不提供“整个应用”。
4. 选择单进程，分别取消和点击“我了解”；取消路径不得发送命令，确认路径须执行后刷新验证。
5. 对多进程应用选择整个应用，确认页面明确说明 force-stop 会停止应用进程、服务和任务，并可能在用户再次显式启动前阻止后台恢复；确认集合只用于身份/结果验证，验证已终止、部分终止或结果未知分类。
6. 构造 PID 复用、集合变化、自然退出、策略拒绝、超时、取消、断开和 Session 切换，确认零范围外误终止和零旧结果更新。

结果语义以 [process-management.md](contracts/process-management.md) 为准。

## 8. Logcat extraction and output

### Snapshot

1. 生成虚构标记日志后启动快照。
2. 验证自动完成、内容只来自开始时可读缓冲，且不超过 10 MiB。
3. 保存到 `OpenDocumentTree` 选择的目录，验证文件名含 `snapshot`、内容可读且无残留 `.part`。

### Continuous

1. 启动后再生成虚构标记日志；验证开始前标记不在文件内。
2. 验证 elapsed/byteCount 更新和用户停止。
3. 分别触发 10 分钟、10 MiB，确认只生成一段且停止原因正确。
4. Session 断开/切换时确认捕获停止且旧字节不写入新任务。

### Share and cleanup

1. 确认敏感内容提示后打开系统分享页，累计执行 20 次并验证至少 19/20 次在 10 秒内出现，且每次只含本次一个 `.txt` 文件；打开 chooser 只进入 `CHOOSER_OPENED`，不显示分享成功。
2. 平台明确报告取消时进入 `CANCELLED` 并确认临时文件立即清理；平台不能观察取消时进入 `OUTCOME_UNKNOWN`，使用中性文案且不误报成功。
3. 平台明确报告目标选择时进入 `TARGET_SELECTED`，确认 URI 为只读临时授权、目录不可枚举，但不声称接收方已读取；`TARGET_SELECTED`/`OUTCOME_UNKNOWN` 的文件在 1 小时后或下次启动/新分享时被清理。
4. 模拟缓存/SAF 无空间、provider 不支持、写入失败，确认结构化错误且不回退到公共目录。

## 9. API 30/33/36 emulator compatibility

在 API 30、33、36 模拟器上安装同一份 Debug APK，并逐台执行：

1. 启动、首页与设备列表导航，验证一行/两行设备展示夹具和 IPv4/IPv6 端点格式不崩溃。
2. 验证应用、进程和 Logcat 页面加载、空状态、结构化错误、刷新/停止控件及 Session 取消路径。
3. 验证通知渠道与短时服务启动；平台可用时验证通知“停止”和 RemoteInput，模拟器缺少无线调试配对端口时将真实配对标为“不适用”，不得记为通过。
4. 验证 `OpenDocumentTree`、受限 FileProvider 和系统分享页；平台不提供可观察的目标/取消结果时确认 `OUTCOME_UNKNOWN`，不得推断成功。
5. 每个 API 分别记录通过、失败、不适用和未验证；模拟器证据不得替代三台现有设备的人工功能与性能证据。

## 10. Governance and final evidence

- 更新 `docs/权限矩阵.md`，证明无新增权限种类。
- 更新受影响架构文档并将 ADR 0002/0006 标记为被 v0.05 取代，保留历史正文。
- 实际执行并分开记录第 9 节 API 30/33/36 模拟器兼容性与三台现有设备的人工证据；设备 A/B/C 的实际 Android 大版本、ROM 类别、主控/被控角色、通过/失败、不适用及未覆盖风险必须如实登记。
- 当前只有一名验收用户：分别完成停止配对扫描、刷新进程、确认/取消终止、保存/打开分享页的适用任务走查；不生成“10 人/90%”或其他群体可用性结论。
- 单独报告功能验证与发布合规；既有 `spake2-java 1.0.5` GPL 阻断仍未解除。
