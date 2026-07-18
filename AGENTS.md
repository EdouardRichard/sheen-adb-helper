# Sheen ADB 助手：仓库工作约束

开始任何任务前，按阶段读取最小上下文：

1. [`.specify/memory/constitution.md`](.specify/memory/constitution.md)；
2. 活跃功能由 `.specify/feature.json` 指向；存在时先读其 `spec.md`；
3. 进入 Plan 后读取当前 `plan.md` 及其引用的相关 [`docs/architecture/`](docs/architecture/) 与 ADR；
4. 进入 Implement 后读取已批准的当前 `tasks.md`；
5. 权限、依赖或发布事项仅按需读取相应矩阵、审查和 ADR。

不要用最高编号或当前 Git 分支猜测活跃功能。历史功能 `specs/001-*`、`specs/002-*` 与 [`docs/archive/`](docs/archive/) 默认不得加载；仅在明确进行历史审计、回归调查或迁移时读取。流程说明见 [`docs/Spec-Kit工作流.md`](docs/Spec-Kit工作流.md)。

以下硬性规则是工程宪法的仓库执行摘要，不构成第二套权威来源，也不得用于放宽宪法：

- Kotlin + Compose；`minSdk 30`，`targetSdk/compileSdk 36`。
- 保持纯本地：禁止账号、后端、广告、统计/遥测 SDK、支付和 Root/无障碍自动化。
- ADB Socket、TLS、配对、密钥和原始命令只能位于 `core:adb`；UI 和 Feature 禁止直接访问。
- 一次只允许一个活跃 ADB Session；所有 ADB 操作必须支持取消、超时、结构化错误和资源清理。
- 不记录/提交 ADB 私钥、配对码、真实 IP、包名上下文、Shell 输出、Logcat 或签名密钥。
- 禁止扩大权限；所有 Manifest 权限必须先登记到 [`docs/权限矩阵.md`](docs/权限矩阵.md) 并符合当前规格。
- 新依赖限 Apache-2.0、MIT、BSD-2-Clause、BSD-3-Clause，且需记录用途与许可证。
- 修改前先说明需求对应项、模块影响和验证方式；只修改任务相关文件。

需求、权限、协议或架构边界不明确时，暂停对应设计或实现并先征求确认或新增 ADR。
