"""Run dedicated-server performance QA against the shared pack lock."""
import argparse
from collections import Counter
import csv
import ctypes
from ctypes import wintypes
from datetime import datetime, timedelta, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import statistics
import struct
import subprocess
import time

PORT, RCON = 25970, 25971
WIN_ARGS = 'libraries/net/neoforged/neoforge/21.1.265/win_args.txt'
PLAYER = 'SkyQaAlpha'
UUID = '6771e4b5-2b06-3e77-9b72-d1154676d8f1'
DIM = 'skycore:team/00/sky'
STAND = (10.5, 104, 8.5)
STAND_SHELL = f'execute in {DIM} run fill 9 103 7 11 106 9 minecraft:glass keep'
STAND_INTERIOR = f'execute in {DIM} run fill 10 104 8 10 105 8 minecraft:air replace minecraft:glass'
BLOCK = f'execute in {DIM} run data get block 10 100 8'
FEED = (f'execute in {DIM} run data merge block 10 100 8 '
        '{OwnerTeam:[I;1735517365,721829495,-1686974187,1182193905],'
        'Items:[{Slot:0b,id:"skycore:stone_fragment",count:4},'
        '{Slot:4b,id:"skycore:primordial_fuel",count:1}]}')
NS = 1_000_000_000
HIDDEN = subprocess.CREATE_NO_WINDOW
DISCOVERY = re.compile(r'Found (?:mod|library|gamelibrary) file "([^"\r\n]+)" \[locator: \{mods folder locator at ([^}\r\n]+)\}')


def require(condition, detail):
    if not condition:
        raise RuntimeError(detail)


def utc():
    return datetime.now(timezone.utc).isoformat()


def text(path):
    return path.read_text(encoding='utf-8-sig', errors='replace') if path.exists() else ''


def read(path):
    return json.loads(text(path))


def write(path, data):
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def tree(path, exclude=()):
    return {file.relative_to(path).as_posix(): sha(file) for file in sorted(path.rglob('*'))
            if file.is_file() and file.relative_to(path).as_posix() not in exclude}


def copy(source, dest, expected):
    for name, digest in expected.items():
        target = dest / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / name, target)
        require(sha(target) == digest, f'Hash mismatch: {target}')


def capture(args, timeout=60):
    return subprocess.run(args, capture_output=True, encoding='utf-8', errors='replace',
                          creationflags=HIDDEN, timeout=timeout)


def java_pids():
    result = capture(['tasklist.exe', '/FO', 'CSV', '/NH'])
    require(result.returncode == 0, result.stderr)
    return {int(row[1]) for row in csv.reader(result.stdout.splitlines())
            if len(row) >= 2 and row[0].lower() in ('java.exe', 'javaw.exe')}


def memory():
    class Status(ctypes.Structure):
        _fields_ = [('length', wintypes.DWORD), ('load', wintypes.DWORD)] + [
            (field, ctypes.c_ulonglong) for field in ('physical', 'available', 'total_page', 'free_page',
                                                      'total_virtual', 'free_virtual', 'extended_virtual')]

    status = Status()
    status.length = ctypes.sizeof(status)
    require(ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)), 'Failed to read host memory')
    return {'physical_bytes': status.physical, 'available_bytes': status.available}


def wait_for(check, timeout, detail):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if check():
            return
        time.sleep(0.5)
    raise TimeoutError(detail)


def sleep_until(deadline):
    time.sleep(max(0, (deadline - time.perf_counter_ns()) / NS))


def rcon(password, command):
    def frame(identifier, kind, value):
        payload = struct.pack('<ii', identifier, kind) + value.encode() + b'\0\0'
        return struct.pack('<i', len(payload)) + payload

    with socket.create_connection(('127.0.0.1', RCON), timeout=35) as connection, connection.makefile('rb') as stream:
        def receive():
            header = stream.read(4)
            require(len(header) == 4, 'Truncated RCON header')
            size, = struct.unpack('<i', header)
            require(10 <= size <= 4194304, f'Invalid RCON frame: {size}')
            raw = stream.read(size)
            require(len(raw) == size and raw[-2:] == b'\0\0', 'Truncated RCON response')
            return *struct.unpack('<ii', raw[:8]), raw[8:-2]

        connection.sendall(frame(11, 3, password))
        while True:
            identifier, kind, _ = receive()
            require(identifier != -1, 'QA RCON authentication failed')
            if identifier == 11 and kind == 2:
                break
        connection.sendall(frame(12, 2, command))
        identifier, _, data = receive()
        require(identifier == 12, 'Unexpected RCON response ID')
        require(len(data) < 4096, 'This QA command requires a single RCON response')
    return data.decode('utf-8', errors='replace')


