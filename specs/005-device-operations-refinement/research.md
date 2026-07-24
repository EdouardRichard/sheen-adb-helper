# Phase 0 Research: v0.05 设备操作体验与性能收敛

## R-01 无线发现实现边界

**Decision**: 继续使用 Android `NsdManager` 实现 `_adb-tls-pairing._tcp` 与 `_adb-tls-connect._tcp` 发现，以功能完成为目标；不调用或复刻工作站 `adb mdns check` / `adb mdns services`，不引入 ADB Server、adb 二进制或额外 mDNS 包。

**Rationale**: `adb mdns` 子命令是桌面 adb 客户端向本地 adb server 查询 mDNS 状态/服务的入口，不是可直接在 Android 被控端 Shell 中复用的设备命令。仓库已有受 Session 管理的 `NsdManager` 适配层，复用它能满足本机配对和局域网发现，同时保持纯本地、最少依赖与既有权限边界。

**Alternatives considered**:

- 内置 adb 客户端/Server：体积、生命周期、许可证和安全面显著增加，且与单一 `AdbSessionManager` 架构冲突。
- 新增第三方 mDNS 库：当前 Android 平台 API 已能实现需求，没有足够收益。
- 逐字等价实现 `host:mdns:*`：用户已明确不要求语义等价，且会引入无价值的协议耦合。

## R-02 配对扫描为什么当前不能真正持续 2 分钟

**Decision**: 把发现模式和唯一截止时间贯穿 source request、NSD request、policy 和 adapter；局域网扫描继续 10 秒，本机配对扫描由外层 2 分钟窗口控制，适配层不得再另行提前截止。

**Rationale**: 现有 `WirelessDiscoverySourceRequest` 有 `mode`，但 `AndroidNsdDiscoveryAdapter` 转换为 `NsdDiscoveryRequest` 时丢失该字段，导致 `NsdDiscoveryPolicy` 对所有模式应用约 10 秒截止。仅把前台流程搬到通知服务仍会在约 10 秒后失去发现源，无法满足“切到设置后最多继续 2 分钟”。

**Alternatives considered**:

- 每 10 秒重启一次 NSD：产生额外竞态、重复服务与难以证明的停止行为。
- 同时保留内外两个定时器：会产生先后竞态，清理和测试更复杂。
- 永不停止 NSD：违反有界后台和电量约束。

## R-03 可靠设备名

**Decision**: 设备名是带来源的可选值，本版本仅接受成功连接后通过既有 overview 属性读取、完成安全文本校验并能与当前扫描条目的同一 `VerifiedWirelessDeviceId` 关联的设备型号（`VERIFIED_AFTER_CONNECTION`）。安全文本校验固定为：去除首尾 Unicode 空白后非空、最多 80 个 Unicode 码点、不含 ISO 控制字符，也不含双向控制字符 U+061C、U+200E–U+200F、U+202A–U+202E、U+2066–U+2069；任一条件不满足时整体标记为 `UNAVAILABLE`，不截断后展示。原始 ADB mDNS/NSD 观察值的名称来源为 `UNAVAILABLE`；服务实例名、GUID/序列样式字符串、主机名、IP 反查、地址或现有档案名称不得用作设备名。缺失或关联不可靠时直接显示端点行。

**Rationale**: AOSP ADB Wi‑Fi 使用 `_adb-tls-pairing._tcp`/`_adb-tls-connect._tcp` 公告服务；ADB mDNS 实现中的实例名通常是序列/GUID 加随机后缀，安全连接 TXT 记录只携带协议版本，不提供友好设备名。`NsdServiceInfo` 的服务名和主机名因此不能证明是用户可识别设备名。80 码点足以容纳常见设备型号，同时给测试和布局提供固定上限；拒绝而非截断避免把两个不同名称显示成相同前缀，控制字符与双向控制字符的固定拒绝集合避免换行、字段遮蔽和视觉重排。宁可省略也不能猜测；名称只用于显示，去重仍使用既有服务身份和端点规则。

**Alternatives considered**:

- 展示 service instance、GUID/序列样式字符串或 hostname：会把协议身份或随机名误报成设备名。
- 反向 DNS：不可用性高、可能阻塞，且增加名称欺骗面。
- 以系统版本硬编码：Android 版本不能补出 mDNS 公告中不存在的友好名称。
- 直接复用现有档案 `displayName`：现有字段可能是端点、默认文案或用户重命名，且 `identityReference` 是主机身份引用，不足以证明远端设备身份。

## R-04 应用标签与图标降级

**Decision**: 保留现有受限 APK 读取和 `apk-parser` 依赖用于应用标签；删除 `apk.allIcons`、图标解析/传输/缓存/占位及对外模型字段。列表只显示标签与包名。

