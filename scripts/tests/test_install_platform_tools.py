import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).parents[1] / "install_platform_tools.py"
SPEC = importlib.util.spec_from_file_location("install_platform_tools", SCRIPT)
assert SPEC and SPEC.loader
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)


class PlatformToolsInstallerTest(unittest.TestCase):
    def test_every_supported_platform_has_a_versioned_archive_and_sha256(self):
        for system in ("Darwin", "Linux", "Windows"):
            filename, checksum = installer.archive_spec(system)
            self.assertIn(installer.VERSION, filename)
            self.assertEqual(64, len(checksum))

    def test_extract_archive_rejects_path_traversal(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "bad.zip"
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("../outside", "bad")
            with self.assertRaisesRegex(RuntimeError, "Unsafe archive member"):
                installer.extract_archive(archive, root / "extract")

    def test_install_from_verified_archive_writes_launchers(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / installer.archive_spec("Linux")[0]
            with zipfile.ZipFile(archive, "w") as bundle:
                bundle.writestr("platform-tools/adb", "adb")
                bundle.writestr("platform-tools/fastboot", "fastboot")

            expected = installer.ARCHIVES["Linux"]
            installer.ARCHIVES["Linux"] = (expected[0], installer.sha256(archive))
            try:
                install_dir = installer.install(root / "env", "Linux", archive)
            finally:
                installer.ARCHIVES["Linux"] = expected

            self.assertTrue((install_dir / "adb").is_file())
            launcher = root / "env" / "bin" / "adb"
            self.assertIn(installer.MANAGED_MARKER, launcher.read_text())

    def test_windows_launcher_calls_executable_beside_its_dlls(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            install_dir = root / "platform-tools"
            install_dir.mkdir()
            launcher = root / "env" / "Scripts" / "adb.cmd"

            installer.write_launcher(launcher, "adb", install_dir, "Windows")

            content = launcher.read_text()
            self.assertIn(installer.MANAGED_MARKER, content)
            self.assertIn(str(install_dir / "adb.exe"), content)


if __name__ == "__main__":
    unittest.main()
