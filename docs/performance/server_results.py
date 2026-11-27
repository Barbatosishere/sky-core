"""Verify dedicated-server run evidence and summarize results without rerunning games."""
import argparse
from collections import Counter
import csv
from datetime import datetime, timedelta
import math
from pathlib import Path
import re
import statistics

from server_baseline import FEED, NS, UUID, parse_ticks, read, require, sha, text, write


def event_time(events, event, phase='initial'):
    return next(entry for entry in events if entry['event'] == event and entry['phase'] == phase)


def audit(directory, row):
    import json

    run = directory.name
    initial, reopen = directory / 'initial', directory / 'reopen'
    evidence = read(directory / 'evidence.sha256.json')
    for name, digest in evidence.items():
        require(sha(directory / name) == digest, f'{run}: SHA-256 differs for {name}')
    if row['valid'] != 'true':
        return {'run_id': run, 'invalid_reason': row['invalid_reason']}

    window, ticks = parse_ticks(text(initial / 'kubejs-server.log'))
    with (initial / 'ticks.csv').open(newline='', encoding='ascii') as stream:
        tick_csv = [[int(value) for value in entry.values()] for entry in csv.DictReader(stream)]
    require(tick_csv == ticks and read(initial / 'tick-window.json') == window, f'{run}: tick export differs')
    durations = sorted(tick[2] / 1_000_000 for tick in ticks)
    median, p95 = statistics.median(durations), durations[math.ceil(.95 * len(durations)) - 1]
    require(len(ticks) == row['tick_count'] and f'{median:.6f}' == row['mspt_median'] and
            f'{p95:.6f}' == row['mspt_p95'], f'{run}: CSV summary differs from raw ticks')
    require(row['loaded_jars_manifest_sha256'] == sha(initial / 'loaded-jars.json'), f'{run}: JAR evidence hash differs')
    server_jars = read(initial / 'loaded-jars.json')['jar_sha256']
    require(len(server_jars) == 265 and server_jars['skycore-0.2.1-neoforge-1.21.1.jar'] == row['core_sha256'] and
            read(reopen / 'loaded-jars.json')['jar_sha256'] == server_jars, f'{run}: JAR set differs across restart')

    events = [json.loads(line) for line in text(directory / 'events.jsonl').splitlines()]
    exit_event, reopen_event = event_time(events, 'server_exit'), event_time(events, 'server_spawn', 'reopen')
    require(exit_event['status'] == event_time(events, 'server_exit', 'reopen')['status'] == 0 and
            exit_event['qpc_ns'] < reopen_event['qpc_ns'] and exit_event['pid'] != reopen_event['pid'],
            f'{run}: restart is not a clean cold restart')
    persistence = read(reopen / 'persistence.json')
    require(len(persistence['ftb_quest_data_byte_equal']) == 2, f'{run}: FTB Quest checkpoints missing')
    replies = [entry for entry in events if entry['event'] == 'rcon_reply']
    require(all(any(entry['phase'] == phase and entry['command'] == 'save-all flush' and 'Saved the game' in entry['reply']
                    for entry in replies) for phase in ('initial', 'reopen')), f'{run}: no save confirmation')
    feeds = [entry for entry in events if entry['event'] == 'machine_feed']
    player = row['workload'] == 'single-player-stone-workbench'
    require(persistence['qa_rejoined'] == player, f'{run}: wrong persistence scenario')
    if player:
        client_jars = read(initial / 'client-loaded-jars.json')['jar_sha256']
        require(len(client_jars) == 286 and read(reopen / 'client-loaded-jars.json')['jar_sha256'] == client_jars,
                f'{run}: client JAR evidence differs')
        require(len(feeds) == 14 and sum(entry['command'] == FEED and 'Modified block data' in entry['reply']
                                        for entry in replies) == 14, f'{run}: wrong feed count')
        require(any(entry['phase'] == 'reopen' and entry['command'] == 'list uuids' and UUID in entry['reply'] for entry in replies),
                f'{run}: locked UUID did not rejoin')
    else:
        require(not feeds and ' joined the game' not in text(initial / 'latest.log'), f'{run}: empty workload had a player')

    hotspot = read(initial / 'hotspots.json')
    start = datetime.fromisoformat(hotspot['sample_start_utc'])
    spawn = event_time(events, 'server_spawn')
    approximate_spawn = datetime.fromisoformat(spawn['utc'])
    gc = []
    for line in text(initial / 'gc.log').splitlines():
        pause = re.match(r'\[(\d+)ms\].*\[gc\s*\] GC\(\d+\) Pause .* ([\d.]+)ms$', line)
        if pause:
            time_ms, duration_ms = pause.groups()
            if start <= approximate_spawn + timedelta(milliseconds=int(time_ms)) < start + timedelta(seconds=300):
                gc.append(float(duration_ms))
    catalog = re.findall(r'Built alloy-furnace recipe catalog: .*elapsed=(\d+) ms', text(initial / 'latest.log'))
    startup_list = next(entry for entry in replies if entry['phase'] == 'initial' and entry['command'] == 'list uuids')
    return {'run_id': run, 'workload': row['workload'], 'ticks': len(ticks), 'mspt_median': median, 'mspt_p95': p95,
            'max_mspt': durations[-1], 'ticks_over_50ms': sum(value > 50 for value in durations),
            'jfr_start_offset_ms': hotspot['jfr_start_offset_ms'], 'jfr_server_samples': hotspot['server_execution_samples_with_stack'],
            'jfr_skycore_inclusive_samples': hotspot['skycore_inclusive_execution_samples'],
            'jfr_server_leaf_top12': hotspot['server_leaf_top30'][:12], 'gc_pauses_in_window_approx': len(gc),
            'max_gc_pause_in_window_ms_approx': max(gc, default=None),
            'startup_catalog_ms': list(map(int, catalog)),
            'startup_first_list_latency_ms': (startup_list['qpc_ns'] - startup_list['sent_qpc_ns']) / 1_000_000,
            'feed_max_dispatch_offset_ms': max((abs(entry['qpc_ns'] - entry['expected_qpc_ns']) / 1_000_000
                                                for entry in feeds if entry['expected_qpc_ns'] is not None), default=None),
            'save_flush_ms': [(entry['qpc_ns'] - entry['sent_qpc_ns']) / 1_000_000 for entry in replies
                              if entry['command'] == 'save-all flush'], 'reopen': row['reopen_result']}


def summarize(root):
    results = [audit(path.parent, read(path)) for path in sorted((root / 'runs').glob('*/result.json'))]
    accepted = [row for row in results if 'workload' in row]
    counts = Counter(row['workload'] for row in accepted)
    groups = {}
    for workload, count in sorted(counts.items()):
        by_metric = {'count': count}
        for metric in ('mspt_median', 'mspt_p95'):
            values = [row[metric] for row in accepted if row['workload'] == workload]
            by_metric[metric] = {'median_of_runs': statistics.median(values), 'min': min(values), 'max': max(values)}
        groups[workload] = by_metric
    return {'complete': counts == {'empty': 3, 'single-player-stone-workbench': 3}, 'groups': groups, 'runs': results}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence_root', type=Path)
    parser.add_argument('output', type=Path)
    arguments = parser.parse_args()
    write(arguments.output, summarize(arguments.evidence_root))
