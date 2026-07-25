# v0.1 Plan Validation Quickstart

## Environment

```powershell
$env:JAVA_HOME = 'C:\Users\Richard\.gradle\sheen-jdk21'
```

Run the controller app only on Android API 30+. Use the current three sanitized controlled devices, including the one below Android 10; the older device is a controlled-device compatibility sample, not a controller target. Do not record real IPs, keys, pairing codes, Shell output, Logcat, screenshots, or recordings in evidence.

Before UI verification, read `D:\androidPorject\stitch_adb\technical_terminal_systems\DESIGN.md`, then compare the Compose implementation with the `code.html` and `screen.png` files under `连接页-未连接状态`, `连接页-已连接`, and `菜单页`. The HTML + Tailwind structure is the primary conversion source; do not run it in a WebView or copy its CDN dependencies.

Before implementation and visual acceptance, verify the SHA-256 baselines recorded in `plan.md`. Stop and re-baseline with the project owner if any source differs or any external design source is unavailable; hashes alone are not a substitute for the source content. Verify unconnected/connected pages at 468×1060 and the drawer at 468×1046 after width normalization.

## Focused verification

```powershell
.\gradlew.bat --no-parallel --no-daemon `
  :core:adb:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :feature:devices:testDebugUnitTest `
  :feature:overview:testDebugUnitTest `
  :feature:settings:testDebugUnitTest `
  :app:testDebugUnitTest
.\gradlew.bat --no-parallel --no-daemon :app:lintDebug :app:assembleDebug
```

Inspect merged Manifest and dependency graph for absence of storage/media/camera/microphone/MediaProjection permissions and third-party media dependencies.
Also scan runtime URLs and packaged resources to confirm Google Fonts, Tailwind CDN, and Material Symbols CDN are not used.

## Manual acceptance sequence

1. Confirm the APK reports `versionName 0.1.0` and `versionCode 3`; fresh start shows six bottom entries, closed drawer and Chinese default.
2. Drawer history preserves reconnect/rename/delete; About shows the GitHub link; Settings switches English and Chinese immediately.
3. Unconnected page supports IP:port, 10-second scan, pull-to-refresh, device selection, QR, 6-digit and local pairing.
4. Connected page shows endpoint, status, overview cards and unknown-value rendering. Screenshot exports a controlled-device frame to the controller via `CreateDocument`; controller screen is never the source.
5. Recording cancels cleanly, stops at 5 minutes/256 MiB first, exports one video-only MP4 segment and never starts a second segment.
6. Confirm reboot; controlled-device disconnect is requested/unknown, no automatic reconnect occurs, and manual reconnect works.
7. Force session replacement, cancellation, timeout, SAF denial and cleanup failure; verify localized errors, no stale progress, lease release and idempotent next-start cleanup.
8. Normalize controller screenshots to 468px width and compare unconnected/connected at 468×1060 and drawer at 468×1046; separately record system-bar, font/icon fallback, accessibility, and controlled-device capability differences.
9. On all three controlled devices, verify connection and independent screenshot/record/reboot capability results. For each device reporting screenshot support, complete three captures/exports within 10 seconds each; for each device reporting recording support, complete three normal short recording/exports. An explicit unsupported result on the below-Android-10 device is a correct degradation, not a failed acceptance.
10. Cover 5-minute and 256 MiB recording limits with automated boundary tests; do not require a single user to perform impractical repeated long-duration manual runs.

## 2026-07-24 UI convergence evidence

- Rechecked all seven design-source SHA-256 values against `plan.md`; no drift was found.
- Ran the focused unit-test suite plus `:core:ui:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` with JDK 21, `--no-parallel`, and `--no-daemon`; all completed successfully. The generated XML set contained 78 reports and no non-zero failure/error count.
- Installed the debug APK on the `Pixel_10_Pro` API 36 emulator. The app established a real loopback TCP/IP ADB session after platform authorization; the connected screen therefore used session-provided overview values rather than preview fixtures.
- Captured and inspected sanitized normalized renders:
  - `app/build/ui-evidence/disconnected-468x1060.png`
  - `app/build/ui-evidence/connected-468x1060.png`
  - `app/build/ui-evidence/drawer-468x1046.png`
- The Compose conversion matches the reviewed HTML/Tailwind structure for dark color hierarchy, 4dp rhythm, outlined rounded cards, 2×2 connected metrics, three quick actions, three disconnected pairing actions, six-item bottom navigation, and the fixed-width drawer hierarchy.
- Intentional runtime differences from the static sample are evidence-driven: an empty scan/history shows the empty state instead of invented devices; unavailable CPU utilization shows core count and ABI without inventing `65%`; controller-provided status/gesture bars remain inset-safe; system fonts and repository-local vector icons replace external Google Fonts and Material Symbols CDN glyphs. These differences do not change placement or semantics of the required controls.
- The emulator evidence validates layout and one real ADB session only. It does not replace T070 acceptance on the user's three controlled devices, especially capability degradation on the device below Android 10.

### Connected metric-card correction

