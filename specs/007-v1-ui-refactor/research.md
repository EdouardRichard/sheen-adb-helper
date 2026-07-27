# Phase 0 Research: v1.0 全功能 UI 重构

**Date**: 2026-07-26
**Spec**: [spec.md](./spec.md)

## 1. Design baseline and precedence

### Decision

Treat the HTML/Tailwind sources as implementation specifications rather than inspiration. Apply this fixed precedence:

1. current feature spec and explicit clarifications;
2. the page's `code.html`;
3. `technical_terminal_systems/DESIGN.md`;
4. `screen.png` for visual comparison;
5. current Compose UI only for preserving authorized behavior.

When the spec adds a control missing from HTML, place it according to the surrounding HTML component grammar. When HTML conflicts with the spec, keep the HTML structure but use the spec's behavior or label. Examples include the fourth force-stop action on ordinary app rows, the APK-install icon instead of `+`, English Logcat level labels, and a download action in the Logcat toolbar.

### Rationale

This preserves all design-present controls while allowing explicitly required functionality and states to be completed without inventing a second visual system.

### Alternatives rejected

- Recreating the screen from PNG pixels: unsuitable for accessibility, localization, dynamic data, and responsive layouts.
- Reusing existing screens and only changing colors: does not satisfy the HTML structure and spacing contract.
- Embedding HTML/WebView: breaks the Compose-first architecture, accessibility expectations, local resource control, and Feature state ownership.

### Baseline fingerprints

These fingerprints define the design snapshot used during planning:

| Source | SHA-256 |
|---|---|
| `technical_terminal_systems/DESIGN.md` | `d8033b1f788bb576f724be3e7e0923385c2d35f539725153429b7b8ef1cfc878` |
| `文件管理页/code.html` | `6310a8f8b1f059037341d15bce0ef8d36cbc79551a514eb90fc180d614dc8974` |
| `文件管理页/screen.png` | `a8a27ce121e5bdef5103aa054b9de4edcc901d6cbe47bc0812934465d5fef0a6` |
| `应用管理页/code.html` | `b363e340a81e30f8c5bc40f4321817fc86e50be423a85f500f1f27d29eec815a` |
| `应用管理页/screen.png` | `156fce49f7b726b81ee06c254f7b45f9f0b03cdc856e1b9b3eefc74f88d2efc3` |
| `进程管理/code.html` | `fbf6894c48cf514d912f451acbdf0facd1759980eb0eb9de31129c856dfd2fa7` |
| `进程管理/screen.png` | `4e50ba1e448756a5bbe7f0ae47c7e088baf9e7d2af061c07f7521dd702930349` |
| `shell终端/code.html` | `904496fe105a7fcd41bbc1e8285256cde017407a3291b78b2ec54e9b2d334ec9` |
| `shell终端/screen.png` | `9fee6f5fbe0a0efded35da5c7342571ad070c95a1e695df6e07285c33a6750c3` |
| `logcat页/code.html` | `4df0aa18d1e44652b283c4f23a2dadfe3715a5f13bbd413e3e7e1ba38c52a393` |
| `logcat页/screen.png` | `2db73b1a7d2a36fc16d03cda14c832e6851c41ff52b1e63c83ddb585534ecd1e` |
| `连接页-未连接状态/code.html` | `b2d85b636a1f08dc2503be11cb72eaa1ac4cbe091865e956d7765489170c10b2` |
| `连接页-已连接/code.html` | `36a4e0f26ad199d0ccb1b0189612b95ed43124c81e433ce11bb172ae85624ac5` |

If any fingerprint changes during implementation, stop visual work, compare the new design with this snapshot, and update the feature artifacts before accepting the change.

## 2. Tailwind-to-Compose conversion

### Decision

Implement the HTML structure as native Compose:

- flex rows/columns → `Row`/`Column`;
- scroll areas and repeated records → `LazyColumn`, `LazyRow`, or bounded `LazyList`;
- fixed top/bottom utility regions → App-owned scaffold slots;
- Tailwind padding/gap/size/radius/color → typed values in `:core:ui`;
- HTML buttons → semantic `IconButton`/custom minimum-touch target primitives;
- terminal/log panels → black bounded surfaces using lazy line rendering and horizontal scroll where required;
- responsive `md:*` branches → Compose width-class branches without new navigation libraries.

Use stable keys for file, app, process, Shell, and Logcat records. Filtering and sorting operate on immutable in-memory snapshots and are memoized/derived; they do not issue device requests on each keystroke.

### Rationale

