# Real EA upload work applied

- Publisher workspace now selects a real document URI, reads the actual `.ex5` bytes, validates extension/size, and uploads multipart content to the developer API.
- Publisher form collects product metadata and a developer-authored JSON parameter array; the app binds the manifest to backend-generated product/version IDs and the authenticated user ID.
- Publisher flow creates/uses a developer profile, creates a product and version, uploads binary and manifest, then calls backend manifest validation. It explicitly does not claim publication after upload.
- Added actual account creation/sign-in controls in Publisher Workspace. Access and refresh tokens are persisted with AndroidX encrypted shared preferences backed by Android Keystore, and the app restores the session against the backend on startup.
- Corrected Android multipart endpoint paths to match FastAPI `/upload-binary` and `/upload-manifest` routes.
- Removed hard-coded demo seed builders and the demo publisher workflow. The local Room schema version is bumped to clear pre-release cached fixtures. Removed the fake market tick engine, demo mode banner, and misleading LIVE-feed labels.
- Removed the simulator VPS provider. AWS remains dry-run by default and clearly reports SIMULATED rather than claiming a VM exists. AWS provisioning still requires credentials/configuration.
- Added `FIRST_TEST_DEPLOYMENT.md` with deployment, HTTPS, scanner, backend URL and manifest instructions.

## Verification

- Backend Python compile check: passed.
- Backend test suite: 43 passed.
- Android Gradle build: NOT VERIFIED. The Gradle wrapper could not download Gradle because this environment had no network access (`UnknownHostException: services.gradle.org`).

## Known blockers before the first end-to-end upload

- Deploy the backend and configure the app with its HTTPS API URL.
- Configure a real malware scanner inside the backend deployment. Without it, publication should remain blocked.
- Android auth session is held in process memory; secure persistent token storage and refresh/revocation handling still need implementation before production.
- The broader trading dashboard still contains legacy demo-oriented screens/components and is not yet certified as fully free of every demo label or fallback. No MT5 connection/trading functionality is included in this upload stage.
- The example manifest is not generated from `.ex5` introspection. Only declare real parameters confirmed by the EA developer/source; use `[]` if none are customer-adjustable.
- Customer marketplace review/admin UX, purchases, licensed downloads, installation version pinning and MT5 terminal integration require further work.

- Disabled local fake broker-account creation, EA assignment, licence activation, order execution, position closing and VPS state transitions. These features now report that MT5/payment integrations are not available rather than creating fake records.
- Account-control commands no longer mark an EA as paused/running/stopped merely because the API accepted a request; the app waits for a real EA acknowledgement.
- Added encrypted persistent access/refresh token storage, session restoration, HTTPS URL validation, production JWT secret guard, configurable CORS origins and a database readiness endpoint.
- Added bounded streaming read for selected `.ex5` files to enforce the 25 MB cap without reading an unbounded file into memory.