class Fixture:
    def __init__(self, root, output):
        self.root = root.resolve()
        self.docs = self.root / 'docs/pack/performance'
        self.qa = self.root / 'perf-qa-20261125'
        self.output = output.resolve()
        self.template = self.output / 'template'
        self.base = read(self.docs / 'baseline.json')
        self.archive = self.root / self.base['archive_runs'][0]
        self.server_archive = self.archive / 'runtime/server'
        self.world = self.root / self.base['world']
        self.world_index = self.root / self.base['world_index']
        self.snapshot = read(self.world_index)['files']
        self.java = Path(os.environ['APPDATA']) / self.base['java_home_under_appdata'] / 'bin'
        mods = list(csv.DictReader(text(self.root / self.base['mods_manifest']).splitlines()))
        self.server_mods = {row['file']: row['sha256'] for row in mods if row['server'] == '1'}
        self.client_mods = {row['file']: row['sha256'] for row in mods if row['client'] == '1'}
        require(len(self.server_mods) == 265 and len(self.client_mods) == 286, 'Wrong mod counts')
        self.core = self.base['core']['sha256']
        for mods in (self.server_mods, self.client_mods):
            require(mods[self.base['core']['file']] == self.core, 'Wrong Core digest')

    def build(self):
        require(not java_pids(), 'Other Java process running; do not overlap QA')
        for name, digest in self.base['files'].items():
            require(sha(self.root / name) == digest, name)
        for name, digest in self.base['java_files'].items():
            require(sha(self.java.parent / name) == digest, name)
        require(tree(self.world, ('session.lock',)) == self.snapshot, 'QA world differs from lock')
        require({path.name: sha(path) for path in (self.server_archive / 'mods').glob('*.jar')} == self.server_mods,
                'Archive server mods differ from lock')
        payload = {name.removeprefix('minecraft/'): digest for digest, name in
                   (line.split(maxsplit=1) for line in text(self.root / self.base['payload_manifest']).splitlines())}
        require(len(payload) == 726, 'Wrong payload count')
        self.template.mkdir(parents=True)
        copy(self.root / 'minecraft', self.template, payload)
        copy(self.server_archive / 'mods', self.template / 'mods', self.server_mods)
        copy(self.server_archive / 'libraries', self.template / 'libraries', tree(self.server_archive / 'libraries'))
        shutil.copyfile(self.server_archive / 'eula.txt', self.template / 'eula.txt')
        require('eula=true' in text(self.template / 'eula.txt').splitlines(), 'QA EULA not accepted')
        shutil.copyfile(Path(__file__).with_name('98_perfticks.js'), self.template / 'kubejs/server_scripts/98_perfticks.js')
        settings = dict(line.split('=', 1) for line in text(self.server_archive / 'server.properties').splitlines()
                        if line and not line.startswith('#') and '=' in line)
        settings.update({'server-ip': '127.0.0.1', 'server-port': str(PORT), 'rcon.port': str(RCON),
                         'rcon.password': '<per-run>', 'enable-rcon': 'true', 'online-mode': 'false',
                         'view-distance': '4', 'simulation-distance': '4', 'difficulty': 'easy',
                         'gamemode': 'survival', 'sync-chunk-writes': 'true', 'level-name': 'world'})
        (self.template / 'server.properties').write_text(''.join(f'{key}={value}\n' for key, value in sorted(settings.items())),
                                                         encoding='ascii')
        write(self.output / 'server-template.sha256.json', tree(self.template))
        version = capture([str(self.java / 'java.exe'), '-version'])
        require(version.returncode == 0 and '21.0.7' in version.stderr, version.stderr)
        write(self.output / 'host-server.json', {'utc': utc(), **memory(), 'java': version.stderr.strip(),
              'java_files': self.base['java_files'], 'host_step_2': read(self.qa / 'host.json'),
              'power_now': capture(['powercfg.exe', '/getactivescheme']).stdout.strip(),
              'gpu_now': capture(['nvidia-smi.exe', '--query-gpu=name,driver_version', '--format=csv']).stdout.strip(),
              'disk_bytes': list(shutil.disk_usage(self.output)), 'jfr_sha256': sha(self.java / 'jfr.exe'),
              'jcmd_sha256': sha(self.java / 'jcmd.exe'), 'launcher_source_sha256': sha(self.archive / 'first-SkyQaAlpha.args')})

    def server_args(self):
        return [str(self.java / 'java.exe'), *self.base['server_jvm'], self.base['gc_jvm_arg'], '@' + WIN_ARGS,
                'nogui', '--world', 'world']

    def discover(self, game, evidence, expected):
        log = text(game / 'logs/debug.log')
        mods = (game / 'mods').resolve()
        found = {name for name, where in DISCOVERY.findall(log) if Path(where).resolve() == mods}
        for line in log.splitlines():
            if 'Found additional transformation services from discovery services:' in line:
                found.update(path.name for path in map(Path, re.findall(r'\[([^\[\]]+\.jar)\]', line))
                             if path.parent.resolve() == mods)
        actual = {name: sha(mods / name) for name in sorted(found)}
        write(evidence, {'jar_sha256': actual, 'missing': sorted(expected.keys() - found), 'unexpected': sorted(found - expected.keys())})
        require(actual == expected, f'Loaded JARs differ: {evidence}')

    def client(self, directory):
        dest = directory / 'client'
        copy(self.qa / 'client-template', dest, read(self.qa / 'client-template.sha256.json'))
        args = [json.loads(line) for line in text(self.archive / 'first-SkyQaAlpha.args').splitlines() if line]
        original = (Path(os.environ['APPDATA']) / 'PrismLauncher').as_posix()
        isolated = (self.qa / 'prism').as_posix()
        args = [arg.replace(original, isolated) for arg in args if not arg.startswith('-Djava.io.tmpdir=')]
        scratch = dest / 'javatmp'
        scratch.mkdir()
        args[:0] = [self.base['gc_jvm_arg'], f'-Djava.io.tmpdir={scratch.as_posix()}']
        values = {'--accessToken': '0', '--username': PLAYER, '--uuid': UUID.replace('-', ''),
                  '--gameDir': (dest / 'minecraft').as_posix(), '--quickPlayMultiplayer': f'127.0.0.1:{PORT}'}
        for flag, value in values.items():
            args[args.index(flag) + 1] = value
        libraries = self.qa / 'prism/libraries'
        lock = read(self.qa / 'prism-libraries.sha256.json')
        filenames = args[args.index('-cp') + 1].split(';') + [arg.split('=', 1)[1] for arg in args
                    if arg.startswith(('-Dforgewrapper.installer=', '-Dforgewrapper.minecraft='))]
        for filename in filenames:
            path = Path(filename)
            require(sha(path) == lock[path.relative_to(libraries).as_posix()], f'Client library changed: {path}')
        argfile = directory / 'client.args'
        argfile.write_text('\n'.join(json.dumps(arg) for arg in args) + '\n', encoding='utf-8')
        return dest / 'minecraft', argfile