Native Compose preserves accessibility, localization, state ownership, and rendering performance while allowing the Tailwind structure to be mapped deterministically.

### Alternatives rejected

- Generic Material components with default padding/radius: defaults differ from the supplied HTML.
- A single vertical `Column` with `verticalScroll`: eagerly composes large lists and violates the 1,000-record performance goal.
- Remote Google Fonts or Material Symbols: violates local-only runtime requirements.

## 3. Fonts and icons

### Decision

Keep the typography roles, sizes, weights, line heights, and letter spacing from `DESIGN.md`, but use local platform sans-serif and monospace families already available to the app. Continue using and extend project-owned vector icons in `:core:ui`; add a dedicated APK-upload/install glyph composed from a package/device outline plus an inward/upward transfer arrow, never a plus sign.

Do not add Hanken Grotesk, JetBrains Mono, Material Symbols, a Web font loader, or a new icon dependency in this feature plan.

### Rationale

The HTML loads Google-hosted fonts and Material Symbols, but the spec forbids runtime remote UI resources. The repository's dependency policy only permits Apache-2.0/MIT/BSD licenses; bundling font files typically introduces a separate OFL license decision that has not been approved. Matching metrics and roles locally is the smallest compliant solution.

### Alternatives rejected

- Runtime font downloads: prohibited by FR-008/FR-052.
- Bundling font files without a license decision: violates dependency/license governance.
- Using emoji or text characters for actions: inconsistent rendering and weak accessibility semantics.

## 4. Navigation, fade transition, gestures, and long-task lock

### Decision

Keep one App-owned page index for `连接 → 文件 → 应用 → 进程 → 终端 → 日志`. Use Compose primitives to:

- detect an intentional horizontal gesture after direction lock and threshold;
- request only the adjacent page and clamp at the first/last page;
- render the accepted page change with `fadeOut` + `fadeIn`;
- converge rapid bottom-navigation or gesture requests to the latest accepted target without re-running page entry work;
- ignore bottom-navigation and horizontal gesture requests while a navigation-blocking task is active.

Do not use a horizontally sliding pager because the required transition is fade, not slide. App state aggregates a small `NavigationLock` contract from Features. System back first resolves overlays and dialogs; during a blocking task it shows continue/cancel-and-leave, awaits cancellation and cleanup, then exits. With no higher-priority UI, back exits from any main page rather than traversing page history.

Blocking tasks are exactly file upload/download, APK extraction/installation, Logcat save, screenshot save, and existing screen-recording save/export. A picker that is only open, browsed, or cancelled is not a lock; the owning Feature exposes the lock only after actual device transfer or host output writing begins. Copying text, viewing an artifact, the Shell child stream, and Logcat collection are not locks. Leaving their pages closes/stops lifecycle-owned work according to its own contract.

### Rationale

One owner prevents bottom state, gesture state, fade state, and lifecycle callbacks from diverging. Explicit gesture arbitration avoids stealing vertical list scrolls.

### Alternatives rejected

- `HorizontalPager` default motion: introduces a slide transition contrary to FR-003.
- Feature-owned navigation: creates circular coupling and inconsistent locks.
- Letting the user switch pages while a file/APK/output transaction is active: conflicts with the confirmed long-task rule and makes cleanup/results ambiguous.

## 5. Page visibility and retained state

### Decision

App supplies each Feature with visible/hidden and foreground/background events. In one unchanged Session:

- files retain path, snapshot, query if any, and scroll anchor;
- apps retain search query, snapshot, and scroll anchor;
- processes retain query, snapshot, and scroll anchor but resume 5-second refresh when visible;
- Shell retains bounded records and command history only in memory, closes its old child stream on leave, clears draft/live-input/modifier state, and inserts a separator before a new child stream;
- Logcat stops collection on leave but retains its bounded snapshot and filters; return never auto-starts.

All retained state is cleared on disconnect or Session ID change. App/background lifecycle cancels navigation-blocking tasks, closes Shell child streams, stops Logcat, and stops process polling.

### Rationale

This matches the clarified per-page lifecycle while avoiding redundant device reads and stale cross-Session data.

### Alternatives rejected

- Recreate all Feature ViewModels on every page change: loses state and repeats remote requests.
- Persist Shell/Logcat content: expands privacy and retention scope beyond the feature.
- Keep hidden streams running: violates performance and resource ownership requirements.

## 6. Files

### Decision

Reuse the existing browse, Sync transfer, SAF staging, conflict, progress, cancellation, lease, and cleanup pipeline. Add a pure presentation sort:

