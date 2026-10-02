import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


SCRIPTS = Path(__file__).parents[1]
sys.path.insert(0, str(SCRIPTS))
SCRIPT = SCRIPTS / "install_android_sdk.py"
SPEC = importlib.util.spec_from_file_location("install_android_sdk", SCRIPT)
assert SPEC and SPEC.loader
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)


class AndroidSdkInstallerTest(unittest.TestCase):
    def test_sdk_packages_pin_repository_revisions(self):
        self.assertEqual(
            ("build-tools/36.0.0@36.0.0", "platforms/android-37.0@2.0.0"),
            installer.SDK_PACKAGES,
        )

    def test_supported_hosts_use_versioned_google_archives(self):
        hosts = (
            ("Darwin", "arm64"),
            ("Darwin", "x86_64"),
            ("Linux", "AMD64"),
            ("Windows", "AMD64"),
        )
        for system, machine in hosts:
            filename, checksum = installer.command_line_spec(system, machine)
            self.assertIn("16111833", filename)
            self.assertEqual(40, len(checksum))

    def test_unsupported_host_fails_clearly(self):
        with self.assertRaisesRegex(RuntimeError, "Unsupported Android SDK host"):
            installer.command_line_spec("Linux", "arm64")

    def test_local_properties_preserves_other_values(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            android = root / "android"
            android.mkdir()
            properties = android / "local.properties"
            properties.write_text("other=value\nsdk.dir=/old\n")

            installer.write_local_properties(root, Path("C:/Android/Sdk"))

            self.assertEqual("other=value\nsdk.dir=C\\:/Android/Sdk\n", properties.read_text())


if __name__ == "__main__":
    unittest.main()
