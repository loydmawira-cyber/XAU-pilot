# XAU Pilot — EA developer integration guide

You (the developer) decide exactly which settings customers see and can change. XAU Pilot
administrators cannot add, edit, relax or unlock any parameter in your manifest.

## 1. Workflow
1. `POST /api/v1/developer/register`
2. `POST /api/v1/developer/ea-products` (listing details, prices)
3. `POST /developer/ea-products/{id}/versions` with `{"version": "1.0.0"}`
4. Upload the compiled `.ex5` (`upload-binary`) and your manifest (`upload-manifest`). Never upload `.mq5` source.
5. `validate`, then `GET .../preview` to see exactly what customers will see.
6. `publish` — the manifest and binary become **immutable**; the server records their SHA-256 hashes and signs them.
7. `submit-for-review` — a platform admin approves or rejects the *listing*. Admins cannot change your manifest.
To change inputs later, publish a new version (`1.1.0`). Old versions and their history are kept.

## 2. Manifest (`schema_version` "1.0")
```json
{
  "schema_version": "1.0",
  "ea_product_id": "<your product id or product_code>",
  "ea_version": "1.0.0",
  "developer_id": "<your user id>",
  "parameters": [
    {"key": "lot_size", "label": "Lot Size", "description": "Volume per order.",
     "type": "decimal", "default": 0.01, "minimum": 0.01, "maximum": 1.0, "step": 0.01,
     "unit": "lots", "category": "Trading",
     "visible_to_customer": true, "customer_editable": true,
     "requires_restart": true, "requires_no_open_positions": false},
    {"key": "mode", "label": "Mode", "type": "enum", "default": "BOTH",
     "choices": ["BUY", "SELL", "BOTH"], "visible_to_customer": true, "customer_editable": false}
  ]
}
```
- Types: `integer`, `decimal`, `boolean`, `string` (optional `max_length`), `enum` (`choices`: strings or `{value,label}`).
- Required per parameter: `key`, `label`, `type`, `default`, `visible_to_customer`, `customer_editable`.
- Units: lots, points, pips, percent, usd, seconds, minutes, hours, bars, trades, multiplier.
- A hidden parameter can never be editable. Unknown fields are rejected. Keys must be unique `[A-Za-z_][A-Za-z0-9_]{0,63}`.
- `default` must satisfy min/max and fall on a `step` boundary. `ea_version`, `ea_product_id` and `developer_id` must match the version/product/you.
- If no parameter is customer-editable, customers see: "The developer has not enabled customer-adjustable settings for this EA."

## 3. Remote configuration inside your MQL5 EA (developer-side work)
An `.ex5` cannot be modified by the platform. To receive customer settings your EA must include
`backend/mql5/XauPilotBridge.mqh`, poll the bridge with its installation token, validate received values against
its own limits, apply them, and acknowledge. A configuration is only `APPLIED` after that acknowledgement.
Until your EA does this, the integration is **pending developer-side MQL5 work**.

## 4. Server settings (operators)
`MANIFEST_SIGNING_KEY` (>=32 chars, separate from `JWT_SECRET`; publishing is refused without it),
`EA_STORAGE_DIR`, `EA_MAX_BINARY_BYTES`, `EA_SCAN_COMMAND`. See `backend/.env.example`.
