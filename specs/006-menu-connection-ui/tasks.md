# Tasks: v0.1 菜单与连接页 UI

**Input**: Design documents from `/specs/006-menu-connection-ui/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**TDD rule**: 每个用户故事必须先完成对应测试任务并确认测试因缺少目标行为而失败，再执行实现任务。每个任务最多修改两个文件；验收标准写在任务末尾。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同阶段其他 `[P]` 任务并行，且不修改同一文件
- **[Story]**: 对应 `spec.md` 的用户故事
- 所有路径均相对仓库根目录

## Phase 1: Setup

**Purpose**: 确认基线并补齐本功能所需的测试配置，不引入新业务依赖。

- [X] T001 按 `specs/006-menu-connection-ui/quickstart.md` 运行当前相关模块测试并记录基线，不修改源码；验收：现有失败与本功能新增失败可区分，记录不含真实 IP、密钥、配对码、Shell 或 Logcat 内容
- [X] T002 [P] 为本地化纯逻辑测试配置 TestNG in `core/ui/build.gradle.kts`；验收：`:core:ui:testDebugUnitTest` 可被 Gradle 发现且未新增第三方运行时依赖
- [X] T003 [P] 为概览快捷操作状态测试配置 coroutines-test in `feature/overview/build.gradle.kts`；验收：`:feature:overview:testDebugUnitTest` 可执行，依赖来自现有 version catalog
- [X] T004 [P] 先编写 v0.1 构建标识失败测试 in `app/src/test/kotlin/com/sheen/adbhelper/VersionContractTest.kt`；验收：测试要求 `versionName 0.1.0`、`versionCode 3` 且菜单不使用设计占位版本，并在实现前失败
- [X] T005 更新 v0.1 构建标识 in `app/build.gradle.kts`；验收：T004 通过，生成的 `BuildConfig.VERSION_NAME` 为 `0.1.0`、versionCode 为 3

---

## Phase 2: Foundational

**Purpose**: 建立所有新 UI 共用的语言类型与字符串目录。

**⚠️ CRITICAL**: 本阶段完成后才能开始用户故事实现。

- [X] T006 先编写语言枚举、中文默认值、English 映射和未知键回退的失败测试 in `core/ui/src/test/kotlin/com/sheen/adb/ui/V01StringsTest.kt`；验收：测试覆盖 `ZH_CN`、`EN_US`、缺失键和未知偏好回退，并在实现前失败
- [X] T007 实现 `UiLanguage` 与 v0.1 字符串目录 in `core/ui/src/main/kotlin/com/sheen/adb/ui/UiLanguage.kt` and `core/ui/src/main/kotlin/com/sheen/adb/ui/V01Strings.kt`；验收：T006 全部通过，目录覆盖菜单、顶部栏、底部栏、连接/配对状态、错误、空状态、关于和快捷操作文案
- [X] T008 [P] 先编写已确认 `DESIGN.md` 色板、4dp 节奏、圆角、字体角色和触控区域映射失败测试 in `core/ui/src/test/kotlin/com/sheen/adb/ui/SheenDesignTokensTest.kt`；验收：测试固化 `plan.md` 已确认的 token 与设计哈希，不在测试运行时依赖仓库外文件，要求 44px 视觉尺寸具有至少 48dp 点击区域，并在实现前失败
- [X] T009 实现本地 dark-first design tokens 与 Compose 主题映射 in `core/ui/src/main/kotlin/com/sheen/adb/ui/SheenTheme.kt`；验收：T008 通过，UI/技术数据分别映射本地 SansSerif/Monospace，且不存在运行时字体/CDN请求

**Checkpoint**: 共用本地化契约可供 app 与 feature 使用。

---

## Phase 3: User Story 1 - 通过连接页建立或恢复连接 (Priority: P1) 🎯 MVP

**Goal**: 提供新连接页未连接/连接中/已连接/断开中状态，复用现有唯一 Session 连接与替换流程。

**Independent Test**: 不依赖其他功能页，验证非法端点不调用 ADB、有效端点连接、发现结果连接、连接失败/取消/超时、Session 替换和断开后旧状态清除。

### Tests for User Story 1

- [X] T010 [P] [US1] 先编写连接页状态映射和非法端点校验的失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentationTest.kt`；验收：覆盖空值、IPv4/IPv6、端口边界、连接中、失败、已连接、断开中及旧 Session 清除，并在实现前失败
- [X] T011 [US1] 实现纯逻辑 `ConnectionPagePresentation` in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentation.kt`；验收：T010 通过，状态只使用项目类型且不拼接 ADB Shell
- [X] T012 [P] [US1] 先编写连接、替换确认、取消、超时、旋转/后台/进程重建和断开调用顺序的失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesConnectionViewModelTest.kt`；验收：断言每个操作绑定当前 `sessionId`，有效连接在测试时钟 10 秒内收敛为明确结果，非法输入零次调用 manager，重建后以实际 Session 为准且旧结果不覆盖新 Session
- [X] T013 [US1] 适配连接页事件与状态收敛 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt`；验收：T012 通过，复用现有 connect/disconnect/replacement 逻辑并保留取消、超时、错误和资源清理
- [X] T014 [P] [US1] 先扩展连接页顶部状态和未连接 `code.html` 页面结构契约失败测试 in `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt`；验收：覆盖菜单按钮、IP:端口输入、连接图标、发现区、三项配对入口及断开回退，并在实现前失败
- [X] T015 [US1] 优先把未连接 `code.html` 的 HTML + Tailwind 结构转换为 Compose 连接页与顶部控件 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt` and `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`；验收：T014 通过，不使用 WebView/CDN，连接和断开均经过 `DevicesViewModel`，旧设备数据在断开或 Session 切换后不可见

