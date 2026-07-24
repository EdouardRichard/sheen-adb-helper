# Implementation Plan: v0.05 设备操作体验与性能收敛

**Branch**: `未单独创建（active feature: 005-device-operations-refinement）` | **Date**: 2026-07-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-device-operations-refinement/spec.md`

## Summary

v0.05 在不进行重大 UI 重构的前提下完成六项收敛：本机无线配对改为通知立即可见且最长 2 分钟的短时后台扫描；局域网设备按“可选的有来源可靠设备名 + 协议 · IP · 端口”展示，无可靠名称时只显示端点；彻底移除专用脱敏诊断事件历史；应用管理停止图标提取，仅保留应用名和包名；进程监控升级为可刷新、逐次确认风险并可终止单进程或 force-stop 整个应用的进程管理；Logcat 分析降级为有界快照/持续采集、保存到用户选择目录或分享单个日志文件。

技术上复用现有 Android `NsdManager`、单一 `AdbSessionManager`、ADB Shell/Sync、SAF 和 Compose 架构。无线发现以功能目标为准，不引入工作站 `adb` 二进制、ADB Server 或额外 mDNS 包，也不要求复刻 `adb mdns check/services` 的命令语义。所有原始命令、进程身份复核、CPU/PSS 采样、终止及 Logcat 流仍封装在 `:core:adb`；Feature 只消费项目自有状态和结果。

## Technical Context

**Language/Version**: Kotlin 2.3.20，Java 17 字节码

**Primary Dependencies**: Android SDK 30–36、Jetpack Compose/Material 3、Coroutines/Flow、Kadb 2.1.1、Okio 3.17.0、DataStore 1.2.0、`apk-parser` 2.6.10（仅保留应用标签解析）；不新增第三方依赖。实现前必须从已解析依赖图确认 `androidx.core.content.FileProvider` 可用，若需要新增直接依赖声明则暂停并先更新依赖审查

**Storage**: DataStore 继续保存既有非敏感偏好/设备档案；日志默认仅在进程内有界保留，用户保存时使用 SAF 目录，用户分享时使用应用专属缓存目录和非导出的 `FileProvider`

**Testing**: TestNG JVM 单元测试、全部 Gradle 模块回归与 Debug 构建；同一 Debug APK 必须在 API 30/33/36 模拟器上分别执行兼容性矩阵并如实记录通过/失败/不适用/未验证；人工验收按现有 1 名用户和 3 台设备单独记录，其中两台 Android 11+ 为主控候选，一台低于 Android 11 仅在可建立 ADB 连接时作为被控兼容性目标

**Target Platform**: Android 11+（minSdk 30、targetSdk/compileSdk 36）

**Project Type**: 多模块纯本地 Android 应用

**Performance Goals**: 配对点击后 1 秒内显示通知；可发现端口 95% 在 5 秒内进入输入状态；200 应用列表 95% 在 10 秒内可用；进程刷新 95% 在 5 秒内完成；终止在 10 秒内给出已验证结果或明确非成功分类；分享页 95% 在 10 秒内打开。百分比指标采用 [quickstart.md](quickstart.md) 的固定 20 次适用试验口径；前置条件不满足时标记未验证，不以单次结果替代

**Constraints**: 单一活跃 ADB Session；所有操作绑定 `sessionId`/代次并支持取消、有限超时和资源清理；配对扫描最长 2 分钟；持续 Logcat 最长 10 分钟或 10 MiB；不使用 Root/Shizuku/无障碍/设备管理；不新增权限种类；不记录真实端点、配对码、原始 Shell 或 Logcat；不新增 mDNS/进程/日志解析依赖

**Scale/Scope**: 6 个用户故事，涉及 `:app`、`:core:adb`、`:core:data`、`:feature:devices`、`:feature:apps`、`:feature:processes`、`:feature:logcat` 及对应测试和治理文档

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Gate | Pre-design | Post-design evidence |
|------|------------|----------------------|
| Pure local / least privilege | PASS | 只使用局域网 NSD、现有 ADB Session、SAF 与显式分享；不新增网络业务、权限或绕过能力 |
| ADB boundary / single session | PASS | 原始 Shell、`/proc`/`dumpsys` 解析、终止、Logcat 和 NSD 生命周期均位于 `:core:adb`；Feature 只见自有契约 |
| Sensitive data by default | PASS | 配对码和端点不持久化；日志仅在用户明确保存/分享后离开内存；临时分享文件有界且清理 |
| Controlled dependencies | PASS | 复用 `NsdManager`、SAF、现有 AndroidX；不新增第三方依赖，`apk-parser` 缩窄为标签用途 |
| Verifiable scoped delivery | PASS | 每个故事均定义正常、边界、取消、超时、断开、ROM 不支持与资源清理验证；任务已按 TDD 拆分，本轮仅修订设计工件、不修改业务代码 |
| Module direction | PASS | `:app`/`:feature:* → :core:*` 保持不变，不新增 Feature 间依赖 |
| Background execution exception | PASS | 只沿用已批准、用户主动、通知可见且最长 2 分钟的本机配对短时服务；局域网扫描和 Logcat 不转后台常驻 |

设计后未发现宪法例外，`Complexity Tracking` 不适用。`spake2-java 1.0.5` 的既有 GPL-3.0-or-later 对外发布阻断不因本功能或构建成功而解除。

## Design Decisions

### Wireless discovery and pairing lifecycle

- 保留 `_adb-tls-pairing._tcp` 与 `_adb-tls-connect._tcp` 的 `NsdManager` 发现，不接入工作站 `adb mdns` 命令或新增 mDNS 实现。
- 将发现 `mode` 和统一截止时间贯穿 `WirelessDiscoverySourceRequest → NsdDiscoveryRequest → policy/adapter`。局域网扫描保留 10 秒发现窗口；`LOCAL_PAIRING` 不再被适配器提前按 10 秒停止，而由唯一的 2 分钟配对窗口统一控制。
- 用户点击菜单后先建立窗口并立即进入前台短时服务/通知初态，再异步启动 NSD。重复点击复用当前窗口，不并发创建第二个扫描或通知。
- ADB mDNS/NSD 原始公告不提供可依赖的友好设备名。显示名称仅接受连接成功后由既有 overview 属性解析得到的设备型号（`VERIFIED_AFTER_CONNECTION`），并且必须与当前扫描条目的同一 `VerifiedWirelessDeviceId` 关联；服务实例名、GUID/序列样式字符串、主机名、IP 反查、地址或现有档案名称均不得冒充设备名。名称去除首尾 Unicode 空白后必须非空且不超过 80 个 Unicode 码点，并拒绝 ISO 控制字符和双向控制字符 U+061C、U+200E–U+200F、U+202A–U+202E、U+2066–U+2069；不合格时整体降级为 `UNAVAILABLE`，不得截断。未连接或型号不可用时同样省略名称行，不新增名称持久化。

### Application metadata downgrade

- 继续使用现有受限 APK 拉取和 `apk-parser` 解析应用标签，以保持跨 ROM 的应用名能力；删除 `allIcons` 调用、图标模型、字节负载、LRU 缓存、占位渲染及相关测试。
- `ApplicationMetadataUpdate` 仅传播标签状态和应用名。列表只展示应用名与包名；既有搜索、目标身份、确认及应用操作不变。
- 解析失败时显示“无法解析应用名”，永远保留由包管理命令得到的包名。

### Process snapshot and termination

- 一次刷新由同一个采样事务完成：基础进程清单/PPID、两次间隔 500 ms 的 CPU 计数、PSS 数据和应用清单共享同一 `sessionId` 与请求代次，总预算 5 秒。
- CPU 使用两个 `/proc/stat` 与 `/proc/<pid>/stat` 快照的增量，按设备总 CPU 容量归一化为 0–100%，保留一位小数；采样不足、计数回退、PID 退出或读取受限时为“计算中/未知”，不得以累计时间或零值替代。
- PSS 优先解析能力探测通过的 `dumpsys meminfo -c` 机器可读输出，以 KiB/1024 换算 MiB并保留一位小数；不可用时为“未知”，不得回退为 RSS。
- 应用关联要求同一用户/UID 下进程名与包名精确相等，或为唯一包名的 `包名:后缀`；共享 UID 或多候选不能唯一确认时显示“无法解析应用名”。
- 单进程终止仅对 PID > 1、`appId >= 10000` 且具有可复核 `startTimeTicks` 的应用进程开放，使用一次 `SIGTERM`，不自动升级为 `SIGKILL`。整个应用仅对唯一可靠包关联开放，复用受控的 `am force-stop --user` 路径；该范围不是逐 PID 终止，风险提示明确其会停止该应用的进程、服务与任务，并可能在用户再次显式启动前阻止后台恢复。
- “我了解”只授权当前一次请求。执行前重新读取 Session、PID、起始时间、UID、进程名及应用进程集合；执行后重新刷新并分类为已终止、部分终止、已退出、策略拒绝/不支持、结果未知、取消、超时或断开。

### Logcat extraction, save and share

- `:core:adb` 提供互斥的 `SNAPSHOT`/`CONTINUOUS` 捕获契约并租用现有 `LOGCAT` 独占操作；快照使用有界 dump，持续采集使用设备端起始游标和能力检测，避免把开始前日志交付为持续结果。
- 两种模式均设 10 MiB 内存上限；持续模式另设 10 分钟上限并显示耗时/字节数。到达任一边界立即关闭命令子流并返回停止原因，不自动分片、压缩或续采。
- 删除结构化 Logcat 解析、应用/进程关联、筛选、暂停和分析缓冲。捕获结果为本次 Session 的原始 UTF-8 文本字节与少量项目自有进度/停止元数据。
- 保存使用 `OpenDocumentTree` 和 `SafDocumentStore` 的临时写入后提交，默认自动改名避免覆盖；文件名包含 `snapshot` 或 `continuous` 及 UTC 时间。
- 分享前显示敏感内容提示；明确确认后才把本次结果写入 `cacheDir/logcat-share/` 并经非导出 `FileProvider` 以只读 URI 分享。状态按 `PREPARED → CHOOSER_OPENED → TARGET_SELECTED/CANCELLED/OUTCOME_UNKNOWN → EXPIRED/CLEANED` 记录：只有平台回调明确报告时才进入 `TARGET_SELECTED` 或 `CANCELLED`，chooser 打开/返回应用本身不是成功。明确取消立即删除；目标选择或结果不可观察时最多保留 1 小时以允许接收方读取，并在下次启动、新分享及清除数据时兜底清理；不声称接收方已读取，不授予持久 URI 权限。

### Diagnostic event retirement

- 删除 `AdbDiagnosticEvent`、`diagnosticEvents`、清除接口、100 条事件缓冲、写入点、首页/设备页入口和全部聚合测试。
- 保留当前操作的结构化结果和安全错误映射，但不建立替代历史、审计列表或跨操作聚合。
- 实现阶段同步更新 ADR 0002/0006 的 superseded 状态、权限矩阵与相关架构文档；历史正文保留，不改写为从未存在。

## Project Structure

### Documentation (this feature)

```text
specs/005-device-operations-refinement/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── wireless-and-device-display.md
│   ├── process-management.md
│   ├── logcat-extraction.md
│   └── feature-retirement.md
└── tasks.md                         # 已生成并按本轮分析整改同步
```

### Source Code (repository root)

```text
app/
└── src/main/                       # 通知/短时服务装配、FileProvider 与分享 Intent

