# Implementation Plan: v0.1 菜单与连接页 UI

**Branch**: `006-menu-connection-ui` | **Date**: 2026-07-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-menu-connection-ui/spec.md`

## Summary

为 v0.1 重构应用壳的顶部栏、底部六项导航和抽屉菜单，并把历史设备、设置语言和关于入口迁入菜单；连接页复用现有 IP:端口、局域网发现、二维码、配对码、本机配对和连接状态逻辑，新增已连接概览与被控端截图/录屏/重启快捷操作。实现保持纯本地、单一 ADB Session 和既有依赖方向：`:core:adb` 提供 session-bound typed quick-action API 与项目自有捕获端口，`:core:data` 管理共享语言偏好、app-private artifact、项目自有 artifact 端口和 SAF 二进制导出，`feature:overview` 内部 Use Case 协调两者；`:app` 仅装配、导航和回传系统文件选择结果。

## Technical Context

**Language/Version**: Kotlin/JVM 17 toolchain; Android Kotlin + Jetpack Compose

**Primary Dependencies**: Existing Compose Material 3, AndroidX lifecycle/activity, Kotlin coroutines, Kadb/Okio and project `:core:adb`/`:core:data`; no new third-party dependency or project module

**Storage**: Existing DataStore single owner for profiles + language; Keystore-backed ADB identity; app-private `cacheDir` bounded media artifacts; user-selected SAF `CreateDocument` destination

**Testing**: Existing TestNG-backed Gradle unit tests, Compose/presentation contract tests, core session/protocol/SAF tests, module lint and debug assemble

**Target Platform**: Controller app Android API 30 minimum, target/compile SDK 36; acceptance uses the current three controlled devices on the local network, including one device below Android 10

**Project Type**: Multi-module local-only Android mobile app

**Performance Goals**: Existing 10-second LAN discovery; responsive Compose navigation with design-faithful 44px visual sizing and at least 48dp clickable targets; bounded screenshot/recording writes; recording stops at 5 minutes or 256 MiB first; no stale-session UI updates

**Constraints**: No new Manifest permission or dependency; no accounts/backend/telemetry; one active session; typed cancellation/timeout/errors; controlled-device-only capture/reboot; no automatic reconnect; temp cleanup on every terminal path and next start

**Scale/Scope**: One current user, three controlled devices, one Android app, six bottom destinations, one connection page with connected/unconnected branches, three mutually exclusive quick actions; deferred file/app/process/Shell/log UI redesign excluded

**Release Identity**: `versionName 0.1.0`, `versionCode 3`; the drawer footer reads the actual build version

## Constitution Check

*GATE: Passed before Phase 0 research and re-checked after Phase 1 design.*

- Pure local: PASS — no account, backend, telemetry, payment or Root/accessibility automation.
- ADB boundary: PASS — protocol, Socket/TLS, pairing, raw command and quick-action capability remain in `:core:adb`.
- Session safety: PASS — all operations carry `sessionId`, use one active session, cancellation, timeout, typed errors and lease cleanup.
- Data/privacy: PASS — no real device material in diagnostics; artifacts are app-private until user-selected SAF export.
- Permissions/dependencies: PASS — no new permission or third-party dependency; matrix and ADR 0008 remain authoritative.
- UI ownership: PASS — app owns only assembly/navigation/platform launcher handoff; `feature:overview` owns its quick-action Use Case and presentation; existing deferred pages keep logic/UI scope.
- Persistence: PASS — language extends the existing DataStore owner; history schema is unchanged.
- Release note: PASS for feature scope; existing `spake2-java` GPL-3.0-or-later release blocker remains unchanged and is not broadened.

## Project Structure

### Documentation (this feature)

```text
specs/006-menu-connection-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── navigation-contract.md
│   └── quick-actions-contract.md
└── tasks.md                 # 由 /speckit-tasks 创建
```

### Source Code (repository root)

```text
app/src/main/kotlin/com/sheen/adbhelper/
├── SheenApp.kt                         # shell, top/bottom bar, drawer, route gating
└── SheenApplication.kt                 # shared container/startup cleanup and dependency wiring
core/adb/src/main/kotlin/com/sheen/adb/core/
├── AdbModels.kt                        # typed quick-action models/errors
├── AdbSessionManager.kt                # public session-bound quick-action API
└── internal/
    ├── QuickActionProtocol.kt           # controlled-device byte streams
    └── QuickActionCapabilityResolver.kt # per-session independent capability detection
