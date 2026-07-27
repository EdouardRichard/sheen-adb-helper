# UI Contract: HTML/Tailwind to Jetpack Compose

**Design root**: `D:\androidPorject\stitch_adb`

**Global design source**: `technical_terminal_systems/DESIGN.md`
**Baseline hashes**: [research.md §1](../research.md#1-design-baseline-and-precedence)

This contract is normative for implementation. It does not permit replacing an HTML control with a generic Material layout merely because both expose the same function.

Localization and module ownership are defined by [localization-module-contract.md](./localization-module-contract.md). This UI contract must not introduce Android application locales or page-local persistence.

## 1. Source and deviation rules

1. Keep every design-present mobile control, its order, hierarchy, alignment, and semantic icon.
2. Implement spec-required missing controls with the nearest matching HTML component grammar.
3. If the spec and HTML differ, the spec controls behavior/text; HTML controls surrounding structure and styling.
4. `screen.png` is a visual comparison reference, not a source of static sample data.
5. Design sample endpoints, package names, filenames, Shell text, and Logcat text are forbidden as runtime defaults, tests, or diagnostics.
6. Loading, empty, error, cancelled, disconnected, unsupported, unknown-result, confirmation, progress, and accessibility states must use the same tonal surfaces, outlines, spacing, and typography.
7. Runtime remote fonts, CSS, Material Symbols, images, or other Web assets are forbidden.

## 2. Global token mapping

The implementation must use project-owned typed tokens in `:core:ui`.

| HTML/DESIGN role | Compose contract |
|---|---|
| `background` | `#0B1326`, full app/root background |
| `surface` | `#111A2D` |
| `surface-container-lowest` | `#070E1D` |
| `surface-container-low` | `#111A2D` |
| `surface-container` | `#182235` |
| `surface-container-high` | `#222D40` |
| `surface-container-highest` | `#2D384C` |
| `surface-bright` | `#354156` |
| primary/cyan | `#7ADFFF` |
| primary container | `#00A5CF` |
| secondary/amber | `#FFCB68` |
| error/red | `#FFB4AB` / container according to DESIGN |
| terminal/log background | true black `#000000` |
| spacing base | 4 dp |
| compact page horizontal margin | 16 dp |
| expanded page horizontal margin | 24 dp |
| common gutter | 12 dp |
| minimum touch target | 44 × 44 dp, even when visible icon plate is smaller |
| radii | 2/4/6/8/12 dp and full circle exactly by DESIGN role |
| title role | 24 sp / 32 sp line height / weight 700 |
| section role | 20 sp / 28 sp / weight 600 |
| body large | 16 sp / 24 sp |
| body | 14 sp / 20 sp |
| terminal code | monospace 14 sp / 20 sp / weight 500 |
| compact code | monospace 12 sp / 18 sp |
| label | 10 sp / 12 sp / weight 700 / 0.05 em |

Font families use local platform sans-serif and monospace while preserving the above metrics. Any future bundling of Hanken Grotesk or JetBrains Mono requires a separate dependency/license decision and is not implicit in this feature.

## 3. Shared compact app chrome

### Top bar

- Height: 44 dp.
- Background/outline must match each page HTML.
- Left: menu icon with minimum 44 dp target.
- Center: current connected endpoint/device label in the HTML monospace role; disconnected state uses localized safe status.
- Right: link/disconnect semantic icon with minimum 44 dp target.
- Center content remains visually centered independent of unequal left/right text widths.

### Bottom navigation

- Fixed to the compact-window bottom, above system insets.
- Six destinations in canonical order: connection, files, applications, processes, shell, logcat.
- Icons, labels, selected color, indicator, and tonal background follow the supplied page HTML.
- Selected state derives only from `PageHostState.current`.
- During a navigation lock, controls remain visible, current selection unchanged, disabled semantics announced, and a task cancel entry remains available.
- Labels and content descriptions resolve from the root-provided `UiLanguage`; a language change must not replace `PageHostState`, page ViewModels, or the active lock.

### Expanded width

- At the existing project width breakpoint (700 dp unless implementation evidence requires the HTML `md` breakpoint to be represented separately), replace compact bottom navigation with the established permanent side navigation.
- Preserve page-specific `md` content from HTML where present: Shell side navigation and Logcat secondary process pane.
- Do not render both bottom and permanent navigation simultaneously.
- Compact normalized screenshots remain the release visual baseline; expanded layouts receive separate structural checks.

## 4. Files page

**Source**: `文件管理页/code.html`

Structure, in order:

1. shared top bar;
2. current-path bar on a tonal surface;
3. lazy file/folder list;
4. upload floating action;
5. shared compact bottom navigation.

Path bar:

- folder semantic icon followed by horizontally scrollable breadcrumb segments;
- 16 dp horizontal and 12 dp vertical content padding;
- every breadcrumb segment is actionable and has a minimum 44 dp target without visually inflating the text gap.

List:

- 16 dp horizontal page inset and 16 dp leading content inset from path bar;
- row/card gap and padding follow the HTML (12 dp internal row padding; compact 8 dp vertical rhythm);
- directory icon uses the amber/secondary role on the matching high tonal container;
- file icons use the HTML file-type semantics;
- directories appear before files; groups sort by modification time descending;
- row body has no click action;
- trailing directory chevron alone enters; trailing file download icon alone downloads;
- use `LazyColumn` and stable Session/path keys.

Upload:

- 56 × 56 dp floating action;
- primary-container fill, page HTML radius/shape;
- compact placement at the HTML lower-right offset, respecting navigation and system insets;
- icon expresses upload to the controlled device.

## 5. Applications page

**Source**: `应用管理页/code.html`

Structure:

1. shared top bar;
2. 16 dp-inset search block;
3. lazy application cards;
4. APK-install floating action;
5. compact bottom navigation.

Search:

- tonal low container, bright one-pixel outline, HTML radius, 8 dp internal padding;
- localized placeholder; Chinese exact text is `请输入应用名或包名。`;
- search icon is a 40 dp visible button with a 44 dp semantic target;
- typing updates the draft only; search icon applies the query.

Application cards:

- 16 dp internal padding, 4 dp vertical inter-card rhythm, HTML rounded outline;
- package name is line one in compact monospace;
- application name is line two in body-large;
- actions are right-aligned with no retained empty slots;
- visible action plate is 32 × 32 dp with 8 dp gaps and at least 44 dp touch targets through invisible semantics padding;
- ordinary order is exactly extract/download, enable/disable, force-stop, uninstall;
- system and unknown rows show only extract/download, shifted to the trailing edge.

Install action:

- 56 × 56 dp;
- compact right offset 16 dp and bottom offset 88 dp plus safe-inset adjustment;
- use a local vector depicting APK/package transfer into the controlled device;
- a plus sign is forbidden.

Confirmations/progress:

- may overlay the page but must use design tonal layers and exact action order;
- destructive confirmation text must include package/app identity and data-loss impact;
- two-stage mismatch install must visibly distinguish old-app uninstall and new-app install.
- split APK extraction must show the expected component set and per-component outcome; complete success, partial success, and none-committed failure use distinct semantic/visual states. Partial success retains committed files and does not present rollback or cleanup of those files.

## 6. Processes page

**Source**: `进程管理/code.html`

Structure:

1. shared top bar;
2. low-tonal search section with 16/12 dp surrounding spacing;
3. lazy process list;
4. compact bottom navigation.

Search:

- one field only;
- 48 dp visual height, HTML bright outline and 8 dp radius;
- no search action icon;
- input immediately filters process name from the current snapshot.

Rows:

- minimum visual height 72 dp;
- 16 dp horizontal and 8 dp vertical content padding;
- bottom divider rather than floating cards;
- process name in bold 16 sp role;
- CPU and memory appear beneath as separate compact chips using container fill, 8 dp horizontal/2 dp vertical padding, 4 dp radius, compact monospace text, and speed/memory local vectors;
- unavailable metrics show localized unknown, never `0`;
- trailing end-process action is a 40 dp red/error plate within a 44 dp touch target.

Refreshing must not change row identity or reset scroll for stable entries.

## 7. Shell page

**Source**: `shell终端/code.html`

The main terminal region is black and fills all space between utility bar, keyboard accessory, and navigation.

Utility bar:

- low/lowest tonal background with one-pixel boundary;
- 16 dp horizontal and 8 dp vertical padding;
- Clear and Filter use compact monospace 12 sp controls with 8/4 dp internal padding;
- Auto-scroll follows the HTML switch/control position.

Terminal:

- black surface, 16 dp content padding;
- local monospace 14 sp/20 sp role;
- records are lazy/bounded; filters do not recreate the stream;
- explicit system separators use the shared subdued/secondary grammar, not sample output.

Keyboard accessory:

- surface-container background and 8 dp padding/gap;
- button order exactly Esc, Tab, Ctrl, Alt, ArrowUp, ArrowDown, flexible spacer, keyboard-return;
- each button visible height 36 dp, minimum width/touch target 44 dp;
- Ctrl/Alt active state uses a clear HTML-consistent tonal highlight;
- return is the primary action and opens/focuses the IME without submitting an empty command.

The pending local command input must be visually integrated with the terminal/accessory grammar even though it is absent from the static sample; it may not push any design-present key offscreen.

## 8. Logcat page

**Source**: `logcat页/code.html`

Structure:

1. shared top bar;
2. 48 dp utility bar;
3. black lazy/horizontally scrollable log surface;
4. compact bottom navigation;
5. expanded-only secondary pane where the HTML defines it.

Utility bar order is exact:

1. immediate text filter;
2. `all`;
3. `debug`;
4. `info`;
5. `error`;
6. delete icon;
7. download icon.

Rules:

- level labels remain lower-case English in every locale;
- severity predicates are fixed: `all` includes every level, `debug` includes Debug/Info/Warning/Error/Fatal, `info` includes Info/Warning/Error/Fatal, and `error` includes Error/Fatal; no Fatal button is added;
- replace the HTML sample's conflicting last action with the spec-required download semantic while retaining its location and component styling;
- no start means the content area shows a design-consistent uncollected state and Start action;
- delete clears the full collection window;
- download writes the full snapshot, not visible filtered rows.

Log surface:

- black, 16 dp content padding;
- compact monospace 12 sp/18 sp;
- preserve line structure and allow horizontal scroll instead of wrapping protocol/log lines;
- severity colors may use existing semantic roles but must preserve readable contrast and never fabricate a level.

At expanded width, implement the HTML's secondary process pane without changing compact order or forcing a new remote request on each filter input.

## 9. Connection-page additions

Use the already-adapted connection HTML as baseline; only scoped repairs are authorized:

- add a trailing close icon to the visible error card;
- render QR/code pairing in a root overlay above the app page, not inline;
- overlay outside tap closes and cleans temporary state;
- transparent text action switches QR to code pairing;
- code/local pairing scan state shows exact Chinese `正在扫描配对端口` and an equivalent English string;
- after discovery, show a six-digit numeric field and explicit Pair action;
- screenshot success removes incorrect recording/extra success prose and retains the existing Save action.
- screenshot and existing recording destination pickers do not lock navigation; after a target is returned, the actual persistent write exposes the Overview-owned lock aggregated by App.

## 10. Interaction and accessibility contract

- Every icon-only action has a localized content description naming action and target.
- Visual icon plates smaller than 44 dp use an expanded, non-overlapping semantic hit target.
- Disabled/hidden policy is represented both visually and semantically; system/unknown app actions are absent, not merely transparent.
- Row non-action zones on Files have no click semantics.
- Navigation lock announces the active task and disabled navigation reason.
- Focus traversal follows visual order, including the exact toolbar/action order above.
- Dynamic progress/status uses polite live-region semantics; errors and destructive confirmations receive focus.
- Language changes re-resolve presentation catalogs only and do not recreate the Activity, Session or operations.
- Every target Route consumes the same root-provided `UiLanguage`; ViewModels expose semantic codes/typed arguments rather than translated prose.
- Target screens must not directly render `AdbError.userMessage` or `nextStep`; Feature presentation maps the structured error to its own semantic text key.
- Paths, package/process names, PIDs, commands, Shell/Logcat content, version and technical codes remain verbatim. `all/debug/info/error` and Esc/Tab/Ctrl/Alt remain the specified technical labels in both languages.
- Language must not be used as a `LaunchedEffect`/`DisposableEffect`, ViewModel, Session, navigation, stream, window, task, or picker identity key.

## 11. Visual acceptance

For each compact page:

1. render with deterministic sanitized fixture data matching the required state shapes, not copied design samples;
2. capture at the reference aspect/width and normalize system insets;
3. compare top/bottom chrome, major regions, action placement, tonal hierarchy, typography role, spacing, radius, and icon semantics;
4. record only intentional differences caused by system bars, localization expansion, unavailable capability, or spec-over-HTML overrides;
5. reject differences caused by Material defaults, missing design-present controls, altered order, or remote-resource fallback.
