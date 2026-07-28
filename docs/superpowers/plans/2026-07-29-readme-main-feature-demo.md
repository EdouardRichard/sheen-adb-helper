# README Main Feature Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an isolated Android 16 controller/Android 12 target emulator demonstration, capture privacy-safe evidence of the main product journey, and replace the repository README with a concise user-facing introduction.

**Architecture:** Treat the emulator network, product demonstration, screenshot set, and README as four sequential evidence layers. Host ADB may prepare and observe the two explicitly selected emulators, but pairing, authentication, discovery, and the business connection must be performed by Sheen ADB 助手 itself.

**Tech Stack:** PowerShell, Android Emulator, host ADB with explicit serials, Windows TAP adapters and Network Bridge, Android Studio/Windows UI automation, Markdown, PNG.

## Global Constraints

- Active feature is determined only by `.specify/feature.json`: `specs/007-v1-ui-refactor`.
- Android 16 is the controller; Android 12 is the controlled target.
- Each emulator uses a unique console port and a unique TAP adapter.
- Do not use host `adb connect` or `adb pair` as product-equivalent operations.
- Every host ADB command must specify `-s <serial>`.
- Do not expose real IP addresses, dynamic ports, pairing codes, QR payloads, keys, package-name context, Shell text, or Logcat text.
- Do not modify application code, Manifest, permission matrix, dependencies, ADRs, or active Spec Kit artifacts.
- Do not commit, push, reset, checkout, clean, delete AVDs, delete TAP adapters, or remove user data.
- Emulator evidence is not real-device acceptance and does not remove the existing release-license blocker.

---

### Task 1: Resolve the Exact Emulator Toolchain and Test Assets

**Files:**
- Read: `.specify/feature.json`
- Read: `specs/007-v1-ui-refactor/tasks.md`
- Modify: none

**Interfaces:**
- Consumes: local Android SDK, AVD, Java, TAP, Network Bridge, and running-process state.
- Produces: process-local values for SDK tools, Android 16/12 AVD names, console ports, serials, and TAP names.

- [ ] **Step 1: Discover Android SDK and Java without guessing**

Run read-only discovery through environment variables, `where.exe`, Android Studio configuration, and known Gradle JDK configuration. Resolve existing `emulator.exe`, `adb.exe`, and Java 21 paths; do not write resolved absolute values into repository files.

- [ ] **Step 2: Enumerate the real AVD names**

Run:

```powershell
& $emulatorExe -list-avds
```

Inspect each candidate with its existing AVD configuration and select exactly one Android 16 AVD and one Android 12 AVD. Do not infer the Android version from the AVD name alone.

- [ ] **Step 3: Resolve unique ports, serials, and TAPs**

Use Android 16 on one free even console port and Android 12 on a different free even console port. Derive each host serial as `emulator-<consolePort>`. Assign two distinct existing test TAP adapters and confirm no selected port belongs to an unrelated running emulator.

- [ ] **Step 4: Record only a sanitized preflight conclusion**

Keep GUIDs, interface indexes, IP addresses, dynamic ports, and process IDs in the current process only. User-facing evidence may state only that the required SDK, AVDs, Java, ports, and isolated adapters were resolved.

### Task 2: Establish the Isolated TAP Network

**Files:**
- Modify: none

**Interfaces:**
- Consumes: resolved TAP names and the existing dedicated Windows Network Bridge.
- Produces: two up TAP adapters in the same isolated bridge with no public, corporate, VPN, or ordinary physical adapter bridged.

- [ ] **Step 1: Inspect adapter and bridge membership**

Use read-only Windows adapter and binding inspection to confirm that the selected TAPs and the dedicated Network Bridge are the intended test resources.

- [ ] **Step 2: Repair only explicitly identified test bindings if required**

If Windows did not persist a selected test TAP's bridge compatibility, rebind only that selected TAP and add it back to the existing dedicated test bridge. Do not touch any non-test adapter.

- [ ] **Step 3: Verify isolation**

Confirm the bridge contains only the two selected test TAPs and does not include public, company, VPN, Wi-Fi, Ethernet, or other ordinary interfaces.

### Task 3: Start and Qualify the Android 16 and Android 12 Emulators

**Files:**
- Modify: none

