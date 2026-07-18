# Feature Specification: Application Management

> **Feature Directory**: `002-application-management`
> **Status**: `archived-retrospective`
> **Migrated**: 2026-07-19
> 产品里程碑：v0.02；Android `versionName 0.0.2`、`versionCode 2`
> 来源：[v0.02 原合并需求/SDD](../../docs/archive/releases/v0.02/需求文档.md) 的需求、范围与验收部分。

## User Scenarios & Testing (mandatory)

### User Story 1 - Browse Current-user Third-party Apps (Priority: P1)

已连接用户可加载被控端当前 Android 用户的第三方应用包列表，按包名搜索并按启用状态筛选，无法可靠读取的字段明确降级。

**Why this priority**: 安全快照是所有后续修改的前提，也独立提供设备盘点价值。

**Independent Test**: 仅加载、搜索、筛选、刷新和断开即可完整验收。

**Acceptance Scenarios**:

1. **Given** 已连接 Session，**When** 用户进入应用管理，**Then** 只显示当前用户第三方包并区分空列表与解析失败。
2. **Given** 已加载快照，**When** 用户搜索或筛选，**Then** 在内存中完成且不发起额外 ADB 查询。

### User Story 2 - Force Stop an Allowed App (Priority: P2)

用户可对同 Session 安全快照中的单个第三方包发起强制停止，并在每次操作前看到风险确认。

**Why this priority**: 提供常见故障处置能力，同时保持单包、可确认的风险边界。

**Independent Test**: 可用一个允许目标与系统包/自身/过期目标分别验证接受和拒绝路径。

**Acceptance Scenarios**:

1. **Given** 合法目标和有效快照，**When** 用户确认强停，**Then** 系统只表达请求已发送，不承诺永久停止。
2. **Given** 系统包、自身或过期 Session 目标，**When** 发起操作，**Then** 在核心边界拒绝。

### User Story 3 - Disable or Re-enable an Allowed App (Priority: P3)

用户可对单个允许目标禁用或重新启用；每次确认后重新查询状态，只有结果符合预期才显示成功。

**Why this priority**: 提供可恢复的应用状态控制，并用后置验证降低误报风险。

**Independent Test**: 对一个测试包执行禁用、刷新、重新启用即可端到端验收。

**Acceptance Scenarios**:

1. **Given** 合法目标，**When** 用户确认状态修改，**Then** 后置查询一致才报告已验证成功。
2. **Given** 命令后超时、取消或掉线，**When** 无法确认最终状态，**Then** 返回结果未知并要求刷新复核。

### Edge Cases

- 当前用户、系统/第三方分类或 enabled 状态无法可靠判断时，必须降级或拒绝修改。
- 包在确认后被卸载、Session 切换或快照过期时，旧确认不得继续执行。
- 列表超过 20,000 项、输出畸形或解析失败时不得生成半真列表。

## Requirements (mandatory)

### Functional Requirements

| ID | 最终确认需求 |
|---|---|
| FR-LIST-001 | 只查询当前 Android 用户，不回退到全部用户。 |
| FR-LIST-002 | 唯一管理范围为当前用户第三方应用。 |
| FR-LIST-003 | 每项包含稳定包名和启用状态。 |
| FR-LIST-004 | 版本号、版本名和安装器仅在可靠可得时展示。 |
| FR-LIST-005 | 不承诺应用名/图标，不从包名推测。 |
| FR-LIST-006 | 支持包名大小写不敏感本地搜索。 |
| FR-LIST-007 | 支持全部/已启用/已禁用内存筛选，不额外查询。 |
| FR-LIST-008 | 首次进入加载一次，可手动刷新，不轮询。 |
| FR-LIST-009 | 区分没有第三方应用与输出无法解析。 |
| FR-LIST-010 | 列表仅内存；断开、Session 变化或进程结束后清除。 |
| FR-STOP-001 | 只能从同 Session 已加载第三方应用项发起强停。 |
| FR-STOP-002 | 每次强停均二次确认，不提供跳过提示。 |
| FR-STOP-003 | 确认展示包名及前台任务、下载、通知、未保存工作风险。 |
| FR-STOP-004 | 本机 Session 禁止强停 Sheen 自身。 |
| FR-STOP-005 | 只表达强停请求已发送，不承诺永久停止。 |
| FR-STOP-006 | 区分策略拒绝、包不存在和 Session 失效。 |
| FR-STATE-001 | 只允许当前用户第三方应用的包级禁用/启用。 |
| FR-STATE-002 | 每次禁用/启用均二次确认。 |
| FR-STATE-003 | 禁用确认说明入口、通知、后台任务和关联功能风险。 |
| FR-STATE-004 | “重新启用”只修改 enabled state，不承诺恢复运行或数据。 |
| FR-STATE-005 | 操作前复核 Session、当前用户和第三方范围。 |
| FR-STATE-006 | 操作后重新查询；状态符合预期才成功。 |
| FR-STATE-007 | 本机 Session 禁止禁用 Sheen 自身。 |
| FR-STATE-008 | 不保存禁用归属；允许重新启用范围内已禁用第三方应用。 |
| FR-OPS-001 | 单活跃 Session，且同 Session 同时一个应用管理高层操作。 |
| FR-OPS-002 | 加载/修改时禁用其他入口并提供取消。 |
| FR-OPS-003 | 列表、强停、状态修改默认超时分别为 15/10/15 秒。 |
| FR-OPS-004 | 取消、超时和异常必须清理流、命令和临时对象。 |
| FR-OPS-005 | 命令发出后取消/超时必须标为结果未知并引导复查。 |
| FR-OPS-006 | 离开页面取消读取，旧操作结果不得写回离开/新 Session 状态。 |

