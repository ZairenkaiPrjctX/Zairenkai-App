# Zairenkai-FAS: provenance and licensing audit

Audited 2026-10-21 at `3c83f0e` (the locked commit: FAS `dev`, the merge of PR #2). This is an
engineering record, not legal advice.

**Outcome:** the audit found no copied or adapted third-party code, and no GPL dependency. On
2026-10-21 the maintainer decided on Apache-2.0, conditional on this audit, and the audit supports
it. The audit was re-run on the same tree that day, with the same result (below). FAS commit
`9d87236` adds `LICENSE`, `NOTICE` and SPDX headers (`f6de6ee` and `333365e` refine `NOTICE`
and the authorship record). They were merged into FAS `dev` by PR #3 (`be08b2d`, 2026-10-21),
and changes no code.

FAS **stays out of distributable builds** for now, for two reasons:
- its distribution has not been explicitly cleared by the maintainer;
- the first hardware validation covers Core only.

Making it distributable is a reviewed change to `integration/system-modules.json` and the release
gate, after both are done.

## Source history

The repository has 10 commits, all on 2026-10-18 and 2026-10-19:
- **8 content commits**, authored "Claude <noreply@anthropic.com>": Claude Code agent sessions
  run by the maintainer, session `session_01U78qpds8wftapp7Npce8FD`. Every one except the first
  foundation commit carries `Signed-off-by: FebriCahyaa <febricahya12345@gmail.com>`.
- **2 merge commits** by the maintainer (FEBRIAN RAHMAD CAHYA, GitHub `FebriCahyaa`): PR #1
  `3f0daae` and PR #2 `3c83f0e`.

No commit imports a tree, vendors a directory, or cites a source for copied code. No other
contributor appears.

| Commit | Content |
|---|---|
| `ce4506d`, `b032a71` | Agent foundation files (AGENT/, .claude/), synced from ZairenkaiPrjctX/.github |
| `361508a` | zfas-core: frame analyzer, controller, profiles, thermal governor |
| `af96d7e` | core config, control, CPCS, diag; zfas-platform |
| `d160672` | engine; zfasd daemon and CLI |
| `daa77f7` | module packaging, CI, docs |
| `9d43894` | `enabled` and `default_profile` in the status |
| `0877452` | 16 KB page-size linking |

## Licence declarations in the source (at `3c83f0e`, before the licence commit)

- **No licence.** There is no `LICENSE` or `COPYING` file, and no `license` field in any
  `Cargo.toml`.
- **No headers.** No file has a copyright line or an SPDX header. `git grep` found no
  copyright, SPDX, GPL, "derived from", "ported from" or "adapted from" text in `crates/`,
  `module/` or `scripts/`.
- **The FAS build script.** `scripts/build-android.sh` copies a `LICENSE` into the zip only "if
  present" (`cp LICENSE … || true`). None is present, so the zip ships without one.

## Comparison with the projects FAS's own docs reference

`docs/THIRD_PARTY.md` lists three GPL projects and states what was taken as an idea and what
was not used:

| Project | Revision cited | Licence (its LICENSE) | FAS's statement |
|---|---|---|---|
| shadow3aaa/fas-rs | `09ba876` (v4.9.1) | GPL-3.0 | Ideas only: the target-FPS ladder, fixed margins, why restore-on-crash matters |
| shadow3aaa/fas-rs-cpcs | `aa3583a` | GPL-3.0 (its BPF object Dual MIT/GPL) | Ideas only: critical-path weights need confidence and a staleness fallback |
| rem01project/encore_fas | `f408a00` | GPL-2.0 (UAPI header GPL-2.0-only WITH Linux-syscall-note) | Ideas only: pause detection, acquisition period, telemetry-only kernel side |

I checked these statements against the code, using the three projects at exactly those
revisions:
- **Line overlap.** Every FAS source file (Rust, shell, HTML, TOML, the module installer) was
  normalised (whitespace collapsed) and compared line by line with every text file of each
  project, ignoring lines under 30 characters or made only of punctuation.

  | Project | Matching lines | Runs of 3 or more consecutive matches |
  |---|---|---|
  | fas-rs | 22 | 0 |
  | fas-rs-cpcs | 24 | 0 |
  | encore_fas | 0 | 0 |

  Every matching line is boilerplate:
  - `use serde::{Deserialize, Serialize};`
  - `#[derive(Debug, Clone, PartialEq, Eq)]`
  - `use std::time::{Duration, Instant};`
  - `let deadline = Instant::now() + timeout;`
  - `impl Default for AnalyzerConfig {`
  - `. /data/adb/magisk/util_functions.sh`: the line every Magisk module installer has.
- **Shared identifiers** (at least 10 characters) are std or libc APIs, sysfs names
  (`scaling_min_freq`, `scaling_available_frequencies`), Magisk installer names
  (`set_perm_recursive`, `util_functions`), and domain words (`target_fps`, `margin_fps`). The
  same type names were compared definition by definition:
  - `ControllerState`: an enum of 12 states in FAS; a struct holding a controller, parameters
    and timers in fas-rs.
  - `AnalyzerConfig`: frame-window and deadline settings in FAS; uprobe and weight settings in
    fas-rs-cpcs.
  - `FrameAnalyzer` and `CpcsAnalyzer` exist only in FAS.

  No structure, field list or algorithm body matches.
- **WebUI.** FAS's `module/webroot/index.html` is one hand-written page. fas-rs's WebUI is a
  separate Next.js project, with nothing in common.
- **No kernel component.** FAS has no kernel module and no BPF object. eBPF and kernel backends
  are probe-only (`crates/zfas-platform/src/probe.rs`), so no GPL kernel obligations arise.

The comparison script is not part of the repository. It used the method above, and the
revisions are listed so that the comparison can be repeated.

**Finding:** no evidence that any FAS file was copied from, or adapted from, these projects.
What FAS takes from them is documented as general approaches, which `THIRD_PARTY.md` records as
ideas, not code. A line comparison cannot prove that nothing was paraphrased, so the finding is
"no evidence found", not "proven independent".

## Dependencies

Statically linked into `zfasd` (aarch64-linux-android, from `Cargo.lock`, generated in full by
`rust_notices.py`): 21 crates, all permissive.
- 17 crates are MIT OR Apache-2.0 (or Apache-2.0 OR MIT).
- `memchr` is Unlicense OR MIT.
- `winnow` and `zmij` are MIT.
- `unicode-ident` is (MIT OR Apache-2.0) AND Unicode-3.0.

The direct dependencies are serde, serde_json, toml and libc. No GPL or LGPL dependency exists.

MIT and Unicode-3.0 require their notices to travel with binaries. The FAS zip does not carry
them today; an internal APK carries them in `licenses/zairenkai_fas/THIRD_PARTY_NOTICES.txt`.

## Packaging and distribution

- **Zip contents.** The zip is FAS's own build output: module scripts, `bin/zfasd`, `webroot/`
  and `THIRD_PARTY.md`. It has no `LICENSE`.
- **Licence.** At `3c83f0e` the zip had no licence, so it could not be distributed. At
  `9d87236` and later it carries `LICENSE` and `NOTICE`, and `verify_integration.py build` fails on a
  module zip that lacks either.
- **This app's handling:**
  - `integration/system-modules.json` marks FAS `"distribution": "internal"`;
  - `build-components.sh` builds and packages it only with `ZAIRENKAI_INTERNAL_BUILD=1`.
    The report is then labelled "internal development build (not for distribution)", and the
    module screen shows "Internal development build only";
  - the default build, CI's full-integration job and `release.yml` do not include it;
  - `verify_integration.py report --release` fails on an internal build, and on any APK
    containing a module other than `zairenkai_core`.
- **Internal builds** are for the maintainer's own testing of their own repository. That use
  needs no licence grant to third parties. They must not be shared.

## Re-run, 2026-10-21

The same comparison was run on FAS `dev` (`3c83f0e`). Commit `9d87236` changes only headers and
licence files, so it gives the same result:

| Project | Matching lines | Runs of 3 or more consecutive matches |
|---|---|---|
| fas-rs | 22 | 0 |
| fas-rs-cpcs | 24 | 0 |
| encore_fas | 0 | 0 |

## Licence decision and what remains

1. **Decision.** The maintainer adopted Apache-2.0 for FAS on 2026-10-21. The code came from
   AI-assisted sessions that the maintainer ran and signed off. The copyright line therefore uses
   the organization convention, "ZairenkaiPrjctX contributors", as Core and ThermalTools do. It
   names no individual owner. Whether copyright subsists in AI-assisted output depends on
   jurisdiction; this record makes no legal claim about it.
2. **Done in `9d87236`, `f6de6ee` and `333365e`:**
   - `LICENSE` (the official text), `NOTICE`, SPDX headers;
   - Cargo `license`;
   - `build-android.sh` now requires `LICENSE` and puts `LICENSE` and `NOTICE` in the zip;
   - `docs/THIRD_PARTY.md` keeps the "ideas, not code" record.

   - an authorship section in `docs/THIRD_PARTY.md`: the Git history and Anthropic's terms
     (`license-inventory.md`, "Authorship and rights").

   Merged by PR #3 as `be08b2d`; the app's lock pins that merge commit.
3. **Distribution decision, 2026-10-22.** The maintainer decided that Zairenkai ships one root
   module, `zairenkai` (Zairenkai-Modules), carrying zcored and zfasd together, and that FAS is
   distributed in it. The separate `zairenkai_fas` module and the `ZAIRENKAI_INTERNAL_BUILD`
   profile are gone; the catalog's only module is `distributable`. The module zip carries
   FAS's `LICENSE`, `NOTICE` and `THIRD_PARTY.md` (with the crate licences) under
   `licenses/zairenkai-fas/`.
4. **Remaining:**
   - validate FAS on hardware: it is now distributed **before** that validation, by the
     maintainer's decision; until then it is labelled unvalidated (`validated_on` is empty);
   - re-run the release gate once the APK and catalog are signed.

Only licence files and headers were changed in FAS; no code.
