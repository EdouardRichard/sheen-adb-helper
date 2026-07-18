# Sheen ADB 助手：GitHub Spec Kit 工作流

> 状态：生效
> 基准：GitHub Spec Kit v0.13.0，Codex skills 集成，PowerShell 脚本
> 边界：本文件说明仓库如何使用 Spec Kit；工程硬约束以 [constitution](../.specify/memory/constitution.md) 为准。

## 1. 官方目录与工件

```text
.agents/skills/                 # Codex 的 $speckit-* 工作流技能
.specify/
├── memory/constitution.md      # 项目宪法
├── scripts/powershell/         # Spec Kit 自动化脚本
├── templates/                  # spec/plan/tasks/checklist 模板
├── integrations/               # Codex 与工作流安装清单
└── workflows/                  # 已安装的 speckit 工作流
specs/
└── NNN-feature-name/
    ├── spec.md                 # Specify 输出
    ├── plan.md                 # Plan 输出
    ├── research.md             # 可选：Phase 0 研究结论
    ├── data-model.md           # 可选：涉及数据模型时
    ├── contracts/              # 可选：涉及接口契约时
    ├── quickstart.md           # 可选：集成/验收说明
    ├── checklists/             # 可选：需求质量检查表
    └── tasks.md                # Tasks 输出
```

`.sdd/` 与全局 `domains/` 不是 GitHub Spec Kit 标准目录。本仓库需要长期维护的当前架构事实放在 [`architecture/`](architecture/)；它们是项目参考资料，不是 Spec Kit 自动生成工件。

## 2. 标准流程

1. `$speckit-constitution`：仅在全局原则确需修订时运行，并同步模板影响。
2. `$speckit-specify`：创建活动功能目录、`.specify/feature.json` 和 `spec.md`。
3. `$speckit-clarify`（按需）：处理高影响歧义；未澄清的协议、权限或架构边界不得进入对应设计。
4. `$speckit-plan`：生成 `plan.md`，并按需要生成 research、data-model、contracts、quickstart。
5. `$speckit-tasks`：根据已批准的 spec/plan 生成可执行 `tasks.md`。
6. `$speckit-analyze`、`$speckit-checklist`（按需）：检查跨工件一致性和需求质量。
7. `$speckit-implement`：只实现已批准 tasks；完成后可用 `$speckit-converge` 检查剩余工作。

活动功能由 `.specify/feature.json` 记录，可由 `SPECIFY_FEATURE_DIRECTORY` 覆盖；不得把 Git 分支或最高目录编号当作唯一来源。当前没有活动功能时，不创建空 `003-*` 目录或伪造 feature state。

## 3. 本仓库的附加门禁

- Plan 未经项目负责人确认，不生成 Tasks；Tasks 未确认，不进入 Implement。
- 自动化、构建、真机验收与发布合规分别给出结论，不能互相替代。
- 历史真机验证与结项材料存放在 [`archive/releases/`](archive/releases/)；这是项目交付扩展，不是 Spec Kit 核心阶段或标准文件名。
- ADR 继续位于 [`adr/`](adr/)；仅在当前设计触及相应决策时读取。
- 当前架构事实位于 [`architecture/`](architecture/)；Plan 按任务引用必要文件，禁止默认加载全部历史规格。

## 4. 历史迁移边界

`specs/001-foundation/` 与 `specs/002-application-management/` 是从已完成版本回顾性整理的 Spec Kit 兼容档案。其 `plan.md` 和 `tasks.md` 永久保留 retrospective 声明，不代表当年曾按 Spec Kit 逐项审批或执行。原始需求、真机清单、实现记录和结项证据保存在 `docs/archive/releases/`，不得为了填满模板而补造历史事实。
