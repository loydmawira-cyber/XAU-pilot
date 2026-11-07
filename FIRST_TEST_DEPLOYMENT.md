# XAU Pilot — first EA upload test

## What this build supports

- No seeded trading accounts, positions, trades, EA marketplace entries, VPS records, simulated quote or fake publisher approvals are loaded into the fresh real-data database.
- Publisher workspace supports real account registration/sign-in against the backend.
- Publisher workflow creates a developer profile, EA product and immutable version, uploads the selected `.ex5` file and developer manifest, then calls backend validation.
- The UI reports upload/validation status only. It does not claim the EA is published merely because upload succeeded.
- MT5 connection, EA installation into a terminal, live/demo execution and managed VPS provisioning are not part of this first upload test.

## Backend deployment

1. Provision a Linux VPS or cloud VM with Docker Engine and the Docker Compose plugin.
2. Copy the repository to the server.
3. In `backend/`, copy `.env.example` to `.env`.
4. Generate separate secrets:

   ```bash
   python3 -c 'import secrets; print(secrets.token_urlsafe(48))'
   ```

   Set `POSTGRES_PASSWORD`, `JWT_SECRET`, and `MANIFEST_SIGNING_KEY` to strong independent values. Set `DATABASE_URL` to `postgresql://xau_user:<URL-ENCODED-PASSWORD>@db:5432/xau_pilot_db`.
5. Configure a real malware scanner. Recommended: run ClamAV/clamd in the deployment and set `EA_SCAN_COMMAND` to a command available inside the backend container that exits 0 for clean and non-zero for infected/error. Do not publish until this is operational. The example has no scanner configured, so publishing should fail closed.
6. Configure HTTPS using a reverse proxy (Caddy or Nginx) and a valid TLS certificate. Do not expose the API over public plain HTTP.
7. Start the services:

   ```bash
   docker compose --env-file .env up -d --build
   docker compose logs -f backend
   ```

8. Verify `https://YOUR_API_HOST/health` (liveness) and `https://YOUR_API_HOST/ready` (database readiness) from a browser. Protect `/docs` or disable it for production.
9. In the Android app, open Profile → Backend & API Configuration and enter `https://YOUR_API_HOST/api/v1/` (include the trailing slash if possible), then save/apply the URL. The app initially points to localhost and will not reach a remote server until you configure your deployed HTTPS address.
10. Open Publisher Workspace, create an account, sign in, select a real `.ex5` file, complete the product details and manifest JSON, and upload.

## Manifest format

The editor accepts a JSON array of developer-defined parameters. Example only — replace these keys, labels, types, defaults and limits with the actual inputs supported by your EA source/binary:

```json
[
  {
    "key": "ExampleLotSize",
    "label": "Example lot size",
    "description": "Example only; must match an actual developer-supported EA input.",
    "type": "decimal",
    "default": 0.01,
    "minimum": 0.01,
    "maximum": 1.0,
    "step": 0.01,
    "unit": "lots",
    "visible_to_customer": true,
    "customer_editable": true,
    "requires_restart": true,
    "requires_no_open_positions": true
  }
]
```

Use `[]` when the EA has no customer-adjustable inputs. The example parameter must not be represented as a real input unless it actually exists in the EA. The backend binds the manifest to the authenticated developer, product and version.

## Important limits / not yet complete

- Android APK compilation has not been verified in this environment because Gradle distribution download requires internet access and was unavailable.
- Backend tests pass locally (43 tests); deployment on your own host still needs verification.
- The APK needs a reachable HTTPS backend and real user credentials before publisher upload can work.
- `EA_SCAN_COMMAND` must be connected to a real scanner; otherwise publication remains blocked.
- Product listing approval/admin workflow still needs its production admin UI and role-protected operational process.
- Customer-side marketplace purchase/download and installation are separate follow-up work.
- MT5 and VPS integrations are deliberately deferred as requested.
- The API base URL is a placeholder until you deploy and configure your own host.
