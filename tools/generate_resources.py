"""Generate vanilla-textured prototype assets and isolated GameTest data, never pack scripts."""
import gzip
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'src/main/resources'
TEST = ROOT / 'src/gameTest/resources'


def write(root, relative, value):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


for name, texture in {
    'rock_core': 'crying_obsidian', 'stone_workbench': 'polished_andesite', 'mineral_soil': 'rooted_dirt',
    'mechanical_anti_entropy_field': 'lodestone_top', 'mechanical_entropy_field': 'respawn_anchor_top',
    'anti_entropy_stone': 'obsidian'
}.items():
    write(MAIN, f'assets/skycore/blockstates/{name}.json', {'variants': {'': {'model': f'skycore:block/{name}'}}})
    write(MAIN, f'assets/skycore/models/block/{name}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'minecraft:block/{texture}'}})
    write(MAIN, f'assets/skycore/models/item/{name}.json', {'parent': f'skycore:block/{name}'})
    pools = [] if name in {'rock_core', 'mechanical_anti_entropy_field'} else [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'skycore:{name}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]
    write(MAIN, f'data/skycore/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': pools})

for name, texture in {
    'stone_fragment': 'flint', 'mineral_residue': 'clay_ball', 'primordial_fuel': 'charcoal',
    'condensate': 'ghast_tear', 'life_embryo': 'wheat_seeds', 'world_fuel': 'blaze_powder'
}.items():
    write(MAIN, f'assets/skycore/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'minecraft:item/{texture}'}})

english = {
    'block.skycore.mechanical_anti_entropy_field': 'Mechanical Anti-Entropy Field',
    'block.skycore.mechanical_entropy_field': 'Mechanical Entropy Field',
    'block.skycore.anti_entropy_stone': 'Anti-Entropy Stone', 'item.skycore.world_fuel': 'World Fuel',
    'message.skycore.entropy_not_ready': 'Entropy runtime is unavailable. Check the server configuration and log.',
    'message.skycore.entropy_warning': 'Entropy hazard! Leave within %s seconds. Explosions can hurt you after the warning.',
    'message.skycore.entropy_fuel_full': 'This machine cannot accept a complete unit of world fuel.',
    'message.skycore.entropy_fuel': 'Shelter fuel: %s ticks. Use world fuel to expand protection.',
    'message.skycore.entropy_switch': 'Sneak with an empty main hand to toggle this entropy field.',
    'message.skycore.entropy_enabled': 'Mechanical entropy field enabled.',
    'message.skycore.entropy_disabled': 'Mechanical entropy field disabled.',
    'death.attack.skycore.entropy_exposure': '%1$s succumbed to entropy',
    'death.attack.skycore.entropy_explosion': '%1$s was consumed by an entropy explosion',
    'message.skycore.shelter_init_failed': 'Shelter initialization failed: %s',
    'block.skycore.rock_core': 'Rock Core', 'block.skycore.stone_workbench': 'Stone Workbench',
    'block.skycore.mineral_soil': 'Mineral Soil', 'item.skycore.stone_fragment': 'Stone Fragment',
    'item.skycore.mineral_residue': 'Mineral Residue', 'item.skycore.primordial_fuel': 'Primordial Fuel',
    'item.skycore.condensate': 'Condensate', 'item.skycore.life_embryo': 'Life Embryo',
    'message.skycore.empty_hand': 'Use your empty main hand to gather from the rock core.',
    'message.skycore.inventory_full': 'Make room for all of the core materials first.',
    'message.skycore.team_not_ready': 'Your team is not ready yet. Try again shortly.',
    'message.skycore.not_owner': 'This block belongs to another team or has not been assigned.',
    'message.skycore.germination_inputs': 'Hold a life embryo in your main hand and condensate in your off hand. Your team must unlock germination first.',
    'message.skycore.germination_progress': 'Germination: %s / %s ticks. Keep the 3 x 5 x 3 space above clear.',
    'gui.skycore.fuel': 'Heat'
}
chinese = {
    'block.skycore.mechanical_anti_entropy_field': '机械逆熵力场',
    'block.skycore.mechanical_entropy_field': '机械熵增力场',
    'block.skycore.anti_entropy_stone': '抗熵石', 'item.skycore.world_fuel': '世界燃料',
    'message.skycore.entropy_not_ready': '熵增运行层不可用，请检查服务端配置与日志。',
    'message.skycore.entropy_warning': '熵增危险！请在%s秒内撤离，预警结束后爆炸也会造成伤害。',
    'message.skycore.entropy_fuel_full': '该机器无法完整接收一份世界燃料。',
    'message.skycore.entropy_fuel': '庇护燃料剩余 %s 刻，使用世界燃料可扩大保护。',
    'message.skycore.entropy_switch': '空主手潜行右键可切换机械熵增力场。',
    'message.skycore.entropy_enabled': '机械熵增力场已开启。',
    'message.skycore.entropy_disabled': '机械熵增力场已关闭。',
    'death.attack.skycore.entropy_exposure': '%1$s 被熵增吞噬',
    'death.attack.skycore.entropy_explosion': '%1$s 在熵增爆炸中消逝',
    'message.skycore.shelter_init_failed': '庇护初始化失败：%s',
    'block.skycore.rock_core': '岩核', 'block.skycore.stone_workbench': '石质工作台',
    'block.skycore.mineral_soil': '矿质土', 'item.skycore.stone_fragment': '碎石',
    'item.skycore.mineral_residue': '矿质残渣', 'item.skycore.primordial_fuel': '原初燃料',
    'item.skycore.condensate': '凝露', 'item.skycore.life_embryo': '生命胚种',
    'message.skycore.empty_hand': '请空主手右键岩核采集。',
    'message.skycore.inventory_full': '背包需要能完整放下本次采集的所有材料。',
    'message.skycore.team_not_ready': '队伍数据尚未就绪，请稍后重试。',
    'message.skycore.not_owner': '此方块属于其他队伍，或尚未分配队伍。',
    'message.skycore.germination_inputs': '请主手持生命胚种、副手持凝露，并先解锁本队萌发阶段。',
    'message.skycore.germination_progress': '萌发进度：%s / %s 刻。请保持上方 3×5×3 空间畅通。',
    'gui.skycore.fuel': '热源'
}
write(MAIN, 'assets/skycore/lang/en_us.json', english)
write(MAIN, 'assets/skycore/lang/zh_cn.json', chinese)
write(MAIN, 'data/minecraft/tags/block/mineable/pickaxe.json', {'replace': False, 'values': ['skycore:rock_core', 'skycore:stone_workbench', 'skycore:mechanical_anti_entropy_field', 'skycore:mechanical_entropy_field', 'skycore:anti_entropy_stone']})
write(MAIN, 'data/minecraft/tags/block/mineable/shovel.json', {'replace': False, 'values': ['skycore:mineral_soil']})

write(MAIN, 'data/skycore/tags/block/entropy_vulnerable_plants.json', {'replace': False, 'values': [
    '#minecraft:logs', '#minecraft:leaves', '#minecraft:saplings', '#minecraft:crops', '#minecraft:flowers',
    'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern',
    'minecraft:dead_bush', 'minecraft:vine', 'minecraft:brown_mushroom', 'minecraft:red_mushroom',
    'minecraft:brown_mushroom_block', 'minecraft:red_mushroom_block', 'minecraft:mushroom_stem',
    'minecraft:sugar_cane', 'minecraft:cactus', 'minecraft:bamboo', 'minecraft:bamboo_sapling',
    'minecraft:kelp', 'minecraft:kelp_plant', 'minecraft:seagrass', 'minecraft:tall_seagrass',
    'minecraft:lily_pad', 'minecraft:sweet_berry_bush', 'minecraft:cave_vines', 'minecraft:cave_vines_plant',
    'minecraft:azalea', 'minecraft:flowering_azalea', 'minecraft:moss_block', 'minecraft:moss_carpet',
    'minecraft:mangrove_roots', 'minecraft:muddy_mangrove_roots', 'minecraft:hanging_roots', 'minecraft:spore_blossom',
    'minecraft:big_dripleaf', 'minecraft:big_dripleaf_stem', 'minecraft:small_dripleaf', 'minecraft:glow_lichen',
    'minecraft:nether_wart', 'minecraft:crimson_fungus', 'minecraft:warped_fungus', 'minecraft:crimson_roots',
    'minecraft:warped_roots', 'minecraft:nether_sprouts', 'minecraft:weeping_vines', 'minecraft:weeping_vines_plant',
    'minecraft:twisting_vines', 'minecraft:twisting_vines_plant', 'minecraft:chorus_plant', 'minecraft:chorus_flower',
    'minecraft:pumpkin_stem', 'minecraft:attached_pumpkin_stem', 'minecraft:melon_stem', 'minecraft:attached_melon_stem'
]})
for damage in ('entropy_exposure', 'entropy_explosion'):
    write(MAIN, f'data/skycore/damage_type/{damage}.json', {
        'message_id': f'skycore.{damage}', 'scaling': 'never', 'exhaustion': 0.0
    })
for damage_tag in ('bypasses_armor', 'bypasses_resistance', 'bypasses_enchantments', 'bypasses_cooldown'):
    write(MAIN, f'data/minecraft/tags/damage_type/{damage_tag}.json', {'replace': False, 'values': ['skycore:entropy_exposure']})
write(MAIN, 'data/minecraft/tags/damage_type/is_explosion.json', {'replace': False, 'values': ['skycore:entropy_explosion']})

contract = json.loads((ROOT / 'contracts/chapter-1.v1.json').read_text(encoding='utf-8'))
entropy = json.loads((ROOT / 'contracts/entropy-shelter.v1.json').read_text(encoding='utf-8'))
for recipe in contract['recipe_examples'] + entropy['recipes']['recipe_examples']:
    namespace, path = recipe['id'].split(':')
    write(TEST, f'data/{namespace}/recipe/{path}.json', recipe['json'])

# Deliberately separate from the main source set: neither QA recipes nor structures enter the JAR.
def string(value):
    encoded = value.encode('utf-8')
    return struct.pack('>H', len(encoded)) + encoded


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


for name, dimensions in [('empty', (16, 9, 16)), ('entropy_start', (32, 9, 32))]:
    template = b'\x0a\x00\x00'
    template += tag(3, 'DataVersion', struct.pack('>i', 3955))
    template += tag(9, 'size', b'\x03' + struct.pack('>i3i', 3, *dimensions))
    template += tag(9, 'palette', b'\x0a' + struct.pack('>i', 1) + tag(8, 'Name', string('minecraft:air')) + b'\x00')
    template += tag(9, 'blocks', b'\x0a' + struct.pack('>i', 0))
    template += tag(9, 'entities', b'\x0a' + struct.pack('>i', 0)) + b'\x00'
    structure = TEST / f'data/skycore/structure/{name}.nbt'
    structure.parent.mkdir(parents=True, exist_ok=True)
    structure.write_bytes(gzip.compress(template, mtime=0))
print('Generated prototype assets and 10 isolated contract recipe fixtures; no pack files changed.')
