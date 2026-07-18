<!--
Sync Impact Report
- Version: template -> 1.0.0
- Added principles: local-first and least privilege; ADB boundary and session ownership;
  sensitive-data discipline; dependency and distribution safety; verifiable delivery
- Added sections: Engineering Baseline; Spec Kit Workflow; Governance
- Templates reviewed: plan-template.md, spec-template.md, tasks-template.md, checklist-template.md
- Follow-up: none
-->

# Sheen ADB 助手 Constitution

## Core Principles

### I. Pure Local and Least Privilege

产品 MUST 保持纯本地：不得引入账号、后端、广告、统计/遥测、支付、远程配置或业务云请求。不得使用 Root、Shizuku、设备管理员或无障碍自动化绕过 ADB/系统授权。Manifest 权限 MUST 先登记到 `docs/权限矩阵.md`，由当前规格授权，并采用最小权限与可降级方案。未经明确渠道需求，不得为应用商店预埋权限、SDK 或渠道逻辑。

### II. ADB Boundary and Single Session

ADB Socket、TLS、配对、主机身份、密钥、协议流、原始命令、第三方 ADB 类型与异常适配 MUST 只位于 `:core:adb`。UI、ViewModel、Feature 与 `:core:data` MUST 只依赖项目自有接口、值对象、状态和结构化错误，不得直接建立 Socket 或拼接业务 Shell 命令。进程内 MUST 只有一个 `AdbSessionManager` 和一个活跃 Session；所有操作 MUST 绑定 Session ID，并支持取消、有限超时、结构化错误和确定性资源清理。

### III. Sensitive Data by Default

ADB 私钥、证书和配对码为高敏感数据，只能短暂驻留内存或由 Android Keystore 保护，绝不得打印、导出、提交或上传。真实端点、设备标识、包名上下文、Shell 输出和 Logcat 为敏感数据，默认只在内存中；只有用户明确复制或通过 SAF 导出时才可离开。诊断 MUST 有界、进程内、字段白名单并脱敏。仓库、测试、Issue 和构建日志不得包含真实设备或签名材料。

### IV. Controlled Dependencies and Distribution

新增依赖 MUST 限于 Apache-2.0、MIT、BSD-2-Clause 或 BSD-3-Clause，并记录用途、版本、许可证、维护状态、替代方案和移除路径。禁止未经审查的二进制、动态代码、广告/分析/支付/云端 SDK。功能完成、真机通过和对外发布合规 MUST 分别判断；构建或真机成功不得覆盖许可证或安全阻断。默认分发为公开源码、自行构建和用户自主安装；应用商店不是默认目标。

### V. Verifiable, Scoped Delivery

每次修改 MUST 对应已确认规格或任务，只改相关文件并保护已有工作。纯逻辑变更 MUST 覆盖正常、边界、取消和错误；UI 状态 MUST 覆盖加载、内容、空、错误、断开/取消；ADB 变更 MUST 覆盖 Session 归属、超时、取消、错误映射与资源关闭。ROM 不支持、策略拒绝和结果未知 MUST 明确表达，不得伪装成功。自动化、构建、真机和证据缺口 MUST 分别报告。

## Engineering Baseline

- 业务代码使用 Kotlin；UI 使用 Jetpack Compose + Material 3；异步使用 Coroutines + Flow。
- `minSdk 30`，`compileSdk 36`，`targetSdk 36`；技术基线变更 MUST 先有 accepted ADR。
- 依赖方向只能为 `:app`/`:feature:*` → `:core:*`；Feature 间不得直接依赖；`:app` 仅负责装配和导航。
- 数据流遵循 UI Event → ViewModel → Use Case/Repository → immutable UI State；不得阻塞主线程或使用 `GlobalScope`。
- 本地持久化仅限必要元数据与偏好；用户选择的文件输出使用 SAF；清除全部本地数据必须覆盖档案、身份引用、偏好和临时数据。

## Spec Kit Workflow

项目采用 GitHub Spec Kit 的核心流程：Specify → Plan → Tasks → Implement。Clarify、Checklist、Analyze 和 Converge 按需使用。活动功能由 `.specify/feature.json` 指向的 `specs/NNN-feature-name/` 决定；不得仅凭 Git 分支或最高编号猜测。

- `spec.md` MUST 描述用户场景、可独立验证的验收场景、功能需求、边界和可衡量成功标准，不写实现方案。
- `plan.md` MUST 完成 Constitution Check，并记录技术上下文、真实项目结构、关键设计和待澄清项；不明确的协议、权限、数据或架构边界必须暂停对应设计。
- `tasks.md` MUST 使用可执行复选项、任务 ID、用户故事标记和明确文件路径；未获项目负责人批准不得进入 Implement。
- `specs/001-*`、`specs/002-*` 是回顾性历史功能档案，默认不作为新功能上下文；只有历史审计、回归调查或迁移才读取。
- 真机证据与发布结项是本仓库的交付扩展，存放在 `docs/archive/releases/`，不冒充 Spec Kit 核心工件或阶段。

## Governance

本宪法优先于仓库内其他工程惯例。修订 MUST 说明原因、影响与迁移方式，更新本文件版本，并同步受影响模板、指引、权限矩阵或 ADR。破坏既有原则或技术基线的变更需要项目负责人明确批准及必要 ADR。

版本采用语义化规则：新增或实质扩展原则为 MINOR，不兼容移除/重定义为 MAJOR，澄清措辞为 PATCH。每个 Plan 在设计前和设计后都 MUST 重新执行 Constitution Check；无法满足时必须在 Complexity Tracking 中给出理由与被拒绝的更简单方案。

**Version**: 1.0.0 | **Ratified**: 2026-07-19 | **Last Amended**: 2026-07-19
