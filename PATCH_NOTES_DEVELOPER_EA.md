# XAU Pilot developer EA integration patch

This patch merges the newer nested work/ tree into the single canonical project root.

## Changes
- Removed duplicate nested project structure.
- Replaced hardcoded customer input catalogue with the signed published developer manifest for the product current version.
- Added backend validation of submitted customer settings against developer visibility/editability and typed manifest constraints.
- Made EA publication require a CLEAN binary scan.
- Added Retrofit declarations for developer registration, product/version creation, multipart binary/manifest upload, validation, preview and publishing.

## Limitations before production
- PublisherWorkspaceScreen still needs ContentResolver-backed file streaming, multipart request construction, upload progress and authenticated ViewModel/repository integration. Retrofit declarations alone do not complete UI upload.
- Installations currently resolve the product current published version. Add immutable ea_product_version_id to installations so new releases cannot silently change existing settings schemas.
- Configure a real malware scanner that returns CLEAN; none is bundled.
- Remote runtime changes require developer-side MQL5 bridge integration.
- Run migrations, backend tests and Android build before deployment.
