# Implementation Plan: Wireless ADB Foundation

> **Feature Directory**: `001-foundation` | **Migrated**: 2026-07-19 | **Spec**: [spec.md](spec.md)
> **Status**: `archived-retrospective`
> **本文件为版本完成后的回顾性设计重建，不代表开发前曾存在或被审批。**
> 依据：最终源码/测试、Git 历史、[原需求](../../docs/archive/releases/v0.01/需求文档.md)、ADR 0001～0004 与[结项材料](../../docs/archive/releases/v0.01/结项记录.md)。

## Summary

以 `:core:adb` 单 Session 能力为中心，交付连接/配对、设备诊断、Shell、只读进程和前台 Logcat，并由独立 Feature 通过项目自有契约消费；本地档案与 SAF 输出由 `:core:data` 管理。

## Technical Context

| Item | Decision |
|---|---|
| Language/Version | Kotlin；JVM/Android toolchain 以仓库版本目录为准 |
| Primary Dependencies | Jetpack Compose、Coroutines/Flow、Kadb 2.1.1、DataStore |
| Storage | DataStore 最小档案元数据；ADB 身份由 Keystore 保护；敏感输出仅内存 |
| Testing | JUnit、Android/Compose 测试、Gradle Lint/Build、真机矩阵 |
| Target Platform | Android 11+；`minSdk 30`，`compileSdk/targetSdk 36` |
| Project Type | 多模块 Android 应用 |
| Constraints | 纯本地、单 Session、可取消/超时/清理、敏感数据不落盘 |

## Constitution Check

- PASS：ADB 协议、命令、身份和 Session 仅位于 `:core:adb`。
- PASS：产品纯本地，Manifest 产品权限仅 `INTERNET`，文件输出使用 SAF。
- PASS：Feature 只依赖 Core，持续流和缓冲有界。
- BLOCKED FOR EXTERNAL DISTRIBUTION：ADR 0004 的传递依赖许可证冲突未解决；不影响历史功能/真机结论。

## Project Structure

### Documentation (this feature)

```text
specs/001-foundation/
├── spec.md
├── plan.md
└── tasks.md
```

### Source Code

```text
app/
core/{adb,data,ui}/
feature/{devices,overview,shell,processes,logcat,settings}/
```

**Structure Decision**: 保持 `:app` 装配、独立 Feature、共享 Core 的多模块 Android 结构；协议能力不进入 UI。

## 设计概览

- `:app` 负责 Application 容器、单导航宿主、页面装配和全局连接状态。
- `:core:adb` 负责端点、协议/TLS/配对、主机身份、单 Session、命名能力、流、错误和脱敏诊断。
- `:core:data` 负责 DataStore 设备档案、最小元数据、SAF 文本导出和清理。
- `:core:ui` 负责 Compose 主题与通用设计令牌。
- 设备、概览、Shell、进程、Logcat、设置分别位于独立 Feature；Feature 只依赖 core。

## 接口与状态

- `AdbSessionManager` 公开连接/配对/断开、Shell、概览、动态指标、进程、Logcat 和身份清除的项目自有契约。
- 连接状态由管理器独占发布；每个操作绑定 Session ID，旧 Session 的异步结果由核心或 ViewModel 丢弃。
- 页面使用不可变 UI State 和显式事件，覆盖离线、加载、内容、空、错误与取消。

## 数据与安全

- ADB RSA 私钥以 Android Keystore AES-GCM 密钥包装，密文位于不可备份私有目录；配对码仅短时内存使用。
- DataStore 只保存设备档案元数据；Shell、进程、Logcat、概览快照和诊断事件不持久化。
- Logcat 只由用户在前台启动，导出使用 SAF；Manifest 产品权限仅 `INTERNET`。
- 第三方协议类型和业务命令不进入 Feature/UI；错误详情采用结构化代码和脱敏目标。

## 资源策略

- 候选连接与活跃 Session 所有权分离；新连接先关闭旧 Session。
- 连接、配对、Shell 与 Logcat 使用协程取消、有限超时和 `finally` 清理。
- Shell/Logcat 使用有界内存缓冲；页面离开、断开或 Session 切换时停止相关工作。

## 测试策略（由最终状态重建）

- 核心：端点解析、错误映射、Session 切换、超时、取消、资源清理、诊断脱敏。
- 数据：档案编码/损坏拒绝、创建/更新/排序和清理语义。
- Feature/App：菜单与状态策略、Shell/Logcat 容量和生命周期。
- 真机：Android 11+ 配对、HyperOS 3/Android 16、环回、ROM 降级、SAF 和清除数据。

## 追溯来源

- 模块/数据/流设计：[ADR 0003](../../docs/adr/0003-v001-modules-data-and-streaming.md)。
- ADB 库/身份设计：[ADR 0001](../../docs/adr/0001-adb-protocol-library.md)。
- 诊断设计：[ADR 0002](../../docs/adr/0002-in-memory-diagnostic-events.md)。
- 许可证阻断：[ADR 0004](../../docs/adr/0004-spake2-license-compliance.md)。

## Complexity Tracking

无宪法例外。Kadb 许可证冲突是独立发布阻断，不作为偏离宪法的理由。历史证据缺口见 [verification](../../docs/archive/releases/v0.01/verification.md)。