core/
├── adb/src/main/                   # NSD、ADB Session、进程采样/终止、Logcat 捕获
├── adb/src/test/                   # 协议/解析/Session/取消/资源关闭契约测试
└── data/
    ├── src/main/                   # SAF 提交与分享缓存生命周期
    └── src/test/

feature/
├── devices/src/{main,test}/        # 配对通知状态、设备展示、移除诊断入口
├── apps/src/{main,test}/           # 标签/包名列表，移除图标
├── processes/src/{main,test}/      # 快照、刷新、范围选择、确认与结果
└── logcat/src/{main,test}/         # 模式、进度、停止、保存与分享

docs/
├── architecture/                   # 实现后同步现行架构
├── adr/                            # 标记被 v0.05 取代的历史决策
└── 权限矩阵.md
```

**Structure Decision**: 沿用现有多模块 Android 结构，不新增模块。平台入口及装配留在 `:app`；原始 ADB/NSD/命令能力留在 `:core:adb`；文件事务和临时分享文件生命周期留在 `:core:data`；各 Feature 只实现自己的 UI 状态与用户事件。

## Implementation Sequence

1. 先修改项目自有契约与纯逻辑解析器，并补足 Session/代次、模式化 NSD 截止时间、进程身份和 Logcat 停止原因测试。
2. 实现 `:core:adb` 的无线生命周期、标签-only 元数据、进程采样/终止和有界 Logcat 捕获，确保任何命令子流失败不关闭活跃 Session。
3. 实现 `:core:data` 的 SAF 目录写入复用和分享缓存清理，再由 `:app` 配置受限 `FileProvider` 与分享装配。
4. 最小化改造四个 Feature 的状态和控件；删除诊断历史、图标和 Logcat 分析的死代码及测试。
5. 同步权限矩阵、架构文档和 ADR 状态；最后执行全部模块测试、Debug 构建、API 30/33/36 自动化/模拟器兼容性，以及按现有三台设备角色矩阵完成的单用户人工验收；三类证据分别记录。

## Requirement Traceability

| Requirement range | Primary design / contract |
|-------------------|---------------------------|
| FR-001–FR-008 | `Wireless discovery and pairing lifecycle`；[wireless-and-device-display.md](contracts/wireless-and-device-display.md) |
| FR-009–FR-012 | `WirelessDeviceDisplayEntry`；[wireless-and-device-display.md](contracts/wireless-and-device-display.md) |
| FR-013–FR-014 | `Diagnostic event retirement`；[feature-retirement.md](contracts/feature-retirement.md) |
| FR-015–FR-017 | `Application metadata downgrade`；[feature-retirement.md](contracts/feature-retirement.md) |
| FR-018–FR-027 | `Process snapshot and termination`；[process-management.md](contracts/process-management.md) |
| FR-028–FR-035 | `Logcat extraction, save and share`；[logcat-extraction.md](contracts/logcat-extraction.md) |
| FR-036–FR-041 | Constitution gates、项目结构、[quickstart.md](quickstart.md) 的 Session/取消/回归/治理验证 |
