# Licence inventory

What the APK contains, and under which terms. Source of truth for revisions:
`integration/repository-lock.json`; for the font: `integration/third-party-assets.json`.
Checked 2026-10-21.

## Packaged in the APK

| Item | From | Licence | Notice in the APK | Status |
|---|---|---|---|---|
| App code (`:app`, `:integration-core`) | Zairenkai-App | **none chosen** (no `LICENSE`) | – | Open: the maintainer must choose a licence before distribution |
| `lib/<abi>/libzart.so` | Zairenkai-ART `19f658d` | **none chosen** (no `LICENSE`); contains Apache-2.0 data derived from AOSP ART; statically links serde/serde_json (MIT OR Apache-2.0) | – | Open: ART needs a licence before distribution |
| `lib/<abi>/libzthermal.so` | Zairenkai-ThermalTools `9d4b310` | Apache-2.0 | `assets/licenses/zthermal/LICENSE`, `NOTICE` | Packaged. Statically links MIT OR Apache-2.0 crates plus foldhash (Zlib), memchr (Unlicense OR MIT), generic-array and zmij (MIT) (ThermalTools `docs/LICENSING.md`) |
| `assets/afid/device-candidates.json` | Zairenkai-Database-Source `d9c4290` | Data CC BY-SA 3.0 (LineageOS Wiki, adapted by AFID) | Attribution inside the asset and on the device-search screen | Packaged |
| `res/font/google_sans_flex.ttf` | google/fonts `47d54ca` | OFL-1.1 | `assets/licenses/google-sans-flex/OFL.txt`, `TRADEMARKS.md` | Packaged |
| AndroidX, Compose, Material 3, Kotlin stdlib, kotlinx | Maven (`gradle/libs.versions.toml`) | Apache-2.0 | – | Open: no generated third-party notice screen yet |

## Not packaged

- Core (Apache-2.0 at `34e93e3`) and FAS (no licence): the app only calls the installed
  modules through root. No code is copied.
- AFID code (Apache-2.0), ART upstream patches, release scripts and CI configuration.

## Open items

1. Choose licences for Zairenkai-App and Zairenkai-ART (and Zairenkai-FAS). Agents must not
   choose them.
2. Generate a third-party notice bundle for the statically linked Rust crates (MIT and Zlib
   ask that the notice travel with binaries) and for the Maven dependencies, and show it in
   System › Licences.