class Run:
    def __init__(self, fixture, name, player):
        self.fx = fixture
        self.directory = fixture.output / 'runs' / name
        self.game = self.directory / 'server'
        self.player = player
        self.server = None
        self.client = None
        self.password = secrets.token_hex(24)
        self.phase = 'initial'
        self.row = {'run_id': name, 'side': 'server', 'variant': 'baseline',
                    'workload': 'single-player-stone-workbench' if player else 'empty'}

    def event(self, kind, **values):
        entry = {'phase': self.phase, 'event': kind, 'utc': utc(), 'qpc_ns': time.perf_counter_ns(), **values}
        with (self.directory / 'events.jsonl').open('a', encoding='utf-8') as stream:
            stream.write(json.dumps(entry, ensure_ascii=False) + '\n')
        return entry['qpc_ns']

    def command(self, command):
        require(self.server.poll() is None, 'QA server is not running')
        sent = self.event('rcon_send', command=command)
        reply = rcon(self.password, command)
        self.event('rcon_reply', command=command, sent_qpc_ns=sent, reply=reply)
        return reply

    def population(self, count):
        reply = self.command('list uuids')
        require(re.search(rf'There are {count} of a max of \d+ players online', reply), reply)
        if count:
            require(PLAYER in reply and UUID in reply, reply)

    def prepare(self):
        require(not java_pids(), 'Do not overlap Java runs')
        for port in (PORT, RCON):
            with socket.socket() as probe:
                probe.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
                probe.bind(('127.0.0.1', port))
        self.directory.mkdir(parents=True)
        index = self.fx.output / 'server-template.sha256.json'
        locked = read(index)
        copy(self.fx.template, self.game, locked)
        require(tree(self.game) == locked, 'Restored template differs from lock')
        copy(self.fx.world, self.game / 'world', self.fx.snapshot)
        require(tree(self.game / 'world') == self.fx.snapshot, 'Restored world differs from lock')
        properties = self.game / 'server.properties'
        properties.write_text(text(properties).replace('rcon.password=<per-run>', 'rcon.password=' + self.password), encoding='ascii')
        self.row.update(template_manifest_sha256=sha(index), snapshot_manifest_sha256=sha(self.fx.world_index), core_sha256=self.fx.core)
        if self.player:
            self.client_game, self.client_args = self.fx.client(self.directory)
        write(self.directory / 'environment.json', {'server_args': self.fx.server_args(), 'server_working_directory': str(self.game),
              'memory_at_launch': memory(),
              'win_args_sha256': sha(self.game / WIN_ARGS), 'client_args': 'client.args' if self.player else None,
              'perfticks_sha256': sha(self.game / 'kubejs/server_scripts/98_perfticks.js')})

    def evidence(self):
        path = self.directory / self.phase
        path.mkdir(exist_ok=True)
        return path

    def launch(self):
        evidence = self.evidence()
        started = utc()
        self.server_console = evidence / 'console.log'
        with self.server_console.open('wb') as stream:
            self.server = subprocess.Popen(self.fx.server_args(), cwd=self.game, stdin=subprocess.DEVNULL,
                                           stdout=stream, stderr=subprocess.STDOUT, creationflags=HIDDEN)
        self.event('server_spawn', pid=self.server.pid)
        if self.phase == 'initial':
            self.row.update(started_utc=started, game_pid=self.server.pid)

        def ready():
            require(self.server.poll() is None, 'Dedicated JVM exited before Done')
            log = text(self.server_console)
            return 'For help, type "help"' in log and 'RCON running on' in log

        wait_for(ready, 420, 'Dedicated JVM did not reach Done/RCON')
        self.population(0)
        self.fx.discover(self.game, evidence / 'loaded-jars.json', self.fx.server_mods)
        if self.phase == 'initial':
            self.row['loaded_jars_manifest_sha256'] = sha(evidence / 'loaded-jars.json')
        self.event('server_ready', pid=self.server.pid)

    def stop_server(self):
        require('Saved the game' in self.command('save-all flush'), 'QA save did not confirm success')
        self.row['save_result'] = 'Saved the game'
        self.command('stop')
        status = self.server.wait(120)
        self.event('server_exit', pid=self.server.pid, status=status)
        self.row['exit_code'] = status
        require(status == 0, f'Dedicated server exit {status}')

    def archive(self):
        evidence = self.evidence()
        for original, name in {'logs/latest.log': 'latest.log', 'logs/debug.log': 'debug.log',
                               'logs/kubejs/server.log': 'kubejs-server.log', 'gc.log': 'gc.log'}.items():
            if (self.game / original).is_file():
                shutil.copyfile(self.game / original, evidence / name)
        if self.player and hasattr(self, 'client_game'):
            for original in ('logs/latest.log', 'logs/debug.log', 'gc.log'):
                if (self.client_game / original).is_file():
                    shutil.copyfile(self.client_game / original, evidence / ('client-' + Path(original).name))
        errors = [line for line in text(evidence / 'latest.log').splitlines()
                  if re.search(r'/(ERROR|FATAL)\]|Can.t keep up!|Watchdog|OutOfMemoryError', line)]
        (evidence / 'errors.log').write_text('\n'.join(errors) + '\n', encoding='utf-8')

    def cleanup(self):
        if self.server is not None and self.server.poll() is None:
            try:
                self.command('stop')
                self.server.wait(90)
            except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
                self.event('server_forced_exit', pid=self.server.pid, detail=repr(error))
                self.server.terminate()
                self.server.wait(30)
        self.stop_client()
        self.archive()

    def stop_client(self):
        if self.client is not None and self.client.poll() is None:
            user32 = ctypes.windll.user32
            callback = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)

            @callback
            def close_owned(hwnd, _):
                pid = wintypes.DWORD()
                user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
                if pid.value == self.client.pid and user32.IsWindowVisible(hwnd):
                    user32.PostMessageW(hwnd, 0x10, 0, 0)
                return True

            user32.EnumWindows(close_owned, 0)
            try:
                self.event('client_exit', pid=self.client.pid, status=self.client.wait(60))
            except subprocess.TimeoutExpired:
                report = capture([str(self.fx.java / 'jcmd.exe'), str(self.client.pid), 'Thread.print', '-l'])
                (self.evidence() / 'client-exit-threads.txt').write_text(report.stdout + report.stderr, encoding='utf-8')
                self.client.terminate()
                self.event('client_forced_exit', pid=self.client.pid, status=self.client.wait(30))

    def position(self, expected=None):
        reply = self.command(f'execute in {DIM} if entity @a[name={PLAYER},distance=0..30000000] run data get entity {PLAYER} Pos')
        match = re.search(r'\[(-?[\d.]+)d, (-?[\d.]+)d, (-?[\d.]+)d\]', reply)
        require(match is not None, f'QA player is not in {DIM}: {reply}')
        coords = tuple(map(float, match.groups()))
        if expected is not None:
            require(math.floor(coords[0]) == math.floor(expected[0]) and abs(coords[1] - expected[1]) < 0.001 and
                    math.floor(coords[2]) == math.floor(expected[2]), f'QA player left the fixed standing block: {coords}')
        return coords

    def join(self):
        evidence = self.evidence()
        with (evidence / 'client-console.log').open('wb') as stream:
            self.client = subprocess.Popen([str(self.fx.java / 'javaw.exe'), '@' + str(self.client_args)],
                                           cwd=self.client_game, stdout=stream, stderr=subprocess.STDOUT)
        self.event('client_spawn', pid=self.client.pid)

        def joined():
            require(self.client.poll() is None and self.server.poll() is None, 'Client or server exited before QA join')
            return re.search(rf'\b{PLAYER}(?: \(formerly known as [^\r\n]+\))? joined the game', text(self.server_console))

        wait_for(joined, 420, 'QA client did not join')
        self.population(1)
        self.fx.discover(self.client_game, evidence / 'client-loaded-jars.json', self.fx.client_mods)
        wait_for(lambda: 'Starting JEI took' in text(self.client_game / 'logs/latest.log'), 180,
                 'QA client did not finish JEI initialization')
        self.event('client_ready', pid=self.client.pid)

    def workbench(self, completed=False):
        reply = self.command(BLOCK)
        require('id: "skycore:stone_workbench"' in reply, reply)
        if completed:
            items = sorted((int(slot), item, int(count)) for count, slot, item in
                           re.findall(r'\{count: (\d+), Slot: (\d+)b, id: "([^"]+)"\}', reply))
            require(items == [(5, 'minecraft:quartz', 1), (6, 'minecraft:redstone', 1)], reply)
        return reply

    def feed(self, deadline=None):
        sent = self.event('machine_feed', expected_qpc_ns=deadline)
        if deadline is not None:
            require(abs(sent - deadline) <= NS, f'Feed missed schedule by {(sent - deadline)/NS:.3f}s')
        require('Modified block data' in self.command(FEED), 'Workbench did not accept scheduled input')

    def position_for_workload(self):
        stand = ' '.join(map(str, STAND))
        require('Successfully filled 36 block(s)' in self.command(STAND_SHELL), 'QA standing enclosure overlapped the original world')
        require('Successfully filled 2 block(s)' in self.command(STAND_INTERIOR), 'QA standing enclosure interior not cleared')
        reply = self.command(f'execute in {DIM} run tp {PLAYER} {stand} 0 0')
        require('Teleported' in reply, reply)
        time.sleep(2)
        self.position(STAND)

    def measure(self):
        if self.player:
            self.join()
            self.position_for_workload()
            self.workbench()
            self.feed()
        else:
            self.population(0)
        require(java_pids() == ({self.server.pid, self.client.pid} if self.player else {self.server.pid}),
                'Unexpected Java process at warmup')
        before = self.event('warmup_dispatch')
        self.command('perfticks')
        after = self.event('warmup_ack')
        log = self.perf_log()
        wait_for(lambda: 'PERF_START ' in text(log), 12, 'Sampling command did not log PERF_START')
        start = int(re.search(r'PERF_START (\d+)', text(log)).group(1))
        require(before <= start <= after, f'Java and Python monotonic clocks do not align: {before}, {start}, {after}')
        self.event('clock_alignment', java_start_ns=start, dispatch_qpc_ns=before, ack_qpc_ns=after)
        evidence = self.evidence()
        recording = evidence / 'server.jfr'
        attach_log = evidence / 'jcmd-jfr-start.log'
        jfr = None
        for offset in range(30 if self.player else 120, 420, 30):
            deadline = start + offset * NS
            if self.player:
                sleep_until(deadline - NS)
                self.population(1)
                self.position(STAND)
                self.workbench(completed=True)
            sleep_until(deadline)
            if offset == 120:
                with attach_log.open('wb') as stream:
                    jfr = subprocess.Popen([str(self.fx.java / 'jcmd.exe'), str(self.server.pid), 'JFR.start',
                                            'name=perf', 'settings=profile', 'duration=300s', f'filename={recording}'],
                                           stdout=stream, stderr=subprocess.STDOUT, creationflags=HIDDEN)
                self.event('jfr_dispatch', pid=jfr.pid, scheduled_qpc_ns=deadline,
                           dispatch_offset_ns=time.perf_counter_ns() - deadline)
            if self.player:
                self.feed(deadline)
        sleep_until(start + 420 * NS)
        wait_for(lambda: 'PERF_WINDOW ' in text(log), 20, 'Tick window did not finish')
        self.event('tick_window_observed')
        self.population(1 if self.player else 0)
        if self.player:
            self.position(STAND)
            self.workbench(completed=True)
        require(jfr is not None and jfr.wait(30) == 0, f'JFR.start failed: {text(attach_log)}')
        wait_for(lambda: recording.is_file() and recording.stat().st_size > 0, 30, 'JFR did not write a recording')
        self.event('jfr_file_observed', size=recording.stat().st_size)
        window, ticks = parse_ticks(text(log))
        with (evidence / 'ticks.csv').open('w', newline='', encoding='ascii') as stream:
            writer = csv.writer(stream)
            writer.writerow(('tick', 'window_offset_ns', 'duration_ns'))
            writer.writerows(ticks)
        write(evidence / 'tick-window.json', window)
        values = sorted(row[2] / 1_000_000 for row in ticks)
        self.row.update(warmup_start_ns=window['warmupStartNs'], sample_start_ns=window['sampleStartNs'],
                        sample_end_ns=window['sampleEndNs'], tick_count=len(ticks),
                        mspt_median=f'{statistics.median(values):.6f}', mspt_p95=f'{values[math.ceil(.95 * len(values)) - 1]:.6f}')

    def perf_log(self):
        return self.game / 'logs/kubejs/server.log'

    def profile(self):
        evidence = self.directory / 'initial'
        jfr = str(self.fx.java / 'jfr.exe')
        file = evidence / 'server.jfr'
        report = capture([jfr, 'summary', str(file)])
        (evidence / 'jfr-summary.txt').write_text(report.stdout + report.stderr, encoding='utf-8')
        require(report.returncode == 0, 'JFR summary failed')
        require(re.search(r'Duration:\s+300 s\b', report.stdout), 'JFR lacks the full 300-second recording')
        report = capture([jfr, 'print', '--json', '--stack-depth', '64', '--events',
                          'jdk.ExecutionSample,jdk.NativeMethodSample,jdk.ActiveRecording', str(file)], timeout=120)
        require(report.returncode == 0, report.stderr)
        dispatch = next(event for event in map(json.loads, text(self.directory / 'events.jsonl').splitlines())
                        if event['event'] == 'jfr_dispatch')
        sample_start = datetime.fromisoformat(dispatch['utc']) - timedelta(microseconds=dispatch['dispatch_offset_ns'] / 1000)
        sample_end = sample_start + timedelta(seconds=300)
        leaves, inclusive, threads, event_types = Counter(), Counter(), Counter(), Counter()
        core_samples = 0
        active_recordings = []
        for event in json.loads(report.stdout)['recording']['events']:
            values = event['values']
            if event['type'] == 'jdk.ActiveRecording':
                active_recordings.append(values)
                continue
            if not sample_start <= datetime.fromisoformat(values['startTime']) < sample_end:
                continue
            thread = values.get('sampledThread') or {}
            name = thread.get('javaName') or thread.get('osName') or '<unknown>'
            threads[name] += 1
            if name != 'Server thread':
                continue
            event_types[event['type']] += 1
            if event['type'] != 'jdk.ExecutionSample':
                continue
            frames = (values.get('stackTrace') or {}).get('frames') or []
            labels = [frame['method']['type']['name'].replace('/', '.') + '.' + frame['method']['name'] for frame in frames]
            if labels:
                leaves[labels[0]] += 1
                inclusive.update(set(labels))
                core_samples += any(label.startswith('dev.skycore.') for label in labels)
        require(active_recordings and any(item['name'] == 'perf' for item in active_recordings), 'Missing perf recording metadata')
        recording_start = datetime.fromisoformat(next(item['recordingStart'] for item in active_recordings if item['name'] == 'perf'))
        write(evidence / 'hotspots.json', {'samples_by_thread': dict(threads.most_common()),
              'sample_start_utc': sample_start.isoformat(), 'sample_end_utc': sample_end.isoformat(),
              'jfr_start_offset_ms': (recording_start - sample_start).total_seconds() * 1000,
              'server_leaf_top30': leaves.most_common(30), 'server_inclusive_top60': inclusive.most_common(60),
              'server_execution_samples_with_stack': leaves.total(), 'server_event_types': dict(event_types),
              'skycore_inclusive_execution_samples': core_samples, 'active_recordings': active_recordings})

    def reopen(self):
        world = self.game / 'world'
        checkpoint = ['level.dat', f'playerdata/{UUID}.dat', 'data/skycore_teams.dat', 'data/skycore_journey_worlds.dat']
        checkpoint += [name for name in self.fx.snapshot if name.startswith(('ftbteams/', 'ftbquests/'))]
        saved = {name: sha(world / name) for name in checkpoint if (world / name).is_file()}
        require('level.dat' in saved, 'Saved world missing level.dat')
        self.phase = 'reopen'
        self.launch()
        if self.player:
            self.join()
            self.position(STAND)
            self.workbench(completed=True)
        else:
            require('The time is' in self.command(f'execute in {DIM} run time query gametime'), 'QA team world did not reopen')
        self.stop_server()
        after = {name: sha(world / name) for name in saved if (world / name).is_file()}
        require(saved.keys() == after.keys(), 'Checkpoint file missing after cold restart')
        quests = {name for name in saved if name.startswith('ftbquests/')}
        require(len(quests) == 2 and all(saved[name] == after[name] for name in quests), 'FTB quest data changed on cold restart')
        write(self.evidence() / 'persistence.json', {'same_world_path': str(world), 'new_pid': self.server.pid,
              'saved_before_restart': saved, 'qa_rejoined': self.player,
              'saved_after_restart': after, 'ftb_quest_data_byte_equal': sorted(quests)})
        self.stop_client()
        self.archive()
        self.row['reopen_result'] = 'pass: ' + ('same UUID, position and workbench outputs' if self.player else 'empty team world')

    def execute(self):
        require(not self.directory.exists(), f'Run exists: {self.directory}')
        try:
            self.prepare()
            self.launch()
            print(f'{self.directory.name}: ready, running warmup and sample', flush=True)
            self.measure()
            self.stop_server()
            self.stop_client()
            self.archive()
            self.profile()
            self.reopen()
            self.row.update(valid='true', invalid_reason='')
        except BaseException as error:
            self.row.update(valid='false', invalid_reason=repr(error))
            if self.directory.exists():
                self.event('failure', detail=repr(error))
            raise
        finally:
            if self.directory.exists():
                self.cleanup()
                self.record()
        print(f'{self.directory.name}: {self.row["mspt_median"]} / {self.row["mspt_p95"]} ms', flush=True)

    def record(self):
        evidence = self.directory / 'initial'
        files = {'launch_args_evidence': self.directory / 'environment.json', 'host_evidence': self.fx.output / 'host-server.json',
                 'clock_evidence': self.directory / 'events.jsonl', 'workload_evidence': self.directory / 'events.jsonl',
                 'ticks_evidence': evidence / 'ticks.csv', 'hotspots_evidence': evidence / 'hotspots.json',
                 'gc_evidence': evidence / 'gc.log', 'latest_log': evidence / 'latest.log', 'debug_log': evidence / 'debug.log',
                 'console_log': evidence / 'console.log', 'regression_evidence': self.directory / 'reopen/persistence.json'}
        self.row.update({key: Path(os.path.relpath(path, self.fx.root)).as_posix() for key, path in files.items() if path.is_file()})
        write(self.directory / 'result.json', self.row)
        write(self.directory / 'evidence.sha256.json',
              {path.relative_to(self.directory).as_posix(): sha(path) for path in sorted([
                  *evidence.glob('*'), *(self.directory / 'reopen').glob('*'), *self.directory.glob('*')])
               if path.is_file() and path.name != 'evidence.sha256.json'})

    def pilot(self):
        self.prepare()
        try:
            self.launch()
            if self.player:
                self.join()
                self.position_for_workload()
                self.workbench()
                self.feed()
                time.sleep(30)
                self.workbench(completed=True)
                self.position(STAND)
                print(f'{self.directory.name}: fixed standing tile, recipe output and consumption verified', flush=True)
                self.stop_server()
                return
            self.command('perfticksprobe')
            log = self.perf_log()
            wait_for(lambda: 'PERF_START ' in text(log), 10, 'No PERF_START marker')
            print(f'{self.directory.name}: server ready, collecting tick script pilot', flush=True)
            wait_for(lambda: 'PERF_WINDOW ' in text(log), 30, 'No PERF_WINDOW marker')
            window, _ = parse_ticks(text(log), warmup=2, sample=5)
            print(window, flush=True)
            self.stop_server()
        finally:
            self.cleanup()


