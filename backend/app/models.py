import uuid
from datetime import datetime
from sqlalchemy import (
    Column, String, Boolean, Integer, Float, Text,
    DateTime, ForeignKey, Numeric, BigInteger, Index, text
)
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship
from backend.app.database import Base

def gen_uuid():
    return str(uuid.uuid4())

def utc_now():
    return datetime.utcnow()

class Tenant(Base):
    __tablename__ = "tenants"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    name = Column(String(120), nullable=False)
    plan = Column(String(60), nullable=False, default="Enterprise Gold")
    max_accounts = Column(Integer, nullable=False, default=10)
    max_ea_installations = Column(Integer, nullable=False, default=25)
    created_at = Column(DateTime, nullable=False, default=utc_now)
    updated_at = Column(DateTime, nullable=False, default=utc_now, onupdate=utc_now)

    users = relationship("User", back_populates="tenant", cascade="all, delete-orphan")
    broker_accounts = relationship("BrokerAccount", back_populates="tenant", cascade="all, delete-orphan")
    licenses = relationship("EaLicense", back_populates="tenant", cascade="all, delete-orphan")
    installations = relationship("EaInstallation", back_populates="tenant", cascade="all, delete-orphan")
    commands = relationship("Command", back_populates="tenant", cascade="all, delete-orphan")
    audit_logs = relationship("AuditLog", back_populates="tenant", cascade="all, delete-orphan")

class User(Base):
    __tablename__ = "users"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    email = Column(String(255), unique=True, nullable=False, index=True)
    password_hash = Column(String(255), nullable=False)
    full_name = Column(String(120), nullable=False)
    role = Column(String(40), nullable=False, default="CUSTOMER") # CUSTOMER, SUPPORT_AGENT, ADMINISTRATOR, SUPER_ADMINISTRATOR
    mfa_enabled = Column(Boolean, nullable=False, default=False)
    mfa_secret = Column(String(120), nullable=True)
    is_active = Column(Boolean, nullable=False, default=True)
    created_at = Column(DateTime, nullable=False, default=utc_now)

    tenant = relationship("Tenant", back_populates="users")

# Enforce exactly one platform Super Admin, including concurrent bootstrap attempts.
Index(
    "uq_users_single_super_administrator", User.role, unique=True,
    postgresql_where=text("role = 'SUPER_ADMINISTRATOR'"),
    sqlite_where=text("role = 'SUPER_ADMINISTRATOR'"),
)

class RevokedToken(Base):
    __tablename__ = "revoked_tokens"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    token = Column(String(512), unique=True, nullable=False, index=True)
    token_type = Column(String(20), nullable=False, default="access") # access or refresh
    revoked_at = Column(DateTime, nullable=False, default=utc_now)
    expires_at = Column(DateTime, nullable=False)

class BrokerAccount(Base):
    __tablename__ = "broker_accounts"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    label = Column(String(100), nullable=False)
    broker_name = Column(String(100), nullable=False)
    server_name = Column(String(100), nullable=False)
    account_number_masked = Column(String(50), nullable=False)
    environment = Column(String(20), nullable=False, default="DEMO") # DEMO, LIVE
    connection_status = Column(String(30), nullable=False, default="CONNECTED") # CONNECTED, DISCONNECTED, ERROR
    currency = Column(String(10), nullable=False, default="USD")
    balance = Column(Float, nullable=False, default=0.0)
    equity = Column(Float, nullable=False, default=0.0)
    free_margin = Column(Float, nullable=False, default=0.0)
    used_margin = Column(Float, nullable=False, default=0.0)
    margin_level_percent = Column(Float, nullable=False, default=0.0)
    leverage = Column(Integer, nullable=False, default=500)
    assigned_ea_count = Column(Integer, nullable=False, default=0)
    last_heartbeat = Column(DateTime, nullable=True)
    hosting_type = Column(String(30), nullable=False, default="MANAGED_VPS")
    created_at = Column(DateTime, nullable=False, default=utc_now)

    tenant = relationship("Tenant", back_populates="broker_accounts")
    risk_profile = relationship("AccountRiskProfile", uselist=False, back_populates="account", cascade="all, delete-orphan")
    installations = relationship("EaInstallation", back_populates="broker_account", cascade="all, delete-orphan")
    positions = relationship("Position", back_populates="broker_account", cascade="all, delete-orphan")
    closed_trades = relationship("ClosedTrade", back_populates="broker_account", cascade="all, delete-orphan")

