"""Recheck locked artifacts and attribute existing QA evidence without launching Minecraft."""
import argparse
from collections import Counter
import csv
from datetime import datetime, timezone
import hashlib
from pathlib import Path
import re

from server_baseline import capture, read, require, sha, text, write
from server_results import summarize


def verify_manifest(directory, manifest):
    for name, digest in manifest.items():
        require(sha(directory / name) == digest, f'Evidence changed: {directory / name}')
    return len(manifest)


def profile_core(directory, jfr, filename='server.jfr', windowed=True):
    import json

    recording = directory / filename
    result = capture([str(jfr), 'print', '--json', '--stack-depth', '64', '--events',
                      'jdk.ExecutionSample', str(recording)], timeout=120)
    require(result.returncode == 0, result.stderr)
    metadata = read(directory / 'hotspots.json') if windowed else None
    if metadata:
        start = datetime.fromisoformat(metadata['sample_start_utc'])
        end = datetime.fromisoformat(metadata['sample_end_utc'])
    total = core = core_leaf = catalog = 0
    methods, leaves, descendants = Counter(), Counter(), Counter()
    for event in json.loads(result.stdout)['recording']['events']:
        values = event['values']
        if (values.get('sampledThread') or {}).get('javaName') != 'Server thread':
            continue
        if metadata and not start <= datetime.fromisoformat(values['startTime']) < end:
            continue
        frames = (values.get('stackTrace') or {}).get('frames') or []
        labels = [frame['method']['type']['name'].replace('/', '.') + '.' + frame['method']['name']
                  for frame in frames]
        if not labels:
            continue
        total += 1
        catalog += any('AlloyFurnaceRecipeCatalog' in label for label in labels)
        core_methods = {label for label in labels if label.startswith('dev.skycore.')}
        if core_methods:
            core += 1
            methods.update(core_methods)
            descendants[labels[0]] += 1
        if labels[0].startswith('dev.skycore.'):
            core_leaf += 1
            leaves[labels[0]] += 1
    if metadata:
        require(total == metadata['server_execution_samples_with_stack'] and
                core == metadata['skycore_inclusive_execution_samples'],
                f'Recomputed JFR counts differ: {directory}')
    return {'recording': str(recording), 'sha256': sha(recording),
            'server_execution_samples_with_stack': total,
            'core_inclusive_samples': core, 'core_inclusive_percent': core / total * 100,
            'core_leaf_samples': core_leaf, 'catalog_inclusive_samples': catalog,
            'core_methods_inclusive': methods.most_common(), 'core_leaf_methods': leaves.most_common(),
            'leaf_under_core_top20': descendants.most_common(20)}


def product_state(repository):
    paths = [path for path in (repository / 'src').rglob('*') if path.is_file()]
    paths += [repository / name for name in ('build.gradle', 'settings.gradle', 'gradle.properties')]
    files = {path.relative_to(repository).as_posix(): sha(path) for path in sorted(paths)}
    result = capture(['git', '-C', str(repository), 'diff', '--binary', '--', 'src',
                      'build.gradle', 'settings.gradle', 'gradle.properties'])
    require(result.returncode == 0, result.stderr)
    return {'files': files, 'existing_diff_sha256': hashlib.sha256(result.stdout.encode()).hexdigest()}


