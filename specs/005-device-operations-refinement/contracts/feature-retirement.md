# Contract: Application Icon, Logcat Analysis, and Diagnostic History Retirement

## 1. Application metadata surface

Allowed public fields:

```text
packageName
displayName or explicit unresolved state
existing operation identity/status fields
```

Forbidden v0.05 fields and behavior:

```text
icon bytes / drawable / icon key
icon extraction or apk.allIcons
icon cache / eviction
icon placeholder rendering
version or installer display in the list
```

Search by application name/package and all existing application operations retain their prior target identity, confirmation, Session and result contracts.

## 2. Logcat feature surface

Allowed:

- snapshot/continuous selection;
- start/stop;
- elapsed time and byte size;
- completion/stop/error state;
- save to chosen directory;
- share one generated file.

Removed:

- log level/process/application filters;
- pause/resume analysis;
- structured line interpretation;
- process/application association;
- abnormal pattern/trend analysis;
- 10,000-line analysis window and visible-record buffer.

本节删除的“process/application association”仅指 Logcat 分析链路中的日志关联 API 与实现，包括旧 `core/adb/.../internal/diagnostics/ProcessAssociation.kt` 及其专用测试；进程管理的 `internal/processes/ProcessApplicationAssociation.kt` 是 US2 的安全目标识别契约，必须先建立并保留，不能被退役扫描或删除任务命中。

## 3. Diagnostic history surface

The following concepts cease to exist:

```text
AdbDiagnosticEvent
diagnosticEvents StateFlow
append/clear/query diagnostic history
100-entry event ring/buffer
"查看脱敏诊断事件" and its card/page
```

Current-operation feedback remains required. It is scoped to the active workflow and is discarded with that workflow; it must not be copied into a replacement “recent operations”, audit feed, analytics event or hidden history.

## 4. Compatibility and deletion checks

Implementation completion requires repository searches to produce zero production references for:

- `AdbDiagnosticEvent` and `diagnosticEvents`;
- icon extraction/cache/rendering symbols;
- structured Logcat analysis/filter/association symbols;
- the user-visible diagnostic history string.

References preserved only inside historical release archives or superseded ADR text are allowed and must be clearly historical.