class AccountRiskProfile(Base):
    __tablename__ = "account_risk_profiles"

    account_id = Column(String(36), ForeignKey("broker_accounts.id", ondelete="CASCADE"), primary_key=True)
    max_daily_loss_amount = Column(Float, nullable=False, default=500.0)
    max_total_exposure_lots = Column(Float, nullable=False, default=2.0)
    max_lot_size_per_trade = Column(Float, nullable=False, default=0.50)
    max_open_positions = Column(Integer, nullable=False, default=8)
    max_spread_points = Column(Float, nullable=False, default=35.0)
    min_margin_level_percent = Column(Float, nullable=False, default=200.0)
    emergency_stop_policy = Column(String(60), nullable=False, default="CLOSE_NEW_ENTRIES_ONLY")
    updated_at = Column(DateTime, nullable=False, default=utc_now, onupdate=utc_now)

    account = relationship("BrokerAccount", back_populates="risk_profile")

class EaProduct(Base):
    __tablename__ = "ea_products"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    product_code = Column(String(40), unique=True, nullable=False, index=True)
    name = Column(String(120), nullable=False)
    description = Column(Text, nullable=False)
    strategy_category = Column(String(60), nullable=False)
    current_version = Column(String(20), nullable=False)
    min_deposit_usd = Column(Float, nullable=False, default=1000.0)
    default_magic_number = Column(BigInteger, nullable=False)
    release_notes = Column(Text, nullable=True)
    risk_disclosures = Column(Text, nullable=False)
    monthly_price_usd = Column(Float, nullable=False)
    annual_price_usd = Column(Float, nullable=False)
    lifetime_price_usd = Column(Float, nullable=False)
    is_published = Column(Boolean, nullable=False, default=True)
    # Developer ownership (NULL = platform-owned catalogue product) and marketplace review state
    developer_id = Column(String(36), ForeignKey("users.id"), nullable=True, index=True)
    listing_status = Column(String(30), nullable=False, default="APPROVED")  # DRAFT, PENDING_REVIEW, APPROVED, REJECTED, SUSPENDED, RETIRED
    created_at = Column(DateTime, nullable=False, default=utc_now)

    licenses = relationship("EaLicense", back_populates="ea_product")
    installations = relationship("EaInstallation", back_populates="ea_product")
    presets = relationship("EaPreset", back_populates="ea_product", cascade="all, delete-orphan")

class EaLicense(Base):
    __tablename__ = "ea_licenses"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    ea_product_id = Column(String(36), ForeignKey("ea_products.id"), nullable=False)
    license_key_hash = Column(String(255), nullable=False)
    license_key_masked = Column(String(60), nullable=False)
    license_type = Column(String(30), nullable=False) # MONTHLY, ANNUAL, LIFETIME
    status = Column(String(30), nullable=False, default="ACTIVE") # PENDING, ACTIVE, EXPIRED, SUSPENDED, REVOKED
    max_accounts = Column(Integer, nullable=False, default=3)
    max_installations = Column(Integer, nullable=False, default=5)
    starts_at = Column(DateTime, nullable=False, default=utc_now)
    expires_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, nullable=False, default=utc_now)

    tenant = relationship("Tenant", back_populates="licenses")
    ea_product = relationship("EaProduct", back_populates="licenses")