### Non-functional Requirements

| ID | 最终确认要求 |
|---|---|
| NFR-SEC-001 | 包列表、包名和状态按敏感设备数据处理，不落盘/上传/日志。 |
| NFR-SEC-002 | 诊断不含包名、命令/输出、用户 ID 或安装路径。 |
| NFR-SEC-003 | 参数不得接受自由文本；目标来自快照并复核包名语法。 |
| NFR-SEC-004 | 不加入系统包开关、隐藏高级模式或跳过确认后门。 |
| NFR-SEC-005 | 不新增应用管理持久化或清理格式。 |
| NFR-REL-001 | 结果携带 Session ID，ViewModel 丢弃不匹配结果。 |
| NFR-REL-002 | 解析失败不崩溃、不产生半真列表。 |
| NFR-REL-003 | 修改采用前置校验、命名操作、后置验证。 |
| NFR-REL-004 | 系统应用识别不可靠时默认拒绝修改。 |
| NFR-REL-005 | 失败后可重试；核心关闭 Session 时 UI 明确断开。 |
| NFR-PERF-001 | 列表在 IO dispatcher 执行，不阻塞主线程。 |
| NFR-PERF-002 | 列表上限 20,000 项，超限返回结构化错误。 |
| NFR-PERF-003 | 搜索筛选在快照完成，不发起多余查询。 |
| NFR-UI-001 | 简体中文优先并支持主题和字体缩放。 |
| NFR-UI-002 | 状态、风险与成功不只使用颜色表达。 |
| NFR-UI-003 | 危险动作可读，确认框焦点顺序正确。 |
| NFR-UI-004 | 适配手机竖/横屏和平板既有布局策略。 |

## Out of Scope

- APK 安装/更新/降级、卸载、split/APKS/XAPK/AAB、应用名/图标提取。
- 系统/预装应用、跨用户、工作资料显式管理、批量/定时/后台、清数据/权限/默认应用修改。
- 操作归属恢复账本、Root、Shizuku、无障碍、设备管理员、包可见性或主控端应用列表。

## Success Criteria (mandatory)

### Measurable Outcomes

| ID | 目标 |
|---|---|
| AC-01 | 当前用户第三方应用列表、搜索筛选、空状态和刷新成立。 |
| AC-02 | 不可靠的名称、图标及其他字段明确降级，不伪造。 |
| AC-03 | 强停限单包、每次确认，并拒绝系统/自身/范围外目标。 |
| AC-04 | 禁用/启用后重新读取状态，不一致时不误报成功。 |
| AC-05 | 超时、取消、掉线和结果未知被结构化表达，并要求刷新确认。 |
| AC-06 | Session 切换后旧快照、确认、结果和流不污染新 Session。 |
| AC-07 | 应用数据与操作结果仅内存且不进入诊断内容。 |
| AC-08 | 不新增 Manifest 权限、组件或导出面。 |
| AC-09 | 不新增外部依赖，模块与 ADB/命令边界保持正确。 |
| AC-10 | 自动化、构建、应用管理真机矩阵及 v0.01 Shell/Logcat 回归达到验收目标。 |

历史验证证据见 [verification](../../docs/archive/releases/v0.02/verification.md)，最终结论见 [closeout](../../docs/archive/releases/v0.02/closeout.md)。
