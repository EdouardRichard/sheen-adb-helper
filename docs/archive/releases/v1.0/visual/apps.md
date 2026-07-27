# v1.0 应用管理页视觉证据

**记录日期**：2026-07-26  
**任务**：T126  
**视觉门禁**：**NOT VERIFIED（OPEN）**

本文件只把已批准设计源、当前 Compose 实现和实际可用证据分层记录。当前没有应用页的 Compose 紧凑/宽屏截图或人工交互录像，因此源码静态可追溯项不构成视觉 PASS；所有未实际渲染或操作的项目保持 `NOT RUN` / `NOT VERIFIED`。

## 证据输入

| 输入 | 结果 | 说明 |
|---|---|---|
| [`../design-baseline.md`](../design-baseline.md) | STATIC CONFIRMED | T001 已登记应用页 HTML 与 PNG 为 MATCH。 |
| `D:\androidPorject\stitch_adb\应用管理页\code.html` | HASH MATCH | 本轮只读复核 SHA-256：`b363e340a81e30f8c5bc40f4321817fc86e50be423a85f500f1f27d29eec815a`。 |
| `D:\androidPorject\stitch_adb\应用管理页\screen.png` | HASH MATCH | 本轮只读复核 SHA-256：`156fce49f7b726b81ee06c254f7b45f9f0b03cdc856e1b9b3eefc74f88d2efc3`；参考画布为 468 × 1060。 |
| [`AppsScreen.kt`](../../../../../feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsScreen.kt) | STATIC REVIEWED | 仅用于确认组件、数值、顺序和状态分支，不代表已渲染。 |
| Compose 紧凑截图 | NOT RUN | 仓库及本轮输出中没有可与参考 PNG 对比的实际截图。 |
| Compose 宽屏截图 | NOT RUN | 未启动模拟器/真机，未生成 700 dp 及以上宽度截图。 |
| 触控、确认流与无障碍交互 | NOT VERIFIED | 未执行按钮热区、焦点顺序、安装/提取或读屏验收。 |

## 静态结构追踪

| 检查项 | 静态结论 | 实际视觉结论 |
|---|---|---|
| 页面层级 | 搜索块、应用 `LazyColumn`、右下 APK 安装入口依次存在；共享顶栏与导航由 App 宿主提供。 | NOT RUN |
| 搜索 | 草稿输入与搜索图标提交分离；中文占位文本精确为“请输入应用名或包名。”，English 来自 Feature 目录。 | NOT VERIFIED |
| 文本顺序 | 卡片先显示安全呈现的包名，再显示应用名；未知应用名使用显式本地化回退。 | NOT RUN |
| 普通应用四按钮 | 静态顺序为提取/下载、禁用/启用、强制停止、卸载。 | NOT VERIFIED |
| 系统/未知应用补位 | 仅构造提取/下载按钮，操作行右对齐且无 `Spacer` 保留隐藏槽位。 | NOT RUN |
| 安装入口 | 56 × 56 dp；使用项目自有 `UploadToDevice` 图标，不使用加号。 | NOT RUN |
| 列表与身份 | 使用 Session、userId、包名组成的稳定 key；包名/应用名显示投影不改变请求身份。 | 不属于视觉证据；NOT VERIFIED |
| 层级与颜色 | 使用 background、surfaceContainerLow、surfaceContainer、surfaceBright、secondaryContainer 及语义色 token。 | NOT RUN |
| 字形 | 包名使用 12/18 monospace；应用名使用 16/24 sans-serif；未加载远程字体。 | NOT RUN |
| 间距 | 搜索块 8 dp 内边距、12 dp 控件间距；卡片 16 dp 内边距、4 dp 卡片间距。 | NOT RUN |
| 搜索圆角 | 当前搜索容器使用 `SheenShapes.extraLarge`（12 dp），已复核 HTML 的自定义 `rounded-xl` 为 8 px。 | **STATIC MISMATCH**；实际差异幅度 NOT RUN |
| 卡片圆角/边框 | 当前卡片为 12 dp 圆角、0.5 dp 半透明边框；HTML 对应 8 px 圆角和 1 px 边框。 | **STATIC MISMATCH**；实际差异幅度 NOT RUN |
| 操作按钮尺寸/间距 | 合同要求 32 × 32 dp 可见 plate、8 dp 间距和至少 44 dp 语义目标；当前实现是相邻的 44 dp `IconButton`，没有独立 32 dp plate 或 8 dp 间距。 | **STATIC MISMATCH**；实际触控/重叠 NOT VERIFIED |
| 搜索按钮尺寸 | 合同要求 40 dp 可见按钮与 44 dp 语义目标；当前 44 dp 尺寸同时承担背景 plate。 | **STATIC MISMATCH**；实际视觉 NOT RUN |
| 宽屏边距 | App 宿主在 700 dp 切换宽屏；应用页依据其内容 `maxWidth >= 600.dp` 才使用 24 dp。宿主侧栏占宽后，部分 expanded 宽度仍会使用 16 dp。 | **STATIC MISMATCH**；宽屏渲染 NOT RUN |

