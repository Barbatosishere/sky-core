"""Read the existing instance and check the public contract without changing pack files."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--instance', type=Path, default=Path(r'D:\MC\PrismLauncher_minecraft\1.21.1(1)'))
parser.add_argument('--write-test-fixtures', action='store_true', help='Copy validated pack-only recipe payloads into this project QA source set')
args = parser.parse_args()
contract = json.loads((ROOT / 'contracts/chapter-1.v1.json').read_text(encoding='utf-8'))
script = args.instance / 'minecraft/kubejs/server_scripts/skypack/00_core_chapter_1.js'
text = script.read_text(encoding='utf-8')
start = re.search(r'\bvar recipes\s*=\s*', text)
assert start, 'Recipe list not found in the existing pack script'
records, _ = json.JSONDecoder().raw_decode(text, start.end())
actual = {record['id']: record['json'] for record in records}
assert len(actual) == len(records), 'Duplicate pack recipe IDs'
for record in contract['recipe_examples']:
    assert actual.get(record['id']) == record['json'], f'Pack differs from v1: {record["id"]}'
mapping = json.loads((args.instance / 'docs/pack/milestone-mapping.json').read_text(encoding='utf-8'))
assert mapping['core_api_class'] == contract['read_api']['class']
assert mapping['core_snapshot_schema_version'] == contract['read_api']['snapshot_schema_version']
assert mapping['mapping']['world_fuel'] == 'skycore:ch01/minerals_refined'
assert mapping['mapping']['first_tree'] == 'skycore:ch01/first_tree'
assert mapping['mapping']['first_thunder'] == contract['reserved_future_milestones']['nether_gate']
assert mapping['mapping']['cognitive_substrate'] == contract['reserved_future_milestones']['end_gate']
known = {milestone['id'] for milestone in contract['milestones']} | set(contract['reserved_future_milestones'].values())
assert all(value is None or value in known for value in mapping['mapping'].values())
entropy_contract = json.loads((ROOT / 'contracts/entropy-shelter.v1.json').read_text(encoding='utf-8'))
evidence_mapping = mapping.get('entropy_evidence_mapping', {})
known_evidence = {entry['id'] for entry in entropy_contract['evidence']}
assert all(value is None or value in known_evidence for value in evidence_mapping.values()), 'Entropy tasks must use true evidence, never legacy milestones'
if evidence_mapping:
    assert mapping['entropy_snapshot_method'] == entropy_contract['pack_bridge']['entropy_snapshot_method']
    assert mapping['entropy_snapshot_schema_version'] == entropy_contract['read_api']['snapshot_schema_version']
print(f'PASS: all 7 core recipe payloads and public IDs match; {len(records) - 7} pack additions; snapshot/milestone mapping matches v1.')
if args.write_test_fixtures:
    fixture_root = (ROOT / 'src/gameTest/resources').resolve()
    for rid, payload in actual.items():
        if rid.startswith('skycore:'):
            continue
        assert re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+', rid), rid
        namespace, path = rid.split(':')
        target = (fixture_root / f'data/{namespace}/recipe/{path}.json').resolve()
        assert target.is_relative_to(fixture_root), rid
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Copied 5 pack addition payloads to local QA resources; no instance files changed.')
