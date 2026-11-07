# XAU Pilot — EA owner MT5 performance requirements

## Public guest access
Guests may browse published EAs, open EA details, and view the same public performance record available to signed-in customers. Guest access is read-only. Purchasing/licensing, uploading, connecting a personal MT5 account, installing an EA, and trading require authentication.

## Source of profitability data
The public track record must be generated from the EA owner's designated MT5 account on which that exact EA product/version is running. It must not be generated from customer accounts, manually typed marketplace claims, fabricated sample values, or unrelated account activity.

## Required owner-account connection and attribution
1. The EA owner explicitly links and authorizes an MT5 account for performance reporting.
2. XAU Pilot records the MT5 broker/server, account identifier in protected form, account currency, account type (demo/live), and authorization/connection state.
3. Each running EA instance must be attributable to the correct product and immutable EA version (for example using the EA's installation identity and magic number, with symbol/timeframe where applicable).
4. Import closed deals and relevant balance/equity history from MT5. Reconcile deposits, withdrawals, credits, fees, swaps, commissions, and balance adjustments so they are not misrepresented as trading profit.
5. Calculate net profit and return, maximum drawdown, win rate, trade count, average win/loss, and an equity curve from reconciled source records. Define the return denominator and reporting period explicitly.
6. Store source timestamps, sync time, account type, broker/server, EA version, and verification status with every published performance snapshot. Retain auditable source records and detect gaps or resets.
7. Never label owner-provided screenshots or manually entered figures as verified. Those may appear only as clearly marked developer-provided/unverified claims, subject to platform policy.

## Public labels
- **Verified Live** — reconciled data from an authorized live MT5 owner account.
- **Verified Demo** — reconciled data from an authorized demo MT5 owner account.
- **Backtested** — strategy tester results, never presented as live trading history.
- **Developer-provided / Unverified** — figures not independently reconciled with MT5.
- **No verified track record yet** — no eligible owner-account data has been received.

## Security and privacy
Never expose MT5 passwords, investor passwords, access tokens, full account credentials, or private account details to guests. Public reporting must use approved aggregate statistics only. The owner must be able to revoke reporting authorization. Require secure server-side credential handling, least privilege, encrypted transport/storage, audit logs, and explicit consent before collecting account history.

## Implementation status of this patch
The Android marketplace patch adds the public performance section and honest empty state; it does not claim that an MT5 connection, history importer, reconciliation engine, or verified profitability API already exists. Those backend/MT5 components must be implemented and tested before any numbers are labelled verified.