def parse_ticks(log, warmup=120, sample=300):
    windows = re.findall(r'PERF_WINDOW (\{[^\r\n]+\})', log)
    batches = re.findall(r'PERF_TICKS (\[\[[^\r\n]+\]\])', log)
    require(len(windows) == 1 and batches, 'Missing or duplicate perf batches/window')
    window = json.loads(windows[0])
    rows = [row for batch in batches for row in json.loads(batch)]
    require(window['count'] == len(rows), 'Tick count differs from PERF_WINDOW')
    require(window['sampleEndNs'] - window['sampleStartNs'] == sample * NS and
            window['sampleStartNs'] - window['warmupStartNs'] == warmup * NS, 'Wrong window duration')
    require(rows[0][0] == window['firstTick'] and rows[-1][0] == window['lastTick'], 'Wrong tick boundaries')
    require(all(duration > 0 for _, _, duration in rows), 'Empty ring slot')
    require(all(new[0] == old[0] + 1 and new[1] > old[1] for old, new in zip(rows, rows[1:])),
            'Tick stream has gaps or unordered timestamps')
    require(0 <= rows[0][1] < NS and (sample - 1) * NS <= rows[-1][1] < sample * NS and
            sample * NS <= window['observedEndOffsetNs'] < (sample + 1) * NS, 'Sample does not cover full window')
    return window, rows