**Checkpoint**: 用户可从新连接页完成手动连接、替换确认和断开；这是最小可交付 MVP。

---

## Phase 4: User Story 2 - 在未连接页面发现并完成配对 (Priority: P1)

**Goal**: 在新未连接页面复用 10 秒发现、二维码、六位配对码和本机配对，并新增下拉刷新。

**Independent Test**: 验证首次进入及每次下拉仅产生一个 10 秒扫描；覆盖内容、空、取消、超时、能力不可用、过期结果，以及三类配对流程的敏感数据清理。

### Tests for User Story 2

- [X] T016 [P] [US2] 先扩展首次进入、下拉刷新、generation 隔离和禁止并行扫描的失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt`；验收：连续刷新始终最多一个扫描，旧 generation 结果不覆盖新结果，并在实现前至少一项失败
- [X] T017 [P] [US2] 先扩展设备行格式、空/扫描中/取消/超时/能力不可用状态的失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPresentationTest.kt`；验收：具名与无名、IPv4/IPv6、端口变化均符合“可选名称 + 协议 · IP · 端口”，并在实现前失败
- [X] T018 [P] [US2] 先扩展二维码、六位码、本机配对取消/超时/Session 切换和敏感值清理的失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentationTest.kt` and `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt`；验收：所有终态均可区分且配对值不进入持久状态，并在实现前失败

### Implementation for User Story 2

- [X] T019 [US2] 接入首次扫描与下拉刷新事件 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt`；验收：T016 通过，每次扫描最长 10 秒、可取消且不会枚举子网或探测端口
- [X] T020 [P] [US2] 实现新扫描卡片及完整状态展示 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt`；验收：T017 通过，无可靠名称时省略名称行，所有非成功状态提供重试或手动输入入口
- [X] T021 [US2] 适配二维码、配对码和本机配对终态/Session 清理 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentation.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt`；验收：T018 通过，入口沿用 T015 的 Compose 适配，成功后只连接已验证服务，取消/超时/端口失效清理临时状态

**Checkpoint**: 用户可独立通过发现或任一配对入口准备并建立连接。

---

## Phase 5: User Story 3 - 查看已连接设备状态并执行快捷操作 (Priority: P1)

**Goal**: 已连接页展示当前 Session 概览，并对被控端执行截图、视频-only 录屏和经确认的重启。

**Independent Test**: 在完整/缺失概览、能力不支持、取消、超时、断开、Session 切换、SAF 取消/失败和录屏上限夹具下验证状态、互斥、清理和结果语义。

### Core ADB contract tests