**Rationale**: 仓库当前没有跨 API 30–36、跨 ROM 可靠返回第三方应用标签的轻量 Shell 接口；完全删除 APK 解析会使大量条目只能显示包名。性能热点主要来自解析全部图标、图像字节和 LRU 管理，移除这些路径即可显著降低 CPU/内存，而不新增依赖。

**Alternatives considered**:

- 仅用 `pm list packages`：可靠得到包名，不能可靠得到本地化应用名。
- 控制端 `PackageManager`：只能解析控制端已安装应用，不能代表被控端。
- 保留缩略图：仍需要提取、解码和缓存，违反明确的零图标目标。

**Residual risk**: 标签仍需读取 APK，200 应用/10 秒目标须以真机数据验证；如仍不达标，应优化标签读取调度或增加有界并发，不得恢复图标或新增未审查依赖。

## R-05 进程 CPU、PSS 与身份

**Decision**: 用同一 500 ms 区间的两个 `/proc/stat` 和 `/proc/<pid>/stat` 快照计算 CPU；基础字段由受控 `ps` 输出提供；PSS 使用能力探测通过的 `dumpsys meminfo -c` 紧凑输出。进程身份至少为 `sessionId + pid + startTimeTicks + uid + processName`。

**Rationale**: `/proc` 增量直接表达采样区间，避免把累计 CPU 时间误报为占用；AOSP `dumpsys meminfo -c` 提供 PSS，而 RSS 不满足规格口径。`startTimeTicks` 能识别相同 PID 被新进程复用。所有原始命令和 ROM 差异解析集中在 `:core:adb`。

**Calculation**:

- `cpuPercent = clamp(processCpuDelta / totalCpuDelta × 100, 0, 100)`，按设备总 CPU 容量归一化并显示一位小数。
- `pssMiB = pssKiB / 1024.0`，显示一位小数。
- 单样本、负增量、退出、访问受限或格式未知返回字段级 `CALCULATING`/`UNKNOWN`，不返回假零值。

**Alternatives considered**:

- `top`：不同 ROM 的列、批处理参数和多核百分比语义差异大。
- RSS：不是 PSS，不能替代用户确认的口径。
- 单次累计 CPU 时间：不表示当前区间占用。

## R-06 应用关联和终止语义

**Decision**: 仅在同一用户/UID 下，进程名等于包名或等于唯一候选的 `包名:后缀` 时建立可靠关联。单进程路径对复核通过的非核心目标发送一次 `SIGTERM`；整个应用路径复用 `am force-stop --user`，它是应用级 force-stop 而不是逐 PID 终止，执行前后都重新枚举并验证应用状态。

**Rationale**: 仅用文本相似度会误终止其他应用；UID、包名和进程后缀联合判断可处理 Android 多进程应用。`SIGTERM` 给单进程最小影响且不自动升级破坏性；`am force-stop` 是无 Root 条件下停止一个应用的受支持 shell 能力，会停止其进程、服务和任务，并可能在用户再次显式启动前阻止后台恢复，因此确认页不能把它描述成“只终止当前进程集合”。

**Safety policy**:

- PID 1、`appId < 10000` 的核心/系统身份、缺失 `startTimeTicks` 或执行前身份变化：拒绝终止。
- 无可靠包关联的普通应用进程只可尝试单进程；“整个应用”不可用。
- 共享 UID、多包候选或进程集合变化：拒绝整个应用请求。
- 不自动使用 `SIGKILL`、Root、Shizuku、设备管理或无障碍绕过策略。

**Alternatives considered**:

- 对应用逐 PID `kill`：在 Android 权限/SELinux 下可靠性较低，且容易遗漏重启进程。
- 直接执行不复核：存在 PID 复用和集合变化误杀风险。
- 默认 `SIGKILL`：风险过高且不符合最小影响原则。

## R-07 Logcat 捕获模型

**Decision**: 用 `:core:adb` 的新捕获契约替代应用内分析契约：快照为有界 dump，持续模式使用设备侧起始游标并只交付游标之后的记录；两者均以 10 MiB 为上限，持续模式再以 10 分钟为上限，并租用 `LOGCAT` 独占操作。

**Rationale**: ADB Logcat 本身可通过有限命令/流提取日志；应用只需保留文件输出，不再需要结构化解析、进程关联、筛选和分析缓冲。独占租约、Session 绑定和明确停止原因能避免 Logcat 与文件等长操作争用同一连接。

**Alternatives considered**:

- 保留现有分析缓冲后导出：继续承担 10,000 行/4 MiB 分析和关联开销，违背降级目标。
- 清空设备日志后开始采集：会破坏被控端状态，不接受。
- 无边界流式写文件：违反显式用户选择、内存/存储和资源清理约束。