class EaInstallation(Base):
    __tablename__ = "ea_installations"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    broker_account_id = Column(String(36), ForeignKey("broker_accounts.id", ondelete="CASCADE"), nullable=False, index=True)
    ea_product_id = Column(String(36), ForeignKey("ea_products.id"), nullable=False)
    magic_number = Column(BigInteger, nullable=False)
    symbol = Column(String(30), nullable=False, default="XAUUSD")
    timeframe = Column(String(10), nullable=False, default="M5")
    runtime_status = Column(String(30), nullable=False, default="RUNNING") # RUNNING, PAUSED, STOPPED
    current_config_version = Column(Integer, nullable=False, default=1)
    pending_config_version = Column(Integer, nullable=True)
    config_status = Column(String(30), nullable=False, default="APPLIED") # APPLIED, PENDING, REJECTED
    current_values = Column(Text, nullable=False, default="{}") # Stored as JSON string for SQLite/PG compatibility
    pending_values = Column(Text, nullable=True) # Desired configuration awaiting EA ack
    auth_token_hash = Column(String(255), nullable=False)
    hosting_type = Column(String(30), nullable=False, default="MANAGED_VPS")
    vps_instance_id = Column(String(36), nullable=True)
    open_positions_count = Column(Integer, nullable=False, default=0)
    total_profit_loss_usd = Column(Float, nullable=False, default=0.0)
    last_heartbeat = Column(DateTime, nullable=True)
    created_at = Column(DateTime, nullable=False, default=utc_now)

    tenant = relationship("Tenant", back_populates="installations")
    broker_account = relationship("BrokerAccount", back_populates="installations")
    ea_product = relationship("EaProduct", back_populates="installations")
    config_versions = relationship("EaConfigVersion", back_populates="installation", cascade="all, delete-orphan")
    commands = relationship("Command", back_populates="installation", cascade="all, delete-orphan")

class EaConfigVersion(Base):
    __tablename__ = "ea_config_versions"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    installation_id = Column(String(36), ForeignKey("ea_installations.id", ondelete="CASCADE"), nullable=False, index=True)
    version = Column(Integer, nullable=False)
    parameter_key = Column(String(100), nullable=False, default="all")
    old_value = Column(String(255), nullable=True)
    new_value = Column(String(255), nullable=True)
    config_values = Column(Text, nullable=False, default="{}")
    status = Column(String(30), nullable=False, default="APPLIED") # PENDING, APPLIED, REJECTED
    submitted_by = Column(String(36), ForeignKey("users.id"), nullable=True)
    submitted_at = Column(DateTime, nullable=False, default=utc_now)
    applied_at = Column(DateTime, nullable=True)
    rejection_reason = Column(Text, nullable=True)

    installation = relationship("EaInstallation", back_populates="config_versions")

