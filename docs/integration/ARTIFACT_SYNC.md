# Reproducible synchronization of Zairenkai artifacts

## Release path

The App consumes only the latest **published** `Zairenkai-Modules` release that contains
`artifact-manifest.json`. Draft and prerelease assets are deliberately ignored. The module release
workflow creates a draft after a change to the component lock, assembler, or installer sources; a
maintainer reviews the changelog and manifest and publishes it explicitly.

The manifest locks the module tag and version, `module.prop` version code, source commit, exact
Core/FAS/Tuning commits, ZIP size, and ZIP SHA-256. The App sync workflow turns that published
release into a proposed change to `integration/repository-lock.json` and the organization inventory.
It also moves the App's Core/FAS protocol source pins to the matching SHAs from the same module
release. It refreshes ART, ThermalTools, Database-Source and Intelligence-Engine direct source pins
separately. Core/FAS/Tuning are never independently advanced by the App workflow.

Before packaging, `fetch_module_artifact.py` revalidates the manifest pin, release/source identity,
all component commits against the checked-out Module source lock, ZIP size and SHA-256, internal
`SHA256SUMS`, module ID/version/version code, and installer-script equality against the locked
`Zairenkai-Modules` source tree. Any mismatch fails the full build. Before the App has a pin to a
manifest-bearing release, the existing reproducible source-build path remains available.

## Automation and review

- `Zairenkai-Modules/.github/workflows/sync-upstreams.yml` checks Core, FAS and Tuning daily and
  opens/updates a PR with a patch-version bump when a pin changes.
- `Zairenkai-Modules/.github/workflows/release.yml` builds a checksum-verified release package and
  creates a **draft** release after the relevant `dev` change. A maintainer publishes the reviewed
  draft. Drafts are not consumed by the App.
- `Zairenkai-App/.github/workflows/sync-upstreams.yml` checks source pins and the newest published
  manifest-bearing Module release daily, writes a PR (never directly to `dev`) and dispatches the
  full integration CI on the candidate branch. A failed CI run fails the synchronization workflow.

No personal access token is required for public source/release reads. In each repository, GitHub
Actions must allow workflow creation/pushes to the bot branch and the workflow token needs `contents`,
`pull-requests`, and `actions` write permission. If the organization disables those permissions,
enable them in repository/org Actions settings or use an approved GitHub App installation with the
same narrowly scoped permissions. Never put a private signing key in a sync job.

## Why not copy all repositories into the root ZIP?

Only runtime components belong in the root module: Core, FAS and Tuning. The App additionally builds
ART inspection, thermal diagnostics, the local inference runner and a compact device-candidate asset.
SDK contracts are checked/copied at a pinned revision; Graphics Platform is not a root daemon and
must not be shipped as an unrelated binary. The lock and manifest provide provenance without
mixing unrelated sources or weakening each component's license.

## Tuning and AI safety

A newer Core can fix the known Android `/data` ownership false positive, but it cannot make every
vendor sysfs node writable. `PermissionRestricted` must remain visible and must not be bypassed with
`chmod`, permissive SELinux, a generic root write, or guessed sensor values. A device-specific
write profile requires validation on that exact device before enabling it.

AI's common read-only questions are routed to real device tools even before the optional model is
imported. The reply is summarized from actual typed facts rather than repeating the small model's
proposed goal. Open-ended intent planning still needs the user-imported model; model quality,
performance, memory use and behavior must be checked on physical devices before declaring the AI
fully validated.
