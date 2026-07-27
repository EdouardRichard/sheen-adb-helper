# v1.0 自动化验证

> 执行日期：2026-07-26  
> 固定 JDK：`C:\Users\Richard\.gradle\sheen-jdk21`

## 最终结论

状态：**PASS WITH WARNINGS**

全量强制单测与 Debug APK 强制组装均明确以退出码 0 结束。2026-07-26 最终全量运行的 XML 汇总为 754 tests、0 failures、0 errors、0 skipped。该结论只覆盖自动化与可组装性，不替代宽屏视觉、真机配对、性能 P95 或发布合规。

## 最终命令

```text
.\gradlew.bat test --rerun-tasks --no-parallel --no-daemon --console=plain
.\gradlew.bat :app:assembleDebug --rerun-tasks --no-parallel --no-daemon --console=plain
```

| 命令 | 结果 |
|---|---|
| 全量 `test` | PASS，退出码 0 |
| `:app:assembleDebug` | PASS，退出码 0；247 actionable tasks executed |
| Debug APK | 已生成 `app/build/outputs/apk/debug/app-debug.apk` |

## 本轮失败与修复

1. 首次全量运行中 `:core:ui` 的旧断言仍要求 48 dp，而批准的 v1.0 设计合同为 44 dp；更新旧测试合同后聚焦测试通过。
2. 同一工作树存在并发 Gradle 时，`:core:adb` Kotlin classpath snapshot 曾发生一次文件模式/缓存打包错误；停止并发写入后，隔离强制编译通过，后续串行全量测试未复现。
3. 文件生命周期旧测试仍期待直接显示已翻译错误 prose；更新为技术码/类型化本地化合同后通过。
4. 配对呈现的双语迁移曾压平不支持、无效码、失败、取消与超时状态；补齐可区分双语键，并更新未配对设备为 QR 优先合同后，设备模块全绿。
5. Logcat 旧平台测试仍期待分享 Intent；按批准的 SAF 输出目录语义更新为 `OpenDocumentTree` 合同后通过。

## 保留警告

- Kotlin 报告部分未来语言版本兼容、可空调用及实验 API opt-in 警告。
- Debug 组装提示两个本地库未 strip，按原样打包。

这些警告未导致当前构建失败，但不能据此宣告发布合规。
