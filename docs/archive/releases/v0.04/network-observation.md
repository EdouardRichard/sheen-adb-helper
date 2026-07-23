# v0.04 LAN discovery 网络观察

> 日期：2026-07-23  
> 证据边界：不保存 pcap、真实地址、service name、端口或设备身份到仓库；只记录聚合流量类别与门禁结论。

## 固定观察协议

每轮在隔离网络启动一次前台 LAN discovery，并由 packet capture 或 sentinel 记录：

1. 允许：DNS-SD/mDNS 查询 `_adb-tls-pairing._tcp`、`_adb-tls-connect._tcp`。
2. 允许：解析网络中已公布的对应服务。
3. 允许：只有用户选择并重新确认后，连接该已公布端点。
4. 禁止：枚举子网地址、探测未公布主机、扫描常见/连续端口、后台周期发现。
5. 共执行 20 轮；每轮只保留脱敏分类和是否出现禁止流量，不提交原始捕获。

## 环境检查与实际执行

- Windows `pktmon` 可用；`tshark`/`dumpcap` 不可用。
- Android SDK `adb` 可用，但已连接设备数为 0，Android emulator 进程为 0。
- 当前环境没有可启动本应用 discovery 的 Android 设备，也没有 15 服务隔离测试网络或 sentinel 目标。

| 指标 | 计划 | 实际 |
|---|---:|---:|
| 观察轮次 | 20 | 0 |
| mDNS/DNS-SD 分类 | 20 | N/A |
| 禁止的子网/端口探测命中 | 0 | N/A |
| 原始捕获入库 | 0 | 0 |

仅运行 `pktmon` 而没有应用 discovery 流量不能证明“没有主动探测”，因此没有制造空捕获作为通过证据。

## 结论

T078 状态为 **BLOCKED（缺少设备与隔离网络）**，SC016 未判定。NSD adapter 的单元测试和代码审计证明实现只注册两个批准 service type，但不能替代 20 轮外部网络观察。
