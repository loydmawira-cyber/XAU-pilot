import hashlib
import io
import os
import tempfile
import unittest

from backend.app.config import settings
from backend.app.ea_dev import storage


class StorageTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self._old = (settings.EA_STORAGE_DIR, settings.EA_MAX_BINARY_BYTES)
        settings.EA_STORAGE_DIR, settings.EA_MAX_BINARY_BYTES = self.tmp.name, 1024

    def tearDown(self):
        settings.EA_STORAGE_DIR, settings.EA_MAX_BINARY_BYTES = self._old
        self.tmp.cleanup()

    def test_saves_and_checksums(self):
        data = b"\x00\x01binary" * 10
        f = storage.save_ex5("p", "v", "Gold.ex5", io.BytesIO(data))
        self.assertEqual(f.sha256, hashlib.sha256(data).hexdigest())
        self.assertEqual(storage.sha256_of(f.storage_key), f.sha256)

    def test_rejects_wrong_extension(self):
        with self.assertRaises(storage.StorageError):
            storage.save_ex5("p", "v", "Gold.mq5", io.BytesIO(b"abc"))

    def test_rejects_source_code(self):
        with self.assertRaises(storage.StorageError):
            storage.save_ex5("p", "v", "Gold.ex5", io.BytesIO(b"#property copyright 'x'"))

    def test_rejects_oversize_and_cleans_up(self):
        with self.assertRaises(storage.StorageError):
            storage.save_ex5("p", "v", "Gold.ex5", io.BytesIO(b"\x01" * 2048))
        leftovers = [f for _, _, fs in os.walk(self.tmp.name) for f in fs]
        self.assertEqual(leftovers, [])

    def test_rejects_empty(self):
        with self.assertRaises(storage.StorageError):
            storage.save_ex5("p", "v", "Gold.ex5", io.BytesIO(b""))

    def test_blocks_path_traversal(self):
        with self.assertRaises(storage.StorageError):
            storage.open_binary("../../etc/passwd")

    def test_tamper_detected_by_checksum(self):
        f = storage.save_ex5("p", "v", "Gold.ex5", io.BytesIO(b"\x01\x02\x03"))
        with open(os.path.join(self.tmp.name, f.storage_key), "ab") as fh:
            fh.write(b"evil")
        self.assertNotEqual(storage.sha256_of(f.storage_key), f.sha256)

    def test_scanner_not_configured_by_default(self):
        f = storage.save_ex5("p", "v", "Gold.ex5", io.BytesIO(b"\x01\x02"))
        self.assertEqual(storage.scan_binary(f.storage_key)[0], "NOT_CONFIGURED")


if __name__ == "__main__":
    unittest.main()
