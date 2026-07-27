# Contract: ADB and Feature Boundaries

This document defines ownership and semantic ports, not public API syntax. Concrete Kotlin names may be refined during task planning, but responsibilities and lifecycle guarantees are normative.

## 1. Ownership boundary

`:core:adb` exclusively owns:

- ADB Socket/TLS/session access;
- Kadb objects and compatibility handling;
- pairing protocol and ephemeral secret handling;
- raw Shell commands and command escaping;
- terminal byte/control-sequence encoding;
- Logcat command/stream setup;
- package-manager/APK remote commands and remote temp files;
- Sync reads/writes and remote path handling;
- operation timeout, cancellation, structured error conversion, and transport cleanup.

Feature/UI modules must not:

- construct `pm`, `am`, `kill`, `logcat`, or arbitrary transport commands;
- access sockets, endpoints, TLS keys, Kadb streams, or raw pairing material;
- infer Session identity from a visible endpoint;
- convert terminal semantic keys to escape bytes;
- expose raw protocol exceptions to presentation.

## 2. Common operation envelope

Every device operation accepts an expected opaque Session ID and returns one of:

- success with a result carrying the same Session ID;
- structured failure with operation stage, stable technical code, retryability, and outcome certainty;
- cancelled.

Every streaming/long operation additionally provides:

- one owner and one active job/stream;
- bounded inactivity/overall timeout appropriate to the operation;
- cancellation that closes the underlying transport;
- a cleanup completion result;
- stale-event rejection by Session ID plus operation/stream generation.

## 3. Interactive Shell port

Semantic operations:

- `openInteractiveShell(expectedSessionId)` → owned child-stream handle or structured unsupported/failure;
- observe bounded output events and child state;
- `sendSubmittedCommand(completeCommand)` only after Feature confirmation;
- `sendTerminalInput(semanticInput)` for live remote program input;
- `close(reason)` → cleanup result.

The handle:

- never owns/disconnects the main ADB Session;
- allows only one child stream for the Shell page at a time;
- encodes Escape, Tab, arrows, Ctrl/Alt-modified input inside core;
- clears/cancels pending writes on close;
- closes on page leave, App background, disconnect, or Session change;
- classifies timeouts, unsupported shell transport, disconnected, protocol failure, output limit, and outcome unknown.

The Feature owns draft editing, command history, filter, auto-scroll, high-risk confirmation state, and one-shot modifier intent. It does not own byte encodings.

## 4. Application snapshot and mutation port

Snapshot operation returns:

- current user ID;
- package identity;
- localized-safe display-name fallback inputs;
- ordinary/system/unknown classification;
- enabled state and available version metadata.

Direct mutation operations:

- enable/disable ordinary app;
- force-stop ordinary app;
- uninstall ordinary app for the current user with private-data deletion semantics.

All mutations require a nonce-bound request with Session, user, package, expected observed state/generation, and required risk acknowledgements. Results distinguish verified success, request accepted/outcome unknown, policy rejected, timeout, unsupported, disconnected, and failed.

System/unknown direct mutation requests are rejected by core policy even if UI policy regresses.

## 5. APK extraction port

Core responsibilities:

1. resolve an application's installed APK components without exposing raw command construction;
2. classify one base component and zero or more splits;
3. provide bounded Sync transfer for each component under one APK-extraction exclusive lease;
4. report bytes/progress, component identity, and verification capability;
5. close readers and release lease on every terminal path.

Feature + `:core:data` responsibilities:

1. obtain a user-selected destination;
2. create a dedicated directory for split applications;
3. create a temporary output with a safe unique name for the current component;
4. stream each component through the core port, then verify and commit that file independently;
5. retain every verified committed file even if another component fails;
6. clean the current uncommitted temporary and close streams/leases on failure/cancellation;
7. return one result per expected component and derive complete success, partial success, none-committed failure/cancel, or outcome unknown;
8. report cleanup/resource uncertainty honestly without calling retained committed output a cleanup failure.

The component set has no cross-file atomic transaction or automatic rollback. Complete success requires every base/split component; partial success requires at least one committed component and at least one failed, cancelled, or not-attempted component. Already committed files are not deleted to simulate rollback.

No restore/install contract is implied by extraction output.

## 6. Single APK install port

Input envelope:

- expected Session ID;
- user-selected `InputStream` supplier and optional bounded size;
- immutable source fingerprint/metadata from SAF;
- explicit options only after the matching confirmation state.

Preflight returns semantic facts without signing secrets:

- standalone APK acceptance/rejection;
- package/version identity;
- installed package presence and ordinary/system/unknown class;
- same/different/unknown signature relation;
- replace/downgrade feasibility when detectable.

