from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List
from datetime import datetime, timedelta
import uuid
import hashlib

from backend.app.database import get_db
from backend.app.models import EaProduct, EaLicense, User, AuditLog
from backend.app.schemas import (
    EaProductResponse, EaLicenseResponse, LicensePurchaseRequest
)
from backend.app.dependencies import get_current_user

router = APIRouter(prefix="", tags=["EA Marketplace & Licensing"])

@router.get("/ea-products", response_model=List[EaProductResponse])
def list_ea_products(db: Session = Depends(get_db)):
    products = db.query(EaProduct).filter(EaProduct.is_published == True).all()
    # Seed default products if DB empty
    if not products:
        products = seed_default_products(db)

    return [
        EaProductResponse(
            id=p.id,
            productCode=p.product_code,
            name=p.name,
            description=p.description,
            strategyCategory=p.strategy_category,
            currentVersion=p.current_version,
            supportedInstruments=["XAUUSD", "GOLD"],
            supportedTimeframes=["M1", "M5", "M15", "H1"],
            minDepositUsd=p.min_deposit_usd,
            defaultMagicNumber=p.default_magic_number,
            releaseNotes=p.release_notes,
            riskDisclosures=p.risk_disclosures,
            documentationUrl=f"https://docs.xaupilot.com/ea/{p.product_code.lower()}",
            monthlyPriceUsd=p.monthly_price_usd,
            annualPriceUsd=p.annual_price_usd,
            lifetimePriceUsd=p.lifetime_price_usd
        )
        for p in products
    ]

@router.get("/ea-products/{product_id}", response_model=EaProductResponse)
def get_ea_product(product_id: str, db: Session = Depends(get_db)):
    p = db.query(EaProduct).filter(EaProduct.id == product_id).first()
    if not p:
        raise HTTPException(status_code=404, detail="EA product not found")
    return EaProductResponse(
        id=p.id,
        productCode=p.product_code,
        name=p.name,
        description=p.description,
        strategyCategory=p.strategy_category,
        currentVersion=p.current_version,
        supportedInstruments=["XAUUSD", "GOLD"],
        supportedTimeframes=["M1", "M5", "M15", "H1"],
        minDepositUsd=p.min_deposit_usd,
        defaultMagicNumber=p.default_magic_number,
        releaseNotes=p.release_notes,
        riskDisclosures=p.risk_disclosures,
        documentationUrl=f"https://docs.xaupilot.com/ea/{p.product_code.lower()}",
        monthlyPriceUsd=p.monthly_price_usd,
        annualPriceUsd=p.annual_price_usd,
        lifetimePriceUsd=p.lifetime_price_usd
    )

