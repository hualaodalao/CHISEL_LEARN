"""Standard-library tests for WorkerBee baseline source/RTL manifests."""

from contextlib import redirect_stderr, redirect_stdout
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


BASELINE_PATH = Path(__file__).resolve().parents[1] / "tools/baseline.py"
SPEC = importlib.util.spec_from_file_location("workerbee_baseline", BASELINE_PATH)
baseline = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(baseline)

HARDWARE_FILES = {
    "src/workerbee/scala/main/z/WorkerBee.scala": b"class WorkerBee\n",
    "src/workerbee/scala/main/A.scala": b"class A\n",
    "src/utils/Stream/Stream.scala": b"class Stream\n",
    "src/utils/MaskedLiteral.scala": b"class MaskedLiteral\n",
    "build.sbt": b"scalaVersion := \"2.13.12\"\n",
    "project/plugins.sbt": b"// plugins\n",
    "project/build.properties": b"sbt.version=1.9.7\n",
    "src/workerbee/doc/spec.md": b"WorkerBee hardware specification\n",
}
MODEL_FILES = {
    "src/workerbee/gem5/model/WorkerBee.py": b"class WorkerBee: pass\n",
    "src/workerbee/gem5/model/workerbee.hh": b"// header\n",
    "src/workerbee/gem5/model/workerbee.cc": b"// implementation\n",
    "src/workerbee/gem5/model/SConscript": b"# external module\n",
    "src/workerbee/gem5/model/test_support/TestDevices.py": b"# test devices\n",
    "src/workerbee/gem5/model/test_support/SConscript": b"# test build\n",
    "src/workerbee/gem5/configs/hardware.py": b"PROFILE_NAME = 'workerbee_default_v4'\n",
    "src/workerbee/gem5/configs/run.py": b"# smoke runner\n",
    "src/workerbee/gem5/tools/baseline.py": b"# baseline tool\n",
    "src/workerbee/gem5/tools/Emit.scala": b"// safe emitter\n",
    "src/workerbee/gem5/tests/test_baseline.py": b"# baseline tests\n",
    "src/workerbee/gem5/tests/EdgeSpec.scala": b"class EdgeSpec\n",
    "src/workerbee/gem5/plan.md": b"P0 plan\n",
    "src/workerbee/gem5/spec.md": b"Timing-only model specification\n",
}
IGNORED_FILES = {
    "src/foragerbee/scala/main/ForagerBee.scala": b"unrelated worker\n",
    "src/llc/scala/main/Llc.scala": b"unrelated LLC\n",
    "src/workerbee/scala/test/Test.scala": b"RTL test\n",
    "src/workerbee/verilog/old/WorkerBee.v": b"historical RTL\n",
    "generated/WorkerBee.sv": b"old generated RTL\n",
    "project/target/cache.sbt": b"generated build cache\n",
    "project/project/plugins.sbt": b"nested build\n",
    "src/workerbee/gem5/model/__pycache__/cached.py": b"cached source\n",
    "src/workerbee/gem5/model/__pycache__/WorkerBee.pyc": b"cached bytecode\n",
    "src/workerbee/gem5/model/generated/autogen.cc": b"generated C++\n",
    "src/workerbee/gem5/model/build/temp.hh": b"build output\n",
    "src/workerbee/gem5/model/target/temp.py": b"target output\n",
    "src/workerbee/gem5/configs/__pycache__/run.py": b"cached config\n",
    "src/workerbee/gem5/tools/generated/emit.scala": b"generated emitter\n",
    "src/workerbee/gem5/tests/result.json": b"{}\n",
    "src/workerbee/gem5/model/WorkerBee.o": b"object\n",
    "src/workerbee/gem5/manifest.json": b"{}\n",
}


def inventory(files):
    return [
        {"path": path, "sha256": hashlib.sha256(content).hexdigest()}
        for path, content in sorted(files.items())
    ]


