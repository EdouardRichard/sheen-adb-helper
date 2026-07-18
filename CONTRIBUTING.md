# 贡献指南

提交改动前先阅读 `AGENTS.md`，再按其最小上下文顺序读取工程宪法、`.specify/feature.json` 指向的活动功能和任务实际涉及的架构/ADR。不要默认加载 `specs/001-*`、`specs/002-*` 或 `docs/archive/`。只实现当前规格范围，保持 app/feature → core 依赖方向，ADB 协议与命令必须留在 `:core:adb`。

依赖许可证仅接受 Apache-2.0、MIT、BSD-2-Clause、BSD-3-Clause。不得提交密钥、真实设备地址或输出。至少运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

协议、权限、持久化或模块边界变化须先更新 ADR 与相关文档。真机结果必须注明设备/系统范围，未验证项不得写成通过。
