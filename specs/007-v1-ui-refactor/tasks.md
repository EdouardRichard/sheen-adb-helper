# Tasks: v1.0 全功能 UI 重构

**Input**: `specs/007-v1-ui-refactor/` 下的 `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/` 与 `quickstart.md`

**Organization**: 7 个用户故事全部为 P1，按规格顺序分组；每个故事先完成失败测试（RED），再进入实现。每项任务只允许修改其列出的 0–2 个文件。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 与同阶段其他标记任务使用不同文件，且不依赖未完成任务，可并行。
- **[Story]**: 对应 `spec.md` 的用户故事；Setup、Foundational 和 Polish 不加故事标签。
- 每项均包含“修改文件”和“验收”；读取设计源、规格或测试夹具不计入修改文件数。
- 测试任务必须先观察到预期失败（RED）；不得通过删除断言、跳过测试、静态样例或伪造终态转绿。
- 若执行中发现需要修改第 3 个文件，必须停止该任务并新增后续任务，不得扩大当前任务范围。

---

## Phase 1: Setup — 设计基线与失败优先守卫

**Purpose**: 在生产实现前固化设计快照、分层、双语、版本和导航状态边界。

- [X] T001 计算 `D:\androidPorject\stitch_adb` 下 `research.md` 基线表所列 13 个设计源的 SHA-256，并新建 `docs/archive/releases/v1.0/design-baseline.md` 记录脱敏复核结论；修改文件：`docs/archive/releases/v1.0/design-baseline.md`；验收：13/13 哈希与 `specs/007-v1-ui-refactor/research.md` 一致，任一不一致即停止受影响页面任务且不得更新基线掩盖差异。
- [X] T002 [P] 扩展 `app/src/test/kotlin/com/sheen/adbhelper/StrictUiDesignContractTest.kt`，为本地设计令牌、44 dp 触控区、本地图标、Compose-only 和禁止远程 UI 资源添加源码契约；修改文件：`app/src/test/kotlin/com/sheen/adbhelper/StrictUiDesignContractTest.kt`；验收：测试因尚未补齐的 v1.0 共享令牌或图标而 RED，并能输出精确违规文件。
- [X] T003 [P] 新建 `core/ui/src/test/kotlin/com/sheen/adb/ui/V1LocalizationPrimitivesTest.kt`，覆盖 `UiLanguage`、类型化参数、共享中英键集合/占位模式一致、固定技术词，以及超长文本、C0/C1 控制字符、双向控制符、不可解码替换、换行/制表策略和底层原值不变；修改文件：`core/ui/src/test/kotlin/com/sheen/adb/ui/V1LocalizationPrimitivesTest.kt`；验收：因类型化本地化与安全原文呈现原语尚不存在而 RED，`all/debug/info/error` 与 Esc/Tab/Ctrl/Alt 在两种语言完全相同，呈现投影不得成为可再次执行的命令文本。
- [X] T004 [P] 扩展 `app/src/test/kotlin/com/sheen/adbhelper/LanguageWiringContractTest.kt` 并新建 `app/src/test/kotlin/com/sheen/adbhelper/AppStringsTest.kt`，约束 `:core:data` 单一 Flow、`:app` 单一根收集、所有目标 Route 接收同一 `UiLanguage`、禁止第二 Locale 机制，并覆盖导航锁提示、长任务返回确认、根浮层、未连接引导和 App 级可访问性文案的中英键/参数一致性；修改文件：上述 2 个文件；验收：当前缺少完整 Route 接线、状态隔离或 App 专属目录时 RED，页面业务文案不得进入 App 目录。
- [X] T005 [P] 新建 `app/src/test/kotlin/com/sheen/adbhelper/AppNavigationModelsTest.kt`，定义六页顺序、App 所有的导航锁、七类阻塞任务、picker 非锁定阶段和安全/不确定清理终态；修改文件：`app/src/test/kotlin/com/sheen/adbhelper/AppNavigationModelsTest.kt`；验收：因 App 导航模型不存在而 RED，Shell 流、Logcat 采集、复制和查看结果均不被列为导航锁。
- [X] T006 [P] 新建 `app/src/test/kotlin/com/sheen/adbhelper/V1ArchitectureBoundaryTest.kt`，扫描 Feature/UI 原始 ADB/Socket/Kadb/命令、Feature 间依赖、页面目录越权和 `:core:ui` 业务状态；修改文件：`app/src/test/kotlin/com/sheen/adbhelper/V1ArchitectureBoundaryTest.kt`；验收：仅 `:core:adb` 可拥有协议/命令，`:app` 只装配导航/语言/picker/锁聚合，且输出不含真实命令或设备材料。
- [X] T007 [P] 更新 `app/src/test/kotlin/com/sheen/adbhelper/VersionContractTest.kt`，预期 `versionCode=4`、`versionName=1.0` 和唯一可见格式 `v1.0`；修改文件：`app/src/test/kotlin/com/sheen/adbhelper/VersionContractTest.kt`；验收：当前版本配置下 RED，并能发现旧版号、设计占位版号或 `vv1.0`。
- [X] T008 [P] 扩展 `feature/settings/src/test/kotlin/com/sheen/adb/feature/settings/SettingsPresentationTest.kt`，只覆盖语言选择器、保存中/保存失败语义和现有偏好写入，不扩大 Settings 重构；修改文件：`feature/settings/src/test/kotlin/com/sheen/adb/feature/settings/SettingsPresentationTest.kt`；验收：当前硬编码中文和最终字符串状态下 RED，测试确认选择语言不会创建第二状态源。

---

## Phase 2: Foundational — 共享设计、语言与 App 宿主基础

**Purpose**: 实现所有故事依赖的本地设计原语、类型化本地化和 App 导航模型。

**⚠️ CRITICAL**: T001 必须通过且本阶段完成前，不得开始任何页面生产实现。

- [X] T009 [P] 在 `core/ui/src/main/kotlin/com/sheen/adb/ui/SheenTheme.kt` 与 `core/ui/src/main/kotlin/com/sheen/adb/ui/SheenIcons.kt` 实现 `DESIGN.md` 的颜色、间距、圆角、字形角色、44 dp 触控区及所需本地向量；修改文件：上述 2 个文件；验收：T002 的共享令牌/图标断言转绿，无新增依赖、WebView 或运行时远程资源。
- [X] T010 [P] 新建 `core/ui/src/main/kotlin/com/sheen/adb/ui/LocalizedText.kt` 与 `core/ui/src/main/kotlin/com/sheen/adb/ui/V1SharedStrings.kt`，实现类型化语义引用、共享中英目录，以及有界预览、显式替换标记、bidi-safe 包装和不修改底层原值的安全原文呈现；修改文件：上述 2 个文件；验收：T003 全绿，`:core:ui` 不包含页面业务状态、ADB/SAF 类型或偏好持久化，安全呈现结果不能被重新解释为命令。
- [X] T011 [P] 新建 `app/src/main/kotlin/com/sheen/adbhelper/AppNavigationModels.kt`，实现 `MainDestination`、`PageHostState`、App-owned `NavigationLock` 与锁终态规则；修改文件：`app/src/main/kotlin/com/sheen/adbhelper/AppNavigationModels.kt`；验收：T005 全绿，安全部分成功可解锁，cleanup/ownership 不确定持续锁定。
- [X] T012 新建 `app/src/main/kotlin/com/sheen/adbhelper/AppStrings.kt` 与 `app/src/main/kotlin/com/sheen/adbhelper/LanguagePresentation.kt`，实现 App-owned 导航、根浮层、长任务返回确认和未连接引导目录，以及 `LanguagePreference` 到 `UiLanguage` 的纯映射；修改文件：上述 2 个文件；验收：T004 的 AppStrings 键集合/参数和禁止第二 Locale 机制断言转绿，页面业务文案不进入 App 目录，语言不进入任何业务身份。
- [X] T013 [P] 在 `feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsViewModel.kt` 与新建的 `feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsStrings.kt` 中把语言保存结果改为语义代码并提供中英目录；修改文件：上述 2 个文件；验收：T008 编译进入呈现断言，ViewModel 不再保存语言选择器/保存反馈的最终中英文句子。
- [X] T014 在 `feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsScreen.kt` 接入根提供的 `UiLanguage` 与 Settings 目录；修改文件：`feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/SettingsScreen.kt`；验收：T008 全绿，语言选择器与保存反馈即时切换且不改造本版本未授权的其他 Settings 区域。
- [X] T015 在 `app/build.gradle.kts` 设置 `versionCode=4`、`versionName=1.0`；修改文件：`app/build.gradle.kts`；验收：T007 全绿，无 Manifest、权限、依赖或 ADR 变更。

**Checkpoint**: 共享设计、共享/App 文案、App 导航模型和纯语言映射可供 US1 接线；唯一根语言分发在 T026 完成后供其余故事使用。

---

## Phase 3: User Story 1 — 通过统一导航浏览工具页 (Priority: P1) 🎯 MVP

**Goal**: 六页共享唯一选中态，支持底栏、相邻滑动、淡出淡入、首尾边界、FR-004 未连接门禁、长任务锁和状态隔离。

**Independent Test**: 用假 Session/页面夹具执行 60 次底栏切换、60 次手势、首尾越界、未连接访问、语言切换、长任务返回及 Session 切换，页面/选中态/生命周期正确率 100%。

### Tests first

