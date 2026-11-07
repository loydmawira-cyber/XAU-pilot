# XAU Pilot — real EA upload test release

## Implemented in this pass

- Removed hard-coded EA products, publisher submissions, broker accounts, positions, trade history, VPS records, quote/candlestick seed builders and the fake publisher approval path from the Android repository.
- Removed the market tick simulation engine and the Demo/Live banner that incorrectly claimed a connected backend or live MT5 feed.
- The app now labels the market feed as waiting until actual quote data arrives.
- Disabled local fake account creation, EA assignment, licence activation, order execution, position closing and VPS state changes. The UI reports that those integrations are not part of this release.
- Account control commands no longer mark EAs running/paused/stopped on HTTP acceptance. Runtime state must be confirmed by the EA bridge.
- Publisher workspace uploads the selected `.ex5` bytes and developer-authored manifest to the backend and calls manifest validation.
- Upload status now displays the actual scanner status returned by the backend. A missing scanner does not become a clean scan and cannot pass publication.
- Access and refresh tokens are stored using AndroidX encrypted preferences backed by Android Keystore; startup attempts to restore the session with the backend.
- Backend URL configuration is persisted and requires HTTPS.
- Backend production configuration refuses to start without a configured JWT secret; CORS origins are environment-configurable.
- Added `/ready` for database readiness; `/health` reports liveness only.
- Removed the simulator VPS provider. AWS remains dry-run by default; a dry-run is reported as `SIMULATED`, not as a real VM.
- Bumped Room schema version to clear pre-release cached fixture data on upgrade.

## Intentionally not included yet

- MT5 account connection and terminal authentication.
- EA installation into an MT5 terminal, EA heartbeat and trade execution.
- Runtime parameter acknowledgements from a real MQL5 bridge.
- Customer checkout/payment and paid licence activation.
- Production malware scanner deployment and admin review console.
- Live AWS VPS provisioning and Windows MT5 host agent.

## Verification status

- Backend tests: 43 passed.
- Backend Python compile check: passed.
- Android Gradle APK build: not verified. The Gradle wrapper attempted to download Gradle 9.3.1, but network access to `services.gradle.org` was unavailable in the build environment.
- Deployment and Android-to-backend upload must still be tested on your own HTTPS host.

## Before first upload test

1. Deploy the backend using `FIRST_TEST_DEPLOYMENT.md`.
2. Set strong unique `POSTGRES_PASSWORD`, `JWT_SECRET` and `MANIFEST_SIGNING_KEY` values in `backend/.env`.
3. Configure a real malware scanner. Until it reports `CLEAN`, publication remains blocked.
4. Verify `/ready` and configure the app's Profile → Backend & API Configuration with the deployed HTTPS `/api/v1/` URL.
5. Register a real user, open Publisher Workspace and upload a real `.ex5` file. Use `[]` if the EA has no customer-adjustable inputs; do not invent manifest parameters.
