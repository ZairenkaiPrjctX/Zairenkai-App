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
| Loading | `LoadingIndicator` | `ProgressRow` (only while a real operation is pending) |
| Selection | `ToggleButton` (shape morph) | FAS profiles, theme mode |
| Status shapes | `MaterialShapes` + `toShape()` (cookie for ready, gem for unverified, burst for failure, pentagon for attention, circle for not applicable) | `StatusGlyph` |
| Navigation | `ShortNavigationBar` (compact), `WideNavigationRail` (≥ 600 dp), `MediumTopAppBar` with exit-until-collapsed scrolling | `MainActivity` |

**Not used:** the segmented button row. In the alpha18 build, a real touch on an unselected
segment did not reach its `onClick` under Robolectric, while the semantics action did. It was
replaced by toggle buttons, which pass the same touch test.

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
- **Offline:** the app never loads a font from the network.

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
  - asymmetric `hero` for the main status;
  - `metric` and `section`;
  - `destructive` (squarer) for dialogs that change the device.
- **Spacing:** `ZairenkaiSpacing` (4–32 dp, gutter 16 dp).
- **Emphasis:** `SectionCard(emphasis = LOW/NORMAL/HIGH)` picks the surface tone by importance.
  `HeroStatusCard`, `MetricTile`, `CapabilityItem`, `UnavailableCard`, `ExpandableSection` and
  `ConfirmDialog` give each kind of information its own treatment.

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
  - radio-button role on the theme choice;
  - merged rows for key/value pairs.
- **Languages:** English (`values/`) and Bahasa Indonesia (`values-in/`); 182 strings and 3
  plurals each. Daemon and integration diagnostics are shown as reported, in English.
