# Contract: Navigation, Visibility, and Long-Task Lifecycle

## 1. Canonical navigation

The compact mobile order is immutable:

```text
Connection <-> Files <-> Applications <-> Processes <-> Shell <-> Logcat
```

Bottom navigation may select any destination. A valid horizontal gesture selects exactly one adjacent destination. Gestures cannot move left of Connection or right of Logcat.

`PageHostState.current` is the only selected-state source. Content, selected navigation item, accessibility announcement, and lifecycle visibility must never use separate destination variables.

## 2. Transition acceptance

A navigation request is accepted only if:

- no dismiss-priority overlay or confirmation consumes it;
- no `NavigationLock` is active;
- target is valid and different from current;
- gesture target is adjacent and within the endpoints.

On acceptance:

1. issue page-leave lifecycle to the old page once;
2. fade old content out;
3. swap the destination exactly once;
4. fade new content in;
5. issue page-visible lifecycle once;
6. settle in an interactive state within 500 ms.

Rapid requests converge to one explicit accepted target. A rejected or superseded request must not trigger remote work or duplicate lifecycle callbacks.

## 3. Gesture arbitration

- Observe a pointer sequence without consuming vertical scrolling initially.
- Lock direction only after movement exceeds touch slop and horizontal intent is dominant.
- Consume only the accepted horizontal gesture.
- Require a documented distance/velocity threshold before changing page; otherwise restore the current page with no lifecycle change.
- Inputs starting on horizontally scrollable child content (breadcrumbs, terminal/log horizontal scroll) must honor child consumption and must not force page navigation.
- Disable gesture recognition while an overlay, confirmation, or navigation lock is active.

## 4. Page visibility matrix

| Page | On visible | On hidden | Same-Session retained state |
|---|---|---|---|
| Connection | disconnected: resume presentation/discovery only as explicitly requested; connected: restore Overview/quick-action presentation | cancel transient overlay-owned wait; stop connected quick-action work by its existing lifecycle unless an export lock prevents leave | connection inputs/discovery snapshot and connected Overview state according to existing policy |
| Files | restore path/query/scroll/snapshot; do not auto full reload solely because of return | keep snapshot; blocking transfer prevents leave | path, latest snapshot, scroll |
| Applications | restore query/scroll/snapshot; do not auto full reload solely because of return | keep snapshot; active extraction/install I/O lock prevents leave | applied/draft query, latest snapshot, scroll |
| Processes | restore query/scroll/snapshot and start non-overlap five-second cadence | cancel wait/refresh loop | query, latest snapshot, scroll |
| Shell | append separator and open a new child stream | close child stream; clear draft/live/modifier | bounded records and history only |
| Logcat | display retained snapshot; never auto-start | stop stream and retain bounded snapshot | full bounded window and filters |

Disconnect or Session change clears every retained row above. Background stops/cancels lifecycle work even if the destination does not change.

## 5. Navigation-blocking tasks

Blocking kinds:

- file upload;
- file download;
- APK extraction;
- APK installation, including mismatch uninstall/install sequence;
- Logcat save;
- screenshot save;
- existing screen-recording save/export.

Lock activation:

| Operation | Unlocked phase | Lock begins |
|---|---|---|
| file upload | source picker open/browsed/cancelled; selected source validation before transfer | actual device upload starts |
| file download | destination picker open/browsed/cancelled; target preparation before transfer | actual device download/output stream starts |
| APK extraction | destination picker and component discovery/preparation | first component transfer/output write starts |
| APK installation | source picker and local/preflight validation | device staging/install transfer starts |
| Logcat save | immutable snapshot creation and destination picker | selected target write starts |
| screenshot save | retained artifact view and destination picker | selected target write starts |
| screen-recording save/export | retained artifact view and destination picker | selected target write starts |

A system picker temporarily covering the Activity is not treated as an active blocking task or as App background cancellation. If the picker is cancelled, no lock is created and no task cancellation is synthesized.

While active:

- bottom navigation is disabled;
- horizontal page gestures are ignored;
- current destination/selection cannot change;
- duplicate start actions are disabled;
- visible progress/status and a cancel entry remain;
- App background, disconnect, or Session change requests cancellation and cleanup.

Shell stream, Logcat collection, process polling, screenshot/recording capture before the persistent export phase, short app mutation, copying, result viewing, and pairing observation are lifecycle-owned but not navigation locks. They stop/cancel when their page/overlay loses ownership under their existing contracts.

Business delivery and cleanup certainty remain separate. Complete success, safe partial success with intentionally retained committed files, safe failure, and safe cancellation release the lock. Cancellation timeout, resource ownership uncertainty, or incomplete cleanup retains it; intentionally retained partial APK files do not count as incomplete cleanup.

## 6. System back priority

Priority from highest to lowest:

1. close or cancel the active root pairing overlay and clear sensitive state;
2. dismiss/cancel the active operation confirmation without sending a request;
3. if navigation-locked, show the long-task back confirmation;
4. otherwise exit the application from any main page.

Long-task confirmation actions:

- **Continue task**: close confirmation, remain on the same page, keep task running.
- **Cancel task and leave**: request cancellation, remain visible/locked through cleanup, then exit only after a safe cancelled terminal state.

Cancellation timeout, outcome unknown, or incomplete cleanup leaves the App on the current page with an explicit error and lock ownership. It must not navigate or exit through the feature back path.

## 7. App background and process lifecycle

When the App enters background:

- cancel navigation-blocking tasks and begin cleanup;
- close active Shell child stream without disconnecting the main Session;
- stop Logcat and retain the same-Session window;
- stop process polling;
- cancel pairing observation and clear secrets;
- prevent delayed callbacks from reopening overlays or switching pages.

Foreground return restores presentation only. It does not auto-restart a cancelled transfer, Shell child stream on a hidden page, Logcat collection, pairing scan, or completed navigation.

Launching a system document picker is excluded from this background rule until it returns a target and actual I/O begins. A picker return is accepted only if its owner/page/Session generation is still current.

## 8. Session switch safety

Every lifecycle callback and result carries expected Session ID plus operation/page generation. A state reducer accepts it only if both match current ownership.

On Session change:

1. mark old page and task generations stale;
2. cancel/close old owned work;
3. clear old snapshots, queries/history/windows as specified;
4. initialize disconnected/new-Session state;
5. never replay a pending old navigation or confirmation against the new Session.

## 9. Testable invariants

- One visible destination, one selected nav item, one page-visible owner.
- At most one transition, root overlay, navigation lock, Shell child stream, Logcat collection, process refresh request, and pairing observation of each owned kind.
- No page change while locked.
- No lock while a file picker is only open/browsed/cancelled.
- Every listed transfer/output operation locks from actual I/O through safe terminal cleanup, including Logcat and Overview exports.
- Safe APK partial success unlocks while retaining and reporting committed files.
- No device request from rejected/superseded navigation.
- No automatic backward traversal across main pages.
- No stream/polling after its visibility owner ends.
- No old-Session result can mutate new-Session UI.
