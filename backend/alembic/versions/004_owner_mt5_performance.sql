-- XAU Pilot owner MT5 performance feed (PostgreSQL)
ALTER TABLE ea_installations ADD COLUMN IF NOT EXISTS is_publisher_account BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS ea_performance_trades (
 id VARCHAR(36) PRIMARY KEY, installation_id VARCHAR(36) NOT NULL REFERENCES ea_installations(id) ON DELETE CASCADE,
 deal_ticket BIGINT NOT NULL, position_id BIGINT NOT NULL DEFAULT 0, symbol VARCHAR(30) NOT NULL DEFAULT 'XAUUSD',
 direction VARCHAR(10) NOT NULL, volume DOUBLE PRECISION NOT NULL, close_price DOUBLE PRECISION NOT NULL,
 profit DOUBLE PRECISION NOT NULL DEFAULT 0, commission DOUBLE PRECISION NOT NULL DEFAULT 0, swap DOUBLE PRECISION NOT NULL DEFAULT 0,
 net_profit DOUBLE PRECISION NOT NULL DEFAULT 0, currency VARCHAR(10) NOT NULL DEFAULT 'USD',
 closed_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_ea_perf_deal_installation UNIQUE (installation_id, deal_ticket));
CREATE INDEX IF NOT EXISTS ix_ea_performance_trades_installation_id ON ea_performance_trades(installation_id);
CREATE INDEX IF NOT EXISTS ix_ea_performance_trades_closed_at ON ea_performance_trades(closed_at);
CREATE TABLE IF NOT EXISTS ea_performance_snapshots (
 id VARCHAR(36) PRIMARY KEY, installation_id VARCHAR(36) NOT NULL REFERENCES ea_installations(id) ON DELETE CASCADE,
 balance DOUBLE PRECISION NOT NULL, equity DOUBLE PRECISION NOT NULL, currency VARCHAR(10) NOT NULL DEFAULT 'USD',
 captured_at TIMESTAMP NOT NULL);
CREATE INDEX IF NOT EXISTS ix_ea_performance_snapshots_installation_id ON ea_performance_snapshots(installation_id);
CREATE INDEX IF NOT EXISTS ix_ea_perf_snapshot_install_time ON ea_performance_snapshots(installation_id, captured_at);
