"""Collect already completed core QA; never installs a JAR or starts/modifies the pack."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
BUILD = Path('D:/MC/skycore-build/build')
RUN = Path('D:/MC/skycore-build/gametest')
OUT = ROOT / 'dist/entropy-core-step-2'
JAR_NAME = 'skycore-0.1.0-neoforge-1.21.1.jar'
ORIGINAL_SHA = '4f1813259d32b5f9919a100a4a50e7091a58586a64944e1c4c29889befe7b67d'

CHANGED = '''README.md
build.gradle
contracts/entropy-shelter.v1.json
docs/entropy-shelter-contract.md
docs/entropy-core-step-2-verification.md
src/main/java/dev/skycore/api/SkyCoreApi.java
src/main/java/dev/skycore/block/WorkbenchBlockEntity.java
src/main/java/dev/skycore/entropy/EntropyBackend.java
src/main/java/dev/skycore/entropy/EntropyCommands.java
src/main/java/dev/skycore/entropy/EntropyContent.java
src/main/java/dev/skycore/entropy/EntropyEvents.java
src/main/java/dev/skycore/entropy/EntropyExplosionSchedule.java
src/main/java/dev/skycore/entropy/EntropyRules.java
src/main/java/dev/skycore/entropy/EntropyRuntime.java
src/main/java/dev/skycore/entropy/EntropySavedData.java
src/main/java/dev/skycore/entropy/FieldMachineBlock.java
src/main/java/dev/skycore/entropy/FieldMachineBlockEntity.java
src/main/java/dev/skycore/entropy/ServerEntropyBackend.java
src/main/resources/assets/skycore/lang/en_us.json
src/main/resources/assets/skycore/lang/zh_cn.json
src/main/resources/data/skycore/tags/block/entropy_vulnerable_plants.json
src/main/resources/data/skycore/damage_type/entropy_exposure.json
src/main/resources/data/skycore/damage_type/entropy_explosion.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_armor.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_resistance.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_enchantments.json
src/main/resources/data/minecraft/tags/damage_type/bypasses_cooldown.json
src/main/resources/data/minecraft/tags/damage_type/is_explosion.json
src/test/java/dev/skycore/entropy/EntropyRulesTest.java
src/test/java/dev/skycore/entropy/EntropyExplosionScheduleTest.java
src/gameTest/java/dev/skycore/entropy/EntropyTests.java
src/gameTest/java/dev/skycore/entropy/EntropyRestartProbe.java
src/gameTest/java/dev/skycore/gametest/ChapterOneTests.java
src/gameTest/resources/data/skycore/structure/entropy_start.nbt
tools/generate_resources.py
tools/check_entropy_contract.py
docs/entropy-core-step-2-review-1.md
tools/verify_entropy_step_2.py'''.splitlines()


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def passed_gametest(path):
    value = path.read_text(encoding='utf-8', errors='replace')
    assert 'All 20 required tests passed' in value, path
    assert 'required tests failed' not in value, path
    return value


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    assert digest(ROOT / 'dist' / JAR_NAME) == ORIGINAL_SHA, 'Existing release changed'
    passed_gametest(OUT / 'gametest-review-1-seed.log')
    passed_gametest(RUN / 'logs/latest.log')
    for name in ('gradle-review-1-seed.log', 'gradle-review-1-verify.log'):
        assert 'BUILD SUCCESSFUL' in passed_gametest(OUT / name), name

    junit = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': []}
    reports = sorted((BUILD / 'test-results/test').glob('TEST-*.xml'))
    assert reports
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in ('tests', 'failures', 'errors', 'skipped'):
            junit[key] += int(suite.attrib[key])
        junit['suites'].append({key: suite.attrib[key] for key in ('name', 'timestamp', 'tests')})
    assert junit['tests'] == 12 and not any(junit[key] for key in ('failures', 'errors', 'skipped')), junit
    review_failure = OUT / 'gametest-review-1-failed.log'
    failed_text = review_failure.read_text(encoding='utf-8', errors='replace')
    assert 'twodimensionssharebudgetswithoutcatchuporchunkloads failed' in failed_text and '1 required tests failed' in failed_text

    contract = json.loads((ROOT / 'contracts/entropy-shelter.v1.json').read_text(encoding='utf-8'))
    config_path = RUN / 'config/skycore-server.toml'
    assert tomllib.loads(config_path.read_text(encoding='utf-8'))['entropy'] == contract['config']['defaults']
    proof_path = RUN / 'world/skycore-entropy-restart-proof.json'
    proof = json.loads(proof_path.read_text(encoding='utf-8'))
    assert proof['seeded'] is True and proof['verified_after_jvm_restart'] is True

    with ZipFile(BUILD / 'libs' / JAR_NAME) as jar:
        names = jar.namelist()
        assert len(names) == len(set(names)), 'Duplicate ZIP entries'
        assert not any((name.startswith('data/') and '/recipe/' in name)
                       or name.endswith(('.nbt', '.jar')) or '/gametest/' in name
                       or 'EntropyTests' in name or 'EntropyRestartProbe' in name for name in names), 'QA/dependency leakage'
        classes = 0
        for name in names:
            if name.endswith('.json'):
                json.loads(jar.read(name))
            if name.endswith('.class'):
                assert int.from_bytes(jar.read(name)[6:8], 'big') == 65, name
                classes += 1
        assert classes > 0
        tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        for relative in ('tags/block/entropy_vulnerable_plants.json',
                         'damage_type/entropy_exposure.json', 'damage_type/entropy_explosion.json'):
            assert 'data/skycore/' + relative in names

    def resource_hashes():
        return {p.relative_to(ROOT).as_posix(): digest(p)
                for sub in ('src/main/resources', 'src/gameTest/resources')
                for p in (ROOT / sub).rglob('*') if p.is_file()}

    before = resource_hashes()
    subprocess.run([sys.executable, str(ROOT / 'tools/generate_resources.py')], cwd=ROOT, check=True)
    assert resource_hashes() == before, 'Generated resources drifted'
    checks = []
    for script in ('check_entropy_contract.py', 'check_pack_contract.py'):
        result = subprocess.run([sys.executable, str(ROOT / 'tools' / script)], cwd=ROOT,
                                capture_output=True, text=True, check=True)
        checks.append(script + '\n' + result.stdout + result.stderr)
    (OUT / 'contract-checks.log').write_text('\n'.join(checks), encoding='utf-8')

    shutil.copy2(BUILD / 'libs' / JAR_NAME, OUT / JAR_NAME)
    shutil.copy2(RUN / 'logs/latest.log', OUT / 'gametest-review-1-verify.log')
    shutil.copy2(config_path, OUT / 'skycore-server.toml')
    shutil.copy2(proof_path, OUT / 'restart-proof.json')
    for report in reports:
        shutil.copy2(report, OUT / report.name)

    artifact = OUT / JAR_NAME
    evidence = {p.relative_to(ROOT).as_posix(): digest(p) for p in sorted(OUT.iterdir())
                if p.is_file() and p.name != 'verification.json'}
    changed_files = CHANGED + sorted(evidence) + ['dist/entropy-core-step-2/verification.json']
    record = {
        'step': 2,
        'review_repair_round': 1,
        'status': 'core_implemented_ready_for_independent_review',
        'recorded_utc': datetime.now(timezone.utc).isoformat(),
        'environment': {'java': 'Microsoft OpenJDK 21.0.10', 'minecraft': '1.21.1',
                        'neoforge': '21.1.265', 'ftb_teams': '2101.1.12',
                        'qa_world': str(RUN / 'world')},
        'runtime_implemented': True,
        'production_installed': False,
        'pack_files_modified': False,
        'ecology_executor_implemented': False,
        'contract': {'path': 'contracts/entropy-shelter.v1.json',
                     'sha256': digest(ROOT / 'contracts/entropy-shelter.v1.json')},
        'artifact': {'path': artifact.relative_to(ROOT).as_posix(), 'sha256': digest(artifact),
                     'bytes': artifact.stat().st_size, 'review_preview_only': True,
                     'contains_qa_resources': False, 'class_major': 65},
        'original_release_sha256_unchanged': ORIGINAL_SHA,
        'checks': {'junit': junit, 'gametest': {'passed': 20, 'failed': 0, 'legacy': 7,
                                              'contract_runtime': 2, 'new_mechanism': 11},
                   'fresh_jvm_confirmation': '20/20 PASS', 'normal_jvm_restart': proof,
                   'default_scheduler_regression': {'dimensions': 3, 'enabled': 2, 'period': 1200,
                                                     'ticks': 7200, 'asserted_blasts_per_enabled_dimension': 3},
                   'interaction_dispatch': 'gameMode.useItemOn: empty hands, offhand shield/stone, cross-team denial, single execution',
                   'entropy_contract': 'PASS', 'old_pack_contract': 'PASS',
                   'actual_config_defaults': 'PASS', 'resource_regeneration': 'PASS',
                   'jar_structure_json_toml_and_isolation': 'PASS'},
        'not_verified': ['independent step-4 review and step-6 formal release',
                         'full-pack dedicated server and graphical client acceptance',
                         'pack FTB true-evidence task integration', '20-40 player load',
                         'whole-world power-loss transaction guarantees'],
        'retained_failure_evidence': [path for path in evidence
                                      if any(word in path for word in ('failed', 'intermittent'))],
        'source_sha256': {relative: digest(ROOT / relative) for relative in CHANGED},
        'evidence_sha256': evidence,
        'changed_files': changed_files,
    }
    (OUT / 'verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('PASS: JUnit 12/12, current seed/verify GameTest JVMs 20/20, restart proof, config, contracts, regeneration and JAR isolation.')
    print('Review preview SHA-256:', digest(artifact))
    print('Delivery record:', OUT / 'verification.json')


if __name__ == '__main__':
    main()
