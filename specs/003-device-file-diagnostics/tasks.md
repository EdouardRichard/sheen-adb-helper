# Tasks: v0.03 设备文件与日志诊断

**Input**: `specs/003-device-file-diagnostics/` 下的 `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/` 与 `quickstart.md`

**Tests**: 本功能严格采用 TDD。每一组业务实现前必须先完成对应 RED 任务：编写测试、实际运行指定测试并确认因目标能力尚未实现而失败；RED 任务不得编写业务代码。随后才可执行对应 GREEN 任务，以最小业务实现使该测试通过。

**Organization**: 任务按用户故事优先级组织；`[P]` 仅表示在其显式依赖已完成后，可与其他不修改相同文件且无未完成依赖的任务并行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行（不同文件、无直接依赖或共享写入冲突）
- **[Story]**: `US1`～`US4` 对应 `spec.md` 中的用户故事
- 每项任务均包含准确文件路径；GREEN 任务显式标出其 RED 前置任务

---

## Phase 1: Setup（共享工程结构）

**Purpose**: 建立 `:feature:files` 模块及仅用于装配的依赖关系；本阶段不编写业务代码。

- [✓] T001 在 `settings.gradle.kts` 注册新的 `:feature:files` 模块
- [✓] T002 [P] 按既有 Compose Feature 约束创建 `feature/files/build.gradle.kts`，仅复用现有项目模块和 Version Catalog 依赖
- [✓] T003 [P] 创建无权限声明的 `feature/files/src/main/AndroidManifest.xml`
- [✓] T004 在 `app/build.gradle.kts` 添加对 `project(":feature:files")` 的装配依赖，并运行 `./gradlew.bat :feature:files:testDebugUnitTest` 确认空模块可进入测试生命周期

**Checkpoint**: `:feature:files` 可被 Gradle 识别，且没有新增外部依赖或 Manifest 权限。

---

## Phase 2: Foundational（阻塞性基础能力）

**Purpose**: 建立所有长任务共享的 Session 互斥、结构化脱敏和文件任务状态契约。

**⚠️ CRITICAL**: 本阶段完成前不得开始任何用户故事的 GREEN 实现。

- [✓] T005 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/AdbExclusiveOperationCoordinatorTest.kt` 编写 FILE_TRANSFER、APK_EXTRACTION、LOGCAT 原子竞争、浏览不占租约、Session 绑定和幂等释放测试；运行 `./gradlew.bat :core:adb:testDebugUnitTest --tests "*AdbExclusiveOperationCoordinatorTest"` 并确认测试因租约能力缺失而失败（RED，不得编写业务代码）
- [✓] T006 [P] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现 Session 级独占操作租约、结构化忙碌错误及全终态幂等释放，使 T005 通过（GREEN，依赖 T005）
- [✓] T007 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/DiagnosticRedactorTest.kt` 增加远端路径、SAF URI、真实包名、APK 路径、摘要、Shell 输出和 Logcat 内容不得进入诊断信息的测试；运行对应 `:core:adb:testDebugUnitTest` 过滤测试并确认新增断言失败（RED，不得编写业务代码）
- [✓] T008 [P] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/DiagnosticRedactor.kt` 增加 v0.03 结构化错误字段的白名单脱敏规则，使 T007 通过且仅保留错误类别、阶段和匿名计数（GREEN，依赖 T007）
- [✓] T009 [P] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt` 编写 Session 身份、任务类型、准备/进行/成功/失败/取消终态、关闭终态和 Session 清理的基础状态测试；运行 `./gradlew.bat :feature:files:testDebugUnitTest --tests "*FileTaskLifecycleTest"` 并确认因模型缺失而失败（RED，不得编写业务代码）
- [✓] T010 [P] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt` 和 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FileTaskStatus.kt` 实现不可变基础 UI/任务状态与结构化错误展示模型，使 T009 通过（GREEN，依赖 T009）

**Checkpoint**: 所有故事可复用同一租约与状态契约，敏感内容不会进入诊断；此后各故事可在遵守文件冲突约束的前提下并行开发。

---

## Phase 3: User Story 1 - 浏览被控设备文件（Priority: P1）🎯 MVP

**Goal**: 默认进入共享存储，并允许通过根目录入口和面包屑浏览当前 ADB 身份可访问的路径，准确区分文件、目录、符号链接及错误状态。

**Independent Test**: 连接含普通目录、空目录、无权限目录、特殊名称和不同链接类型的设备，仅通过文件浏览入口完成导航、信息查看与选择；不依赖传输、APK 提取或 Logcat。

### RED → GREEN: Core 路径策略

