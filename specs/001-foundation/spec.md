# Feature Specification: Wireless ADB Foundation

> **Feature Directory**: `001-foundation`
> **Status**: `archived-retrospective`
> **Migrated**: 2026-07-19
> 产品里程碑：v0.01；Android `versionName 0.0.1`、`versionCode 1`
> 来源：[v0.01 原需求](../../docs/archive/releases/v0.01/需求文档.md)。本文件在迁移时提炼；`V1-*` 为归档追踪 ID，不声称是原文既有编号。

## User Scenarios & Testing (mandatory)

### User Story 1 - Connect or Pair a Device (Priority: P1)

用户输入远端或本机端点，优先直接连接；仅在认证/未配对失败时使用 6 位配对码，并能取消、重试或断开。

**Why this priority**: 没有安全、可恢复的连接，其他功能均无用户价值。

**Independent Test**: 只通过连接、首次配对、取消、断开和 Session 切换即可端到端验证。

**Acceptance Scenarios**:

1. **Given** 有效端点和已授权设备，**When** 用户连接，**Then** 协议探针成功后显示已连接。
2. **Given** 认证失败，**When** 用户完成配对，**Then** 可回到调试端口建立单一活跃 Session。
3. **Given** 操作被取消、超时或 Session 已切换，**When** 旧结果返回，**Then** 不得污染新 Session。

### User Story 2 - Inspect and Debug the Device (Priority: P2)

已连接用户可查看设备概览、执行原始 Shell、读取进程快照并在前台按需采集 Logcat，无法可靠获取的 ROM 字段明确降级。

**Why this priority**: 这是建立连接后的核心调试价值。

**Independent Test**: 每个页面均可在一个已连接 Session 内单独验证加载、刷新、取消、错误和断开状态。

**Acceptance Scenarios**:

1. **Given** 已连接设备，**When** 用户进入任一调试页面，**Then** 只展示可靠结果并允许取消或停止。
2. **Given** 用户离开 Logcat 或设备断开，**When** 生命周期结束，**Then** 持续流立即停止且旧内容不进入新 Session。

### User Story 3 - Manage Local Profiles and Privacy (Priority: P3)

用户可保存最小设备档案、重命名/删除/重连，并清除全部本地数据；敏感输出默认不持久化。

**Why this priority**: 提高重复使用效率，并提供明确的数据控制。

**Independent Test**: 可通过档案增删改、应用重启、清除全部数据和 SAF 导出单独验收。

**Acceptance Scenarios**:

1. **Given** 已保存档案，**When** 用户删除或清除全部数据，**Then** 档案和关联身份不可继续恢复使用。

### Edge Cases

- IPv6、变化的无线调试端口、旧式 `:5555` 与 ROM 命令输出差异必须明确处理或降级。
- 网络不可达、TLS/认证失败、设备拒绝、超时、远端关闭和未知错误不得相互混淆。
- Shell/Logcat 达到容量上限时丢弃最早内容并提示，不能无界增长。

## Requirements (mandatory)

### Functional Requirements

| ID | 最终确认需求 |
|---|---|
| V1-CONN-01 | 支持 IPv4、主机名和方括号 IPv6 的手动端点；不做局域网发现或扫描。 |
| V1-CONN-02 | 直接连接仅在认证/配对错误下提供 6 位配对码回退；区分配对端口与调试端口。 |
| V1-CONN-03 | 支持用户手动配置的 `127.0.0.1`，并尽力兼容明确开启的旧式 `:5555`。 |
| V1-CONN-04 | 同时仅一个活跃 Session；连接/配对/断开可取消、超时、结构化报错并清理资源。 |
| V1-DATA-01 | 保存最小设备档案元数据和身份引用；支持重命名、删除、重连及清除全部本地数据。 |
| V1-OVERVIEW-01 | 展示尽力获取的设备/系统/资源概览；不可得字段明确缺失，前台动态刷新。 |
| V1-SHELL-01 | 支持原始多行 Shell、stdout/stderr、复制、取消和超时；转录仅内存且上限 1 MiB。 |
| V1-PROC-01 | 提供只读进程列表、搜索、刷新和复制；ROM 不兼容时降级，不提供终止入口。 |
| V1-LOG-01 | 用户在前台明确开始 Logcat；支持过滤、暂停、清屏、复制及 SAF 导出。 |
| V1-LOG-02 | Logcat 内存上限 10,000 行或 4 MiB；停止、离开、断开、切换时立即结束。 |
| V1-PRIV-01 | 纯本地、最小权限；密钥/配对码/设备输出不上传、不记录，敏感内容默认不持久化。 |
| V1-DIAG-01 | 提供最多 100 条、进程内、字段白名单的脱敏连接诊断事件。 |

## Out of Scope

- 应用管理、APK 安装/卸载、文件管理/传输、截图/录屏、高级重启。
- 二维码配对、局域网发现、后台 Logcat、后台常驻、自启动。
- Root、提权、绕过系统授权、无障碍自动化、所有文件访问。
- 账号、后端、云同步、广告、统计/遥测和支付。

## Success Criteria (mandatory)

### Measurable Outcomes

| ID | 目标 |
|---|---|
| V1-AC-01 | 已配对/首次配对/本机/有条件旧式连接路径可恢复，切换后旧 Session 不再接收操作。 |
| V1-AC-02 | 概览、Shell、只读进程和前台 Logcat 按生命周期工作且明确降级。 |
| V1-AC-03 | Manifest 与权限矩阵一致；Release 无遥测、支付、Root、无障碍或敏感材料。 |
| V1-AC-04 | 清除全部本地数据后，档案与关联身份不可继续恢复使用。 |

历史验证证据见 [verification](../../docs/archive/releases/v0.01/verification.md)，最终结论见 [closeout](../../docs/archive/releases/v0.01/closeout.md)。
