# Owner MT5 performance feed — implementation notes

## Flow
This first release accepts publisher performance connections in DEMO mode only. Live-account performance remains disabled pending security, legal, broker and reconciliation approval.

1. An authenticated developer calls `POST /api/v1/developer/ea-products/{product_id}/performance-account` with broker name, server, MT5 account number, DEMO/LIVE, magic number, symbol and timeframe. Do not send the broker password.
2. The API creates a publisher-only EA installation and returns its installation ID and a random bridge token exactly once. Store the token securely; never commit it to GitHub or expose it to marketplace guests. If lost, rotate it using `POST /api/v1/developer/ea-products/{product_id}/performance-account/rotate-token`; the previous token immediately stops working.
3. Use a dedicated MT5 account for each published EA; other EAs or manual trades on that account would contaminate account-wide equity snapshots. The developer installs a bridge-compatible build of their EA in their own MT5 terminal and configures the backend URL, installation ID, token and magic number. MT5 WebRequest must allow the backend HTTPS domain.
4. The MQL5 bridge reports closed deals only (exit deals) and periodic balance/equity snapshots. API endpoints require the per-installation bearer token. Duplicate deal tickets are ignored. Snapshot writes are throttled to one per minute.
5. Guests read `GET /api/v1/marketplace/ea-products/{product_id}/performance`. Only aggregate metrics and equity samples are returned; no account number, installation token, owner identity, or broker credentials are public.

## Public status labels
- `NO_CONNECTED_OWNER_ACCOUNT`: no publisher tracking account exists.
- `BUILDING_TRACK_RECORD`: real MT5 data has started, but fewer than 10 closed trades or fewer than 7 days have been collected.
- `CONNECTED_MT5_DEMO` / `CONNECTED_MT5_LIVE`: feed has at least 10 closed trades and 7 days of snapshots. This means the app ingested an authenticated EA feed; it is **not an independently audited broker statement**.

## Metrics
- Net profit = sum of closed deal profit + commission + swap.
- Win rate = profitable closed deals / all closed deals.
- Profit factor = gross positive net deal result / absolute gross negative net deal result.
- Maximum equity drawdown = maximum peak-to-trough percentage measured from the recorded equity snapshots.
- Equity curve contains timestamped balance/equity snapshots.

## Important limitations and release gates
- This first feed is EA-reported. For stronger independent verification, add a broker statement import or broker-authorized read-only integration and reconcile deal tickets, deposits/withdrawals and account currency.
- The EA source must call `XauPilot_ReportTradeEvent` from its `OnTradeTransaction` and call `XauPilot_SendPerformanceSnapshot` through the bridge timer. The owner’s compiled EX5 must be built with the compatible bridge; an existing EX5 cannot be patched remotely.
- The bridge header must be included and the EA must initialize it with the installation ID/token returned by the API. The developer must merge the bridge callbacks into their EA’s own event handlers; do not create duplicate `OnTimer` or `OnTradeTransaction` functions.
- MT5 WebRequest requires the backend URL to be explicitly allow-listed in terminal options.
- Run migration `004_owner_mt5_performance.sql` before deploying this backend. Back up PostgreSQL first.
- Enforce HTTPS, keep JWT and EA tokens in Secret Manager, rotate/revoke tokens on compromise, add rate limiting and alerting before public production.
- Demo is the recommended first verification stage. Live-account public performance should be disabled until legal, broker, security and reconciliation reviews are complete.
