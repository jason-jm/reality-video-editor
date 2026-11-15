"""Regression checks for copying the output identified by a render job."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from export_fixture import copy_output


class RenderOutputCopyTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.output = Path(temp.name) / "editor's previous file.mp4"
        self.output.write_bytes(b"previous")
        self.uri = "content://media/external_primary/video/media/27"
        self.media = b"\x00\x00\x00\x18ftypisom"
        self.job = {"id": "new-job", "status": "DONE", "output": self.uri, "bytes": len(self.media)}
        self.adb = ["adb", "-s", "chosen-device"]

    def test_a_newer_gallery_item_does_not_replace_the_jobs_media(self):
        def transfer(command, *, stdout, **kwargs):
            self.assertEqual(command, [*self.adb, "exec-out", "content", "read", "--uri", self.uri])
            media = {self.uri: self.media, "content://media/external_primary/video/media/28": b"\x00\x00\x00\x18ftypmp42"}
            stdout.write(media[command[-1]])
        with patch("subprocess.run", side_effect=transfer):
            copy_output(self.adb, self.job, "video", self.output)
        self.assertEqual(self.output.read_bytes(), self.media)

    def test_failed_partial_and_wrong_media_jobs_leave_the_existing_output_alone(self):
        with patch("subprocess.run") as transfer:
            for changed in [{"status": "FAILED"}, {"status": "PARTIAL"}, {"status": "QUEUED"}, {"output": None},
                            {"output": "content://media/external_primary/audio/media/27"}, {"bytes": 0}]:
                with self.subTest(changed=changed), self.assertRaises((RuntimeError, ValueError)):
                    copy_output(self.adb, {**self.job, **changed}, "video", self.output)
            transfer.assert_not_called()
        self.assertEqual(self.output.read_bytes(), b"previous")

    def test_short_or_failed_transfer_preserves_the_previous_file_and_cleans_up(self):
        for fails in [False, True]:
            def transfer(command, *, stdout, **kwargs):
                stdout.write(b"r")
                if fails:
                    raise subprocess.CalledProcessError(1, command)
            with self.subTest(fails=fails), patch("subprocess.run", side_effect=transfer):
                with self.assertRaises((RuntimeError, subprocess.CalledProcessError)):
                    copy_output(self.adb, self.job, "video", self.output)
                self.assertEqual(self.output.read_bytes(), b"previous")
                self.assertEqual(list(self.output.parent.iterdir()), [self.output])

    def test_provider_error_text_equal_to_the_expected_size_is_not_media(self):
        def transfer(command, *, stdout, **kwargs):
            stdout.write(b"File missing")
        with patch("subprocess.run", side_effect=transfer), self.assertRaisesRegex(RuntimeError, "did not return"):
            copy_output(self.adb, self.job, "video", self.output)
        self.assertEqual(self.output.read_bytes(), b"previous")


if __name__ == "__main__":
    unittest.main()