- [X] T022 [US3] 先编写 typed quick-action API、项目自有 `AdbCaptureSink`、捕获元数据、能力、进度和错误契约失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/QuickActionContractTest.kt`；验收：测试要求三个操作均携带 `expectedSessionId`，ADB 只写项目自有端口，不返回 `core:data` artifact、File、URI、OutputStream、raw Shell、Socket、Kadb 或远端路径，并在实现前失败
- [X] T023 [US3] 增加 quick-action 项目模型、`AdbCaptureSink` 与 `AdbSessionManager` typed API in `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`；验收：T022 通过，捕获只返回项目自有元数据，结果区分成功、取消、失败、stale Session 和结果未知
- [X] T024 [US3] 先扩展快捷操作与文件传输/APK/Logcat 全局互斥及 lease 失效的失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/AdbExclusiveOperationCoordinatorTest.kt`；验收：三个快捷操作彼此及与既有长操作冲突，取消/异常/Session 切换释放 lease，并在实现前失败
- [X] T025 [US3] 实现 quick-action 全局互斥和 Session-bound lease in `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T024 通过，lease 在 non-cancellable cleanup 中释放且不等待设备重连
- [X] T026 [US3] 先编写截图、录屏、重启独立能力探测与 Session 失效失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionCapabilityTest.kt`；验收：三项分别覆盖 Supported、Unsupported、PolicyRejected、ProbeFailed、Unknown，包含低于 Android 10 的受控端能力夹具，禁止仅按系统版本推断，并在实现前失败
- [X] T027 [US3] 实现 session-bound quick-action capability resolver in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionCapabilityResolver.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T026 通过，Session 切换后旧能力失效，未支持请求不会发送
- [X] T028 [P] [US3] 先编写被控端截图字节流、10 秒超时、空/无效图片、取消和旧 Session 隔离失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionScreenshotSessionManagerTest.kt`；验收：测试明确来源为被控端且不使用文本 Shell 结果，并在实现前失败
- [X] T029 [P] [US3] 先编写被控端无音频单段录屏、停止、5 分钟/256 MiB 上限和流清理失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionScreenRecordSessionManagerTest.kt`；验收：断言先到上限停止、无第二段、无音频参数、取消后子流关闭，并在实现前失败
- [X] T030 [P] [US3] 先编写被控端重启确认后发送、拒绝/不支持、断开结果未知及不自动重连失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionRebootSessionManagerTest.kt`；验收：断开不能映射为成功，manager 零次发起重连，并在实现前失败

### Core ADB implementation

- [X] T031 [US3] 实现向项目自有 `AdbCaptureSink` 写入被控端截图并返回捕获元数据 in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T028 通过，超时/取消/断开只关闭子流并保留仍有效 Session，ADB 不拥有 artifact
- [X] T032 [US3] 实现向项目自有 `AdbCaptureSink` 写入被控端 video-only 数据与双上限停止 in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T029 通过，不通过持锁的 `executeShell` 运行长任务，不产生音频、后台续录或第二段
- [X] T033 [US3] 实现被控端重启 typed 操作及中性结果映射 in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T030 通过，发送后断开返回 requested/unknown，取消确认不会调用本 API

### Artifact and SAF tests

- [X] T034 [P] [US3] 先编写 app-private artifact、项目自有 `ArtifactSink`/`ExportDestination`、大小限制、格式、过期和幂等清理失败测试 in `core/data/src/test/kotlin/com/sheen/adb/data/QuickActionArtifactStoreTest.kt`；验收：覆盖成功、空输出、空间/IO、取消、断开和下次启动清理，公开接口不暴露 File、OutputStream、ContentResolver 或原始 URI，并在实现前失败
- [X] T035 [P] [US3] 先编写 `CreateDocument` 二进制导出、用户取消、写入失败和源清理失败测试 in `core/data/src/test/kotlin/com/sheen/adb/data/SafBinaryExporterTest.kt`；验收：只有完整可读目标返回成功，不在用户 tree 建立 quick-action `.part`，并在实现前失败
- [X] T036 [P] [US3] 实现 bounded app-private artifact store、`ArtifactSink`/`ExportDestination` 与 typed SAF exporter in `core/data/src/main/kotlin/com/sheen/adb/data/QuickActionArtifactStore.kt` and `core/data/src/main/kotlin/com/sheen/adb/data/SafBinaryExporter.kt`；验收：T034、T035 通过，UI/ViewModel 不接触平台类型，所有终态 best-effort 删除私有临时文件
- [X] T037 [US3] 为 `feature:overview` 接入既有 `:core:data` 项目依赖 in `feature/overview/build.gradle.kts`；验收：保留概览功能已有的合法 `:core:adb` 依赖，新增依赖仅用于 Use Case 消费项目自有 data 端口，不新增第三方或 Feature 间依赖
- [X] T038 [US3] 先编写 feature-owned `QuickActionUseCase` 协调与边界失败测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/QuickActionUseCaseTest.kt`；验收：T023、T036、T037 完成后编译测试；覆盖 artifact/sink 创建、ADB 调用、元数据校验、导出和终态清理；Use Case 只桥接项目自有端口，不向 ViewModel/app 暴露 File、原始 URI、OutputStream、ContentResolver、协议字节流或 raw ADB，并在实现前失败
- [X] T039 [US3] 实现 feature-owned quick-action Use Case 与结果模型 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionUseCase.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionModels.kt`；验收：T038 通过，Use Case 协调 data artifact/sink、typed ADB、元数据校验、SAF 导出与清理，并只向 ViewModel 暴露 opaque reference 和结构化结果
- [X] T040 [US3] 先编写 app 仅装配/注入快捷操作 Use Case 的失败契约测试 in `app/src/test/kotlin/com/sheen/adbhelper/QuickActionWiringContractTest.kt`；验收：测试拒绝 app 中出现捕获、artifact 校验、SAF 写入或清理业务，并在实现前失败
- [X] T041 [US3] 在应用容器中构造并注入 `QuickActionUseCase` in `app/src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt`；验收：T040 通过，app 只连接既有 core 实例、Feature Use Case 和生命周期清理入口，不拥有快捷操作业务状态机
- [X] T042 [P] [US3] 先编写进程启动清理和“清除本地数据”覆盖 quick-action artifact 的失败契约测试 in `app/src/test/kotlin/com/sheen/adbhelper/QuickActionStartupCleanupContractTest.kt`；验收：测试要求启动清理幂等、清理失败不阻塞 app 启动，并在实现前失败
- [X] T043 [US3] 接入启动与设置清除路径的 artifact 清理 in `core/data/src/main/kotlin/com/sheen/adb/data/TemporaryDataCleaner.kt` and `app/src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt`；验收：T042 通过，清理不记录真实 artifact 路径或内容

### Connected-page presentation tests

- [X] T044 [P] [US3] 先编写 quick-action reducer 的互斥、进度、导出、取消、失败、结果未知和 stale Session 失败测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/QuickActionPresentationTest.kt`；验收：旧 Session 状态/文件零次交付到新 Session，且在实现前失败
- [X] T045 [P] [US3] 先编写概览完整/缺失字段、动态刷新和断开清除失败测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/OverviewPresentationTest.kt`；验收：不可用字段不伪造零值，旧 Session 不覆盖新 Session，并在实现前失败
- [X] T046 [US3] 实现消费 `QuickActionUseCase` 的 session-bound 状态机和 ViewModel 调度 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionPresentation.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewViewModel.kt`；验收：T038、T044 通过，ViewModel 不持有 sink/平台类型，三项操作互斥且取消/超时/断开均进入单一终态
- [X] T047 [US3] 优先把已连接 `code.html` 的 HTML + Tailwind 结构转换为 Compose 概览卡及快捷操作 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewViewModel.kt`；验收：T045 与 T027 通过，不使用 WebView/CDN，CPU 等能力缺失时布局明确降级，重启发送前显示完整风险确认
- [X] T048 [P] [US3] 先编写 app SAF launcher、导出目标转换、被控端目标和 Feature 无 raw ADB/ContentResolver 的失败契约测试 in `app/src/test/kotlin/com/sheen/adbhelper/QuickActionPlatformContractTest.kt`；验收：测试要求 PNG/MP4 仅在 artifact 完成后启动 `CreateDocument`，选择结果通过 data 平台适配器转换为项目自有 `ExportDestination` 后只转交 Use Case，取消不显示成功，并在实现前失败
- [X] T049 [US3] 装配已连接页、`QuickActionUseCase` 与 SAF 结果回传 in `app/src/main/kotlin/com/sheen/adbhelper/MainActivity.kt` and `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`；验收：T041、T047、T048 通过，app 不执行捕获/导出业务，截图/录屏导出到主控端用户所选位置，主控端屏幕从未作为捕获源，重启后不自动重连

**Checkpoint**: 已连接页可独立展示当前 Session 概览并安全完成三个被控端快捷操作。

---

## Phase 6: User Story 4 - 使用顶部栏、底部栏和菜单栏导航 (Priority: P1)

**Goal**: 建立移动端六项底部栏、大屏等价持久导航和不丢失 Session 的菜单开关。

**Independent Test**: 在连接/未连接及移动/大屏布局验证顺序、选中态、44px 触控、入口门控、抽屉遮罩/返回关闭和既有页面路由。

### Tests for User Story 4

- [X] T050 [US4] 先扩展六项顺序、未连接门控、设计 token、断开回连接页、抽屉关闭和大屏等价导航失败测试 in `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt`；验收：恰好六项且顺序固定，禁用入口不创建 Session，关闭抽屉保持 destination/language/session，点击区域至少 48dp，并在实现前失败

### Implementation for User Story 4

- [X] T051 [US4] 按三个目标 `code.html` 把统一顶部栏、移动底部栏、响应式导航和菜单生命周期转换为 Compose in `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`；验收：T050 通过，不使用 WebView/CDN，视觉尺寸遵循设计且点击区域至少 48dp，五个延后页面继续调用原 route

**Checkpoint**: 新应用壳在连接和未连接状态下均可独立验收。

---

## Phase 7: User Story 5 - 从菜单管理历史设备、设置和关于信息 (Priority: P2)

**Goal**: 把历史设备迁入菜单，并提供设置、关于和用户主动打开 GitHub 链接。

**Independent Test**: 使用在线、离线、空列表档案验证排序、重连、替换确认、重命名、删除、设置返回和关于弹层。

### Tests for User Story 5

- [X] T052 [P] [US5] 先编写历史设备菜单排序、endpoint 在线标记、空状态、重连/重命名/删除回调失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DeviceHistoryMenuPresentationTest.kt`；验收：在线必须同时匹配 host 与 port，空列表不伪造设备，并在实现前失败
- [X] T053 [P] [US5] 先扩展菜单 `code.html` 的抽屉层级、设置/关于、完整 GitHub 链接和真实版本页脚失败测试 in `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt`；验收：打开菜单不联网，只有点击链接产生外部 Intent，版本来自 BuildConfig 且不出现 `v2.4.1-测试版`，并在实现前失败

