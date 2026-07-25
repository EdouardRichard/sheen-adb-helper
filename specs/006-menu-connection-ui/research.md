# Phase 0 Research: v0.1 菜单与连接页 UI

## 目标与已确认边界

本研究把 `spec.md` 与 ADR 0008 转换为可实现的模块边界。三个快捷操作的对象均为被控端：主控端只发起操作并接收/导出被控端屏幕内容；不得截取或重启主控端。截图、录屏、重启使用同一个全局互斥组，并绑定发起操作时的 `sessionId`。

## 现有实现适配度

| 需求 | 当前实现 | 计划适配 |
|---|---|---|
| 顶部栏、抽屉菜单、底部六项导航 | `app/.../SheenApp.kt` 已有顶部栏和响应式抽屉，无底部栏 | 在 app 壳集中维护顶部/底部/抽屉状态；连接页作为“连接”入口，保留其余五个页面现有逻辑 |
| 历史设备 | `core:data` 的 `DeviceProfileRepository` 已持久化排序、重命名、删除和重连 | 只迁移展示位置到菜单；重连和替换确认仍由 `DevicesViewModel` 执行 |
| 局域网扫描、IP:端口、二维码、配对码、本机配对 | `feature:devices` 与 `core:adb` 已有完整流程，扫描已有 10 秒生命周期 | 复用 ViewModel/会话流程；新增下拉刷新手势调用现有 `refreshDiscovery()` |
| 已连接设备概览 | `feature:overview` 已按 session 加载并轮询动态指标 | 把 session-aware 概览状态组合进连接页已连接分支，不复制 ADB 读取逻辑 |
| 语言切换 | 当前没有语言偏好；连接页和 app 壳有硬编码中文 | 在 `core:data` 共享 DataStore 中增加语言键；仅本版本壳、连接页、设置/菜单文案提供中英文资源，未纳入页面保持原逻辑 |
| 截图/录屏/重启 | `AdbSessionManager` 尚无 typed API | 在 `:core:adb` 增加快捷操作模型和 API；UI 不组装 raw shell |
| SAF 导出与临时文件 | `SafDocumentStore`、`SafTextExporter`、`TemporaryDataCleaner` 已存在 | 新增二进制导出网关：被控端内容先写 app-private bounded temp，再由用户选择的 `CreateDocument` URI 导出；所有终态清理 |
| 快捷操作用例编排 | 当前没有符合 app-only-assembly 边界的协调层 | 在 `feature:overview` 内增加 Use Case，协调 `:core:adb` 与 `:core:data` 的项目自有端口；不新增模块，app 仅构造/注入并回传系统文件选择结果 |

## 关键技术决策

### 1. ADB 长时操作使用流式会话

录屏不可通过现有 `executeShell` 实现，因为该方法会在整个命令期间持有 manager 锁。实现应复用 `streamLogcat` 的模式：取得 active session 快照，打开 typed shell/sync stream，在取消、超时、断开或 session 切换时关闭子流并释放 lease。截图可使用同一字节流/remote-temp + sync sink 模式；协议细节封装在 `core:adb`。

### 2. 快捷操作使用独立全局互斥门

现有 `FILE_TRANSFER`、`APK_EXTRACTION`、`LOGCAT` lease 不能直接表达三个快捷操作之间的互斥。增加 `QUICK_ACTION`（或等价的专用 coordinator）并规定它与现有长操作冲突。所有请求带 `expectedSessionId`；旧 session 的结果必须转换为结构化 stale-session failure，不得更新新连接页面。

### 3. 受控端视频为无音频单段

录屏只保存视频，不请求被控端音频、主控端麦克风或 MediaProjection。达到 5 分钟或 256 MiB 任一上限即停止；取消、超时、断开、格式校验失败都只产生一个失败/取消终态，不自动拆分或续录。

### 4. 重启结果语义

