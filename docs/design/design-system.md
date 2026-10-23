# Zairenkai design system

Source: `app/src/main/kotlin/com/zairenkaiprjctx/app/design/`. All Compose UI goes through it;
feature screens contain no hard-coded colours or font files.

## Material 3 Expressive: what is used, and which version provides it

Each claim was checked against the AAR, not taken from documentation.

| Library | Finding |
|---|---|
| `material3` **1.4.0** (newest stable) | `MaterialExpressiveTheme`, `MotionScheme` and `ExperimentalMaterial3ExpressiveApi` are **internal**. Compiling against them fails with "it is internal". `ShortNavigationBar` and `WideNavigationRail` are public. |
| `material3` **1.5.0-alpha18** (used) | Expressive APIs public under `@ExperimentalMaterial3ExpressiveApi`. The AAR requires compileSdk 35 and AGP 8.6+, and pulls Compose 1.11. |
| `material3` 1.5.0-alpha19 and later | Require compileSdk 37 and AGP 9.1. They would need a toolchain migration, which was not done. |

The rest of Compose comes from the stable BOM 2026.06.01 (UI 1.11.4). Material 3 is the only
pre-release dependency, pinned in `gradle/libs.versions.toml` with the reason. Revisit when
1.5.0 is stable.

| Element | API | Where |
|---|---|---|
| Theme with the expressive motion scheme | `MaterialExpressiveTheme` | `ZairenkaiTheme` |
| Motion tokens | `MaterialTheme.motionScheme` (spatial and effects specs) | `ZairenkaiMotion`, all transitions |
| Measured values | `CircularWavyProgressIndicator` (determinate) | `RingGauge`: ready capabilities on Home, FPS against the FAS target, thermal headroom. Shown only for a measured value; an unknown value is an empty track and a dash, never a guess |
| Bars | `LinearWavyProgressIndicator` (determinate) | `Meter`: CPU and memory (Core tuning telemetry), battery level, zones read by zthermal |
| Work in progress | `LinearWavyProgressIndicator` (indeterminate) | `WorkingBar`: under the app bar while the registry refreshes, on the module screen while the installer works. Nothing is drawn with animations off |
| Loading | `LoadingIndicator` | `ProgressRow` (only while a real operation is pending) |
| Single choice | `ToggleButton` with `ButtonGroupDefaults.connected*ButtonShapes()` | `ConnectedChoice`: theme mode, language. The chosen option morphs to a pill and carries a check mark |
| Lists | segmented groups: rows on one tonal surface, 2 dp apart, large outer and small inner corners | `SegmentedGroup`, `GroupRow` (every screen) |
| Status shapes | `MaterialShapes` + `toShape()` (cookie for ready, gem for unverified, burst for failure, pentagon for attention, circle for not applicable) | `StatusGlyph` |
| AI mark | `MaterialShapes` `Morph` (Cookie9Sided ↔ SoftBurst) | `AiOrb`; still while no model is available |
| Navigation | `ShortNavigationBar` (compact), `WideNavigationRail` (≥ 600 dp), `MediumFlexibleTopAppBar` with a subtitle and exit-until-collapsed scrolling | `MainActivity` |

**Segmented buttons and connected button groups under Robolectric.** A real touch on the first
or last segment does not reach its handler under LEGACY graphics, Robolectric 4.14's default:
those segments have path outlines, and LEGACY graphics cannot hit-test a path. Under NATIVE
graphics every segment takes the touch (`ConnectedChoiceTouchTest`). The LEGACY-mode tests
(`AppUiTest`, `LanguageTest`) therefore invoke the click action on these groups. The earlier note
here blamed the segmented button row itself; that was the same test limitation.

## Information architecture

- **Five destinations:** Home, Performance, Thermal, AI, System. Runtime (ART), Settings and the
  System Module are pages opened from System or the app bar, with a back arrow.
- **Home:** a hero with the device and a ring of ready capabilities, two tiles (thermal state,
  root access), "Suggested" next steps (`nextSteps`: install the module when root is granted and
  it is missing, enable root when features wait for it, cool down at severe throttling), then the
  capabilities grouped as needing attention, ready, and not possible on this device.
- **Performance:** FAS hero (profile, FPS ring, missed frames, decision details), profiles as a
  radio list, service switches (FAS through Core, battery saver through Core), Core, and Core's
  tuning telemetry. Every change still goes through Core's preview and policy.
- **Thermal:** Android's thermal status and headroom, Core's readings, the FAS cap, and the
  zthermal snapshot. Read only: no control exists.
- **System:** root access, components, device search, ecosystem, diagnostics, licences, settings.
- **Detail on demand:** sources, decision details, device details and long diagnostics sit in
  `ExpandableSection`s, collapsed by default.

## Zairenkai AI screen

Prepared for Zairenkai-Intelligence-Engine; no on-device model is packaged yet, so the registry
record `intelligence.analysis` reports `NO_UPSTREAM_IMPLEMENTATION` and the screen says so.

- The composer is disabled unless that record is usable. The suggested prompts open the screen
  that already answers them (Thermal, Performance, Home, System).
