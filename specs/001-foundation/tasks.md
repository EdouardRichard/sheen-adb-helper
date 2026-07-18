# Tasks: Wireless ADB Foundation

> **Status**: `archived-retrospective`
> **Important**: 本清单由最终代码、测试和 Git 历史重建，仅用于审计。勾选表示最终仓库中存在对应实现，不表示开发前曾按此清单审批或执行。

## Format

`[ID] [P?] [Story] Description with repository path`

- `[P]`：最终结构中可与同阶段其他任务独立验证。
- `[US1]`、`[US2]`、`[US3]`：对应 [spec.md](spec.md) 的用户故事。

## Phase 1: Shared Foundation

- [x] T001 建立多模块装配与单向依赖边界，覆盖 `settings.gradle.kts`、`app/`、`core/` 与 `feature/`
- [x] T002 在 `core/adb/src/` 建立项目自有 ADB 模型、结构化错误、单 Session 管理与脱敏诊断契约
- [x] T003 [P] 在 `core/data/src/` 建立版本化设备档案、DataStore、SAF 文本输出和清理契约
- [x] T004 [P] 在 `core/ui/src/` 建立 Compose 主题、设计令牌和通用 UI 基线

## Phase 2: User Story 1 - Connect or Pair a Device (P1)

**Independent Test**: 端点解析、已配对直连、认证失败配对回退、取消、超时、断开与 Session 切换均可独立验证。

- [x] T005 [US1] 在 `core/adb/src/` 实现 IPv4、主机名、方括号 IPv6、环回与调试/配对端口边界
- [x] T006 [US1] 在 `core/adb/src/` 实现 Kadb/TLS/配对适配、Keystore 保护的主机身份和候选连接清理
- [x] T007 [US1] 在 `feature/devices/src/` 实现连接、配对、取消、最近设备和诊断状态
- [x] T008 [US1] 在 `app/src/` 装配唯一 `AdbSessionManager`、全局连接状态和单导航宿主

## Phase 3: User Story 2 - Inspect and Debug the Device (P2)

**Independent Test**: 概览、Shell、只读进程和 Logcat 可在一个已连接 Session 中分别完成加载、取消、错误、断开与容量验证。

- [x] T009 [P] [US2] 在 `core/adb/src/` 与 `feature/overview/src/` 实现可降级概览和前台动态刷新
- [x] T010 [P] [US2] 在 `core/adb/src/` 与 `feature/shell/src/` 实现原始 Shell、取消、超时、结构化输出和 1 MiB 有界转录
- [x] T011 [P] [US2] 在 `core/adb/src/` 与 `feature/processes/src/` 实现 ROM 兼容的只读进程快照、搜索和复制
- [x] T012 [P] [US2] 在 `core/adb/src/` 与 `feature/logcat/src/` 实现前台 Logcat、过滤、暂停、停止和 10,000 行/4 MiB 有界缓冲

## Phase 4: User Story 3 - Manage Local Profiles and Privacy (P3)

**Independent Test**: 档案增删改、身份引用清理、SAF 导出和清除全部数据可不依赖调试页面单独验证。

- [x] T013 [US3] 在 `core/data/src/` 与 `feature/devices/src/` 实现档案创建、更新、排序、重命名、删除和重连
- [x] T014 [P] [US3] 在 `feature/settings/src/` 实现隐私/许可证、系统设置帮助和清除全部本地数据
- [x] T015 [P] [US3] 在 `feature/logcat/src/` 与 `core/data/src/` 实现用户主动 SAF 导出且不缓存副本

## Phase 5: Verification and Historical Closeout

- [x] T016 补齐 `core/*/src/test/`、`feature/*/src/test/` 与 `app/src/test/` 的核心逻辑/状态测试
- [x] T017 对照 `docs/权限矩阵.md`、`docs/第三方依赖审查.md` 与 merged Manifest 检查权限、依赖和敏感材料边界
- [x] T018 记录历史自动化、真机总体确认、证据缺口和许可证阻断到 `docs/archive/releases/v0.01/`

## Historical Evidence

主要实现提交：`c4a1527`；后续应用/解析修订：`f93e9f8`、`c4ff8c5`；归档事实提交：`2f28db1`。本清单不推断未记录的评审或执行顺序。
