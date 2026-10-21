"""Failure paths of verify_integration.py: an incorrect lock, a repository CI cannot fetch, a
missing native component, and an APK that differs from the build. Run with
`python3 -m unittest discover -s integration/scripts/tests`."""
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "integration/scripts/verify_integration.py"


def run(*args, env=None):
    return subprocess.run([sys.executable, str(SCRIPT), *args], capture_output=True, text=True,
                          env={**os.environ, **(env or {})})


class LockTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.lock = json.loads((ROOT / "integration/repository-lock.json").read_text())

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def check(self, mutate):
        lock = json.loads(json.dumps(self.lock))
        mutate(lock)
        p = self.tmp / "lock.json"
        p.write_text(json.dumps(lock))
        return run("lock", env={"ZAIRENKAI_LOCK": str(p)})

    def test_committed_lock_passes(self):
        r = run("lock")
        self.assertEqual(r.returncode, 0, r.stdout)

    def test_incorrect_locks_fail(self):
        cases = {
            "short commit": lambda l: l["components"][0].update(commit="19f658d"),
            "url mismatch": lambda l: l["components"][0].update(url="https://github.com/someone/else.git"),
            "duplicate": lambda l: l["components"].append(dict(l["components"][0])),
            "missing mandatory": lambda l: l.update(components=[c for c in l["components"] if c["id"] != "zthermal"]),
            "merged not bool": lambda l: l["components"][0].update(merged="yes"),
            "bad abi": lambda l: l["components"][0].update(abis=["mips"]),
            "drift from inventory": lambda l: l["components"][0].update(commit="0" * 40),
        }
        for name, mutate in cases.items():
            with self.subTest(name):
                r = self.check(mutate)
                self.assertEqual(r.returncode, 1, f"{name}: {r.stdout}")
                self.assertIn("FAIL lock:", r.stdout)

    def test_an_unfetchable_repository_is_reported(self):
        def unreachable(l):
            l["components"] = [dict(l["components"][0], url=f"file://{self.tmp}/missing.git", repository="x/y")]
        lock = json.loads(json.dumps(self.lock))
        unreachable(lock)
        p = self.tmp / "lock.json"
        p.write_text(json.dumps(lock))
        r = run("access", env={"ZAIRENKAI_LOCK": str(p)})
        self.assertEqual(r.returncode, 1)
        self.assertIn("cannot fetch", r.stdout)
        self.assertIn("contents:read", r.stdout)


class BuildTest(unittest.TestCase):
    def setUp(self):
        self.out = pathlib.Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.out)

    def test_missing_components_fail(self):
        r = run("build", env={"ZAIRENKAI_OUT": str(self.out)})
        self.assertEqual(r.returncode, 1)
        self.assertIn("no checkout", r.stdout)
        self.assertIn("components.json missing", r.stdout)

    def test_a_tampered_or_missing_native_component_fails(self):
        (self.out / "jniLibs/arm64-v8a").mkdir(parents=True)
        lib = self.out / "jniLibs/arm64-v8a/libzart.so"
        lib.write_bytes(b"\x7fELF")
        (self.out / "components.json").write_text(json.dumps({"files": {"jniLibs/arm64-v8a/libzart.so": "0" * 64}}))
        r = run("build", env={"ZAIRENKAI_OUT": str(self.out)})
        self.assertIn("does not match its recorded SHA-256", r.stdout)
        self.assertIn("jniLibs/x86_64/libzthermal.so not built", r.stdout)

    def test_an_apk_without_components_or_with_a_key_fails(self):
        (self.out / "components.json").write_text(json.dumps({"files": {"jniLibs/arm64-v8a/libzart.so": "0" * 64}}))
        apk = self.out / "app.apk"
        with zipfile.ZipFile(apk, "w") as z:
            z.writestr("classes.dex", b"dex")
            z.writestr("assets/release.jks", b"x")
            z.writestr("assets/notes.txt", b"-----BEGIN PRIVATE KEY-----\n")
        r = run("apk", str(apk), env={"ZAIRENKAI_OUT": str(self.out)})
        self.assertEqual(r.returncode, 1)
        self.assertIn("lib/arm64-v8a/libzart.so missing or different", r.stdout)
        self.assertIn("forbidden files", r.stdout)
        self.assertIn("private-key or token markers", r.stdout)
        self.assertIn("assets/licenses/android/NOTICES.txt missing", r.stdout)


if __name__ == "__main__":
    unittest.main()
