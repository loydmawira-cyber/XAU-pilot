from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any

# --- Authentication & Users ---
class UserRegisterRequest(BaseModel):
    email: str
    password: str
    full_name: str

class UserLoginRequest(BaseModel):
    email: str
    password: str

class SuperAdminBootstrapRequest(BaseModel):
    email: str
    password: str = Field(min_length=14, max_length=128)
    full_name: str = Field(min_length=2, max_length=120)

class RestrictedAdminCreateRequest(BaseModel):
    email: str
    password: str = Field(min_length=14, max_length=128)
    full_name: str = Field(min_length=2, max_length=120)

class TokenRefreshRequest(BaseModel):
    refresh_token: str

class UserResponse(BaseModel):
    id: str
    email: str
    fullName: str
    role: str
    tenantId: str
    mfaEnabled: bool = False
    createdAt: str

class TokenResponse(BaseModel):
    accessToken: str
    refreshToken: str
    tokenType: str = "Bearer"
    expiresIn: int = 86400
    user: UserResponse

class TenantResponse(BaseModel):
    id: str
    name: str
    plan: str
    maxAccounts: int
    maxEaInstallations: int
    createdAt: str

# --- Broker Accounts ---
class BrokerAccountCreate(BaseModel):
    label: str
    brokerName: str
    serverName: str
    accountNumber: str
    environment: str = "DEMO"
    initialBalance: float = 10000.0

class BrokerAccountResponse(BaseModel):
    id: str
    tenantId: str
    label: str
    brokerName: str
    serverName: str
    accountNumberMasked: str
    environment: str
    connectionStatus: str
    currency: str = "USD"
    balance: float
    equity: float
    freeMargin: float
    usedMargin: float
    marginLevelPercent: float
    leverage: int = 500
    assignedEaCount: int = 0
    lastHeartbeatUtc: Optional[str] = None
    hostingType: str = "MANAGED_VPS"

class AccountRiskProfileSchema(BaseModel):
    accountId: str
    maxDailyLossAmount: float = 500.0
    maxTotalExposureLots: float = 2.0
    maxLotSizePerTrade: float = 0.50
    maxOpenPositions: int = 8
    maxSpreadPoints: float = 35.0
    minMarginLevelPercent: float = 200.0
    emergencyStopPolicy: str = "CLOSE_NEW_ENTRIES_ONLY"

# --- EA Marketplace & Licensing ---
class EaProductResponse(BaseModel):
    id: str
    productCode: str
    name: str
    description: str
    strategyCategory: str
    currentVersion: str
    supportedInstruments: List[str] = ["XAUUSD"]
    supportedTimeframes: List[str] = ["M5"]
    minDepositUsd: float
    defaultMagicNumber: int
    releaseNotes: Optional[str] = None
    riskDisclosures: str
    documentationUrl: str
    monthlyPriceUsd: float
    annualPriceUsd: float
    lifetimePriceUsd: float

class LicensePurchaseRequest(BaseModel):
    ea_product_id: str
    license_type: str # MONTHLY, ANNUAL, LIFETIME

class EaLicenseResponse(BaseModel):
    id: str
    eaProductId: str
    eaProductName: str
    tenantId: str
    licenseKeyMasked: str
    licenseType: str
    status: str
    startDate: str
    expiryDate: Optional[str] = None
    maxAccounts: int
    maxInstallations: int
    currentActiveInstallations: int = 0

# --- EA Installations & Remote Configuration ---
class EaInstallationCreate(BaseModel):
    eaProductId: str
    brokerAccountId: str
    magicNumber: int
    timeframe: str = "M5"

class EaInstallationResponse(BaseModel):
    id: str
    tenantId: str
    eaProductId: str
    eaProductName: str
    brokerAccountId: str
    brokerAccountLabel: str
    magicNumber: int
    symbol: str = "XAUUSD"
    timeframe: str = "M5"
    runtimeStatus: str
    currentConfigVersion: int
    pendingConfigVersion: Optional[int] = None
    configStatus: str
    currentValues: Dict[str, str] = {}
    lastHeartbeatUtc: Optional[str] = None
    hostingType: str
    vpsInstanceId: Optional[str] = None
    openPositionsCount: int = 0
    totalProfitLossUsd: float = 0.0