Install execution:

- accepts only a single independently installable APK;
- stages/transfers through core-owned resources;
- supports standard install or confirmed replace/downgrade;
- never uses Root or permission/policy bypass;
- verifies installed outcome where possible;
- cleans remote staging in `NonCancellable` bounded cleanup.

Signature mismatch sequence:

1. no uninstall before explicit second confirmation;
2. execute current-user/full uninstall semantics available to the platform;
3. verify what was actually removed;
4. attempt new install only if the confirmed state machine allows it;
5. if install fails after removal, return `oldRemovedNoRollback`;
6. if a system base package remains or policy blocks replacement, return an explicit policy/remaining-package failure.

## 7. Files port

Continue the existing contracts for directory listing, upload preparation, Sync transfer, conflict policy, commit, cleanup, progress, and exclusive file lease.

Additional presentation invariants do not enter the protocol:

- sorting is local;
- only icon actions initiate enter/download;
- breadcrumb navigation requests the selected absolute-path handle;
- every snapshot and transfer receipt is Session-bound.

## 8. Processes port

Continue the existing analyzed refresh and termination contracts:

- refresh accepts expected Session and returns a snapshot generation;
- each entry has stable observed identity needed to reject PID reuse;
- whole-app termination requires a reliable package association and confirmed process set;
- single-process termination targets the confirmed identity;
- a stale generation or Session is rejected.

The five-second cadence and search filter are Feature responsibilities; raw process commands remain core-owned.

## 9. Logcat port

Core creates one bounded stream only after explicit Start. The port:

- may provide accepted recent-history-plus-follow semantics after Start;
- emits raw lines with normalized severity when available, including a distinct Fatal value;
- enforces/collaborates with the 10-minute and 10-MiB limits;
- closes promptly on stop/leave/background/disconnect;
- never restarts due to text or level filters.

Feature owns the raw in-memory window, derived filters, retained stopped snapshot, and export snapshot selection. Its severity predicates are: `all` includes every level; `debug` includes Debug/Info/Warning/Error/Fatal; `info` includes Info/Warning/Error/Fatal; `error` includes Error/Fatal. `:core:data` owns user-selected output staging/commit. Export receives the complete immutable window, never `visibleRecords`.

Picker browsing/cancellation and Logcat collection are not navigation locks. `:feature:logcat` exposes an output-write lock only after the selected target is returned and `:core:data` begins writing.

## 10. Pairing/discovery port

Pairing-code “port scan” means bounded NSD discovery of the approved ADB TLS pairing service. The contract:

- starts a new 30-second observation with a unique attempt ID;
- never overlaps a retry with the previous observation;
- resolves only verified service observations;
- exposes an opaque endpoint handle, not a UI-built host/port command;
- submits exactly six ASCII digits only after an explicit Feature action;
- owns and zeroes pairing secret buffers;
- cancels on overlay dismissal, background, timeout, disconnect, or Session change.

Subnet probes, sequential TCP port scans, host `adb`, and UI socket access are outside the contract.

## 11. Exclusive operation and navigation-lock separation

The existing single ADB Session remains the authority. At minimum these operations require exclusive leases:

- file upload/download;
- APK extraction;
- APK install staging/execution;
- any core-owned device transaction that would conflict with the above.

An ADB exclusive lease and an App navigation lock are related but different contracts:

- `:core:adb` owns device-operation leases and never knows the current page.
- `:core:data` owns SAF write/stage/verify/commit/cleanup results and never knows navigation.
- the corresponding Feature maps actual transfer/output-write phases to its project-owned delivery/lock state;
- `:app` aggregates Feature lock state and alone disables navigation, handles back confirmation, and applies background policy.

The full navigation-lock set is file upload/download, APK extraction/install, Logcat save, screenshot save, and existing screen-recording save/export. It starts at actual device transfer or persistent output writing, not while the picker is open. A purely local Logcat/screenshot/recording export does not acquire an ADB lease merely because it is a navigation lock.

Short app mutations and process termination still serialize through Session operation ownership but do not automatically become navigation locks. Logcat collection and Shell have dedicated stream ownership and are stopped on page leave.

If a lease cannot be acquired, return a structured busy result. Never queue an invisible duplicate operation from rapid UI actions.

## 12. Localization boundary

`:core:adb` exposes structured error type, stage, stable technical code, retryability, certainty, and raw technical data. It does not accept `UiLanguage`, choose Chinese/English, or expose a localized string as the target-page contract. Feature reducers map structured results to semantic presentation keys; Compose resolves those keys from the current language.