1. enterable directories;
2. non-directory files/links;
3. within each group, known modification time descending;
4. unknown modification time last;
5. ties preserve source order for stable output.

Only the trailing folder-enter or file-download icon is actionable. Breadcrumb segments are individual actions. Upload uses the floating trailing action from HTML and a system document picker.

### Rationale

The transport path already provides the critical transaction safety. The gap is deterministic in-memory ordering and exact Compose interaction semantics.

### Alternatives rejected

- Sorting on the device with a Shell command: violates ADB ownership and adds extra work.
- Making the entire row clickable: explicitly forbidden.
- Loading all file row composables eagerly: fails scale goals.

## 7. Applications, extraction, install, and uninstall

### Decision

Extend project-owned application contracts in `:core:adb` and orchestrate user-selected SAF sources/targets in `:feature:apps`.

- The application snapshot includes ordinary, system, and unknown classification plus display metadata.
- Ordinary rows show download, enable/disable, force-stop, uninstall in that order.
- System/unknown rows show only download; the exception for a same-package system app is reachable only from the explicit install workflow.
- Extraction queries all APK component paths. A single APK is one file; a split app creates one independent destination directory and attempts base plus every split as individually staged, verified, and committed files.
- Each component produces its own result. Complete success requires every component; if at least one verified file is committed and another fails or remains incomplete, retain the committed files and report overall partial success with every component result. If none commit, report failure/cancel as applicable.
- Cleanup removes the current uncommitted temporary and closes streams/leases; it does not delete already committed successful files. The set has no cross-file atomic commit or automatic rollback promise.
- Installation accepts exactly one independently installable `.apk` selected through SAF. Reject directories, multi-select, `.apks`, `.xapk`, isolated split APKs, and incomplete split-dependent bases.
- The install preflight identifies package/signature/version relationships. Same-signature force install may request replace/downgrade and preserves private data when the platform succeeds. Signature mismatch requires a second confirmation before full uninstall then install.
- The same signature-mismatch sequence may be attempted for a system package without Root or policy bypass. If Android only removes the current-user state, retains the base package, or rejects the operation, surface the precise structured outcome.
- If uninstall succeeds but installation fails, report a non-rollback partial outcome.

APK extraction and APK installation remain independent capabilities; no restore promise is made.

### Rationale

The contracts keep raw `pm`/package-manager Shell details and remote temp resources inside `:core:adb`, while SAF remains the user-mediated host storage boundary.

### Alternatives rejected

- Feature-layer `pm install`, `pm uninstall`, or `cat` commands: violates the core boundary.
- Installing extracted split sets: outside v1.0 scope.
- Silent uninstall on signature mismatch: destructive and contrary to the confirmed two-stage flow.
- Claiming rollback after new install failure: the old app/data cannot be recreated safely.

## 8. Processes

### Decision

Replace the three current query fields with one process-name query and derive visible rows immediately from the current snapshot. While the page is connected, foreground, and visible:

1. refresh immediately if no current snapshot;
2. await completion;
3. wait until the next 5-second cadence;
4. refresh again only if no request is active.

Stop the loop on page leave, background, disconnect, or Session change. Keep the existing stable process identity/generation checks and termination scope dialog. CPU/PSS unavailable values render as unknown.

### Rationale

The current manager already supports analyzed snapshots and safe termination, but the current screen only refreshes manually and exposes three filters. A single sequential loop guarantees no overlap.

### Alternatives rejected

- Fixed-rate launches independent of prior completion: can overlap slow device reads.
- Filtering by issuing remote commands: violates FR-051 and increases load.

## 9. Persistent interactive Shell

### Decision

Add a project-owned `InteractiveShellSession` contract to `:core:adb` with:

- expected Session ID;
- bounded output events;
- explicit byte/control input methods;
- child-stream activity state;
- cancellation/close with a bounded cleanup result;
- structured unsupported, timeout, disconnected, protocol, output-limit, and outcome-unknown errors.

The Feature maintains a local draft. Before submission, Esc/Tab/arrows edit or navigate local state and Ctrl/Alt arm one-shot modifiers; nothing is sent remotely. Submission classifies high-risk commands locally using the existing policy and sends no bytes, including newline, until confirmation. While a remote program is active, special keys are encoded by `:core:adb` and sent live; Ctrl/Alt modify only the next live input then clear.

Leaving, backgrounding, disconnecting, or switching Session closes only the child Shell stream, not the main ADB Session.

### Rationale

