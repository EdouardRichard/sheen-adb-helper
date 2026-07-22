# Implementation Plan: v0.03 设备文件与日志诊断

**Branch**: `main` | **Date**: 2026-07-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-device-file-diagnostics/spec.md`

## Summary

v0.03 新增一个独立的 `:feature:files`，覆盖被控端文件浏览、单文件双向传输和完整 APK 提取；同时优化既有 `:feature:logcat` 的最近历史与持续跟随。远端文件数据使用 Kadb 2.1.1 已有的 ADB Sync v1/v2 流，所有协议、命令、路径校验、Session 绑定和长任务互斥继续只位于 `:core:adb`。主控端文件访问由 `:core:data` 的 SAF 网关处理：上传来源使用通用 OpenableColumns/ContentResolver 语义，下载目标保持树根与树内子文档 URI 身份；不新增 Manifest 权限、外部依赖或持久化格式。文件任务由 Activity 级 ViewModel 持有以支持应用前台跨页面继续，进入后台、取消、断开或 Session 切换时关闭子流并清理暂存结果。

## Technical Context

**Language/Version**: Kotlin 2.3.20，JVM/Java 17

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01、AndroidX Activity Compose 1.13.0、Lifecycle 2.10.0、Coroutines 1.10.2、Kadb 2.1.1、Okio 3.17.0；不新增第三方依赖

**Storage**: 用户明确选择的 SAF 文档/目录树；任务状态、远端目录、APK 组成和 Logcat 仅驻留内存；不新增 DataStore 字段或自动落盘

**Testing**: Gradle + TestNG JVM 单元测试、模块测试、Debug 构建、Android lint、merged Manifest 检查、受控 SAF provider/真机矩阵；真机复测前必须重新安装本次构建制品，并核对 APK 生成时间不晚于设备端应用安装更新时间，防止旧包结果被误判为当前实现回归

**Target Platform**: 主控端 Android 11+（minSdk 30），compileSdk/targetSdk 36；被控端为用户已授权的 ADB 设备，不以 Android 11 为下限。文件浏览和双向传输至少兼容通过 `IP:5555` 提供标准 Sync v1 的 Android 10 及更早设备；APK 与 Logcat 按实际 Shell/Logcat 能力探测并明确报告限制

**Project Type**: 单 Activity、多 Gradle 模块的本地 Android 应用

**Performance Goals**: 不超过 1,000 项的目录浏览 95% 在 3 秒内完成或返回明确错误；Logcat 95% 在 3 秒内进入历史、空历史或降级状态；传输至少覆盖 2 GiB 单文件且使用固定分块、`Long` 计数

**Constraints**: 纯本地、单活跃 ADB Session、单文件任务、文件任务与 Logcat 互斥、应用后台取消、无固定总传输超时但 30 秒无进展超时、无 Root/无障碍/存储权限/后台服务、诊断不得包含路径/包名/URI/文件内容/Logcat；v0.03 新增能力不得以 Android 11 作为被控端版本门槛，必须按协议/命令实际能力协商

**Scale/Scope**: 一名本地用户、一个活跃 Session、一个活动文件或 APK 任务；单目录安全上限 10,000 项；APK 组成安全上限 256；Logcat 最近历史上限 5 分钟/512 条/1 MiB，单解码行 64 KiB，既有 Feature 缓冲保持 10,000 行或 4 MiB、可见 100 条

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-design gate

| Principle | Status | Plan evidence |
|---|---|---|
| Pure Local and Least Privilege | PASS | 仅 ADB 与用户选择的 SAF URI；无账号、后端、遥测、Root、后台服务或新权限。 |
| ADB Boundary and Single Session | PASS | Sync、Shell、Logcat、路径与 Session 租约均限定在 `:core:adb`；Feature 只依赖项目自有契约。 |
| Sensitive Data by Default | PASS | 文件内容、真实路径、包名、URI 和 Logcat 只在任务内存/用户目标中；诊断只记录白名单阶段与技术代码。 |
| Controlled Dependencies and Distribution | PASS WITH EXISTING BLOCKER | 不新增依赖；ADR 0004 的 `spake2-java` 许可证阻断继续存在，功能完成不得被表述为发布合规通过。 |
| Verifiable, Scoped Delivery | PASS | 计划覆盖正常、边界、取消、超时、Session、清理、ROM 降级、自动化和真机证据。 |
| Engineering Baseline | PASS | Kotlin/Compose/Coroutines、API 30/36、Feature→Core 依赖和不可变 UI State 保持。 |
| ADR 0006 review | PLAN ACTION | 计划开始时为 `proposed`，仅允许研究；Phase 1 完成后以本计划的数值、降级和回滚规则更新为 `accepted`，在此之前禁止实现。 |

### Post-design gate

| Principle | Status | Design result |
|---|---|---|
| Pure Local and Least Privilege | PASS | SAF `OpenDocument`/`OpenDocumentTree` 覆盖主控端输入输出，权限矩阵同步且 Manifest 仍只有 `INTERNET`。 |
| ADB Boundary and Single Session | PASS | `AdbSessionManager`/协议适配器拥有 Sync 与长任务租约；文件浏览不占租约，传输/APK/Logcat 原子互斥。 |
| Sensitive Data by Default | PASS | 数据模型和契约禁止在诊断、测试证据及构建日志中记录真实数据；暂存项按任务清理。 |
| Controlled Dependencies and Distribution | PASS WITH EXISTING BLOCKER | 仅复用已固定依赖；发布合规仍受 ADR 0004 阻断并在 quickstart 单独报告。 |
| Verifiable, Scoped Delivery | PASS | quickstart 区分自动化、构建、Manifest、受控 provider、真机/ROM 和发布合规证据。 |
| Engineering Baseline | PASS | 新增 `:feature:files`，扩展 `:core:adb`/`:core:data`；无 Feature 间依赖，`:app` 只装配、导航和生命周期转发。 |
| ADR 0006 review | PASS | ADR 0006 更新为 `accepted` 的 v0.03 设计决定；实现状态仍明确为未实现。 |

## Project Structure

### Documentation (this feature)

```text
specs/003-device-file-diagnostics/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── adb-file-logcat-contract.md
│   └── saf-task-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                         # 由 /speckit-tasks 后续创建
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts
└── src/
    ├── main/kotlin/com/sheen/adbhelper/
    │   ├── MainActivity.kt          # Activity 级 FilesViewModel 与后台取消信号
    │   ├── SheenApplication.kt      # 共享 core 服务装配
    │   └── SheenApp.kt              # 文件入口、全局任务摘要与导航
    └── test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt

core/adb/src/
├── main/kotlin/com/sheen/adb/core/
│   ├── AdbSessionManager.kt         # 项目自有文件、APK、Logcat 契约
│   ├── AdbModels.kt                 # 远端条目、任务租约、结构化事件/错误
│   └── internal/
│       ├── AdbProtocolAdapter.kt    # closeable Sync 子流抽象
│       ├── KadbProtocolClientFactory.kt
│       ├── DefaultAdbSessionManager.kt
│       ├── RemoteFileCapabilities.kt
│       ├── ApkExtractionCapabilities.kt
│       └── LogcatCapabilities.kt
└── test/kotlin/com/sheen/adb/core/internal/
    ├── RemoteFileSessionManagerTest.kt
    ├── RemotePathPolicyTest.kt
    ├── ApkExtractionParsersTest.kt
    ├── LogcatHandoffTest.kt
    └── AdbExclusiveOperationCoordinatorTest.kt

core/data/src/
├── main/kotlin/com/sheen/adb/data/
│   ├── SafDocumentStore.kt          # SAF 元数据、流、暂存、提交和回滚
│   └── TemporaryDataCleaner.kt      # 保持现有清理范围并覆盖本任务临时项
└── test/kotlin/com/sheen/adb/data/SafDocumentPolicyTest.kt

