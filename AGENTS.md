# Sheen ADB 助手：仓库工作约束

开始任何实现前，必须阅读：

1. [`docs/v0.01-需求文档.md`](docs/v0.01-需求文档.md)
2. [`docs/生命周期与AI协作规范.md`](docs/生命周期与AI协作规范.md)

硬性规则：

- Kotlin + Compose；`minSdk 30`，`targetSdk/compileSdk 36`。
- 保持纯本地：禁止账号、后端、广告、统计/遥测 SDK、支付和 Root/无障碍自动化。
- ADB Socket、TLS、配对、密钥和原始命令只能位于 `core:adb`；UI 和 Feature 禁止直接访问。
- 一次只允许一个活跃 ADB Session；所有 ADB 操作必须支持取消、超时、结构化错误和资源清理。
- 不记录/提交 ADB 私钥、配对码、真实 IP、Shell 输出、Logcat 或签名密钥。
- 禁止扩大权限；所有 Manifest 权限必须先登记到 `docs/权限矩阵.md` 并符合当前需求。
- 新依赖限 Apache-2.0、MIT、BSD-2-Clause、BSD-3-Clause，且需记录用途与许可证。
- 修改前先说明需求对应项、模块影响和验证方式；只修改任务相关文件。

需求、权限、协议或架构边界不明确时，暂停实现并先征求确认或新增 ADR。
