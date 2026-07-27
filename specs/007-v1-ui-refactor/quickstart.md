# Quickstart: Implement and Validate v1.0 UI Refactor

This guide is for the later `/speckit-implement` phase. It does not authorize code changes during Plan.

## 1. Read the approved context

Read in this order:

1. `.specify/memory/constitution.md`
2. `.specify/feature.json`
3. `specs/007-v1-ui-refactor/spec.md`
4. `specs/007-v1-ui-refactor/plan.md`
5. `specs/007-v1-ui-refactor/research.md`
6. `specs/007-v1-ui-refactor/data-model.md`
7. all files in `specs/007-v1-ui-refactor/contracts/`
8. approved `specs/007-v1-ui-refactor/tasks.md`

Read only the architecture/ADR files referenced by the Plan or the current implementation task. Do not load historical feature specs unless a specific regression investigation requires them.

## 2. Confirm the design snapshot

Before editing a page, calculate SHA-256 for its `code.html`, `screen.png`, and `technical_terminal_systems/DESIGN.md`, then compare with [research.md](./research.md#baseline-fingerprints).

If a hash differs:

1. stop that page's implementation;
2. diff/inspect the changed design;
3. determine whether the active spec or contracts need an update;
4. do not silently combine old planning with the new design.

The required conversion is native Jetpack Compose. Do not introduce WebView or runtime Web resources.

## 3. Implementation order

Follow task dependencies rather than implementing screen visuals first:

1. core models, operation stages, errors, exclusive ownership and typed localization primitives;
2. existing `:core:data` language preference → `:app` root `UiLanguage` distribution, with shared and per-Feature catalog contracts;
3. `:core:adb` interactive Shell, application/APK ports and normalized Fatal severity;
4. `:core:data` output-directory plus per-file staging/verification/commit support; do not add a cross-file atomic transaction;
5. Feature reducers/state machines, pure policies, semantic presentation keys and per-page catalogs;
6. App-level lifecycle/navigation lock/root-overlay orchestration;
7. `:core:ui` local tokens/icons and shared catalog;
8. five Compose page conversions;
9. connection-page scoped fixes split across `:feature:devices`, `:feature:overview` and `:app`;
10. version metadata, automated, fixture, visual, then real-device validation.

Do not allow a Feature/UI module to bridge a missing core capability with a raw Shell command.

## 4. Local build environment

Use the repository's known JDK and non-parallel deterministic Gradle invocation:

```powershell
$env:JAVA_HOME = 'C:\Users\Richard\.gradle\sheen-jdk21'
.\gradlew.bat --no-parallel --no-daemon --console=plain test
.\gradlew.bat --no-parallel --no-daemon --console=plain :app:assembleDebug
```

Wait for the explicit Gradle exit result. A task line that appears successful before process exit is not final evidence.

For a focused change, run the affected module first, then the full suite. Examples:

```powershell
.\gradlew.bat --no-parallel --no-daemon --console=plain :core:adb:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :core:data:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :core:ui:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:files:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:apps:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:processes:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:shell:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:logcat:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:devices:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:overview:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :feature:settings:test
.\gradlew.bat --no-parallel --no-daemon --console=plain :app:test
```

Use actual Gradle task names reported by the project if Android plugin variants expose `testDebugUnitTest` instead of the shorthand.

## 5. Automated contract suite

At minimum, add deterministic tests for:

### Navigation/lifecycle

- six-destination order and endpoint clamping;
- latest accepted rapid request and exactly-once visibility callbacks;
- fade state completion within the configured 500 ms budget;
- vertical gesture rejection and child horizontal-scroll precedence;
- file upload/download, APK extraction/install, Logcat save, screenshot save and existing screen-recording save/export each disable bottom/gesture navigation only after actual transfer/write begins;
- picker open/browse/cancel, copying, viewing, Shell and Logcat collection never create a navigation lock;
- system back priority and wait-for-cleanup behavior;
- background and Session-switch cancellation;
- normal back exits rather than page history traversal.

### Files

- 1,000-entry stable directory-first/time-descending sort;
- unknown timestamps last within each group;
- row body has no enter/download action;
- conflict, cancel, timeout, disconnect, cleanup and stale Session.

### Applications

- draft query vs applied search behavior;
- ordinary four-action order;
- system/unknown download-only policy in UI and core;
- base plus all splits complete extraction;
- injected component failure retains verified committed files, cleans only the current uncommitted temporary/resources, reports every component and produces partial success without atomic/rollback claims;
- none-committed failure/cancel, partial-success safe unlock, result-unknown ownership and stale Session;
- reject directory/multi-select/`.apks`/`.xapk`/isolated split/incomplete base;
- same-signature replace/downgrade confirmation;
- mismatch double confirmation and no request on cancel;
- system-policy rejection and remaining-base-package outcome;
- install failure after uninstall produces no-rollback result.

### Processes

- immediate process-name-only filter;
- exactly one refresh at a time;
- no more than 12 cycles in a 60-second virtual-time window;
- stop on hidden/background/disconnect/Session switch;
- identity/generation protection and termination scope choice.

### Shell

- no per-key remote send while composing a draft;
- no bytes/newline before high-risk confirmation;
- local Esc/Tab/arrows/history before submit;
- real-time semantic keys only in remote-active state;
- Ctrl/Alt clear after exactly one next input and every lifecycle reset;
- close child stream on leave without disconnecting main Session;
- same-Session records/history retention and new-stream separator;
- old stream generation output rejection.

### Logcat

- no collection before explicit Start;
- one collection per window;
- local text and exact severity predicates: Fatal matches `all/debug/info/error`, Warning matches `all/debug/info`, and no fifth Fatal control exists;
- export complete raw snapshot under every filter;
- leave stops and retains, return does not restart;
- explicit restart clears old window;
- 10-minute/10-MiB terminal boundaries;
- Session switch clears old snapshot.

### Pairing/connection

- error dismiss preserves input/discovery/Session;
- root overlay outside-tap/back cleanup;
- 30-second NSD observation and explicit retry;
- six ASCII digits plus explicit Pair only;
- no IME/change auto-submit or duplicate submit;
- secret clearing and single active attempt;
- screenshot exposes Save without incorrect recording/success text;
- screenshot and existing recording picker phases remain unlocked, while actual persistent export writing locks and safely unlocks at terminal cleanup.

### Localization and module ownership

- `:core:data` remains the only `ui_language_v1` persistence owner; `:feature:settings` remains the only user-facing writer;
- `:app` collects the language Flow once and passes one `UiLanguage` snapshot to App chrome, root overlays and every in-scope Feature Route;
- no AppCompat application locale, `LocaleManager`, `Activity.recreate()`, second preference or configuration-driven language switching;
- shared and every in-scope Feature-specific Chinese/English catalog have identical required key sets and placeholder schemas;
- missing English on a target surface fails instead of passing through Chinese fallback;
- target ViewModels retain semantic codes/typed arguments and do not depend on `UiLanguage` or store finalized prose;
- target screens do not directly render `AdbError.userMessage` or `nextStep`;
- fixed-English controls and verbatim technical/user/device values remain unchanged in both languages;
- changing language while each long task/stream/poll/pairing flow is active updates visible/accessibility text within one second while preserving ViewModel object, page, Session, task/stream/window/attempt IDs, navigation lock and ADB/SAF request counts.

## 6. Performance fixtures

Run performance-oriented deterministic fixtures separately from unit correctness:

- 1,000 mixed file entries with duplicate/unknown timestamps;
- 1,000 application/process rows with repeated filter changes;
- bounded Shell transcript near its configured limit;
- 10 MiB Logcat window with mixed levels and text filters.

Measure:

- 95% filter/search/click visible feedback at or below 100 ms on the designated test profile;
- stable keys preserve scroll and avoid full-list recomposition where measurable;
- no device request is caused by each filter keystroke;
- hidden pages create zero process polls and zero Logcat reads;
- refresh/stream jobs never overlap.

Record fixture/tool/device profile with results. Do not present host benchmark numbers as real-device UX evidence.

## 7. Visual acceptance

Use sanitized deterministic fixtures and both in-app languages.

For each of Files, Applications, Processes, Shell, and Logcat:

1. capture the compact page at a stable emulator/device size;
2. normalize to the reference PNG width/aspect while separating system-bar insets;
3. compare against `code.html`, then `DESIGN.md`, then `screen.png`;
4. check shared top/bottom chrome, content hierarchy, sizes, action order/placement, colors, typography roles, spacing, radii, and icons;
5. check loading, empty, error, disabled, confirmation, progress, unsupported and result-unknown variants;
6. record justified deviations only for spec-over-HTML controls, system bars, localization expansion, or actual device capability.

Expanded checks:

- no simultaneous bottom and permanent navigation;
- Shell expanded side navigation matches its HTML branch;
- Logcat expanded secondary pane matches its HTML branch;
- compact order/behavior remains unchanged.

Any missing design-present control or default Material spacing/radius mismatch is a failure, not a discretionary difference.

## 8. Real-device acceptance

Use three existing controlled-device profiles. Keep evidence sanitized:

- no real IP/endpoint;
- no pairing code or QR payload;
- no package-name context;
- no raw Shell or Logcat;
- no ADB/signing key material.

For each profile, validate:

1. connection, discovered paired target, QR/code pairing, local pairing, timeout/retry, outside dismissal;
2. file browse/breadcrumb/sort and upload/download success/cancel/conflict/disconnect;
3. app search, extraction, enable/disable, force-stop, uninstall, install, replace/downgrade, mismatch sequence, policy refusal;
4. process five-second metrics and both termination scopes where available;
5. interactive Shell programs, risk confirmation, six special keys, modifier reset, leave/return;
6. Logcat explicit Start, filters, leave/return, restart, clear, full-window save;
7. screenshot Save and existing recording export behavior;
8. all seven lock kinds, picker-unlocked phases, navigation/back/background behavior and partial-success unlock;
9. switch Simplified Chinese/English through the app setting during active operations without operation restarts, request duplication or Session/task identity changes.

Clearly label unsupported device capabilities. Do not substitute a simulated success state.

## 9. Version and release checks

Verify:

```text
versionCode = 4
versionName = 1.0
visible version = v1.0
```

Search source/resources/generated presentation for old or design-placeholder versions. Confirm the rendered menu/settings/about output derives from the build version.

Then audit:

- Manifest permission diff: no new permission;
- dependency diff: no unapproved dependency;
- runtime network behavior: no UI-resource request;
- license report and ADR 0004 gate.

The known `spake2-java:1.0.5` GPL-3.0-or-later issue remains a release blocker until formally resolved. Passing tests and producing an APK do not authorize publishing v1.0.

## 10. Documentation quality checks

Before handing off:

```powershell
rg -n "NEEDS CLARIFICATION|TBD|TODO|\[FEATURE\]|\[DATE\]" specs/007-v1-ui-refactor
git diff --check
git status --short
```

Confirm only task-related files changed. Report automated, fixture, visual, real-device, security, and release-compliance evidence as separate gates.
