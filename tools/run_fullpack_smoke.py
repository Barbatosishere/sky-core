"""Run the existing instance's native server integration tests.

The instance owns the recipe/quest fixture and verified client-only classpath
classification. Delegate to that maintained runner rather than reimplementing it.
--raw-startup retains the original unfiltered diagnostic launch (no assertions).
Shared inventory/write_json helpers are also imported by the instance runner.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys


PROJECT = Path(__file__).resolve().parents[1]
DEFAULT_PACK = Path("D:/MC/PrismLauncher_minecraft/1.21.1(1)/minecraft")


def inventory(pack: Path) -> dict:
    entries = {}
    for folder in ("mods", "kubejs", "config"):
        for path in sorted((pack / folder).rglob("*")):
            if path.is_file():
                with path.open("rb") as source:
                    digest = hashlib.file_digest(source, "sha256").hexdigest()
                entries[path.relative_to(pack).as_posix()] = {
                    "size": path.stat().st_size,
                    "sha256": digest,
                }
    return entries


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    # Windows' default GBK cannot print every installed mod's display name.
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pack", type=Path, default=DEFAULT_PACK)
    parser.add_argument("--output", type=Path,
                        help="Output root for --raw-startup or --prepare-only")
    parser.add_argument("--raw-startup", action="store_true",
                        help="Diagnose all installed JARs without server-side filtering or behavior assertions")
    parser.add_argument("--prepare-only", action="store_true")
    args = parser.parse_args()
    pack = args.pack.resolve(strict=True)
    if not args.raw_startup and not args.prepare_only:
        entrypoint = pack.parent / "tools/run_server_integration.py"
        if not entrypoint.is_file():
            parser.error(f"Instance integration runner is missing: {entrypoint}; "
                         "use --raw-startup only for an unfiltered startup diagnosis")
        if args.output is not None:
            parser.error("--output applies to --raw-startup/--prepare-only; the integration runner records its evidence directory")
        print(f"Native integration entrypoint: {entrypoint}", flush=True)
        # Do not import its main(): it imports our helpers and owns QA setup/results.
        return subprocess.run([sys.executable, str(entrypoint)], cwd=pack.parent).returncode
    output = (args.output or Path("D:/MC/skycore-build/fullpack-smoke")).resolve()
    if output == pack or output.is_relative_to(pack):
        raise ValueError("Smoke output must be outside the original game directory")
    output.mkdir(parents=True, exist_ok=True)
    run = output / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run.mkdir(exist_ok=False)
    game = run / "game"
    game.mkdir()
    before = inventory(pack)
    write_json(run / "original-before.json", before)
    for folder in ("config", "kubejs", "defaultconfigs", "coremods"):
        if (pack / folder).is_dir():
            shutil.copytree(pack / folder, game / folder)
    for relative, expected in before.items():
        if not relative.startswith(("config/", "kubejs/")):
            continue
        path = game / relative
        with path.open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        if actual != expected["sha256"]:
            raise RuntimeError(f"Snapshot verification failed: {relative}")
    # Test-world-only settings. No server port is exposed; the requested port is 0.
    (game / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
        "level-name=smoke-world\nmax-players=4\nview-distance=2\n"
        "simulation-distance=2\nspawn-protection=0\n", encoding="ascii")
    command = [str(PROJECT / "gradlew.bat"), "--console=plain", "-Pfullpack_smoke=true",
               f"-Ppack_minecraft_dir={pack.as_posix()}",
               f"-Pfullpack_smoke_dir={game.as_posix()}", "runFullPackSmokeServer"]
    write_json(run / "launch.json", {
        "original_game_directory": str(pack),
        "test_working_directory": str(game),
        "installed_jar_count": len(list((pack / "mods").glob("*.jar"))),
        "jar_source": "original paths on runtime classpath, no filters",
        "command": command,
        "project": str(PROJECT),
        "kind": "native full-pack startup gate; no behavior assertions yet",
    })
    print(f"Evidence directory: {run}", flush=True)
    print(f"Installed JARs: {len(list((pack / 'mods').glob('*.jar')))}", flush=True)
    if args.prepare_only:
        return 0
    exit_code = -1
    try:
        with (run / "console.log").open("w", encoding="utf-8") as log:
            process = subprocess.Popen(command, cwd=PROJECT, stdout=subprocess.PIPE,
                                       stderr=subprocess.STDOUT, encoding="utf-8", errors="replace")
            write_json(run / "process.json", {"launcher_pid": process.pid})
            assert process.stdout is not None
            for line in process.stdout:
                log.write(line)
                log.flush()
                print(line, end="", flush=True)
            exit_code = process.wait()
    finally:
        after = inventory(pack)
        write_json(run / "original-after.json", after)
        changed = sorted(path for path in before.keys() | after.keys()
                         if before.get(path) != after.get(path))
        write_json(run / "result.json", {
            "launcher_exit_code": exit_code,
            "status": "native_startup_failed" if exit_code != 0 else "startup_only_not_behavior_verified",
            "original_changed_files": changed,
            "behavior_assertions": "not executed by startup gate",
        })
        print(f"Original mods/config/kubejs changed files: {len(changed)}", flush=True)
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