class EaPreset(Base):
    __tablename__ = "ea_presets"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    ea_product_id = Column(String(36), ForeignKey("ea_products.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    description = Column(Text, nullable=False)
    values = Column(Text, nullable=False, default="{}")
    is_system_default = Column(Boolean, nullable=False, default=False)
    created_at = Column(DateTime, nullable=False, default=utc_now)

    ea_product = relationship("EaProduct", back_populates="presets")

class Command(Base):
    __tablename__ = "commands"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    installation_id = Column(String(36), ForeignKey("ea_installations.id", ondelete="CASCADE"), nullable=True, index=True)
    broker_account_id = Column(String(36), ForeignKey("broker_accounts.id", ondelete="CASCADE"), nullable=True, index=True)
    command_type = Column(String(40), nullable=False) # PAUSE, RESUME, STOP, EMERGENCY_STOP
    command_state = Column(String(30), nullable=False, default="PENDING") # PENDING, DELIVERED, APPLIED, REJECTED, EXPIRED
    policy = Column(String(60), nullable=True)
    payload = Column(Text, nullable=False, default="{}")
    created_at = Column(DateTime, nullable=False, default=utc_now)
    acknowledged_at = Column(DateTime, nullable=True)

    tenant = relationship("Tenant", back_populates="commands")
    installation = relationship("EaInstallation", back_populates="commands")

class HostingJob(Base):
    __tablename__ = "hosting_jobs"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    installation_id = Column(String(36), ForeignKey("ea_installations.id", ondelete="CASCADE"), nullable=False, index=True)
    provider = Column(String(40), nullable=False)
    action = Column(String(40), nullable=False, default="PROVISION_HOST")
    status = Column(String(30), nullable=False, default="QUEUED")
    region = Column(String(60), nullable=False)
    provider_instance_id = Column(String(120), nullable=True)
    request_payload = Column(Text, nullable=False, default="{}")
    result_payload = Column(Text, nullable=True)
    error_message = Column(Text, nullable=True)
    created_at = Column(DateTime, nullable=False, default=utc_now)
    started_at = Column(DateTime, nullable=True)
    completed_at = Column(DateTime, nullable=True)

class Position(Base):
    __tablename__ = "positions"

    ticket = Column(BigInteger, primary_key=True)
    broker_account_id = Column(String(36), ForeignKey("broker_accounts.id", ondelete="CASCADE"), nullable=False, index=True)
    ea_installation_id = Column(String(36), nullable=False)
    ea_name = Column(String(100), nullable=False)
    symbol = Column(String(30), nullable=False, default="XAUUSD")
    direction = Column(String(10), nullable=False) # BUY, SELL
    lot_size = Column(Float, nullable=False)
    open_price = Column(Float, nullable=False)
    current_price = Column(Float, nullable=False)
    stop_loss = Column(Float, nullable=True)
    take_profit = Column(Float, nullable=True)
    floating_profit_loss = Column(Float, nullable=False, default=0.0)
    swap = Column(Float, nullable=False, default=0.0)
    commission = Column(Float, nullable=False, default=0.0)
    magic_number = Column(BigInteger, nullable=False)
    open_time = Column(DateTime, nullable=False, default=utc_now)

    broker_account = relationship("BrokerAccount", back_populates="positions")

class ClosedTrade(Base):
    __tablename__ = "closed_trades"

    ticket = Column(BigInteger, primary_key=True)
    broker_account_id = Column(String(36), ForeignKey("broker_accounts.id", ondelete="CASCADE"), nullable=False, index=True)
    ea_name = Column(String(100), nullable=False)
    symbol = Column(String(30), nullable=False, default="XAUUSD")
    direction = Column(String(10), nullable=False)
    lot_size = Column(Float, nullable=False)
    open_price = Column(Float, nullable=False)
    close_price = Column(Float, nullable=False)
    stop_loss = Column(Float, nullable=True)
    take_profit = Column(Float, nullable=True)
    net_profit_loss = Column(Float, nullable=False)
    commission = Column(Float, nullable=False, default=0.0)
    swap = Column(Float, nullable=False, default=0.0)
    open_time = Column(DateTime, nullable=False)
    close_time = Column(DateTime, nullable=False, default=utc_now)

    broker_account = relationship("BrokerAccount", back_populates="closed_trades")

class VpsInstance(Base):
    __tablename__ = "vps_instances"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    plan_name = Column(String(100), nullable=False)
    ip_address = Column(String(50), nullable=False)
    os = Column(String(60), nullable=False, default="Windows Server 2022 Datacenter")
    cpu_cores = Column(Integer, nullable=False, default=4)
    ram_gb = Column(Integer, nullable=False, default=8)
    ssd_gb = Column(Integer, nullable=False, default=160)
    region = Column(String(100), nullable=False)
    status = Column(String(30), nullable=False, default="RUNNING") # RUNNING, REBOOTING, STOPPED, MAINTENANCE
    mt5_terminal_status = Column(String(100), nullable=False, default="MetaTrader 5 Build 4480 Running")
    uptime_hours = Column(Integer, nullable=False, default=1)
    cpu_usage_percent = Column(Integer, nullable=False, default=10)
    ram_usage_percent = Column(Integer, nullable=False, default=30)
    last_heartbeat = Column(DateTime, nullable=False, default=utc_now)

class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    tenant_id = Column(String(36), ForeignKey("tenants.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    action = Column(String(100), nullable=False)
    target_type = Column(String(60), nullable=False)
    target_id = Column(String(100), nullable=False)
    details = Column(Text, nullable=False, default="{}")
    ip_address = Column(String(45), nullable=True)
    created_at = Column(DateTime, nullable=False, default=utc_now)

    tenant = relationship("Tenant", back_populates="audit_logs")


# ---------------------------------------------------------------------------
# Developer-controlled EA releases (manifest + binary), immutable once published
# ---------------------------------------------------------------------------
class DeveloperProfile(Base):
    __tablename__ = "developer_profiles"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    user_id = Column(String(36), ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False)
    display_name = Column(String(120), nullable=False)
    contact_email = Column(String(255), nullable=False)
    status = Column(String(20), nullable=False, default="ACTIVE")  # ACTIVE, SUSPENDED
    created_at = Column(DateTime, nullable=False, default=utc_now)

class EaProductVersion(Base):
    __tablename__ = "ea_product_versions"
    __table_args__ = (Index("uq_ea_version_per_product", "ea_product_id", "version", unique=True),)

    id = Column(String(36), primary_key=True, default=gen_uuid)
    ea_product_id = Column(String(36), ForeignKey("ea_products.id", ondelete="CASCADE"), nullable=False, index=True)
    version = Column(String(20), nullable=False)
    status = Column(String(20), nullable=False, default="DRAFT")  # DRAFT, PUBLISHED, RETIRED
    release_notes = Column(Text, nullable=True)
    manifest_hash = Column(String(64), nullable=True)
    binary_sha256 = Column(String(64), nullable=True)
    signature = Column(String(128), nullable=True)
    published_by = Column(String(36), ForeignKey("users.id"), nullable=True)
    published_at = Column(DateTime, nullable=True)
    created_by = Column(String(36), ForeignKey("users.id"), nullable=False)
    created_at = Column(DateTime, nullable=False, default=utc_now)

class EaBinaryPackage(Base):
    __tablename__ = "ea_binary_packages"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    version_id = Column(String(36), ForeignKey("ea_product_versions.id", ondelete="CASCADE"), unique=True, nullable=False)
    storage_key = Column(String(300), nullable=False)
    original_filename = Column(String(200), nullable=False)
    sha256 = Column(String(64), nullable=False)
    size_bytes = Column(BigInteger, nullable=False)
    scan_status = Column(String(20), nullable=False, default="NOT_CONFIGURED")  # CLEAN, INFECTED, ERROR, NOT_CONFIGURED
    uploaded_by = Column(String(36), ForeignKey("users.id"), nullable=False)
    uploaded_at = Column(DateTime, nullable=False, default=utc_now)

class EaInputManifest(Base):
    __tablename__ = "ea_input_manifests"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    version_id = Column(String(36), ForeignKey("ea_product_versions.id", ondelete="CASCADE"), unique=True, nullable=False)
    schema_version = Column(String(10), nullable=False)
    canonical_json = Column(Text, nullable=False)  # exact text that was hashed
    manifest_hash = Column(String(64), nullable=False)
    validation_errors = Column(Text, nullable=False, default="[]")
    is_valid = Column(Boolean, nullable=False, default=False)
    uploaded_by = Column(String(36), ForeignKey("users.id"), nullable=False)
    uploaded_at = Column(DateTime, nullable=False, default=utc_now)

class EaDeveloperAuditLog(Base):
    __tablename__ = "ea_developer_audit_logs"
    __table_args__ = (Index("ix_ea_dev_audit_product", "ea_product_id", "created_at"),)

    id = Column(String(36), primary_key=True, default=gen_uuid)
    ea_product_id = Column(String(36), ForeignKey("ea_products.id", ondelete="CASCADE"), nullable=True)
    version_id = Column(String(36), nullable=True)
    actor_user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    actor_role = Column(String(40), nullable=False)
    action = Column(String(60), nullable=False)
    details = Column(Text, nullable=False, default="{}")
    created_at = Column(DateTime, nullable=False, default=utc_now)

class EaPackageDownloadLog(Base):
    __tablename__ = "ea_package_download_logs"

    id = Column(String(36), primary_key=True, default=gen_uuid)
    version_id = Column(String(36), ForeignKey("ea_product_versions.id"), nullable=False, index=True)
    user_id = Column(String(36), ForeignKey("users.id"), nullable=True)
    tenant_id = Column(String(36), nullable=True)
    allowed = Column(Boolean, nullable=False)
    reason = Column(String(120), nullable=True)
    created_at = Column(DateTime, nullable=False, default=utc_now)