## 紧凑与宽屏对比

| 视口 | 搜索/列表结构 | 按钮与入口位置 | 层级/字形 | 间距/圆角 | 结论 |
|---|---|---|---|---|---|
| 紧凑（参考 468 × 1060） | 参考稿已查看，Compose 未截图。 | 四按钮与非加号入口未在实际渲染中测量。 | 未做叠图/像素对比。 | 源码存在上述 plate、间距、圆角及边框差异。 | NOT RUN / NOT VERIFIED |
| 宽屏（≥700 dp 宿主） | 仅确认宿主永久侧栏与应用页约束分支。 | 未核对按钮拥挤、补位或入口 inset。 | 未核对包名/应用名截断。 | expanded 边距分支与宿主断点不一致。 | NOT RUN；STATIC MISMATCH |

## 十一类页面状态

`Initial` 是源码枚举中的内部占位，但当前状态选择器不会单独返回它，因此不把它冒充为一类已渲染页面状态。下表记录实际选择器可达的十一类页面状态；静态分支存在不等于视觉已经通过。

| 状态 | 静态可追溯结论 | 紧凑 | 宽屏 |
|---|---|---|---|
| Loading | 居中 tonal 面板和 28 dp 进度环。 | NOT RUN | NOT RUN |
| Content | 搜索、稳定 key 列表、卡片操作和安装入口同时存在。 | NOT RUN | NOT RUN |
| Empty | 应用集合为空或搜索无结果时进入空状态面板，但当前文案复用了“零组件提交”语义。**STATIC MISMATCH** | NOT RUN | NOT RUN |
| Error | 显示本地化错误、脱敏技术码和重试。 | NOT RUN | NOT RUN |
| Cancelled | 显示取消状态和重试。 | NOT RUN | NOT RUN |
| Disconnected | 断开连接时有独立状态分支，但当前文案复用了通用错误语义。**STATIC MISMATCH** | NOT RUN | NOT RUN |
| Unsupported | 结构化技术码映射到不支持状态。 | NOT RUN | NOT RUN |
| Outcome unknown | 结果未知状态与普通错误分离。 | NOT RUN | NOT RUN |
| Confirmation | 变更、强制停止及卸载确认层存在；确认时底层内容会切换为通用确认面板，而不是保留应用列表。**STATIC MISMATCH** | NOT RUN | NOT RUN |
| Progress | APK 任务未终止时显示任务层和进度环；底层列表会切换为通用进度面板。 | NOT RUN | NOT RUN |
| Partial success | APK 组件部分成功有独立标题语义。 | NOT RUN | NOT RUN |

## 四按钮、补位与安装入口

| 场景 | 静态结论 | 交互/视觉结论 |
|---|---|---|
| 普通已启用应用 | 提取、禁用、强制停止、卸载，顺序固定。 | NOT VERIFIED |
| 普通已禁用应用 | 第二个操作切换为启用；其他位置不变。 | NOT VERIFIED |
| 普通启用状态未知 | 四个位置仍构造，但启用/禁用按钮不可用。 | NOT VERIFIED |
| 系统应用 | 仅提取按钮，右对齐，不保留隐藏槽位。 | NOT VERIFIED |
| 分类未知应用 | 仅提取按钮，右对齐，不保留隐藏槽位。 | NOT VERIFIED |
| APK 安装入口 | `UploadToDevice` 本地图标表达从主控端选择 APK 并安装；源码不含 `SheenIcons.Add`。 | NOT VERIFIED |