### Implementation for User Story 5

- [X] T054 [P] [US5] 实现 `DeviceHistoryMenu` 并从连接页移除重复历史列表 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DeviceHistoryMenu.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt`；验收：T052 通过，现有 repository schema、重连、替换确认、重命名、删除和身份引用清理语义不变
- [X] T055 [US5] 优先把菜单 `code.html` 的 HTML + Tailwind 抽屉转换为 Compose 并装配历史、设置、关于和真实版本页脚 in `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`；验收：T053、T054 通过，不使用 WebView/CDN或占位版本，固定支持文案正确且菜单关闭后保持 Session/当前页面

**Checkpoint**: 菜单可独立完成历史设备管理、设置跳转和关于展示。

---

## Phase 8: User Story 6 - 在设置中切换中英文 (Priority: P2)

**Goal**: 默认简体中文，支持 English 即时切换和持久恢复，不重启或取消连接/配对流程。

**Independent Test**: 切换两种语言、重启 app、注入未知/损坏偏好，并验证 v0.1 壳、连接页、状态、错误、空状态和关于文案。

### Tests for User Story 6

- [X] T056 [P] [US6] 先编写共享 DataStore 语言默认值、保存、恢复、未知值回退和 clear-all 失败测试 in `core/data/src/test/kotlin/com/sheen/adb/data/LanguagePreferenceRepositoryTest.kt`；验收：测试证明只存在一个 `sheen_local_data` owner，默认/异常均为简体中文，并在实现前失败
- [X] T057 [US6] 扩展现有 repository 契约与单一 DataStore owner 保存语言 in `core/data/src/main/kotlin/com/sheen/adb/data/DeviceProfileRepository.kt` and `core/data/src/main/kotlin/com/sheen/adb/data/DataStoreDeviceProfileRepository.kt`；验收：T056 通过，语言切换不改变设备档案且 clear-all 同时重置语言
- [X] T058 [P] [US6] 先扩展设置页互斥选项、即时状态和清除回退失败测试 in `feature/settings/src/test/kotlin/com/sheen/adb/feature/settings/SettingsPresentationTest.kt`；验收：中英文选项互斥，保存错误可见且不修改 Session，并在实现前失败
- [X] T059 [US6] 实现设置语言状态、保存事件和两个互斥选项 in `feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsViewModel.kt` and `feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsScreen.kt`；验收：T058 通过，选择后 UI state 立即更新，现有隐私/清除入口保留
- [X] T060 [P] [US6] 先编写 app 壳、顶部/底部栏、菜单、关于和连接状态即时双语失败测试 in `app/src/test/kotlin/com/sheen/adbhelper/V01LocalizationContractTest.kt`；验收：所有 v0.1 壳文案在测试时钟 1 秒内由同一语义键更新，并在实现前失败
- [X] T061 [P] [US6] 先扩展扫描、二维码、配对码、本机配对状态和错误双语失败测试 in `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPresentationTest.kt` and `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentationTest.kt`；验收：中英文覆盖加载、空、失败、取消、超时、能力不可用且不依赖字符串判断业务状态，并在实现前失败
- [X] T062 [P] [US6] 接入 app 壳与连接页的 `UiLanguage`/`V01Strings` in `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt`；验收：T060 通过，切换只重组可见文案，不重建或取消正在进行的连接/配对
- [X] T063 [P] [US6] 接入发现与配对表现层双语字符串 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentation.kt`；验收：T061 通过，业务 reducer 使用稳定语义状态而非已本地化文本
- [X] T064 [US6] 先编写已连接概览和快捷操作全部状态双语失败测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/OverviewLocalizationTest.kt`；验收：覆盖能力不可用、录制中、上限停止、导出失败、取消和结果未知，并在实现前失败
- [X] T065 [US6] 接入 overview/quick-action 本地化 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionPresentation.kt`；验收：T064 通过，中英文切换不取消当前快捷操作
- [X] T066 [US6] 先编写 AppContainer 单例注入、重启恢复及异常回退失败契约测试 in `app/src/test/kotlin/com/sheen/adbhelper/LanguageWiringContractTest.kt`；验收：测试要求 app 只构造一个偏好 owner，语言变化不替换 `AdbSessionManager`，并在实现前失败
- [X] T067 [US6] 装配共享语言 flow 到设置、菜单和连接页 in `app/src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt` and `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`；验收：T066 通过，首次启动为中文、重启恢复上次选择、损坏值回退中文且活动 Session 保持

