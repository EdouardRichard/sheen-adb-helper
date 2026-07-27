# v1.0 文件管理页视觉证据

**记录日期**：2026-07-26  
**任务**：T125  
**视觉门禁**：**NOT VERIFIED（OPEN）**

本文件区分设计源复核、Compose 源码静态可追溯性和实际渲染/交互证据。当前没有文件页的 Compose 紧凑或宽屏截图，也没有本轮人工交互录像，因此下列源码可追溯项不构成视觉 PASS，未执行项保持 `NOT RUN`。

## 证据输入

| 输入 | 结果 | 说明 |
|---|---|---|
| [`../design-baseline.md`](../design-baseline.md) | STATIC CONFIRMED | T001 已登记文件页 HTML 与 PNG 为 MATCH。 |
| `D:\androidPorject\stitch_adb\文件管理页\code.html` | HASH MATCH | 本轮只读复核 SHA-256：`6310a8f8b1f059037341d15bce0ef8d36cbc79551a514eb90fc180d614dc8974`。 |
| `D:\androidPorject\stitch_adb\文件管理页\screen.png` | HASH MATCH | 本轮只读复核 SHA-256：`a8a27ce121e5bdef5103aa054b9de4edcc901d6cbe47bc0812934465d5fef0a6`；参考画布为 468 × 1060。 |
| [`FilesScreen.kt`](../../../../../feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesScreen.kt) | STATIC REVIEWED | 仅用于确认组件、数值和状态分支，不代表已渲染。 |
| Compose 紧凑截图 | NOT RUN | 仓库及本轮输出中没有可与参考 PNG 对比的实际截图。 |
| Compose 宽屏截图 | NOT RUN | 未启动模拟器/真机，未生成 700 dp 及以上宽度截图。 |
| 触控与无障碍交互 | NOT VERIFIED | 未执行点击热区、焦点顺序或读屏验收。 |

## 静态结构追踪

| 检查项 | 静态结论 | 实际视觉结论 |
|---|---|---|
| 页面层级 | `FilesScreen` 按路径栏、列表/状态内容、右下角上传入口组织；共享顶栏与导航由 App 宿主提供。 | NOT RUN |
| 路径栏 | 可追溯到 16 dp 水平、12 dp 垂直内边距、横向滚动面包屑、16 dp 分隔图标和至少 44 dp 的路径段目标。 | NOT RUN |
| 文件列表 | 使用稳定 key 的 `LazyColumn`；16 dp 四周主要内边距、8 dp 行间距、12 dp 行内边距、40 dp 类型图标容器。 | NOT RUN |
| 操作位置 | 行体无点击修饰；行尾 44 dp 图标目标承担“进入文件夹”或“下载文件”；上传入口为 56 × 56 dp 并位于页面右下。 | NOT VERIFIED |
| 排序 | [`FilesModels.kt`](../../../../../feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesModels.kt) 静态显示文件夹优先、组内时间倒序、未知时间置后并保持稳定次序。 | 不属于视觉证据；NOT VERIFIED |
| 层级与颜色 | 使用设计系统的 background、surfaceContainer、surfaceContainerLow、surfaceContainerHighest、outlineVariant、primary、tertiary 等本地 token。 | NOT RUN |
| 字形 | 文件名使用 14/20 sans-serif 角色；大小/权限与路径使用本地 monospace 角色；未加载远程字体。 | NOT RUN |
| 间距 | 主要 16/12/8/4 dp 节奏可静态追溯。 | NOT RUN |
| 圆角 | 行容器使用 `SheenShapes.large`（8 dp），而已复核 HTML 的自定义 `rounded-lg` 为 4 px。 | **STATIC MISMATCH**；实际差异幅度 NOT RUN |
| 上传入口形状 | 56 dp、12 dp 圆角及上传语义图标与参考稿可静态对应。 | NOT RUN |
| 宽屏边距 | 文件页固定使用 16 dp 列表边距；合同要求 expanded 页面使用 24 dp。 | **STATIC MISMATCH**；宽屏渲染 NOT RUN |

## 紧凑与宽屏对比

