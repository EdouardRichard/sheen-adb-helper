# Quick Actions Contract

`:core:adb` owns capability detection, session binding, protocol commands, byte streams, timeouts, cancellation, lease and cleanup. Feature owns presentation and confirmation; `:core:data` owns app-private artifacts and SAF binary export. No layer outside `:core:adb` assembles raw shell or accesses Socket/TLS.

Typed capture operations accept a project-owned `AdbCaptureSink` and return capture metadata; reboot returns a typed result without an artifact. Every request contains the initiating `sessionId`; a stale ID fails structurally. Screenshot and recording source is the controlled device screen. Recording is video-only, one segment, max 5 minutes or 256 MiB first. Reboot requires confirmation and returns `ResultUnknown` when the expected disconnect follows a sent command.

One `QuickActionLease` conflicts with screenshot, recording, reboot, file transfer, APK extraction and logcat. Release is guaranteed in a non-cancellable cleanup block. Cancellation, timeout, stream close, no capability, invalid/empty artifact, limits, disconnect and stale session map to typed errors.

`:core:data` creates the bounded app-private artifact and project-owned `ArtifactSink`. `feature:overview` owns `QuickActionUseCase`; its internal adapter connects the two project-owned sink ports without exposing platform or raw stream types to ViewModel/UI, validates returned metadata, coordinates `SafBinaryExporter`, and exposes only an opaque artifact reference plus structured results.

The app constructs and injects this Use Case. It converts the user-selected `CreateDocument` result through the `:core:data` platform adapter into an `ExportDestination` and forwards that value; it does not perform capture, validation, export, or cleanup logic. Export uses PNG or MP4 MIME type and reports cancel, provider-write, conflict, space/IO and cleanup outcomes. Private artifacts are deleted on every terminal path and startup cleanup is idempotent. No state transition reconnects automatically.

Capability detection is independent for screenshot, screen recording and reboot and is bound to the current `sessionId`. Each capability reports `Supported`, `Unsupported`, `PolicyRejected`, `ProbeFailed`, or `Unknown`; UI must not send an unsupported request.
