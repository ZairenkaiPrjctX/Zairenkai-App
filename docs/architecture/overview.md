# Architecture overview

Zairenkai App is one APK that reaches every Zairenkai component with a runtime capability. It
reaches each one through the interface that component actually exposes. Repository facts:
`integration/repository-audit.md`.

## Modules

| Gradle module | Languages | Responsibility |
|---|---|---|
| `:integration-core` | Kotlin + Java | Pure JVM, no Android dependency. Integration facade and capability registry; typed clients for Core, FAS, zart, zthermal and AFID; root-manager adapters and the module installer (`systemmodule/`); the root shell; models and parsers. Unit-tested on the JVM. |
| `:app` | Kotlin + Java | Android application. Compose UI and the design system (Kotlin). Android platform adapters (Java). Dependency container, packaged components and assets. |

**Why only two modules.** No other repository provides Android or JVM source to include. Each
integrated component sits behind one client file in `:integration-core`, so feature modules
would be empty shells.

## Layers

```
Presentation (Kotlin, Compose)        app/.../ui/*Screen.kt, MainActivity.kt
  └ design system (Kotlin)            app/.../design/  (theme, colour, type, shapes, motion, components)
State (Kotlin, coroutines + Flow)     app/.../ui/ViewModels.kt
Android platform adapters (Java)      app/src/main/java/.../platform/AndroidPlatformInfo, ThermalStatusMonitor
Integration facade (Kotlin)           integration-core/.../registry/ (IntegrationRegistry, subsystems)
Typed clients (Kotlin)                integration-core/.../core, fas, zart, thermaltools, afid
Process execution (Java)              integration-core/src/main/java/.../exec/ProcessCommandRunner
Root bridge (Kotlin)                  integration-core/.../exec/RootShell
──────────────── process boundary ────────────────
zart, zthermal (Rust, packaged)  ·  zcored / zfasd (installed modules, via su)  ·  AFID asset (packaged JSON)
```

The UI never builds commands. It calls a client method such as `CoreClient.submit(op, dryRun)`,
and the client builds a fixed argv.

## Kotlin and Java

| Java component | Why Java | Kotlin boundary |
|---|---|---|
| `ProcessCommandRunner` | Plain JVM process and stream handling with explicit threads; no coroutine or Android dependency | Implements the Kotlin `fun interface CommandRunner` and returns the Kotlin sealed `CommandOutcome` (`TimedOut.INSTANCE` and so on). Nullability uses `org.jetbrains.annotations`. |
| `AndroidPlatformInfo` | Thin adapter over framework APIs (`Build`, `PowerManager`, `PackageManager`) | Implements the Kotlin interface `PlatformInfo`. `@Nullable`/`@NonNull` (androidx) give Kotlin `Int?`, `Float?`, `Long?`. |
| `ThermalStatusMonitor` | Callback-style framework listener with explicit register and remove | Kotlin's `statusFlow()` adapts it with `callbackFlow` and unregisters in `awaitClose`. A refused listener returns `null`, and Kotlin falls back to polling. |

Kotlin APIs meant for Java callers carry `@JvmStatic` (`ShellWord.arg/json`,
`PackageNames.isValid`). Both directions are tested:
- Java calling Kotlin: `JavaInteropTest`, `PlatformJavaTest`.
- Kotlin calling Java: every client test runs on `CommandRunner`; `AppUiTest` uses the real
  `AndroidPlatformInfo`.

Compose UI is Kotlin only, because Compose is a Kotlin API. Business rules exist once:
validation (package names, shell words) lives in `:integration-core` and is called from both
languages.

## Registry and availability

`IntegrationRegistry` probes each subsystem off the main thread and publishes a
`RegistrySnapshot` (`StateFlow`). Each `CapabilityRecord` has:
- an id, name and description;
- a provider (repository and revision);
- a privilege (standard, root, remote or none);
- an `Availability`, with a state and the reason in words.

The UI renders availability only from this snapshot.

| State | Meaning |
|---|---|
| `AVAILABLE` | Probed successfully on this device |
| `NOT_VERIFIED` | Callable, but not probed (for example dexopt status, which runs on demand) |
| `ROOT_REQUIRED` | Root integration is off, refused, or the device is not rooted |
| `COMPONENT_NOT_INSTALLED` | The module binary is missing (exit 127) |
| `NATIVE_COMPONENT_UNAVAILABLE` | zart or zthermal is not packaged for this ABI, or cannot start |
| `PERMISSION_REQUIRED` | zthermal ran, but Android denied the app uid every thermal zone it found |
| `INITIALIZATION_FAILED` | The daemon is unreachable, the response is unexpected, or a probe threw |
| `UNSUPPORTED_DEVICE` | API level, no readable thermal zone, or not possible in an APK at all (ART patches) |
| `NO_UPSTREAM_IMPLEMENTATION` | The owning repository has no code (with its audited commit), or no validated implementation of that feature (thermal controls) |

`NETWORK_UNAVAILABLE` and `LICENSE_REQUIRED` exist in the model but nothing produces them: the
app uses the network only for its own updates (`docs/build/releases.md`), and no licence server exists. `PERMISSION_REQUIRED` means an OS
access denial, not a runtime permission the app could request.

The ecosystem prompt's state names map onto these:

| Requested state | App state |
|---|---|
| NOT_BUILT | `NATIVE_COMPONENT_UNAVAILABLE` (component not packaged; `components.json` says what was built) or `NO_UPSTREAM_IMPLEMENTATION` (nothing to build) |
| NOT_INSTALLED | `COMPONENT_NOT_INSTALLED` |
| NOT_INITIALIZED | `INITIALIZATION_FAILED`, or no record yet while the first probe runs |
| AVAILABLE | `AVAILABLE` (probed) or `NOT_VERIFIED` (callable, not probed) |
| DEGRADED | `AVAILABLE` with the component's own degraded reason (for example Core health `degraded`) |
| UNSUPPORTED | `UNSUPPORTED_DEVICE` |
| PERMISSION_DENIED | `ROOT_REQUIRED` (su refused) or `PERMISSION_REQUIRED` (OS denial) |
| INCOMPATIBLE | `INITIALIZATION_FAILED` with the reason (wrong schema or protocol version) |
| ERROR | `INITIALIZATION_FAILED` |

## Navigation

| Destination | Capabilities |
|---|---|
| Home | Device identity, thermal status, AFID match, integration health |
| Performance | FAS status, Core health; profile, enable/disable and battery-saver actions with Core's dry-run preview |
| Thermal | Android thermal service (listener plus headroom), zthermal sensors (read only), Core telemetry, FAS thermal cap |
| Runtime | ART APEX version, zart inspection, dexopt status |
| System | Root access, module inventory, device search, ecosystem, diagnostics, licences |
| Settings (from the app bar) | Theme, dynamic colour, motion, language, the Zairenkai System Module entry |
| Zairenkai System Module (from Home, System or Settings) | Root-manager detection, preflight, verified install, state after reboot, removal (`docs/integration/system-module.md`) |

Tabs are shown only when their subsystems are registered. Phones get a `ShortNavigationBar`;
widths of 600 dp and more get a `WideNavigationRail`.
