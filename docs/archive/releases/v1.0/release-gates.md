# v1.0 安全与发布门禁

> 执行日期：2026-07-26  
> 依据：`docs/权限矩阵.md`、`docs/architecture/security-and-delivery.md`、ADR 0004、解析后的 Debug 运行时依赖。

## 汇总结论

状态：**BLOCKED / NOT RELEASABLE**

权限、纯本地和架构自动化未发现 v1.0 新增越权；但 Kadb 2.1.1 的运行时传递依赖 `spake2-java:1.0.5` 仍为 GPL-3.0-or-later，不在仓库允许的 Apache-2.0、MIT、BSD-2-Clause、BSD-3-Clause 白名单。ADR 0004 仍为 accepted 且完成条件未满足，因此构建、单测或未来真机成功都不能覆盖该发布阻断。

## 门禁矩阵

| 门禁 | 状态 | 证据 |
|---|---|---|
| Manifest 权限 | PASS | 本功能未修改 Manifest；未新增存储、位置、相机、Nearby、无障碍、Root 等权限 |
| 新外部依赖 | PASS WITH NOTE | 未新增版本目录坐标；`:feature:apps` 仅接入现有项目模块及仓库已使用的 Activity Compose |
| ADB/Socket/命令边界 | PASS | `V1ArchitectureBoundaryTest` 强制重跑通过 |
| WebView/远程 UI 资源 | PASS | `StrictUiDesignContractTest` 通过 |
| 账号、后端、广告、遥测、支付、Root、无障碍 | PASS（静态/自动化范围） | 无相关依赖或 Manifest 变更 |
| 运行时依赖解析 | BLOCKED | 仍解析 `kadb:2.1.1 -> spake2-java:1.0.5` |
| 真机安全行为 | NOT RUN | 无连接设备 |
| 对外发布 | BLOCKED | ADR 0004 未关闭 |

## 解析依赖摘录

```text
com.flyfishxu:kadb:2.1.1
  com.flyfishxu:kadb-android:2.1.1
    com.github.Flyfish233:spake2-java:1.0.5
net.dongliu:apk-parser:2.6.10
com.google.zxing:core:3.5.4
```

## 解除阻断条件

按 ADR 0004 获得覆盖精确发布物的白名单授权，或在 `:core:adb` 替换/排除该实现并完成依赖、协议、许可证、配对与真机回归。完成前不得创建“v1.0 可发布”、release tag 或对外分发完成结论。
# 2026-07-26 运行时发布补充

- API 37、16 KB 页大小模拟器在安装 Debug APK 时报告现有 native 库 RELRO/对齐不兼容，应用以页面大小兼容模式运行。
- 该问题未阻断本机功能验收，但在完成依赖来源、许可证与升级影响审查前，不得把 16 KB 页大小兼容性标记为通过。
- 既有 `spake2-java:1.0.5` GPL-3.0-or-later 冲突继续独立阻止对外发布。
