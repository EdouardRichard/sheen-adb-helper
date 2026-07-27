# v1.0 固定夹具与性能证据

> 执行日期：2026-07-26  
> 工具：JDK 21、Gradle 9.3.1、TestNG、Kotlin 协程虚拟时间。

## 结论

状态：**PARTIAL / OPEN**

固定夹具的正确性、容量边界、顺序调度和无重叠约束通过；没有 Android 运行时帧时序、Compose 点击延迟或 P95 采样，因此 `95% 本地反馈 ≤ 100 ms` 未验证，也不以主机单测耗时替代真机 UX。

## 执行命令

```text
.\gradlew.bat :feature:files:testDebugUnitTest --tests com.sheen.adb.feature.files.FilesReducerTest :feature:shell:testDebugUnitTest --tests com.sheen.adb.feature.shell.ShellTranscriptBufferTest --tests com.sheen.adb.feature.shell.ShellAutoScrollPolicyTest :feature:logcat:testDebugUnitTest --tests com.sheen.adb.feature.logcat.LogcatBufferTest :feature:processes:testDebugUnitTest --tests com.sheen.adb.feature.processes.ProcessesViewModelTest --rerun-tasks --no-parallel --no-daemon --console=plain
```

结果：退出码 0。

## 夹具矩阵

| 夹具 | 自动化结论 | 性能结论 |
|---|---|---|
| 文件列表 1,000 项，文件夹优先、已知时间倒序且稳定 | PASS | 算法行为通过；Compose 滚动与 P95 NOT RUN |
| Shell 10 MiB 有界窗口、本地过滤、原快照不变 | PASS | 容量/正确性通过；终端绘制 P95 NOT RUN |
| Logcat 精确 10 MiB UTF-8 边界、级别/文本即时派生过滤 | PASS | 容量/正确性通过；持续绘制 P95 NOT RUN |
| 进程刷新立即一次、随后严格 5 秒、慢请求不重叠 | PASS | 虚拟时间通过；三机设备开销 NOT RUN |
| 隐藏/后台/断开/Session 切换停止进程轮询 | PASS，60 秒虚拟时间无追加请求 | 真实生命周期 NOT RUN |
| Shell 自动滚动开/关及阅读锚点 | PASS（纯策略） | Compose 滚动交互 NOT RUN |
| 不可见 Logcat 停止采集 | PASS（生命周期合同） | 真实设备读流 NOT RUN |

## 未覆盖

- 应用列表 1,000 项的独立固定性能夹具不存在。
- 本地过滤/搜索/点击的 95 百分位延迟未采样。
- 页面切换 500 ms 内可交互、掉帧、内存峰值与长时间稳定性未测。

这些缺口使性能门禁保持 OPEN，但不否定已通过的容量和调度自动化。