After a second source-and-render comparison, the four overview cards were corrected to follow the connected-page HTML flex rules rather than merely sharing equal outer dimensions. The processor uses a dedicated chip icon with its ring centered and no extra ABI row. RAM and storage use distinct icons, independently aligned left/right values, and bottom-anchored progress bars. Battery uses the charging-battery icon, keeps charging state at the upper right, and groups percentage plus temperature at the lower left. `app/build/ui-evidence/connected-metrics-468x1060.png` is the normalized post-fix render from a real emulator ADB session. Because the protocol does not expose CPU utilization, the ring truthfully displays core count without inventing the sample design's `65%` amber progress.

### Quick-action runtime thread-safety correction

The latest installed v0.1 debug APK was reproduced on an API 36 emulator through a real local TCP/IP ADB session. The recording crash was a `NetworkOnMainThreadException` while opening the controlled-device recording stream. Reboot capability probing had the same caller-thread defect; that exception was reduced to a failed probe and then incorrectly surfaced as `ADB_DEVICE_REJECTED`, so it was not evidence of controlled-device policy rejection.

The capability probes and recording/reboot stream operations now run on the injected I/O dispatcher. Cancellation is preserved, and stream cleanup runs in a non-cancellable I/O context. The dedicated red-green regression suite verifies probe execution plus recording and reboot stream lifecycle thread ownership.

After installing the corrected APK, clicking recording kept the same app process alive for the observation window and produced no new fatal crash. The short emulator run returned without retaining a recording artifact. Reconnecting, confirming controlled-device reboot, and observing the emulator restart produced neither a fatal crash nor an `ADB_DEVICE_REJECTED` message. This emulator evidence validates the reported regression path; T070 remains the separate three-controlled-device acceptance gate.

### Controlled-device MP4 output correction

The next runtime attempt no longer crashed but returned `ADB_UNKNOWN`. The latest device timeline showed the exact cause: SELinux denied `screenrecord` access to `/proc/self/fd/1`. This controlled-device build documents `screenrecord` as an MP4 file writer, so treating its standard output descriptor as the destination was not portable.

Recording now writes to a uniquely named file under the controlled device's shell-owned temporary directory. The shell command removes stale app-prefixed files before starting, installs exit/signal cleanup, records the bounded MP4, streams that file through the existing ADB protocol only after successful completion, and removes the remote temporary file. The ADB operation timeout retains the request's cleanup/export grace period instead of expiring at exactly the recording duration.

The recording command also persists only its own numeric remote process ID in an app-prefixed shell temporary file. Normal completion or cancellation sends a separate cleanup command on the I/O dispatcher that terminates only that owned process and removes its PID/MP4 files. If Android force-kills the controller process before its coroutine can clean up, the next recording performs the same scoped cleanup before starting; it never uses an unscoped `pkill screenrecord`.

On the API 36 emulator, a one-second protocol-equivalent probe exited successfully, produced a positive MP4 byte count, and left no remote temporary file. The rebuilt APK was then installed and connected through a real local TCP/IP ADB session. Five seconds after tapping recording, the same app process and the owned remote `screenrecord` process were alive, the UI had not shown `ADB_UNKNOWN`, and the crash buffer had no new fatal entry. Disconnecting from the app cancelled that recording; the owned remote process stopped and the app-prefixed PID/MP4 count returned to zero. No recording content was retained. The command contract and full related Gradle verification cover this correction; three-device acceptance remains T070.

### Interactive screen-record completion

The remaining freeze was reproduced as a full-lifecycle defect rather than treated as a UI symptom. Recording held one long-lived ADB shell stream while the connected-page overview polling continued to issue Session work, and the page had no stop action. The device timeline ended in a native mutex abort on a dispatcher thread. Recording now starts with a bounded command, reports elapsed time and remote size through short status polls, accepts a typed stop request bound to the active Session, waits for `screenrecord` to finalize, and only then opens one stream to transfer the MP4. Overview loading and polling pause for that exclusive recording interval and resume after its artifact reaches a terminal state.

The TDD sequence first added failing typed-contract, protocol/Session, use-case/reducer, and ViewModel lifecycle tests. The implementation then made the middle quick-action button switch between recording and stop without changing the three-button connected-page layout. Repeated stop requests are idempotent, other quick actions remain disabled, and stopping preserves the original artifact through the export state.

The rebuilt APK completed a real local API 36 emulator flow: start recording, remain responsive with the same app process, stop after a short interval, finalize one MP4, open `CreateDocument`, and save to the controller-selected location. The exported file was non-empty, had an MP4 `ftyp` header, and the platform media tool decoded 18 AVC frames successfully. The crash buffer contained no matching fatal entry, the owned remote PID/MP4 count and app-cache MP4 count were both zero after export, and the generated acceptance recording was removed after validation.

The final regression command ran `:core:adb:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:feature:overview:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` with the required JDK and Gradle flags. All 406 unit tests, lint, and debug assembly passed. This is automated plus one emulator Session evidence; it does not replace the outstanding three-controlled-device T070 acceptance, including the below-Android-10 compatibility sample.
