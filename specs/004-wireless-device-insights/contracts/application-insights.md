# Contract: Application Names, Icons, and Search

## Scope

只枚举当前 Session 的当前用户第三方应用，保持既有强停/禁用/启用授权范围。新增展示元数据不能扩大到主控端应用清单、其他用户、系统应用管理或网络商店查询。

## Delivery

1. `listApplications()` 快速返回包名、userId、enabled state 和既有可选字段。
2. `observeApplicationMetadata(expectedSessionId)` 对同一 snapshot 逐项发出项目自有 `ApplicationMetadataUpdate`。
3. UI 首帧使用包名和默认图标；update 到达时按 `(sessionId,userId,packageName)` 替换。
4. Session 变化、页面离开或取消后不再交付 update，并清空原始 APK 字节与图标 cache。

## Resource and security limits

- 基础 APK 路径只能来自核心层解析的 PackageManager 输出；feature 不提供路径或 Shell。
- 单包原始输入不超过 32 MiB；单图标不超过 1 MiB；图标 LRU 总计不超过 16 MiB；整批 enrichment 默认 10 秒且单包顺序处理。
- 所有字节仅在内存；不写 cache/files/external storage，不进入诊断或测试夹具。
- ZIP traversal、损坏资源、压缩炸弹、split-only/动态资源、缺失 entry、权限拒绝和超限都转为明确 metadata status，不崩溃、不无限重试。

## Display and search

- `displayName` 非空时主标题显示名称、次标题显示包名；名称缺失时主标题显示包名并显示降级原因。
- 同名应用必须显示各自包名。
- query 经过 trim 和稳定大小写规范化后，以 `packageName contains query OR displayName contains query` 匹配；filter（enabled/disabled）与 query 取交集。
- 元数据加载中搜索结果可逐步更新；包名匹配从首帧起完整可用，应用名匹配在对应 metadata 可用后 1 秒内出现。

## Parser isolation

第三方 APK parser 仅实现 internal `ApplicationMetadataParser`；核心 public model 不出现第三方类型。若许可证、传递依赖、Android 运行时或安全验证不通过，必须替换 parser 或返回 `UNSUPPORTED`，不能扩大权限、安装 remote helper、使用网络服务或持久化 APK。
