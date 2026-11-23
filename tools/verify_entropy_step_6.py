"""Verify completed core release QA and collect evidence; never installs into the pack.

Run after test/build and the seed/verify GameTest JVMs have completed. Required logs
and the seed proof are supplied by the release operator in OUT. Only OUT and an
isolated temporary directory below BUILD are written; source and pack stay intact.
"""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
BUILD = Path('D:/MC/skycore-build/build')
RUN = Path('D:/MC/skycore-build/gametest')
OUT = ROOT / 'dist/entropy-core-step-6'
VERSION = '0.1.1'
JAR_NAME = f'skycore-{VERSION}-neoforge-1.21.1.jar'
OLD_JAR_NAME = 'skycore-0.1.0-neoforge-1.21.1.jar'
ORIGINAL_SHA = '4f1813259d32b5f9919a100a4a50e7091a58586a64944e1c4c29889befe7b67d'
PREVIEW_SHA = '67b53ff6f5e4f8fd55f083ae8f73d5012cee6752f9bdc463dc39104a5b8d3b51'
CONTRACT_SHA = 'a6e4740169a24c1232fed04e17ab8f3ad9eb66b5d412f6113a18505df5d22076'
CHANGED = [
    'README.md',
    'gradle.properties',
    'src/main/resources/META-INF/neoforge.mods.toml',
    'docs/entropy-core-step-6-verification.md',
    'tools/verify_entropy_step_6.py',
]


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def passed_gametest(path, gradle=False):
    value = path.read_text(encoding='utf-8-sig', errors='replace')
    require('All 20 required tests passed' in value, f'Missing 20/20 pass: {path}')
    require('required tests failed' not in value, f'GameTest failure: {path}')
    require('Stopping server' in value and 'All dimensions are saved' in value
            and 'Game test server shutting down' in value, f'No normal save/exit: {path}')
    if gradle:
        require('BUILD SUCCESSFUL' in value and 'BUILD FAILED' not in value,
                f'Gradle did not succeed: {path}')
        require(re.search(r'\bSky Core ' + re.escape(VERSION) + r' \(skycore\)', value),
                f'GameTest did not load the formal Sky Core version: {path}')
        require(re.search(r'\bNeoForge 21\.1\.265 \(neoforge\)', value)
                and re.search(r'\bjava version 21(?:\.|\s)', value),
                f'GameTest did not load the required NeoForge/Java runtime: {path}')
    return value


def check_junit():
    log = (OUT / 'gradle-test-build.log').read_text(encoding='utf-8-sig', errors='replace')
    require('BUILD SUCCESSFUL' in log and 'BUILD FAILED' not in log, 'Test/build Gradle failed')
    require(re.search(r'^> Task :test\s*$', log, re.MULTILINE),
            'JUnit task must execute, not be UP-TO-DATE/FROM-CACHE')
    reports = sorted((BUILD / 'test-results/test').glob('TEST-*.xml'))
    require(bool(reports), 'Missing JUnit XML reports')
    junit = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0, 'suites': []}
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in ('tests', 'failures', 'errors', 'skipped'):
            junit[key] += int(suite.attrib[key])
        junit['suites'].append({key: suite.attrib[key] for key in ('name', 'timestamp', 'tests')})
    require(junit['tests'] == 12 and not any(junit[key] for key in ('failures', 'errors', 'skipped')),
            f'JUnit must pass all 12 without skips: {junit}')
    return junit, reports