def update_csv(fixture):
    source = fixture.docs / 'runs.csv'
    with source.open(encoding='utf-8-sig', newline='') as stream:
        reader = csv.DictReader(stream)
        original = list(reader)
        fields = reader.fieldnames
    results = [read(path) for path in sorted((fixture.output / 'runs').glob('*/result.json'))]
    replace = {row['run_id'] for row in results}
    rows = [row for row in original if row['run_id'] not in replace] + results
    target = fixture.output / 'runs.csv'
    with target.open('w', encoding='utf-8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('action', choices=('build', 'pilot', 'run', 'series', 'csv'))
    parser.add_argument('--name', default='server-pilot')
    parser.add_argument('--player', action='store_true')
    parser.add_argument('--sequence', type=int, default=1)
    parser.add_argument('--attempt', type=int, default=1)
    options = parser.parse_args()
    fixture = Fixture(options.root, options.output)
    if options.action == 'build':
        fixture.build()
    elif options.action == 'pilot':
        require(re.fullmatch(r'server-[a-z0-9-]+', options.name), 'Invalid run name')
        Run(fixture, options.name, options.player).pilot()
    elif options.action == 'csv':
        update_csv(fixture)
    else:
        require(re.fullmatch(r'server-[a-z0-9-]+', options.name), 'Invalid run name')
        jobs = ([(options.player, options.sequence, options.attempt, options.name)] if options.action == 'run' else
                [(player, sequence, 1, f'server-{label}-b{sequence}')
                 for player, label in ((False, 'empty'), (True, 'stone')) for sequence in range(1, 4)])
        for player, sequence, attempt, name in jobs:
            run = Run(fixture, name, player)
            previous = run.directory / 'result.json'
            if previous.is_file() and read(previous)['valid'] == 'true' and options.action == 'series':
                continue
            label = 'stone' if player else 'empty'
            run.row.update(block_id=f'server-{label}-baseline-20261125', sequence=sequence, attempt=attempt)
            try:
                run.execute()
            finally:
                update_csv(fixture)
