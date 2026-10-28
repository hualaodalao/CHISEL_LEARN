import argparse
import ast
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

MODULE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODULE_ROOT / "configs"))
from hardware import GEM5_COMMIT
from baseline import snapshot


def command(argv, cwd):
    result = subprocess.run(argv, cwd=cwd, text=True, capture_output=True, check=False)
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    if result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {argv[0]}")
    return result.stdout


def build(gem5_root, scons, name, jobs):
    root = Path(gem5_root).expanduser().resolve()
    if not re.fullmatch(r"WORKERBEE_P0(?:_[A-Za-z0-9_]+)?", name) or jobs <= 0:
        raise ValueError("Use a WORKERBEE_P0 build name and positive job count")
    if not (root / "build").is_dir():
        raise ValueError("gem5 build parent directory does not exist")
    commit = command(["git", "rev-parse", "HEAD"], root).strip()
    if commit != GEM5_COMMIT:
        raise ValueError("Unsupported gem5 commit")
    output = root / "build" / name
    extras = str(MODULE_ROOT / "model")
    extras_file = output / "gem5.build/extras"
    if extras_file.exists():
        tree = ast.parse(extras_file.read_text())
        assignments = {node.targets[0].id: ast.literal_eval(node.value)
                       for node in tree.body if isinstance(node, ast.Assign)}
        if assignments.get("EXTRAS") != extras:
            raise ValueError("Existing build belongs to a different external module")
    before = snapshot(MODULE_ROOT.parents[2])
    sources = [entry for entry in before["model_sources"]
               if entry["path"].startswith("src/workerbee/gem5/model/")]
    commands = []
    if not (output / "gem5.build/config").exists():
        commands.append([str(scons), "defconfig", f"build/{name}", "build_opts/NULL", f"EXTRAS={extras}"])
    commands.append([str(scons), f"build/{name}/gem5.opt", "-j", str(jobs), f"EXTRAS={extras}"])
    output.mkdir(exist_ok=True)
    with (output / "p0-build.log").open("a", buffering=1) as log:
        for argv in commands:
            log.write(json.dumps(argv) + "\n")
            log.flush()
            completed = subprocess.run(argv, cwd=root, stdout=log,
                                       stderr=subprocess.STDOUT, check=False)
            if completed.returncode:
                raise RuntimeError(f"Command failed ({completed.returncode}); see {log.name}")
    after = snapshot(MODULE_ROOT.parents[2])
    compiled = [entry for entry in after["model_sources"]
                if entry["path"].startswith("src/workerbee/gem5/model/")]
    if compiled != sources:
        raise RuntimeError("Model sources changed during compilation; rerun the build")
    executable = output / "gem5.opt"
    record = {
        "gem5_commit": commit, "compiled_model_sources": compiled,
        "executable_sha256": hashlib.sha256(executable.read_bytes()).hexdigest(),
        "commands": commands, "host_linux_validated": False,
        "compiler_version": command(["clang++", "--version"], root).strip(),
        "scons_version": command([str(scons), "--version"], root).strip(),
    }
    (output / "p0-build.json").write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
    return record


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv=None):
    parser = ArgumentParser(description="Build an isolated, source-fingerprinted P0 gem5 binary")
    parser.add_argument("--gem5-root", type=Path, required=True)
    parser.add_argument("--scons", default="scons")
    parser.add_argument("--build-name", default="WORKERBEE_P0")
    parser.add_argument("--jobs", type=int, default=6)
    try:
        args = parser.parse_args(argv)
        build(args.gem5_root, args.scons, args.build_name, args.jobs)
        return 0
    except (ValueError, OSError, RuntimeError) as exc:
        sys.stderr.write(json.dumps({"error": {
            "kind": "environment", "code": "build_failed", "message": str(exc),
            "cycle": None, "task_id": None, "port": None,
        }}) + "\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
