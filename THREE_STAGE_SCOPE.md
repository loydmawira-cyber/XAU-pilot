# Three-stage XAU Pilot scope

This build implements the product workflow without VPS provisioning or linking.

## Stage 1 — Managed execution contract

The Android app supports broker-account setup, EA licensing, installation records, risk profiles, configuration drafts, commands, telemetry models, and an explicit queued state. New EA assignments are marked `DISCONNECTED` with `DRAFT` configuration until a hosting link is attached. The app does not claim that an EA is live when no terminal is connected.

## Stage 2 — Customer automation contract

The customer flow is marketplace -> license -> MT5 account -> assign EA -> configure risk and parameters. The remaining host adapter will consume the installation record and deploy the selected package to an MT5 terminal. The UI already exposes the installation and configuration state needed for that adapter.

## Stage 3 — Publisher marketplace

Publishers can choose a compiled `.ex5` package in the Publisher Workspace, enter product metadata and pricing, submit it for review, and see review status. Demo admin actions can approve or request changes. Approval publishes the product into the local marketplace state. Production persistence, malware scanning, billing, and object storage belong in the backend publisher service before public launch.

## Deliberately deferred

VPS provisioning, MT5 terminal linking, automatic EA deployment, broker-password handling, live execution, payment processing, and production file storage are not enabled by this build. They must be added behind a hosting adapter and tested on demo accounts before live trading is allowed.
