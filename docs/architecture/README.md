# 当前架构事实

本目录描述 HEAD 已经成立的能力与边界，供当前功能 Plan 按需引用。它是项目文档，不是 GitHub Spec Kit 的标准生成目录，也不得替代 `spec.md`、`plan.md` 或 ADR。

- [`adb-session.md`](adb-session.md)：协议适配、单 Session、身份和诊断边界。
- [`application-management.md`](application-management.md)：当前用户第三方应用管理能力与限制。
- [`device-and-data.md`](device-and-data.md)：设备档案、DataStore、概览、SAF 与清理。
- [`diagnostics.md`](diagnostics.md)：Shell、只读进程和 Logcat 当前行为。
- [`security-and-delivery.md`](security-and-delivery.md)：权限、依赖、分发和当前合规阻断。

仅在实现合入并有代码/测试证据后更新本目录；未来需求、未批准方案和 proposed ADR 不得写成当前事实。