**ROM fallback**: 若设备不支持可靠起始游标，持续模式返回 `UNSUPPORTED`，不得静默包含开始前日志；快照仍可独立可用。

## R-08 保存目录和系统分享

**Decision**: 保存使用 `OpenDocumentTree` + 既有 `SafDocumentStore` 的临时文件/原子式提交；分享使用应用专属 `cacheDir/logcat-share/` 与非导出 `FileProvider` 的临时只读 URI。命名为 `sheen-logcat-{snapshot|continuous}-{UTC}.txt`。

**Rationale**: SAF 让用户明确选择目录且无需全盘存储权限；FileProvider 是 Android 分享私有文件的标准机制。先在内存捕获，只有用户明确保存或分享后才产生持久文件或临时缓存。

**Observable states and cleanup policy**:

- 保存取消/失败：清理 `.part` 临时文件；成功文件由用户管理。
- 分享租约状态为 `PREPARED/CHOOSER_OPENED/TARGET_SELECTED/CANCELLED/OUTCOME_UNKNOWN/EXPIRED/CLEANED`。
- chooser 打开或应用恢复前台只表示 `CHOOSER_OPENED`，不得报告分享成功。
- 平台回调明确报告目标选择时进入 `TARGET_SELECTED`；这只证明选择发生，不证明接收方已读取文件。
- 平台明确报告取消时进入 `CANCELLED` 并立即删除；平台无法观察选择/取消时进入 `OUTCOME_UNKNOWN`，不显示成功或失败。
- `TARGET_SELECTED` 和 `OUTCOME_UNKNOWN` 均最多保留 1 小时，并在到期、下次启动、创建新分享、清除数据时清理。
- 只授予本次 URI 的临时只读权限，不持久授权、不共享目录。

**Alternatives considered**:

- `CreateDocument`：只能逐个选择文件位置，不满足“指定目录”。
- 外部共享存储直写：需要更广权限且用户意图不清晰。
- 自定义网络上传：违反纯本地。

## R-09 专用诊断事件历史

**Decision**: 完整删除事件模型、100 条内存缓冲、写入/清除/查询接口和 UI 入口；保留每个当前操作自己的结构化结果和错误状态。

**Rationale**: 用户明确后续版本不再需要该能力，规格要求不仅不可达，还要停止采集、缓存和聚合。保留错误类型不等于保留历史。

**Alternatives considered**:

- 仅隐藏按钮：后台仍采集，违反 FR-013/SC-005。
- 用新“最近操作”替代：构成未授权的替代事件历史。
- 删除所有结构化错误：会破坏当前操作反馈和宪法验证要求。

## R-10 权限、依赖与治理

**Decision**: 不新增权限或第三方依赖。实现前从 Gradle 已解析依赖图确认 `androidx.core.content.FileProvider` 可用；若必须新增直接依赖声明，则暂停并先完成依赖用途/许可证审查，而不是依赖偶然的传递关系。实现阶段更新 `docs/权限矩阵.md` 中通知出现时机、SAF 目录和 FileProvider 临时分享说明；将 ADR 0002 与 ADR 0006 标记为被 v0.05 取代，同时保留历史正文；同步相关架构文档。

**Rationale**: 通知/短时服务权限已在 v0.04 获批；SAF 和应用私有缓存分享不需要广泛存储权限；`FileProvider` 使用现有 AndroidX/平台能力。治理文档必须反映实际行为，且历史决策不能被无痕改写。

**Alternatives considered**:

- 新增 Nearby/定位/存储权限：功能不需要且扩大权限。
- 新增进程或日志解析库：当前 AOSP 文本格式和项目自有解析器足够。
- 重写历史 ADR：破坏审计性。

## Sources

- [AOSP: ADB Wi‑Fi architecture and service types](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_wifi.md)
- [AOSP: ADB mDNS instance and TXT-record construction](https://android.googlesource.com/platform/packages/modules/adb/+/f4ba8d73079b99532069dbe888a58167b8723d6c/adb_mdns.cpp)
- [AOSP: adb `mdns` command dispatch](https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/adb.cpp#1114)
- [Android Developers: Android Debug Bridge](https://developer.android.com/tools/adb)
- [AOSP: compact meminfo PSS parsing usage](https://android.googlesource.com/platform/tools/base/+/studio-master-dev/profiler/native/perfd/memory/memory_usage_reader_impl.cc)
- [Linux `/proc` process and CPU accounting documentation](https://android.googlesource.com/kernel/common/+/refs/tags/android14-6.1-2024-12_r8/Documentation/filesystems/proc.rst)