def main(pack, output):
    repository = Path(__file__).resolve().parents[2]
    before = product_state(repository)
    baseline = read(pack / 'docs/pack/performance/baseline.json')
    evidence = pack / 'perf-qa-20261125'
    server = evidence / 'server-baseline-v2'
    with (pack / baseline['mods_manifest']).open(encoding='utf-8-sig', newline='') as stream:
        lock = list(csv.DictReader(stream))
    core_name, core_sha = baseline['core']['file'], baseline['core']['sha256']
    templates = []
    expected_by_side = {}
    for side, directory in [('client', evidence / 'client-template/minecraft/mods'),
                            ('server', server / 'template/mods')]:
        expected = {row['file']: row['sha256'] for row in lock if row[side] == '1'}
        actual = {path.name: sha(path) for path in sorted(directory.glob('*.jar'))}
        require(actual == expected and actual[core_name] == core_sha, f'{side}: JAR lock differs')
        require(len(actual) == baseline['counts'][f'{side}_jars'], f'{side}: JAR count differs')
        expected_by_side[side] = expected
        templates.append({'side': side, 'mods': str(directory), 'verified_jars': len(actual),
                          'core_file': str(directory / core_name), 'core_sha256': actual[core_name]})
    clients = []
    for run in ['client-b2', 'client-b4', 'client-b6']:
        directory = evidence / 'runs' / run
        count = verify_manifest(directory, read(evidence / f'{run}-evidence.sha256.json'))
        require(read(directory / 'loaded-jars.json') == expected_by_side['client'], f'{run}: loaded JARs differ')
        timing = read(directory / 'timing.json')
        require(timing['forced'] and timing['exit_code'] == 124, f'{run}: exit evidence differs')
        dumps = []
        for path in sorted(directory.glob('save-threads-*.txt')):
            content = text(path)
            blocks = re.split(r'\n(?=")', content)
            render = next(block for block in blocks if block.startswith('"Render thread"'))
            integrated = next(block for block in blocks if block.startswith('"Server thread"'))
            dumps.append({'path': str(path), 'sha256': sha(path),
                          'core_frame_count': len(re.findall(r'(?m)^\s+at .*dev\.skycore', content)),
                          'render_in_disconnect': 'Minecraft.disconnect' in render,
                          'render_in_glfw_wait': 'glfwWaitEventsTimeout' in render,
                          'server_state': integrated.splitlines()[1].strip(),
                          'server_in_tick_wait': 'MinecraftServer.waitUntilNextTick' in integrated})
        require(len(dumps) == 2, f'{run}: expected two timeout thread dumps')
        clients.append({'run_id': run, 'verified_evidence_files': count, 'timeout_dumps': dumps})
    audit = summarize(server)
    require(audit['complete'], 'Incomplete baseline server results')
    require(audit == read(server / 'server-audit.json'), 'Server baseline audit changed')
    jfr = Path.home() / 'AppData/Roaming' / baseline['java_home_under_appdata'] / 'bin/jfr.exe'
    require(sha(jfr.parent / 'java.exe') == baseline['java_files']['bin/java.exe'], 'QA JDK changed')
    runs = []
    for row in audit['runs']:
        if 'workload' not in row:
            continue
        directory = server / 'runs' / row['run_id']
        for phase in ['initial', 'reopen']:
            require(read(directory / phase / 'loaded-jars.json')['jar_sha256'] == expected_by_side['server'],
                    f'{directory}/{phase}: loaded JARs differ')
        profile = profile_core(directory / 'initial', jfr)
        runs.append({'baseline': row, 'profile': profile})
        print(f"Verified {row['run_id']}: {profile['core_inclusive_samples']}/"
              f"{profile['server_execution_samples_with_stack']} Core-inclusive samples", flush=True)
    startup = profile_core(server / 'runs/server-pilot-stone/initial', jfr, 'startup-pilot.jfr', False)
    after = product_state(repository)
    require(before == after, 'Product source changed during the read-only audit')
    result = {'generated_utc': datetime.now(timezone.utc).isoformat(), 'decision': 'retain_locked_baseline',
              'new_core_candidate': None, 'baseline_core': baseline['core'],
              'baseline_lock_sha256': sha(pack / 'docs/pack/performance/baseline.json'),
              'mods_lock_sha256': sha(pack / baseline['mods_manifest']),
              'templates': templates, 'client_timeout_audit': clients, 'server_runs': runs,
              'startup_profile': startup, 'server_groups': audit['groups'],
              'product_source_unchanged_during_audit': True, 'product_source_at_audit': before,
              'limitations': ['Execution samples do not measure exclusive CPU time or expected MSPT savings.',
                              'Core causality for integrated-server save/quit remains unresolved.',
                              'No new candidate, GameTest run or B/C performance run in step 5.']}
    write(output, result)
    print(f'Wrote {output}', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pack', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    arguments = parser.parse_args()
    main(arguments.pack, arguments.output)
