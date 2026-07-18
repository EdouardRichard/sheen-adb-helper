# Implementation Plan: Application Management

> **Feature Directory**: `002-application-management` | **Migrated**: 2026-07-19 | **Spec**: [spec.md](spec.md)
> **Status**: `archived-retrospective`
> **本文件为版本完成后的结构化回顾，不新增历史审批事实。**
> 来源：[原合并需求/SDD](../../docs/archive/releases/v0.02/需求文档.md) 的架构、接口、状态、数据、安全、生命周期和测试设计部分。

## Summary

在 `:core:adb` 增加当前用户第三方应用安全快照和单包命名操作，在新 `:feature:apps` 中提供列表、搜索/筛选、一次性确认和结构化结果；不新增持久化、Manifest 权限或外部依赖。

## Technical Context

| Item | Decision |
|---|---|
| Language/Version | Kotlin；仓库 Android toolchain |
| Primary Dependencies | 复用 Compose、Coroutines/Flow 与既有 ADB 适配 |
| Storage | N/A；应用快照、确认和结果仅内存 |
| Testing | JUnit、Feature/App 状态测试、Lint、Debug/Release、真机矩阵 |
| Target Platform | Android 11+ 被控端；`minSdk 30` 主控端 |
| Project Type | 多模块 Android 应用 |
| Performance Goals | 单次列表最多 20,000 项；搜索/筛选不重复查询 |
| Constraints | 当前用户、第三方包、单包、单操作、Session 绑定、后置验证 |

## Constitution Check

- PASS：所有 `pm`/`cmd package`/`am` 命令、参数校验和 Session 防护位于 `:core:adb`。
- PASS：`:feature:apps` 仅消费项目自有契约；`:core:data` 无新增职责。
- PASS：不新增权限、外部依赖、持久化或后台组件。
- PASS：危险操作每次确认，结果未知不伪装成功。
- BLOCKED FOR EXTERNAL DISTRIBUTION：ADR 0004 的既有许可证冲突仍独立存在。

## Project Structure

### Documentation (this feature)

```text
specs/002-application-management/
├── spec.md
├── plan.md
└── tasks.md
```

### Source Code

```text
app/
core/adb/
feature/apps/
```

**Structure Decision**: 新增独立 `:feature:apps`；命名 ADB 能力下沉 `:core:adb`；`:app` 只装配导航。

## 模块影响

- 新增 `:feature:apps`：Compose 页面、不可变 `AppsUiState`、ViewModel、内存搜索筛选与一次性确认。
- `:core:adb` 新增应用模型、当前用户第三方列表、单包强停、单包启用状态修改、命令/解析/错误与 Session 保护。
- `:app` 装配 AppsViewModel、注册“应用管理”目的地并处理离线/断开导航。
- `:core:data` 无新增职责；不修改 DataStore 格式。

## 接口与状态

- `AdbSessionManager` 提供 `listApplications`、`forceStopApplication`、`setApplicationEnabled` 命名能力。
- `ApplicationSnapshot` 携带 Session ID、当前用户、应用列表、不可用字段和降级原因。
- 修改结果区分后置验证成功、请求接受和结果未知；错误区分用户不可得、列表不支持、包消失、目标禁止、策略拒绝、验证失败和结果未知。
- UI 覆盖离线、加载、内容、空、降级、错误、修改中、取消和结果未知；Session 变化清空全部旧状态。

## 命令、数据与安全

- `pm`/`cmd package`/`am` 等命令只在 `:core:adb`；当前用户和包名参数均由核心层校验并限定。
- 修改目标必须来自同 Session 最近安全快照，非系统且状态明确；本机拒绝自身。
- 列表、用户 ID、搜索筛选、确认和操作结果仅内存；诊断不含包名、用户 ID、命令或输出。
- 不新增外部依赖、Manifest 权限、组件、导出或持久化。

## 并发与清理

- 每个 Session 同时只执行一个应用管理高层操作；加载/修改时其他入口禁用并提供取消。
- 页面离开取消列表读取并清确认；Session 变化取消 Feature 作业。
- 命令发出后超时、取消或掉线返回结果未知；必要时关闭 Session 并由管理器发布断开。

## 测试策略

- 核心覆盖当前用户/列表解析、包名和命令边界、容量、Session、成功/拒绝/消失/不一致、超时/取消/掉线、诊断脱敏。
- Feature/App 覆盖搜索筛选、确认策略、系统/自身/未知目标、Session 清空、菜单与离线导航。
- 静态检查覆盖命令边界、Manifest、依赖和持久化；真机矩阵覆盖 ROM、用户、连接、危险操作与 v0.01 回归。

## 有效边界

- 默认分发策略见 [ADR 0005](../../docs/adr/0005-distribution-strategy.md)。
- 许可证冲突见 [ADR 0004](../../docs/adr/0004-spake2-license-compliance.md)，不因本设计无新依赖而解除。
- [ADR 0006](../../docs/adr/0006-logcat-recent-history-follow-mode.md) 是 v0.03 proposed 设计，不属于本版本实现。

## Complexity Tracking

无宪法例外；新增 Feature 是既有模块化规则的直接应用。历史证据缺口见 [verification](../../docs/archive/releases/v0.02/verification.md)。