def check_jar():
    source = BUILD / 'libs' / JAR_NAME
    compiled_root = BUILD / 'classes/java/main'
    resource_root = ROOT / 'src/main/resources'
    compiled = {p.relative_to(compiled_root).as_posix(): p
                for p in compiled_root.rglob('*.class')}
    resources = {p.relative_to(resource_root).as_posix(): p
                 for p in resource_root.rglob('*') if p.is_file()}
    with ZipFile(source) as jar:
        names = jar.namelist()
        require(len(names) == len(set(names)), 'Duplicate JAR entries')
        leaks = [name for name in names if
                 (name.startswith('data/') and '/recipe/' in name)
                 or name.endswith(('.nbt', '.jar')) or '/gametest/' in name
                 or 'EntropyTests' in name or 'EntropyRestartProbe' in name]
        require(not leaks, f'QA/recipe/embedded dependency leakage: {leaks}')
        classes = {name for name in names if name.endswith('.class')}
        require(classes and classes == set(compiled), 'JAR class inventory differs from current compile')
        for name in classes:
            raw = jar.read(name)
            require(raw[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(raw[6:8], 'big') == 65,
                    f'Expected Java 21 class: {name}')
            require(raw == compiled[name].read_bytes(), f'JAR/current compile class mismatch: {name}')
        for name, path in resources.items():
            require(name in names and jar.read(name) == path.read_bytes(),
                    f'JAR/source resource mismatch: {name}')
        for name in names:
            if name.endswith('.json'):
                json.loads(jar.read(name))
            elif name.endswith('.toml'):
                tomllib.loads(jar.read(name).decode('utf-8'))
        metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
        require(len(metadata['mods']) == 1 and metadata['mods'][0]['modId'] == 'skycore'
                and metadata['mods'][0]['version'] == VERSION, 'Wrong release mod metadata')
        with ZipFile(ROOT / 'dist/entropy-core-step-2' / OLD_JAR_NAME) as preview:
            preview_classes = {name for name in preview.namelist() if name.endswith('.class')}
            preview_equivalent = classes == preview_classes and all(
                jar.read(name) == preview.read(name) for name in classes)
            require(preview_equivalent,
                    'Release classes differ from the independently reviewed preview; this release only changes the version')
    return {'class_count': len(classes), 'class_major': 65,
            'current_compile_class_equality': True, 'source_resource_equality': True,
            'reviewed_preview_class_equivalence': preview_equivalent,
            'json_toml_valid': True, 'duplicate_zip_entries': 0,
            'qa_recipe_or_dependency_leakage': [], 'mod_version': VERSION}


def check_generated_resources():
    # Run the unchanged generator in a disposable tree. No source file is rewritten.
    with tempfile.TemporaryDirectory(prefix='entropy-step6-generation-', dir=BUILD) as temporary:
        isolated = Path(temporary)
        (isolated / 'tools').mkdir()
        (isolated / 'contracts').mkdir()
        shutil.copy2(ROOT / 'tools/generate_resources.py', isolated / 'tools/generate_resources.py')
        for name in ('chapter-1.v1.json', 'entropy-shelter.v1.json'):
            shutil.copy2(ROOT / 'contracts' / name, isolated / 'contracts' / name)
        result = subprocess.run([sys.executable, '-X', 'utf8', str(isolated / 'tools/generate_resources.py')],
                                cwd=isolated, capture_output=True, text=True, encoding='utf-8', check=True)
        generated = sorted(p for p in (isolated / 'src').rglob('*') if p.is_file())
        require(bool(generated), 'Resource generator produced no files')
        for path in generated:
            relative = path.relative_to(isolated)
            require((ROOT / relative).is_file() and path.read_bytes() == (ROOT / relative).read_bytes(),
                    f'Generated resource drift: {relative}')
        (OUT / 'resource-regeneration.log').write_text(
            result.stdout + result.stderr
            + f'PASS: {len(generated)} generated files equal source bytes; generator ran only in isolated build tree.\n',
            encoding='utf-8')
    return {'status': 'PASS', 'generated_files_compared': len(generated),
            'mode': 'isolated temporary build tree; source files were not rewritten'}


def collect_source_hashes():
    files = {ROOT / name for name in ('README.md', 'build.gradle', 'gradle.properties', 'settings.gradle')}
    for subdirectory in ('src', 'contracts', 'docs', 'tools', 'gradle'):
        files.update(path for path in (ROOT / subdirectory).rglob('*')
                     if path.is_file() and '__pycache__' not in path.parts)
    return {path.relative_to(ROOT).as_posix(): digest(path) for path in sorted(files)}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    require(digest(ROOT / 'dist' / OLD_JAR_NAME) == ORIGINAL_SHA, 'Previous formal JAR changed')
    require(digest(ROOT / 'dist/entropy-core-step-2' / OLD_JAR_NAME) == PREVIEW_SHA,
            'Reviewed preview JAR changed')
    contract_path = ROOT / 'contracts/entropy-shelter.v1.json'
    require(digest(contract_path) == CONTRACT_SHA, 'Shared contract bytes changed')
    require(re.search(r'^mod_version=' + re.escape(VERSION) + r'\s*$',
                      (ROOT / 'gradle.properties').read_text(encoding='utf-8'), re.MULTILINE),
            'gradle.properties release version mismatch')
    contract = read_json(contract_path)
    junit, reports = check_junit()
    for phase in ('seed', 'verify'):
        passed_gametest(OUT / f'gametest-{phase}.log')
        passed_gametest(OUT / f'gradle-{phase}.log', gradle=True)

    proof_path = RUN / 'world/skycore-entropy-restart-proof.json'
    proof = read_json(proof_path)
    seed = read_json(OUT / 'restart-proof-seed.json')
    require(seed.get('seeded') is True and seed.get('verified_after_jvm_restart') is not True,
            'Seed proof must precede verify JVM')
    require(proof.get('seeded') is True and proof.get('verified_after_jvm_restart') is True,
            'No completed restart proof')
    require(all(seed[key] == proof[key] for key in ('owner', 'inverse', 'entropy', 'pos')),
            'Seed/verify identities do not match')
    config_path = RUN / 'config/skycore-server.toml'
    require(tomllib.loads(config_path.read_text(encoding='utf-8'))['entropy'] == contract['config']['defaults'],
            'Actual QA configuration differs from public defaults')

    checks = []
    for script in ('check_entropy_contract.py', 'check_pack_contract.py'):
        result = subprocess.run([sys.executable, '-X', 'utf8', str(ROOT / 'tools' / script)], cwd=ROOT,
                                capture_output=True, text=True, encoding='utf-8', check=True)
        checks.append(script + '\n' + result.stdout + result.stderr)
    (OUT / 'contract-checks.log').write_text('\n'.join(checks), encoding='utf-8')
    generated = check_generated_resources()
    jar_checks = check_jar()

    before, after = OUT / 'pack-files-before.json', OUT / 'pack-files-after.json'
    require(before.is_file() and after.is_file(), 'Both pack fingerprint snapshots are required')
    before_snapshot, after_snapshot = read_json(before), read_json(after)
    require(before_snapshot['root'] == after_snapshot['root']
            and before_snapshot['roots'] == after_snapshot['roots'],
            'Pack fingerprint root or protected directory list changed')
    require(Path(before_snapshot['root']).resolve() == Path(contract['ownership']['pack_root']).resolve()
            and before_snapshot['roots'], 'Pack fingerprint does not identify the contracted pack')
    before_files, after_files = before_snapshot['files'], after_snapshot['files']
    require(before_files and before_files == after_files, 'Pack file fingerprint changed')
    pack_check = {'status': 'PASS', 'writes_by_this_tool': False,
                  'root': before_snapshot['root'], 'roots': before_snapshot['roots'],
                  'protected_file_count': len(before_files), 'files_unchanged': True}

    for source, destination in ((BUILD / 'libs' / JAR_NAME, OUT / JAR_NAME),
                                (contract_path, OUT / 'entropy-shelter.v1.json'),
                                (config_path, OUT / 'skycore-server.toml'),
                                (proof_path, OUT / 'restart-proof.json')):
        shutil.copy2(source, destination)
    for report in reports:
        shutil.copy2(report, OUT / report.name)

    evidence = {path.relative_to(ROOT).as_posix(): digest(path) for path in sorted(OUT.iterdir())
                if path.is_file() and path.name not in ('verification.json', 'SHA256SUMS')}
    changed = CHANGED + sorted(evidence) + [
        'dist/entropy-core-step-6/verification.json', 'dist/entropy-core-step-6/SHA256SUMS']
    artifact = OUT / JAR_NAME
    record = {
        'step': 6, 'status': 'core_formally_released_ready_for_pack_installation_and_qa',
        'recorded_utc': datetime.now(timezone.utc).isoformat(), 'release_version': VERSION,
        'environment': {'java': 'Microsoft OpenJDK 21.0.10', 'minecraft': '1.21.1',
                        'neoforge': '21.1.265', 'ftb_teams': '2101.1.12',
                        'qa_world': str(RUN / 'world')},
        'production_installed': False, 'pack_files_modified': False,
        'runtime_implemented': True, 'ecology_executor_implemented': False,
        'contract': {'path': 'contracts/entropy-shelter.v1.json', 'sha256': CONTRACT_SHA,
                     'delivery_copy': 'dist/entropy-core-step-6/entropy-shelter.v1.json'},
        'artifact': {'path': artifact.relative_to(ROOT).as_posix(), 'sha256': digest(artifact),
                     'bytes': artifact.stat().st_size, 'review_preview_only': False,
                     'contains_qa_resources': False, 'class_major': 65},
        'previous_formal_release_sha256_unchanged': ORIGINAL_SHA,
        'reviewed_preview_sha256_unchanged': PREVIEW_SHA,
        'checks': {'junit': junit, 'gametest_seed': {'passed': 20, 'failed': 0},
                   'gametest_verify': {'passed': 20, 'failed': 0},
                   'both_gametest_jvms_loaded_formal_version_and_required_runtime': True,
                   'normal_jvm_restart': proof, 'seed_verify_identity_match': True,
                   'actual_config_defaults': 'PASS', 'entropy_contract': 'PASS',
                   'legacy_pack_contract': 'PASS', 'resource_regeneration': generated,
                   'jar': jar_checks, 'pack_fingerprint': pack_check},
        'scope_limits': ['Core QA only; full-pack dedicated server and graphical client acceptance belong to steps 7/8.',
                         'No production world or pack installation was modified.',
                         'Ecology decay executor, climate facilities and independent team-world generation remain unimplemented.'],
        'not_verified': ['full-pack dedicated server and graphical client acceptance',
                         'new core actual actions through Rhino/FTB true-evidence task synchronization',
                         'full-pack restart and complete first-chapter playthrough',
                         '20-40 player load', 'whole-world operating-system power-loss transaction guarantees'],
        'source_sha256': collect_source_hashes(), 'evidence_sha256': evidence,
        'checksum_policy': 'SHA256SUMS covers every delivered file in this directory except itself, including verification.json; verification.json evidence_sha256 excludes itself and SHA256SUMS to avoid circular hashes.',
        'changed_files': changed,
    }
    (OUT / 'verification.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    sums = [f'{digest(path)}  {path.name}\n' for path in sorted(OUT.iterdir())
            if path.is_file() and path.name != 'SHA256SUMS']
    (OUT / 'SHA256SUMS').write_text(''.join(sums), encoding='utf-8')
    print('PASS: JUnit 12/12; seed/verify GameTest JVMs 20/20 each; normal restart; contracts/config; isolated generation; formal JAR audit.')
    print('Formal release SHA-256:', digest(artifact))
    print('Delivery record:', OUT / 'verification.json')


if __name__ == '__main__':
    main()