**Interfaces:**
- Consumes: resolved AVD names, ports, TAP names, SDK tools, and Java.
- Produces: two booted emulator instances with stable Android Framework Wi-Fi and valid TAP routes.

- [ ] **Step 1: Start each emulator with its dedicated TAP**

Run each existing AVD with:

```powershell
& $emulatorExe -avd $avdName -port $consolePort -net-tap $tapName -no-snapshot-load
```

Launch only instances whose AVD, port, TAP, and ownership were checked in Task 1.

- [ ] **Step 2: Wait for both explicit serials**

For each selected serial, run:

```powershell
& $adbExe -s $serial get-state
& $adbExe -s $serial shell getprop sys.boot_completed
& $adbExe -s $serial shell getprop ro.build.version.release
& $adbExe -s $serial shell getprop ro.build.version.sdk
```

Proceed only when both report `device`, boot completion `1`, and the intended Android versions.

- [ ] **Step 3: Enable and connect Wi-Fi through system UI**

On each emulator, open Network & internet, enter Internet/Wi-Fi, enable Wi-Fi, choose a network actually displayed by the system, and confirm Connected. Observe for at least 15 seconds to ensure it remains connected.

- [ ] **Step 4: Cross-check Wi-Fi and route state**

Use explicit serials with `cmd wifi status`, `dumpsys connectivity`, `ip addr`, `ip route`, and `ip rule`. Dynamically identify the TAP guest interface and address; do not assume interface names or subnets.

- [ ] **Step 5: Verify the network gate**

Confirm both guests obtained test addresses in the same subnet, are mutually reachable in both directions, and route traffic to each other through the TAP interface while Android Framework Wi-Fi remains active.

### Task 4: Prepare and Connect the Product

**Files:**
- Read: `app/build/outputs/apk/debug/app-debug.apk`
- Modify: none

**Interfaces:**
- Consumes: the built debug APK, Android 16 controller, Android 12 target, and qualified isolated network.
- Produces: a product-owned paired and connected session.

- [ ] **Step 1: Build or reuse a verified current Debug APK**

If the current Debug APK is absent or older than relevant source changes, run the repository Gradle wrapper with the configured JDK 21 and:

```powershell
.\gradlew.bat :app:assembleDebug --rerun-tasks --no-parallel --no-daemon
```

Wait for an explicit Gradle exit result.

- [ ] **Step 2: Install the app only on the Android 16 controller**

Use the controller's explicit serial:

```powershell
& $adbExe -s $controllerSerial install -r $debugApk
```

Host ADB installation is allowed preparation; it must not connect or pair the target.

- [ ] **Step 3: Enable Wireless debugging on the Android 12 target**

Through target system UI, enable developer options and Wireless debugging, while retaining active Wi-Fi. Open the system pairing-code or QR page only when the product needs that service.

- [ ] **Step 4: Pair and connect through Sheen ADB 助手**

Use the app's discovery, QR, or six-digit pairing flow. Confirm that the selected target remains associated with the Android 12 device, pairing succeeds, and the app itself transitions to an active connected state without host `adb pair` or `adb connect`.

- [ ] **Step 5: Verify session ownership**

Navigate away from the connection page and back, then perform at least one read-only device operation. Confirm the same active product Session remains available and no host-created TCP device connection is involved.

### Task 5: Capture the Seven Core README Screenshots

**Files:**
- Create: `docs/images/readme/connection.png`
- Create: `docs/images/readme/overview.png`
- Create: `docs/images/readme/files.png`
- Create: `docs/images/readme/apps.png`
- Create: `docs/images/readme/processes.png`
- Create: `docs/images/readme/shell.png`
- Create: `docs/images/readme/logcat.png`
- Optional create only if needed for clarity: `docs/images/readme/connected.png`

**Interfaces:**
- Consumes: active product Session and prepared non-sensitive demo data.
- Produces: a consistent, privacy-safe screenshot set from the Android 16 controller.

- [ ] **Step 1: Prepare non-sensitive display state**

Use only generic demonstration names and safe UI states. Do not display a pairing code, QR payload, real endpoint, real package context, Shell command/output, or raw Logcat line.

- [ ] **Step 2: Capture connection and overview**

