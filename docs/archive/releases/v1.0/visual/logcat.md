# v1.0 Logcat 页视觉验收证据

**任务**：T129  
**记录日期**：2026-07-26  
**证据范围**：脱敏静态检查与自动化契约；未采集应用实际渲染截图  
**整体视觉结论**：**NOT RUN / NOT VERIFIED**

## 设计源门禁

| 设计源 | 本次 SHA-256 | 规划基线 | 结论 |
|---|---|---|---|
| `logcat页/code.html` | `4df0aa18d1e44652b283c4f23a2dadfe3715a5f13bbd413e3e7e1ba38c52a393` | 相同 | MATCH |
| `logcat页/screen.png` | `2db73b1a7d2a36fc16d03cda14c832e6851c41ff52b1e63c83ddb585534ecd1e` | 相同 | MATCH |
| `technical_terminal_systems/DESIGN.md` | `d8033b1f788bb576f724be3e7e0923385c2d35f539725153429b7b8ef1cfc878` | 相同 | MATCH |

哈希与 `docs/archive/releases/v1.0/design-baseline.md` 一致，可使用已批准设计源进行后续渲染对比。本节只证明输入未漂移，不证明 Compose 实际像素效果。

## 静态与自动化证据

- `LogcatScreen.kt` 的静态结构包含 48 dp 工具栏、固定英文 `all/debug/info/error` 顺序、删除和下载本地图标、黑色日志面板、等宽 12 sp/18 sp 行高，以及 840 dp 起启用的副面板分支。
- `LogcatPresentationTest` 对上述结构、响应式分支、完整页面状态键和安全原文呈现建立源代码契约。
- `LogcatBufferTest` 验证 Fatal 在 `all/debug/info/error` 四档均可见，同时没有第五个 Fatal 工具栏按钮。
- `LogcatStringsTest` 验证中英文键集合、占位参数、采集/保存终态和可访问性键契约；日志原文与四个级别标签保持逐字值。
- 本次强制重跑命令：

  ```powershell
  .\gradlew.bat --no-parallel --no-daemon --console=plain --rerun-tasks :feature:logcat:testDebugUnitTest --tests '*LogcatPresentationTest' --tests '*LogcatStringsTest' --tests '*LogcatBufferTest'
  ```

  结果：`BUILD SUCCESSFUL`，45 个任务均实际执行。该结果只属于自动化契约证据，不是视觉渲染证据。

## 紧凑与宽屏视觉矩阵

| 检查项 | 紧凑布局 | 宽屏布局 | 当前结论 |
|---|---|---|---|
| HTML 层级、控件位置与操作顺序 | 未采集实际截图 | 未采集实际截图 | NOT RUN |
| 48 dp 工具栏实际尺寸 | 未测量 | 未测量 | NOT VERIFIED |
| `all/debug/info/error` 标签顺序与选中态 | 未渲染 | 未渲染 | NOT VERIFIED |
| 删除/下载按钮位置、图标与 44 dp 触控区 | 未测量 | 未测量 | NOT VERIFIED |
| 黑色面板、颜色、字形、行高、间距与圆角 | 未对比 | 未对比 | NOT VERIFIED |
| 超长行、控制字符、双向文本及横向滚动 | 未做界面交互 | 未做界面交互 | NOT RUN |
| 宽屏进程上下文副面板 | 不适用 | 未渲染 | NOT RUN |
| 系统栏 inset 与参考图归一化差异 | 未记录 | 未记录 | NOT RUN |

在获得稳定模拟器/设备的紧凑与宽屏截图并按参考宽度归一化前，不对像素、间距、字形角色或响应式布局作通过声明。

## 十类状态视觉矩阵

| 状态 | 静态/自动化覆盖 | 实际中英文渲染 | 视觉结论 |
|---|---|---|---|
| never-started | 状态键与开始入口有契约 | 未采集 | NOT VERIFIED |
| loading | 状态键与进度指示结构有契约 | 未采集 | NOT VERIFIED |
| content | 日志列表、黑色面板与安全原文有契约 | 未采集 | NOT VERIFIED |
| empty | 空窗口状态分支有契约 | 未采集 | NOT VERIFIED |
| error | 错误与安全技术码分支有契约 | 未采集 | NOT VERIFIED |
| cancelled | 取消语义键有契约 | 未采集 | NOT VERIFIED |
| disconnected | 断开语义键有契约 | 未采集 | NOT VERIFIED |
| unsupported | 不支持语义键有契约 | 未采集 | NOT VERIFIED |
| outcome-unknown | 结果未知语义键有契约 | 未采集 | NOT VERIFIED |
| progress | 保存写入进度分支有契约 | 未采集 | NOT VERIFIED |

另有 `LimitTime`、`LimitBytes` 和 `Stopped` 静态分支，但本任务未取得其实际渲染证据。

## Fatal 与双语专项

| 检查项 | 自动化结论 | 视觉结论 |
|---|---|---|
| Fatal 在四个级别筛选中均显示 | 已由缓冲区测试验证 | NOT VERIFIED |
| 工具栏不存在第五个 Fatal 按钮 | 已由呈现契约验证 | NOT VERIFIED |
| `all/debug/info/error` 两种语言均保持英文 | 已由文案契约验证 | NOT VERIFIED |
| 中英文业务文案键及占位模式一致 | 已由文案契约验证 | NOT VERIFIED |
| 语言切换后的实际截断、换行和可访问性朗读 | 无实际界面证据 | NOT RUN |

## 待补证据

1. 使用脱敏固定日志夹具，在稳定紧凑尺寸分别采集中英文截图。
2. 在至少 840 dp 宽度分别采集中英文截图并确认副面板。
3. 对十类状态逐一留存渲染截图；Fatal 只作为日志结果出现。
4. 对照 `code.html`、`DESIGN.md`、`screen.png` 记录尺寸、颜色、字形、间距、圆角和经批准的差异。

在上述证据完成前，T129 的自动化契约可复现，但视觉验收仍为 **NOT RUN / NOT VERIFIED**。
# 2026-07-26 模拟器补充验收

本机 Android 模拟器紧凑布局交互结论为 **PASS**：

- 页面首次进入只显示开始入口，用户触发后才读取被控端日志。
- 工具栏固定使用 `all`、`debug`、`info`、`error`；各级别切换后日志窗口立即重算。
- 文本过滤输入后立即生效；清空后当前可见日志为零，新日志仍可继续进入窗口。
- 下载动作通过 SAF 选择输出目录，保存本次完整窗口为非空文本文件。
- 切换到 Shell 后，被控端匹配的 Logcat 子进程数量由 2 降为 0，证明离页停止和资源清理生效。
- 顶部栏与其他功能页统一。

本节不记录任何原始日志内容。宽屏、English、Fatal 实际样本和全部异常状态逐像素对照仍未执行。