feature/files/
├── build.gradle.kts
└── src/
    ├── main/AndroidManifest.xml
    ├── main/kotlin/com/sheen/adb/feature/files/
    │   ├── FilesModels.kt
    │   ├── FilesViewModel.kt
    │   ├── FilesScreen.kt
    │   └── FileTaskStatus.kt
    └── test/kotlin/com/sheen/adb/feature/files/
        ├── FilesReducerTest.kt
        ├── FileConflictPolicyTest.kt
        └── FileTaskLifecycleTest.kt

feature/logcat/src/
├── main/kotlin/com/sheen/adb/feature/logcat/
│   ├── LogcatViewModel.kt
│   ├── LogcatScreen.kt
│   └── LogcatBuffer.kt
└── test/kotlin/com/sheen/adb/feature/logcat/
    ├── LogcatBufferTest.kt
    └── LogcatViewModelTest.kt
```

**Structure Decision**: 采用一个新的 `:feature:files` 聚合文件浏览、双向传输和 APK 提取，使用独立的当前用户只读 APK 候选快照，避免复用第三方应用管理策略、修改历史 `:feature:apps` 语义或产生 Feature 间依赖。Activity 级 `FilesViewModel` 负责跨页面任务状态；`:core:data` 仅负责主控端 SAF；`:core:adb` 负责所有远端能力、原始命令、协议、Session 和互斥。

## Phase 0: Research Decisions

- 采用 Kadb 原生 ADB Sync v1/v2 `Source`/`Sink`、64 KiB 分块、v2 64 位 stat/list；不以 Shell `cat/dd` 传输文件。
- Sync v1/v2 列表在 Core 边界过滤 adbd 返回的 `.`/`..` 协议目录项；共享存储以 LIST 成功而非 v1 `lstat` 的目录类型作为可浏览判据，以兼容旧设备常见的 `/sdcard` 链接。
- Feature 将面包屑渲染为无按钮内边距的连续 `/目录名/子目录名` 片段，同时保留各祖先段的导航动作。
- 上传与下载均先写任务专属暂存目标；提交前复核 Session、来源和冲突，失败/取消执行确定性清理与可报告回滚。
- 远端摘要、rename 与清理 Shell 调用统一进入 `:core:adb` 注入的 I/O dispatcher；包括清理在内的所有异常都在 Core 边界转换为结构化结果，不允许逃逸到主线程。
- Sync v1 首次传输仅在“远端子流关闭且累计字节为 0”时重新打开 Sync 子流并重试一次；任何已产生进度、本地流失败、Sync FAIL、取消或超时均不重试。Core 使用明确的本地来源/目标流异常，避免被通用 `IOException` 映射为会话断开。
- `ls_v2`/`stat_v2` 与 `sendrecv_v2` 分别决定列表/元数据版本和文件收发版本；协议适配器必须保存两种版本，传输兼容策略只读取 `sendrecv_v2` 对应版本。
- 主控端上传用 `OpenDocument`，下载和 APK 提取用 `OpenDocumentTree`；provider 缺少安全提交能力时要求更换位置。
- Activity 级 FilesViewModel 支持应用前台跨页面继续；`onStop && !isChangingConfigurations` 取消活动文件任务。
- Logcat 默认使用设备时间游标的两阶段历史/follow，采用 5 分钟、512 条、1 MiB、64 KiB 单行、2 秒历史/3 秒初始化边界；能力不足时等待用户选择 follow-only 或 history-only。
- 具体论证、替代方案与权威来源见 [research.md](research.md)。

## Phase 1: Design Decisions

- 领域实体、状态机、验证规则和不持久化边界见 [data-model.md](data-model.md)。
- ADB Sync、远端路径、APK 组成、Logcat 事件及租约契约见 [contracts/adb-file-logcat-contract.md](contracts/adb-file-logcat-contract.md)。
- SAF 暂存/提交、冲突规则、跨页面任务和 UI 状态契约见 [contracts/saf-task-contract.md](contracts/saf-task-contract.md)。
- 自动化、构建、Manifest、受控 provider 与真机/ROM 验收流程见 [quickstart.md](quickstart.md)。

## Complexity Tracking

无宪法违规需要豁免。新增一个 Feature 模块是既有模块化原则的直接应用；未新增核心模块、第三方依赖、权限、服务或持久化格式。
