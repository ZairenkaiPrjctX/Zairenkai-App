# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""verify_integration.manifest_problems: the release manifest rules, read through a fake aapt2
that prints a compiled-manifest tree in aapt2's own format."""
import os
import pathlib
import stat
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import verify_integration as v  # noqa: E402

A = "A: http://schemas.android.com/apk/res/android:"


def tree(app_attrs, permissions):
    lines = ["N: android=http://schemas.android.com/apk/res/android (line=2)", "  E: manifest (line=2)"]
    for p in permissions:
        lines += ["      E: uses-permission (line=3)", f'        {A}name(0x01010003)="{p}" (Raw: "{p}")']
    lines += ["      E: permission (line=4)", f'        {A}name(0x01010003)="android.permission.NOT_REQUESTED" (Raw: "x")']
    lines += ["      E: application (line=5)"] + [f"        {A}{k}(0x0)={val}" for k, val in app_attrs.items()]
    return "\n".join(lines) + "\n"


GOOD = {"allowBackup": "false", "usesCleartextTraffic": "false", "networkSecurityConfig": "@0x7f110000"}


class ManifestTest(unittest.TestCase):
    def check(self, attrs, perms=("android.permission.INTERNET",)):
        with tempfile.TemporaryDirectory() as d:
            out = pathlib.Path(d, "tree.txt")
            out.write_text(tree(attrs, perms))
            tool = pathlib.Path(d, "aapt2")
            tool.write_text(f"#!/bin/sh\ncat '{out}'\n")
            tool.chmod(tool.stat().st_mode | stat.S_IEXEC)
            old = os.environ.get("PATH", "")
            os.environ["PATH"] = f"{d}{os.pathsep}{old}"
            try:
                return v.manifest_problems(pathlib.Path(d, "app.apk"))
            finally:
                os.environ["PATH"] = old

    def test_a_hardened_manifest_passes(self):
        self.assertEqual(self.check(GOOD, sorted(v.ALLOWED_PERMISSIONS)), [])

    def test_each_rule(self):
        cases = {
            "debuggable": ({**GOOD, "debuggable": "true"}, "debuggable"),
            "testOnly": ({**GOOD, "testOnly": "true"}, "test-only"),
            "cleartext": ({k: x for k, x in GOOD.items() if k != "usesCleartextTraffic"}, "cleartext"),
            "nsc": ({k: x for k, x in GOOD.items() if k != "networkSecurityConfig"}, "networkSecurityConfig"),
            "backup": ({**GOOD, "allowBackup": "true"}, "allowBackup"),
        }
        for name, (attrs, word) in cases.items():
            problems = self.check(attrs)
            self.assertEqual(len(problems), 1, (name, problems))
            self.assertIn(word, problems[0], name)

    def test_only_allowed_permissions(self):
        problems = self.check(GOOD, ["android.permission.INTERNET", "android.permission.READ_CONTACTS", "com.example.OWN"])
        self.assertEqual(problems, ["release: unexpected permissions ['android.permission.READ_CONTACTS']"])

    def test_the_built_release_apk_if_present(self):
        apk = v.ROOT / "app/build/outputs/apk/release/app-release-unsigned.apk"
        if not apk.is_file() or v.build_tool("aapt2") is None:
            self.skipTest("no release APK or aapt2 here")
        self.assertEqual(v.manifest_problems(apk), [])


if __name__ == "__main__":
    unittest.main()
