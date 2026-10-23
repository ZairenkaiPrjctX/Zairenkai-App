#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""The app version and its release notes, for the release workflow.

  app_release.py version         prints appVersion from app/build.gradle.kts, checked
  app_release.py notes VERSION   prints the CHANGELOG.md entry of VERSION (fails if missing)

A version is MAJOR.MINOR.PATCH (a release) or MAJOR.MINOR.PATCH-beta.N (a pre-release), the
format the app's update check accepts (integration-core update/AppVersion.kt).
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERSION = re.compile(r"^(0|[1-9]\d{0,3})\.(0|[1-9]\d?)\.(0|[1-9]\d?)(?:-beta\.([1-9]\d?))?$")


def version(gradle_text):
    m = re.search(r'^val appVersion = "([^"]+)"', gradle_text, re.M)
    if not m:
        raise ValueError("app/build.gradle.kts declares no appVersion")
    v = m.group(1)
    if not VERSION.match(v):
        raise ValueError(f"appVersion {v!r} is not MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-beta.N")
    return v


def notes(changelog_text, v):
    """The body of the `## [v] ...` section, without its heading; ValueError if absent or empty."""
    lines = changelog_text.splitlines()
    out, inside = [], False
    for line in lines:
        if line.startswith("## "):
            if inside:
                break
            inside = re.match(r"^## \[?v?" + re.escape(v) + r"\]?(\s|$)", line) is not None
            continue
        if inside:
            out.append(line)
    body = "\n".join(out).strip()
    if not body:
        raise ValueError(f"CHANGELOG.md has no entry for {v}")
    return body + "\n"


def main(argv):
    try:
        if argv[:1] == ["version"]:
            print(version((ROOT / "app" / "build.gradle.kts").read_text()))
        elif len(argv) == 2 and argv[0] == "notes":
            sys.stdout.write(notes((ROOT / "CHANGELOG.md").read_text(), argv[1]))
        else:
            print(__doc__, file=sys.stderr)
            return 64
    except ValueError as e:
        print(f"app_release: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