| 视口 | 结构 | 尺寸/操作位置 | 层级/字形 | 间距/圆角 | 结论 |
|---|---|---|---|---|---|
| 紧凑（参考 468 × 1060） | 参考稿已查看，Compose 未截图。 | 未测量实际像素或系统 inset。 | 未做叠图/像素对比。 | 源码存在上述 8 dp/4 px 圆角差异。 | NOT RUN / NOT VERIFIED |
| 宽屏（≥700 dp 宿主） | 仅确认宿主切换为永久侧栏，文件页本身没有宽屏分支。 | 未生成截图；静态边距仍为 16 dp。 | 未核对宽屏文字截断与层级。 | expanded 24 dp 合同未静态落地。 | NOT RUN；STATIC MISMATCH |

## 九类补充状态

以下九类是内容态之外必须使用同一设计语法的状态。静态分支存在不等于视觉已经通过。

| 状态 | 静态可追溯结论 | 紧凑 | 宽屏 |
|---|---|---|---|
| Loading | 居中 tonal 面板、28 dp 进度环，可取消加载。 | NOT RUN | NOT RUN |
| Empty | 居中 tonal 空状态面板。 | NOT RUN | NOT RUN |
| Error | 错误面板、脱敏技术码和重试入口。 | NOT RUN | NOT RUN |
| Cancelled | 取消状态及重试入口。 | NOT RUN | NOT RUN |
| Disconnected | 断开状态独立文案。 | NOT RUN | NOT RUN |
| Unsupported | 由结构化错误映射到不支持状态。 | NOT RUN | NOT RUN |
| Outcome unknown | 清理失败/流关闭映射到结果未知，避免伪装成功。 | NOT RUN | NOT RUN |
| Confirmation | 同名冲突使用覆盖、自动重命名、取消确认层。 | NOT RUN | NOT RUN |
| Progress | 准备、传输、验证、提交共用任务层；传输态显示有界进度。 | NOT RUN | NOT RUN |

## 双语差异

[`FilesStrings.kt`](../../../../../feature/files/src/main/kotlin/com/sheen/adb/feature/files/FilesStrings.kt) 静态包含简体中文和 English 的路径、状态、操作及无障碍语义；冲突按钮与任务摘要中的补充文本也按语言分支。路径、文件名、大小、权限和技术码保持安全原文呈现。

- 简体中文紧凑/宽屏截图：NOT RUN。
- English 紧凑/宽屏截图：NOT RUN。
- 长英文换行、截断、按钮拥挤及 content description：NOT VERIFIED。
- 运行中切换语言是否保持任务与滚动身份：不属于本文件的静态视觉结论，需由本地化和真机门禁单独验证。

## 结论与待补证据

文件页的主要 Compose 结构、操作语义、状态分支和设计 token 可静态追溯，但这不能替代实际视觉验收。当前已发现行圆角和 expanded 边距两项静态合同差异；同时缺少紧凑/宽屏、九类状态及双语截图。因此 T125 的视觉门禁保持 **NOT VERIFIED（OPEN）**，不得汇总为 PASS。

补齐门禁至少需要：

1. 使用脱敏确定性夹具生成紧凑与宽屏的内容态截图；
2. 为九类补充状态分别生成紧凑与宽屏截图；
3. 对简体中文和 English 核对文字截断、操作位置、触控语义及系统 inset；
4. 修复或经批准解释上述静态差异后，再执行参考 PNG 的结构化对比。
# 2026-07-26 模拟器补充验收

本机 Android 模拟器紧凑布局交互结论为 **PASS**：

- 统一顶部栏、面包屑、文件/文件夹列表与上传悬浮按钮正常渲染。
- 文件夹始终位于文件之前，同类按修改时间倒序；行主体不响应，仅尾部进入/下载按钮响应。
- 下载、同名冲突自动重命名、上传、取消和临时文件清理均完成；成功与取消终态文案准确。
- 256 MiB 上传期间页面切换被锁定；取消后 Session 保持连接且可进入其他功能页。

宽屏、English 和真机存储提供程序差异仍未执行。
