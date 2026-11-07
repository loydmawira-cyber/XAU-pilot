# MT5 EX5 input extraction proof of concept

## Goal

Discover an EA's customer-facing inputs by asking a real Windows MetaTrader 5 terminal to open the EX5 and show its **Inputs** properties page. XAU Pilot must not decompile EX5 files or guess parameter names/defaults.

## Important technical boundary

The official MetaTrader 5 Python package can connect to a terminal and inspect account/terminal state, but it does not expose a supported API that enumerates arbitrary compiled EA input declarations. A reliable experiment therefore needs the actual MT5 desktop UI. UI automation is terminal-build- and language-dependent; this is experimental until tested against real EAs.

## Safe proof-of-concept procedure

1. Use a dedicated Windows VM with MetaTrader 5 installed and logged into a demo account (never a customer's live account).
2. Install the EA supplied by its developer into that terminal's `MQL5/Experts` folder.
3. Open a chart, attach the EA, and open its Properties dialog.
4. Select the **Inputs** tab.
5. Run `python inspect_inputs_window.py` on that same Windows desktop session.
6. Inspect the captured control tree. Confirm whether MT5 exposes each row's variable name and default value as accessible UI Automation properties.
7. Repeat with different terminal builds, display scaling, and terminal languages. Do not enable automatic publishing until the extraction is verified.

## Data integrity rules

- Extract only what MT5 actually exposes; never infer hidden settings.
- Keep the original EX5 checksum and associate extracted metadata with that exact binary hash.
- Preserve exact parameter keys, types, defaults, and enum choices when exposed.
- If a field cannot be read unambiguously, mark it unresolved and require developer review.
- The developer must approve the discovered schema and choose customer visibility/editability. Admins cannot alter EA strategy inputs.
- Do not start, trade, or publish the EA during extraction.

## Current status

This is an isolated diagnostic proof of concept, not a production extraction service. No claim is made that all MT5 builds expose the Inputs grid through UI Automation. It requires a Windows MT5 terminal and a real EX5 sample to validate. The Linux FastAPI backend cannot perform this desktop operation by itself.