class ParameterDefinitionSchema(BaseModel):
    key: str
    displayName: str
    description: str
    dataType: str # DECIMAL, INTEGER, BOOLEAN, ENUM, STRING
    defaultValue: str
    minValue: Optional[float] = None
    maxValue: Optional[float] = None
    step: Optional[float] = None
    unit: str = ""
    category: str = "General"
    visibility: str = "STANDARD" # STANDARD, ADVANCED
    enumOptions: Optional[List[str]] = None
    requiresRestart: bool = False

class ConfigAuditEntrySchema(BaseModel):
    version: int
    parameterKey: str
    oldValue: str
    newValue: str
    submittedAtUtc: str
    appliedAtUtc: Optional[str] = None
    status: str
    rejectionReason: Optional[str] = None

class EaPresetSchema(BaseModel):
    id: str
    eaProductId: str
    name: str
    description: str
    values: Dict[str, str]
    isSystemDefault: bool = False

# --- Hosting & VPS ---
class VpsInstanceResponse(BaseModel):
    id: str
    name: str
    planName: str
    ipAddress: str
    os: str
    cpuCores: int
    ramGb: int
    ssdGb: int
    region: str
    status: str
    mt5TerminalStatus: str
    uptimeHours: int
    cpuUsagePercent: int
    ramUsagePercent: int
    lastHeartbeatUtc: str

# --- Commands & Orders ---
class RemoteCommandResponse(BaseModel):
    id: str
    accountId: Optional[str] = None
    installationId: Optional[str] = None
    type: str
    state: str
    emergencyPolicy: Optional[str] = None
    createdAtUtc: str
    acknowledgedAtUtc: Optional[str] = None

class PositionResponse(BaseModel):
    ticket: int
    brokerAccountId: str
    eaInstallationId: str
    eaName: str
    symbol: str
    direction: str
    lotSize: float
    openPrice: float
    currentPrice: float
    stopLoss: Optional[float] = None
    takeProfit: Optional[float] = None
    floatingProfitLoss: float
    swap: float = 0.0
    commission: float = 0.0
    magicNumber: int
    openTimeUtc: str

class ClosedTradeResponse(BaseModel):
    ticket: int
    brokerAccountId: str
    eaName: str
    symbol: str
    direction: str
    lotSize: float
    openPrice: float
    closePrice: float
    stopLoss: Optional[float] = None
    takeProfit: Optional[float] = None
    netProfitLoss: float
    commission: float = 0.0
    swap: float = 0.0
    openTimeUtc: str
    closeTimeUtc: str

# --- MQL5 Bridge Schemas ---
class EaHeartbeatRequest(BaseModel):
    installation_id: str
    status: str
    balance: float
    equity: float
    free_margin: float
    config_version: int

class LicenseValidateRequest(BaseModel):
    installation_id: str
    account: int

class LicenseValidateResponse(BaseModel):
    valid: bool
    license_status: str
    entitled_until: Optional[str] = None

class CommandAckRequest(BaseModel):
    status: str # APPLIED, REJECTED
    timestamp: int
    error_message: Optional[str] = None

class ConfigAckRequest(BaseModel):
    installation_id: str
    version: int
    status: str # APPLIED, REJECTED
    applied_values: Dict[str, str]
    rejection_reason: Optional[str] = None

class TradeEventRequest(BaseModel):
    installation_id: str
    magic_number: int
    ticket: int
    deal_type: str # DEAL_TYPE_BUY, DEAL_TYPE_SELL
    volume: float
    price: float
    profit: float
    comment: Optional[str] = None

class PositionsSnapshotRequest(BaseModel):
    installation_id: str
    positions: List[Dict[str, Any]]
