# v1.0 Shell 页视觉与自动滚动证据

**任务**：T128  
**复核日期**：2026-07-26  
**页面**：Shell 终端  
**总体视觉/交互门禁**：`NOT VERIFIED`

本文件分别记录设计源完整性、Compose 静态实现、自动滚动策略与实际渲染/交互。静态源码或单元策略不能代替 Android 紧凑/宽屏截图、输入法行为和触控交互。

## 设计源

设计优先级遵循 `specs/007-v1-ui-refactor/research.md`：已批准规格与澄清高于页面 `code.html`，其后依次为 `DESIGN.md`、`screen.png` 与既有 Compose。

| 源 | SHA-256 | 与 T001 基线 |
|---|---|---|
| `D:\androidPorject\stitch_adb\shell终端\code.html` | `904496fe105a7fcd41bbc1e8285256cde017407a3291b78b2ec54e9b2d334ec9` | MATCH |
| `D:\androidPorject\stitch_adb\shell终端\screen.png` | `9fee6f5fbe0a0efded35da5c7342571ad070c95a1e695df6e07285c33a6750c3` | MATCH |
| `D:\androidPorject\stitch_adb\technical_terminal_systems\DESIGN.md` | `d8033b1f788bb576f724be3e7e0923385c2d35f539725153429b7b8ef1cfc878` | MATCH |

**设计源完整性**：`PASS`（3/3 当前哈希与 `docs/archive/releases/v1.0/design-baseline.md` 一致）。

## Compose 静态对照

| 检查项 | 设计/规格要求 | Compose 静态证据 | 静态结论 | 实际渲染/交互 |
|---|---|---|---|---|
| 工具栏 | 清空、过滤、自动滚动依次位于终端上方 | `ShellUtilityBar` 提供 Clear、Filter 与尾端 Auto-scroll；过滤按钮展开单行本地输入框 | VERIFIED | NOT RUN |
| 自动滚动控件 | 显示开/关状态并可切换 | 状态绑定 `state.autoScroll`，变更交给 `setAutoScroll`；当前使用 Material `Switch`，源码未固定 HTML 的 32×16 外观 | STATIC DIFFERENCE | NOT RUN |
| 终端面板 | 黑色、有界、monospace、长内容可滚动 | `ShellTerminalPanel` 使用 `SheenColors.terminalBackground`、`LazyColumn`、16 dp 内边距、稳定记录 key 与 monospace 文本 | VERIFIED | NOT RUN |
| 主控输入 | 可在主控端编辑完整命令并提交 | 黑色面板下方存在单行 `OutlinedTextField`；只有非空 IME Send 才提交。该输入框是功能补全，参考 PNG 未单独展示 | SPEC COMPLETION | NOT RUN |
| 键栏顺序 | Esc、Tab、Ctrl、Alt、上、下；最右回车 | `ShellKeyboardAccessory` 按该顺序构造六键，Spacer 后为 `KeyboardReturn` | VERIFIED | NOT RUN |
| 键尺寸与触控 | 键视觉高 36 dp，最小触控 44 dp | 每个键内部 `height(36.dp)`，外层 `height(minimumTouchTarget)` 且最小宽 44 dp | VERIFIED | NOT RUN |
| 紧凑键栏可达性 | HTML 键栏允许水平滚动，所有键应可达 | Compose 使用固定 `Row`，没有 `horizontalScroll`/惰性横向容器；极窄宽度可能裁切，尚无实际截图或触控证据 | STATIC GAP | NOT RUN |
| Ctrl/Alt 选中 | 一次性修饰键需有明显高亮 | 选中时使用 secondary container；实际色差、消耗后解除高亮的画面未观察 | VERIFIED | NOT RUN |
| 回车/IME | 最右回车仅聚焦输入并弹出输入法，不提交空命令 | `requestIme` 只执行 focus 与 keyboard show；回车键调用该函数，不直接调用 execute | VERIFIED | NOT RUN |
| 安全文本 | 命令、输出和技术码显示有界，不回流为可执行文本 | 命令为有界单行投影，输出为有界多行投影；提交始终读取原始 `state.draft` | VERIFIED | NOT RUN |
| 双语 | 工具栏、输入、风险确认、流状态和可访问性覆盖简中与 English；Esc/Tab/Ctrl/Alt 固定 | `ShellStrings.kt` 为两种语言提供同一键集合与占位模式；固定键标签共用同一英文集合 | VERIFIED | NOT RUN |

## 自动滚动状态

