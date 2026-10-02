import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO_ROOT))

from ml.tools.download_models import (  # noqa: E402
    ChecksumMismatch,
    ModelArtifact,
    ModelManifest,
    download_artifact,
    load_manifest,
    select_artifacts,
)


class DownloadModelsTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.payload = b"deterministic-model-bytes"
        self.source = self.root / "source.bin"
        self.source.write_bytes(self.payload)
        self.sha256 = hashlib.sha256(self.payload).hexdigest()

    def tearDown(self):
        self.temp_dir.cleanup()

    def artifact(self, **overrides):
        values = {
            "id": "approved_model",
            "filename": "approved.bin",
            "url": self.source.as_uri(),
            "sha256": self.sha256,
            "status": "approved_for_evaluation",
        }
        values.update(overrides)
        return ModelArtifact(**values)

    def test_manifest_rejects_missing_required_fields(self):
        manifest_path = self.root / "models.json"
        manifest_path.write_text(
            json.dumps({"schema_version": 1, "artifacts": [{"id": "broken"}]}),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(ValueError, "missing"):
            load_manifest(manifest_path)

    def test_only_approved_models_are_selected_by_default(self):
        manifest = ModelManifest(
            schema_version=1,
            artifacts=(
                self.artifact(),
                self.artifact(id="unclear", status="unverified"),
            ),
        )

        selected = select_artifacts(manifest, include_unverified=False)

        self.assertEqual(["approved_model"], [item.id for item in selected])

    def test_unverified_models_require_explicit_opt_in(self):
        manifest = ModelManifest(
            schema_version=1,
            artifacts=(
                self.artifact(),
                self.artifact(id="unclear", status="unverified"),
            ),
        )

        selected = select_artifacts(manifest, include_unverified=True)

        self.assertEqual(["approved_model", "unclear"], [item.id for item in selected])

    def test_download_verifies_checksum(self):
        output_dir = self.root / "models"

        target = download_artifact(self.artifact(), output_dir)

        self.assertEqual(self.payload, target.read_bytes())

    def test_checksum_failure_removes_partial_file(self):
        output_dir = self.root / "models"
        artifact = self.artifact(sha256="0" * 64)

        with self.assertRaises(ChecksumMismatch):
            download_artifact(artifact, output_dir)

        self.assertFalse((output_dir / artifact.filename).exists())
        self.assertFalse((output_dir / f"{artifact.filename}.part").exists())

    def test_valid_existing_file_is_reused(self):
        output_dir = self.root / "models"
        artifact = self.artifact()
        target = download_artifact(artifact, output_dir)
        self.source.unlink()

        reused = download_artifact(artifact, output_dir)

        self.assertEqual(target, reused)
        self.assertEqual(self.payload, reused.read_bytes())


if __name__ == "__main__":
    unittest.main()