**Checkpoint**: v0.1 新增/改版界面可在 1 秒内切换中英文并持久恢复。

---

## Phase 9: Polish & Cross-Cutting Verification

**Purpose**: 验证全部故事集成后仍满足权限、依赖、隐私、回归和构建边界。

- [X] T068 按 `specs/006-menu-connection-ui/quickstart.md` 运行 `:core:adb`、`:core:data`、`:feature:devices`、`:feature:overview`、`:feature:settings`、`:app` 单元测试，不修改文件；验收：所有测试通过，并能证明各实现任务对应测试曾先失败后通过
- [X] T069 运行 `:app:lintDebug` 与 `:app:assembleDebug` 并检查 `app/src/main/AndroidManifest.xml`、Gradle 依赖报告和运行时 URL，不修改文件；验收：构建通过，未新增受限权限或第三方媒体 SDK，Google Fonts/Tailwind/Material Symbols CDN 请求为 0
- [ ] T070 按 `specs/006-menu-connection-ui/quickstart.md` 由当前一名用户在三台脱敏被控端执行六个用户故事的独立验收，不修改文件；验收：三台均完成连接与独立能力检测，每台支持的截图/录屏各完成 3 次，低于 Android 10 的设备明确不支持时记为正确降级，截图/录屏来源为被控端、录屏无音频、重启不误报成功且不自动重连
- [X] T071 复核 `plan.md` 设计哈希并对照三个目标 `screen.png` 执行归一化视觉核对，不修改文件；验收：未连接/已连接按 468×1060、菜单按 468×1046 核对，HTML + Tailwind 主要结构均有 Compose 对应，色彩层级、间距、圆角、抽屉宽度、导航和操作位置一致，系统栏、字体/图标回退和能力降级差异有记录；哈希漂移或任一仓库外设计源不可访问时暂停验收
- [X] T072 按 `specs/006-menu-connection-ui/tasks.md` 所列变更路径检查 `git diff --check`、敏感信息和范围，不修改文件；验收：无 whitespace 错误、无真实 IP/密钥/配对码/Shell/Logcat/媒体内容，文件/应用/进程/Shell/日志页面无超范围业务改动

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无前置依赖；测试配置和版本失败测试可并行，版本实现必须在失败测试后执行。
- **Foundational (Phase 2)**: 依赖 Setup；T006/T008 必须先失败，T007/T009 再实现；完成后解锁所有用户故事。
- **P1 stories (Phases 3–6)**: 默认按 US1 → US2 → US3 → US4 交付，以便先稳定连接垂直切片；Foundation 完成后，可由不同人员按文件冲突约束并行，但同文件任务必须串行。
- **P2 stories (Phases 7–8)**: US5 依赖 US4 的菜单壳；US6 依赖 Foundation 字符串目录，并在 US4/US5 完成后接入全部 v0.1 壳文案。
- **Polish (Phase 9)**: 依赖计划交付的所有用户故事完成。

