# Implementation Plan: v1.0 全功能 UI 重构

**Branch**: `007-v1-ui-refactor` | **Date**: 2026-07-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/007-v1-ui-refactor/spec.md`

## Summary

在不扩大权限、不引入远程 UI 资源、不突破既有 ADB 分层的前提下，将文件、应用、进程、Shell、Logcat 五页严格按对应 `code.html` 的结构和 Tailwind 设计值转换为 Jetpack Compose，并用 `DESIGN.md` 统一补足加载、空、错误、确认、长任务和响应式状态。实现顺序先补齐 `:core:adb` 中缺失的持续交互式 Shell、APK 提取/安装/卸载等能力契约，再由 `:core:data` 提供 SAF 单文件阶段提交与多文件逐项结果所需原语，随后改造 Feature 状态机和 App 级导航/浮层。双语沿用现有 `ui_language_v1` 偏好与 Kotlin 类型化文案目录：语言切换只重组呈现，不重建 Activity、ViewModel、ADB Session 或活动任务。用户可见版本更新为 `v1.0`，内部 `versionCode` 从当前 3 单调递增为 4。

## Technical Context

**Language/Version**: Kotlin 2.3.20；Java 17 工具链；Gradle Kotlin DSL

**Primary Dependencies**: Jetpack Compose BOM 2026.06.01、AndroidX Lifecycle/ViewModel、Kotlin Coroutines 1.10.2、Kadb 2.1.1、Okio 3.17、ZXing 3.5.4、apk-parser 2.6.10；不新增运行时 Web、字体或图标依赖

**Storage**: Android DataStore 继续以既有 `ui_language_v1` 保存唯一语言偏好并保存既有设置；页面快照、查询、滚动位置、Shell 历史与 Logcat 窗口仅保存在当前进程内存；主控端文件输入/输出继续使用 SAF 和用户明确选择的 URI/目录

**Testing**: TestNG 单元/契约测试、Gradle 模块测试与 assemble、Compose 状态/语义契约测试、固定夹具性能测试、设计截图人工视觉核对、三台既有被控端真实设备验收

**Target Platform**: Android `minSdk 30`、`targetSdk 36`、`compileSdk 36`；手机紧凑布局为验收主基线，宽屏按 `code.html` 的 `md` 分支和既有永久导航规则响应

**Project Type**: 多模块本地 Android 应用（Kotlin + Jetpack Compose）

**Performance Goals**: 页面切换 500 ms 内可交互；本地过滤/搜索/点击的 95% 反馈在 100 ms 内；1,000 项列表保持惰性渲染；10 MiB 文本窗口采用有界增量状态；进程刷新严格 5 秒、无重叠；不可见页面无轮询和采集

**Constraints**: 纯本地；单一活动 ADB Session；ADB Socket、TLS、配对、原始命令和协议流只在 `:core:adb`；所有操作可取消、有界超时、结构化错误和确定性清理；不扩大 Manifest 权限；设计远程资源不得在运行时加载；实际传输/写入阶段的长任务禁止切页，文件选择器浏览/取消阶段不锁页；多 APK 输出不承诺跨文件原子提交或自动回滚

**Scale/Scope**: 6 个主页面，其中 5 个页面完整 UI 重构，连接页补齐错误/配对/截屏反馈；简体中文与 English；文件列表至少 1,000 项、Shell/Logcat 有界文本窗口最高 10 MiB

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-research gate

| Gate | Result | Plan evidence |
|---|---|---|
| 当前功能来源 | PASS | 仅以 `.specify/feature.json` 指向的 `specs/007-v1-ui-refactor/spec.md` 为活动规格；不以分支或历史编号猜测 |
| 纯本地与隐私 | PASS | 不新增账号、后端、广告、遥测、支付、Root 或无障碍；设计样例数据不进入默认数据或记录 |
| ADB 分层 | PASS | 所有新增协议、Shell、配对、APK 与 Session 资源契约归属 `:core:adb`；Feature 只消费项目自有端口 |
| 单 Session 与资源所有权 | PASS | 每个长任务、采集、轮询和终端子流绑定发起时 Session；切换/断开时取消并丢弃旧结果 |
| 取消、超时、错误与清理 | PASS | 文件、APK、Shell、Logcat、进程和配对均设计显式生命周期、结构化结果与清理终态 |
| 权限最小化 | PASS | 使用现有网络、前台服务、通知和 SAF 能力；本计划不修改 Manifest 或权限矩阵 |
| 依赖与许可证 | PASS WITH RELEASE BLOCKER | 计划不新增依赖；既有 `spake2-java:1.0.5` 的 GPL-3.0-or-later 发布阻断仍须按 ADR 0004 在发布前解决，构建成功不能代替发布合规 |
| 用户确认与危险操作 | PASS | 卸载、强制安装、进程结束和高风险 Shell 均有不可绕过的确认状态 |
| 双语与状态隔离 | PASS | 复用现有 DataStore 偏好与显式 `UiLanguage` 呈现链；业务状态只保存语义代码/参数，切换语言不重建 Session、ViewModel、流或长任务 |
| 规格/规划边界 | PASS | Plan 阶段只创建规格设计产物，不修改业务代码、Manifest、权限矩阵、依赖或 ADR |

### Post-design gate

Phase 1 设计完成后再次检查：

- [x] `contracts/adb-feature-contracts.md` 保持 `:core:adb` 为协议与原始命令唯一所有者。
- [x] `data-model.md` 为长任务、Shell、Logcat、配对和 Session 切换定义了取消及清理终态。
- [x] `contracts/ui-compose-contract.md` 禁止运行时远程字体、CSS、图标和样例数据。
- [x] `contracts/localization-module-contract.md` 固定现有语言偏好链、文案归属与“只重组呈现”不变量，不引入第二套 Locale 机制。
- [x] `contracts/navigation-lifecycle.md` 将长任务锁页、后台取消、系统返回和页面可见性统一到 App 级策略。
- [x] `contracts/adb-feature-contracts.md` 将多 APK 提取定义为逐文件提交和逐项结果，不再承诺跨文件原子提交或回滚。
- [x] `quickstart.md` 将自动化、模拟数据、视觉和真实设备证据分开，不以单一构建结果宣告完成。
- [x] 没有新增权限或依赖；既有许可证发布阻断被明确保留为 v1.0 发布门禁。

结论：设计可进入任务拆分；不存在由本功能新增的宪法违反。v1.0 的“功能实现完成”与“具备发布资格”必须分开，后者仍受 ADR 0004 既有许可证门禁约束。

## UI Design Source of Truth

实现优先级固定为：

1. 当前 `spec.md` 与用户明确澄清；
2. 每页 `code.html` 的 DOM 层级、Tailwind 类、尺寸和响应式分支；
3. `technical_terminal_systems/DESIGN.md` 的全局设计令牌；
4. `screen.png` 的视觉核对；
5. 既有 Compose 实现仅用于保留已授权能力和状态，不得反向覆盖设计稿。

基线文件及 SHA-256 已记录在 [research.md](./research.md)。任何后续设计文件变化都必须重新计算哈希并记录差异，不能在未审计情况下把新设计与本计划混用。

## Architecture Authorities

- [ADB Session 与协议现状](../../docs/architecture/adb-session.md)：单 Session、协议/命令/配对与结构化错误边界。
- [设备与本地数据现状](../../docs/architecture/device-and-data.md)：`:core:data`、DataStore、SAF 与临时数据边界。
- [应用管理现状](../../docs/architecture/application-management.md)：现有应用安全快照、元数据、操作与限制。
- [调试与诊断能力现状](../../docs/architecture/diagnostics.md)：Shell、进程、Logcat 的当前流与内存边界。
- [安全、权限与交付现状](../../docs/architecture/security-and-delivery.md)：权限、依赖与发布阻断事实。
- [ADR 0003](../../docs/adr/0003-v001-modules-data-and-streaming.md)：模块依赖方向和 App/Core/Feature 基线。
- [ADR 0006](../../docs/adr/0006-logcat-recent-history-follow-mode.md)：Logcat Core/Feature 分层与前台采集。
- [ADR 0007](../../docs/adr/0007-wireless-discovery-and-local-pairing-lifecycle.md)：NSD-only 配对发现和敏感生命周期。
- [ADR 0008](../../docs/adr/0008-connected-page-quick-actions.md)：`:feature:overview` 的截屏/录屏/重启及 SAF 导出归属。
- [ADR 0004](../../docs/adr/0004-spake2-license-compliance.md)：继续有效的发布合规阻断。

这些文档描述 HEAD 当前事实，不在 Plan 阶段改写。v1.0 未来能力只记录在本功能工件；实现合入且有证据后才能同步 `docs/architecture/`。本次同步不需要新 ADR、权限或第三方依赖；唯一新增依赖边是 `:feature:apps -> :core:data`。

## Implementation Strategy

### Phase A — ADB 与本地输出能力先行

1. 在 `:core:adb` 增加项目自有的持续交互式 Shell 子流端口、输入事件、关闭原因和结构化错误；适配器独占 Kadb 流与资源清理。
2. 扩展应用领域模型，支持普通/系统/未知分类、APK 组件清单、提取、单 APK 预检/安装、卸载、强制安装阶段及结果未知。
3. APK 提取由 `:core:adb` 提供 Session 绑定的组件清单和有界 Sync 流；`:core:data` 提供独立目标目录及逐文件暂存、验证、提交和当前暂存项清理。`:feature:apps` 负责编排并保存逐文件结果：全部提交才是完整成功，至少一个已验证文件与至少一个失败/未完成项构成部分成功；已提交文件保留，不宣称跨文件原子提交或自动回滚。
4. `:feature:apps` 在实现期增加对 `:core:data` 的直接依赖；`:core:adb` 与 `:core:data` 仍互不依赖，二者只由 Feature 内项目自有端口编排。
5. 保留既有文件传输、进程身份防 PID 复用、Logcat 有界流和 NSD 配对架构；只补齐 v1.0 所需契约，不在 Feature 拼接 Shell。

### Phase B — Feature 状态机与性能

1. `:feature:files` 增加文件夹优先、组内修改时间倒序、未知时间稳定置尾的纯内存排序，并暴露长任务导航锁。
2. `:feature:apps` 增加点击搜索、系统/未知按钮策略、APK 提取逐文件交付、单 APK 安装、卸载、强制安装二阶段确认和导航锁状态；多文件终态区分完整成功、部分成功、全失败、取消与结果未知。
3. `:feature:processes` 合并为单一进程名即时过滤；页面可见时启动 5 秒非重叠刷新，离页/后台/断开立即停止。
4. `:feature:shell` 管理持续子流、本地待提交命令、一次性 Ctrl/Alt、实时键发送、有界记录、过滤、历史和同 Session 分隔。
5. `:feature:logcat` 将采集窗口与显示过滤分离；离页停止但同 Session 保留快照，重新开始清空，保存始终使用完整快照；`error` 匹配 Error 与 Fatal，Fatal 同时属于全部四档。
6. `:feature:devices` 补齐可关闭错误、配对内容状态、30 秒 NSD 配对服务发现、六位码显式提交、本机配对和敏感状态清理；`:app` 只把该内容承载为根级浮层。
7. `:feature:overview` 保留截屏/录屏捕获与 SAF 导出业务编排，并仅在实际导出写入阶段暴露导航锁；等待文件选择器返回时不锁页。
8. 各 Feature 的 ViewModel/Reducer 只保存语义状态、错误代码和格式化参数，不保存已翻译 prose；目标页面在 Compose 呈现时按当前 `UiLanguage` 解析本模块文案目录。

### Phase C — Compose UI 与 App 级编排

1. `:core:ui` 固化本地色彩、间距、圆角、字形角色和图标向量，并提供 `UiLanguage`、类型化文案键/格式参数及共享 chrome/通用状态文案；不拉取 Google Fonts 或 Material Symbols，不持有文件、应用、进程、Shell、Logcat 等业务专属文案。
2. 五页按 [UI Compose contract](./contracts/ui-compose-contract.md) 重写为惰性列表/有界终端布局，补齐设计遗漏状态但不改变设计中已有控件顺序。
3. `:core:data` 继续拥有 `LanguagePreference`、`ui_language_v1` 的 Flow/写入和损坏值回退；`:feature:settings` 是唯一用户修改入口。不得新增 Android `LocaleManager`/`AppCompatDelegate` 或资源目录驱动的第二套语言状态。
4. `:app` 在唯一根 Compose 宿主收集语言 Flow，映射为 `UiLanguage` 并显式传给 App chrome、根浮层与各页面；语言变化只触发呈现重组，不重建 `AppContainer`、`AdbSessionManager`、ViewModel、流、窗口或任务。
5. 每个本版本目标 Feature 拥有本页专属类型化文案目录和语义到文案键的映射；`:feature:settings` 只同步语言选择控件及保存错误所需文案，不扩大为无关设置页重构。`:app` 只拥有导航、根浮层和应用级确认文案，`:core:ui` 只拥有共享原语/文案。技术数据、路径、包名、进程名、命令、Shell/Logcat 原文不进入翻译目录；`all/debug/info/error` 两种语言保持英文。
6. `:app` 建立唯一页面索引、淡出淡入切换、受限水平手势、底部导航一致性、响应式宽屏布局和根级配对浮层。
7. `:app` 聚合 Feature 的导航锁和页面可见性。文件上传/下载、APK 提取/安装、Logcat 保存、截屏保存和既有录屏保存/导出仅从实际传输/写入开始锁定；文件选择器浏览/取消、复制、查看、Shell 和 Logcat 采集不锁页。锁定时禁用底部/滑动，系统返回先确认取消并等待清理，普通返回直接退出应用。
8. `versionName` 设为 `1.0`、`versionCode` 设为 4，统一通过构建元数据展示 `v1.0`。

### Phase D — 验证与交付门禁

1. 纯逻辑测试覆盖排序、过滤、状态机、Session 隔离、刷新调度、按键编码、安装/卸载确认、逐文件部分成功和导航锁起止阶段。
2. Gradle 测试与 assemble 使用固定 JDK 21 环境并关闭 daemon/parallel，等待明确退出码。
3. 以 1,000 项列表和 10 MiB 文本窗口做性能夹具；检查不可见页无请求。
4. 按参考宽度归一化截图，逐页核对 HTML 结构、控件位置、颜色、字体角色、间距和圆角；系统栏与能力降级单列。
5. 双语契约测试覆盖每个目标语义键的中英文完整性、格式参数一致性、英文界面无中文回退、技术值不翻译，以及语言切换前后 Session ID、ViewModel/任务/流 generation 与导航锁不变。
6. 在三台既有被控端分别执行配对、传输、应用、进程、Shell、Logcat 与双语验收；真实设备信息和原始输出不得进入仓库。
7. 发布前单独复核 ADR 0004 许可证门禁；未解决时只能报告功能/测试状态，不能宣告 v1.0 可发布。

## Project Structure

### Documentation (this feature)

```text
specs/007-v1-ui-refactor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── adb-feature-contracts.md
│   ├── localization-module-contract.md
│   ├── navigation-lifecycle.md
│   └── ui-compose-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # 已存在；本 Plan 同步后需由 /speckit-tasks 重新生成
```

### Source Code (repository root)

```text
app/
└── src/{main,test}/         # 页面宿主、语言 Flow 分发、导航/根浮层/生命周期、版本与 App 级契约测试