This is the minimum architecture capable of the clarified terminal semantics. One-shot `executeShell` cannot reliably support interactive programs or real-time control keys.

### Alternatives rejected

- Continue using one-shot commands: fails interactive-program requirements.
- Send each keyboard character immediately: bypasses high-risk confirmation.
- Encode terminal escape sequences in the UI: violates raw protocol/command ownership.

## 10. Logcat

### Decision

Separate the raw bounded collection window from the derived visible lines:

- user explicitly starts a new window;
- starting clears the prior window and opens one stream;
- raw lines are retained up to existing 10 minutes or 10 MiB, whichever comes first;
- text and level filters are pure local derivations;
- levels use these local predicates: `all` = every level; `debug` = Debug/Info/Warning/Error/Fatal; `info` = Info/Warning/Error/Fatal; `error` = Error/Fatal;
- delete clears the full raw window;
- download snapshots and writes the complete raw window regardless of active filters;
- leaving/background stops and closes collection but preserves the same-Session snapshot;
- returning does not restart; disconnect/Session change clears everything.

The accepted “recent history plus follow” behavior may be used only after the user presses Start; it must never cause automatic collection on page entry.

### Rationale

The current raw buffer and streaming boundary are reusable, but current Feature filter methods are no-ops, export uses visible text, and leaving clears state. Separating collection from presentation fixes all three without device requests per keystroke.

### Alternatives rejected

- Restarting Logcat on filter changes: wasteful and loses window identity.
- Exporting visible lines: contradicts the confirmed full-window requirement.
- Keeping collection alive offscreen: violates lifecycle and performance rules.

## 11. Pairing and connection fixes

### Decision

Use the existing NSD-only `_adb-tls-pairing._tcp` discovery architecture for “scan pairing port”; do not probe address ranges or arbitrary TCP ports. Each code-pairing attempt owns a new 30-second bounded NSD observation window. On discovery, enable the Pair action only for exactly six ASCII digits, and submit only on explicit button activation.

Render QR/code pairing as a root App overlay above the connection page. Outside tap, close, back, timeout, disconnect, or Session change cancels observation and clears QR/code/endpoint temporary state. The local-pairing outer lifecycle may retain its accepted longer safety window, but each visible “scan pairing port” attempt is an explicit, non-overlapping 30-second discovery cycle.

Closing a connection error only removes the visible error. Screenshot success shows the existing Save action without “recording” or extra success text.

### Rationale

NSD is already the approved discovery mechanism and requires no network scanning expansion. Root ownership ensures the pairing surface floats over the current app instead of being embedded in the page.

### Alternatives rejected

- Subnet or sequential TCP port scanning: violates the approved discovery boundary and expands network behavior.
- Auto-submit on the sixth digit or IME action: contradicts the explicit Pair requirement.
- Retaining the pairing secret after overlay close: violates sensitive-state cleanup.

## 12. Versioning and localization

### Decision

Set `versionName = "1.0"` and increment `versionCode` from 3 to 4. UI labels derive the visible form `v1.0` from BuildConfig rather than hard-coded design samples.

Retain the repository's existing in-app localization chain rather than adding Android application locales:

1. `:core:data` remains the sole persistence owner of `LanguagePreference` and `ui_language_v1`, with Simplified Chinese as the existing default/corruption fallback.
2. `:feature:settings` remains the only user-facing writer of that preference.
3. `:app` collects the preference once at the root, maps it to `UiLanguage`, and passes the current value as presentation input to App chrome, root overlays, and every target Feature route.
4. `:core:ui` owns `UiLanguage`, typed text/argument primitives, and shared chrome/common-state text. Each Feature owns its page-specific semantic keys, Chinese/English catalog, and error/state-to-key mapping.
5. ViewModels and reducers retain semantic state/error codes and typed raw arguments, never finalized localized prose. Compose resolves the current catalog during recomposition.
6. Language is never a ViewModel, navigation, Session, task, stream, window, picker, `LaunchedEffect`, or `DisposableEffect` identity key.

Do not call AppCompat application-locale APIs, `LocaleManager`, or `Activity.recreate()`: configuration recreation can trigger lifecycle stop paths that cancel or stop active work. No second language DataStore or `values-zh-rCN` source is introduced. All new/changed labels, content descriptions, confirmations, errors, states, and accessibility text in scope must have both catalog entries. Missing English entries are test failures rather than silently accepted Chinese fallback.

