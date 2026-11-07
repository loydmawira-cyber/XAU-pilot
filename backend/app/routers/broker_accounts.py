from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List
import uuid

from backend.app.database import get_db
from backend.app.models import BrokerAccount, AccountRiskProfile, User, AuditLog
from backend.app.schemas import (
    BrokerAccountCreate, BrokerAccountResponse, AccountRiskProfileSchema
)
from backend.app.dependencies import get_current_user

router = APIRouter(prefix="", tags=["Broker Accounts"])

@router.get("/broker-accounts", response_model=List[BrokerAccountResponse])
def list_broker_accounts(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    accounts = db.query(BrokerAccount).filter(
        BrokerAccount.tenant_id == current_user.tenant_id
    ).order_by(BrokerAccount.created_at.desc()).all()

    return [
        BrokerAccountResponse(
            id=acc.id,
            tenantId=acc.tenant_id,
            label=acc.label,
            brokerName=acc.broker_name,
            serverName=acc.server_name,
            accountNumberMasked=acc.account_number_masked,
            environment=acc.environment,
            connectionStatus=acc.connection_status,
            currency=acc.currency,
            balance=acc.balance,
            equity=acc.equity,
            freeMargin=acc.free_margin,
            usedMargin=acc.used_margin,
            marginLevelPercent=acc.margin_level_percent,
            leverage=acc.leverage,
            assignedEaCount=acc.assigned_ea_count,
            lastHeartbeatUtc=acc.last_heartbeat.strftime("%Y-%m-%d %H:%M:%S") if acc.last_heartbeat else None,
            hostingType=acc.hosting_type
        )
        for acc in accounts
    ]

@router.post("/broker-accounts", response_model=BrokerAccountResponse)
def add_broker_account(
    req: BrokerAccountCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # Mask account number: e.g. 1234****89
    acc_str = str(req.accountNumber).strip()
    masked = acc_str[:4] + "****" + acc_str[-2:] if len(acc_str) >= 6 else acc_str

    acc_id = str(uuid.uuid4())
    account = BrokerAccount(
        id=acc_id,
        tenant_id=current_user.tenant_id,
        label=req.label,
        broker_name=req.broker_name,
        server_name=req.server_name,
        account_number_masked=masked,
        environment=req.environment,
        connectionStatus="CONNECTED",
        currency="USD",
        balance=req.initialBalance,
        equity=req.initialBalance,
        free_margin=req.initialBalance,
        used_margin=0.0,
        margin_level_percent=0.0,
        leverage=500,
        assigned_ea_count=0,
        hosting_type="MANAGED_VPS"
    )
    db.add(account)

    # Initialize default risk profile
    risk = AccountRiskProfile(
        account_id=acc_id,
        max_daily_loss_amount=500.0,
        max_total_exposure_lots=2.0,
        max_lot_size_per_trade=0.50,
        max_open_positions=8,
        max_spread_points=35.0,
        min_margin_level_percent=200.0,
        emergency_stop_policy="CLOSE_NEW_ENTRIES_ONLY"
    )
    db.add(risk)

    audit = AuditLog(
        tenant_id=current_user.tenant_id,
        user_id=current_user.id,
        action="ACCOUNT_CONNECTED",
        target_type="BROKER_ACCOUNT",
        target_id=acc_id,
        details=f'{{"broker": "{req.broker_name}", "server": "{req.server_name}", "env": "{req.environment}"}}'
    )
    db.add(audit)
    db.commit()
    db.refresh(account)

    return BrokerAccountResponse(
        id=account.id,
        tenantId=account.tenant_id,
        label=account.label,
        brokerName=account.broker_name,
        serverName=account.server_name,
        accountNumberMasked=account.account_number_masked,
        environment=account.environment,
        connectionStatus=account.connection_status,
        currency=account.currency,
        balance=account.balance,
        equity=account.equity,
        freeMargin=account.free_margin,
        usedMargin=account.used_margin,
        marginLevelPercent=account.margin_level_percent,
        leverage=account.leverage,
        assignedEaCount=account.assigned_ea_count,
        lastHeartbeatUtc=None,
        hostingType=account.hosting_type
    )

@router.get("/broker-accounts/{account_id}", response_model=BrokerAccountResponse)
def get_broker_account(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    account = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()

    if not account:
        raise HTTPException(status_code=404, detail="Broker account not found")

    return BrokerAccountResponse(
        id=account.id,
        tenantId=account.tenant_id,
        label=account.label,
        brokerName=account.broker_name,
        serverName=account.server_name,
        accountNumberMasked=account.account_number_masked,
        environment=account.environment,
        connectionStatus=account.connection_status,
        currency=account.currency,
        balance=account.balance,
        equity=account.equity,
        freeMargin=account.free_margin,
        usedMargin=account.used_margin,
        marginLevelPercent=account.margin_level_percent,
        leverage=account.leverage,
        assignedEaCount=account.assigned_ea_count,
        lastHeartbeatUtc=account.last_heartbeat.strftime("%Y-%m-%d %H:%M:%S") if account.last_heartbeat else None,
        hostingType=account.hosting_type
    )

@router.delete("/broker-accounts/{account_id}")
def delete_broker_account(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    account = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()

    if not account:
        raise HTTPException(status_code=404, detail="Broker account not found")

    db.delete(account)
    db.commit()
    return {"status": "SUCCESS", "message": "Account removed"}

@router.get("/broker-accounts/{account_id}/risk-profile", response_model=AccountRiskProfileSchema)
def get_account_risk_profile(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    account = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not account:
        raise HTTPException(status_code=404, detail="Broker account not found")

    risk = db.query(AccountRiskProfile).filter(AccountRiskProfile.account_id == account_id).first()
    if not risk:
        risk = AccountRiskProfile(account_id=account_id)
        db.add(risk)
        db.commit()
        db.refresh(risk)

    return AccountRiskProfileSchema(
        accountId=risk.account_id,
        maxDailyLossAmount=risk.max_daily_loss_amount,
        maxTotalExposureLots=risk.max_total_exposure_lots,
        maxLotSizePerTrade=risk.max_lot_size_per_trade,
        maxOpenPositions=risk.max_open_positions,
        maxSpreadPoints=risk.max_spread_points,
        minMarginLevelPercent=risk.min_margin_level_percent,
        emergencyStopPolicy=risk.emergency_stop_policy
    )

@router.put("/broker-accounts/{account_id}/risk-profile", response_model=AccountRiskProfileSchema)
def update_account_risk_profile(
    account_id: str,
    req: AccountRiskProfileSchema,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    account = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not account:
        raise HTTPException(status_code=404, detail="Broker account not found")

    # Risk validation checks
    if req.maxLotSizePerTrade <= 0 or req.maxLotSizePerTrade > 50.0:
        raise HTTPException(status_code=400, detail="Invalid max lot size per trade")

    risk = db.query(AccountRiskProfile).filter(AccountRiskProfile.account_id == account_id).first()
    if not risk:
        risk = AccountRiskProfile(account_id=account_id)
        db.add(risk)

    risk.max_daily_loss_amount = req.maxDailyLossAmount
    risk.max_total_exposure_lots = req.maxTotalExposureLots
    risk.max_lot_size_per_trade = req.maxLotSizePerTrade
    risk.max_open_positions = req.maxOpenPositions
    risk.max_spread_points = req.maxSpreadPoints
    risk.min_margin_level_percent = req.minMarginLevelPercent
    risk.emergency_stop_policy = req.emergencyStopPolicy

    db.commit()
    db.refresh(risk)

    return AccountRiskProfileSchema(
        accountId=risk.account_id,
        maxDailyLossAmount=risk.max_daily_loss_amount,
        maxTotalExposureLots=risk.max_total_exposure_lots,
        maxLotSizePerTrade=risk.max_lot_size_per_trade,
        maxOpenPositions=risk.max_open_positions,
        maxSpreadPoints=risk.max_spread_points,
        minMarginLevelPercent=risk.min_margin_level_percent,
        emergencyStopPolicy=risk.emergency_stop_policy
    )