| 场景 | 策略与接线的静态证据 | 静态结论 | 实际交互 |
|---|---|---|---|
| 开启后收到新输出 | `onOutputChanged` 计算最后一项，`ShellTerminalPanel` 调用 `scrollToItem` | VERIFIED | NOT RUN |
| 关闭后收到新输出 | 策略返回空 target，不主动改变当前 `LazyListState` | VERIFIED | NOT RUN |
| 从关闭切到开启 | `setEnabled` 立即返回当前末项 target | VERIFIED | NOT RUN |
| 过滤后锚点越界 | 策略具有 `onVisibleRecordsChanged` 安全夹取分支；当前画面接线主要依赖 LazyColumn 自身约束，尚无 UI 级截图/手势证据 | PARTIALLY VERIFIED | NOT RUN |
| 清空后 | 策略可回到索引 0 且关闭状态不强制滚动；未观察当前阅读位置与清空动画 | VERIFIED | NOT RUN |
| 语言切换 | 文案由 `UiLanguage` 重组；滚动状态不是翻译目录的一部分 | VERIFIED | NOT RUN |

自动滚动策略已有独立测试源码覆盖开启跟随、关闭保留、重新开启跳到底部及过滤/清空夹取。本次视觉证据任务未运行设备或 Compose UI 测试，因此不把策略测试存在性写成实际交互 `PASS`。

## 状态对照

任务所称“九类状态”按当前批准契约解释为 `Content` 之外的九个状态；`ShellVisualState` 加上 `Content` 共十项。

| 状态 | Compose 分支 | 紧凑 | 宽屏 |
|---|---|---|---|
| Content | 有 | NOT RUN | NOT RUN |
| Loading | 有 | NOT RUN | NOT RUN |
| Empty | 有 | NOT RUN | NOT RUN |
| Error | 有 | NOT RUN | NOT RUN |
| Cancelled | 有 | NOT RUN | NOT RUN |
| Disconnected | 有 | NOT RUN | NOT RUN |
| Unsupported | 有 | NOT RUN | NOT RUN |
| OutcomeUnknown | 有 | NOT RUN | NOT RUN |
| Confirmation | 有；另有高风险命令对话框 | NOT RUN | NOT RUN |
| Progress | 有 | NOT RUN | NOT RUN |

除 Content 外，页面通过统一半不透明低层级 Surface 覆盖在黑色终端面板上；Loading/Progress 显示进度，错误技术码使用 monospace 安全投影。实际层级、遮挡、长文换行和 TalkBack 顺序未执行。

## 紧凑与宽屏

| 画像 | 静态证据 | 实际证据 | 结论 |
|---|---|---|---|
| 紧凑 | App 宿主宽度小于 700 dp 时使用底部六项导航；Shell 为纵向工具栏、终端、输入区与键栏 | 未启动 Android 渲染器，未生成当前 Compose 的紧凑截图；键栏极窄宽度可达性仍有静态缺口 | `NOT VERIFIED` |
| 宽屏 | App 宿主宽度至少 700 dp 时使用 `PermanentNavigationDrawer`；Shell 内容占剩余区域 | 未生成当前 Compose 的宽屏截图，未验证工具栏拉伸、终端行宽、IME resize 或键栏定位 | `NOT VERIFIED` |

## 视觉与交互差异结论

- 工具栏、黑色终端、输入区、键序、最右 IME 入口、状态分支、自动滚动策略和双语目录具有可追溯静态证据。
- 当前 Material `Switch` 未静态证明与 HTML 的 32×16 开关一致；紧凑键栏缺少 HTML 的横向滚动行为。这两项不得写为视觉通过。
- 字体实际字形、文本基线、颜色合成、间距、圆角、IME 弹出/收起、自动滚动开/关、紧凑/宽屏以及十项状态均无当前应用实际证据。
- 因上述缺口，本页不得标记视觉或交互 `PASS`；需要补充脱敏的紧凑/宽屏截图、极窄宽度键栏验证和自动滚动交互记录。
# 2026-07-26 模拟器补充验收

本机 Android 模拟器紧凑布局交互结论为 **PASS**：

- 统一顶部栏、清空、过滤、自动滚动、终端面板、命令输入和辅助键栏均可达。
- 修复辅助键宽度后，Esc、Tab、Ctrl、Alt、方向上、方向下和最右回车同时显示；回车只聚焦输入并打开输入法。
- 主控端提交无害命令后，被控端执行并返回预期输出。
- 过滤输入即时生效；清空移除本地记录；自动滚动开关可关闭并恢复。
- 从 Shell 切换到其他页面会关闭子流；返回后显示新的终端会话和新提示符。

宽屏、English、TalkBack 和全部异常状态的实际渲染仍未执行。