- [X] T016 [P] [US1] 新建 `app/src/test/kotlin/com/sheen/adbhelper/AppNavigationPolicyTest.kt`，覆盖六页顺序、相邻/首尾手势、垂直意图拒绝、快速请求收敛和锁定拒绝；修改文件：该测试文件；验收：因策略不存在而 RED，拒绝或被替代请求不产生页面进入事件。
- [X] T017 [P] [US1] 新建 `app/src/test/kotlin/com/sheen/adbhelper/AppNavigationLifecycleTest.kt`，覆盖 exactly-once 离页/进页、前后台、同 Session 保留、Session 切换清空及返回优先级；修改文件：该测试文件；验收：当前宿主未统一生命周期时 RED，长任务取消清理前不得退出。
- [X] T018 [P] [US1] 新建 `app/src/test/kotlin/com/sheen/adbhelper/V1NavigationPresentationTest.kt`，检查 44 dp 顶栏、六项底栏、紧凑/宽屏互斥、淡出淡入和无水平滑页动画；修改文件：该测试文件；验收：当前页面宿主下 RED，并能发现底栏与可见页使用不同状态源。
- [X] T019 [P] [US1] 新建 `app/src/test/kotlin/com/sheen/adbhelper/DisconnectedDestinationGateTest.kt`，为 FR-004 覆盖底栏、手势、恢复回调和深层目标在无活动 Session 时的门禁；修改文件：该测试文件；验收：当前可实例化受控端页面或生成空 Session 时 RED，所有路径只显示可操作的先连接引导。
- [X] T020 [P] [US1] 新建 `app/src/test/kotlin/com/sheen/adbhelper/LanguageStateIsolationTest.kt`，覆盖语言切换时页面/ViewModel/Session/锁/进程轮询/Shell 流/Logcat 窗口/配对 attempt/picker ID 和请求计数不变；修改文件：该测试文件；验收：当前目标页未统一接线时 RED，文本和可访问性在 1 秒内变化而业务身份零变化。

### Implementation

- [X] T021 [P] [US1] 新建 `app/src/main/kotlin/com/sheen/adbhelper/AppNavigationPolicy.kt`，实现目标接收、方向锁、阈值、首尾夹紧、快速请求收敛和锁定拒绝；修改文件：该实现文件；验收：T016 全绿，单次有效手势最多移动一个相邻页面。
- [X] T022 [US1] 重构 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`，以唯一 `PageHostState.current` 驱动内容/选中态，加入底栏、宽屏互斥布局及 500 ms 内淡出淡入；修改文件：该实现文件；验收：T018 全绿，动画期间不叠页且业务入口只调用一次。
- [X] T023 [US1] 在 `app/src/main/kotlin/com/sheen/adbhelper/MainActivity.kt` 与 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 接入前后台和“浮层→确认→长任务→直接退出”的系统返回优先级；修改文件：上述 2 个文件；验收：T017 的普通返回直接退出和长任务清理等待用例转绿。
- [X] T024 [US1] 新建 `app/src/main/kotlin/com/sheen/adbhelper/DeliveryLockAggregator.kt` 并在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 聚合各 Feature 输出任务锁；修改文件：上述 2 个文件；验收：七类任务仅从实际传输/写入开始锁定，picker 浏览/取消不锁，安全终态解锁，不确定清理保持当前页。
- [X] T025 [US1] 在 `app/src/main/kotlin/com/sheen/adbhelper/AppNavigationPolicy.kt` 与 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 实现 FR-004 未连接门禁和共享先连接引导；修改文件：上述 2 个文件；验收：T019 全绿，受控端 Feature 不收到 page-visible 或设备请求且不创建空 Session。
- [X] T026 [US1] 在 `app/src/main/kotlin/com/sheen/adbhelper/LanguagePresentation.kt` 与 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 接入现有语言 Flow 的唯一根收集、全部 Route 分发和 `AppStrings`，完成语言切换纯重组呈现与状态身份保留；修改文件：上述 2 个文件；验收：T004、T020 全绿，无 Activity recreate、业务 effect 重启、请求增量或硬编码 App 级最终文案。
- [X] T027 [US1] 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US1 自动化与假 Session 验收；修改文件：无；验收：T016–T020 全绿，SC-001、SC-002、SC-020、SC-021、SC-024 满足且 FR-004 有独立可追溯结果。

**Checkpoint**: US1 可作为独立 MVP；未实现业务页只能呈现真实不可用状态，不能显示设计样例设备数据。

---

## Phase 4: User Story 2 — 浏览并传输被控端文件 (Priority: P1)

**Goal**: 严格还原文件页 HTML，支持面包屑、稳定排序、仅行尾操作、传输锁、同 Session 恢复及 FR-007 全状态。

**Independent Test**: 用 1,000 项混合目录和现有传输假网关验证排序、路径、点击边界、上传/下载、冲突、进度、取消、断开、清理与所有页面状态。

### Tests first

- [X] T028 [P] [US2] 扩展 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesReducerTest.kt`，覆盖目录优先、组内时间倒序、未知时间置尾、稳定排序和 Session 切换清空；修改文件：该测试文件；验收：当前排序行为下 RED，1,000 项重复运行结果一致。
- [X] T029 [P] [US2] 扩展 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt`，覆盖上传/下载锁、后台/断开/Session 切换取消、cleanup 不确定保持锁和旧结果丢弃；修改文件：该测试文件；验收：当前缺少完整锁状态时 RED，取消后无半成品成功。
- [X] T030 [P] [US2] 新建 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesStringsTest.kt`，覆盖页面专属中英键、占位参数、错误映射和图标可访问性；修改文件：该测试文件；验收：因 Feature 目录不存在而 RED，路径/文件名/技术码作为逐字参数不翻译。
- [X] T031 [P] [US2] 新建 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FilesPresentationTest.kt`，检查 HTML 路径栏、列表、行尾操作、上传入口、全状态，以及路径/文件名的超长、控制字符、双向文本和不可解码安全呈现；修改文件：该测试文件；验收：当前通用页面下 RED，行主体无 click 语义，loading/content/empty/error/cancelled/disconnected/unsupported/outcome-unknown/confirmation/progress 使用统一设计语法，显示投影不改变底层路径。

### Implementation

- [X] T032 [US2] 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt` 与 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt` 实现稳定排序、状态语义、Session 快照/路径/滚动锚点和传输锁描述；修改文件：上述 2 个文件；验收：T028、T029 全绿，本地排序不新增远端请求。
- [X] T033 [P] [US2] 新建 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesStrings.kt`；修改文件：该实现文件；验收：T030 全绿，目录只属于 `:feature:files` 且不直接呈现 `AdbError.userMessage/nextStep`。
- [X] T034 [US2] 按 `文件管理页/code.html` 重写 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt` 并接入共享安全原文呈现；修改文件：该实现文件；验收：T031 全绿，全状态、面包屑和行尾图标具 44 dp 语义触控区，1,000 项使用稳定 key 的 LazyColumn，异常路径/文件名不破坏布局或改变操作目标。
- [X] T035 [US2] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 与 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt` 接入 SAF 结果、页面可见性、语言和传输锁；修改文件：上述 2 个文件；验收：实际 I/O 中不能切页，picker 阶段不锁，同 Session 返回不重复全量加载。
- [X] T036 [US2] 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US2 模块与夹具验收；修改文件：无；验收：SC-003–SC-005 满足，排序/点击边界正确率 100%，所有正常和异常终态均真实可见。

**Checkpoint**: US2 独立可用，保留现有 Sync/SAF 边界且 UI 全状态完成。

---

## Phase 5: User Story 3 — 搜索、提取、管理和安装应用 (Priority: P1)

**Goal**: 严格还原应用页，补齐 APK 提取、单 APK 安装、卸载、强停、强制安装两阶段及逐组件部分成功。

**Independent Test**: 用普通/系统/未知、多 APK、无效 APK、同签名和签名不一致夹具验证搜索、按钮策略、逐文件提交、确认顺序、锁和全状态。

### Tests first

- [X] T037 [P] [US3] 扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationCapabilitiesTest.kt`，覆盖普通/系统/未知分类和 core 防御性拒绝；修改文件：该测试文件；验收：当前仅第三方快照语义下 RED，系统/未知只允许提取。
- [X] T038 [P] [US3] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/applications/ApkPackagePolicyTest.kt`，覆盖 base/split、独立 APK、`.apks/.xapk`、孤立 split、缺失 split 和签名关系；修改文件：该测试文件；验收：策略不存在时 RED，测试/错误不暴露签名材料。
- [X] T039 [P] [US3] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApkExtractionSessionManagerTest.kt`，覆盖组件发现、单 lease、进度、取消/超时/断开和旧 Session 结果；修改文件：该测试文件；验收：提取端口不存在时 RED，组件结果不包含原始远端路径。
- [X] T040 [P] [US3] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApkInstallPolicyTest.kt`，覆盖正常安装、同签名覆盖/降级、签名不一致二次确认、系统包策略拒绝和 no-root；修改文件：该测试文件；验收：预检策略不存在时 RED。
- [X] T041 [P] [US3] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApkInstallSessionManagerTest.kt`，覆盖远端暂存、安装验证、取消/超时/清理、重复点击 busy 和卸载成功安装失败的 no-rollback；修改文件：该测试文件；验收：安装 Session 端口不存在时 RED。
- [X] T042 [P] [US3] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationUninstallSessionManagerTest.kt`，覆盖私有数据删除语义、确认 nonce、系统基础包保留、拒绝和 outcome-unknown；修改文件：该测试文件；验收：卸载契约不存在时 RED。
- [X] T043 [P] [US3] 新建 `core/data/src/test/kotlin/com/sheen/adb/data/SafComponentOutputStoreTest.kt`，覆盖独立目录、逐组件暂存/验证/提交、已提交保留、当前未提交临时清理和逐项结果；修改文件：该测试文件；验收：当前无多组件存储端口时 RED，禁止跨文件原子、整体回滚或删除已提交文件的断言全部成立。
- [X] T044 [P] [US3] 扩展 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsPolicyTest.kt`，覆盖 draft/applied 搜索、名称/包名、普通四按钮顺序、系统/未知仅提取及启用状态；修改文件：该测试文件；验收：当前三动作/搜索语义下 RED。
- [X] T045 [P] [US3] 新建 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsTaskLifecycleTest.kt`，覆盖提取/安装锁、逐组件完整/部分/零提交、卸载确认、两阶段强制安装、取消不发送和旧包已删后失败；修改文件：该测试文件；验收：当前无任务状态机时 RED，部分成功后安全解锁且逐项结果完整。
- [X] T046 [P] [US3] 新建 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsStringsTest.kt`，覆盖页面专属中英键、破坏性确认、阶段进度、逐项结果和可访问性；修改文件：该测试文件；验收：Feature 目录不存在时 RED，包名/应用名/技术码逐字呈现。
- [X] T047 [P] [US3] 扩展 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsPresentationTest.kt`，检查 HTML 搜索、文本顺序、四按钮/补位、非加号安装图标、全状态，以及应用名/包名的超长、控制字符、双向文本和不可解码安全呈现；修改文件：该测试文件；验收：当前页面下 RED，loading/content/empty/error/cancelled/disconnected/unsupported/outcome-unknown/confirmation/progress/partial-success 使用统一设计语法，系统/未知隐藏按钮不占位且显示投影不改变包身份。

