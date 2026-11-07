"""Windows host agent skeleton for XAU Pilot.

Run this component on the Windows host, not in the FastAPI container. It is
intentionally conservative: it reports readiness and refuses activation unless
terminal, broker, and EA checks pass.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
from typing import Any


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_package(path: str, expected_sha256: str) -> bool:
    return sha256_file(path).lower() == expected_sha256.lower()


def terminal_readiness(expected_server: str | None = None) -> dict[str, Any]:
    """Read MT5 health when the MetaTrader5 package is installed on Windows."""
    try:
        import MetaTrader5 as mt5
    except ImportError:
        return {"ready": False, "reason": "MetaTrader5 Python package is not installed"}

    info = mt5.terminal_info()
    account = mt5.account_info()
    if info is None or account is None:
        return {"ready": False, "reason": "MT5 terminal or account is unavailable"}

    connected = bool(getattr(info, "connected", False))
    trade_allowed = bool(getattr(info, "trade_allowed", False))
    server = getattr(account, "server", None)
    server_matches = expected_server is None or server == expected_server
    ready = connected and trade_allowed and server_matches
    return {
        "ready": ready,
        "connected": connected,
        "trade_allowed": trade_allowed,
        "server": server,
        "server_matches": server_matches,
        "terminal_path": getattr(info, "path", None),
        "data_path": getattr(info, "data_path", None),
        "build": getattr(info, "build", None),
    }


def build_health_payload(installation_id: str, expected_server: str | None = None) -> dict[str, Any]:
    return {
        "installation_id": installation_id,
        "agent_version": os.getenv("XAUPILOT_AGENT_VERSION", "0.1.0"),
        "terminal": terminal_readiness(expected_server),
    }


if __name__ == "__main__":
    installation_id = os.environ.get("XAUPILOT_INSTALLATION_ID", "unknown")
    print(json.dumps(build_health_payload(installation_id, os.environ.get("XAUPILOT_EXPECTED_SERVER"))))
