# Zairenkai AI + Tuning integration repair

Status: proposed integration changes; device validation remains required.

## What the reported screen means

- `Model not imported` means the optional Qwen weights are not installed in App-private storage. It is not proof that the `zie-run` native runner is absent. The composer should be enabled for deterministic, read-only device checks when the runner/assets/Core are present; free-form model answers still require verified weights.
- `Not validated on this device` is intentionally different from a failed SHA-256 check. A matching file proves integrity, not that inference works on a specific Android ABI/device.
- `Thinking on this device…` without a final message is an app-level liveness defect. The ViewModel must clear its working state in `finally`; runner failures and timeouts must be rendered as a visible refusal, not leave the UI spinning.
- Core `PermissionRestricted` for `tuning.io_scheduler` / `tuning.gpu_governor` on module v0.3.1 matches the old Core component-path validator. The proposed module v0.3.2 pins Core commit `377ba4508321557f378d0188f05ff5c643ee62b4`, which applies Android-specific ownership validation for `/data` while keeping `/data/adb` and executed binaries protected.

## AI changes in this patch

1. Add a closed, read-only `tuning.telemetry` tool to the Intelligence Engine registry. Its output points to the canonical Zairenkai-Sdk tuning schema; a captured replay fixture is used for deterministic offline tests. Regenerate `output.gbnf` from the registry and verify mirrored assets.
2. Add deterministic routing for unambiguous questions about heat, active FAS profile, CPU/GPU/RAM/storage telemetry, and Core health. Those requests no longer depend on the 0.5B model generating the right JSON tool call. The model is still used for free-form questions, and an attempted state change still goes through Core's dry-run and an explicit user approval.
3. Render actual Core/Tuning values in the answer and detail cards, including failure statuses such as `PermissionRestricted`. Missing data is never converted into guessed numbers.
4. Clear the chat's busy state in `finally`, show model setup guidance early, and allow read-only status prompts before model weights are imported when the local runner/assets are available.

## Tuning and safety boundary

`Zairenkai-Tuning` already emits typed CPU, GPU, thermal, memory, storage and capability telemetry. The missing part seen here is its reliable end-to-end path into the App and a clean Core/module package, not a reason to bypass permissions. The profile index is currently empty for device-validated profiles. Therefore write control for I/O scheduler and GPU governor must remain restricted until a profile is validated on the exact hardware/kernel and Core approves the operation. Do not add a generic profile or force permissions just to make the UI look green.

## Artifact/version strategy

- The single root module is built from the exact Core/FAS/Tuning commits in `Zairenkai-Modules/components.lock.json`. Its assembler validates component manifests, per-file hashes, source archives for GPL components, and the resulting ZIP SHA-256.
- App's integration build uses an exact source-lock as the reproducible fallback. It can consume a published Modules release ZIP only after its `artifact-manifest.json`, source commit, component commits, asset SHA-256 and size are pinned. `sync_pins.py --module-artifact-tag v0.3.2` performs these checks once the v0.3.2 release is actually published.
- Do not pin the currently published v0.3.1 ZIP for this repair: it contains the old Core component. Until the compatible v0.3.2 artifact is published and locked, the App should keep source-building from immutable SHAs instead of silently installing the old artifact.
- ART, ThermalTools and Intelligence Engine native binaries are built from their exact locked commits by App CI. The model weights are a separate signed/hash-checked release download, never bundled into the APK. SDK and Database-Source are contracts/data sources rather than runtime daemons; Graphics-Platform is not inserted into the root module without an explicit runtime integration and licence review.

## Required merge order

1. Merge the Intelligence Engine patch to `dev` after its unit tests, grammar regeneration check and SDK contract-copy checks pass.
2. Merge the Modules patch to `dev` after its full module CI is green. It bumps the candidate module to v0.3.2 and locks Core `377ba450…`.
3. The Modules release workflow builds the ZIP and immutable `artifact-manifest.json`, then creates a draft release. A maintainer reviews the CI summary, changelog, manifest and ZIP SHA-256 before publishing it.
4. After the release is published, the App sync workflow discovers it, locks the published Module ZIP and corresponding Core/FAS/Tuning commits, updates direct-source pins, creates a PR and runs full integration CI against that PR branch. No merge or publication happens automatically from this workflow.
5. Merge the App PR only when full-integration CI passes. Package a release APK only from the full distributable build with a signed module catalog; the reduced build is not a release candidate.
6. Install the new module through KernelSU's normal installer, reboot, then capture Core health/capabilities and Tuning telemetry. Only report the permission issue resolved if the exact device shows the corresponding capabilities no longer restricted.
7. Test model download/import with SHA validation, `zie-run --version`, short Indonesian and English prompts, read-only heat/FAS/Tuning questions, model timeout/error UX, and a device cold/warm run. The local environment cannot substitute for the target-device validation.

## Known verification limits

The source-level Modules and Intelligence Engine tests pass in the repair workspace. The Android Gradle test/build could not be executed in this environment because the Gradle distribution download requires network access. The local model is not available for inference here, and no target-device run was performed. Do not describe the AI as fully validated until the CI and device checks above pass.
