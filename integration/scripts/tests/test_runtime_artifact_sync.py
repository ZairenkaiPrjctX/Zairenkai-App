# SPDX-License-Identifier: Apache-2.0
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[3]
SCRIPT=ROOT/'integration/scripts/sync_runtime_artifacts.py'
spec=importlib.util.spec_from_file_location('runtime_artifact_sync',SCRIPT)
sync=importlib.util.module_from_spec(spec); spec.loader.exec_module(sync)
class RuntimeArtifactSyncTests(unittest.TestCase):
 def rows(self):
  repos={'zart':'ZairenkaiPrjctX/Zairenkai-ART','zthermal':'ZairenkaiPrjctX/Zairenkai-ThermalTools','afid-device-candidates':'ZairenkaiPrjctX/Zairenkai-Database-Source','zie-run':'ZairenkaiPrjctX/Zairenkai-Intelligence-Engine'}
  return [{'id':k,'repository':v,'commit':'a'*40} for k,v in repos.items()]
 def test_published_release_pins_all_direct_artifacts(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td)/'integration'; p.mkdir(); (p/'repository-lock.json').write_text(json.dumps({'components':self.rows()})); (p/'organization-repositories.json').write_text(json.dumps({'repositories':[{'name':r['repository'].split('/')[-1],'audited_commit':'a'*40,'evidence':''} for r in self.rows()]}))
   def fake(repo,component,commit): return {'repository':repo,'component':component,'source_commit':commit,'release_tag':'artifact-'+commit,'asset_name':'bundle.tar.gz','asset_sha256':'b'*64,'asset_size_bytes':123,'manifest_name':'artifact-manifest.json','manifest_sha256':'c'*64}
   with patch.object(sync,'published_pin',side_effect=fake): self.assertTrue(sync.sync_app(Path(td)))
   out=json.loads((p/'repository-lock.json').read_text())
   self.assertTrue(all('release_artifact' in r for r in out['components']))
 def test_rejects_missing_expected_component(self):
  with tempfile.TemporaryDirectory() as td:
   p=Path(td)/'integration'; p.mkdir(); (p/'repository-lock.json').write_text(json.dumps({'components':[]})); (p/'organization-repositories.json').write_text(json.dumps({'repositories':[]}))
   with self.assertRaises(RuntimeError): sync.sync_app(Path(td))
if __name__=='__main__': unittest.main()