Capture the product connection/pairing entry without secret material, then capture the connected overview with the Android 12 role clear from non-sensitive system metadata.

- [ ] **Step 3: Capture file, application, and process pages**

Open each page and wait for its real loading state to settle. Capture only actual product output; do not substitute fixture or design data.

- [ ] **Step 4: Capture Shell and Logcat safely**

Demonstrate the terminal controls and Logcat controls with sensitive content absent, cleared, filtered out, or irreversibly obscured. The screenshot must still prove the real page is running.

- [ ] **Step 5: Normalize presentation**

Keep screenshots in PNG, use consistent controller viewport and orientation, and crop only surrounding desktop chrome. Do not alter product state, labels, success indicators, or functional content.

- [ ] **Step 6: Inspect every image**

Visually inspect all screenshots at original resolution. Reject and recapture any image containing a secret, identifiable endpoint, dynamic port, package context, Shell text, Logcat text, or misleading state.

### Task 6: Rewrite README as a User Journey

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: the verified screenshot set and current product capabilities.
- Produces: a concise Chinese-first README that explains the product, its value, use, privacy, requirements, and limits.

- [ ] **Step 1: Replace the engineering-first opening**

Lead with the product name and one sentence explaining that an Android device can locally connect to and manage another Android device over wireless ADB.

- [ ] **Step 2: Add the Android 16 to Android 12 demonstration**

State that the screenshots use an Android 16 controller and Android 12 target on an isolated same-network emulator setup. Explicitly label this as emulator demonstration evidence.

- [ ] **Step 3: Present the core capabilities**

Describe connection/pairing, device overview and quick actions, file transfer, application management, process management, interactive Shell, and on-demand Logcat in short user-focused language.

- [ ] **Step 4: Embed the verified screenshots**

Use repository-relative links under `docs/images/readme/`, one short caption per screenshot, in the same order as the task journey.

- [ ] **Step 5: Add a short quick-start path**

Reduce first use to enabling target Wireless debugging, pairing through the app, connecting, and opening the required tool page. Preserve the distinction between pairing and connect ports without showing any actual endpoint.

- [ ] **Step 6: Add privacy, requirements, build, docs, and limits**

Keep these sections compact and accurate. State no accounts, backend, ads, telemetry, Root, or accessibility automation. Retain the existing release-license blocker and do not imply store readiness.

### Task 7: Verify Evidence, Markdown, Privacy, and Workspace Scope

**Files:**
- Read: `README.md`
- Read: `docs/images/readme/*.png`
- Read: `docs/superpowers/specs/2026-07-29-readme-main-feature-demo-design.md`
- Read: `docs/superpowers/plans/2026-07-29-readme-main-feature-demo.md`
- Modify: none

**Interfaces:**
- Consumes: completed README, screenshots, emulator evidence, and Git diff.
- Produces: a final audit that separates automated, emulator, real-device, and release evidence.

- [ ] **Step 1: Validate every local link**

Resolve each README image and documentation link from the repository root. Fail the verification if any path is missing or case-mismatched.

- [ ] **Step 2: Scan text and images for sensitive material**

Search the README for endpoint-like strings, pairing material, secrets, raw Shell/Logcat excerpts, and identifiable package context. Reinspect every PNG at original resolution.

- [ ] **Step 3: Verify Markdown quality**

Confirm heading order, concise paragraphs, useful alt text, and readable image placement. Ensure the first screen explains what the app is and what it can do.

- [ ] **Step 4: Check repository scope**

Run:

```powershell
git status --short
git diff --check
git diff -- README.md docs/images/readme docs/superpowers/specs/2026-07-29-readme-main-feature-demo-design.md docs/superpowers/plans/2026-07-29-readme-main-feature-demo.md
```

Confirm no unrelated file changed.

- [ ] **Step 5: Report evidence boundaries**

Report separately: network preflight, emulator product demonstration, README/link/privacy validation, unexecuted real-device acceptance, and the existing release-license blocker.

- [ ] **Step 6: Preserve the test environment**

Remove only temporary firewall rules, guest routes, or relay processes created by this run. Preserve AVDs, TAPs, Network Bridge, APKs, screenshots, and user data unless the user later asks for their removal.
