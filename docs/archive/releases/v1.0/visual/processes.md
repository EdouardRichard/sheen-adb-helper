# v1.0 进程页视觉证据

**任务**：T127  
**复核日期**：2026-07-26  
**页面**：进程管理  
**总体视觉门禁**：`NOT VERIFIED`

本文件区分设计源完整性、Compose 静态证据与实际渲染证据。源码中存在某个尺寸、颜色或状态分支，只能证明实现意图，不能代替 Android 实际渲染截图、交互或真机验收。

## 设计源

设计优先级遵循 `specs/007-v1-ui-refactor/research.md`：已批准规格与澄清高于页面 `code.html`，其后依次为 `DESIGN.md`、`screen.png` 与既有 Compose。

| 源 | SHA-256 | 与 T001 基线 |
|---|---|---|
| `D:\androidPorject\stitch_adb\进程管理\code.html` | `fbf6894c48cf514d912f451acbdf0facd1759980eb0eb9de31129c856dfd2fa7` | MATCH |
| `D:\androidPorject\stitch_adb\进程管理\screen.png` | `4e50ba1e448756a5bbe7f0ae47c7e088baf9e7d2af061c07f7521dd702930349` | MATCH |
| `D:\androidPorject\stitch_adb\technical_terminal_systems\DESIGN.md` | `d8033b1f788bb576f724be3e7e0923385c2d35f539725153429b7b8ef1cfc878` | MATCH |

**设计源完整性**：`PASS`（3/3 当前哈希与 `docs/archive/releases/v1.0/design-baseline.md` 一致）。

## Compose 静态对照

| 检查项 | 设计要求 | Compose 静态证据 | 静态结论 | 实际渲染 |
|---|---|---|---|---|
| 搜索 | 顶部单一进程名搜索框；无搜索按钮 | `ProcessesScreen.kt` 只有一个 `OutlinedTextField`，48 dp 高、前置 Search 图标，输入直接交给 `updateQuery`，无 `onSearch`/手动刷新动作 | VERIFIED | NOT RUN |
| 列表结构 | 内容区为惰性进程列表 | `LazyColumn` 使用 Session、generation、PID 与启动时间组成稳定 key | VERIFIED | NOT RUN |
| 72 dp 行 | 每行最小高度 72 dp，名称在上、指标在下、操作在右 | `ProcessRow` 使用 `heightIn(min = 72.dp)`、16 dp 横向与 8 dp 纵向内边距；末端为独立停止图标触控区 | VERIFIED | NOT RUN |
| 指标 chip | CPU 与内存为等风格紧凑 chip | 两个 `MetricChip` 均使用本地图标、monospace 小字、8 dp/2 dp 内边距和 4 dp 圆角 | VERIFIED | NOT RUN |
| 未知值 | CPU/PSS 不可读时明确显示未知，不伪造 0 | `metricText` 仅在字段可用且数值非空时格式化，否则读取 `UNKNOWN_METRIC` | VERIFIED | NOT RUN |
| 结束按钮 | 行末错误色操作，满足最小触控目标 | `SheenIcons.Stop` 位于 40 dp 错误容器中，外层为共享 44 dp 最小触控区并带双语 content description | VERIFIED | NOT RUN |
| 结束确认 | 先选择按单进程或按应用结束，再执行确认 | `TerminationDialog` 分两阶段；无应用关联时隐藏按应用范围并给出说明，确认目标绑定原始进程 identity | VERIFIED | NOT RUN |
| 状态语法 | 内容态外覆盖九类状态：加载、空、错误、取消、断开、不支持、结果未知、确认、进行中 | `ProcessesVisualState` 包含 `Content` 以及上述九个非内容态；非内容态使用统一低层级 Surface，加载/进行中显示进度，错误/结果未知为 polite live region | VERIFIED | NOT RUN |
| 安全文本 | 进程名、PID 与技术码不能破坏布局或改变操作身份 | 显示投影分别有界为 96/24/64 code points；确认仍使用 `pending.entry.identity` | VERIFIED | NOT RUN |
| 双语 | 页面状态、未知值、范围确认、结果和可访问性同时覆盖简中与 English | `ProcessesStrings.kt` 为 `ZH_CN`、`EN_US` 提供同一完整键集合；CPU 保持技术词，未知值分别为“未知”/“Unknown” | VERIFIED | NOT RUN |