def aggregate(sources):
    encoded = json.dumps(sources, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


class BaselineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="workerbee-baseline-")
        self.addCleanup(self.temp.cleanup)
        self.workspace = Path(self.temp.name).resolve()
        self.root = self.workspace / "repo"
        self.root.mkdir()
        # Reverse insertion order exercises sorting independently of discovery.
        for path, content in reversed(list({**HARDWARE_FILES, **MODEL_FILES, **IGNORED_FILES}.items())):
            self.write(path, content)

    def write(self, relative, content):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def cli(self, *args):
        return subprocess.run(
            [sys.executable, "-B", str(BASELINE_PATH), *map(str, args)],
            capture_output=True,
            text=True,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
            check=False,
            timeout=10,
            shell=False,
        )

    def assert_cli_error(self, result, kind="input", code=None):
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")
        payload = json.loads(result.stderr)
        self.assertEqual(set(payload), {"error"})
        error = payload["error"]
        self.assertEqual(set(error), {"kind", "code", "message", "cycle", "task_id", "port"})
        self.assertEqual(error["kind"], kind)
        self.assertTrue(error["code"])
        self.assertTrue(error["message"])
        if code is not None:
            self.assertEqual(error["code"], code)
        for key in ("cycle", "task_id", "port"):
            self.assertIsNone(error[key])

    def test_stable_sorted_hashes_and_non_git_root(self):
        first = baseline.snapshot(self.root)
        self.assertEqual(first, baseline.snapshot(self.root))
        self.assertIsNone(first["git_head"])
        for prefix, files in (("hardware", HARDWARE_FILES), ("model", MODEL_FILES)):
            expected = inventory(files)
            self.assertEqual(first[f"{prefix}_sources"], expected)
            self.assertEqual(first[f"{prefix}_source_hash"], aggregate(expected))
        self.assertEqual(first["schema_version"], 1)
        self.assertEqual(first["model_kind"], "timing_only")
        self.assertFalse(first["data_valid"])
        self.assertFalse(first["rtl_present"])
        self.assertFalse(first["rtl_verified"])
        self.assertIsNone(first["rtl_hash"])
        self.assertEqual(first["rtl_sources"], [])

    def test_absolute_root_and_mtime_do_not_enter_hashes(self):
        first = baseline.snapshot(self.root)
        copy = self.workspace / "copy"
        shutil.copytree(self.root, copy)
        for relative in HARDWARE_FILES:
            os.utime(copy / relative, (100, 100))
        second = baseline.snapshot(copy)
        self.assertEqual(first, second)

    def test_each_hardware_source_changes_only_hardware_hash(self):
        original = baseline.snapshot(self.root)
        for relative, content in HARDWARE_FILES.items():
            with self.subTest(path=relative):
                self.write(relative, content + b"// changed\n")
                changed = baseline.snapshot(self.root)
                self.assertNotEqual(original["hardware_source_hash"], changed["hardware_source_hash"])
                self.assertEqual(original["model_source_hash"], changed["model_source_hash"])
                self.write(relative, content)

    def test_each_model_source_changes_only_model_hash(self):
        original = baseline.snapshot(self.root)
        for relative, content in MODEL_FILES.items():
            with self.subTest(path=relative):
                self.write(relative, content + b"# changed\n")
                changed = baseline.snapshot(self.root)
                self.assertEqual(original["hardware_source_hash"], changed["hardware_source_hash"])
                self.assertNotEqual(original["model_source_hash"], changed["model_source_hash"])
                self.write(relative, content)

    def test_relative_path_and_file_addition_affect_hash(self):
        original = baseline.snapshot(self.root)
        old = self.root / "src/workerbee/scala/main/A.scala"
        renamed = old.with_name("Renamed.scala")
        old.rename(renamed)
        self.assertNotEqual(original["hardware_source_hash"], baseline.snapshot(self.root)["hardware_source_hash"])
        renamed.rename(old)
        self.write("src/workerbee/gem5/model/new.cc", b"// new model source\n")
        added = baseline.snapshot(self.root)
        self.assertEqual(original["hardware_source_hash"], added["hardware_source_hash"])
        self.assertNotEqual(original["model_source_hash"], added["model_source_hash"])

    def test_old_rtl_unrelated_modules_and_generated_files_are_excluded(self):
        original = baseline.snapshot(self.root)
        for relative in IGNORED_FILES:
            self.write(relative, b"completely changed\n")
        self.assertEqual(original, baseline.snapshot(self.root))

    def test_rtl_missing_empty_non_directory_and_non_rtl_are_rejected(self):
        empty = self.workspace / "empty-rtl"
        empty.mkdir()
        for path in (self.workspace / "missing", empty, self.root / "build.sbt"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                baseline.snapshot(self.root, path)
        (empty / "metadata.json").write_text("{}", encoding="utf-8")
        with self.assertRaises(ValueError):
            baseline.snapshot(self.root, empty)

    def test_real_rtl_artifact_hashes_do_not_claim_verification(self):
        rtl = self.workspace / "rtl"
        (rtl / "nested").mkdir(parents=True)
        files = {"z.sv": b"module top; endmodule\n", "nested/a.v": b"\x00\xff RTL\n"}
        for relative, content in files.items():
            (rtl / relative).write_bytes(content)
        (rtl / "metadata.json").write_text("{}", encoding="utf-8")
        source_only = baseline.snapshot(self.root)
        result = baseline.snapshot(self.root, rtl)
        self.assertEqual(result["rtl_sources"], inventory(files))
        self.assertEqual(result["rtl_hash"], aggregate(inventory(files)))
        self.assertTrue(result["rtl_present"])
        self.assertFalse(result["rtl_verified"])
        for key in ("hardware_source_hash", "model_source_hash"):
            self.assertEqual(source_only[key], result[key])
        (rtl / "z.sv").write_bytes(b"module changed; endmodule\n")
        changed = baseline.snapshot(self.root, rtl)
        self.assertNotEqual(result["rtl_hash"], changed["rtl_hash"])
        self.assertFalse(changed["rtl_verified"])

    def test_git_head_uses_subprocess_without_shell_and_not_content_hashes(self):
        with patch.object(baseline.subprocess, "run") as run:
            run.return_value = subprocess.CompletedProcess([], 0, "a" * 40 + "\n", "")
            first = baseline.snapshot(self.root)
            self.assertEqual(first["git_head"], "a" * 40)
            self.assertEqual(run.call_args.args[0], ["git", "-C", str(self.root), "rev-parse", "--verify", "HEAD"])
            self.assertIs(run.call_args.kwargs["shell"], False)
            run.return_value = subprocess.CompletedProcess([], 0, "b" * 40 + "\n", "")
            second = baseline.snapshot(self.root)
            self.assertEqual(second["git_head"], "b" * 40)
            for key in ("hardware_source_hash", "model_source_hash"):
                self.assertEqual(first[key], second[key])
        with patch.object(baseline.subprocess, "run", side_effect=FileNotFoundError("git unavailable")):
            self.assertIsNone(baseline.snapshot(self.root)["git_head"])

    def test_invalid_root_and_missing_required_source_are_rejected(self):
        with self.assertRaises(ValueError):
            baseline.snapshot(self.workspace / "missing")
        (self.root / "project/build.properties").unlink()
        with self.assertRaises(FileNotFoundError):
            baseline.snapshot(self.root)

    def test_cli_stdout_and_existing_parent_output(self):
        stdout = self.cli("--repo-root", self.root)
        self.assertEqual(stdout.returncode, 0, stdout.stderr)
        self.assertEqual(stdout.stderr, "")
        self.assertEqual(json.loads(stdout.stdout), baseline.snapshot(self.root))
        output = self.workspace / "manifest.json"
        result = self.cli("--repo-root", self.root, "--output", output)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(result.stderr, "")
        self.assertEqual(json.loads(output.read_text(encoding="utf-8")), baseline.snapshot(self.root))
        rtl = self.workspace / "rtl"
        rtl.mkdir()
        (rtl / "WorkerBee.sv").write_bytes(b"module WorkerBee; endmodule\n")
        result = self.cli("--repo-root", self.root, "--rtl-dir", rtl, "--output", output)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(output.read_text(encoding="utf-8")), baseline.snapshot(self.root, rtl))

    def test_cli_output_parent_missing_is_not_created(self):
        output = self.workspace / "missing-parent/manifest.json"
        result = self.cli("--repo-root", self.root, "--output", output)
        self.assert_cli_error(result, code="missing_path")
        self.assertFalse(output.parent.exists())

    def test_cli_invalid_rtl_preserves_existing_output(self):
        output = self.workspace / "manifest.json"
        output.write_text("unchanged", encoding="utf-8")
        result = self.cli("--repo-root", self.root, "--rtl-dir", self.workspace / "missing", "--output", output)
        self.assert_cli_error(result, code="invalid_input")
        self.assertEqual(output.read_text(encoding="utf-8"), "unchanged")

    def test_cli_argument_and_missing_source_errors_are_json(self):
        for args in ((), ("--repo-root",), ("--repo-root", self.root, "--unknown")):
            with self.subTest(args=args):
                self.assert_cli_error(self.cli(*args), code="invalid_input")
        (self.root / "build.sbt").unlink()
        self.assert_cli_error(self.cli("--repo-root", self.root), code="missing_path")

    def test_cli_environment_and_model_exceptions_are_json(self):
        for exc, kind in ((PermissionError("denied"), "environment"), (RuntimeError("broken"), "model")):
            with self.subTest(kind=kind), patch.object(baseline, "snapshot", side_effect=exc):
                stdout, stderr = io.StringIO(), io.StringIO()
                with redirect_stdout(stdout), redirect_stderr(stderr):
                    status = baseline.main(["--repo-root", str(self.root)])
                self.assert_cli_error(subprocess.CompletedProcess([], status, stdout.getvalue(), stderr.getvalue()), kind=kind)


if __name__ == "__main__":
    unittest.main()
