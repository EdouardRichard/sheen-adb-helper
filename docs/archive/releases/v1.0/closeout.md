# v1.0 阶段收口

> 日期：2026-07-26  
> 本文件区分自动化、模拟器、真机与发布门禁；不得把其中一种证据解释为另一种证据。

## 门禁汇总

| 门禁 | 状态 | 证据 |
|---|---|---|
| 设计源基线 | PASS | [`design-baseline.md`](design-baseline.md) |
| 版本与架构合同 | PASS | `versionName=1.0`、`versionCode=4`；[`automated-verification.md`](automated-verification.md) |
| 全量自动化 | PASS | 754 tests、0 failures、0 errors、0 skipped |
| Debug 组装 | PASS | `:app:assembleDebug --rerun-tasks` 退出码 0 |
| 本机模拟器全功能流程 | PASS WITH EXCLUSIONS | [`real-device-acceptance.md`](real-device-acceptance.md) |
| 紧凑中文 UI | PASS WITH OPEN VARIANTS | [`visual/`](visual/) |
| 双语目录自动化 | PASS | [`localization-acceptance.md`](localization-acceptance.md) |
| English 运行时、宽屏、TalkBack | OPEN | 尚未执行完整运行时矩阵 |
| 真机 QR/配对码/本机配对 | SKIPPED / OPEN | 按用户指示跳过；仍需真机 |
| 三机真机矩阵 | NOT RUN / OPEN | [`real-device-acceptance.md`](real-device-acceptance.md) |
| 权限与纯本地架构 | PASS（静态/自动化范围） | [`release-gates.md`](release-gates.md) |
| 许可证与对外发布 | BLOCKED / NOT RELEASABLE | [`release-gates.md`](release-gates.md)、ADR 0004 |

## 本轮运行时结论

- 文件、应用、进程、Shell、Logcat 均复用连接页统一顶部栏。
- 文件传输、APK 提取/安装、进程采样/结束、Shell 命令与 Logcat 采集/保存完成本机双端闭环。
- 长任务禁止切页；SAF 选择器不误锁页；取消任务保留 Session。
- 进程和应用结果均有明确成功、拒绝或未知语义，不用“对话框关闭”替代后端验证。
- Shell/Logcat 离页后子流释放，返回 Shell 建立新会话。
- 本轮生成的测试文件和测试安装包已按精确路径清理。

## 开放项

- QR、配对码、本机主动配对端口扫描及厂商 ROM 行为必须由真机补证。
- English、宽屏、TalkBack、全部异常状态的实际渲染矩阵仍开放。
- 性能 P95、真机帧时间和真机内存没有完成发布级测量。
- API 37 的 16 KB 页大小模拟器报告现有 native 库 RELRO/对齐不兼容，当前以兼容模式运行；需在后续依赖审查中解决。
- `spake2-java:1.0.5` 的 GPL-3.0-or-later 许可证冲突继续阻止对外发布。

## 收口判断

v1.0 的批准任务、全量自动化、Debug 组装及用户授权范围内的本机模拟器功能流程已经完成；真机配对、扩展视觉矩阵和发布合规仍是独立开放门禁。因此当前可交付 Debug 验证包，但不得描述为“已满足对外发布条件”。
