import os
import secrets
from typing import List

class Settings:
    PROJECT_NAME: str = "XAU Pilot Core Engine"
    API_V1_STR: str = "/api/v1"
    EA_V1_STR: str = "/ea/v1"
    VERSION: str = "2.4.0"
    
    # Database
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "postgresql://xau_user@localhost:5432/xau_pilot_db"
    )
    
    # Security & JWT. Production must provide stable secrets; ephemeral keys are dev-only.
    ENVIRONMENT: str = os.getenv("ENVIRONMENT", "development").lower()
    _configured_jwt_secret = os.getenv("JWT_SECRET")
    if ENVIRONMENT == "production" and not _configured_jwt_secret:
        raise RuntimeError("JWT_SECRET must be configured in production")
    JWT_SECRET: str = _configured_jwt_secret or secrets.token_urlsafe(48)
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24 # 24 hours
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30

    # One-time first-admin secret; configure only in the backend host.
    SUPER_ADMIN_BOOTSTRAP_TOKEN: str = os.getenv("SUPER_ADMIN_BOOTSTRAP_TOKEN", "")
    
    # CORS (Strict origin list to prevent CSRF)
    ALLOWED_ORIGINS: List[str] = [
        origin.strip() for origin in os.getenv(
            "ALLOWED_ORIGINS",
            "https://app.xaupilot.com,https://portal.xaupilot.com,http://localhost:3000"
        ).split(",") if origin.strip()
    ]
    
    # Default Risk Boundaries
    DEFAULT_MAX_DAILY_LOSS: float = 500.0
    DEFAULT_MAX_TOTAL_EXPOSURE_LOTS: float = 2.0
    DEFAULT_MAX_LOT_PER_TRADE: float = 0.50
    DEFAULT_MAX_OPEN_POSITIONS: int = 8
    DEFAULT_MAX_SPREAD_POINTS: float = 35.0
    DEFAULT_MIN_MARGIN_LEVEL: float = 200.0

    # Hosting adapter. Dry-run is mandatory by default to prevent accidental cloud spend.
    HOSTING_PROVIDER: str = os.getenv("HOSTING_PROVIDER", "aws_ec2")
    HOSTING_DRY_RUN: bool = os.getenv("HOSTING_DRY_RUN", "true").lower() == "true"
    AWS_REGION: str = os.getenv("AWS_REGION", "us-east-1")
    AWS_AMI_ID: str = os.getenv("AWS_AMI_ID", "")
    AWS_INSTANCE_TYPE: str = os.getenv("AWS_INSTANCE_TYPE", "t3.medium")
    AWS_SUBNET_ID: str = os.getenv("AWS_SUBNET_ID", "")
    AWS_SECURITY_GROUP_ID: str = os.getenv("AWS_SECURITY_GROUP_ID", "")
    AWS_IAM_INSTANCE_PROFILE: str = os.getenv("AWS_IAM_INSTANCE_PROFILE", "")

    # EA package storage and manifest signing (server-side secrets; never ship in the APK)
    EA_STORAGE_DIR: str = os.getenv("EA_STORAGE_DIR", "./var/ea_packages")
    EA_MAX_BINARY_BYTES: int = int(os.getenv("EA_MAX_BINARY_BYTES", str(25 * 1024 * 1024)))
    EA_SCAN_COMMAND: str = os.getenv("EA_SCAN_COMMAND", "")
    MANIFEST_SIGNING_KEY: str = os.getenv("MANIFEST_SIGNING_KEY", "")

settings = Settings()