### Implementation

- [X] T048 [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt` 增加应用分类、APK 组件、提取/安装/卸载请求结果、阶段和 exclusive kind 项目契约；修改文件：上述 2 个文件；验收：T037–T042 编译进入行为断言，所有请求强制 expected Session 且不暴露 Kadb 类型。
- [X] T049 [P] [US3] 新建 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/applications/ApkPackagePolicy.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/applications/ApplicationPackageProtocol.kt`，实现组件/输入/签名策略并集中原始包管理命令；修改文件：上述 2 个文件；验收：T038、T040 策略用例全绿，Feature 中无原始命令。
- [X] T050 [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现 APK 组件解析和 exclusive lease 下的 Sync 提取；修改文件：上述 2 个文件；验收：T039 全绿，关闭/取消释放 reader 和 lease。
- [X] T051 [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/KadbProtocolClientFactory.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现单 APK 暂存、预检、覆盖/降级、验证和有界清理；修改文件：上述 2 个文件；验收：T041 全绿，无 Root、host adb 或 UI 直连。
- [X] T052 [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/ApplicationCapabilities.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现系统/未知分类、防御拒绝和确认后卸载；修改文件：上述 2 个文件；验收：T037、T042 全绿，系统基础包部分移除不报告完整卸载。
- [X] T053 [P] [US3] 新建 `core/data/src/main/kotlin/com/sheen/adb/data/SafComponentOutputStore.kt` 并扩展 `core/data/src/main/kotlin/com/sheen/adb/data/SafDocumentStore.kt`，实现逐组件独立暂存/验证/提交和当前临时资源清理；修改文件：上述 2 个文件；验收：T043 全绿，已提交组件在后续失败/取消时保留且整体状态按完整/部分/零提交区分。
- [X] T054 [US3] 在 `feature/apps/build.gradle.kts` 增加现有 `:core:data` 项目依赖，并扩展 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt` 的搜索、分类、确认、组件结果和任务状态；修改文件：上述 2 个文件；验收：无外部依赖，T044、T045 可编译到行为断言，模块归属符合 Plan。
- [X] T055 [US3] 新建 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsTasks.kt` 并扩展 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt`，编排 SAF、提取、安装、卸载、强停和 Session/锁生命周期；修改文件：上述 2 个文件；验收：T045 全绿，签名不一致未二次确认绝不卸载，逐组件结果不被压成单一成功。
- [X] T056 [US3] 在 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt` 与 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt` 实现 draft/applied 搜索、动作策略、状态恢复及语义错误映射；修改文件：上述 2 个文件；验收：T044 全绿，输入字符不触发远端全量读取。
- [X] T057 [P] [US3] 新建 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsStrings.kt`；修改文件：该实现文件；验收：T046 全绿，目录只属于 `:feature:apps`，不直接呈现 core 错误最终文案。
- [X] T058 [US3] 按 `应用管理页/code.html` 重写 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt` 并接入共享安全原文呈现；修改文件：该实现文件；验收：T047 全绿，四按钮、补位、非加号入口和全状态符合合同，异常应用名/包名不破坏布局、确认目标或请求身份。
- [X] T059 [US3] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 与 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt` 接入单 APK/目录 SAF、语言、进度和导航锁；修改文件：上述 2 个文件；验收：安装只接受一次单 APK 选择，提取与安装入口互不推断/复用，picker 阶段不锁。
- [X] T060 [US3] 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US3 模块与夹具验收；修改文件：无；验收：SC-006–SC-008 满足，逐组件结果、部分成功、系统策略和全部异常终态准确率 100%。

**Checkpoint**: US3 独立可用；提取与安装保持独立功能，提取结果不承诺可直接安装回去。

---

## Phase 6: User Story 4 — 搜索并结束进程 (Priority: P1)

**Goal**: 单搜索即时过滤、可见时 5 秒无重叠刷新、CPU/PSS 未知表达、结束范围选择及 UI 全状态。

**Independent Test**: 用虚拟时间和假进程网关持续可见 60 秒，验证最多 12 次非重叠刷新、离页零追加请求、PID/Session 防护和全状态呈现。

### Tests first

- [X] T061 [P] [US4] 扩展 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesAnalysisPolicyTest.kt`，覆盖单进程名过滤、未知 CPU/PSS、关联/不关联应用的结束范围和 Session 快照；修改文件：该测试文件；验收：当前查询模型下 RED。
- [X] T062 [P] [US4] 扩展 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesViewModelTest.kt`，用虚拟时间覆盖立即首刷、5 秒顺序刷新、慢请求不重叠、离页/后台/断开/Session 切换停止；修改文件：该测试文件；验收：当前手动刷新下 RED。
- [X] T063 [P] [US4] 新建 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesStringsTest.kt`，覆盖页面专属中英键、未知指标、范围确认、结果与可访问性；修改文件：该测试文件；验收：Feature 目录不存在时 RED，进程名/PID/技术码逐字呈现。
- [X] T064 [P] [US4] 扩展 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesPresentationTest.kt`，检查 HTML 单搜索、72 dp 行、指标 chip、结束按钮、全状态，以及进程名/PID 的超长、控制字符和双向文本安全呈现；修改文件：该测试文件；验收：当前页面下 RED，无搜索按钮，loading/content/empty/error/cancelled/disconnected/unsupported/outcome-unknown/confirmation/progress 设计一致，显示投影不改变确认绑定的进程身份。

### Implementation

- [X] T065 [US4] 在 `feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesViewModel.kt` 实现单查询、语义状态、5 秒顺序调度、同 Session 快照/滚动锚点和 PID 防护；修改文件：该实现文件；验收：T061、T062 全绿，60 秒不超过 12 次且从不重叠。
- [X] T066 [P] [US4] 新建 `feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesStrings.kt`；修改文件：该实现文件；验收：T063 全绿，目录只属于 `:feature:processes` 且 ViewModel 不持有最终翻译文案。
- [X] T067 [US4] 按 `进程管理/code.html` 重写 `feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesScreen.kt` 并接入共享安全原文呈现；修改文件：该实现文件；验收：T064 全绿，不可读指标显示本地化未知，全状态、范围确认和结果未知明确，异常进程名不破坏布局或操作身份。
- [X] T068 [US4] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 与 `feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesViewModel.kt` 接入 visible/hidden/foreground、语言和 Session 门禁；修改文件：上述 2 个文件；验收：离页/后台/断开后额外刷新为 0，同 Session 返回恢复查询/滚动后续刷。
- [X] T069 [US4] 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US4 模块与虚拟时间验收；修改文件：无；验收：SC-009、SC-010 满足，即时过滤 95%≤100 ms，取消发送数 0，PID 复用不误报成功。

**Checkpoint**: US4 独立可用，隐藏页面没有轮询且 UI 全状态完成。

---

## Phase 7: User Story 5 — 使用被控端 Shell 终端 (Priority: P1)

**Goal**: 单一交互式 Shell 子流、本地命令提交、高风险确认、特殊键阶段语义、一次性修饰、自动滚动和 UI 全状态。

**Independent Test**: 用假双向流执行普通命令、交互程序、高风险取消、特殊键、自动滚动开/关、超时、离页返回与 Session 切换，确认主 Session 不被关闭。

### Tests first

- [X] T070 [P] [US5] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/TerminalInputEncoderTest.kt`，覆盖 Esc/Tab/方向键和 Ctrl/Alt 一次性组合编码；修改文件：该测试文件；验收：编码器不存在时 RED，键名不得成为普通文本命令。
- [X] T071 [P] [US5] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/InteractiveShellProtocolTest.kt`，覆盖单一双向流、完整命令提交、实时语义输入、关闭输出和主连接保留；修改文件：该测试文件；验收：当前 one-shot 协议下 RED。
- [X] T072 [P] [US5] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/InteractiveShellSessionManagerTest.kt`，覆盖 expected Session、单子流、超时/取消/unsupported/outcome-unknown、旧输出丢弃和确定性关闭；修改文件：该测试文件；验收：交互 Session 端口不存在时 RED。
- [X] T073 [P] [US5] 扩展 `feature/shell/src/test/kotlin/com/sheen/adb/feature/shell/ShellCommandPolicyTest.kt`，覆盖本地 draft、高风险确认前零发送、取消保留文本和空命令不提交；修改文件：该测试文件；验收：当前 one-shot ViewModel 语义下 RED。
- [X] T074 [P] [US5] 新建 `feature/shell/src/test/kotlin/com/sheen/adb/feature/shell/ShellStateMachineTest.kt`，覆盖本地/remote-active 特殊键分流、一次性 Ctrl/Alt、离页清理、同 Session 分隔和 Session 切换清空；修改文件：该测试文件；验收：状态机不存在时 RED。
- [X] T075 [P] [US5] 扩展 `feature/shell/src/test/kotlin/com/sheen/adb/feature/shell/ShellTranscriptBufferTest.kt`，覆盖有界记录、即时过滤、清空仅本地、稳定顺序和 10 MiB 夹具；修改文件：该测试文件；验收：现有缓冲语义下 RED。
- [X] T076 [P] [US5] 新建 `feature/shell/src/test/kotlin/com/sheen/adb/feature/shell/ShellAutoScrollPolicyTest.kt`，覆盖开启时新输出跟随末尾、关闭时保留锚点、切换开启立即定位末尾及过滤/清空后的安全锚点；修改文件：该测试文件；验收：自动滚动策略不存在时 RED，关闭后任何新输出都不得强制改变阅读位置。
- [X] T077 [P] [US5] 新建 `feature/shell/src/test/kotlin/com/sheen/adb/feature/shell/ShellStringsTest.kt`，覆盖页面专属中英键、风险确认、流终态和可访问性；修改文件：该测试文件；验收：Feature 目录不存在时 RED，命令/输出/技术码及 Esc/Tab/Ctrl/Alt 逐字呈现。
- [X] T078 [P] [US5] 新建 `feature/shell/src/test/kotlin/com/sheen/adb/feature/shell/ShellPresentationTest.kt`，检查 HTML 工具栏、自动滚动开关、黑色终端、键栏/IME、全状态，以及命令/输出中的超长、控制字符、双向文本和不可解码安全呈现；修改文件：该测试文件；验收：当前页面下 RED，loading/content/empty/error/cancelled/disconnected/unsupported/outcome-unknown/confirmation/progress 与键序/44 dp 触控契约完整，呈现投影不得回流为待提交命令。

### Implementation

- [X] T079 [US5] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt` 增加 `InteractiveShellSession`、语义输入、输出事件、关闭原因和结构化错误；修改文件：上述 2 个文件；验收：T070–T072 编译进入行为断言，接口不暴露 Kadb 类型。
- [X] T080 [P] [US5] 新建 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/TerminalInputEncoder.kt` 并扩展 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/KadbProtocolClientFactory.kt` 的双向流适配；修改文件：上述 2 个文件；验收：T070、T071 全绿，编码仅存在 `:core:adb`。
- [X] T081 [US5] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现单子流所有权、写入串行化、输出边界和 child-only close；修改文件：上述 2 个文件；验收：T072 的 Session/代际/资源用例全绿，主 Session 保持连接。
- [X] T082 [US5] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbExceptionMapper.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 映射 unsupported/timeout/disconnect/protocol/outcome-unknown；修改文件：上述 2 个文件；验收：T072 所有失败路径为结构化结果且资源关闭。
- [X] T083 [P] [US5] 新建 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellTerminalState.kt` 并扩展 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellTranscriptBuffer.kt`，实现 draft/history/modifier/generation/有界记录与过滤；修改文件：上述 2 个文件；验收：T074、T075 全绿。
- [X] T084 [US5] 重构 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellViewModel.kt`，编排完整提交、高风险确认、实时/本地键、离页关闭、同 Session 新流分隔和语义终态；修改文件：该实现文件；验收：T073、T074 全绿，确认前包括换行在内发送 0 字节。
- [X] T085 [P] [US5] 新建 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellAutoScrollPolicy.kt`，实现输出代际、末尾锚点和显式开关策略；修改文件：该实现文件；验收：T076 全绿，关闭状态不会被新输出、过滤或重组强制滚动。
- [X] T086 [P] [US5] 新建 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellStrings.kt`；修改文件：该实现文件；验收：T077 全绿，目录只属于 `:feature:shell` 且 ViewModel 无最终翻译文案。
- [X] T087 [US5] 按 `shell终端/code.html` 重写 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellScreen.kt`，接入 `ShellAutoScrollPolicy` 与共享安全原文呈现；修改文件：该实现文件；验收：T076、T078 全绿，自动滚动、全状态、键栏和 IME 准确，异常命令/输出不破坏布局且显示投影不能再次执行。
- [X] T088 [US5] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 与 `feature/shell/src/main/kotlin/com/sheen/adb/feature/shell/ShellViewModel.kt` 接入可见性/前后台关闭、语言和同 Session 返回重开；修改文件：上述 2 个文件；验收：离页关闭 child 而不关闭主 Session，断开/切换 Session 清空全部终端上下文。
- [X] T089 [US5] 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US5 核心、Feature 和交互夹具验收；修改文件：无；验收：SC-011、SC-012 满足，自动滚动关闭后阅读锚点偏移为 0，逐键泄漏/修饰残留/旧流恢复均为 0。

**Checkpoint**: US5 独立可用，自动滚动行为有单独红测、实现和验收证据。

---

## Phase 8: User Story 6 — 按需查看并保存 Logcat (Priority: P1)

**Goal**: 显式开始、离页停止保留、四档即时过滤、Fatal 归一化、完整窗口保存和 UI 全状态。

**Independent Test**: 用 10 MiB 混合级别夹具验证未开始零读取、Fatal 阈值、离页静态快照、重开清空、全量保存、锁和所有页面状态。

### Tests first

- [X] T090 [P] [US6] 扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LogcatAnalysisTest.kt`，覆盖 Fatal 归一化解析及未知行不伪装严重度；修改文件：该测试文件；验收：当前 Fatal 未标准化时 RED，原始日志不进入断言输出。
- [X] T091 [P] [US6] 扩展 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatBufferTest.kt`，覆盖 raw/visible 分离、all/debug/info/error 阈值、Fatal 四档可见、全文过滤、清空全窗口和 10 MiB 边界；修改文件：该测试文件；验收：现有缓冲语义下 RED。
- [X] T092 [P] [US6] 新建 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatLifecycleTest.kt`，覆盖未开始零请求、显式开始单流、离页停止保留、返回不自启、重开清空和 Session 切换清空；修改文件：该测试文件；验收：当前生命周期下 RED。
- [X] T093 [P] [US6] 扩展 `core/data/src/test/kotlin/com/sheen/adb/data/LogcatOutputStoreTest.kt`，覆盖完整不可变窗口写入、过滤无关、取消/失败不成功和输出锁阶段；修改文件：该测试文件；验收：当前可见结果导出语义下 RED。
- [X] T094 [P] [US6] 新建 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatStringsTest.kt`，覆盖页面专属中英键、开始/停止/保存结果和可访问性；修改文件：该测试文件；验收：Feature 目录不存在时 RED，四级标签和日志原文始终逐字英文/原文。
- [X] T095 [P] [US6] 扩展 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatPresentationTest.kt`，检查 HTML 48 dp 工具栏、英文四级、删除/下载、黑色面板、全状态，以及日志原文中的超长、控制字符、双向文本和不可解码安全呈现；修改文件：该测试文件；验收：当前页面下 RED，never-started/loading/content/empty/error/cancelled/disconnected/unsupported/outcome-unknown/progress 与宽屏副面板使用统一设计语法，显示投影不改变完整保存快照。

### Implementation

- [X] T096 [P] [US6] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/diagnostics/StructuredLogcatParser.kt` 实现 Fatal 标准严重度；修改文件：该实现文件；验收：T090 全绿，`:core:adb` 只归一化结构而不选择 UI 过滤档位。
- [X] T097 [P] [US6] 在 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatBuffer.kt` 与 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatViewModel.kt` 分离原始窗口和派生显示过滤；修改文件：上述 2 个文件；验收：T091 全绿，Fatal 在四档均按最低严重度语义出现，输入不重启采集。
- [X] T098 [US6] 在 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatViewModel.kt` 实现显式 Start、窗口 ID、离页停止保留、重开清空、10 分钟/10 MiB 终态和 Session 清理；修改文件：该实现文件；验收：T092 全绿，隐藏页追加日志数为 0。
- [X] T099 [P] [US6] 在 `core/data/src/main/kotlin/com/sheen/adb/data/LogcatOutputStore.kt` 实现完整快照的 SAF 暂存/验证/提交与写入阶段；修改文件：该实现文件；验收：T093 全绿，过滤隐藏行仍全部写入且 picker 阶段不报告锁。
- [X] T100 [P] [US6] 新建 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatStrings.kt`；修改文件：该实现文件；验收：T094 全绿，目录只属于 `:feature:logcat` 且无最终文案进入 ViewModel。
- [X] T101 [US6] 按 `logcat页/code.html` 重写 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt` 并接入共享安全原文呈现；修改文件：该实现文件；验收：T095 全绿，工具栏顺序、英文级别、全状态和宽屏分支准确，异常日志不破坏布局且呈现投影不改变完整窗口导出内容。
- [X] T102 [US6] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 与 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatViewModel.kt` 接入 visible/hidden/foreground、语言和保存锁；修改文件：上述 2 个文件；验收：离页允许但立即停采，实际保存写入期间禁止切页，返回仅显示静态快照。
- [X] T103 [US6] 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US6 模块与 10 MiB 夹具验收；修改文件：无；验收：SC-013–SC-015 满足，Fatal 映射正确率 100%，保存内容与操作时完整窗口一致。

**Checkpoint**: US6 独立可用，未点击 Start 时 Logcat 读取次数为 0，Fatal 无新增按钮。

---

## Phase 9: User Story 7 — 修复连接、配对和截屏反馈 (Priority: P1)

**Goal**: 错误可关闭；QR/配对码为根浮层；30 秒 NSD 配对端口扫描、显式六位码提交、本机配对提示和截屏/录屏输出锁正确。

**Independent Test**: 用已配对/未配对/本机配对假服务和虚拟时间验证浮层、扫描、提交、清理、双语、错误关闭及截屏/录屏保存。

### Tests first

- [X] T104 [P] [US7] 扩展 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentationTest.kt`，覆盖错误 dismiss、未连接全状态、设备标签/安全技术码的超长、控制字符和双向文本安全呈现，以及关闭错误时保留输入、发现结果和 Session；修改文件：该测试文件；验收：当前错误不可关闭、状态覆盖不足或原文呈现不安全时 RED；本测试不得承担截屏/录屏呈现职责。
- [X] T105 [P] [US7] 扩展 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingReducerTest.kt`，覆盖 QR→code、扫描/发现/输入/显式提交/超时/重试、六位 ASCII 数字和敏感值清理；修改文件：该测试文件；验收：当前 reducer 状态不足时 RED。
- [X] T106 [P] [US7] 扩展 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt`，覆盖已配对连接、未配对浮层、本机扫描、Pair 单次提交、IME 不提交和并发重试拒绝；修改文件：该测试文件；验收：当前内嵌/自动流程下 RED。
- [X] T107 [P] [US7] 新建 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/PairingDiscoveryWindowTest.kt`，用虚拟时间覆盖 30 秒 NSD pairing service、唯一 attempt、超时停止、显式重试和 opaque endpoint；修改文件：该测试文件；验收：专用窗口不存在时 RED，禁止子网/顺序端口探测。
- [X] T108 [P] [US7] 新建 `app/src/test/kotlin/com/sheen/adbhelper/RootPairingOverlayTest.kt`，覆盖根浮层、外点/返回/后台/Session 切换关闭和敏感状态清理；修改文件：该测试文件；验收：当前配对 UI 位于页面内容时 RED。
- [X] T109 [P] [US7] 新建 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesStringsTest.kt`，覆盖错误、QR/code/local、扫描精确中文/等价英文、确认和可访问性；修改文件：该测试文件；验收：Feature 目录不存在时 RED，端点/代码材料不写入测试输出。
- [X] T110 [P] [US7] 扩展 `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/QuickActionUseCaseTest.kt` 与 `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/QuickActionPresentationTest.kt`，覆盖截屏/录屏 picker 不锁、实际写入锁、后台取消、安全/不确定清理、截屏完成只保留保存入口，以及 artifact 标签/安全技术码的异常文本呈现；修改文件：上述 2 个文件；验收：当前 Overview 未暴露输出锁、仍显示错误录屏提示或原文呈现不安全时 RED；Devices 测试不得重复承担这些断言。
- [X] T111 [P] [US7] 新建 `feature/overview/src/test/kotlin/com/sheen/adb/feature/overview/OverviewStringsTest.kt`，覆盖截屏/录屏/重启、保存阶段和结果的中英语义；修改文件：该测试文件；验收：Feature 目录不存在时 RED，`:feature:devices` 不拥有 connected quick-action 文案。

### Implementation

- [X] T112 [US7] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/PairingModels.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 增加 30 秒配对服务发现 attempt、opaque endpoint 和显式提交契约；修改文件：上述 2 个文件；验收：T107 编译进入行为断言，UI 不接触 host/port 命令。
- [X] T113 [US7] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt` 与 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/pairing/PairingLifecycle.kt` 实现无重叠 30 秒窗口、取消/超时/重试和 secret 清零；修改文件：上述 2 个文件；验收：T107 全绿，无子网或顺序端口探测。
- [X] T114 [P] [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingModels.kt` 与 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingReducer.kt` 实现 QR/code/local 状态机和六位码显式提交门禁；修改文件：上述 2 个文件；验收：T105 全绿，输入变化和 IME 请求数为 0。
- [X] T115 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 与 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentation.kt` 编排路由、本机扫描、超时重试及敏感清理；修改文件：上述 2 个文件；验收：T106 全绿，快速重复 Pair 只产生一次请求。
- [X] T116 [P] [US7] 新建 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesStrings.kt`；修改文件：该实现文件；验收：T109 全绿，目录只属于 `:feature:devices`，不得接管 connected Overview 文案。
- [X] T117 [P] [US7] 新建 `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewStrings.kt`；修改文件：该实现文件；验收：T111 全绿，目录只属于 `:feature:overview`，版本目标页不直接呈现 core 最终错误文案。
- [X] T118 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentation.kt` 与 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt` 实现错误关闭、未连接全状态和设备/技术值安全呈现；修改文件：上述 2 个文件；验收：T104 全绿，关闭错误不改变输入/扫描/Session，Devices 不包含截屏/录屏业务或最终文案。
- [X] T119 [US7] 在 `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionModels.kt` 与 `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewViewModel.kt` 实现截屏/录屏输出阶段、导航锁描述和语义结果；修改文件：上述 2 个文件；验收：T110 全绿，capture/picker 不锁，写入至安全清理锁定。
- [X] T120 [US7] 在 `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/QuickActionPresentation.kt` 与 `feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/OverviewScreen.kt` 接入 Overview 目录、保存按钮、真实写入状态和 artifact/技术值安全呈现；修改文件：上述 2 个文件；验收：T110 全绿，截屏成功只保留既有保存入口，无错误录屏提示，语言切换不重启 capture/export。
- [X] T121 [US7] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 与 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt` 将 QR/code/local 配对内容承载为 App 根级浮层；修改文件：上述 2 个文件；验收：T108 全绿，浮层不占页面内容流，外点、返回、后台或 Session 切换均停止临时观察并清零秘密；本任务不接入 Overview 输出锁。
- [X] T122 [US7] 在 `app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 接入 Overview 暴露的截屏/录屏写入状态并交给 `DeliveryLockAggregator`，随后按 `specs/007-v1-ui-refactor/quickstart.md` 执行 US7 模块与虚拟时间验收；修改文件：该实现文件；验收：T024、T104–T111 全绿，capture/picker 不锁、实际写入至安全清理期间锁定，SC-016–SC-019 满足且敏感临时值残留为 0。

**Checkpoint**: US7 独立可用，连接修复不扩大网络发现、权限或模块边界。

---

## Phase 10: Polish & Cross-Cutting Concerns — 分层证据与发布收口

**Purpose**: 将设计、自动化、性能、双语、视觉、真机、安全和发布证据写入互不冒充的文件。

- [X] T123 运行 `app/src/test/kotlin/com/sheen/adbhelper/VersionContractTest.kt` 并核对菜单/设置/关于的 BuildConfig 派生展示；修改文件：无；验收：所有表面仅显示 `v1.0`，无旧版号、占位版号或 `vv1.0`。
- [X] T124 运行 `app/src/test/kotlin/com/sheen/adbhelper/V1ArchitectureBoundaryTest.kt` 与 `app/src/test/kotlin/com/sheen/adbhelper/StrictUiDesignContractTest.kt`；修改文件：无；验收：ADB、语言、导航、SAF、Overview/Devices 和 Feature 文案归属全部符合 Plan，无 WebView/远程资源/越权依赖。
- [X] T125 [P] 依据通过 T001 复核的文件页设计源完成紧凑/宽屏及九类状态视觉对比，并新建 `docs/archive/releases/v1.0/visual/files.md`；修改文件：`docs/archive/releases/v1.0/visual/files.md`；验收：结构、尺寸、操作位置、层级、字形、间距、圆角、状态和双语差异均有结论，未通过项不得写为 PASS。
- [X] T126 [P] 依据通过 T001 复核的应用页设计源完成紧凑/宽屏及十一类状态视觉对比，并新建 `docs/archive/releases/v1.0/visual/apps.md`；修改文件：`docs/archive/releases/v1.0/visual/apps.md`；验收：搜索、文本顺序、四按钮/补位、非加号入口、部分成功和双语均有脱敏结论。
- [X] T127 [P] 依据通过 T001 复核的进程页设计源完成紧凑/宽屏及九类状态视觉对比，并新建 `docs/archive/releases/v1.0/visual/processes.md`；修改文件：`docs/archive/releases/v1.0/visual/processes.md`；验收：搜索、72 dp 行、chip、未知值、结束确认、状态和双语均有结论。
- [X] T128 [P] 依据通过 T001 复核的 Shell 页设计源完成紧凑/宽屏、九类状态和自动滚动视觉/交互对比，并新建 `docs/archive/releases/v1.0/visual/shell.md`；修改文件：`docs/archive/releases/v1.0/visual/shell.md`；验收：工具栏、终端、键栏、自动滚动开/关、IME、状态和双语均有结论。
- [X] T129 [P] 依据通过 T001 复核的 Logcat 页设计源完成紧凑/宽屏及十类状态视觉对比，并新建 `docs/archive/releases/v1.0/visual/logcat.md`；修改文件：`docs/archive/releases/v1.0/visual/logcat.md`；验收：48 dp 工具栏、英文级别、删除/下载、黑色面板、Fatal 结果、状态和双语均有结论。
- [X] T130 [P] 依据通过 T001 复核的连接页设计源完成 scoped 修复视觉对比，并新建 `docs/archive/releases/v1.0/visual/connection.md`；修改文件：`docs/archive/releases/v1.0/visual/connection.md`；验收：仅记录错误关闭、根配对浮层、全状态和截屏反馈的授权差异。
- [X] T131 按 `specs/007-v1-ui-refactor/quickstart.md` 完成简中/English 全页、紧凑/宽屏和运行中切换验收，并新建 `docs/archive/releases/v1.0/localization-acceptance.md`；修改文件：`docs/archive/releases/v1.0/localization-acceptance.md`；验收：可见文本/content description 覆盖 100%，技术词保持逐字，切换后业务身份和请求计数不变。
- [X] T132 按 `specs/007-v1-ui-refactor/quickstart.md` 执行 1,000 列表项、10 MiB Shell/Logcat、5 秒刷新和自动滚动夹具，并新建 `docs/archive/releases/v1.0/fixture-performance.md`；修改文件：`docs/archive/releases/v1.0/fixture-performance.md`；验收：记录夹具/工具/配置，95% 本地反馈≤100 ms、隐藏页请求为 0、刷新/流不重叠，不冒充真机 UX。
- [X] T133 使用固定 JDK 执行全部 TestNG、模块测试与 `:app:assembleDebug`，并新建 `docs/archive/releases/v1.0/automated-verification.md`；修改文件：`docs/archive/releases/v1.0/automated-verification.md`；验收：等待 Gradle 明确退出码，记录命令/结论/失败/跳过，自动化结果不冒充视觉、真机或发布证据。
- [X] T134 按 `specs/007-v1-ui-refactor/quickstart.md` 在三台既有被控端执行配对、文件、应用、进程、Shell、Logcat、导航锁、FR-004 和双语验收，并新建 `docs/archive/releases/v1.0/real-device-acceptance.md`；修改文件：`docs/archive/releases/v1.0/real-device-acceptance.md`；验收：逐设备画像只记录脱敏结论，不含真实 IP、配对材料、包名上下文、Shell/Logcat 原文、密钥或签名数据，未执行项保持 NOT RUN。
- [X] T135 依据 `docs/权限矩阵.md`、`docs/architecture/security-and-delivery.md` 和 ADR 0004 执行权限、依赖、纯本地与许可证审计，并新建 `docs/archive/releases/v1.0/release-gates.md`；修改文件：`docs/archive/releases/v1.0/release-gates.md`；验收：无新增权限/外部依赖/业务网络；GPL 传递依赖未解决时明确标记不可发布，不以构建或真机成功覆盖。
- [X] T136 汇总 T001、T123–T135 的独立门禁并新建 `docs/archive/releases/v1.0/closeout.md`；修改文件：`docs/archive/releases/v1.0/closeout.md`；验收：分别列出功能、自动化、性能、视觉、真机、安全和发布状态及证据链接，任何缺失/失败门禁不得被“整体完成”措辞掩盖。

---

## Phase 11: P1 真机回归修复 — 远端进程、断链与 Android 11+ 发现

- [X] T137 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ProcessSessionManagerTest.kt` 增加跨页面排队和远端多命令指标降级的 RED 测试；修改文件：`core/adb/src/test/kotlin/com/sheen/adb/core/internal/ProcessSessionManagerTest.kt`；验收：测试证明单条子命令均未超时时，整轮指标采样不得因固定 5 秒总预算返回 `ADB_TIMEOUT`，且上一页命令收尾不得消耗本页子命令预算。
- [X] T138 调整 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 的进程刷新总预算，保留每条命令独立超时、取消和 Session 代际校验；修改文件：`core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T137 转绿，进程刷新仍在 30 秒硬上限内结束，超时/取消只关闭命令子流。
- [X] T139 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManagerTest.kt` 增加空闲远端断链的 RED 测试；修改文件：`core/adb/src/test/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManagerTest.kt`；验收：远端传输失效后无需用户再次操作即可发布 `Disconnected`，关闭旧 Client 且不泄露端点或命令输出。
- [X] T140 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 增加低频、有界、连续失败判定的 Session 健康检查，并在长任务或交互 Shell 活跃时跳过；修改文件：`core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`；验收：T139 转绿，瞬时单次失败不误断，确认断链后根状态回到未连接，既有页面导航守卫可自动返回连接页。
- [X] T141 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/NsdDiscoveryAdapterTest.kt` 增加“过期配对记录不得遮蔽 Android 11+ connect 服务”的 RED 测试；修改文件：`core/adb/src/test/kotlin/com/sheen/adb/core/internal/NsdDiscoveryAdapterTest.kt`；验收：同轮发现中 pairing 解析失败后，`_adb-tls-connect._tcp` 仍可解析并发布。
- [X] T142 调整 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt` 的解析失败隔离语义；修改文件：`core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt`；验收：T141 转绿，单条过期 DNS-SD 记录只局部失败，网络切换、权限失败和平台级发现失败仍终止本轮并清理资源。
- [X] T143 使用 `emulator-5554` 执行安装、启动、本机连接、应用/进程/文件/终端/日志页面快速切换、断开后回连接页的全流程回归；修改文件：0；验收：所有 ADB 命令显式指定 `-s emulator-5554`，无闪退、无错误 Session 复用、进程指标可刷新；二维码、配对码和真实 Android 11+ mDNS 发现明确保留为真机外部验收。

---

## Phase 12: P1 Android 11+ 远程会话二次回归修复

- [X] T144 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManagerTest.kt` 增加“功能命令阻塞时远端断链”的 RED 测试；修改文件：上述 1 个文件；验收：旧实现因健康探测被 Session 互斥锁阻塞而失败，同时保留单个命令子流 I/O 失败可恢复的既有契约。
- [X] T145 调整 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 的健康探测和确认断链后的退休顺序；修改文件：上述 1 个文件；验收：T144 转绿，健康探测可与挂起子命令并行，确认整条传输断链时先关闭失效传输释放命令，再原子清理 Session；单个命令子流失败但健康探测成功时仍允许 Session 复用。
- [X] T146 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/NsdDiscoveryAdapterTest.kt` 增加 Android 平台 `.local.` DNS-SD 类型规范化 RED 测试；修改文件：上述 1 个文件；验收：`_adb-tls-connect._tcp.local.` 在旧实现中被拒绝，证明合法 Android 11+ connect 服务可能被过滤。
- [X] T147 调整 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/NsdDiscoveryPolicy.kt` 的 DNS-SD 类型规范化；修改文件：上述 1 个文件；验收：T146 转绿，根标签与 `.local` 域后缀被规范化为批准的 `_adb-tls-connect._tcp`，不扩大批准服务类型集合。
- [ ] T148 使用两台独立 API 37 模拟器执行主控端→被控端跨实例连接、应用→进程切页、700 ms 单向延迟、远端断链和未连接态回归，并运行 `:core:adb:testDebugUnitTest` 与 `:app:assembleDebug`；修改文件：无；验收：所有 ADB 命令显式指定 `emulator-5554` 或 `emulator-5556`，明文跨实例链路无进程超时/闪退，断链后主动回到未连接态；Emulator 因 Wi‑Fi 不可用而无法启用 Android 11+ TLS 配对时，必须保留该项为真机外部验收，不得用 5555 冒充 TLS 证据。

---

## Phase 13: P1 发现端口语义、远程配对自动连接与错误详情

- [X] T149 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentationTest.kt` 增加错误详情可见性的 RED 测试；修改文件：上述 1 个文件；验收：“详情”必须打开应用内脱敏详情弹层，弹层可关闭并可显式复制，不能继续以无反馈的剪贴板写入冒充详情展示。
- [X] T150 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt` 实现错误详情弹层；修改文件：上述 1 个文件；验收：T149 转绿，错误卡右上角关闭仅隐藏错误卡，“详情”显示脱敏技术码与详情，详情弹层关闭不改变输入、发现或 Session。
- [X] T151 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducerTest.kt` 增加发现列表与点击路由 RED 测试；修改文件：上述 1 个文件；验收：配对服务不形成可见列表项，列表只呈现已解析调试服务；未知动态调试端口确认后打开二维码配对，已验证调试服务与 5555 确认后进入连接。
- [X] T152 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryModels.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducer.kt` 实现调试端口投影与安全路由模型；修改文件：上述 2 个文件；验收：T151 转绿，不以相同名称或地址推断设备身份，过期目标不可操作，5555 旧设备保持直接连接。
- [X] T153 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt` 增加未知动态调试端口复用根级二维码浮层、5555 直接连接的 RED 测试；修改文件：上述 1 个文件；验收：动态端口不提交伪造配对目标且显示二维码浮层，5555 只调用一次发现目标连接。
- [X] T154 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现发现路由效果；修改文件：上述 1 个文件；验收：T153 转绿，配对浮层仍可在二维码与配对码之间切换，切换到配对码后才建立最长 30 秒的配对端口扫描。
- [X] T155 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LanDiscoverySessionManagerTest.kt` 增加二维码成功后可按尝试解析对应调试服务并自动连接的 RED 测试；修改文件：上述 1 个文件；验收：旧实现因二维码成功未登记临时关联而失败，且测试不得持久化地址、配对材料或密钥。
- [X] T156 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 登记二维码成功的内存态配对关联并复用受控调试服务解析；修改文件：上述 1 个文件；验收：T155 转绿，只按当前尝试与已解析地址候选连接，取消、失败、超时或身份不一致均不保留成功态并清理 Session。
- [X] T157 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt` 增加二维码和发现配对码成功后自动连接的 RED 测试；修改文件：上述 1 个文件；验收：两种非本机配对成功均恰好触发一次调试服务解析/连接，连接成功才关闭浮层并显示已连接，失败显示可关闭错误且不伪装为成功。
- [X] T158 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现非本机配对自动连接生命周期；修改文件：上述 1 个文件；验收：T157 转绿，自动连接支持取消和超时，不与前台发现建立重叠扫描，敏感值在提交后清理。
- [X] T159 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPresentationTest.kt` 增加“仅显示调试端口”与中英双语操作语义 RED 测试；修改文件：上述 1 个文件；验收：展示模型不输出“配对服务”行或配对端口，中文和 English 均准确区分“配对后连接”与“直接连接”。
- [X] T160 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt` 对齐发现列表与确认弹层；修改文件：上述 1 个文件；验收：T159 转绿，行与右侧图标执行同一路由，确认弹层可关闭且不会泄漏不可见配对端口。
- [X] T161 使用 `emulator-5554`（Android 16 主控）、`emulator-5556`（Android 12 被控）和 `emulator-5558`（Android 9 被控）执行显式串号的跨实例验收，并运行相关单测、`:core:adb:testDebugUnitTest` 与 `:app:assembleDebug`；修改文件：无；验收：先依据实际中文或英文系统界面切换 Wi‑Fi，再验证 Android 12 动态调试端口只显示调试服务、未配对进入浮层、二维码/配对码成功自动连接，以及 Android 9 的 5555 直接连接；模拟器不支持的 TLS/二维码外部交互必须如实标记，不得用 5555 结果替代。
- [X] T162 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentationTest.kt` 增加连接技术码与输入错误去重的 RED 测试；修改文件：上述 1 个文件；验收：同一连接失败不得同时渲染通用输入错误卡和带详情的结构化连接错误卡，独立输入校验错误仍可显示。
- [X] T163 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/ConnectionPagePresentation.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt` 实现错误卡去重；修改文件：上述 2 个文件；验收：T162 转绿，失败后恢复可操作状态且保留唯一可关闭、可查看详情的结构化错误。
- [X] T164 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt` 增加从未知动态调试服务进入二维码浮层前释放前台发现的 RED 测试；修改文件：上述 1 个文件；验收：确认未配对调试服务后，旧 LAN 发现订阅必须先归零，再启动二维码配对发现，避免真实 `NsdManager` 的重叠扫描冲突。
- [X] T165 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现扫描结果到根级配对浮层的串行切换；修改文件：上述 1 个文件；验收：T164 转绿，二维码与配对码仍复用同一浮层，取消后可恢复前台扫描。
- [X] T166 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LanDiscoverySessionManagerTest.kt` 增加 NSD 停止回调延迟、首轮发现被拒绝的 RED 测试；修改文件：上述 1 个文件；验收：自动连接在总超时内重试瞬态发现失败，且测试不泄漏地址或配对材料。
- [X] T167 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现配对后有界重试与确定性调试服务选择；修改文件：上述 1 个文件；验收：T166 转绿，重试受 30 秒总时限及取消控制，配对服务绝不作为连接候选，最终只建立一个 ADB Session。
- [X] T168 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/NsdDiscoveryAdapterTest.kt` 增加 Android 13+ 绑定当前网络时仍持有 Wi‑Fi 组播锁的 RED 测试；修改文件：上述 1 个文件；验收：现代系统的前台 LAN 发现同时绑定当前网络并持有可释放的组播锁，使跨设备 `_adb-tls-connect._tcp` 广播可达。
- [X] T169 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/NsdDiscoveryPolicy.kt` 修正现代 Android 的组播接收策略；修改文件：上述 1 个文件；验收：T168 转绿，网络不可用时不申请锁，停止、取消、超时、网络变化和重启均恰好释放一次。

## Phase 14: P1 TAP 跨实例全流程回归修复

- [X] T170 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt` 增加非本机配对后首次调试服务解析失败必须新建发现窗口重试的 RED 测试；修改文件：上述 1 个文件；验收：旧发现订阅已停止或拒绝时不会复用失效句柄，取消和 30 秒总超时仍能终止。
- [X] T171 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现配对后自动连接的全新发现重试；修改文件：上述 1 个文件；验收：T170 转绿，二维码和配对码成功后都只建立一个调试 Session，配对端口不进入连接候选。
- [X] T172 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LanDiscoverySessionManagerTest.kt` 增加配对发现快照短期保留与动态调试端口优先于同主机 5555 的 RED 测试；修改文件：上述 1 个文件；验收：停止配对发现后仍可在有界窗口解析对应 connect 服务，动态服务存在时不被旧版探测结果遮蔽。
- [X] T173 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现配对发现快照缓存和确定性动态端口选择；修改文件：上述 1 个文件；验收：T172 转绿，缓存仅驻留内存、按尝试和时限失效，不持久化地址或配对材料。
- [X] T174 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LegacyAdb5555DiscoveryTest.kt` 增加多网络地址源、候选上限与子网轮询公平性的 RED 测试；修改文件：上述 1 个文件；验收：TAP、Emulator NAT 和实际 Wi‑Fi 同时存在时不会只扫描默认网络，也不会由首个 `/24` 耗尽全部预算。
- [X] T175 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/LegacyAdb5555Scanner.kt` 实现多网络旧版 5555 扫描；修改文件：上述 2 个文件；验收：T174 转绿，候选总量有界、多个子网轮询取样、取消后停止，Android 11+ 配对服务仍不进入可见调试列表。
- [X] T176 [P] 在 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsTaskLifecycleTest.kt` 增加 SAF picker 生命周期租约、APK 传输前停止后台应用读取以及安装后刷新快照的 RED 测试；修改文件：上述 1 个文件；验收：picker 的 ON_STOP 不取消 Session 子流，提取/安装独占传输前不残留元数据读取，安装成功后立即可管理新应用。
- [X] T177 在 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt`、`feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt` 实现稳定 picker 租约和应用任务读写隔离；修改文件：上述 2 个文件；验收：T176 转绿，取消或完成 picker 后保持连接，APK 提取真实写入 SAF，强制安装完成后列表自动刷新。
- [X] T178 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/InteractiveShellSessionManagerTest.kt` 增加进程轮询取消收尾与交互 Shell 创建必须串行的 RED 测试；修改文件：上述 1 个文件；验收：旧实现允许一次性 Shell 子流关闭和交互子流创建并发，测试明确约束锁顺序且禁止关闭主 Session。
- [X] T179 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 串行化共享 Session 上的交互 Shell 创建与一次性命令子流收尾；修改文件：上述 1 个文件；验收：T178 转绿，应用/进程/文件/终端快速切换至少 8 次不再断连，交互 Shell 生命周期仍只关闭子资源。
- [X] T180 [P] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesConnectionViewModelTest.kt` 增加远端 Session 丢失后清除旧“连接成功”提示的 RED 测试；修改文件：上述 1 个文件；验收：断链状态不再同时显示成功提示，主动断开仍无重复提示。
- [X] T181 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 清理断链后的瞬态成功提示；修改文件：上述 1 个文件；验收：T180 转绿，确认断链后连接页只呈现当前未连接状态，错误卡和详情语义不受影响。
- [X] T182 [P] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt` 增加文件 picker 停止保护和上传成功后目录快照刷新的 RED 测试；修改文件：上述 1 个文件；验收：打开系统选择器不取消目录子流，上传提交且释放独占租约后重新读取当前目录。
- [X] T183 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt` 实现文件 picker 生命周期保护和提交后刷新；修改文件：上述 1 个文件；验收：T182 转绿，SAF 返回后 Session 保持，上传文件真实存在并在当前列表可见，真实后台停止仍取消进行中的传输。
- [X] T184 在项目根目录新增 `AI_AGENT_TAP_EMULATOR_REGRESSION_PROMPT.md`，记录不含真实端点或配对材料的三模拟器复现提示词；修改文件：上述 1 个文件；验收：明确 Android 12/16 TAP 地址属于 `eth0`、Android 9 使用受控网桥 relay、逐级验证 ARP/ICMP/TCP 和两项最高优先级产品路径。
- [X] T185 使用 `emulator-5554`、`emulator-5556`、`emulator-5558` 完成 TAP/relay 跨实例全功能回归并强制重跑全量测试与 debug 构建；修改文件：0；验收：动态配对成功后自动连接、列表不显示配对端口、5555 直接连接、文件双向传输、应用全操作、进程指标、Shell、Logcat、快速切页和远端断链均通过；QR 摄像头与通知厂商行为保留为真机外部证据。
- [X] T186 [P] 在 `feature/files/src/test/kotlin/com/sheen/adb/feature/files/FileTaskLifecycleTest.kt`、`app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 增加 ActivityResult 与宿主 stop/start 乱序的 picker 租约 RED 测试；修改文件：上述 2 个文件；验收：回调先启动下载、随后到达 ON_STOP 时仍不取消传输，租约只能在真实 ON_START 后释放。
- [X] T187 在 `feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesViewModel.kt`、`app/src/main/kotlin/com/sheen/adbhelper/MainActivity.kt` 实现跨 Activity 生命周期的文件 picker 租约；修改文件：上述 2 个文件；验收：T186 转绿，上传和下载从 SAF 返回后均保持主 Session，真实后台停止仍能取消非 picker 长任务并清理资源。
- [X] T188 [P] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/KadbRemoteFileProtocolTest.kt` 增加下载前后 `stat` 与 `recv` 必须复用单个 Sync 子流的 RED 测试；修改文件：上述 1 个文件；验收：旧实现因连续打开三个 Sync 子流而失败，新契约在一个子流中返回传输前后元数据、字节数并关闭子资源。
- [X] T189 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbProtocolAdapter.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现可靠元数据的单 Sync 下载与降级摘要回退；修改文件：上述 2 个文件；验收：T188 与文件 Session 回归测试转绿，Android 12 小文件真实下载写入 SAF 且主 Session 保持，低能力 Sync 仍在写入前后执行摘要完整性校验。

## Phase 15: P1 页面加载门禁、Shell 与 Logcat 交互回归

- [X] T190 [P] [US1] 在 `app/src/test/kotlin/com/sheen/adbhelper/AppNavigationPolicyTest.kt` 增加页面加载门禁 RED 测试；修改文件：上述 1 个文件；验收：应用/进程页仍有设备读取时仅保留最新目标并等待，读取完成后自动放行，超过有界等待时间后要求取消当前读取并进入恢复期，恢复期结束才允许切页。
- [X] T191 [P] [US3] 在 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsTaskLifecycleTest.kt` 增加应用元数据读取属于页面稳定期的 RED 测试；修改文件：上述 1 个文件；验收：列表读取结束但元数据流仍活跃时状态继续声明页面加载中，元数据完成、取消、失败或 Session 切换后必定释放。
- [X] T192 [P] [US6] 在 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatPresentationTest.kt` 增加过滤输入可见与整块日志共享横向滚动的 RED 测试；修改文件：上述 1 个文件；验收：过滤框保留可读前景色、光标和紧凑屏幕最小宽度，横向滚动状态只在日志视口创建一次且所有日志行同步移动，禁止每行独立创建滚动状态。
- [X] T193 [US3] 在 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt`、`feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt` 实现应用页面稳定期状态与所有终止路径清理；修改文件：上述 2 个文件；验收：T191 转绿，应用页元数据后台读取未完成时不会被误判为空闲，离页取消与 Session 变化不遗留加载状态。
- [X] T194 [US1] 在 `app/src/main/kotlin/com/sheen/adbhelper/AppNavigationPolicy.kt`、`app/src/main/kotlin/com/sheen/adbhelper/AppNavigationModels.kt` 实现纯状态页面加载门禁及有界等待/恢复决策；修改文件：上述 2 个文件；验收：T190 转绿，连续点击只保留最后目标，正常完成立即切换，超时先取消当前读取并留出资源清理期，门禁不会永久卡死。
- [X] T195 [US1] 在 `app/src/main/kotlin/com/sheen/adbhelper/AppStrings.kt`、`app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt` 接入双语非阻塞等待提示、立即取消并切换操作和自动恢复；修改文件：上述 2 个文件；验收：应用/进程读取期间底栏与手势请求均进入同一门禁，用户能看到正在完成当前加载和倒计时，可主动取消，超时自动恢复，切页后不并发启动下一页 ADB 读取。
- [X] T196 [US6] 在 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt` 实现可见过滤输入和日志视口共享横向滚动；修改文件：上述 1 个文件；验收：T192 转绿，紧凑屏幕可看到已输入过滤字符，每条日志保持单行且拖动任意位置时所有行作为一个日志平面同步横移。
- [ ] T197 [US5] [US6] 使用本机 Android 16 主控模拟器与隔离测试目标完成 Shell、Logcat、应用/进程快速切页回归，并强制重跑相关测试、全量测试与 Debug 构建；修改文件：0；验收：Shell 命令执行、清空/过滤/自动滚动/特殊键及离页清理正常，Logcat 按需开始/离页停止/即时过滤/整块横移/清空/保存正常，快速切页无 Session 断开或 `ADB_IP_FAILURE`，加载超时与主动取消均可恢复。

## Phase 16: P1 多设备配对目标关联与自动连接修复

- [X] T198 [P] [US7] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/WirelessDiscoveryCoreTest.kt` 增加多设备配对目标选择 RED 测试；修改文件：上述 1 个文件；验收：选中的 TLS 调试服务只能关联同一设备 GUID/地址的配对码服务，不能取发现列表第一项；无匹配时等待，多匹配时返回歧义。
- [X] T199 [P] [US7] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducerTest.kt`、`feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt` 增加未配对调试设备到配对浮层的目标保持与手动端点覆盖 RED 测试；修改文件：上述 2 个文件；验收：点击未配对调试设备后 QR/配对码切换始终保留原调试目标，发现到无关配对服务不得进入可提交态，自动发现端点必须可见，用户改写端点后不得继续向旧发现目标提交。
- [X] T200 [US7] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/WirelessDiscoveryModels.kt` 实现配对码服务关联策略；修改文件：上述 1 个文件；验收：T198 转绿，策略只使用已批准 DNS-SD 类型、解析状态、AOSP GUID 服务名规则与地址交集，歧义时不猜测。
- [X] T201 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryModels.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducer.kt` 保留未配对调试设备目标到根浮层 effect；修改文件：上述 2 个文件；验收：T199 的 reducer 部分转绿，确认未配对调试设备时 effect 携带原 `WirelessDiscoveryTarget`，配对页关闭或取消后清理。
- [X] T202 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现目标感知配对端口选择、可见端点同步及手动覆盖语义；修改文件：上述 1 个文件；验收：T199 全部转绿，多设备发现顺序不影响目标，用户编辑端点后严格按编辑值提交，失败可重试且不遗留陈旧目标。
- [ ] T203 [US7] 在 Android 16 主控与 Android 12 隔离目标上从全新系统配对窗口完成 QR、配对码及配对后自动连接实测，再执行 T197 全功能回归；修改文件：0；验收：每次失败均重新采集并定位原因直至成功，局域网列表只展示调试端口，未配对调试设备打开复用配对浮层，QR/配对码成功后无需再次点击即可进入已连接态，最终完成 Shell、Logcat、快速切页与断线恢复验证。

## Phase 17: 跨实例回归缺陷修复

- [X] T204 [US7] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducerTest.kt` 增加已配对但身份尚未关联的动态调试服务应先尝试连接的 RED 测试；修改文件：上述 1 个文件；验收：确认选择后产生直接连接效果，不预判为未配对。
- [X] T205 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducer.kt` 实现动态调试服务先连接策略；修改文件：上述 1 个文件；验收：T204 转绿，已验证服务与旧版 5555 行为保持不变。
- [X] T206 [US7] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt` 增加动态调试连接仅在鉴权失败时进入配对、其他错误不进入配对的 RED 测试；修改文件：上述 1 个文件；验收：成功连接不显示配对，`allowsPairingFallback` 为 true 时复用配对浮层，超时等错误保留连接错误。
- [X] T207 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现发现连接的鉴权失败配对回退；修改文件：上述 1 个文件；验收：T206 转绿，回退前释放前台发现且不复用陈旧 generation。
- [X] T208 [US3] [US4] 为应用列表完成后立即进入进程页导致 Session 断开的路径增加 RED 测试；修改文件：最多 2 个测试文件；验收：稳定复现子任务切换错误关闭主 Session 或占用共享通道的原因。
- [X] T209 [US3] [US4] 修复应用与进程快速切页的 Session/子流生命周期；修改文件：最多 2 个生产文件；验收：T208 转绿，离页只取消页面子任务，主 Session 保留，随后发现与重连不依赖被控端重启无线调试。
- [ ] T210 [US7] 强制重跑相关模块、全量测试与 Debug 构建，并在三模拟器隔离网络上复验两个缺陷；修改文件：0；验收：自动化、跨实例、UI/功能、未执行项与发布门禁证据分开记录，Android 9 的 5555 结果不得冒充 Android 12 TLS 证据。
- [X] T211 [US7] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt` 更新配对目标关联回归夹具，使未配对路径由真实鉴权失败触发；修改文件：上述 1 个文件；验收：多设备目标关联覆盖保持不变，且不再把未知动态端口直接等同于未配对。
- [X] T212 [US3] [US4] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationMetadataSessionManagerTest.kt` 增加元数据 Sync 子传输超时/清理不得关闭主 Session 的 RED 测试；修改文件：上述 1 个文件；验收：元数据使用独立子客户端，子客户端清理后主 Session 仍可执行进程所需 Shell 请求。
- [X] T213 [US3] [US4] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 隔离应用元数据传输客户端并限定强制关闭范围；修改文件：上述 1 个文件；验收：T212 转绿，元数据批次结束、取消、超时或 Session 变化只关闭元数据子客户端，不关闭活动主 Session。
- [X] T214 [US7] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt` 增加前台意外断链后自动重启 NSD 的 RED 测试；修改文件：上述 1 个文件；验收：已终止的旧 discovery 不复用，断链后产生一个新 generation 的前台扫描。
- [X] T215 [US7] 在 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt` 实现前台意外断链后的 discovery 恢复；修改文件：上述 1 个文件；验收：T214 转绿，显式后台/取消仍不自动重启，旧 generation 回调仍被拒绝。
- [X] T216 [US7] 强制重跑相关模块、全量测试与 Debug 构建，并在 Android 12 TLS 目标上执行应用页到进程页至少 20 秒观察及意外断链发现恢复复验；修改文件：0；验收：自动化与跨实例证据分开记录，Session 不因元数据子流超时断开，前台断链后无需重启被控端无线调试即可重新发现。

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 Setup**: T001 是所有页面实现的设计哈希门禁；T002–T008 可并行写入失败测试。
- **Phase 2 Foundational**: T009←T002，T010←T003，T011←T005，T012←T004/T010，T013–T014←T008/T010，T015←T007。
- **US1**: 依赖 Phase 2；为页面可见性、FR-004、语言分发和锁聚合的 App 集成基础。
- **US2–US7**: 各自核心/Feature 逻辑在 Phase 2 后可开始；所有触碰 `SheenApp.kt` 的集成任务依赖 T024–T026。
- **Polish**: T123–T135 依赖其覆盖的实现完成；T136 依赖 T001 和 T123–T135 的真实结果。

### User story dependencies

```text
T001 + Foundational -> US1
                    ├-> US2 domain/Feature -> App integration after US1
                    ├-> US3 core/data/Feature -> App integration after US1
                    ├-> US4 Feature -> App integration after US1
                    ├-> US5 core/Feature -> App integration after US1
                    ├-> US6 core/data/Feature -> App integration after US1
                    └-> US7 core/Devices/Overview -> root integration after US1
all selected stories -> independent evidence gates -> closeout
```

- 每个故事在假网关/夹具下可独立测试；P1 按 US1→US7 顺序交付时最易控制共享文件冲突。
- US3 与 US5 都修改 `AdbModels.kt`、`AdbSessionManager.kt`、`DefaultAdbSessionManager.kt`，同一工作树中应先完成 T048–T052，再开始 T079–T082。
- `SheenApp.kt` 集成主线为 T022→T024→T025→T026→T035→T059→T068→T088→T102→T121→T122。

### Within each user story

1. 完成该故事全部 **Tests first** 任务并确认预期 RED。
2. 模型/契约先于协议和存储实现。
3. Core/Data 先于 Feature 状态机。
4. Feature 状态机/目录先于 Compose 页面和 App 集成。
5. 对应测试转绿且独立验收完成后才进入 Checkpoint。

---

## Parallel Opportunities

### Setup / Foundational

```text
并行 RED: T002–T008
并行实现: T009, T010, T011, T013（各自前置 RED 完成后）
```

### User Story 1

```text
并行 RED: T016–T020
主线: T021 -> T022 -> T023 -> T024 -> T025 -> T026
```

### User Story 2

```text
并行 RED: T028–T031
并行实现: T032 与 T033（分别完成前置测试后）
```

### User Story 3

```text
并行 RED: T037–T047
并行实现: T049 与 T053；T057 可在 Feature 状态模型稳定后并行
主线: T048 -> {T049,T053} -> T050/T051/T052 -> T054 -> T055/T056 -> T058 -> T059
```

### User Story 4

```text
并行 RED: T061–T064
并行实现: T065 与 T066
```

### User Story 5

```text
并行 RED: T070–T078
并行实现: T080, T083, T085, T086（各自前置契约完成后）
主线: T079 -> T080 -> T081 -> T082 -> T084 -> T087 -> T088
```

### User Story 6

```text
并行 RED: T090–T095
并行实现: T096, T097, T099, T100（各自前置测试完成后）
```

### User Story 7

```text
并行 RED: T104–T111
并行实现: T113, T114, T116, T117（各自前置测试完成后）
```

### Evidence

```text
并行视觉证据: T125–T130
独立门禁文件: T131–T135（工具/设备资源允许时可并行）
```

---

## Implementation Strategy

### MVP first

1. 完成 T001–T015。
2. 完成 US1（T016–T027）。
3. 停止并独立验收导航、淡入淡出、FR-004、语言状态隔离、长任务锁和返回规则。
4. 不把其他尚未实现页面显示成真实内容或成功状态。

### Incremental delivery

1. US1：统一宿主、FR-004、双语分发与生命周期。
2. US2：文件浏览/传输及全状态。
3. US3：应用管理、逐组件 APK 提取和单 APK 安装。
4. US4：进程实时管理及全状态。
5. US5：交互式 Shell、特殊键、自动滚动及全状态。
6. US6：按需 Logcat、Fatal、完整窗口保存及全状态。
7. US7：连接/配对和 Overview 截屏/录屏输出修复。
8. Phase 10：分别形成视觉、自动化、性能、双语、真机和发布证据后再结项。

### File-count enforcement

- 每项任务只允许修改“修改文件”字段列出的 0–2 个文件。
- 设计源、规格、合同、quickstart 和测试夹具仅作为只读输入，不计入修改文件数。
- 验证任务发现缺陷时不得顺手改生产代码，应返回对应故事新增最多两文件的任务。
- `[P]` 不授权对同一文件并发编辑；共享文件任务必须按依赖主线串行。

---

## Notes

- 所有设备请求绑定 expected Session ID；旧 Session 结果必须丢弃。
- 不记录真实端点、配对码、包名上下文、Shell/Logcat 原文、密钥或签名材料。
- 不新增 Manifest 权限、外部依赖、远程 UI 资源、Root、无障碍自动化、账号、后端或业务网络。
- `:core:adb` 独占原始 ADB/命令；`:core:data` 独占偏好与 SAF 原语；`:core:ui` 只拥有共享设计/本地化；页面目录归各 Feature；`:app` 只装配语言、导航、根浮层、picker 和锁。
- 自动化、性能夹具、视觉、双语、真实设备、安全和发布合规是独立门禁，任何一类不得冒充另一类。