### User Story Dependencies

```text
Foundation
├── US1 (连接页核心) ──┬── US2 (发现与配对)
│                     └── US3 (概览与快捷操作)
└── US4 (导航壳) ─────── US5 (菜单内容) ── US6 (完整双语接入)
```

- **US1 (P1)**: Foundation 后可独立完成；MVP。
- **US2 (P1)**: 复用 US1 连接页容器与现有连接流程。
- **US3 (P1)**: 复用 US1 已连接状态；core ADB、data、quickactions 和 overview 子组可按依赖交错推进。
- **US4 (P1)**: Foundation 后可开发，但最终接入需要保留 US1 route。
- **US5 (P2)**: 依赖 US4 抽屉；复用 US1 的 `DevicesViewModel` profile 操作。
- **US6 (P2)**: 依赖 Foundation、US4、US5 的所有 v0.1 文案表面。

### TDD Ordering Within Each Story

1. 完成该 slice 的 `先编写…失败测试` 任务并观察预期失败。
2. 只执行紧随其后的实现任务，使对应测试通过。
3. 运行该模块既有回归测试。
4. 在故事 Checkpoint 执行 Independent Test 后再进入下一故事。

## Parallel Opportunities

### User Story 1

```text
T010 connection presentation tests
T012 connection ViewModel tests
T014 app connection-shell contract tests
```

三个失败测试修改不同文件，可并行编写；实现按 T011 → T013 → T015 串行收敛。

### User Story 2

```text
T016 discovery lifecycle tests
T017 discovery presentation tests
T018 pairing tests
```

三项测试修改不同文件，可并行；之后 T019/T020 可按文件冲突执行，T021 最后整合页面。

### User Story 3

```text
After T027:
  T028 screenshot tests
  T029 recording tests
  T030 reboot tests

In parallel:
  T034 artifact-store tests
  T035 SAF-export tests
  T044 quick-action presentation tests
  T045 overview presentation tests
```

T026/T027 独立收敛能力检测；core ADB 的 T031–T033 因共同修改 `DefaultAdbSessionManager.kt` 必须串行。T036 可与 core ADB 实现并行；artifact 与 ADB 项目自有端口就绪后，以 T038/T039 收敛 feature-owned Use Case，完成依赖和 app 装配契约后再接入 T046–T049。

### User Story 4

US4 只有 `AppUiPolicyTest.kt` 和 `SheenApp.kt` 这一组严格的 red-green slice；T050 必须先失败，T051 随后实现，因此故事内部不标记并行任务。它可在 Foundation 后与 US1 的 feature 层工作并行，但合并 `SheenApp.kt` 前必须串行协调。

### User Story 5

```text
T052 history-menu tests
T053 app menu/about tests
```

测试文件不同，可并行；T054 完成 `DeviceHistoryMenu` 后，T055 再将其装配到 app 抽屉。

### User Story 6

```text
T060 app localization contract
T061 devices localization contract
T064 overview localization contract
```

三个测试任务可并行；实现 T062、T063、T065 后再执行单例装配 T066/T067。

## Implementation Strategy

### MVP First

1. 完成 Phase 1–2。
2. 完成 US1 的 T010–T015。
3. 运行 US1 独立测试并暂停验收。
4. MVP 只包含可靠的新连接页建立/恢复/断开流程，不提前交付快捷操作或菜单附加能力。

### Incremental Delivery

1. US1：连接页核心。
2. US2：发现与配对。
3. US3：已连接概览及被控端快捷操作。
4. US4：完整导航壳。
5. US5：历史、设置、关于菜单。
6. US6：中英文即时切换和持久化。
7. Phase 9：全量回归、构建、权限/依赖和脱敏真机验收。

## Notes

- `[P]` 仅表示任务文件和直接依赖允许并行，不授权并发修改同一工作树文件。
- 每个任务最多修改两个文件；若实现发现需要第三个文件，必须拆成新任务并重新执行对应 TDD 顺序。
- Feature/UI 不得拼接 Shell、访问 Socket/TLS、操作 `ContentResolver` 或持有原始文件路径。
- `:app` 只负责装配、导航与系统 launcher 回传；捕获、artifact 校验、导出和终态清理由 `feature:overview` 内部 Use Case 协调。
- 不提交、推送或创建 PR，除非项目负责人另行明确要求。

---

## Phase 10: Convergence

