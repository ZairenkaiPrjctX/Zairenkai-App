# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
"""setup-actions-secrets.sh against a fake gh: argument checks, confirmation and overwrite
guards, key file permissions, the trust list receiving only the public key, and no key or
password reaching the output. No GitHub account or real key is used. Run with
`python3 -m unittest discover -s integration/scripts/tests`."""
import base64
import json
import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
SCRIPT = ROOT / "integration/scripts/setup-actions-secrets.sh"

FAKE_GH = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
state = pathlib.Path(os.environ["FAKE_GH_STATE"])
args = sys.argv[1:]
def lines(name):
    p = state / name
    return p.read_text().split() if p.exists() else []
def add(name, value):
    if value not in lines(name):
        with open(state / name, "a") as f:
            f.write(value + "\n")
env = args[args.index("--env") + 1] if "--env" in args else None
with open(state / "calls.log", "a") as f:
    f.write(" ".join(args[:2]) + (" --env " + env if env else "") + "\n")
if args[:2] == ["auth", "status"]:
    sys.exit(0)
if args[0] == "api":
    path = args[1] if args[1] not in ("-X",) else args[3]
    if path == "user":
        print("tester"); sys.exit(0)
    custom = (state / "policies").exists()
    if path.endswith("/deployment-branch-policies"):
        if not custom:  # real gh prints the 404 body on stdout
            print('{"message":"Not Found","status":"404"}'); sys.exit(1)
        print("\n".join(lines("policies"))); sys.exit(0)
    if path.endswith("/environments/release"):
        if not (state / "env").exists():
            sys.exit(1)
        if "--jq" in args:
            print("true" if custom else "false")
        sys.exit(0)
    if path.startswith("repos/") and "--jq" in args:
        print("true"); sys.exit(0)
    sys.exit(f"fake gh: unexpected api call {args}")
kind, verb, name = args[0], args[1], (args[2] if len(args) > 2 else None)
store = ("env_" if env else "") + kind + "s"
if verb == "list":
    print("\n".join(lines(store))); sys.exit(0)
if verb == "set":
    value = args[args.index("--body") + 1].encode() if "--body" in args else sys.stdin.buffer.read()
    (state / f"value.{store}.{name}").write_bytes(value)
    add(store, name); sys.exit(0)