@router.get("/ea-licenses", response_model=List[EaLicenseResponse])
def list_licenses(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    licenses = db.query(EaLicense).filter(
        EaLicense.tenant_id == current_user.tenant_id
    ).all()

    return [
        EaLicenseResponse(
            id=lic.id,
            eaProductId=lic.ea_product_id,
            eaProductName=lic.ea_product.name if lic.ea_product else "XAU Expert",
            tenantId=lic.tenant_id,
            licenseKeyMasked=lic.license_key_masked,
            licenseType=lic.license_type,
            status=lic.status,
            startDate=lic.starts_at.strftime("%Y-%m-%d %H:%M:%S"),
            expiryDate=lic.expires_at.strftime("%Y-%m-%d %H:%M:%S") if lic.expires_at else None,
            maxAccounts=lic.max_accounts,
            maxInstallations=lic.max_installations,
            currentActiveInstallations=0
        )
        for lic in licenses
    ]

@router.post("/ea-licenses/purchase", response_model=EaLicenseResponse)
def purchase_license(
    req: LicensePurchaseRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    ea = db.query(EaProduct).filter(EaProduct.id == req.ea_product_id).first()
    if not ea:
        raise HTTPException(status_code=404, detail="EA product not found")

    lic_id = str(uuid.uuid4())
    raw_key = f"XAU-{req.license_type[:3]}-{uuid.uuid4().hex[:8].upper()}-{uuid.uuid4().hex[:4].upper()}"
    key_hash = hashlib.sha256(raw_key.encode("utf-8")).hexdigest()
    masked = raw_key[:7] + "****" + raw_key[-4:]

    expires_at = None
    if req.license_type == "MONTHLY":
        expires_at = datetime.utcnow() + timedelta(days=30)
    elif req.license_type == "ANNUAL":
        expires_at = datetime.utcnow() + timedelta(days=365)

    lic = EaLicense(
        id=lic_id,
        tenant_id=current_user.tenant_id,
        ea_product_id=ea.id,
        license_key_hash=key_hash,
        license_key_masked=masked,
        license_type=req.license_type,
        status="ACTIVE",
        starts_at=datetime.utcnow(),
        expires_at=expires_at,
        max_accounts=10 if req.license_type == "LIFETIME" else 3,
        max_installations=25 if req.license_type == "LIFETIME" else 5
    )
    db.add(lic)

    audit = AuditLog(
        tenant_id=current_user.tenant_id,
        user_id=current_user.id,
        action="LICENSE_PURCHASED",
        target_type="EA_LICENSE",
        target_id=lic_id,
        details=f'{{"ea": "{ea.name}", "type": "{req.license_type}"}}'
    )
    db.add(audit)
    db.commit()
    db.refresh(lic)

    return EaLicenseResponse(
        id=lic.id,
        eaProductId=lic.ea_product_id,
        eaProductName=ea.name,
        tenantId=lic.tenant_id,
        licenseKeyMasked=lic.license_key_masked,
        licenseType=lic.license_type,
        status=lic.status,
        startDate=lic.starts_at.strftime("%Y-%m-%d %H:%M:%S"),
        expiryDate=lic.expires_at.strftime("%Y-%m-%d %H:%M:%S") if lic.expires_at else None,
        maxAccounts=lic.max_accounts,
        maxInstallations=lic.max_installations,
        currentActiveInstallations=0
    )

@router.post("/ea-licenses/{license_id}/activate", response_model=EaLicenseResponse)
def activate_license(
    license_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    lic = db.query(EaLicense).filter(
        EaLicense.id == license_id,
        EaLicense.tenant_id == current_user.tenant_id
    ).first()
    if not lic:
        raise HTTPException(status_code=404, detail="License not found")

    lic.status = "ACTIVE"
    db.commit()
    db.refresh(lic)

    return EaLicenseResponse(
        id=lic.id,
        eaProductId=lic.ea_product_id,
        eaProductName=lic.ea_product.name if lic.ea_product else "XAU Expert",
        tenantId=lic.tenant_id,
        licenseKeyMasked=lic.license_key_masked,
        licenseType=lic.license_type,
        status=lic.status,
        startDate=lic.starts_at.strftime("%Y-%m-%d %H:%M:%S"),
        expiryDate=lic.expires_at.strftime("%Y-%m-%d %H:%M:%S") if lic.expires_at else None,
        maxAccounts=lic.max_accounts,
        maxInstallations=lic.max_installations,
        currentActiveInstallations=0
    )

def seed_default_products(db: Session) -> List[EaProduct]:
    p1 = EaProduct(
        id="ea_gold_pro",
        product_code="XAU-PRO-01",
        name="Gold Pro EA",
        description="Institutional multi-session liquidity sweep & momentum trading engine for XAUUSD with dynamic volatility trailing stops.",
        strategy_category="GOLD_TREND",
        current_version="2.4.0",
        min_deposit_usd=2000.0,
        default_magic_number=108821,
        release_notes="v2.4.0: Added remote runtime parameter synchronization, dynamic spread filter & NY open volatility throttle.",
        risk_disclosures="Trading gold carries high risk of loss. Past backtests do not guarantee future performance.",
        monthly_price_usd=79.0,
        annual_price_usd=690.0,
        lifetime_price_usd=1290.0,
        is_published=True
    )
    p2 = EaProduct(
        id="ea_gold_scalper",
        product_code="XAU-SCL-02",
        name="Gold Scalper EA",
        description="High-frequency micro-trend exploitation strategy capitalizing on London & New York session liquidity bursts.",
        strategy_category="GOLD_SCALPING",
        current_version="3.1.2",
        min_deposit_usd=1000.0,
        default_magic_number=209934,
        release_notes="v3.1.2: Improved slippage compensation and order execution latency safeguards.",
        risk_disclosures="Requires ultra-low latency VPS execution (sub 2ms) to prevent adverse slippage.",
        monthly_price_usd=89.0,
        annual_price_usd=790.0,
        lifetime_price_usd=1450.0,
        is_published=True
    )
    p3 = EaProduct(
        id="ea_gold_swing",
        product_code="XAU-SWG-03",
        name="Gold Swing EA",
        description="Macro structural swing trader tracking major daily support/resistance zones and gold central bank demand cycles.",
        strategy_category="GOLD_SWING",
        current_version="1.8.0",
        min_deposit_usd=5000.0,
        default_magic_number=301145,
        release_notes="v1.8.0: Integrated macro interest rate trend bias filter.",
        risk_disclosures="Holding positions overnight is subject to broker swap charges.",
        monthly_price_usd=69.0,
        annual_price_usd=590.0,
        lifetime_price_usd=990.0,
        is_published=True
    )
    p4 = EaProduct(
        id="ea_gold_breakout",
        product_code="XAU-BRK-04",
        name="Gold Breakout EA",
        description="London range compression breakout algorithm with smart false-breakout detection and order-cancellation timers.",
        strategy_category="GOLD_BREAKOUT",
        current_version="2.0.1",
        min_deposit_usd=2500.0,
        default_magic_number=405512,
        release_notes="v2.0.1: Added Asian high/low range projection box filter.",
        risk_disclosures="False breakouts during major economic news releases may trigger stop losses.",
        monthly_price_usd=75.0,
        annual_price_usd=650.0,
        lifetime_price_usd=1190.0,
        is_published=True
    )
    db.add_all([p1, p2, p3, p4])
    db.commit()
    return [p1, p2, p3, p4]