- The transcript is empty in production: nothing invents an answer.
- Components for the runtime, shaped after the SDK's Local-AI contracts: `ChatBubble`,
  `ToolCallCard` (a read-only tool from the closed registry), `ApprovalCard` (a device change:
  Core's dry-run decision first; Approve is enabled only when Core allows it; applying stays
  with Core). `AiViewModel.decide` only answers a pending preview.

## Settings › Appearance

Inspired by the appearance settings of mpvRx (AGPL-3.0); none of its code was copied, only the
behaviour was studied, and everything here is written for this app.

- **Theme:** System / Light / Dark; **app theme** as a row of live preview cards: Dynamic
  (Android 12+), Zairenkai (the hand-tuned brand scheme), eleven generated palettes, the user's
  custom themes and "Custom theme".
- **Generated schemes** (`schemeFromSeed`): every Material role from one seed, each at the tone
  Material assigns it (primary 40/80, containers 90/30, surfaces 98/6, ...). Tones are found by
  luminance (CIE L*), not HSL lightness, so contrast holds for any hue. `AppearanceTest` checks
  4.5:1 for text on primary and on every container, and 7:1 on surfaces, for all palettes and
  extreme seeds (yellow, cyan, white, black, grey, magenta), light, dark and AMOLED.
- **Custom theme editor:** live light and dark preview, name, and a colour picker with three modes
  (hue/saturation wheel with brightness, saturation/value square with a hue bar, RGB sliders), a
  hex field (only a complete valid value applies) and quick colours.
- **AMOLED black:** pure black background and darker containers in dark themes.
- **Wallpaper:** four gradients drawn in code (no image files), or a photo picked with the system
  photo picker, copied scaled down into private storage (no permission). Fill or fit, and a dim
  (the app background laid over it) so text keeps its contrast.
- **App icon:** ten launcher variants, each an `activity-alias` of MainActivity with its own
  adaptive icon; exactly one is enabled (`LauncherIconSwitcher` enables the new one first). A
  custom colour picks the nearest variant: launchers can only show icons in the APK.
- **Background animation:** none, rings, waves or orbs; faint, slow, frozen when animations are off.
- **App font:** Google Sans Flex (bundled), the system font, or one of 59 Google Fonts (see below).
- **App UI scale:** 85–120 %, applied to every dp and sp through `LocalDensity`.
- **Liquid glass (experimental):** a frosted navigation bar (and optionally top bar) through Haze
  1.6.10 (Apache-2.0): blur (frost), tint, rim highlight and grain, with a checkerboard preview.
  Android 11 and older get the tint without blur. Haze 2.x needs Compose 1.12 and Kotlin 2.4, so
  1.6.x is pinned.
- **Search:** Settings has a search box over every appearance setting, language and the module.

## Logo

The Zairenkai mark is a **Z whose diagonal is a wave**, with a small spark: one continuous
stroke (width 7.5 on the 108-unit adaptive-icon grid, round caps and joins) in white on a deep
teal-to-black gradient (`#0E5568` → `#0B2433` → `#0A1218`), and a cyan spark (`#65D3FF`).