点击重启必须先经明确高风险确认。发送重启命令后，被控端断开是 `requested/unknown`，不是成功；操作不等待重连、不自动重连，也不持有 mutex 等待设备回来。用户必须手动重新连接。

### 5. 临时文件与导出

截图/录屏先写 `cacheDir` 下按任务生成的不可预测临时文件，限制大小、校验非空和格式。UI 通过 Activity Result `CreateDocument` 获取目标 URI；URI 写入和错误映射由 `core:data` 网关负责，UI 不访问 `ContentResolver`。成功、取消、异常、进程启动清理和“清除本地数据”都必须删除主控端临时文件。不得把真实路径、IP、配对材料或二进制内容写入诊断日志。

### 6. 导航与语言

底部栏固定六项：连接、文件、应用、进程、终端、日志。独立 `OVERVIEW` 不作为底部栏项目；其内容由连接页已连接分支复用。抽屉菜单承载历史设备、设置、关于和版本信息。语言偏好使用当前 profile DataStore 的同一 owner，默认中文，切换后壳和连接页可见文案立即更新；不得创建同一文件的第二个 DataStore owner。

### 7. Feature-owned 快捷操作 Use Case

`feature:overview` 的 `QuickActionUseCase` 创建 `core:data` artifact/sink、调用 `core:adb` typed API、校验元数据、协调 SAF 导出与终态清理，并只向 ViewModel 返回 opaque artifact reference 和结构化结果。两个 core 模块分别提供项目自有端口且互不依赖；Feature 内部适配器只桥接这些端口，不向 ViewModel/UI 暴露协议字节流、File、OutputStream、ContentResolver 或原始 URI。`:app` 不拥有上述业务流程，只负责构造/注入 Use Case、导航，并把系统 `CreateDocument` 结果通过 data 平台适配器转换为项目自有导出目标后回传。

## 未引入的方案

- 不新增存储、媒体、相机、麦克风、通知、前台服务或 MediaProjection 权限。
- 不新增第三方媒体/二维码/ADB 依赖。
- 不把历史设备迁移到新的数据表。
- 不改文件、应用、进程、Shell、日志页的既有功能实现或本版 UI。

## UI 设计转换决策

实现者先读 `D:\androidPorject\stitch_adb\technical_terminal_systems\DESIGN.md`，再读取未连接页、已连接页和菜单页各自的 `code.html`/`screen.png`。`code.html` 是 HTML + Tailwind CSS 的页面设计源码，优先转换其结构、组件层级、尺寸、图标语义和状态为 Jetpack Compose；不使用 WebView，也不复制 Google Fonts、Tailwind 或 Material Symbols CDN。

`DESIGN.md` 的 dark-first 语义、4px 节奏和字体角色映射到 `core:ui`；页面截图按未连接/已连接 468×1060、菜单 468×1046 归一化核对。设计文件 SHA-256 固化在 `plan.md`，实现前必须复核；发生漂移或仓库外设计源不可访问时暂停并由项目负责人重新确认，不得仅凭哈希猜测设计内容。设计稿的 44px 作为视觉尺寸，实际 Compose 点击区域保持至少 48dp。未批准本地字体资产时使用 SansSerif/Monospace 本地回退，并记录字体/图标替代差异；菜单使用真实 `versionName 0.1.0`（`versionCode 3`）。

## 验证依据

研究依据为当前 `docs/architecture/`、ADR 0001/0007/0008、权限矩阵、`SheenApp.kt`、`feature:devices`、`feature:overview`、`AdbSessionManager`、`SafDocumentStore` 与现有 session/transfer/logcat/SAF 测试。控制端基线仍为 API 30+；实现阶段使用当前一名用户可用的三台被控端逐台验证连接、断开和独立能力，其中低于 Android 10 的设备重点验证能力检测与正确降级。对报告支持的设备再验证截图、录屏上限、重启未知结果和 SAF 失败路径；证据不得包含真实 IP、密钥、配对码、Shell/Logcat 原文。
