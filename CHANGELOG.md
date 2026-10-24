# Changelog

Every version of Zairenkai App, newest first. The app shows the entry of its own version in
Settings › About, and the release workflow uses the entry as the release notes. Versions are
`MAJOR.MINOR.PATCH` for a release and `MAJOR.MINOR.PATCH-beta.N` for a beta (a GitHub
pre-release); see `docs/build/releases.md`.

## [0.2.0-beta.1] - unreleased

### Added
- Updates: Settings › Updates checks GitHub Releases for a newer version on the Stable or Beta
  channel, once a day or on request, and notifies you. It shows the release notes and the full
  version, downloads the APK, checks its SHA-256 and its signing key, then opens Android's
  installer. Nothing is installed without your confirmation.
- Settings › About: version, build, components, licences and links.
- The Zairenkai logo: launcher icon, ten colour variants, a themed (monochrome) icon.
- Settings › Appearance: palettes, custom themes, AMOLED black, wallpaper, launcher icon,
  background animation, app font, UI scale and an experimental glass bar.
- A redesigned System Module screen: the installation journey from root to active, the module's
  programs and their digests, and how the package is verified.
- Zairenkai AI runs on the phone: zie-run (Zairenkai-Intelligence-Engine, llama.cpp) answers
  questions by asking read-only tools (Core health and capabilities, FAS status, thermal
  snapshot). A FAS profile change goes to Core as a dry run, and only your approval applies it.
  Get the model once (Qwen2.5-0.5B-Instruct, 429 MB): tap "Download model" (from Zairenkai's own
  GitHub release, resumable, asks first on mobile data) or import the file. It is kept only if its
  SHA-256 matches. Your questions and device data never leave the phone.
- Module updates: the app also checks Zairenkai-Modules' releases and tells you when a newer
  module than the installed one is published.

### Changed
- The whole interface follows Material 3 Expressive, with wavy progress indicators, segmented
  groups and a fifth destination for the (not yet available) on-device AI.
- Long text wraps, shrinks or collapses behind "Show more" instead of overflowing.

### Fixed
- The packaged module (0.3.1) installs on KernelSU-Next: 0.3.0 stopped with "The package has no
  bin/zcored".

### Security
- Release builds: no cleartext traffic, system CAs only, APK signature schemes v2/v3 only, not
  debuggable.
- The app now requests the INTERNET permission, used only to read the release list and download
  a release from GitHub when you check for updates (or the daily check you can turn off), and for
  the AI model download you start. It sends no data about the device.

## [0.1.0] - 2026-10-19

### Added
- First build: the capability registry, Core and FAS clients, zart and zthermal diagnostics,
  the AFID device database, and the installer for the Zairenkai system module.
