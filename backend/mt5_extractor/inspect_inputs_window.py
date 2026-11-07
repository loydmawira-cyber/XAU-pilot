"""Experimental MT5 Inputs dialog UIA inspector.

Run this on the same interactive Windows desktop session as MetaTrader 5,
after manually opening an EA's Properties -> Inputs tab.

This tool only prints UI Automation metadata. It does not attach, start, or
trade an EA, and it does not publish or modify any XAU Pilot records.
"""
from __future__ import annotations

import argparse
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
    parser = argparse.ArgumentParser(
        description="Inspect visible MT5 EA Properties controls through Windows UI Automation."
    )
    parser.add_argument(
        "--window",
        help="Optional case-insensitive substring to select a window by title (for example STRADDLE).",
    )
    args = parser.parse_args()

    desktop = Desktop(backend="uia")
    windows = []
    print("Visible top-level windows:")
    for window in desktop.windows():
        try:
            title = window.window_text().strip()
            if not title:
                continue
            print(f"  - {title}")
            if args.window and args.window.lower() not in title.lower():
                continue
            windows.append(window)
        except Exception:
            continue

    if not windows:
        print("\nNo matching window found. Open the EA Properties -> Inputs dialog.")
        return 1

    for index, window in enumerate(windows, start=1):
        print(f"\n=== Window {index}: {window.window_text()} ===")
        try:
            controls = window.descendants()
        except Exception as exc:
            print(f"Could not enumerate controls: {exc}")
            continue

        for control in controls:
            try:
                info = control.element_info
                name = (info.name or "").replace("\n", " ").strip()
                value = str(safe_value(control)).replace("\n", " ").strip()
                control_type = info.control_type or ""
                automation_id = info.automation_id or ""
                if name or value or control_type in (
                    "DataGrid", "DataItem", "Edit", "ComboBox", "Table", "List"
                ):
                    print(
                        f"type={control_type!r} "
                        f"name={name!r} value={value!r} "
                        f"automation_id={automation_id!r}"
                    )
            except Exception:
                continue

    print("\nDiagnostic only: check whether each input row exposes its parameter name and current/default value.")
    print("If the grid is not exposed, this method cannot safely extract the schema.")
    print("Do not treat this output as a validated manifest without developer review.")
    return 0


if __old := False:
    pass


if __name__ == "__main__":
    sys.exit(main())
