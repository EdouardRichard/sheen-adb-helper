# Contract: Localization State and Module Ownership

This contract defines the v1.0 Simplified Chinese/English mechanism and its module boundary. It extends the repository's existing in-app language preference; it does not introduce Android application locales or a second localization state.

## 1. Non-negotiable invariants

- Supported presentation languages are exactly Simplified Chinese (`zh-CN`) and English (`en-US`) for this version.
- Missing or corrupt persisted preference keeps the existing Simplified Chinese fallback.
- A language change only re-resolves visible text, formatting and accessibility semantics.
- A language change must not recreate the Activity, App container, ADB manager, ViewModel, page host, Session, task, stream, Logcat window, pairing attempt or picker.
- A language change must not cancel, restart, duplicate or otherwise mutate an ADB/SAF operation.
- `all`, `debug`, `info`, `error`, Esc, Tab, Ctrl and Alt remain the specified literal labels in both languages.
- Paths, package names, process names, PIDs, commands, Shell/Logcat content, device/user-provided text, version strings and technical codes remain verbatim.

## 2. Canonical state flow

```text
:feature:settings user selection
  -> :core:data LanguagePreference / ui_language_v1
  -> Flow<LanguagePreference>
  -> :app root collects once
  -> UiLanguage mapping from :core:ui
  -> App chrome/root overlays + every target Feature Route
  -> owning Compose presentation resolves typed catalog
```

There is one persisted language owner and one root collector. Features do not read DataStore directly. Android `LocaleManager`, AppCompat application locales, configuration-based resource switching, `Activity.recreate()`, and a second language preference are prohibited for v1.0.

The existing app `strings.xml` may continue to contain platform-required static metadata such as `app_name`; it is not the v1.0 runtime language source.

## 3. Module ownership

| Module | Owns | Does not own |
|---|---|---|
| `:core:data` | `LanguagePreference`, `ui_language_v1`, Flow/write, default/corruption/clear semantics | `UiLanguage`, UI text, Compose, locale APIs |
| `:feature:settings` | language-selection UI event and repository mutation request | persistence implementation, second language state |
| `:app` | the only root Flow collection, mapping to `UiLanguage`, distribution to App chrome/root overlays/Feature routes | page-specific catalogs, translated business results, language-keyed ViewModels/effects |
| `:core:ui` | `UiLanguage`, typed text/argument primitives, shared chrome/common-state catalog, design/accessibility primitives | page business state, DataStore, ADB/SAF operations |
| in-scope target Features | page-specific semantic keys, complete Chinese/English catalog, state/error-to-key mapping and formatting; Settings is limited to its language control/save feedback | DataStore collection, raw ADB messages as final prose, another Feature's text, unrelated Settings-page redesign |
| `:core:adb` | structured type/stage/technical code/certainty and raw device data | language input, translated prose, UI resource/catalog keys |

Connected-page ownership remains split: `:feature:devices` owns disconnected discovery/pairing/error presentation, while `:feature:overview` owns connected overview and screenshot/record/reboot presentation. `:app` mounts both under the Connection destination and owns only the root overlay/navigation composition.

## 4. Typed catalog contract

Concrete Kotlin type names may be refined during task generation, but the following semantics are required:

```text
LocalizedTextRef(
  owner,
  semanticKey,
  typedArguments,
  rawArguments
)
```

- Shared navigation, top bar, common actions and common safe error/status text use the `:core:ui` catalog.
- Files, Applications, Processes, Shell, Logcat, Devices and Overview keep their in-scope page-specific keys/catalogs in their own modules; Settings coverage in this version is limited to the language selector and its save feedback.
- Chinese and English required-key sets must be identical.
- Format placeholder names, count and types must be identical between languages.
- Every icon-only action, disabled reason, progress state, confirmation and live-region announcement has the same coverage requirement as visible text.
- A missing English entry on a v1.0 target surface is a failing contract, even if a runtime safety fallback can display Chinese or a semantic key.
- Inline `if (language == ...)`, paired `localized(zh, en)` calls and hard-coded user-visible prose are migration inputs, not the final target-page mechanism.

## 5. Business-state separation

ViewModels and reducers expose:

- semantic state/result/error codes;
- typed formatting arguments;
- raw technical/user/device values explicitly marked as verbatim;
- stable task, Session, stream/window and page identities.

They do not expose finalized Chinese/English prose. Target pages must not directly render `AdbError.userMessage` or `AdbError.nextStep`; they map structured error type/stage/technical code to an owning Feature/shared semantic key, retaining the technical code separately when safe.

The selected language must not appear in:

- ViewModel factory keys;
- navigation destination/page keys;
- Session/task/operation/generation identities;
- `LaunchedEffect` or `DisposableEffect` keys that own business work;
- ADB/SAF request fingerprints;
- picker request identity.

## 6. Runtime switch behavior

When `ui_language_v1` changes:

1. `:app` receives the new preference value through the existing Flow.
2. The root maps it to `UiLanguage`.
3. App chrome, root overlays and currently composed Feature content recompose.
4. Each owner resolves the same semantic state against the new catalog.
5. Existing objects, input drafts, scroll anchors, visible page, navigation lock and operation generations remain unchanged.

The selected-language presentation must be visible within the SC-024 one-second limit. No new ADB request, SAF request, stream, poll, collection, navigation event or task start is permitted as a consequence of the language change.

Language changes during file transfer, APK work, process refresh, Shell interaction, Logcat collection/save, pairing, screenshot save or screen-recording export follow the same rule. A navigation lock keeps the same task identity while its label and accessibility explanation update.

## 7. Formatting and verbatim values

- Human-facing surrounding phrases, units and unknown/unavailable labels use the selected catalog.
- Locale-aware number/date formatting, if used, receives `UiLanguage` only in the presentation formatter and must not alter stored values or sorting.
- File paths, filenames supplied by the device/user, package names, process names, commands, raw Shell/Logcat lines and technical codes are inserted as escaped verbatim arguments.
- Technical values must never be translated, normalized into another identifier, or interpolated into a command.
- Sanitization, truncation and bidi-safe rendering happen before/at presentation without mutating the underlying raw value.

## 8. Verification contract

Automated checks must prove:

- `:core:data` retains one preference key, persists both values, falls back safely and does not alter profiles when language changes;
- `:core:ui` shared Chinese/English key sets and placeholder schemas are identical;
- every target Feature catalog has complete nonblank entries in both languages;
- fixed-English tokens return identical values in both languages;
- target screens do not directly render `AdbError.userMessage/nextStep`;
- target ViewModels contain no language dependency or localized final prose;
- no AppCompat/LocaleManager/recreate language switching is introduced;
- a root language Flow change updates all in-scope visible text/accessibility within one second while preserving ViewModel instances, Session/page/task/stream/window/attempt IDs and request counts;
- both-language screenshot/semantic checks cover compact and expanded layouts, including English expansion without clipping or control reordering.

Real-device evidence must change language through the app's existing setting, not by changing the device system language, and must keep all sensitive device data out of recorded artifacts.
