# Contract: SAF 与跨页面文件任务

## SAF gateway (`:core:data`)

上传源由 `OpenDocument` 返回，输出目录由 `OpenDocumentTree` 返回。上传来源的显示名/大小使用通用 `OpenableColumns`、MIME 使用 `ContentResolver.getType()`，不得要求来源 Provider 提供 DocumentsContract 专属 flags 或修改时间列。输出树根转换为可操作文档 URI；`createDocument()` 返回的树内子文档 URI 必须原样保留，不得再次折叠为树根。网关提供：

- 查询文档显示名、MIME、可用大小与 provider flags。
- 打开固定分块的输入/输出流。
- 列出用户选择目录的直接子项并按显示名检测冲突。
- 创建任务专属暂存文档/目录。
- 重命名、删除、备份、提交和回滚。

开始写入前必须具备任务所需的 create/write/delete/rename 能力；大小或空间不可判断时允许开始，但写入期空间不足必须结构化失败。URI grant 只用于当前前台任务，不持久化。

上传来源必须支持传输前后稳定性复核；优先比较可靠元数据，元数据不足时允许重新打开并流式计算摘要。provider 无法重新打开或稳定性无法判定时返回 `INTEGRITY_UNAVAILABLE`，不得启动或提交伪成功结果。

## Conflict and commit

- 初始策略为 `CANCEL`，没有用户明确选择不得写入。
- `AUTO_RENAME` 使用 `name (n).ext`，从 1 开始、有界查找，并在提交前再次检查。
- `OVERWRITE`：原目标在暂存写完前保持不变；提交使用备份—替换—删除备份，任一步失败尽力回滚。
- 只删除当前任务创建的暂存/备份；清理或回滚失败进入 `CLEANUP_FAILED` 并向用户显示受影响目标。
- 单 APK 提交一个 `{package}.apk`；多 APK 提交一个 `{package}-apks` 目录；真实包名仅出现在用户可见结果，不进入全局摘要或诊断。

## FilesViewModel ownership

- 由 `MainActivity` 的 ViewModelStore 创建，跨 Destination 与配置变更保持。
- SAF picker 返回且用户确认后才获取 ADB 独占租约并启动 I/O。
- `MainActivity.onStop()` 且 `!isChangingConfigurations` 时调用 `cancelForAppBackground()`。
- `onCleared()`、断开和 Session 切换强制取消并清理；旧结果按 task token + sessionId 丢弃。
- 活动文件任务期间不再打开新的 SAF picker。
- 终态保留到用户关闭或新 Session 清理。

## UI state

浏览状态必须区分：`Initial`、`Loading`、`Content`、`Empty`、`Error`、`Disconnected`、`Cancelled`。

任务状态必须区分：`Idle`、`Preparing`、`AwaitingConflict`、`Transferring`、`Verifying`、`Committing`、`Succeeded`、`Failed`、`Cancelled`、`CleanupFailed`。

全局任务摘要由 `:feature:files` 提供 Composable，`:app` 只装配；摘要展示任务类型、阶段、字节进度/不定进度和取消，不展示完整路径、包名或 URI；仅在当前不位于文件页面时展示“查看”，文件页面内由任务面板直接展示详情。

## UI actions

- 浏览：在“共享存储”和“设备根目录”间切换、通过面包屑/已验证条目进入目录、返回上级、重试、选择文件；不提供自由文本路径命令入口。
- 下载：选择远端文件 → 选择主控端目录 → 解决冲突 → 开始/取消。
- 上传：选择主控端文件 → 选择远端目录 → 解决冲突 → 开始/取消。
- APK：加载当前用户设备可读的 APK 候选 → 选择系统或第三方应用 → 选择主控端目录 → 解决冲突 → 开始/取消。
- 操作冲突：提示先停止 Logcat 或取消文件任务；不得自动终止现有任务。

Android 11+ 系统选择器拒绝内部存储根、可靠 SD 卡根和 Downloads 根时，UI 说明选择或新建子目录，不申请更广权限。