core/
├── adb/src/{main,test}/     # 唯一 ADB 协议边界：Session、Shell、APK、Logcat、配对、文件、进程
├── data/src/{main,test}/    # 语言偏好、SAF 输入/目录输出、逐文件暂存提交与确定性清理
└── ui/src/{main,test}/      # UiLanguage/文案原语、共享文案、Compose 设计令牌、本地图标与语义

feature/
├── devices/src/{main,test}/ # 未连接、发现、配对内容状态、连接错误
├── overview/src/{main,test}/# 已连接概览、截屏/录屏/重启及媒体导出
├── settings/src/{main,test}/# 唯一语言偏好修改入口
├── files/src/{main,test}/   # 浏览/传输、文件页状态与本页文案
├── apps/src/{main,test}/    # 应用/APK 工作流、逐文件结果与本页文案
├── processes/src/{main,test}/# 进程过滤/刷新/结束范围与本页文案
├── shell/src/{main,test}/   # 终端交互状态与本页文案
└── logcat/src/{main,test}/  # 采集/过滤/完整快照保存与本页文案

docs/architecture/           # 只作为既有架构依据；实现中若产生新架构决策，先另行确认 ADR
```

**Structure Decision**: 保留现有多模块 Android 结构。协议与远端资源所有权集中在 `:core:adb`；语言偏好和 SAF 单文件阶段提交/逐项输出原语在 `:core:data`；语言/视觉共享原语在 `:core:ui`；每个页面的业务状态、SAF 工作流编排和专属文案留在对应 Feature；跨页面导航、根浮层与语言分发只由 `:app` 编排。`:feature:apps` 仅新增对现有 `:core:data` 的依赖，不新增模块、外部依赖、Core 间依赖或横向 Feature 依赖。

## Complexity Tracking

无由本功能新增且需要豁免的宪法违反。既有 GPL 传递依赖属于发布门禁遗留项，不作为扩大本计划复杂度的理由，也不得被本功能的构建或测试结果掩盖。
