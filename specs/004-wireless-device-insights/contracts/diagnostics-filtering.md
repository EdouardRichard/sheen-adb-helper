# Contract: Process and Logcat Basic Analysis

## Process snapshot

- 继续只读获取当前 Session 快照；不结束进程、不提权、不后台监控。
- 支持 PID 完整/部分文本、进程名、应用筛选；所有已启用条件取 AND。
- 应用关联为 `VERIFIED / MULTIPLE / UNKNOWN`。只有 sessionId、snapshot generation 及可靠 UID/package/process 信息一致时可为 VERIFIED。
- PID 复用、共享 UID、多进程、目标退出、字段缺失或快照竞态不得猜测唯一应用。

## Structured Logcat

- 核心以 threadtime 语义解析 timestamp、PID、TID、level、tag、message，同时保留 raw text。
- 无法解析的记录标 `UNPARSED` 并保留；stderr 标 `STDERR`，两者不伪造 level/PID/tag。
- 支持 level、tag、keyword、PID、process、application 的单项或组合筛选；所有非空条件取 AND。
- process/application 条件只匹配 VERIFIED association；UNKNOWN/MULTIPLE 不得被当作唯一命中。

## Lifecycle and bounds

- 原始内存边界保持 10,000 行或 4 MiB，最旧记录先淘汰；可见窗口保持最新 100 条匹配记录。
- filter 变化只重新计算有界 buffer；1 秒内完成，不重新读取无界历史。
- pause 保留缓冲但停止可见更新；clear 清空原始/可见/association；stop、离页、断开和 Session change 停止流并拒绝旧结果。
- 复制/SAF 导出沿用现有用户明确动作；应用名、包名关联及 Logcat 内容不进入普通诊断日志。

## Out of scope

不做 crash/ANR 自动识别、CPU/内存趋势、时间段对比、线程转储、网络抓包、后台 Logcat、自动上传或进程终止。
