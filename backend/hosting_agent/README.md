# XAU Pilot Windows Host Agent

This agent runs on a Windows host created by the configured provider adapter. It is separate from the FastAPI control plane.

The initial implementation exposes safe package verification and MT5 readiness checks. It must be installed as a restricted Windows service before demo activation is enabled.

## Required host layout

```text
C:\XauPilot\terminals\<terminal_id>\
  terminal.exe
  MQL5\Experts\<approved-package>.ex5
  MQL5\Include\XauPilotBridge.mqh
  config\terminal.ini
```

Each customer terminal must use a separate installation directory and Windows identity. Do not share customer EA files, logs, terminal data directories, or broker credentials.

## Readiness requirements

The agent should report `ready=true` only when:

- The MT5 terminal process is available.
- The terminal is connected to the expected broker server.
- Trading permission is enabled only for an explicitly activated installation.
- The package SHA-256 matches the approved manifest.
- The expected EA and chart profile are present.
- The XauPilot EA heartbeat is fresh.

The current Python helper uses the official `MetaTrader5` package for terminal and account inspection. It must never log broker passwords or raw secret payloads.

## Future service wrapper

Wrap `agent.py` in a signed Windows service. The service should receive typed jobs from the control plane, never arbitrary shell commands. Store broker credentials through a short-lived encrypted lease and clear the bootstrap payload after writing terminal configuration.
