# v1.0 连接页 scoped 视觉验收证据

**任务**：T130  
**记录日期**：2026-07-26  
**授权范围**：错误关闭、根配对浮层、连接页全状态、截屏反馈差异  
**证据范围**：脱敏静态检查与自动化契约；未采集应用实际渲染截图  
**整体视觉结论**：**NOT RUN / NOT VERIFIED**

本文不重新验收已完成的菜单页、连接页整体重构，也不扩展记录文件、应用、进程、Shell 或 Logcat 页面。

## 设计源门禁

| 设计源 | 本次 SHA-256 | 规划基线 | 结论 |
|---|---|---|---|
| `连接页-未连接状态/code.html` | `b2d85b636a1f08dc2503be11cb72eaa1ac4cbe091865e956d7765489170c10b2` | 相同 | MATCH |
| `连接页-已连接/code.html` | `36a4e0f26ad199d0ccb1b0189612b95ed43124c81e433ce11bb172ae85624ac5` | 相同 | MATCH |
| `technical_terminal_systems/DESIGN.md` | `d8033b1f788bb576f724be3e7e0923385c2d35f539725153429b7b8ef1cfc878` | 相同 | MATCH |

连接页规划基线只登记两份 HTML 与设计风格文件，没有连接页参考 PNG。本节只证明设计输入未漂移。

## 静态与自动化证据

- `ConnectionPagePresentationTest` 覆盖错误卡关闭入口、关闭时不改变输入/发现结果/Session、未连接语义状态集合，以及设备标签和技术码的安全原文呈现。
- `RootPairingOverlayTest` 覆盖配对内容由 App 根层持有，点击外部、系统返回、进入后台和 Session 切换时关闭并清理敏感状态。
- `QuickActionPresentationTest` 覆盖截屏完成后进入待保存状态，待保存分支不显示录屏或成功反馈。
- `OverviewStringsTest` 覆盖截屏、录屏、保存阶段和终态的中英文语义键。
- 本次使用固定 JDK、关闭 parallel/daemon 并带 `--rerun-tasks` 执行以下聚焦测试：

  ```powershell
  .\gradlew.bat --no-parallel --no-daemon --console=plain --rerun-tasks :feature:devices:testDebugUnitTest --tests '*ConnectionPagePresentationTest'
  .\gradlew.bat --no-parallel --no-daemon --console=plain --rerun-tasks :feature:overview:testDebugUnitTest --tests '*QuickActionPresentationTest' --tests '*OverviewStringsTest'
  .\gradlew.bat --no-parallel --no-daemon --console=plain --rerun-tasks :app:testDebugUnitTest --tests '*RootPairingOverlayTest'
  ```

  三次命令均以退出码 0 结束；Overview 命令明确报告 `BUILD SUCCESSFUL` 且 45 个任务实际执行。它们属于自动化契约证据，不替代视觉渲染与交互证据。

## 1. 错误关闭

| 检查项 | 静态/自动化结论 | 实际视觉结论 |
|---|---|---|
| 错误框右侧提供关闭图标按钮 | 源代码与契约包含本地 Close 图标及独立 dismiss 回调 | NOT VERIFIED |
| 关闭只隐藏当前错误，不重置连接输入 | 自动化契约覆盖 | NOT VERIFIED |
| 关闭不重启扫描、不取消/替换 Session | 自动化契约覆盖 | NOT VERIFIED |
| 长技术码、控制字符和双向文本不破坏错误框 | 安全原文契约覆盖 | NOT RUN |
| 中英文错误标题及关闭可访问性文本 | 文案契约存在 | NOT RUN |

未采集错误框的实际位置、颜色、边框、间距、关闭按钮触控区或中英文截断截图。

## 2. 根配对浮层

