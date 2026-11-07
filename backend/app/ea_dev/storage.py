"""Private EX5 package storage. Files are never served from a public path;
downloads must go through an authenticated, entitlement-checked endpoint."""
from __future__ import annotations

import hashlib
import os
import subprocess
import uuid
from dataclasses import dataclass
from typing import BinaryIO, Tuple

from backend.app.config import settings

SOURCE_MARKERS = (b"#property", b"#include <", b"void OnInit", b"int OnInit", b"void OnTick")


class StorageError(ValueError):
    pass


@dataclass
class StoredFile:
    storage_key: str
    sha256: str
    size_bytes: int


def _root() -> str:
    root = os.path.abspath(settings.EA_STORAGE_DIR)
    os.makedirs(root, mode=0o700, exist_ok=True)
    return root


def _path(storage_key: str) -> str:
    root = _root()
    full = os.path.abspath(os.path.join(root, storage_key))
    if os.path.commonpath([root, full]) != root:
        raise StorageError("invalid storage key")
    return full


def save_ex5(product_id: str, version_id: str, filename: str, stream: BinaryIO) -> StoredFile:
    if not (filename or "").lower().endswith(".ex5"):
        raise StorageError("only compiled .ex5 files are accepted")
    key = f"{product_id}/{version_id}/{uuid.uuid4().hex}.ex5"
    full = _path(key)
    os.makedirs(os.path.dirname(full), mode=0o700, exist_ok=True)
    digest, size, head = hashlib.sha256(), 0, b""
    try:
        with open(full, "wb") as out:
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > settings.EA_MAX_BINARY_BYTES:
                    raise StorageError(f"file larger than {settings.EA_MAX_BINARY_BYTES} bytes")
                if len(head) < 65536:
                    head += chunk[: 65536 - len(head)]
                digest.update(chunk)
                out.write(chunk)
        if size == 0:
            raise StorageError("file is empty")
        if any(marker in head for marker in SOURCE_MARKERS):
            raise StorageError("this looks like MQ5 source code; upload the compiled .ex5 only")
    except Exception:
        if os.path.exists(full):
            os.remove(full)
        raise
    return StoredFile(key, digest.hexdigest(), size)


def open_binary(storage_key: str) -> BinaryIO:
    return open(_path(storage_key), "rb")


def sha256_of(storage_key: str) -> str:
    digest = hashlib.sha256()
    with open_binary(storage_key) as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def delete_binary(storage_key: str) -> None:
    try:
        os.remove(_path(storage_key))
    except FileNotFoundError:
        pass


def scan_binary(storage_key: str) -> Tuple[str, str]:
    """Malware-scan integration point. Set EA_SCAN_COMMAND (e.g. 'clamdscan --no-summary')
    to enable. Returns (status, detail); status is CLEAN, INFECTED, ERROR or NOT_CONFIGURED."""
    cmd = settings.EA_SCAN_COMMAND
    if not cmd:
        return "NOT_CONFIGURED", "no scanner configured"
    try:
        res = subprocess.run(cmd.split() + [_path(storage_key)], capture_output=True, text=True, timeout=120)
    except Exception as exc:  # scanner missing / timeout
        return "ERROR", str(exc)[:200]
    if res.returncode == 0:
        return "CLEAN", ""
    if res.returncode == 1:
        return "INFECTED", (res.stdout or "")[:200]
    return "ERROR", (res.stderr or "")[:200]