- [X] T073 [HIGH] 先编写三个目标页面的 Compose 设计契约失败测试 in `app/src/test/kotlin/com/sheen/adbhelper/StrictUiDesignContractTest.kt` per T071 and plan: `code.html` primary conversion source (missing)；验收：测试锁定 44dp 视觉栏/至少 48dp 点击区、60dp 移动底栏、288dp 抽屉、未连接设备卡与底部三操作、已连接 2×2 指标/CPU 环/进度条/快捷操作、菜单头部/历史/系统/固定页脚，并在修复前失败
- [X] T074 [MEDIUM] 实现设计所需的本地 Compose 矢量图标与精确尺寸 token in `core/ui/src/main/kotlin/com/sheen/adb/ui/SheenIcons.kt` and `core/ui/src/main/kotlin/com/sheen/adb/ui/SheenTheme.kt` per DESIGN.md and plan: no CDN/WebView (partial)；验收：菜单、连接、设备、六项导航、概览和快捷操作不再使用字符占位图标，字体保持宪法允许的本地系统回退，不新增运行时依赖
- [X] T075 [HIGH] 严格按未连接/已连接 `code.html` 重做顶部栏与六项移动底栏 in `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` per US4/T051 and plan: HTML + Tailwind conversion (contradicts)；验收：顶部栏视觉高度 44dp、触控至少 48dp，未连接显示地址输入与连接图标，已连接显示 endpoint 与断开图标，底栏高度/颜色/选中胶囊/顺序与设计一致且路由和 Session 不变
- [X] T076 [HIGH] 严格按未连接 `code.html` 重排扫描结果和三项配对操作 in `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt` per US1/T015 and US2/T021 (partial)；验收：扫描目标为紧凑整行可点设备卡，名称/endpoint/设备图标/连接图标层级一致，三项按钮固定在可用内容底部；刷新、空/错/配对详情仅在对应状态出现且不挤占默认设计
- [X] T077 [HIGH] 严格按已连接 `code.html` 重做设备卡、2×2 指标和快捷操作 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt` per US3/T047 (partial)；验收：实现设备身份卡、CPU 环、RAM/存储进度条、电池/充电/温度布局及三色快捷操作，缺失能力保持同尺寸明确降级，既有快捷操作状态机不变
- [X] T078 [HIGH] 严格按菜单 `code.html` 重做抽屉与历史设备行 in `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` and `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DeviceHistoryMenu.kt` per US5/T055 (partial/unrequested)；验收：288dp 抽屉包含品牌图标、关闭按钮、历史分组、在线强调/离线行、系统分组和固定版本页脚；移除额外本机配对菜单项，保留重连/重命名/删除行为
- [X] T079 [HIGH] 运行 T073、相关模块回归、lint/assemble，并在可用 Android 渲染目标上按 468×1060/468×1046 生成脱敏截图与三个 `screen.png` 逐项核对 in `specs/006-menu-connection-ui/quickstart.md` per T071 and SC visual fidelity (missing)；验收：自动化全部通过，颜色/间距/圆角/位置的已知差异有证据；没有设备/模拟器时仅保留截图比对子项未完成，不得宣称像素级验收通过

---

## Phase 11: Connected Metric Card Alignment

- [X] T080 [HIGH] [US3] 先编写已连接页四张指标卡内部布局失败契约测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/OverviewMetricLayoutContractTest.kt` per connected `code.html`；验收：测试锁定处理器环居中且无额外 ABI 行、RAM/存储左右数值基线与底部进度条、电池百分比/温度左下分组及四种独立图标，并在修复前 3 项失败
- [X] T081 [HIGH] [US3] 严格按 connected `code.html` 修正四张指标卡图标与内部定位 in `core/ui/src/main/kotlin/com/sheen/adb/ui/SheenIcons.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt`；验收：处理器标题左上且环居中，RAM/存储标题左上且数值/进度条贴底，电池标题/充电状态置顶且百分比/温度贴左下，动态真实值不换行、不伪造 CPU 占用
- [X] T082 [US3] 在 API 36 模拟器真实 ADB Session 上生成 468×1060 指标卡渲染并对照设计记录 in `specs/006-menu-connection-ui/quickstart.md`；验收：四张卡的图标、标题、数值和进度/温度位置与 HTML flex 规则一致，CPU 能力缺失差异单独记录

---

## Phase 12: Quick-action runtime thread safety

