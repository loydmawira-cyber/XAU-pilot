# XAU Pilot MT5 extraction worker contract

## Purpose

The API accepts a developer's single EX5 upload. A separate, isolated Windows worker inspects the EA in MetaTrader 5 and returns the Inputs schema for developer review. The API host remains Linux; it must never attempt to inspect EX5 internals or infer inputs.

## Required deployment boundary

- Run the worker on a dedicated Windows VM with a pinned, supported MT5 terminal build.
- Use a disposable, isolated Windows profile and a demo-only terminal with no broker credentials or live account.
- Disable AutoTrading and all network access from the MT5 terminal except the minimum required to obtain terminal updates, if any.
- Never attach the uploaded EA to a live account, enable trading, publish it, or execute its OnInit/OnTick logic in a trading environment.
- Treat EX5 files as untrusted executable code. UI inspection by attaching an EA can execute its initialization code; therefore the worker must run in a disposable VM with outbound network blocked and no secrets. If that isolation cannot be guaranteed, do not execute the EX5 and do not claim automatic extraction is available.
- Destroy/revert the VM snapshot after each job.

## Job lifecycle

1. Successful EX5 upload creates a pending extraction job bound to product ID, version ID, storage key, and SHA-256.
2. Worker claims one job using an authenticated, short-lived lease. Claim response includes a one-time download URL or streamed binary and the expected SHA-256.
3. Worker verifies SHA-256 before inspection.
4. Worker installs the binary into a clean MT5 data directory, opens Properties -> Inputs in the isolated desktop session, and captures only values MT5 actually exposes.
5. Worker submits a structured result with the job ID, binary SHA-256, terminal build, extractor version, extracted rows, and unresolved rows.
6. API rejects results if the lease expired, job is not in progress, checksum differs, or payload fails strict validation.
7. Successful extraction creates a developer-owned draft schema. It is not published automatically.
8. Developer reviews exact keys, types, defaults, and enum choices, then selects customer visibility/editability and approves the schema.
9. Publication remains blocked until the binary scan is CLEAN, extraction is complete, developer approval is recorded, schema validation passes, and the schema hash is bound to the exact binary hash.

## Result format

The worker returns JSON with this logical shape:

```json
{
  "job_id": "server-issued-id",
  "binary_sha256": "64 lowercase hex characters",
  "terminal_build": "MT5 build identifier",
  "extractor_version": "worker release identifier",
  "status": "COMPLETE",
  "inputs": [
    {
      "key": "exact MT5 input variable name",
      "display_name": "label shown by MT5",
      "type": "bool|int|double|string|enum|unresolved",
      "default": "value captured from MT5",
      "choices": [],
      "source": "MT5 Inputs dialog"
    }
  ],
  "unresolved": []
}
```

The result is evidence for developer review, not a customer manifest. The worker must not invent ranges, steps, descriptions, visibility, editability, or restart behavior. Those remain developer-controlled choices. Values that cannot be read unambiguously must be marked unresolved.

## Scanner and storage gates

- Configure a real malware scanner on the API host; `NOT_CONFIGURED` and `ERROR` are never equivalent to CLEAN.
- Use durable private object storage or a persistent encrypted disk for EX5 packages. Local ephemeral container storage is not a production retention solution.
- Never expose raw storage paths or public unauthenticated binary URLs.
- Keep upload, scan, extraction, review, and publication states separately observable.

## Current implementation status

The existing `inspect_inputs_window.py` is a manual diagnostic only. This contract does not claim that a production worker, authenticated job API, durable storage, or malware scanner is deployed. Those components must be implemented and tested before the UI can show extraction as active.