说明：任务所称“九类状态”按当前批准契约解释为 `Content` 之外的九个状态；Compose 同时保留 `Content`，因此状态枚举共十项。

## 紧凑与宽屏

| 画像 | 静态证据 | 实际证据 | 结论 |
|---|---|---|---|
| 紧凑 | 页面填满宿主；App 宿主在宽度小于 700 dp 时使用底部六项导航。参考 PNG 展示单列搜索与 72 dp 行结构 | 未启动 Android 渲染器，未生成当前 Compose 的紧凑截图，未做归一化叠图 | `NOT VERIFIED` |
| 宽屏 | App 宿主在宽度至少 700 dp 时使用 `PermanentNavigationDrawer`，同一进程页内容填满剩余区域 | 未生成当前 Compose 的宽屏截图；未验证超长名称、双语或对话框在宽屏上的实际约束 | `NOT VERIFIED` |

## 实际状态证据矩阵

下列条目均已找到 Compose 分支，但没有对应的当前应用截图或设备/模拟器交互记录。

| 状态 | 紧凑 | 宽屏 | 备注 |
|---|---|---|---|
| Content | NOT RUN | NOT RUN | 未核对搜索、72 dp 行、chip、停止图标的像素结果 |
| Loading | NOT RUN | NOT RUN | 未核对进度指示与页面层级 |
| Empty | NOT RUN | NOT RUN | 未核对中英文空状态换行 |
| Error | NOT RUN | NOT RUN | 未核对错误与安全技术码的实际布局 |
| Cancelled | NOT RUN | NOT RUN | 未核对取消状态 |
| Disconnected | NOT RUN | NOT RUN | 未核对断开提示 |
| Unsupported | NOT RUN | NOT RUN | 未核对不支持提示 |
| OutcomeUnknown | NOT RUN | NOT RUN | 未核对 live region 与长文本 |
| Confirmation | NOT RUN | NOT RUN | 未执行单进程/按应用两阶段确认 |
| Progress | NOT RUN | NOT RUN | 未核对结束中状态 |

## 视觉差异结论

- 结构、操作位置、尺寸常量、状态分支与双语目录具有可追溯的静态实现证据。
- 字体实际字形、文本基线、颜色合成、间距、圆角、紧凑/宽屏约束、系统栏与所有状态的实际画面均未渲染核对。
- 因缺少当前 Compose 的应用截图与状态截图，本页不得标记视觉 `PASS`；后续需在指定紧凑/宽屏画像生成脱敏截图并逐项对比后更新结论。
# 2026-07-26 模拟器补充验收

紧凑布局已在本机 Android 模拟器完成运行时验证，结论为 **PASS（紧凑中文路径）**：

- 统一顶部栏、搜索框、72 dp 列表行、CPU/内存 chip 与停止按钮均正常渲染。
- CPU/内存在完整刷新周期后显示真实数值；高负载应用进程显示约 100% CPU 和非零内存，5 秒刷新有效。
- 搜索输入即时过滤。
- 未可靠关联应用的目标仅显示单进程选项；可靠关联的普通应用同时显示“仅结束此进程”和“结束整个应用”。
- `shell` UID 目标被安全策略拒绝，普通应用按应用结束后确认目标进程消失。
- 确认对话框打开后暂停轮询，避免 generation 变化使确认失效；结束后恢复轮询并显示可访问的结果横幅。

宽屏、English、异常状态逐像素对照仍未执行，不由本节覆盖。