| 检查项 | 静态/自动化结论 | 实际交互/视觉结论 |
|---|---|---|
| 二维码/配对码内容位于 App 根浮层，而非连接页内联区域 | App 宿主静态结构与控制器契约覆盖 | NOT VERIFIED |
| 遮罩覆盖当前应用页面，配对卡内部点击不关闭 | 静态点击层级存在 | NOT RUN |
| 点击非配对区域关闭 | 控制器契约覆盖 | NOT RUN |
| 系统返回优先关闭浮层 | 控制器契约覆盖 | NOT RUN |
| 后台或 Session 切换关闭并清理材料 | 控制器契约覆盖 | NOT RUN |
| 二维码切换配对码、扫描中、找到端口、输入、超时、重试 | Feature 状态机有自动化覆盖 | NOT VERIFIED |
| 中英文文案、遮罩透明度、卡片尺寸和焦点顺序 | 无实际渲染/可访问性采集 | NOT RUN |

证据中不保存二维码载荷、配对码、真实端点或设备标识。

## 3. 连接页全状态

| 状态 | 静态/自动化覆盖 | 紧凑/宽屏实际渲染 |
|---|---|---|
| loading | 未连接页语义状态存在 | NOT VERIFIED |
| content | 发现结果内容状态存在 | NOT VERIFIED |
| empty | 空发现结果状态存在 | NOT VERIFIED |
| error | 错误状态及可关闭错误框存在 | NOT VERIFIED |
| cancelled | 取消状态存在 | NOT VERIFIED |
| disconnected | 断开状态存在 | NOT VERIFIED |
| unsupported | 不支持状态存在 | NOT VERIFIED |
| connecting | 连接生命周期映射存在 | NOT VERIFIED |
| connected | 已连接生命周期映射存在 | NOT VERIFIED |
| disconnecting | 断开中生命周期映射存在 | NOT VERIFIED |

尚未逐状态采集中英文截图，因此内容层级、文本展开、焦点、触控区、紧凑/宽屏重排和实际状态转换反馈均未作视觉通过声明。

## 4. 截屏反馈的授权差异

已连接设计 HTML 提供独立的“截屏”和“录屏”入口。本版本规格对截屏完成后的反馈作了明确覆盖：

| 阶段 | 授权行为 | 自动化结论 | 实际视觉结论 |
|---|---|---|---|
| 正在截屏 | 可显示截屏进行中语义，不冒充录屏 | 文案与状态契约覆盖 | NOT VERIFIED |
| 截屏产物就绪、尚未保存 | 不显示“截屏成功”提示，不显示任何录屏提示；只显示“保存截屏”按钮 | 待保存分支契约覆盖 | NOT VERIFIED |
| 打开/浏览/取消保存位置选择器 | 保持 picker 阶段语义；不把选择器当作写入成功 | 状态契约覆盖 | NOT RUN |
| 实际持久化写入 | 显示保存写入状态 | 状态与文案契约覆盖 | NOT VERIFIED |
| 保存终态 | 只在实际保存完成后显示保存结果 | 状态与文案契约覆盖 | NOT VERIFIED |
| 录屏 | 保留独立录屏流程，不复用截屏反馈 | 类型与文案契约覆盖 | NOT VERIFIED |

这里的差异是规格对设计初稿遗漏状态的授权补充，不据此改变两枚快捷操作入口的设计层级。

## 待补证据

1. 使用脱敏发现/连接夹具，在紧凑和宽屏尺寸采集中英文的全部状态截图。
2. 实际操作错误关闭，确认视觉消失且输入、发现结果和 Session 呈现保持不变。
3. 实际操作根配对浮层的卡内点击、外部点击、系统返回与后台切换，并记录无敏感内容的结论。
4. 实际完成一次截屏，留存“仅显示保存按钮”的截图，再分别记录 picker、写入与保存终态。

在完成上述截图和交互记录前，T130 的静态/自动化契约可复现，但 scoped 视觉验收仍为 **NOT RUN / NOT VERIFIED**。
# 2026-07-26 模拟器补充验收

- 连接页既有顶部栏作为全局唯一实现复用于文件、应用、进程、Shell 与 Logcat 页面。
- 每个已连接功能页均显示菜单、当前 ADB 端点和断开连接动作；不再回退为通用标题栏。
- 连接页错误提示可关闭；截屏成功路径不再显示错误的“录屏”提示。
- QR、配对码及本机配对端口扫描依赖真机，按用户指示跳过，不以模拟器结果替代。
