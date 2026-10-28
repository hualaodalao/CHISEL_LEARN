from contextlib import redirect_stderr
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(MODULE / "tools"))
SPEC = importlib.util.spec_from_file_location("workerbee_p0_build", MODULE / "tools/build.py")
builder = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(builder)


class BuildTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="workerbee-build-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "build").mkdir()

    def test_existing_user_build_names_are_not_allowed(self):
        for name in ("RISCV", "X86", "../RISCV", "WORKERBEE_P0/other"):
            with self.subTest(name=name), self.assertRaises(ValueError):
                builder.build(self.root, "scons", name, 6)

    def test_jobs_must_be_positive(self):
        with self.assertRaises(ValueError):
            builder.build(self.root, "scons", "WORKERBEE_P0", 0)

    def test_wrong_upstream_commit_is_rejected_before_scons(self):
        with patch.object(builder, "command", return_value="wrong") as command:
            with self.assertRaisesRegex(ValueError, "commit"):
                builder.build(self.root, "scons", "WORKERBEE_P0", 6)
        self.assertEqual(command.call_count, 1)

    def test_other_external_module_build_is_not_overwritten(self):
        metadata = self.root / "build/WORKERBEE_P0/gem5.build"
        metadata.mkdir(parents=True)
        extras = metadata / "extras"
        extras.write_text("EXTRAS = '/other/module'\n")
        with patch.object(builder, "command", return_value=builder.GEM5_COMMIT):
            with self.assertRaisesRegex(ValueError, "different external module"):
                builder.build(self.root, "scons", "WORKERBEE_P0", 6)
        self.assertEqual(extras.read_text(), "EXTRAS = '/other/module'\n")

    def test_failed_compiler_log_is_preserved(self):
        metadata = self.root / "build/WORKERBEE_P0/gem5.build"
        metadata.mkdir(parents=True)
        (metadata / "config").write_text("fixture config")
        log_path = metadata.parent / "p0-build.log"
        log_path.write_text("previous attempt\n")

        def failed_compile(argv, **kwargs):
            self.assertEqual(argv[argv.index("-j") + 1], "2")
            self.assertIs(kwargs["stderr"], builder.subprocess.STDOUT)
            kwargs["stdout"].write("compiler failed\n")
            return builder.subprocess.CompletedProcess(argv, -9)

        with patch.object(builder, "command", return_value=builder.GEM5_COMMIT), \
             patch.object(builder, "snapshot", return_value={"model_sources": []}), \
             patch.object(builder.subprocess, "run", side_effect=failed_compile):
            with self.assertRaisesRegex(RuntimeError, "Command failed"):
                builder.build(self.root, "scons", "WORKERBEE_P0", 2)
        self.assertIn("previous attempt\n", log_path.read_text())
        self.assertIn("compiler failed\n", log_path.read_text())
        self.assertFalse((metadata.parent / "p0-build.json").exists())

    def test_cli_argument_errors_are_structured(self):
        stderr = io.StringIO()
        with redirect_stderr(stderr):
            status = builder.main([])
        self.assertEqual(status, 1)
        error = json.loads(stderr.getvalue())["error"]
        self.assertEqual(set(error), {"kind", "code", "message", "cycle", "task_id", "port"})


if __name__ == "__main__":
    unittest.main()
