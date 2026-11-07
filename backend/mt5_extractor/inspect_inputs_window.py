"""Experimental MT5 Inputs dialog UIA inspector.

Run this on the same interactive Windows desktop session as MetaTrader 5,
after manually opening an EA's Properties -> Inputs tab.

This tool only prints UI Automation metadata. It does not attach, start, or
trade an EA, and it does not publish or modify any XAU Pilot records.
"""
from __future__ import annotations

import sys

try:
    from pywinauto import Desktop
except ImportError:
    print("Missing dependency. Install with: py -m pip install pywinauto")
    raise SystemExit(2)


def safe_value(control):
    try:
        return control.get_value()
    except Exception:
        return ""


def main() -> int:
    desktop = Desktop(backend="uia")
    candidates = []
    for window in desktop.windows():
        try:
            title = window.window_text()
            if title and any(word in title.lower() for word in ("expert", "properties", "advisor")):
                candidates.append(window)
        except Exception:
            continue

    if not candidates:
        print("No likely EA properties window found.")
        print("Open the EA Properties dialog and select its Inputs tab, then retry.")
        return 1

    for index, window in enumerate(candidates, start=1):
        print(f"\n=== Candidate {index}: {window.window_text()} ===")
        try:
            for control in window.descendants():
                try:
                    info = control.element_info
                    name = (info.name or "").replace("\n", " ").strip()
                    value = str(safe_value(control)).replace("\n", " ").strip()
                    control_type = info.control_type or ""
                    automation_id = info.automation_id or ""
                    if name or value or control_type in ("DataGrid", "DataItem", "Edit", "ComboBox"):
                        print(
                            f"type={control_type!r} "
                            f"name={name!r} value={value!r} "
                            f"automation_id={automation_id!r}"
                        )
                except Exception:
                    continue
        except Exception as exc:
            print(f"Could not inspect this window: {exc}")

    print("\nDiagnostic only: inspect whether every input row exposes a distinct key and value.")
    print("Do not treat this output as a validated manifest without developer review.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
