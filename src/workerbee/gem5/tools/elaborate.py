import argparse
import json
from pathlib import Path
import subprocess
import sys

MODULE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODULE_ROOT / "configs"))
from hardware import DEFAULT_PROFILE, verify_elaboration
from baseline import snapshot


def elaborate(output_dir, sbt):
    repo = MODULE_ROOT.parents[2]
    output = Path(output_dir).expanduser().resolve()
    if output.exists() or not output.parent.is_dir():
        raise ValueError("RTL output must be a new directory with an existing parent")
    before = snapshot(repo)
    source_setting = (
        'set Compile / unmanagedSources := (Compile / unmanagedSources).value.filter('
        'f => f.getPath.contains("/src/workerbee/scala/main/") || '
        'f.getPath.contains("/src/utils/")) :+ baseDirectory.value / "src" / '
        '"workerbee" / "gem5" / "tools" / "EmitP0.scala"'
    )
    command = [str(sbt), "-J-Xmx6G", source_setting,
               "runMain WorkerBeeGem5P0Elaborate " + json.dumps(str(output))]
    completed = subprocess.run(command, cwd=repo, text=True, capture_output=True, check=False)
    sys.stdout.write(completed.stdout)
    sys.stderr.write(completed.stderr)
    if completed.returncode:
        raise RuntimeError(f"SBT elaboration failed with exit code {completed.returncode}")
    profile = json.loads((output / "hardware.json").read_text())
    verify_elaboration(profile)
    after = snapshot(repo, output)
    for key in ("hardware_source_hash", "model_source_hash"):
        if before[key] != after[key]:
            raise RuntimeError("Sources changed during elaboration; discard this baseline")
    after["hardware_profile"] = DEFAULT_PROFILE
    after["elaboration"] = {"command": command, "profile_matches": True,
                            "edge_calibrated": False}
    (output / "baseline.json").write_text(json.dumps(after, indent=2, sort_keys=True) + "\n")
    (output / "elaboration.log").write_text(completed.stdout + completed.stderr)
    return after


class ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv=None):
    parser = ArgumentParser(description="Emit the default P0 RTL into a fresh directory")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--sbt", default="sbt")
    try:
        args = parser.parse_args(argv)
        elaborate(args.output_dir, args.sbt)
        return 0
    except (ValueError, OSError, RuntimeError) as exc:
        sys.stderr.write(json.dumps({"error": {
            "kind": "environment", "code": "elaboration_failed", "message": str(exc),
            "cycle": None, "task_id": None, "port": None,
        }}) + "\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
