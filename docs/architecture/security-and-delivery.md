# 安全、权限与交付现状

> 类型：当前主干事实
> 依据：HEAD Manifest/依赖、权限与许可证文档、accepted ADR。

## 产品与权限

- 产品无账号、后端、广告、统计/遥测、崩溃上报、推送、支付、Root 或无障碍自动化。
- 产品 Manifest 当前只声明普通权限 `android.permission.INTERNET`；SAF 导出不增加 Manifest 权限。
- 没有包可见性、存储、通知、前台服务、自启动、设备管理员或后台采集组件。
- 权限的唯一当前登记入口是 [`docs/权限矩阵.md`](../权限矩阵.md)。

## 数据与交付

- ADB 身份由 Keystore 保护；配对材料、真实端点、设备标识、包名上下文、Shell/Logcat 和签名材料不得进入仓库或日志。
- 默认分发为开源仓库、自行构建与用户自主安装，不默认面向任何应用商店，见 [ADR 0005](../adr/0005-distribution-strategy.md)。
- 仓库许可证为 Apache-2.0；依赖白名单为 Apache-2.0、MIT、BSD-2-Clause、BSD-3-Clause。
- 依赖与许可证事实分别由 [`docs/第三方依赖审查.md`](../第三方依赖审查.md) 和 [`docs/第三方依赖与许可证.md`](../第三方依赖与许可证.md) 维护。

## 当前阻断

- Kadb 2.1.1 运行时传递依赖 `spake2-java 1.0.5` 为 GPL-3.0-or-later，不在白名单。
- v0.01/v0.02 的功能与真机结论不因此撤销，但取得可审计白名单授权或替换并回归前，对外发布合规仍被阻断。
- 完成条件和处置边界见 [ADR 0004](../adr/0004-spake2-license-compliance.md)；不得通过改写许可证名称消除事实。
