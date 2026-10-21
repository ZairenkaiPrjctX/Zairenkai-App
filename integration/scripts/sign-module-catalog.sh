#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copyright 2026 ZairenkaiPrjctX contributors
# Sign the module catalog (integration/out/assets/system-modules.json) with the module-signing
# key, writing system-modules.json.sig next to it.
#
# Usage: sign-module-catalog.sh <private key PEM file>
# The key must be a P-256 key whose public half is listed in integration/module-trust.json;
# otherwise this fails. The key file is read once by openssl and never copied or printed.
set -euo pipefail

cd "$(dirname "$0")/../.."
KEY=${1:?usage: sign-module-catalog.sh <private key PEM file>}
CATALOG=integration/out/assets/system-modules.json
[ -f "$CATALOG" ] || { echo "no $CATALOG: run build-components.sh first" >&2; exit 1; }
[ -r "$KEY" ] || { echo "cannot read the signing key file" >&2; exit 1; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
openssl pkey -in "$KEY" -pubout -outform DER -out "$TMP/pub.der" 2>/dev/null ||
  { echo "the signing key could not be read as a private key" >&2; exit 1; }
KEY_ID=$(sha256sum "$TMP/pub.der" | cut -d' ' -f1)
python3 - "$KEY_ID" integration/module-trust.json <<'PY'
import json, sys
key_id, trust = sys.argv[1], json.load(open(sys.argv[2]))
if key_id not in {k["key_id"] for k in trust["keys"]}:
    sys.exit(f"key {key_id[:16]}… is not in module-trust.json: refusing to sign with an untrusted key")
PY
openssl dgst -sha256 -sign "$KEY" -out "$TMP/sig.der" "$CATALOG"
# Self-check with the public key before publishing the signature.
openssl dgst -sha256 -verify <(openssl pkey -pubin -inform DER -in "$TMP/pub.der") -signature "$TMP/sig.der" "$CATALOG" >/dev/null
python3 - "$KEY_ID" "$TMP/sig.der" "$CATALOG.sig" <<'PY'
import base64, json, sys
key_id, sig, out = sys.argv[1], open(sys.argv[2], "rb").read(), sys.argv[3]
open(out, "w").write(json.dumps({"schema": 1, "algorithm": "ECDSA_P256_SHA256", "key_id": key_id,
                                 "signature": base64.b64encode(sig).decode()}, indent=2) + "\n")
PY
echo "signed $CATALOG with key ${KEY_ID:0:16}…"