core/data/src/main/kotlin/com/sheen/adb/data/
├── DataStoreDeviceProfileRepository.kt # shared DataStore owner + language key
├── QuickActionArtifactStore.kt          # bounded app-private artifact/sink owner
├── SafBinaryExporter.kt                 # typed binary export
└── TemporaryDataCleaner.kt              # startup and terminal cleanup
core/ui/src/main/kotlin/com/sheen/adb/ui/
├── SheenTheme.kt                        # converted DESIGN.md tokens
├── UiLanguage.kt                        # language value
└── V01Strings.kt                        # v0.1 localized strings
feature/devices/src/main/kotlin/com/sheen/adb/feature/devices/
├── DevicesScreen.kt                     # connection page branches
├── DevicesViewModel.kt                  # existing connection/discovery/pairing flows
└── DeviceHistoryMenu.kt                 # menu presentation only
feature/overview/src/main/kotlin/com/sheen/adb/feature/overview/
├── OverviewViewModel.kt                 # session-aware overview reuse
├── OverviewScreen.kt                    # connected code.html -> Compose UI
├── QuickActionUseCase.kt                # feature-owned coordination over core ports
├── QuickActionModels.kt                 # opaque artifact and structured use-case results
└── QuickActionPresentation.kt           # feature-owned state/use-case consumption
feature/settings/src/main/kotlin/com/sheen/adb/feature/settings/
└── SettingsViewModel.kt                 # language preference and clear-all
```

Tests remain beside each module under `src/test/kotlin`, with new session/stream, artifact/export, reducer/presentation, app navigation and settings persistence tests.

**Structure Decision**: 保持现有 Android 多模块结构，不新增模块。导航和系统 URI launcher 由 app 协调；ADB 协议继续封装在 `:core:adb`，数据/SAF/临时文件继续封装在 `:core:data`。`feature:overview` 已合法依赖 `:core:adb`，本版本增加对 `:core:data` 的依赖，并在 Feature 内部 Use Case 中协调两者的项目自有端口；Feature 之间不产生依赖，app 不承载快捷操作业务编排。

## UI Design Conversion

实现前必须先读 `D:\androidPorject\stitch_adb\technical_terminal_systems\DESIGN.md`，再读取以下页面文件：

| HTML + Tailwind source | Visual reference | Compose target | SHA-256 baseline |
|---|---|---|---|
| `D:\androidPorject\stitch_adb\连接页-未连接状态\code.html` | 同目录 `screen.png` (468×1060) | `DevicesScreen.kt`, `DevicesDiscoveryPanel.kt` | code `B2D85B636A1F08DC2503BE11CB72EAA1AC4CBE091865E956D7765489170C10B2`; image `D5AED2A10966675BBD4E5E7904DBFC7DDBB5B9A1C6000ADBD654376ECFD18AFA` |
| `D:\androidPorject\stitch_adb\连接页-已连接\code.html` | 同目录 `screen.png` (468×1060) | `OverviewScreen.kt`, `SheenApp.kt` | code `36A4E0F26AD199D0CCB1B0189612B95ED43124C81E433CE11BB172AE85624AC5`; image `0DED2C9411B6C0F44F67525CF25718B9BE81615347117DDE79CA682D746D9FD3` |
| `D:\androidPorject\stitch_adb\菜单页\code.html` | 同目录 `screen.png` (468×1046) | `SheenApp.kt`, `DeviceHistoryMenu.kt` | code `BC499DE907D78C2B38BE933D525B7696A4F1F8792B4CE54F2BFF5F5C5280056F`; image `F31F45CFC7120F4E7D975032AD13ACEA17C28D034E2742587AF57D0428669BB9` |

`DESIGN.md` 的 SHA-256 基线为 `D8033B1F788BB576F724BE3E7E0923385C2D35F539725153429B7B8EF1CFC878`。`code.html` 是首要页面转换输入：把 Tailwind 结构、grid/flex 布局、表面层级、圆角、间距、图标语义和状态转换为 Compose，不使用 WebView。`DESIGN.md` 提供 dark-first 色彩语义、4px rhythm 和字体角色；`screen.png` 按表中尺寸归一化核对。开始实现或视觉验收前必须复核哈希；任一文件变化或任一仓库外设计源不可访问时暂停并由项目负责人重新确认基线，不得静默采用漂移文件或仅凭哈希猜测设计内容。

Hanken Grotesk 与 JetBrains Mono 的角色分别映射为 UI 字体与技术数据等宽字体；本版本在没有已批准本地字体资产时使用 `FontFamily.SansSerif` / `FontFamily.Monospace`，不新增运行时字体依赖，并在视觉核对记录中列明字体度量偏差。Material Symbols 名称映射为现有 Compose icon 或本地 vector；图标替代也必须记录。菜单页脚读取真实 `BuildConfig.VERSION_NAME`。

## Quick-Action Boundary Decision

`:core:data` 创建和拥有 app-private artifact、项目自有 `ArtifactSink`、校验和 SAF 导出；`:core:adb` 的 typed API 只写入项目自有 `AdbCaptureSink` 并返回捕获元数据。两个 core 模块互不依赖。`feature:overview` 内部 `QuickActionUseCase` 通过仅使用项目自有端口的适配器连接两者，负责创建 artifact、调用 ADB、验证元数据、请求导出和触发终态清理，并只向 ViewModel 暴露 opaque artifact reference 与结构化结果。适配器不得向 ViewModel/UI 暴露 File、原始路径、OutputStream、ContentResolver、协议字节流或 raw ADB。

`:app` 只构造/注入 Use Case，并把 `CreateDocument` Activity Result 通过 `:core:data` 的平台适配器转换为项目自有 `ExportDestination` 后转交 Use Case；app 不执行捕获、验证、导出或清理业务。由此保持依赖方向为 app/feature → core、Feature 间无依赖且 app 仅装配和导航。

`:core:adb` 还必须对截图、录屏、重启分别返回 session-bound 的 `Supported`、`Unsupported`、`PolicyRejected`、`ProbeFailed` 或 `Unknown` 能力结果；不得只按系统版本推断能力。

## Phase 0/1 Artifacts

- [Research](research.md) records current implementation fit and decisions for session streaming, mutual exclusion, localization, and SAF.
- [Data model](data-model.md) defines navigation, connection, quick-action, artifact and export state with lifecycle invariants.
- [Navigation contract](contracts/navigation-contract.md) and [quick-actions contract](contracts/quick-actions-contract.md) define internal module boundaries and state transitions.
- [Quickstart](quickstart.md) defines focused Gradle validation and sanitized manual acceptance.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | No constitution violations identified. | N/A |
