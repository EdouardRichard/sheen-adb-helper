# Tasks: v0.05 设备操作体验与性能收敛

**Input**: Design documents from `/specs/005-device-operations-refinement/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: 本清单强制 TDD。每个用户故事必须先完成其全部测试任务并确认因缺少目标行为而失败，再开始实现任务；不得先写实现后补测试。

**Task size rule**: 每个任务最多修改 2 个文件。任务中列出的文件即该任务允许修改的完整文件集合；验收命令、只读检查和删除文件也计入任务说明，但不得顺手修改其他文件。

**Organization**: 按用户故事优先级 P1 → P6 分组。`[P]` 仅表示文件互不冲突且不依赖尚未完成任务，可并行执行。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行，且不得与同时执行任务修改同一文件
- **[Story]**: 用户故事追踪标签
- **验收**: 每个任务行末提供该任务独立完成标准

---

## Phase 1: Setup（实现基线）

**Purpose**: 在任何代码修改前确认活动功能、构建基线、权限和依赖边界；本阶段只读，不修改业务文件。

- [X] T001 核对活动功能与当前测试基线，读取 `.specify/feature.json`、`specs/005-device-operations-refinement/plan.md` 并运行现有 `:core:adb`、`:core:data`、四个受影响 Feature 与 `:app` 单测；验收：活动目录确认为 `specs/005-device-operations-refinement`，基线成功/失败及既有失败项被记录到执行回报，文件修改数为 0
- [X] T002 [P] 核对依赖与权限基线，检查 `gradle/libs.versions.toml`、`app/src/main/AndroidManifest.xml` 并以 Gradle 已解析依赖图确认 `androidx.core.content.FileProvider` 可用性；验收：确认不需要新增第三方依赖、权限种类、后台能力、Root/Shizuku/无障碍/设备管理，若缺少 FileProvider 直接依赖则暂停并先走依赖审查，文件修改数为 0

---

## Phase 2: Foundational（共享阻断条件）

**Purpose**: 本版本不新增共享模块或数据库迁移，所有故事复用既有单一 `AdbSessionManager`、结构化错误、SAF 和 Compose 架构。

**⚠️ CRITICAL**: T001–T002 完成前不得开始任何用户故事；实现期间所有原始 NSD/ADB/Shell/`/proc`/`dumpsys`/Logcat 操作必须留在 `:core:adb`。

**Checkpoint**: 基线、权限和依赖边界已确认，进入 P1。

---

## Phase 3: User Story 1 - 通过即时通知完成本机无线配对（Priority: P1）🎯 MVP

**Goal**: 点击入口后 1 秒内出现可停止通知，NSD 在应用不可见后仍由唯一 2 分钟窗口控制；发现端口后提供安全的六位配对码输入，并覆盖应用内降级。

**Independent Test**: 只测试本机无线配对；在第 10 秒后才开启配对端口仍可发现，通知初态/输入态/停止/锁屏/拒绝通知/超时均符合 [wireless-and-device-display.md](contracts/wireless-and-device-display.md)。

### Tests for User Story 1（先写并确认失败）

- [X] T003 [P] [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/NsdDiscoveryAdapterTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/WirelessDiscoveryFactoryTest.kt` 增加模式和截止时间契约测试；验收：测试证明 `LAN_SCAN` 仍为 10 秒、`LOCAL_PAIRING` 超过 10 秒仍发现且最多 2 分钟，并在实现前因 mode/deadline 丢失而失败
- [X] T004 [P] [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LocalPairingCoordinatorTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/LocalPairingNotificationPolicyTest.kt` 增加立即初态、重复点击复用、nonce 失效、停止和 2 分钟清理测试；验收：每种结束路径在 3 秒内进入终态且配对码/端点不出现在可打印状态，实现前至少一项失败
- [X] T005 [P] [US1] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/WirelessSessionManagerContractTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManagerTest.kt` 增加唯一 source、Session 切换、取消和命令子流资源清理测试；验收：旧 generation 不更新新窗口、停止幂等且不关闭有效主 Session，实现前失败
- [X] T006 [P] [US1] 在 `app/src/test/kotlin/com/sheen/adbhelper/localpairing/LocalPairingPlatformContractTest.kt`、`app/src/test/kotlin/com/sheen/adbhelper/localpairing/LocalPairingAppBridgeTest.kt` 增加 1 秒内初始通知、停止 action、RemoteInput、锁屏拒绝、用户划除通知、扫描中撤销通知权限、设备重启和通知关闭降级测试；验收：任何平台中断都不留下不可见扫描，通知公开文本不含端点/配对码/设备身份，陈旧 action 无效，实现前失败
- [X] T007 [P] [US1] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingReducerTest.kt`、`feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt` 增加应用内等价状态、连续点击、输入清空、超时和旧 Session 丢弃测试；验收：通知不可用不被映射为配对失败，取消/成功后输入为空，实现前失败
- [X] T008 [P] [US1] 在 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentationTest.kt`、`app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 增加固定文案和最简控件策略测试；验收：精确包含“请开启无线调试。”、“已检测到配对端口，请输入配对码：”、停止和六位输入，不显示敏感值，实现前失败

### Implementation for User Story 1

- [X] T009 [US1] 贯通 NSD 模式和截止时间并让 adapter 服从单一 deadline，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/NsdDiscoveryPolicy.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt`（依赖 T003）；验收：T003 通过，LAN 10 秒与本机配对 2 分钟互不串用，stop 释放 listener/timer
- [X] T010 [US1] 将 source request/deadline 接入唯一 Session 和 generation，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`（依赖 T005、T009）；验收：T005 通过，重复窗口不创建第二 source，Session 切换清理旧发现但保留新 Session
- [X] T011 [US1] 实现立即 `PROMPT_ENABLE_DEBUGGING`、窗口复用、一次性 nonce 和所有终止路径，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/pairing/LocalPairingCoordinator.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/pairing/LocalPairingNotificationPolicy.kt`（依赖 T004、T010）；验收：T004 通过，2 分钟不可延长，重复/过期提交被拒绝且敏感输入清零
- [X] T012 [US1] 实现先启动可见短服务/通知再开始发现以及停止、RemoteInput、锁屏、通知划除、权限中途撤销、设备重启和通知关闭处理，修改 `app/src/main/kotlin/com/sheen/adbhelper/localpairing/LocalPairingAppBridge.kt`、`app/src/main/kotlin/com/sheen/adbhelper/localpairing/LocalPairingForegroundService.kt`（依赖 T006、T011）；验收：T006 通过，初始通知 1 秒内出现，所有结束路径 3 秒内撤销通知并停止服务，应用内无不可见活动扫描
- [X] T013 [US1] 建模应用内扫描/输入/终止状态并拒绝陈旧事件，修改 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingModels.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingReducer.kt`（依赖 T007）；验收：Reducer 测试通过，状态覆盖扫描、发现、提交、结果、取消、超时和通知降级
- [X] T014 [US1] 接入窗口复用、停止和安全提交，修改 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesPairingPresentation.kt`（依赖 T007、T011、T013）；验收：ViewModel/Presentation 测试通过，旧窗口/旧 Session 结果不更新 UI，通知不可用时功能仍完整
- [X] T015 [US1] 以最简方式呈现两段固定提示、停止和六位输入，修改 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt`、`app/src/main/res/values/strings.xml`（依赖 T008、T014）；验收：T008 通过，无重大布局重构，公开 UI/通知不展示真实端点、设备身份或配对码回显

**Checkpoint**: P1 可独立演示；在不启用其他 v0.05 功能时完成前台、切设置后短时后台及应用内降级配对。

---

## Phase 4: User Story 2 - 查看并安全终止被控端进程（Priority: P2）

**Goal**: 提供含六项规定字段的可刷新进程快照，并在逐次范围选择和“我了解”确认后安全终止单进程或整个应用，执行前后均验证身份。

**Independent Test**: 仅连接包含普通、多进程、短时和受保护进程的设备，验证 CPU/PSS、应用关联、刷新、取消、PID 复用、单进程、整个应用、部分终止、拒绝、超时和断开。

### Tests for User Story 2（先写并确认失败）

- [X] T016 [P] [US2] 新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/processes/ProcessSnapshotParserTest.kt`，覆盖 `ps`、两次 `/proc` 计数、500 ms 区间、PSS compact meminfo、缺列/非数字/计数回退/进程退出；验收：CPU 归一化 0–100、PSS KiB/1024、不可用字段为 CALCULATING/UNKNOWN，测试在解析器实现前失败
- [X] T017 [P] [US2] 新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/processes/ProcessApplicationAssociationTest.kt`，覆盖同 user/UID 的精确包名、`包名:后缀`、共享 UID、多候选和未知名称；验收：只有唯一可靠关联返回应用名/整应用能力，其他固定为“无法解析应用名”，实现前失败
- [X] T018 [P] [US2] 在 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/AdbCapabilityParsersTest.kt` 新增字段级降级测试，并新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ProcessSessionManagerTest.kt` 覆盖 5 秒预算、刷新代次、取消/断开/Session 切换；验收：旧刷新不能覆盖新快照且命令子流关闭，实现前失败
- [X] T019 [P] [US2] 新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/processes/ProcessTerminationPolicyTest.kt`，覆盖 PID 1、`appId < 10000`、缺失 startTime、一次性确认 nonce、单进程/整应用可用性；验收：危险/不确定目标被拒绝，取消确认产生零命令，实现前失败
- [X] T020 [P] [US2] 新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ProcessTerminationSessionManagerTest.kt`，并扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationSessionManagerTest.kt` 覆盖 SIGTERM、应用级 force-stop、PID 复用、集合变化及最终验证分类；验收：测试证明 force-stop 不是逐 PID 终止且会影响进程/服务/任务，请求已发送不等于成功，全部契约结果可区分且零范围外应用误终止，实现前失败
- [X] T021 [P] [US2] 新增 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesViewModelTest.kt`，扩展 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesAnalysisPolicyTest.kt` 覆盖主动刷新、字段状态、范围选择、逐次确认、取消和 stale generation；验收：取消零终止请求、整应用选项只在可靠关联出现，实现前失败
- [X] T022 [P] [US2] 新增 `feature/processes/src/test/kotlin/com/sheen/adb/feature/processes/ProcessesPresentationTest.kt`，扩展 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 覆盖“进程管理”名称、六字段、刷新、范围选择和风险文案；验收：必须显示“我了解”且含数据丢失/服务中断/设备不稳定风险，整个应用还明确 force-stop 会停止进程/服务/任务并可能在再次显式启动前阻止后台恢复，实现前失败

### Implementation for User Story 2

- [X] T023 [US2] 增加 `ProcessSnapshotEntry`、字段状态、`ProcessIdentity`、终止请求/结果和项目自有 API，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`（依赖 T016–T020）；验收：公开契约不暴露原始命令/第三方类型，所有结果绑定 sessionId/generation
- [X] T024 [US2] 实现受限 `ps`/`/proc`/compact meminfo 解析和 CPU/PSS 计算，新增 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/processes/ProcessSnapshotParser.kt` 并修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbCapabilityParsers.kt`（依赖 T016、T018、T023）；验收：T016/T018 解析用例通过，不以 RSS、累计 CPU 或假零值降级
- [X] T025 [US2] 实现唯一应用关联和终止前安全策略，新增 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/processes/ProcessApplicationAssociation.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/processes/ProcessTerminationPolicy.kt`（依赖 T017、T019、T023）；验收：T017/T019 通过，共享 UID/多候选/PID1/系统 appId/身份缺失均不可误执行
- [X] T026 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现 500 ms 双样本、PSS、5 秒预算和刷新代次（依赖 T018、T024、T025）；验收：ProcessSessionManager 测试通过，取消/超时/断开只关闭本次子流，旧结果不交付
- [X] T027 [US2] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现单次 SIGTERM、应用级 `am force-stop --user`、执行前身份复核和执行后刷新分类（依赖 T020、T025、T026）；验收：终止测试通过，确认集合仅用于目标/结果验证且不把 force-stop 伪装成逐 PID 操作，不使用 SIGKILL/Root/绕过，10 秒内返回已验证结果或明确非成功
- [X] T028 [US2] 实现进程管理 UI 状态、主动刷新、确认 nonce 和结果展示，修改 `feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesViewModel.kt`（依赖 T021、T026、T027）；验收：T021 通过，连续刷新/终止互斥安全，取消和 Session 切换使确认失效
- [X] T029 [US2] 实现最简六字段列表、刷新按钮、范围选择和风险确认，并更名导航，修改 `feature/processes/src/main/kotlin/com/sheen/adb/feature/processes/ProcessesScreen.kt`、`app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`（依赖 T022、T028）；验收：T022 通过，字段未知仍保留条目，只有点击“我了解”才调用终止

**Checkpoint**: P2 可独立验证；不依赖 Logcat、应用图标降级或设备名称展示即可完成刷新和安全终止。

---

## Phase 5: User Story 3 - 将 Logcat 提取为文件或分享（Priority: P3）

**Goal**: 用快照/持续两种有界捕获替代分析体验；结果默认仅内存，可显式保存到 SAF 目录或经受限 FileProvider 分享。

**Independent Test**: 只使用 Logcat 页面验证模式选择、开始/停止、10 分钟/10 MiB、开始游标、空日志、Session 切换、SAF 失败、分享取消和临时文件清理。

### Tests for User Story 3（先写并确认失败）

- [X] T030 [P] [US3] 新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/logcat/LogcatCapturePolicyTest.kt`，覆盖 SNAPSHOT/CONTINUOUS、起始游标、UTF-8 边界、10 MiB、10 分钟和停止原因；验收：快照自动结束、持续只含开始后日志且不分片，测试在实现前失败
- [X] T031 [P] [US3] 新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LogcatCaptureSessionManagerTest.kt`，扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/AdbExclusiveOperationCoordinatorTest.kt` 覆盖 LOGCAT 租约、取消/超时/断开/Session 切换及主 Session 存活；验收：所有路径释放租约和命令子流，ROM 无可靠游标返回 UNSUPPORTED，实现前失败
- [X] T032 [P] [US3] 新增 `core/data/src/test/kotlin/com/sheen/adb/data/LogcatOutputStoreTest.kt`，扩展 `core/data/src/test/kotlin/com/sheen/adb/data/SafDocumentPolicyTest.kt` 覆盖 OpenDocumentTree、`.part`、AUTO_RENAME、取消和提交失败；验收：成功只留下一个模式命名文件，失败不留损坏文件，实现前失败
- [X] T033 [P] [US3] 新增 `core/data/src/test/kotlin/com/sheen/adb/data/LogcatShareFileStoreTest.kt`、`core/data/src/test/kotlin/com/sheen/adb/data/TemporaryDataCleanerTest.kt` 覆盖 `PREPARED/CHOOSER_OPENED/TARGET_SELECTED/CANCELLED/OUTCOME_UNKNOWN/EXPIRED/CLEANED`、单文件租约、只读 URI、明确取消立即清理、未知/目标选择 1 小时过期及启动/新分享兜底清理；验收：chooser 打开不等于成功，不声称接收方已读取，不得共享目录或授予持久 URI 权限，实现前失败
- [ ] T034 [P] [US3] 新增 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatViewModelTest.kt`，重写 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatBufferTest.kt` 覆盖模式互斥、进度、停止、10 MiB 内存和 stale Session；验收：未选模式不采集、未选保存/分享不写持久位置，实现前失败
- [ ] T035 [P] [US3] 重写 `feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatPresentationTest.kt`、`feature/logcat/src/test/kotlin/com/sheen/adb/feature/logcat/LogcatAnalysisWindowTest.kt` 为提取/退化契约测试；验收：只存在模式、开始/停止、进度、通过 `ActivityResultContracts.OpenDocumentTree` 保存、分享和敏感提示，不存在筛选/暂停/Logcat 关联/分析控件，实现前失败
- [ ] T036 [P] [US3] 新增 `app/src/test/kotlin/com/sheen/adbhelper/LogcatSharePlatformContractTest.kt`，扩展 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt` 覆盖非导出 FileProvider、限定 path、单文件 ACTION_SEND、临时只读授权和平台可观察/不可观察分享回调；验收：chooser 打开/返回不是成功，明确取消与结果未知可区分，目标选择不表示已读取且无公共目录回退，实现前失败
- [ ] T037 [P] [US3] 重写 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/LogcatAnalysisTest.kt` 并新增 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/logcat/LogcatRetirementContractTest.kt`，以静态源码/API surface 扫描声明仅 Logcat 专用结构化分析、筛选和日志进程关联 API 必须消失；验收：旧 Logcat 分析代码仍存在时测试失败，US2 的 `ProcessApplicationAssociation` 不被命中，捕获错误仍保留安全结构化表达

### Implementation for User Story 3

- [ ] T038 [US3] 用 `LogcatCapture` 模式、事件、停止原因和错误替换结构化分析公开模型/API，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`（依赖 T030、T031、T037）；验收：Feature 只见项目自有有界字节/进度，公开 API 不含 StructuredLogcat 类型
- [ ] T039 [US3] 实现快照/持续命令策略、设备起始游标和 UTF-8/大小/时长边界，新增 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/logcat/LogcatCapturePolicy.kt` 并修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/AdbCapabilityParsers.kt`（依赖 T030、T038）；验收：T030 通过，无法保证持续起点时明确 UNSUPPORTED，不清空被控端日志
- [ ] T040 [US3] 在 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt` 实现 LOGCAT 独占租约、有界捕获、停止和资源清理（依赖 T031、T039）；验收：T031 通过，10 分钟/10 MiB/用户停止任一先到即结束，不关闭有效主 Session
- [X] T041 [US3] 实现按目录暂存后提交的日志输出，新增 `core/data/src/main/kotlin/com/sheen/adb/data/LogcatOutputStore.kt` 并修改 `core/data/src/main/kotlin/com/sheen/adb/data/SafDocumentStore.kt`（依赖 T032）；验收：T032 通过，文件名含 snapshot/continuous 与 UTC，取消/失败清理 `.part`
- [X] T042 [US3] 实现应用缓存单文件分享租约和过期清理，新增 `core/data/src/main/kotlin/com/sheen/adb/data/LogcatShareFileStore.kt` 并修改 `core/data/src/main/kotlin/com/sheen/adb/data/TemporaryDataCleaner.kt`（依赖 T033）；验收：T033 通过，只创建 `cacheDir/logcat-share/` 下文件，明确取消立即删，`TARGET_SELECTED/OUTCOME_UNKNOWN` 最长保留 1 小时且清理幂等
- [ ] T043 [US3] 以有界捕获结果重写状态管理并删除分析窗口行为，修改 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatBuffer.kt`、`feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatViewModel.kt`（依赖 T034、T040–T042）；验收：T034 通过，默认仅内存，Session 切换释放旧结果且不能写入新输出任务
- [ ] T044 [US3] 以最简模式选择、进度、停止、`ActivityResultContracts.OpenDocumentTree` 保存、分享和敏感提示替换分析 UI，修改 `feature/logcat/src/main/kotlin/com/sheen/adb/feature/logcat/LogcatScreen.kt`（依赖 T035、T043）；验收：T035 通过，目录由用户本次明确选择，无筛选/暂停/Logcat 进程关联/趋势等分析入口
- [X] T045 [US3] 配置只允许日志分享子目录的 FileProvider，修改 `app/src/main/AndroidManifest.xml`、新增 `app/src/main/res/xml/logcat_share_paths.xml`（依赖 T036、T042）；验收：Provider `exported=false`、`grantUriPermissions=true`，path 不能访问其他 files/cache 目录
- [X] T046 [US3] 装配输出/分享 store、启动清理和 ACTION_SEND 单文件分享，修改 `app/src/main/kotlin/com/sheen/adbhelper/SheenApplication.kt`、`app/src/main/kotlin/com/sheen/adbhelper/SheenApp.kt`（依赖 T036、T043、T045）；验收：T036 通过，分享 Intent 仅含本次 text/plain URI 与临时只读 grant，chooser 打开不报成功，按平台信号区分目标选择/取消/结果未知且不声称接收方已读取
- [ ] T047 [US3] 删除不再使用的核心结构化 Logcat 解析和筛选实现，删除 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/diagnostics/StructuredLogcatParser.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/diagnostics/DiagnosticFilter.kt`（依赖 T037–T040）；验收：T037 通过，生产代码中 StructuredLogcat/DiagnosticFilter 引用为 0，当前捕获错误仍可区分
- [ ] T048 [US3] 删除旧 Logcat 专用进程关联实现及其测试，删除 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/diagnostics/ProcessAssociation.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/ProcessAssociationTest.kt`（依赖 T027、T037、T040、T047）；验收：T037 通过，生产/测试源码中旧 `internal.diagnostics.ProcessAssociation` 引用为 0，US2 的 `internal.processes.ProcessApplicationAssociation` 及其测试仍存在并通过

**Checkpoint**: P3 可独立完成快照或持续提取，并保存/分享一个文件；没有应用内分析能力或自动持久化。

---

## Phase 6: User Story 4 - 以更低开销查看和管理应用（Priority: P4）

**Goal**: 应用列表仅解析/展示应用名和包名，图标提取、传输、缓存、占位和渲染次数为 0，既有搜索和应用操作不回退。

**Independent Test**: 在 200 应用、同名、中文名和名称不可解析设备上验证 10 秒目标、搜索与全部既有操作，并用测试探针确认零图标路径。

### Tests for User Story 4（先写并确认失败）

- [X] T049 [P] [US4] 重写 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationMetadataParserTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationMetadataLoaderTest.kt` 为标签-only 契约；验收：测试断言不调用 `allIcons`、不产生/缓存/驱逐 icon bytes，标签失败仍保留包名，实现前失败
- [X] T050 [P] [US4] 扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationMetadataSessionManagerTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/ApplicationSessionManagerTest.kt` 覆盖标签-only update、Session 切换和既有操作回归；验收：公开 update 无 icon 字段，旧名称不跨 Session/包复用，实现前失败
- [X] T051 [P] [US4] 扩展 `feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsPolicyTest.kt`、`feature/apps/src/test/kotlin/com/sheen/adb/feature/apps/AppsPresentationTest.kt` 覆盖仅名称/包名、未知名称、同名区分、搜索和零占位图标；验收：版本/安装器/icon 不出现在列表策略，既有管理确认结果不变，实现前失败

### Implementation for User Story 4

- [X] T052 [US4] 删除全部图标解析和 LRU 缓存，仅保留标签解析，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/applications/ApplicationMetadataParser.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/applications/ApplicationMetadataLoader.kt`（依赖 T049）；验收：T049 通过，`apk-parser` 只读取标签，图标字节分配/缓存次数为 0
- [X] T053 [US4] 将公开元数据 update 和 Session 管理收敛为标签/包名，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`（依赖 T050、T052）；验收：T050 通过，现有搜索身份/应用操作/Session 校验保持不变
- [X] T054 [US4] 将列表模型和条目 UI 收敛为应用名/包名，修改 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt`、`feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt`（依赖 T051、T053）；验收：T051 通过，无法解析时显示“无法解析应用名”且始终展示正确包名
- [X] T055 [US4] 删除图标 renderer 并清除 ViewModel 图标状态，删除 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/ApplicationIconRenderer.kt`、修改 `feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsViewModel.kt`（依赖 T051、T054）；验收：生产代码中 icon payload/cache/renderer/placeholder 引用为 0，应用 Feature 单测通过

**Checkpoint**: P4 可独立验证；移除图标不影响包身份、搜索或任何既有应用操作。

---

## Phase 7: User Story 5 - 清楚识别局域网扫描设备（Priority: P5）

**Goal**: 连接后读取的设备型号能与同一 `VerifiedWirelessDeviceId` 可靠关联时第一行显示设备名，第二行显示“协议 · IP · 端口”；原始 ADB mDNS/NSD 无可靠名称时省略名称，且不改变去重/端点有效性规则。

**Independent Test**: 使用具名、无名、同名、IPv4、IPv6、端口变化和恶意名称的注入夹具独立验证展示与既有去重；名称来源和身份关联另由模拟或真实成功连接契约验证。

### Tests for User Story 5（先写并确认失败）

- [ ] T056 [P] [US5] 扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/WirelessDiscoveryCoreTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/WirelessDiscoveryFactoryTest.kt` 覆盖 `VERIFIED_AFTER_CONNECTION/UNAVAILABLE`、`VerifiedWirelessDeviceId` 关联、Unicode 首尾空白、80/81 码点边界、ISO 控制字符、规定的全部双向控制字符和名称不参与身份；验收：不安全名称整体为 null 且不截断，原始 service instance/GUID/序列样式字符串/hostname/IP 不会被猜成设备名，关联失效为 null，实现前失败
- [ ] T057 [P] [US5] 扩展 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/NsdDiscoveryAdapterTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/LanDiscoverySessionManagerTest.kt` 覆盖原始平台观察名称为 `UNAVAILABLE`、模拟或真实连接成功后的 overview 型号与已验证身份关联、型号缺失/不安全文本、IPv4/IPv6 和端口更新；验收：ADB mDNS/NSD 实例名不进入 displayName，只有同一 `VerifiedWirelessDeviceId` 的安全型号可进入，去重仍用既有身份，实现前失败
- [X] T058 [P] [US5] 扩展 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPresentationTest.kt`、`feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt` 覆盖两行/一行布局、`协议 · IP · 端口`、同名和 stale result；验收：旧“IPV4 · 端口 xxxx”文案为 0，实现前失败

### Implementation for User Story 5

- [X] T059 [US5] 增加可选安全名称、名称来源、身份引用和独立端点字段并保持身份 reducer 不使用名称，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/WirelessDiscoveryModels.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducer.kt`（依赖 T056）；验收：已加入 80 码点/控制字符安全策略、可信身份关联名称字段，原始扫描无名且名称不参与去重
- [ ] T060 [US5] 让原始平台解析结果固定为无可靠名称，并在连接成功后读取安全设备型号、绑定已验证身份，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/discovery/AndroidNsdDiscoveryAdapter.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`（依赖 T057、T059）；验收：T057 通过，不传播 service instance/hostname/档案名为设备名，型号缺失、文本不安全或身份不匹配时仍为无名，IPv6 与既有去重不回退
- [X] T061 [US5] 实现设备条目的可选名称和端点 presentation，修改 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryModels.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryPanel.kt`（依赖 T058–T060）；验收：有名两行、无名一行，IPv4/IPv6 均完整显示协议/地址/端口
- [ ] T062 [US5] 接入显示模型、仅在连接后验证型号与同一 `VerifiedWirelessDeviceId` 关联时附加名称，并拒绝旧 generation，修改 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryReducer.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt`（依赖 T058、T061）；验收：T058 通过，展示夹具验证格式，T057/T060 验证连接来源链路；原始扫描默认无名，身份无法证明时不复用档案名称，手动输入降级、可靠身份、单一 Session 行为无回退

**Checkpoint**: P5 的显示、去重与安全文本规则可用注入夹具独立验收；`VERIFIED_AFTER_CONNECTION` 来源和同一身份关联必须由 T057/T060 的模拟或真实成功连接验证后才能判定故事通过，无需进入其他管理页面。

---

## Phase 8: User Story 6 - 移除脱敏诊断事件入口（Priority: P6）

**Goal**: 删除专用事件历史的模型、采集、缓存、查询和 UI 入口，同时保留当前操作自己的结构化反馈。

**Independent Test**: 检查首页/设备页/恢复路径及常见失败流程；入口和历史记录均为 0，当前操作仍显示失败、取消、超时、断开、不支持和未知结果。

### Tests for User Story 6（先写并确认失败）

- [ ] T063 [P] [US6] 重写 `core/adb/src/test/kotlin/com/sheen/adb/core/internal/DiagnosticsSessionManagerTest.kt`、`core/adb/src/test/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManagerTest.kt` 为静态源码/API surface 诊断历史退役契约；验收：扫描在 API/100 条缓冲/append/clear/query 任一生产符号存在时失败，当前操作错误仍安全可区分，实现前失败
- [X] T064 [P] [US6] 扩展 `feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesDiscoveryViewModelTest.kt`、`feature/devices/src/test/kotlin/com/sheen/adb/feature/devices/DevicesPairingViewModelTest.kt` 覆盖零诊断状态和当前流程反馈；验收：ViewModel 不收集历史，配对/发现失败仍在当前状态展示，实现前失败
- [ ] T065 [P] [US6] 新增 `app/src/test/kotlin/com/sheen/adbhelper/V005RetirementContractTest.kt` 并扩展 `app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt`，静态断言生产源码无事件历史字符串/API/替代“最近操作”入口；验收：旧入口或隐藏聚合仍存在时测试失败，历史 ADR/归档不计入生产命中

### Implementation for User Story 6

- [ ] T066 [US6] 删除诊断事件模型和 Session API，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/AdbModels.kt`、`core/adb/src/main/kotlin/com/sheen/adb/core/AdbSessionManager.kt`（依赖 T063）；验收：公开接口无 `AdbDiagnosticEvent`、`diagnosticEvents`、clear/query，现有 `AdbOperationResult`/`AdbError` 保留
- [ ] T067 [US6] 删除事件缓冲和所有写入点，修改 `core/adb/src/main/kotlin/com/sheen/adb/core/internal/DefaultAdbSessionManager.kt`（依赖 T063、T066）；验收：T063 通过，任何配对/连接/应用/进程/文件/日志结果均不进入专用历史
- [X] T068 [US6] 删除设备状态中的诊断列表/展开/清除动作和 UI 卡片，修改 `feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesViewModel.kt`、`feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/DevicesScreen.kt`（依赖 T064、T067）；验收：T064 通过，当前操作反馈不受影响，旧历史不可达
- [ ] T069 [US6] 运行退役合同与生产源码扫描，只读核对 `app/src/test/kotlin/com/sheen/adbhelper/V005RetirementContractTest.kt`、`app/src/test/kotlin/com/sheen/adbhelper/AppUiPolicyTest.kt`（依赖 T065–T068）；验收：T065 通过，`app core feature` 中事件历史 API/文案/替代入口生产命中为 0，文件修改数为 0

**Checkpoint**: P6 可独立验收；删除的是专用历史，而不是当前操作的错误与结果反馈。

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: 同步现行架构、权限、依赖、历史 ADR 和验证证据；不得借此扩大功能范围。

- [ ] T070 [P] 同步 Session/无线/进程/Logcat 生命周期架构，修改 `docs/architecture/adb-session.md`、`docs/architecture/device-and-data.md`；验收：记录单一 Session、命令子流隔离、2 分钟 NSD、进程身份复核、SAF/FileProvider 边界，且无真实端点/命令输出
- [ ] T071 [P] 同步标签-only 应用管理和诊断/Logcat 退化架构，修改 `docs/architecture/application-management.md`、`docs/architecture/diagnostics.md`；验收：图标、结构化 Logcat 分析和诊断事件历史均标为已移除，当前操作反馈仍保留
- [ ] T072 [P] 同步安全交付和权限登记，修改 `docs/architecture/security-and-delivery.md`、`docs/权限矩阵.md`；验收：明确无新增权限种类，记录通知立即出现、OpenDocumentTree 与受限 FileProvider 临时只读分享
- [ ] T073 [P] 保留历史正文并标记旧决策被 v0.05 取代，修改 `docs/adr/0002-in-memory-diagnostic-events.md`、`docs/adr/0006-logcat-recent-history-follow-mode.md`；验收：状态/取代原因/迁移方向可审计，不把历史要求改写为从未存在
- [ ] T074 [P] 审计依赖用途和发布阻断，修改 `docs/第三方依赖与许可证.md`、`docs/第三方依赖审查.md`；验收：`apk-parser` 用途缩窄为标签解析、无新增依赖，`spake2-java 1.0.5` GPL-3.0-or-later 阻断继续独立报告
- [ ] T075 按 `specs/005-device-operations-refinement/quickstart.md` 运行 `:core:adb`、`:core:data`、`:core:ui`、`:feature:devices`、`:feature:overview`、`:feature:shell`、`:feature:processes`、`:feature:logcat`、`:feature:settings`、`:feature:apps`、`:feature:files`、`:app` 全部单测、Debug 构建、静态退役扫描和 `git diff --check`，并新增 `docs/archive/releases/v0.05/verification.md`；验收：在验证记录中逐项登记每个模块、命令、Debug 构建和静态扫描的通过/失败及既有失败项，失败不得伪装通过，自动化证据与尚未执行的模拟器/真机项目明确分栏
- [ ] T076 按 `specs/005-device-operations-refinement/quickstart.md` 第 9 节实际执行 API 30/33/36 模拟器兼容性矩阵，并按三台现有设备适用角色执行 P1–P6 人工功能与性能验收，更新 `docs/archive/releases/v0.05/verification.md`；验收：三个 API 分别记录同一 Debug APK 下的通过/失败/不适用/未验证，模拟器缺失能力不得记为通过；设备 A/B 作为 Android 11+ 主控候选，设备 C 低于 Android 11 且仅在可建立 ADB 连接时作为被控兼容性目标；另行记录脱敏设备类别、Android 大版本、ROM、实际角色、20 次性能分母和未覆盖风险，自动化、模拟器与三台设备证据不互相冒充
- [ ] T077 由当前唯一验收用户完成停止配对扫描、刷新进程、确认与取消终止、保存与打开分享页的适用任务走查，新增 `docs/archive/releases/v0.05/usability.md`；验收：每项至少执行一次并记录通过/失败/不适用，不伪造 10 人、90% 或群体可用性结论，设备 C 不承担主控/原生无线配对步骤
- [ ] T078 单独完成发布门禁结论，新增 `docs/archive/releases/v0.05/release-gates.md`；验收：自动化、API 30/33/36 模拟器、功能实现、三台设备人工覆盖、单用户走查和外部发布合规分别给出结论，GPL 阻断未解除时不得声明可对外发布

---

## Dependencies & Execution Order

### Phase Dependencies

```text
Setup T001–T002
        ↓
Foundational checkpoint
        ↓
US1 (P1 / MVP)
        ↓
US2 (P2)
        ↓
US3 (P3)
        ↓
US4 (P4)
        ↓
US5 (P5)
        ↓
US6 (P6)
        ↓
Polish / full validation
```

- 按产品优先级默认顺序执行 P1 → P6。
- 从功能语义看，各故事均可在 Foundation 后独立验收；但源码存在文件冲突，只有标记 `[P]` 的任务可直接并行。
- US1 与 US5 都修改 NSD/Devices 文件；US2、US3、US4、US6 都会分阶段修改 `AdbModels.kt`、`AdbSessionManager.kt` 或 `DefaultAdbSessionManager.kt`，不得并行编辑这些任务。
- Polish 仅在计划交付的故事实现并通过独立验收后执行。

### TDD Dependencies Within Every Story

1. 完成该故事全部“Tests”任务。
2. 运行相关测试，确认因缺少目标行为而失败并保存失败原因。
3. 按 implementation 任务列出的依赖顺序实现。
4. 每完成一个实现任务，只运行最小相关测试；故事结束再运行整个模块测试。
5. 测试若在实现前意外通过，先证明它确实覆盖新需求，否则不得进入实现。

### User Story Dependencies

- **US1**: 无其他故事依赖，是建议 MVP。
- **US2**: 复用当前应用清单但不依赖 US4 的图标移除；独立完成进程管理。
- **US3**: 不依赖 US2 的进程关联；Logcat 分析关联直接退役。
- **US4**: 不依赖 US2；只收敛应用元数据与展示。
- **US5**: 与 US1 共享 NSD 文件但不依赖配对流程，按顺序避免冲突。
- **US6**: 语义独立，但安排在最后可一次删除前述流程仍可能引用的历史写入点。

---

## Parallel Opportunities

### User Story 1

```text
并行测试：T003, T004, T005, T006, T007, T008
实现顺序：T009 → T010 → T011 → T012；T013 → T014 → T015
```

### User Story 2

```text
并行测试：T016, T017, T018, T019, T020, T021, T022
实现顺序：T023 → (T024 与 T025 可在不同时修改其列出文件时并行) → T026 → T027 → T028 → T029
```

### User Story 3

```text
并行测试：T030, T031, T032, T033, T034, T035, T036, T037
实现分支：T038 → T039 → T040
          T041 与 T042 可并行
          T043 → T044；T045 → T046；最后 T047 → T048
```

### User Story 4

```text
并行测试：T049, T050, T051
实现顺序：T052 → T053 → T054 → T055
```

### User Story 5

```text
并行测试：T056, T057, T058
实现顺序：T059 → T060 → T061 → T062
```

### User Story 6

```text
并行测试：T063, T064, T065
实现顺序：T066 → T067 → T068 → T069
```

### Documentation

```text
T070, T071, T072, T073, T074 可并行；T075–T078 在代码与文档收敛后顺序执行
```

---

## Implementation Strategy

### MVP First（仅 User Story 1）

1. 完成 T001–T002。
2. 先写并运行 T003–T008，确认红灯。
3. 实现 T009–T015。
4. 运行 `:core:adb`、`:feature:devices`、`:app` 相关测试。
5. 在真机验证通知 1 秒、发现超过 10 秒仍继续、2 分钟上限和 3 秒清理。
6. 停止并评审 MVP；未通过前不进入 P2。

### Incremental Delivery

1. **P1**: 本机配对即时通知和真正 2 分钟发现。
2. **P2**: 可刷新、可验证、逐次确认的进程管理。
3. **P3**: Logcat 有界文件提取、目录保存和分享。
4. **P4**: 应用标签/包名列表与零图标链路。
5. **P5**: 设备名和 `协议 · IP · 端口` 展示。
6. **P6**: 完整退役诊断事件历史。
7. **Polish**: 架构/ADR/权限/依赖同步和完整验证。

---

## Task Summary

| Phase | Task range | Count |
|-------|------------|-------|
| Setup | T001–T002 | 2 |
| Foundational | checkpoint only | 0 |
| US1 / P1 | T003–T015 | 13 |
| US2 / P2 | T016–T029 | 14 |
| US3 / P3 | T030–T048 | 19 |
| US4 / P4 | T049–T055 | 7 |
| US5 / P5 | T056–T062 | 7 |
| US6 / P6 | T063–T069 | 7 |
| Polish | T070–T078 | 9 |
| **Total** | **T001–T078** | **78** |

## Notes

- 每个任务修改文件数为 0、1 或 2，禁止扩展到第三个文件；若执行中发现必须修改更多文件，应拆出新任务并重新编号/审查。
- `[P]` 不表示可以绕过 TDD 依赖；只允许同一测试阶段或明确无文件冲突的工作并行。
- 所有测试夹具使用虚构 IP、包名、进程、日志和配对材料。
- 不提交、推送、创建 PR 或进入 Implement，除非项目负责人明确批准当前 `tasks.md`。
