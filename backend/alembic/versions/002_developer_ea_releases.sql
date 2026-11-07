-- 002: developer-controlled EA releases. Extends ea_products; does not duplicate it.
ALTER TABLE ea_products
    ADD COLUMN IF NOT EXISTS developer_id UUID REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS listing_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED';
CREATE INDEX IF NOT EXISTS idx_ea_products_developer ON ea_products(developer_id);

CREATE TABLE developer_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE TABLE ea_product_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ea_product_id UUID NOT NULL REFERENCES ea_products(id) ON DELETE CASCADE,
    version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    release_notes TEXT,
    manifest_hash CHAR(64),
    binary_sha256 CHAR(64),
    signature VARCHAR(128),
    published_by UUID REFERENCES users(id),
    published_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    UNIQUE (ea_product_id, version)
);
CREATE INDEX idx_ea_versions_product ON ea_product_versions(ea_product_id);

CREATE TABLE ea_binary_packages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_id UUID NOT NULL UNIQUE REFERENCES ea_product_versions(id) ON DELETE CASCADE,
    storage_key VARCHAR(300) NOT NULL,
    original_filename VARCHAR(200) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    scan_status VARCHAR(20) NOT NULL DEFAULT 'NOT_CONFIGURED',
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE TABLE ea_input_manifests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_id UUID NOT NULL UNIQUE REFERENCES ea_product_versions(id) ON DELETE CASCADE,
    schema_version VARCHAR(10) NOT NULL,
    canonical_json TEXT NOT NULL,
    manifest_hash CHAR(64) NOT NULL,
    validation_errors TEXT NOT NULL DEFAULT '[]',
    is_valid BOOLEAN NOT NULL DEFAULT FALSE,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE TABLE ea_developer_audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ea_product_id UUID REFERENCES ea_products(id) ON DELETE CASCADE,
    version_id UUID,
    actor_user_id UUID REFERENCES users(id),
    actor_role VARCHAR(40) NOT NULL,
    action VARCHAR(60) NOT NULL,
    details TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);
CREATE INDEX ix_ea_dev_audit_product ON ea_developer_audit_logs(ea_product_id, created_at);

CREATE TABLE ea_package_download_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    version_id UUID NOT NULL REFERENCES ea_product_versions(id),
    user_id UUID REFERENCES users(id),
    tenant_id UUID,
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);
CREATE INDEX idx_ea_download_logs_version ON ea_package_download_logs(version_id);

-- Immutability is enforced in the database as well as the API: once a version is
-- PUBLISHED its manifest, binary record and release fields cannot change or be deleted.
CREATE OR REPLACE FUNCTION ea_block_published_child_change() RETURNS trigger AS $$
DECLARE v_status TEXT; v_id UUID;
BEGIN
    v_id := COALESCE(OLD.version_id, NEW.version_id);
    SELECT status INTO v_status FROM ea_product_versions WHERE id = v_id;
    IF v_status IN ('PUBLISHED', 'RETIRED') THEN
        RAISE EXCEPTION 'EA version % is published; its manifest and binary are immutable', v_id;
    END IF;
    RETURN COALESCE(NEW, OLD);
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_manifest_immutable BEFORE UPDATE OR DELETE ON ea_input_manifests
    FOR EACH ROW EXECUTE FUNCTION ea_block_published_child_change();
CREATE TRIGGER trg_binary_immutable BEFORE UPDATE OR DELETE ON ea_binary_packages
    FOR EACH ROW EXECUTE FUNCTION ea_block_published_child_change();

CREATE OR REPLACE FUNCTION ea_block_published_version_change() RETURNS trigger AS $$
BEGIN
    IF OLD.status IN ('PUBLISHED', 'RETIRED') THEN
        IF TG_OP = 'DELETE' THEN
            RAISE EXCEPTION 'published EA versions cannot be deleted';
        END IF;
        IF NEW.version IS DISTINCT FROM OLD.version
           OR NEW.manifest_hash IS DISTINCT FROM OLD.manifest_hash
           OR NEW.binary_sha256 IS DISTINCT FROM OLD.binary_sha256
           OR NEW.signature IS DISTINCT FROM OLD.signature
           OR NEW.published_by IS DISTINCT FROM OLD.published_by
           OR NEW.published_at IS DISTINCT FROM OLD.published_at
           OR NEW.ea_product_id IS DISTINCT FROM OLD.ea_product_id
           OR NEW.status = 'DRAFT' THEN
            RAISE EXCEPTION 'published EA version fields are immutable';
        END IF;
    END IF;
    RETURN COALESCE(NEW, OLD);
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_version_immutable BEFORE UPDATE OR DELETE ON ea_product_versions
    FOR EACH ROW EXECUTE FUNCTION ea_block_published_version_change();
