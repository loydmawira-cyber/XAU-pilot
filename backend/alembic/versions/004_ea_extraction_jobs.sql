-- 004: checksum-bound EX5 extraction queue.
-- The worker is external; this table only records jobs and short-lived leases.
CREATE TABLE IF NOT EXISTS ea_extraction_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES ea_products(id) ON DELETE CASCADE,
    version_id UUID NOT NULL UNIQUE REFERENCES ea_product_versions(id) ON DELETE CASCADE,
    binary_sha256 CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETE', 'FAILED')),
    lease_token_hash CHAR(64),
    lease_expires_at TIMESTAMPTZ,
    worker_id VARCHAR(100),
    result_json TEXT,
    error_code VARCHAR(60),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);
CREATE INDEX IF NOT EXISTS ix_ea_extract_status_created
    ON ea_extraction_jobs(status, created_at);
CREATE INDEX IF NOT EXISTS ix_ea_extraction_jobs_product
    ON ea_extraction_jobs(product_id);
