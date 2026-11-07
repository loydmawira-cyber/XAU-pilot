-- ====================================================================
-- XAU PILOT: PRODUCTION POSTGRESQL MULTI-TENANT SCHEMA (001_initial)
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. TENANCY & USERS
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(120) NOT NULL,
    plan VARCHAR(60) NOT NULL DEFAULT 'Enterprise Gold',
    max_accounts INT NOT NULL DEFAULT 10,
    max_ea_installations INT NOT NULL DEFAULT 25,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(40) NOT NULL DEFAULT 'CUSTOMER', -- CUSTOMER, SUPPORT_AGENT, ADMINISTRATOR, SUPER_ADMINISTRATOR
    mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE INDEX idx_users_tenant ON users(tenant_id);

-- 2. MT5 BROKER ACCOUNTS
CREATE TABLE broker_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    label VARCHAR(100) NOT NULL,
    broker_name VARCHAR(100) NOT NULL,
    server_name VARCHAR(100) NOT NULL,
    account_number_masked VARCHAR(50) NOT NULL,
    environment VARCHAR(20) NOT NULL DEFAULT 'DEMO', -- 'DEMO' or 'LIVE'
    connection_status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED', -- CONNECTED, DISCONNECTED, ERROR
    currency VARCHAR(10) NOT NULL DEFAULT 'USD',
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    equity NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    free_margin NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    used_margin NUMERIC(18, 4) NOT NULL DEFAULT 0.0000,
    margin_level_percent NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    leverage INT NOT NULL DEFAULT 500,
    last_heartbeat TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE INDEX idx_broker_accounts_tenant ON broker_accounts(tenant_id);

CREATE TABLE account_risk_profiles (
    account_id UUID PRIMARY KEY REFERENCES broker_accounts(id) ON DELETE CASCADE,
    max_daily_loss_amount NUMERIC(18, 4) NOT NULL DEFAULT 500.00,
    max_total_exposure_lots NUMERIC(10, 2) NOT NULL DEFAULT 2.00,
    max_lot_size_per_trade NUMERIC(10, 2) NOT NULL DEFAULT 0.50,
    max_open_positions INT NOT NULL DEFAULT 8,
    max_spread_points NUMERIC(10, 2) NOT NULL DEFAULT 35.00,
    min_margin_level_percent NUMERIC(10, 2) NOT NULL DEFAULT 200.00,
    emergency_stop_policy VARCHAR(60) NOT NULL DEFAULT 'CLOSE_NEW_ENTRIES_ONLY',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

-- 3. EA CATALOG & LICENSING
CREATE TABLE ea_products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_code VARCHAR(40) UNIQUE NOT NULL,
    name VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    strategy_category VARCHAR(60) NOT NULL,
    current_version VARCHAR(20) NOT NULL,
    min_deposit_usd NUMERIC(14, 2) NOT NULL DEFAULT 1000.00,
    default_magic_number BIGINT NOT NULL,
    release_notes TEXT,
    risk_disclosures TEXT NOT NULL,
    monthly_price_usd NUMERIC(10, 2) NOT NULL,
    annual_price_usd NUMERIC(10, 2) NOT NULL,
    lifetime_price_usd NUMERIC(10, 2) NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE TABLE ea_licenses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    ea_product_id UUID NOT NULL REFERENCES ea_products(id),
    license_key_hash VARCHAR(255) NOT NULL,
    license_type VARCHAR(30) NOT NULL, -- 'MONTHLY', 'ANNUAL', 'LIFETIME'
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- PENDING, ACTIVE, EXPIRED, SUSPENDED, REVOKED
    max_accounts INT NOT NULL DEFAULT 3,
    max_installations INT NOT NULL DEFAULT 5,
    starts_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

CREATE INDEX idx_ea_licenses_tenant ON ea_licenses(tenant_id);

-- 4. EA INSTALLATIONS & RUNTIME
CREATE TABLE ea_installations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    broker_account_id UUID NOT NULL REFERENCES broker_accounts(id) ON DELETE CASCADE,
    ea_product_id UUID NOT NULL REFERENCES ea_products(id),
    magic_number BIGINT NOT NULL,
    symbol VARCHAR(30) NOT NULL DEFAULT 'XAUUSD',
    timeframe VARCHAR(10) NOT NULL DEFAULT 'M5',
    runtime_status VARCHAR(30) NOT NULL DEFAULT 'RUNNING', -- RUNNING, PAUSED, STOPPED, DISCONNECTED
    current_config_version INT NOT NULL DEFAULT 1,
    pending_config_version INT,
    config_status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    last_heartbeat TIMESTAMPTZ,
    auth_token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    CONSTRAINT uq_account_magic UNIQUE (broker_account_id, magic_number)
);

CREATE INDEX idx_ea_installations_tenant ON ea_installations(tenant_id);

-- 5. EA CONFIGURATION VERSIONS & AUDIT
CREATE TABLE ea_config_versions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    installation_id UUID NOT NULL REFERENCES ea_installations(id) ON DELETE CASCADE,
    version INT NOT NULL,
    config_values JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'APPLIED',
    submitted_by UUID REFERENCES users(id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    applied_at TIMESTAMPTZ,
    rejection_reason TEXT,
    CONSTRAINT uq_inst_version UNIQUE (installation_id, version)
);

-- 6. REMOTE COMMANDS QUEUE
CREATE TABLE commands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    installation_id UUID REFERENCES ea_installations(id) ON DELETE CASCADE,
    broker_account_id UUID REFERENCES broker_accounts(id) ON DELETE CASCADE,
    command_type VARCHAR(40) NOT NULL, -- PAUSE, RESUME, STOP, EMERGENCY_STOP
    command_state VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, DELIVERED, APPLIED, REJECTED, EXPIRED
    policy VARCHAR(60),
    payload JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
    acknowledged_at TIMESTAMPTZ
);

CREATE INDEX idx_commands_pending ON commands(installation_id, command_state) WHERE command_state = 'PENDING';

-- 7. AUDIT LOGS
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(60) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now())
);

-- ROW LEVEL SECURITY (RLS) FOR MULTI-TENANT ISOLATION
ALTER TABLE broker_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE ea_licenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE ea_installations ENABLE ROW LEVEL SECURITY;
ALTER TABLE commands ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_broker_accounts ON broker_accounts
    FOR ALL USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_ea_licenses ON ea_licenses
    FOR ALL USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_ea_installations ON ea_installations
    FOR ALL USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_commands ON commands
    FOR ALL USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
