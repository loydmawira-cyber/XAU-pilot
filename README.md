# XAU Pilot

XAU Pilot is a Kotlin/Jetpack Compose Android client with an optional FastAPI/PostgreSQL backend for XAUUSD EA management.

## Android app

Open the repository root in Android Studio or AI Studio and let Gradle sync. The application does not seed demo accounts, positions, trades, EAs, quotes, or VPS records. Live features remain unavailable until you configure and deploy the backend and authenticate. The publisher upload requires a real signed-in developer session and a reachable backend.

From a machine with Android SDK installed:

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew test
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For live API mode, authenticate through the app's login flow (or the host integration), configure the API base URL, and set the backend's `JWT_SECRET` and `DATABASE_URL` environment variables. Do not put access tokens, broker passwords, database passwords, or signing keys in source control.

## Backend

Install the pinned dependencies, configure the database and JWT secret, then start FastAPI:

```bash
cd backend
python3 -m pip install -r requirements.txt
export DATABASE_URL='postgresql://user:password@host:5432/xau_pilot_db'
export JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
uvicorn backend.app.main:app --host 0.0.0.0 --port 8000
```

Health check: `GET /health`.

## Safety notes

Demo mode is intentionally local-only. Live account controls and emergency-stop commands should be used only after the backend, EA bridge, authentication, and risk limits have been configured and tested in a broker demo account.

## Developer EA releases (backend)

See `backend/docs/developer_integration_guide.md`. Run the checks that need no web stack:

```bash
python3 -m unittest backend.tests.test_manifest backend.tests.test_storage
```

API tests (need `pip install -r backend/requirements.txt`):

```bash
python3 -m unittest backend.tests.test_developer_api -v
```

Run with Docker (from `backend/`, with `backend/.env` filled in from `.env.example`):

```bash
docker compose up --build
```

## First real EA upload test

See `FIRST_TEST_DEPLOYMENT.md` for backend deployment and Android configuration. See `RELEASE_STATUS.md` for the exact implemented scope and features deliberately disabled until MT5, payment and VPS integrations are completed. This release does not simulate broker accounts, quotes, trades, EA acknowledgements or VPS state.
