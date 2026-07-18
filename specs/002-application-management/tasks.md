# Tasks: Application Management

> **Status**: `archived-retrospective`
> **Important**: 本清单由最终代码、测试和 Git 历史重建，仅用于审计。勾选表示最终仓库中存在对应实现，不表示开发前曾按此清单审批或执行。

## Phase 1: Core Application Snapshot Foundation

- [x] T001 [US1] 在 `core/adb/src/` 增加当前用户、应用条目、快照、降级字段和结构化错误模型
- [x] T002 [US1] 在 `core/adb/src/` 实现当前用户第三方包查询、保守解析、20,000 项上限和 Session 快照绑定
- [x] T003 [P] [US1] 在 `core/adb/src/test/` 覆盖当前用户、空/畸形/超限输出、包属性与 Session 归属

## Phase 2: User Story 1 - Browse Current-user Third-party Apps (P1)

**Independent Test**: 加载、包名搜索、启用状态筛选、刷新、空状态、降级和断开清理可独立验证。

- [x] T004 [US1] 在 `feature/apps/src/` 建立不可变 `AppsUiState`、ViewModel 与一次加载/手动刷新策略
- [x] T005 [US1] 在 `feature/apps/src/` 实现包名主标题、可选字段、内存搜索/筛选和错误/降级界面
- [x] T006 [P] [US1] 在 `feature/apps/src/test/` 覆盖搜索、筛选、刷新、Session 切换和离开页面取消

## Phase 3: User Story 2 - Force Stop an Allowed App (P2)

**Independent Test**: 合法包、系统包、自身、列表外目标、过期 Session、策略拒绝和结果未知路径可独立验证。

- [x] T007 [US2] 在 `core/adb/src/` 实现目标快照复核、包名校验、单包强停和“请求已接受”语义
- [x] T008 [US2] 在 `feature/apps/src/` 实现每次一次性风险确认、执行中禁用和结构化结果展示
- [x] T009 [P] [US2] 在 `core/adb/src/test/` 与 `feature/apps/src/test/` 覆盖允许/拒绝/取消/超时/掉线/自身保护

## Phase 4: User Story 3 - Disable or Re-enable an Allowed App (P3)

**Independent Test**: 一个测试包的禁用、后置查询、重新启用和不一致/结果未知路径可端到端验证。

- [x] T010 [US3] 在 `core/adb/src/` 实现包级 enabled state 修改、前置复核和后置验证
- [x] T011 [US3] 在 `feature/apps/src/` 实现禁用/重新启用确认、风险文本和复核提示
- [x] T012 [P] [US3] 在 `core/adb/src/test/` 与 `feature/apps/src/test/` 覆盖成功、包消失、验证不符、超时和 Session 切换

## Phase 5: Integration and Regression

- [x] T013 在 `app/src/` 装配 Apps ViewModel、导航目的地、离线/断开策略和菜单顺序
- [x] T014 [P] 回归 `core/adb/src/test/` 与 `feature/shell/src/test/` 的命令子流所有权和错误映射
- [x] T015 [P] 回归 `core/adb/src/test/` 与 `feature/logcat/src/test/` 的持续流、内存窗口和生命周期
- [x] T016 检查 Manifest、依赖、持久化和诊断边界，并记录验证与结项到 `docs/archive/releases/v0.02/`

## Historical Evidence

主要代码/测试提交：`0a4d563`；结项材料修订：`82d7891`。许可证发布阻断保持独立，不因任务完成而解除。