sys.exit(f"fake gh: unexpected call {args}")
'''


class SetupScriptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.state = self.tmp / "gh-state"
        self.state.mkdir()
        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        gh = bin_dir / "gh"
        gh.write_text(FAKE_GH)
        gh.chmod(0o755)
        self.trust = self.tmp / "module-trust.json"
        shutil.copy(ROOT / "integration/module-trust.json", self.trust)
        self.trusted_before = len(json.loads(self.trust.read_text())["keys"])
        self.keys = self.tmp / "keys"
        self.env = {**os.environ, "PATH": f"{bin_dir}:{os.environ['PATH']}", "FAKE_GH_STATE": str(self.state),
                    "ZAIRENKAI_MODULE_TRUST": str(self.trust), "ZAIRENKAI_KEYS_DIR": str(self.keys)}

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def run_script(self, *args, stdin=""):
        return subprocess.run(["bash", str(SCRIPT), *args], input=stdin, capture_output=True, text=True, env=self.env)

    def secret(self, store, name):
        p = self.state / f"value.{store}.{name}"
        return p.read_bytes() if p.exists() else None

    def calls(self):
        p = self.state / "calls.log"
        return p.read_text() if p.exists() else ""

    def test_help_documents_every_subcommand(self):
        r = self.run_script("help")
        self.assertEqual(r.returncode, 0)
        for sub in ("status", "environment", "app", "module-signing", "keystore", "help"):
            self.assertIn(f"\n  {sub}", r.stdout)

    def test_unknown_subcommand_fails(self):
        self.assertEqual(self.run_script("ci-app").returncode, 2)

    def test_app_rejects_client_id_and_non_numeric_id(self):
        pem = self.tmp / "app.pem"
        pem.write_text("unused")
        for app_id, msg in (("Iv23liAbc", "Client ID"), ("12a", "is a number")):
            r = self.run_script("app", app_id, str(pem))
            self.assertNotEqual(r.returncode, 0)
            self.assertIn(msg, r.stderr)
        self.assertEqual(self.calls(), "")

    def test_app_rejects_a_key_that_is_not_an_app_rsa_key(self):
        pem = self.tmp / "local.pem"
        subprocess.run(["openssl", "ecparam", "-name", "prime256v1", "-genkey", "-noout", "-out", str(pem)], check=True)
        r = self.run_script("app", "123456", str(pem))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("not an RSA private key", r.stderr)
        self.assertEqual(self.calls(), "")

    def test_module_signing_needs_the_release_environment(self):
        r = self.run_script("module-signing", stdin="y\n")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("environment release does not exist", r.stderr)
        self.assertFalse(self.keys.exists())

    def test_module_signing_trusts_only_the_public_key_and_stores_the_secret(self):
        (self.state / "env").touch()
        r = self.run_script("module-signing", stdin="y\n")
        self.assertEqual(r.returncode, 0, r.stderr)
        key = self.keys / "module-signing.pem"
        self.assertEqual(stat.S_IMODE(self.keys.stat().st_mode), 0o700)
        self.assertEqual(stat.S_IMODE(key.stat().st_mode), 0o600)
        pem = key.read_bytes()
        self.assertEqual(self.secret("env_secrets", "ZAIRENKAI_MODULE_SIGNING_KEY"), pem)
        trust = json.loads(self.trust.read_text())
        self.assertEqual(len(trust["keys"]), self.trusted_before + 1)
        self.assertNotIn("PRIVATE", self.trust.read_text())
        der = subprocess.run(["openssl", "pkey", "-in", str(key), "-pubout", "-outform", "DER"],
                             capture_output=True, check=True).stdout
        self.assertEqual(base64.b64decode(trust["keys"][-1]["public_key"]), der)
        self.assertNotIn(pem.decode().splitlines()[1], r.stdout + r.stderr)

        # A second run reuses the key, keeps one trust entry, and refuses to replace the secret
        # unless "overwrite" is typed.
        r = self.run_script("module-signing", stdin="y\nno\n")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("kept the existing ZAIRENKAI_MODULE_SIGNING_KEY", r.stderr)
        self.assertEqual(key.read_bytes(), pem)
        self.assertEqual(len(json.loads(self.trust.read_text())["keys"]), self.trusted_before + 1)

    def test_declining_the_confirmation_changes_nothing_on_github(self):
        (self.state / "env").touch()
        r = self.run_script("module-signing", stdin="n\n")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("cancelled", r.stderr)
        self.assertNotIn("secret set", self.calls())

    @unittest.skipUnless(shutil.which("keytool"), "keytool not installed")
    def test_keystore_generates_and_stores_four_secrets_without_printing_them(self):
        (self.state / "env").touch()
        r = self.run_script("keystore", stdin="\n\n\ny\n")
        self.assertEqual(r.returncode, 0, r.stderr)
        password = (self.keys / "release-keystore.password").read_text()
        self.assertEqual(stat.S_IMODE((self.keys / "release-keystore.password").stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE((self.keys / "release.jks").stat().st_mode), 0o600)
        self.assertNotIn(password, r.stdout + r.stderr)
        self.assertEqual(self.secret("env_secrets", "ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD").decode(), password)
        self.assertEqual(self.secret("env_secrets", "ZAIRENKAI_RELEASE_KEY_PASSWORD").decode(), password)
        self.assertEqual(self.secret("env_secrets", "ZAIRENKAI_RELEASE_KEY_ALIAS"), b"zairenkai-release")
        self.assertEqual(base64.b64decode(self.secret("env_secrets", "ZAIRENKAI_RELEASE_KEYSTORE_BASE64")),
                         (self.keys / "release.jks").read_bytes())
        self.assertIn("SHA256:", r.stdout)

    def test_keystore_rejects_a_short_password(self):
        (self.state / "env").touch()
        r = self.run_script("keystore", stdin="\n\nshort\n")
        self.assertNotEqual(r.returncode, 0)
        self.assertFalse((self.keys / "release.jks").exists())

    def test_status_reports_missing_values_and_passes_when_complete(self):
        r = self.run_script("status")
        self.assertEqual(r.returncode, 1)
        self.assertIn("MISSING  environment release", r.stdout)
        (self.state / "env").touch()
        r = self.run_script("status")
        self.assertIn("MISSING  environment release branch policy", r.stdout)
        self.assertNotIn("Not Found", r.stdout)
        (self.state / "policies").write_text("dev\n")
        (self.state / "variables").write_text("ZAIRENKAI_CI_APP_ID\n")
        (self.state / "secrets").write_text("ZAIRENKAI_CI_APP_PRIVATE_KEY\n")
        (self.state / "env_secrets").write_text("\n".join([
            "ZAIRENKAI_MODULE_SIGNING_KEY", "ZAIRENKAI_RELEASE_KEYSTORE_BASE64", "ZAIRENKAI_RELEASE_KEYSTORE_PASSWORD",
            "ZAIRENKAI_RELEASE_KEY_ALIAS", "ZAIRENKAI_RELEASE_KEY_PASSWORD"]) + "\n")
        r = self.run_script("status")
        self.assertEqual(r.returncode, 0, r.stdout)
        self.assertNotIn("MISSING", r.stdout)


if __name__ == "__main__":
    unittest.main()