## APK 提取结果与部分成功

[`AppsModels.kt`](../../../../../feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsModels.kt) 区分完整成功、部分成功和零组件提交失败；[`AppsStrings.kt`](../../../../../feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsStrings.kt) 为三者提供中英文语义。当前任务对话框会选择三种不同标题，但只在运行中显示进度环，未静态发现预期组件集合、逐组件成功/失败、已提交数量或清理状态的可见明细。

| 结果 | 标题语义 | 逐组件明细 | 实际视觉 |
|---|---|---|---|
| Complete success | STATIC CONFIRMED | **STATIC MISMATCH：未呈现** | NOT RUN |
| Partial success | STATIC CONFIRMED | **STATIC MISMATCH：未呈现** | NOT RUN |
| None committed | STATIC CONFIRMED | **STATIC MISMATCH：未呈现** | NOT RUN |

部分成功的“保留已验证写入文件、不承诺回滚”属于行为合同；本文件没有 SAF/真机执行证据，保持 NOT VERIFIED。

## 双语差异

[`AppsStrings.kt`](../../../../../feature/apps/src/main/kotlin/com/sheen/adb/feature/apps/AppsStrings.kt) 静态包含简体中文与 English 的搜索、操作、确认、安装步骤、组件结果、状态及无障碍语义。包名、应用名和技术码按安全原文显示，不把设计稿样例写入证据。

- 简体中文紧凑/宽屏截图：NOT RUN。
- English 紧凑/宽屏截图：NOT RUN。
- 长英文搜索占位、四按钮 content description、确认文案换行及包名截断：NOT VERIFIED。
- 运行中切换语言是否保持应用列表、查询和任务身份：需由本地化与真机门禁单独验证，本文件不宣称通过。

## 结论与待补证据

应用页的搜索、文本顺序、普通应用四按钮、系统/未知应用补位和非加号安装入口可静态追溯；完整/部分/零提交也有不同语义标题。但当前缺少紧凑、宽屏、十一类状态、双语及交互截图，并存在搜索/卡片圆角、卡片边框、操作 plate/间距、搜索按钮 plate、expanded 边距、空/断开文案、确认层底图和 APK 逐组件明细等静态合同差异。因此 T126 的视觉门禁保持 **NOT VERIFIED（OPEN）**，不得汇总为 PASS。

补齐门禁至少需要：

1. 使用脱敏确定性夹具生成紧凑与宽屏内容态截图，覆盖普通、系统和未知应用；
2. 为十一类页面状态生成紧凑与宽屏截图；
3. 单独捕获完整成功、部分成功和零提交结果，核对逐组件明细与清理语义；
4. 在简体中文与 English 下核对搜索、确认、按钮语义、文字截断和系统 inset；
5. 修复或经批准解释上述静态差异后，再执行参考 PNG 的结构化对比。
# 2026-07-26 模拟器补充验收

本机 Android 模拟器紧凑布局交互结论为 **PASS**：

- 统一顶部栏、应用名/包名搜索、应用列表和安装悬浮按钮正常渲染。
- 普通应用显示提取、禁用、卸载和强制停止动作；系统应用隐藏受限动作，保留动作右对齐。
- 系统应用列表可加载；特殊框架包名和单个命名系统 APK 不再使整批解析降级。
- 应用名称元数据实际进入列表与搜索；无法可靠读取的条目保持诚实回退。
- APK 提取通过 SAF 成功；独立测试 APK 安装后验证新包存在并显示“APK 安装完成”。
- 打开系统选择器导致 Activity 停止时，待完成任务不再被提前清除。

强制安装冲突的用户确认由自动化覆盖；本轮未在模拟器制造签名冲突。宽屏、English 和真机系统策略仍未执行。