| Where | Source |
|---|---|
| Launcher icon (default) | `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_background.xml`; monochrome layer `ic_launcher_monochrome.xml` (Android 13 themed icons) |
| Icon variants (Settings › Appearance › App icon) | `ic_launcher_foreground_<variant>.xml`, same geometry in each variant's colours |
| In the app | `design/Logo.kt` (`ZairenkaiLogo`, `ZairenkaiMark`): the same paths drawn in Compose, used in the Settings header and the icon picker |
| Vector original | `docs/brand/zairenkai-logo.svg` (also the module WebUI's `logo.svg` in Zairenkai-Modules) |

Keep the geometry identical everywhere; change it in `ZairenkaiMark` and the SVG together.

## System Module, Updates and About

- **System Module:** a hero tinted by the install phase (status glyph and phase name in words),
  then a five-step journey — Root, Check, Install, Reboot, Active — where a done step shows a
  check, the current one a ring (or "!" when the flow is stuck there) and later ones an outline;
  the spoken description names the step. Under it the phase's actions, then the module's programs
  as tiles (from the build record's `bin/` entries and their SHA-256), a Security group
  (catalog signature, package digest, the checks run before install, on-device integrity from
  `SHA256SUMS`, distribution) and the details behind expandable sections.
- **Updates:** a hero with the update state in words, the next step with exactly the action that
  state allows, the offered release (version and code, channel chip, size, date, notes rendered as
  text), the channel as two choice cards, the daily check switch, and how updates are verified.
- **About:** the logo large on a gradient, name, tagline and chips for the full version, the build
  channel and the licence; then updates, this version's changelog entry, build facts (commit with
  copy, signing certificate), packaged components, privacy, links and licences.

## Typography: Google Sans Flex

- **File:** `res/font/google_sans_flex.ttf`, the variable font (wght 1–1000, opsz 1–144, wdth,
  slnt, ROND), version 5.000, built from upstream tag `v5.000` (`cedf1e9`).
- **Source:** Google Fonts' official repository `google/fonts` at commit `47d54ca1`, path
  `ofl/googlesansflex/`. Provenance and SHA-256 are in `integration/third-party-assets.json`.
- **Why not the upstream demo files:** `googlefonts/googlesans-flex` itself ships only sources
  and `documentation/demo-fonts` (0.006 preview builds), which were not used.
- **Licence:** SIL OFL 1.1, plus Google's trademark notice. Both are shipped in
  `assets/licenses/google-sans-flex/` and viewable under System › Licences.
  - The font's name ID 0 still reads "Copyright 2015 Google LLC. All Rights Reserved.", while
    the OFL file names the Google Sans Flex Project Authors. This is recorded, not changed.
  - "Google Sans Flex" is used only to name the font, never as or in the product name.
- **Optical sizes:** one `FontFamily` per optical size, each weight 300–800 an instance with
  `wght` and `opsz` set:
  - display (opsz 48) for display and headline styles;
  - text (opsz 18) for title and body styles;
  - small (opsz 12) for labels and captions.
- **Technical roles** (`ZairenkaiTextStyles`):
  - `metric` and `numeric` use tabular figures (`tnum`) so digits do not jump;
  - `code` is monospace for paths, property names and commits.
- **Views:** `values/themes.xml` and `values-night/themes.xml` set `android:fontFamily` to the
  same font. No View-based screen exists today, so this covers framework-drawn text only.
- **Fallback:** if the font cannot load, the platform sans-serif is used. Missing glyphs fall
  back per character through the system font chain. The font covers latin, latin-ext,
  vietnamese, math and symbols (`METADATA.pb`), which includes Indonesian.
- **Offline:** the app itself never loads a font from the network (its INTERNET permission
  serves only update checks, `docs/build/releases.md`). A Google Font chosen in Settings › Appearance › App font is fetched by Google Play
  services' font provider (`GoogleFont.Provider`, verified against the provider certificates in
  `res/values/font_certs.xml`, copied unchanged from AOSP's DownloadableFonts sample). Without
  Play services, or until it arrives, the system font is shown. Nothing is requested until the
  user picks a Google Font.

## Colour

- **Brand schemes:** `ZairenkaiColorScheme.light` and `.dark` (teal primary, amber tertiary),
  used when dynamic colour is off or below Android 12.
- **Dynamic colour:** used on Android 12+ by default; the user can switch it off.
- **Theme mode:** System / Light / Dark, persisted (`ThemePreferences`).
- **Semantic status colours:** `ZairenkaiStatusColors` (success, warning, error, info,
  unavailable) do not follow the wallpaper, so meaning never changes with it. Status is always
  icon + text + tone (+ shape on hero surfaces), never colour alone.
- **System bars:** `enableEdgeToEdge` is re-applied when the effective theme changes, so bar
  icons stay legible.

## Shape, spacing, emphasis

- **Shape scale:** 8, 12, 20, 28 and 36 dp, plus role shapes:
  - `hero` (32 dp) for the main surface of a screen (`HeroPanel`);
  - segment corners: 24 dp outer, 6 dp inner, 2 dp gap;
  - `metric` and `section`;
  - `destructive` (squarer) for dialogs that change the device.
- **Spacing:** `ZairenkaiSpacing` (4–32 dp, gutter 16 dp).
- **Emphasis:** `SectionCard(emphasis = LOW/NORMAL/HIGH)` picks the surface tone by importance.
  `HeroPanel`, `MetricTile`, `ActionTile`, `GroupRow`, `CapabilityItem`, `UnavailableCard`,
  `ExpandableSection` and `ConfirmDialog` give each kind of information its own treatment.
- **Icons:** `ZairenkaiGlyphs`, original 24 dp line icons drawn for this app (`Glyphs.kt`).

## Motion and accessibility

- **Transitions:**
  - destinations: fade + slight slide (`AnimatedContent`);
  - sections: expand/collapse (`animateContentSize` and a rotating chevron);
  - status badges: colour change;
  - metric values: short cross-fade only, never counting animations.
- **Reduced motion:** when the system animator scale is 0 (Remove animations),
  `LocalReducedMotion` is true. Every spec becomes `snap()` and the loading indicator becomes a
  static icon.
- **Semantics:**
  - headings on section titles;
  - `stateDescription` on status badges;
  - content descriptions on icon buttons;
  - radio-button role on the theme, language and FAS profile choices, with a check mark on the
    chosen one;
  - expand/collapse state on expandable sections;
  - merged rows for key/value pairs.
- **Languages:** English (`values/`) and Bahasa Indonesia (`values-in/`); 419 translated strings and
  5 plurals each, plus 3 names that are not translated. Daemon and integration diagnostics are shown as reported, in English.
- **Default language: English**, whatever the system language. Indonesian applies only when
  chosen in Settings › Language (`AppLanguage`, applied in `MainActivity.attachBaseContext`):
  - API 33+: the choice is the per-app language (`LocaleManager`), so the system's app-language
    setting shows the same value; "System default" there means English;
  - API 29–32: the choice is stored in the app's preferences (`app_language`).