- [X] T083 [HIGH] [US3] 先为快捷操作补充失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionIoThreadTest.kt`；验收标准：测试能够在修复前复现能力探测、录屏流打开/读取/关闭和重启派发运行在调用线程的问题，并锁定这些网络 I/O 必须离开主线程。
- [X] T084 [HIGH] [US3] 将快捷操作能力探测、录屏和重启协议 I/O 调度到受控 I/O dispatcher in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt`；验收标准：取消继续向上传播，流在不可取消的 I/O 上下文关闭，能力探测不再把主线程网络异常误报为设备拒绝，T083 全部通过。
- [X] T085 [US3] 在 API 36 模拟器的真实本机 TCP/IP ADB Session 上复验录屏和重启并记录脱敏证据 in `specs/006-menu-connection-ui/quickstart.md`；验收标准：点击录屏后应用进程保持存活且 crash buffer 无新增 fatal，确认重启后模拟器完成重启且页面未显示 `ADB_DEVICE_REJECTED`，不保存录屏内容或真实设备材料。
- [X] T086 [HIGH] [US3] 先补充被控端 MP4 临时文件录制命令的失败契约 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionIoThreadTest.kt`；验收标准：测试拒绝 `/proc/self/fd/1` 输出，要求 shell 专用临时路径、退出/中断清理 trap 和录制成功后的 ADB 流式回传，并在实现前失败。
- [X] T087 [HIGH] [US3] 将录屏协议改为被控端临时 MP4 录制后回传，并为回传保留超时余量 in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收标准：不再触发被控端 SELinux 对 `/proc/self/fd/1` 的拒绝，正常/信号/取消路径清理临时文件，录制时长仍由 `maxDuration` 限定而整体操作由请求 timeout 收口。
- [X] T088 [US3] 在 API 36 模拟器验证被控端 MP4 生成和清理并记录脱敏证据 in `specs/006-menu-connection-ui/quickstart.md`；验收标准：1 秒探针返回正字节 MP4、命令退出成功、被控端临时文件已删除，完整自动化通过；不保存或提交录屏内容。
- [X] T089 [HIGH] [US3] 扩展录屏命令契约以覆盖远端进程归属和精确清理 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionIoThreadTest.kt`；验收标准：测试要求记录专属 PID、等待该 PID，并在 I/O dispatcher 上发送仅针对该 PID 与应用前缀文件的清理命令，且在实现前失败。
- [X] T090 [HIGH] [US3] 实现录屏 PID 文件、正常取消清理与下次启动幂等回收 in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt`；验收标准：关闭录屏流后通过独立 ADB shell 精确终止本次远端进程并删除 PID/MP4，主控进程被系统强杀时留下的应用前缀资源在下次录制前回收，不终止无关录屏进程。

---

## Phase 13: Interactive screen-record completion

- [X] T091 [HIGH] [US3] 先扩展 typed API 失败契约 in `core/adb/src/test/kotlin/com/sheen/adb/core/QuickActionContractTest.kt`；验收标准：要求存在绑定 `expectedSessionId` 的停止录屏 API，停止请求只操作当前录屏且不暴露 raw Shell/远端路径，并在实现前失败。
- [X] T092 [HIGH] [US3] 增加 typed 停止录屏入口 in `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`；验收标准：T091 通过，接口返回结构化 `QuickActionResult<Unit>`，UI/Feature 无需取消协程或拼接命令。
- [X] T093 [HIGH] [US3] 先编写录屏启动、低频有界状态轮询、进度、用户停止、MP4 回传、大小/时长上限、取消和清理失败测试 in `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionScreenRecordSessionManagerTest.kt` and `core/adb/src/test/kotlin/com/sheen/adb/core/internal/QuickActionScreenRecordProtocolTest.kt`；验收标准：测试拒绝在录制期间阻塞读取 shell stream，要求停止后才打开单一 MP4 回传流，停止只向所属 PID 发送信号，且实现前失败。
- [X] T094 [HIGH] [US3] 实现可交互停止的录屏协议与 Session 控制 in `core/adb/src/main/kotlin/com/sheen/adb/core/internal/QuickActionProtocol.kt` and `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收标准：T093 通过，录制期间通过有界短命令更新 elapsed/远端文件大小，用户停止后等待 MP4 完成再回传，5 分钟/256 MiB 先到即停，取消/断开/超时无并发关闭读取流且确定性清理。
- [X] T095 [HIGH] [US3] 先编写 Use Case 与 reducer 的停止/正在停止/完成后导出失败测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/QuickActionUseCaseTest.kt` and `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/QuickActionPresentationTest.kt`；验收标准：重复停止幂等，停止不丢弃正在完成的 artifact，旧 Session 停止不影响新 Session，且实现前失败。
- [X] T096 [US3] 实现 typed 停止录屏用例结果 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionUseCase.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionModels.kt`；验收标准：停止成功、失败、取消和 stale Session 结构化返回，停止请求不丢弃或替换原录制 artifact。
- [X] T097 [US3] 实现正在停止的 reducer 状态 in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionPresentation.kt`；验收标准：T095 通过，停止请求保持原录制任务继续完成 MP4，成功后进入 `AwaitingExport`，失败/取消只产生一个终态。
- [X] T098 [HIGH] [US3] 先编写 ViewModel 完整录屏交互失败测试 in `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/OverviewScreenRecordFlowTest.kt`；验收标准：首次点击开始、录制中再次点击请求停止、状态持续更新、停止时暂停概览轮询、完成后恢复轮询并请求导出，且实现前失败。
- [X] T099 [US3] 在现有三按钮设计内把录屏按钮转换为开始/停止切换并接入 ViewModel in `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewViewModel.kt` and `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt`；验收标准：T098 通过，录制中中间按钮保持可点并显示“停止”，状态区持续显示时长/文件大小/正在停止，其他快捷操作禁用，布局仍与已连接 `code.html` 一致。
- [X] T100 [US3] 在本机真实 ADB Session 完成短录制→停止→MP4 artifact→SAF 导出→播放校验与断开/取消清理，并记录脱敏证据 in `specs/006-menu-connection-ui/quickstart.md`；验收标准：应用无 ANR/native crash，录制期间可交互，导出文件非空且可播放，远端/主控临时资源归零；自动化、模拟器和三设备 T070 证据分别报告。
