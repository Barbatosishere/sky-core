"""Check the shared entropy contract and generated core assets; never writes either project."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
contract = json.loads((ROOT / 'contracts/entropy-shelter.v1.json').read_text(encoding='utf-8'))
java = (ROOT / 'src/main/java/dev/skycore/api/entropy/EntropyContract.java').read_text(encoding='utf-8')
ids = dict(re.findall(r'public static final String (\w+) = "([^"]+)";', java))
expected = set(contract['registry']['items_only'] + contract['registry']['blocks_with_same_id_items'])
assert expected == {ids[k] for k in ('WORLD_FUEL', 'ANTI_ENTROPY_FIELD', 'ENTROPY_FIELD', 'ANTI_ENTROPY_STONE')}
assert {entry['id'] for entry in contract['evidence']} == {ids['WORLD_FUEL_PRODUCED'], ids['WORLD_FUEL_SUPPLIED']}
assert contract['config']['defaults']['naturalDimensions'] == []
assert contract['fields']['base_radius_chunks'] == 0
assert contract['config']['defaults']['explosionIntervalTicks'] == 1200
assert contract['player_explosion_policy']['user_choice'] == 'B'
numbers = {name: float(value) for name, value in re.findall(r'public static final (?:int|double) (\w+) = ([0-9.]+);', java)}
config_source = (ROOT / 'src/main/java/dev/skycore/entropy/EntropyConfig.java').read_text(encoding='utf-8')
config_defaults = {'naturalDimensions': []}
for name, default in re.findall(r'defineInRange\("(\w+)", (EntropyContract\.\w+|[0-9.]+),', config_source):
    config_defaults[name] = numbers[default.split('.')[1]] if default.startswith('EntropyContract.') else float(default)
assert config_defaults == contract['config']['defaults'], 'Public config defaults drifted from the compiled spec'

for locale in ('zh_cn', 'en_us'):
    language = json.loads((ROOT / f'src/main/resources/assets/skycore/lang/{locale}.json').read_text(encoding='utf-8'))
    for rid in expected:
        namespace, name = rid.split(':')
        prefix = 'item' if rid in contract['registry']['items_only'] else 'block'
        assert f'{prefix}.{namespace}.{name}' in language, (locale, rid)
        assert (ROOT / f'src/main/resources/assets/{namespace}/models/item/{name}.json').is_file(), rid
        if prefix == 'block':
            for relative in (f'assets/{namespace}/blockstates/{name}.json',
                             f'assets/{namespace}/models/block/{name}.json',
                             f'data/{namespace}/loot_table/blocks/{name}.json'):
                json.loads((ROOT / 'src/main/resources' / relative).read_text(encoding='utf-8'))

records = contract['recipes']['recipe_examples']
assert len({r['id'] for r in records}) == 3
assert {r['id'] for r in records} == {ids[k] for k in ('WORLD_FUEL_RECIPE', 'ANTI_ENTROPY_FIELD_RECIPE', 'ENTROPY_FIELD_RECIPE')}
for record in records:
    namespace, name = record['id'].split(':')
    relative = f'data/{namespace}/recipe/{name}.json'
    assert json.loads((ROOT / 'src/gameTest/resources' / relative).read_text(encoding='utf-8')) == record['json']
    assert not (ROOT / 'src/main/resources' / relative).exists(), 'QA recipe leaked into production'
fuel = next(r['json'] for r in records if r['id'] == ids['WORLD_FUEL_RECIPE'])
assert {result['id'] for result in fuel['results']} == {ids['WORLD_FUEL'], ids['ANTI_ENTROPY_STONE']}
assert len(fuel['inputs']) <= 4 and len(fuel['results']) == 2
assert all(result['count'] > 0 for result in fuel['results'])
plants = json.loads((ROOT / 'src/main/resources/data/skycore/tags/block/entropy_vulnerable_plants.json').read_text(encoding='utf-8'))
assert plants['replace'] is False and {'#minecraft:logs', '#minecraft:leaves', '#minecraft:crops', 'minecraft:seagrass'} <= set(plants['values'])
for damage in ('entropy_exposure', 'entropy_explosion'):
    payload = json.loads((ROOT / f'src/main/resources/data/skycore/damage_type/{damage}.json').read_text(encoding='utf-8'))
    assert payload['message_id'] == f'skycore.{damage}'
for tag_name in ('bypasses_armor', 'bypasses_resistance', 'bypasses_enchantments', 'bypasses_cooldown'):
    payload = json.loads((ROOT / f'src/main/resources/data/minecraft/tags/damage_type/{tag_name}.json').read_text(encoding='utf-8'))
    assert payload == {'replace': False, 'values': ['skycore:entropy_exposure']}
print('PASS: entropy v1 public IDs, evidence, recipes, QA-only fixtures and generated assets agree.')
print('Contract SHA-256:', hashlib.sha256((ROOT / 'contracts/entropy-shelter.v1.json').read_bytes()).hexdigest())
