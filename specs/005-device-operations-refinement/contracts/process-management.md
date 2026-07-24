# Contract: Process Management

## 1. Public project-owned operations

```text
refreshProcesses(): Flow<AdbOperationResult<ProcessSnapshot>>
prepareTermination(targetIdentity): AdbOperationResult<TerminationOptions>
terminateProcess(request): Flow<AdbOperationResult<ProcessTerminationResult>>
```

Feature 不得传递或拼接 `ps`、`kill`、`dumpsys`、`am` 或 `/proc` 命令。所有操作必须绑定调用开始时的当前 `sessionId` 和请求代次。

## 2. Snapshot guarantees

- 每次刷新独立取消上一次未完成刷新，旧代次不能覆盖新状态。
- 单次事务的 CPU 两个样本默认间隔 500 ms，总预算 5 秒。
- 每个进程返回六个用户字段或字段级状态：应用名、进程名、CPU%、PSS MiB、PPID、PID。
- CPU 为设备总 CPU 容量归一化的区间百分比 0–100；PSS 为 KiB/1024 的 MiB。
- 单字段不可用不得隐藏整个进程，也不得用 RSS、累计 CPU 或 0 冒充。
- 解析器必须对缺列、列顺序差异、超长行、非数字、进程中途退出和 ROM 拒绝返回结构化结果。

## 3. Association guarantees

Reliable association requires:

```text
same session
AND same Android user
AND compatible UID
AND (
  processName == packageName
  OR processName startsWith packageName + ":" and package candidate is unique
)
```

不满足时：

- `applicationName = "无法解析应用名"`；
- `wholeApplicationAllowed = false`；
- 若目标属于普通应用 UID 且身份完整，可保留单进程终止选项。

## 4. Confirmation boundary

`prepareTermination` 返回：

- 单进程是否可用及拒绝原因；
- 整个应用是否可用及可靠包身份；
- 当前确认范围内的进程身份摘要；
- 每次确认唯一 nonce。

风险确认必须展示范围、目标和至少以下影响：应用异常、未保存数据丢失、服务/任务中断、设备不稳定；整个应用还须说明这是应用级 force-stop，会停止应用进程、服务和任务，并可能在用户再次显式启动前阻止后台恢复，而不是只逐个终止确认页中的 PID。

只有同一确认页点击“我了解”才能创建 `ProcessTerminationRequest`。取消、返回、关闭或目标刷新使 nonce 失效，零终止命令。

## 5. Execution

### Single process

1. 重新读取并比较 `sessionId/pid/startTimeTicks/uid/processName`。
2. PID 1、`appId < 10000`、身份不全或变化时返回 `POLICY_REJECTED`/`IDENTITY_CHANGED`。
3. 发送一次 `SIGTERM`；不自动发送 `SIGKILL`。
4. 有界等待后刷新并验证该 identity 是否消失。

### Whole application

1. 重新确认唯一 package/user/UID 关联及当前集合。
2. 集合无法安全确认或与用户确认对象发生身份冲突时拒绝。
3. 通过 `:core:adb` 的受控 `am force-stop --user` 执行。
4. force-stop 允许停止该应用的进程、服务和任务，并影响其后台恢复；确认集合仅用于目标身份和结果验证，不限定系统命令的实际影响面。
5. 刷新并验证确认集合及同包新进程；仍存活则为 `PARTIAL` 或 `UNKNOWN`，不得报成功。验证通过只表示当前观察时点没有同包活动进程，不承诺应用不会在用户显式启动后再次运行。

## 6. Result mapping

| Outcome | UI meaning |
|---------|------------|
| `TERMINATED` | 所选范围经刷新确认已不存在 |
| `PARTIAL` | 整个应用目标仅部分消失或出现仍属同包的进程 |
| `ALREADY_EXITED` | 执行前目标已自然退出，未误杀替代 PID |
| `POLICY_REJECTED` | 本地安全策略或被控端权限拒绝 |
| `UNSUPPORTED` | ROM 不提供所需身份/命令能力 |
| `IDENTITY_CHANGED` | PID 复用、UID/名称/集合改变 |
| `UNKNOWN` | 命令或验证结果不足以证明状态 |
| `CANCELLED` | 用户/调用取消且资源已关闭 |
| `TIMED_OUT` | 10 秒内未得到可验证结果 |
| `DISCONNECTED` | 发起 Session 断开或切换 |

结果仅包含安全项目码和身份摘要，不包含原始 Shell 输出。