- [✓] T011 [P] [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemotePathPolicyTest.kt` 编写共享存储候选顺序、绝对路径/NUL/1,024 UTF-8 字节限制、安全子路径、10,000 项上限及符号链接失效/无权限/祖先循环测试；运行过滤后的 `:core:adb:testDebugUnitTest` 并确认因路径策略缺失而失败（RED，不得编写业务代码）
- [✓] T012 [P] [US1] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/RemoteFileCapabilities.kt` 实现路径验证、共享存储探测、目录容量守卫和 v2 `(dev, ino)`/v1 受控回退链接策略，使 T011 通过（GREEN，依赖 T011）

### RED → GREEN: Sync 列表与元数据

- [✓] T013 [P] [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/KadbRemoteFileProtocolTest.kt` 编写 Sync v1/v2 list、lstat/stat、mode/dev/inode、空目录、取消关闭子流、短超时和特殊名称不经 Shell 解析的适配器测试；运行过滤后的 `:core:adb:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [✓] T014 [P] [US1] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/KadbProtocolClientFactory.kt` 封装 Kadb Sync v1/v2 列表与元数据流并确保取消/超时关闭子流，使 T013 通过（GREEN，依赖 T013）

### RED → GREEN: Session 绑定的目录浏览

- [✓] T015 [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 编写默认目录、根目录、面包屑导航、加载/空/拒绝/断开/取消、快照 Session 隔离和超限不返回部分列表测试；运行过滤后的 `:core:adb:testDebugUnitTest` 并确认失败（RED，不得编写业务代码；依赖 T011、T013）
- [✓] T016 [US1] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 暴露并实现 Session 绑定的目录快照、路径解析与结构化错误，且浏览不占独占租约，使 T015 通过（GREEN，依赖 T012、T014、T015）

### RED → GREEN: 文件浏览状态与 UI

- [✓] T017 [P] [US1] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesReducerTest.kt` 编写初始共享存储、根目录入口、面包屑、刷新、选择、链接标识以及加载/空/失败/断开/取消状态归约测试；运行过滤后的 `:feature:files:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [✓] T018 [P] [US1] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt` 实现浏览动作、条目展示和纯状态归约，使 T017 通过（GREEN，依赖 T017）
- [✓] T019 [US1] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesReducerTest.kt` 增加 `FilesViewModel` 首次加载、刷新竞争、旧 Session 结果丢弃、取消和错误映射测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码；依赖 T017）
- [✓] T020 [US1] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt` 实现只依赖 Core 契约的浏览编排与 Session 结果守卫，使 T019 通过（GREEN，依赖 T016、T018、T019）
- [✓] T021 [US1] 在 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 编写文件入口存在、目的地可导航、既有目的地语义不变且 App 不直接调用 ADB/SAF 的装配策略测试；运行过滤后的 `:app:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [✓] T022 [US1] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt` 实现浏览页面，并在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 添加文件目的地和导航装配，使 T021 与 US1 的 Feature 测试通过（GREEN，依赖 T020、T021）
- [✓] T023 [US1] 按 `specs/003-device-file-diagnostics/quickstart.md` 的文件浏览场景运行 `:core:adb:testDebugUnitTest`、`:feature:files:testDebugUnitTest` 和 `:app:testDebugUnitTest`，确认 US1 全部通过且可作为独立 MVP 验收

**Checkpoint**: 用户可独立完成远端文件浏览；MVP 可在此停止并验证。

---

## Phase 4: User Story 2 - 双向传输文件（Priority: P2）

**Goal**: 用户可通过 SAF 明确选择主控端来源/目标，执行单文件上传或下载，并在冲突、失败、取消、后台及 Session 变化时获得安全且可恢复的结果；被控端兼容标准 Sync v1/v2，不以 Android 11 为版本门槛。

**Independent Test**: 分别上传和下载空文件、普通文件及至少 2 GiB 文件，覆盖目标不存在、三种冲突选择、空间不足、30 秒无进展、取消、断开、后台与 Session 切换；所有成功结果逐字节一致，并以 Sync v1 测试替身覆盖 Android 11 以下被控端协议路径。

### RED → GREEN: 二进制流与完整性

- [✓] T024 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/KadbRemoteFileProtocolTest.kt` 增加 Sync v1/v2 接收/发送、固定最大 64 KiB 分块、`Long` 跨越 `Int.MAX_VALUE`、未知长度来源、流式进度及关闭测试；运行过滤后的 `:core:adb:testDebugUnitTest` 并确认新增测试失败（RED，不得编写业务代码）
- [✓] T025 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/KadbProtocolClientFactory.kt` 实现有界内存的二进制 Sync 读写与 `Long` 进度，使 T024 通过（GREEN，依赖 T024）
- [✓] T026 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 增加来源前后 size/mtime/identity 复核、SHA-256 内存回退、来源变化、摘要不可用、30 秒无进展、取消后 3 秒旧 Session 兜底及租约释放测试；运行过滤后的模块测试并确认失败（RED，不得编写业务代码；依赖 T024）
- [✓] T027 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/RemoteFileCapabilities.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现下载/上传流、完整性复核、无进展看门狗、子流关闭与 Session 兜底，使 T026 通过（GREEN，依赖 T006、T025、T026）

### RED → GREEN: 远端暂存与冲突提交

- [✓] T028 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 增加远端目标探测、同目录暂存、覆盖备份、自动重命名、未明确选择不覆盖、提交回滚及清理失败路径测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码）
- [✓] T029 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/RemoteFileCapabilities.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现远端暂存、显式冲突决策、安全提交、回滚和受影响目标错误，使 T028 通过（GREEN，依赖 T027、T028）

### RED → GREEN: 主控端 SAF

- [✓] T030 [P] [US2] 在 `core/data/src/test/kotlin/com/sheen/adb/data/SafDocumentPolicyTest.kt` 编写 `OpenDocument`/`OpenDocumentTree` 元数据、provider 能力、可用空间、同名冲突、隐藏暂存、备份提交、自动重命名、取消、回滚和清理失败测试；运行 `./gradlew.bat :core:data:testDebugUnitTest --tests "*SafDocumentPolicyTest"` 并确认因 SAF 网关缺失而失败（RED，不得编写业务代码）
- [✓] T031 [P] [US2] 在 `core/data/src/main/kotlin/com/sheen/adb/data/SafDocumentStore.kt` 实现无持久扫描的 SAF 来源/目标网关、暂存写入及 provider 能力驱动的提交回滚，并在 `core/data/src/main/kotlin/com/sheen/adb/data/TemporaryDataCleaner.kt` 接入确定性清理，使 T030 通过（GREEN，依赖 T030）

### RED → GREEN: 冲突、任务生命周期与 UI

- [✓] T032 [P] [US2] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileConflictPolicyTest.kt` 编写覆盖、自动重命名、取消、无明确选择默认取消和原目标不变的状态机测试；运行过滤后的 `:feature:files:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [✓] T033 [P] [US2] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt` 实现显式冲突决策与待确认状态，使 T032 通过（GREEN，依赖 T032）
- [✓] T034 [US2] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt` 增加单任务限制、进度、取消、跨页面继续、全局摘要、后台取消、配置变化保留、断开/切换 Session 清理及与 Logcat 互斥测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码）
- [✓] T035 [US2] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt` 实现上传/下载编排、SAF/ADB 双端关闭、Activity 级任务状态和全终态清理，使 T034 通过（GREEN，依赖 T027、T029、T031、T033、T034）
- [✓] T036 [US2] 在 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 增加 Activity 级 `FilesViewModel` 唯一所有者、跨页面摘要、真实 `onStop` 取消和配置变化不取消的测试；运行过滤后的 `:app:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [✓] T037 [US2] 在 `app/src/main/kotlin/com/sheen/adbhelper/MainActivity.kt`、`app/src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt` 和 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 装配 Activity 级文件任务所有权与生命周期转发，使 T036 通过（GREEN，依赖 T035、T036）
- [✓] T038 [US2] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt` 增加来源/目标选择、冲突对话框、进度、取消、终态关闭和清理失败可见性的 UI 状态契约测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码）
- [✓] T039 [US2] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt` 实现 SAF 选择触发、上传/下载入口、冲突选择、任务进度/取消及可重新找到的全局摘要，使 T038 通过（GREEN，依赖 T035、T037、T038）
- [✓] T040 [US2] 按 `specs/003-device-file-diagnostics/quickstart.md` 的双向传输场景运行 `:core:adb:testDebugUnitTest`、`:core:data:testDebugUnitTest`、`:feature:files:testDebugUnitTest` 和 `:app:testDebugUnitTest`，确认 US2 独立测试及 Sync v1/v2 被控端兼容测试全部通过

**Checkpoint**: 单文件上传/下载在冲突、失败和生命周期变化下均保持目标安全，且不依赖 APK 或 Logcat。

---

## Phase 5: User Story 3 - 提取完整应用安装包（Priority: P3）

**Goal**: 从独立的当前用户只读候选快照选择应用，将 base APK 与全部 split APK 作为一个整体提取到主控端，不改变被控应用或历史应用管理语义。

**Independent Test**: 分别提取单 APK 与拆分 APK 测试应用，对照设备报告验证组成和内容；任一组成缺失、变化或保存失败时整体不得成功。

### RED → GREEN: APK 候选与组成解析

- [ ] T041 [P] [US3] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApkExtractionParsersTest.kt` 编写当前用户只读候选（含可读系统包）、20,000 项上限、严格 `pm path` 解析、1..256 个唯一绝对 `.apk` 路径、base/split 顺序、重复/缺失/不可读拒绝测试；运行过滤后的 `:core:adb:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [ ] T042 [P] [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/ApkExtractionCapabilities.kt` 实现独立候选快照和受控 APK 组成解析，不复用或放宽历史 `listApplications()`，使 T041 通过（GREEN，依赖 T041）

### RED → GREEN: 整体提取事务

- [ ] T043 [US3] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 增加同 Session 组成复核、逐组件拉取、任一失败整体失败、路径集合变化、租约竞争、取消和暂存集合清理测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码；依赖 T041）
- [ ] T044 [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/ApkExtractionCapabilities.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现 APK 候选获取、全组成复核与整体提取事务，使 T043 通过（GREEN，依赖 T006、T027、T042、T043）

### RED → GREEN: APK 提取状态与 UI

- [ ] T045 [US3] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt` 增加候选加载、单 APK 命名、多 APK 集合、组成进度、整体失败、取消、跨页面摘要和包名/路径不进入摘要的测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码）
- [ ] T046 [US3] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt` 和 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt` 实现 APK 候选与整体提取编排，使 T045 通过（GREEN，依赖 T031、T044、T045）
- [ ] T047 [US3] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesReducerTest.kt` 增加 APK 候选选择、单文件/文件集合结果、组成不完整和结构化错误的页面状态测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码）
- [ ] T048 [US3] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt` 实现 APK 候选入口、组成说明、目标目录选择、整体进度/取消及完整/不完整结果展示，使 T047 通过（GREEN，依赖 T046、T047）
- [ ] T049 [US3] 按 `specs/003-device-file-diagnostics/quickstart.md` 的 APK 场景运行 `:core:adb:testDebugUnitTest`、`:core:data:testDebugUnitTest`、`:feature:files:testDebugUnitTest` 和既有 `:feature:apps:testDebugUnitTest`，确认 US3 通过且历史应用管理范围和修改语义未扩大

**Checkpoint**: 单 APK 与 split APK 均可整体提取，任一缺失均不宣称成功，历史应用管理保持不变。

---

## Phase 6: User Story 4 - 最近历史与持续 Logcat（Priority: P4）

**Goal**: 用户主动开始后，在 3 秒初始化预算内获得最多最近 5 分钟的有界历史并持续跟随；交接精确去重，能力不足时由用户明确选择有限降级。

**Independent Test**: 用唯一合成标记覆盖开始前历史、交接边界和开始后日志，验证边界标记恰好一次、后续标记至少一次，并覆盖空历史、显式降级、停止、离页、断开和 Session 切换。

### RED → GREEN: Core 两阶段流与交接

- [ ] T050 [P] [US4] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LogcatHandoffTest.kt` 编写严格 epoch 游标、300 秒/512 条/1 MiB/64 KiB 边界、2 秒探测与历史、3 秒初始化、空历史、exact-record fingerprint 多重集计数去重及窗口清空测试；运行过滤后的 `:core:adb:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [ ] T051 [P] [US4] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/LogcatCapabilities.kt` 实现能力探测、有界历史解析、结构化事件和交接多重集，使 T050 通过（GREEN，依赖 T050）
- [ ] T052 [US4] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LogcatHandoffTest.kt` 增加默认历史+follow、`CapabilityLimited` 后释放租约、显式 `FOLLOW_ONLY`/`HISTORY_ONLY`、无空闲 follow 超时、用户停止/离页/断开/切换 Session 关闭及与文件任务互斥测试；运行过滤后的模块测试并确认新增测试失败（RED，不得编写业务代码）
- [ ] T053 [US4] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt` 和 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现两阶段采集、显式有限降级、租约和资源生命周期，使 T052 通过（GREEN，依赖 T006、T051、T052）

### RED → GREEN: Feature 缓冲、状态与 UI

- [ ] T054 [P] [US4] 在 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatBufferTest.kt` 增加结构化事件到既有 threadtime 展示、10,000 行/4 MiB 内存边界、可见最新 100 条、64 KiB 超长行和过滤兼容测试；运行过滤后的 `:feature:logcat:testDebugUnitTest` 并确认新增测试失败（RED，不得编写业务代码）
- [ ] T055 [P] [US4] 在 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatBuffer.kt` 实现结构化事件接入且保持既有过滤与展示边界，使 T054 通过（GREEN，依赖 T054）
- [ ] T056 [US4] 在 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatViewModelTest.kt` 编写用户主动开始、历史/空历史/跟随状态、能力限制暂停、明确降级选择、停止/离页/断开/Session 切换和旧流结果丢弃测试；运行过滤后的模块测试并确认失败（RED，不得编写业务代码）
- [ ] T057 [US4] 在 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatViewModel.kt` 实现两阶段状态机、显式降级动作和页面前台生命周期，使 T056 通过（GREEN，依赖 T053、T055、T056）
- [ ] T058 [US4] 在 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 增加 Logcat 能力限制必须显示“仅跟随/仅历史/取消”且不得自动降级、离开页面停止、既有过滤入口仍可用的 UI 策略测试；运行过滤后的 `:app:testDebugUnitTest` 并确认失败（RED，不得编写业务代码）
- [ ] T059 [US4] 在 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt` 实现最近历史状态、能力限制说明与显式降级选择，并在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 保持页面生命周期转发，使 T058 和 Logcat Feature 测试通过（GREEN，依赖 T057、T058）
- [ ] T060 [US4] 按 `specs/003-device-file-diagnostics/quickstart.md` 的 Logcat 场景运行 `:core:adb:testDebugUnitTest`、`:feature:logcat:testDebugUnitTest` 和 `:app:testDebugUnitTest`，确认 US4 独立测试全部通过

**Checkpoint**: 最近历史、持续跟随、交接去重和显式降级均可独立验证，且不与文件长任务并发。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: 完成全量门禁、真机/Provider 验收、历史回归、架构事实同步和 v0.03 版本收口。

- [ ] T061 运行 `./gradlew.bat :core:adb:testDebugUnitTest :core:data:testDebugUnitTest :feature:files:testDebugUnitTest :feature:logcat:testDebugUnitTest :app:testDebugUnitTest`，确认全部 RED→GREEN 回归通过，并检查 `core/*/build/reports/tests/`、`feature/*/build/reports/tests/` 和 `app/build/reports/tests/` 不含敏感测试数据
- [ ] T062 运行 `./gradlew.bat :app:assembleDebug`，确认 `app/build/outputs/apk/debug/` 生成可安装 Debug APK
- [ ] T063 运行 `./gradlew.bat lintDebug` 并清零与本功能相关的问题，结果位于各模块 `build/reports/lint-results-debug.html`
- [ ] T064 检查 `app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml` 仅保留权限矩阵允许的 `INTERNET`，并核对 `gradle/libs.versions.toml` 与依赖解析结果未新增外部依赖；ADR 0004 既有许可证阻断必须单独报告（依赖 T062）
- [ ] T065 [P] 运行边界与隐私检查，确认 `feature/files/src/`、`feature/logcat/src/` 和 `app/src/` 不含 ADB Socket、原始 Shell/Sync 命令、直接 SAF 实现或敏感日志输出，且 `core/adb/src/test/kotlin/com/sheen/adb/core/DiagnosticRedactorTest.kt` 全部通过
- [ ] T066 按 `specs/003-device-file-diagnostics/quickstart.md` 在最低设备/Provider 矩阵完成浏览与双向传输验收，并将不含真实端点、路径、URI、摘要或文件内容的结果记录到 `specs/003-device-file-diagnostics/checklists/file-transfer-validation.md`
- [ ] T067 [P] 按 `specs/003-device-file-diagnostics/quickstart.md` 在最低设备矩阵完成单/拆分 APK 与 Logcat 交接、洪泛、时钟跳变和降级验收，并将匿名结果记录到 `specs/003-device-file-diagnostics/checklists/apk-logcat-validation.md`
- [ ] T068 运行 v0.02 连接、配对、设备信息、应用管理、Shell 和既有 Logcat 自动化/真机回归，将无敏感数据的结果记录到 `specs/003-device-file-diagnostics/checklists/regression-validation.md`，确认除明确调整的 Logcat 启动行为外无退化
- [ ] T069 根据已通过的实现证据更新 `docs/architecture/adb-session.md`、`docs/architecture/device-and-data.md`、`docs/architecture/application-management.md`、`docs/architecture/diagnostics.md` 和 `docs/architecture/security-and-delivery.md`，只记录当前架构事实，不写真实设备或测试内容
- [ ] T070 将 `app/build.gradle.kts` 的 `versionCode`/`versionName` 更新为 v0.03 对应值，并重新运行 `./gradlew.bat :app:assembleDebug` 验证最终产物版本与构建成功

---

## Phase 8: True-device Feedback Fixes

**Purpose**: 修复文件浏览真机反馈中的面包屑布局空隙和 Android 10 及更早 Sync v1 兼容问题，不扩大功能范围。

- [✓] T071 [US1] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesReducerTest.kt` 增加 `/目录名/子目录名` 连续路径显示测试，并确认因显示格式能力缺失而失败（RED）
- [✓] T072 [US1] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt` 与 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt` 实现无按钮内边距空隙的可导航路径片段，使 T071 通过（GREEN，依赖 T071）
- [✓] T073 [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 增加 Sync v1 `.`/`..` 与 `/sdcard` 链接候选回归测试，并确认旧实现分别产生协议不兼容或无法解析共享存储（RED）
- [✓] T074 [US1] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 过滤协议目录项并以 LIST 成功选择共享存储，使 T073 通过（GREEN，依赖 T073）
- [✓] T075 同步更新 `spec.md`、`plan.md`、`research.md`、ADB 契约与 `quickstart.md`，明确被控端旧版 adbd 能力边界和路径显示验收
- [✓] T076 运行 `:core:adb:testDebugUnitTest`、`:feature:files:testDebugUnitTest`、`:app:testDebugUnitTest`、相关 lint 与 Debug 构建，确认修复无回归
- [✓] T077 按 `quickstart.md` 在一台 Android 10 或更早、通过 `IP:5555` 授权的真机复验共享存储、子目录和路径显示，并记录匿名结果

---

## Phase 9: True-device Transfer and Summary Fixes

**Purpose**: 修复通用 `OpenDocument` 来源被错误要求 DocumentsContract 专属列、树内子文档 URI 被折叠为树根，以及文件页面显示无效果“查看”按钮的问题；不放宽安全提交与权限边界。

- [✓] T078 [P] [US2] 在 `core/data/src/test/kotlin/com/sheen/adb/data/SafDocumentPolicyTest.kt` 增加仅有通用来源元数据且缺少 DocumentsContract flags/mtime 时仍可打开并用摘要复核的测试；运行过滤测试并确认失败（RED）
- [✓] T079 [P] [US2] 在 `core/data/src/main/kotlin/com/sheen/adb/data/SafDocumentStore.kt` 分离通用上传来源元数据与输出文档元数据，使 T078 通过（GREEN，依赖 T078）
- [✓] T080 [P] [US2] 在 `core/data/src/test/kotlin/com/sheen/adb/data/SafDocumentPolicyTest.kt` 增加树根 URI 需要解析而 `tree/.../document/...` 子文档 URI 必须保留的策略测试；运行过滤测试并确认失败（RED）
- [✓] T081 [P] [US2] 在 `core/data/src/main/kotlin/com/sheen/adb/data/SafDocumentStore.kt` 修正 Android SAF URI 解析，使能力检查与写入作用于实际临时子文档，使 T080 通过（GREEN，依赖 T080）
- [✓] T082 [US2] 在 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 增加文件页面隐藏“查看”、其他页面显示并导航的装配测试；运行过滤测试并确认失败（RED）
- [✓] T083 [US2] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt` 与 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 实现按当前 Destination 显示“查看”，使 T082 通过（GREEN，依赖 T082）
- [✓] T084 同步 `spec.md`、`plan.md`、`research.md`、SAF 契约与 `quickstart.md`，运行 `:core:data:testDebugUnitTest`、`:feature:files:testDebugUnitTest`、`:app:testDebugUnitTest`、相关 lint 与 `:app:assembleDebug`，确认修复无回归

---

## Phase 10: True-device Sync v1 Upload and Download Stability Fixes

**Purpose**: 修复远端上传提交/清理在主线程执行导致闪退、下载错误被统一误报为 Session 关闭，以及旧版 Sync v1 首个零字节子流早退的问题；不扩大传输、权限或 UI 范围。

- [✓] T085 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 增加远端摘要、提交与清理必须运行于注入 I/O dispatcher，且清理 Socket 异常不得逃逸的测试；运行过滤测试并确认失败（RED，不得修改业务代码）
- [✓] T086 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 将远端摘要、提交和清理命令统一切换到注入 I/O dispatcher，并把清理异常收敛为结构化结果，使 T085 通过（GREEN，依赖 T085）
- [✓] T087 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/KadbRemoteFileProtocolTest.kt` 增加 Sync v1 首次零字节远端子流关闭后仅重试一次，以及已有进度、本地来源/目标 I/O、Sync FAIL、取消和超时均不重试的测试；运行过滤测试并确认失败（RED，不得修改业务代码）
- [✓] T088 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 实现本地来源/目标异常身份与 Sync v1 零字节单次安全重试，使 T087 通过（GREEN，依赖 T087）
- [✓] T089 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/RemoteFileSessionManagerTest.kt` 增加本地文件 I/O、远端权限/路径拒绝、Sync 子流关闭和 Session 关闭的结构化错误映射测试；运行过滤测试并确认失败（RED，不得修改业务代码）
- [✓] T090 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现错误来源分类，使 T089 通过（GREEN，依赖 T089）
- [✓] T091 同步本阶段规格、计划、研究、ADB 契约与真机回归步骤，运行 `:core:adb:testDebugUnitTest`、`:feature:files:testDebugUnitTest`、`:app:testDebugUnitTest`、相关 lint 与 `:app:assembleDebug`，确认无主线程网络异常、无错误误报且无历史回归

---

## Phase 11: Mixed Sync Capability Download Fix

**Purpose**: 修复 `ls_v2`/`stat_v2` 与 `sendrecv_v2` 被错误合并为单一版本判断，导致旧设备的 Sync v1 文件收发兼容路径被跳过的问题。

- [✓] T092 [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/KadbRemoteFileProtocolTest.kt` 增加“v2 列表/元数据 + v1 文件收发”的混合能力测试，验证下载首次零字节远端关闭时仍重试一次；运行过滤测试并确认失败（RED，不得修改业务代码）
- [✓] T093 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/KadbProtocolClientFactory.kt` 独立暴露列表版本和传输版本，使 T092 通过（GREEN，依赖 T092）
- [✓] T094 同步本阶段规格、计划、研究、ADB 契约与真机矩阵，运行 `:core:adb:testDebugUnitTest`、`:feature:files:testDebugUnitTest`、`:app:testDebugUnitTest`、`lintDebug` 与 `:app:assembleDebug`，确认混合能力设备走 Sync v1 收发兼容路径且无历史回归

---

## Phase 12: True-device Artifact Consistency Gate

**Purpose**: 防止真机继续运行早于当前修复的旧安装包，确保下载/上传复测结果与被验证源码和构建制品一致；不修改产品功能、权限或发布版本号。

- [✓] T095 核对本轮完整 Logcat、当前连接主控设备的 `lastUpdateTime` 与上一轮修复制品生成时间，确认 01:40 复测运行的是 07-19 21:31 安装的旧包，早于 07-20 01:35 后生成的 Phase 10/11 修复制品
- [✓] T096 在 `plan.md` 与 `quickstart.md` 增加真机复测前重新安装当前 APK、比对制品生成时间与设备安装更新时间并仅在本地核对 SHA-256 的门禁；重新运行文件传输相关测试、lint 与 `:app:assembleDebug`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 无依赖；T002、T003 可在 T001 后并行，T004 依赖 T001～T003。
- **Phase 2 Foundational**: 依赖 Phase 1；三组 TDD 链可并行：`T005 → T006`、`T007 → T008`、`T009 → T010`。完成前阻塞所有故事 GREEN 实现。
- **US1 (Phase 3)**: 依赖 Phase 2；是推荐 MVP。Core 的路径链与协议链可并行，之后汇合到 Session 和 UI。
- **US2 (Phase 4)**: 依赖 Phase 2；Core 二进制/远端提交、Core Data SAF 和 Feature 冲突测试可分线推进，但对 `FilesViewModel.kt`/`FilesScreen.kt` 的集成必须在 US1 对相同文件的写入完成后串行合并。
- **US3 (Phase 5)**: 依赖 Phase 2、T027 的可靠拉取能力和 T031 的 SAF 提交能力；候选解析可提前并行。
- **US4 (Phase 6)**: 依赖 Phase 2，与 US1～US3 的功能实现无依赖；仅 `SheenApp.kt` 的装配写入需避免与文件故事同时修改。
- **Phase 7 Polish**: 依赖所选择交付的全部故事；T062、T063 分别执行构建与 lint，T064 等待 T062 的 merged Manifest，T065 可与这些只读门禁并行；T066 与 T067 可在不同设备/证据文件上并行，T069 等待验收证据，T070 在最终验证后执行。
- **Phase 8 True-device Feedback Fixes**: T071→T072 与 T073→T074 可并行；T075 在行为确定后同步工件；T076 依赖 T072、T074、T075；T077 依赖 T076 并需要真实旧版被控设备。
- **Phase 10 True-device Sync v1 Upload and Download Stability Fixes**: `T085→T086`、`T087→T088`、`T089→T090` 必须严格按 RED→GREEN 执行；T091 依赖 T086、T088、T090。
- **Phase 11 Mixed Sync Capability Download Fix**: `T092→T093` 必须严格按 RED→GREEN 执行；T094 依赖 T093。
- **Phase 12 True-device Artifact Consistency Gate**: T095 先证明复测安装包早于修复制品；T096 依赖 T095，并在重新生成当前 APK、完成门禁且记录本地制品信息后结束。

### RED → GREEN Hard Dependencies

- `T005 → T006`；`T007 → T008`；`T009 → T010`
- Phase 10: `T085 → T086`；`T087 → T088`；`T089 → T090`
- Phase 11: `T092 → T093`
- US1: `T011 → T012`；`T013 → T014`；`T015 → T016`；`T017 → T018`；`T019 → T020`；`T021 → T022`
- US2: `T024 → T025`；`T026 → T027`；`T028 → T029`；`T030 → T031`；`T032 → T033`；`T034 → T035`；`T036 → T037`；`T038 → T039`
- US3: `T041 → T042`；`T043 → T044`；`T045 → T046`；`T047 → T048`
- US4: `T050 → T051`；`T052 → T053`；`T054 → T055`；`T056 → T057`；`T058 → T059`
- 任何 GREEN 任务不得与其 RED 前置任务合并或提前执行；RED 失败原因必须是目标能力尚未实现，而不是编译环境、测试夹具或无关回归故障。

### User Story Dependency Graph

```text
Setup → Foundational → US1 (MVP)
                    ├→ US2 ─┐
                    ├→ US4  ├→ Polish
                    └→ US3 ←┘

US3 additionally reuses the tested transfer/SAF primitives from US2 (T027, T031),
but its candidate parsing chain T041 → T042 can start after Foundational.
```

### Parallel Opportunities

- Phase 2 的 Core 租约、诊断脱敏和 Feature 状态三条 TDD 链修改不同文件，可并行。
- US1 中 `T011 → T012` 与 `T013 → T014` 可并行，`T017 → T018` 也可与 Core 工作并行。
- US2 中 `T030 → T031`（Core Data）与 Core ADB 传输链并行；`T032 → T033` 可同时推进。
- US3 的 `T041 → T042` 可与 US2 的后续 UI 集成并行，但 `T044` 必须等待可靠拉取能力。
- US4 的 Core、Feature Buffer RED/GREEN 链可与文件故事并行；共享 `SheenApp.kt` 的 T059 必须串行合并。
- `[P]` 不覆盖 RED→GREEN 依赖，也不允许两个任务同时修改同一文件。

### Parallel Examples

```text
# US1：基础完成后并行启动
T011 RemotePathPolicyTest RED       || T013 KadbRemoteFileProtocolTest RED
T012 RemoteFileCapabilities GREEN   || T014 Kadb protocol adapter GREEN

# US2：远端与主控端能力并行
T024→T025→T026→T027 core:adb        || T030→T031 core:data
T032→T033 feature conflict policy   || (same time, different files/modules)

# US4：Core 交接与 Feature 缓冲并行
T050→T051 core:adb handoff          || T054→T055 feature:logcat buffer
```

---

## Implementation Strategy

### MVP First（仅 US1）

1. 完成 Phase 1 与 Phase 2。
2. 完成 `T011`～`T023`，严格保留每组 RED 失败证据后再做 GREEN。
3. 在 T023 停止并独立验证浏览价值；不要让传输、APK 或 Logcat 的未完成状态阻塞 MVP 判断。

### Incremental Delivery

1. Setup + Foundational：建立模块、租约、脱敏和共享状态。
2. US1：交付只读文件浏览 MVP。
3. US2：增加双向单文件传输与 SAF 安全提交。
4. US3：复用已验证的拉取/提交原语，增加完整 APK 集合事务。
5. US4：独立交付 Logcat 两阶段采集与显式降级。
6. Phase 7：完成全量门禁、真机矩阵、历史回归与版本收口。

## Notes

- RED 任务只允许修改测试或测试夹具，并必须实际观察到预期失败；不得把“尚未运行”当作 RED 完成。
- GREEN 任务只实现使对应失败测试通过的最小能力；重构应在测试保持绿色后进行，且不得扩大任务范围。
- 所有 ADB 原始协议、命令、Session 和租约实现只能位于 `:core:adb`；Feature 与 App 只消费项目契约。
- 不引入新 Manifest 权限、外部依赖、持久化格式、后台常驻、账号、网络后端或遥测。
- 测试、报告和验收证据不得包含真实 IP、路径、SAF URI、包名上下文、APK 内容、摘要、Shell 输出或 Logcat。
- 建议每个 RED/GREEN 对或一个小的逻辑组独立提交，便于审查测试确实先于实现。
