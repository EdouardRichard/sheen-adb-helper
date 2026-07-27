# v1.0 双语验收

> 执行日期：2026-07-26  
> 范围：简体中文、English、语言状态隔离、紧凑/宽屏呈现。

## 结论

状态：**PARTIAL / OPEN**

自动化证明各模块的类型化文案目录具有中英键集合与参数模式一致性，App 根节点向六个页面分发同一 `UiLanguage`，语言切换不会改变 Session、页面 ViewModel、任务标识、流 generation 或导航锁。全量强制单测通过。

实际 Compose 紧凑/宽屏逐页查看、TalkBack content description 朗读以及运行中人工切换没有可用设备或渲染会话，均保持 `NOT RUN`。因此不能宣称“可见文本/content description 运行时覆盖 100%”。

## 验收矩阵

| 项目 | 自动化/静态结果 | 运行时结果 |
|---|---|---|
| 简中与 English 键完整性、参数模式一致 | PASS | NOT RUN |
| 六页接收同一根语言状态 | PASS | NOT RUN |
| 技术码、路径、包名、进程名、Shell/Logcat 原文不翻译 | PASS（类型与安全原文合同） | NOT RUN |
| 语言切换不重建 Session/ViewModel/任务/流 | PASS（状态隔离测试） | NOT RUN |
| 运行中切换后请求计数不增加 | PASS（假网关/状态合同） | NOT RUN |
| 紧凑/宽屏可见文案与 content description | 静态目录完整 | NOT RUN |

## 证据

- `test --rerun-tasks`：退出码 0。
- 相关测试：`LanguageWiringContractTest`、`LanguageStateIsolationTest`、`AppStringsTest`、`V1LocalizationPrimitivesTest` 及各 Feature `*StringsTest`。
- 页面级视觉证据见 [`visual/`](visual/)；所有缺少实际渲染的项目均保留 `NOT VERIFIED`。

## 开放项

在紧凑与宽屏宿主上逐页切换中英语言，覆盖空、错误、确认、进度和长任务状态；同时检查无中文回退、技术值逐字保持及 content description。完成前本门禁不得改为 PASS。