The Logcat labels remain literal `all`, `debug`, `info`, and `error` in both languages. Esc/Tab/Ctrl/Alt, paths, package/process names, commands, Shell/Logcat content, version strings, technical codes, and other user/device raw data remain unchanged; only surrounding presentation text is translated.

### Rationale

This satisfies the exact visible version, preserves Android's monotonic internal versioning, reuses the proven preference flow, and prevents state-changing lifecycle side effects from localization.

### Alternatives rejected

- Set `versionName = "v1.0"`: would produce duplicated `vv1.0` in existing BuildConfig-based surfaces.
- Keep versionCode 3: violates monotonic release identity.
- Store localized strings in ViewModel state: makes locale changes stale or operation-destructive.
- Android application-locale or Activity recreation: introduces a second locale mechanism and can stop active page work.
- Let every Feature collect DataStore independently: duplicates subscriptions and leaks persistence into presentation modules.
- Put every page-specific phrase in `:core:ui`: centralizes business semantics in a shared visual module.

## 13. Module ownership and dependency direction

### Decision

| Module | Owns in v1.0 | Must not own |
|---|---|---|
| `:app` | root language collection/distribution, page host, fade/gesture/navigation, root overlays, ActivityResult/SAF picker bridging where already required, Feature lock aggregation, back/background policy, object graph | page business rules, ADB commands, SAF commit policy, page-specific text catalogs |
| `:core:adb` | single Session, raw Shell/Logcat/PM/Sync, APK component discovery/read, Fatal parsing, capabilities, timeouts, cancellation and transport cleanup | localization, Compose, Android document providers, navigation |
| `:core:data` | `LanguagePreference`, DataStore, SAF input/output primitives, directory creation, per-file staging/verification/commit and temporary cleanup | ADB protocol, page state, navigation locks, translated text |
| `:core:ui` | `UiLanguage`, typed localization primitives/shared catalog, design tokens, local icons and shared accessibility primitives | Feature business state, persistence, ADB/data orchestration |
| `:feature:files` | file snapshot/sort/interactions, upload/download orchestration, task/result state, page catalog and lock description | raw Sync/Shell, App navigation |
| `:feature:apps` | app search/policy/confirmations, orchestration of `:core:adb` APK streams with `:core:data` outputs, per-component result/partial success, page catalog and lock description | raw package commands, cross-file atomic claim |
| `:feature:processes` | local filter, visible-only cadence, termination scope confirmation and page catalog | raw process commands, global polling |
| `:feature:shell` | draft/history/filter/modifier intent, risk confirmation and page catalog | escape-byte encoding or main Session ownership |
| `:feature:logcat` | bounded window, local text/severity predicates, export snapshot, page catalog and export lock | Logcat command construction or App navigation |
| `:feature:devices` | disconnected discovery/pairing/error presentation and page catalog | connected quick-action artifacts |
| `:feature:overview` | connected overview, screenshot/record/reboot workflow, artifact export state/catalog and export lock | root navigation or raw capture command |
| `:feature:settings` | language selection presentation and repository mutation request | second language source or page-language collection |

Only the existing project dependency `:feature:apps -> :core:data` is added. Core modules remain mutually independent; Features do not depend on one another; no new module, external dependency, permission, or ADR is required. Current-fact files under `docs/architecture/` remain unchanged until implementation is merged and evidenced.

### Rationale

The table follows ADR 0003's dependency direction and ADR 0008's connected quick-action ownership while preventing navigation, localization, storage, and ADB protocol responsibilities from collapsing into one module.

### Alternatives rejected

- Put navigation locks in `:core:data`: storage cannot know page ownership or navigation.
- Move connected quick actions into `:feature:devices`: contradicts ADR 0008 and the existing connected `:feature:overview` path.
- Add a cross-Feature coordinator module: unnecessary because `:app` already owns assembly and Feature lock aggregation.

## 14. Verification and release evidence

### Decision

Use four separate evidence classes:

1. automated unit/contract/build results;
2. deterministic fixture and performance results;
3. normalized visual comparison against the recorded design baseline;
4. sanitized real-device acceptance across three existing device profiles.

Do not store real IPs, pairing codes, package context, raw Shell/Logcat, signing data, or device secrets in artifacts. Treat the existing GPL transitive dependency as an independent release blocker until ADR 0004 is resolved.

### Rationale

Automated tests can validate state machines and layout contracts but cannot prove actual device compatibility, visual usability, or release licensing.

### Alternatives rejected

- Declare complete after `assembleDebug`: insufficient evidence.
- Commit raw device logs/screens containing identifiers: violates privacy rules.
