"""Source and optional RTL fingerprints for the WorkerBee P0 manifest."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys


_GEM5 = Path("src/workerbee/gem5")
_GENERATED_DIRS = {"__pycache__", "generated", "build", "target", "m5out", ".git"}


def _sources(root: Path, paths) -> list[dict]:
    return [
        {
            "path": path.relative_to(root).as_posix(),
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        }
        for path in sorted(set(paths), key=lambda path: path.relative_to(root).as_posix())
    ]


def _source_hash(sources: list[dict]) -> str:
    # Only relative paths and content hashes enter the aggregate, not metadata.
    encoded = json.dumps(sources, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def snapshot(repo_root: Path, rtl_dir: Path | None = None) -> dict:
    root = Path(repo_root).expanduser().resolve()
    if not root.is_dir():
        raise ValueError(f"Repository root is not a directory: {root}")

    hardware_paths = list((root / "src/workerbee/scala/main").rglob("*.scala"))
    hardware_paths.extend((root / "src/utils").rglob("*.scala"))
    hardware_paths.extend((root / "project").glob("*.sbt"))
    hardware_paths.extend(
        root / path
        for path in ("build.sbt", "project/build.properties", "src/workerbee/doc/spec.md")
    )
    hardware_sources = _sources(root, hardware_paths)

    gem5 = root / _GEM5
    model_paths = []
    for pattern in ("*.py", "*.hh", "*.cc", "SConscript"):
        model_paths.extend((gem5 / "model").rglob(pattern))
    for directory, patterns in (
        ("configs", ("*.py",)),
        ("tools", ("*.py", "*.scala")),
        ("tests", ("*.py", "*.scala")),
    ):
        for pattern in patterns:
            model_paths.extend((gem5 / directory).glob(pattern))
    model_paths.extend(gem5.glob("plan.md"))
    model_paths.extend(gem5.glob("spec.md"))
    model_sources = _sources(
        root,
        (
            path
            for path in model_paths
            if path.is_file()
            and not _GENERATED_DIRS.intersection(path.relative_to(gem5).parts)
        ),
    )

    rtl_sources = []
    if rtl_dir is not None:
        rtl_root = Path(rtl_dir).expanduser().resolve()
        if not rtl_root.is_dir():
            raise ValueError(f"RTL directory does not exist or is not a directory: {rtl_root}")
        rtl_sources = _sources(
            rtl_root,
            (path for path in rtl_root.rglob("*") if path.is_file() and path.suffix in {".sv", ".v"}),
        )
        if not rtl_sources:
            raise ValueError(f"RTL directory contains no .sv or .v artifacts: {rtl_root}")

    try:
        head = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "--verify", "HEAD"],
            capture_output=True,
            text=True,
            check=False,
            timeout=5,
            shell=False,
        )
        git_head = head.stdout.strip() if head.returncode == 0 else None
    except FileNotFoundError:
        git_head = None

    return {
        "schema_version": 1,
        "model_kind": "timing_only",
        "data_valid": False,
        "git_head": git_head,
        "hardware_sources": hardware_sources,
        "hardware_source_hash": _source_hash(hardware_sources),
        "model_sources": model_sources,
        "model_source_hash": _source_hash(model_sources),
        "rtl_sources": rtl_sources,
        "rtl_present": bool(rtl_sources),
        "rtl_hash": _source_hash(rtl_sources) if rtl_sources else None,
        "rtl_verified": False,
    }


class _ArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        raise ValueError(message)


def main(argv: list[str] | None = None) -> int:
    parser = _ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--rtl-dir", type=Path)
    parser.add_argument("--output", type=Path)
    try:
        args = parser.parse_args(argv)
        output = args.output.expanduser() if args.output is not None else None
        if output is not None and not output.parent.is_dir():
            raise FileNotFoundError(f"Output parent directory does not exist: {output.parent}")
        result = snapshot(args.repo_root, args.rtl_dir)
        encoded = json.dumps(result, indent=2, sort_keys=True) + "\n"
        if output is None:
            sys.stdout.write(encoded)
        else:
            output.write_text(encoded, encoding="utf-8")
        return 0
    except Exception as exc:
        if isinstance(exc, FileNotFoundError):
            kind, code = "input", "missing_path"
        elif isinstance(exc, ValueError):
            kind, code = "input", "invalid_input"
        elif isinstance(exc, (OSError, subprocess.SubprocessError)):
            kind, code = "environment", "io_error"
        else:
            kind, code = "model", "internal_error"
        error = {
            "kind": kind,
            "code": code,
            "message": str(exc),
            "cycle": None,
            "task_id": None,
            "port": None,
        }
        sys.stderr.write(json.dumps({"error": error}, sort_keys=True) + "\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
