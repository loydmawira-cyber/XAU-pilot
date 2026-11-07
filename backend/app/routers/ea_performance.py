"""Publisher MT5 performance feed. Public output contains aggregate metrics only."""
import secrets
from datetime import datetime, timedelta
from typing import Optional

from fastapi import APIRouter, Depends, Header, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.dependencies import get_current_user
from backend.app.models import (
    User, DeveloperProfile, EaProduct, BrokerAccount, EaInstallation,
    EaPerformanceTrade, EaPerformanceSnapshot,
)
from backend.app.security import hash_ea_token

router = APIRouter(tags=["EA Performance"] )

class PublisherAccountConnectRequest(BaseModel):
    label: str = Field(default="Publisher performance account", min_length=2, max_length=100)
    broker_name: str = Field(min_length=2, max_length=100)
    server_name: str = Field(min_length=2, max_length=100)
    account_number: str = Field(min_length=3, max_length=64)
    environment: str = Field(default="DEMO", pattern="^DEMO$")
    magic_number: int = Field(gt=0, le=9223372036854775807)
    symbol: str = Field(default="XAUUSD", min_length=1, max_length=30)
    timeframe: str = Field(default="M5", min_length=1, max_length=10)

class PerformanceTradeIn(BaseModel):
    installation_id: str
    deal_ticket: int = Field(gt=0)
    position_id: int = Field(default=0, ge=0)
    symbol: str = Field(default="XAUUSD", max_length=30)
    direction: str = Field(pattern="^(BUY|SELL)$")
    volume: float = Field(gt=0, le=1000)
    close_price: float = Field(gt=0)
    profit: float
    commission: float = 0.0
    swap: float = 0.0
    currency: str = Field(default="USD", min_length=3, max_length=10)
    closed_at_epoch: int = Field(gt=0)

class PerformanceSnapshotIn(BaseModel):
    installation_id: str
    balance: float = Field(ge=0)
    equity: float = Field(ge=0)
    currency: str = Field(default="USD", min_length=3, max_length=10)
    captured_at_epoch: int = Field(gt=0)

def _authenticate_installation(installation_id: str, authorization: Optional[str], db: Session) -> EaInstallation:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Missing EA bearer token")
    token = authorization.split(" ", 1)[1].strip()
    inst = db.query(EaInstallation).filter(EaInstallation.id == installation_id).first()
    if not inst or not secrets.compare_digest(inst.auth_token_hash, hash_ea_token(token)):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "EA authentication failed")
    if not inst.is_publisher_account:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "This installation is not a publisher performance feed")
    return inst

@router.post("/api/v1/developer/ea-products/{product_id}/performance-account", status_code=201)
def connect_publisher_account(product_id: str, req: PublisherAccountConnectRequest,
                              user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    profile = db.query(DeveloperProfile).filter(DeveloperProfile.user_id == user.id, DeveloperProfile.status == "ACTIVE").first()
    if not profile:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Register as an active EA developer first")
    product = db.query(EaProduct).filter(EaProduct.id == product_id, EaProduct.developer_id == user.id).first()
    if not product:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "EA product not found")
    existing = db.query(EaInstallation).filter(EaInstallation.ea_product_id == product.id, EaInstallation.is_publisher_account == True).first()
    if existing:
        raise HTTPException(status.HTTP_409_CONFLICT, "A publisher performance account is already connected to this EA. Contact support to rotate or replace it.")
    account_number = req.account_number.strip()
    masked = ("*" * max(0, len(account_number)-4)) + account_number[-4:]
    account = BrokerAccount(tenant_id=user.tenant_id, label=req.label.strip(), broker_name=req.broker_name.strip(),
        server_name=req.server_name.strip(), account_number_masked=masked, environment=req.environment,
        connection_status="PENDING", currency="USD", hosting_type="CUSTOMER_HOSTED")
    db.add(account); db.flush()
    token = secrets.token_urlsafe(40)
    installation = EaInstallation(tenant_id=user.tenant_id, broker_account_id=account.id, ea_product_id=product.id,
        magic_number=req.magic_number, symbol=req.symbol, timeframe=req.timeframe, runtime_status="STOPPED",
        auth_token_hash=hash_ea_token(token), hosting_type="CUSTOMER_HOSTED", is_publisher_account=True)
    db.add(installation); db.flush()
    account.assigned_ea_count = 1
    db.commit()
    return {"installation_id": installation.id, "ea_name": product.name, "broker": req.broker_name,
        "server": req.server_name, "account_number_masked": masked, "environment": req.environment,
        "magic_number": req.magic_number, "ea_bridge_token": token,
        "token_warning": "Copy this token now. It is shown once. Never share it or put it in a public repository.",
        "setup": "Install the developer's bridge-enabled EA on this MT5 account and enter the installation ID and token in its XAU Pilot settings."}

@router.post("/api/v1/developer/ea-products/{product_id}/performance-account/rotate-token")
def rotate_publisher_token(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    profile = db.query(DeveloperProfile).filter(DeveloperProfile.user_id == user.id, DeveloperProfile.status == "ACTIVE").first()
    if not profile:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Register as an active EA developer first")
    product = db.query(EaProduct).filter(EaProduct.id == product_id, EaProduct.developer_id == user.id).first()
    if not product:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "EA product not found")
    inst = db.query(EaInstallation).filter(EaInstallation.ea_product_id == product.id, EaInstallation.is_publisher_account == True).first()
    if not inst:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "No publisher MT5 account is connected")
    token = secrets.token_urlsafe(40)
    inst.auth_token_hash = hash_ea_token(token)
    db.commit()
    return {"installation_id": inst.id, "ea_bridge_token": token,
            "token_warning": "Previous token is now invalid. Copy this new token now; it is shown once."}

@router.post("/ea/v1/performance/trade", status_code=200)
def receive_performance_trade(req: PerformanceTradeIn, authorization: Optional[str] = Header(default=None), db: Session = Depends(get_db)):
    inst = _authenticate_installation(req.installation_id, authorization, db)
    existing = db.query(EaPerformanceTrade).filter(EaPerformanceTrade.installation_id == inst.id, EaPerformanceTrade.deal_ticket == req.deal_ticket).first()
    if existing:
        return {"status": "DUPLICATE_IGNORED", "deal_ticket": req.deal_ticket}
    closed_at = datetime.utcfromtimestamp(req.closed_at_epoch)
    if closed_at > datetime.utcnow() + timedelta(minutes=5) or closed_at < datetime.utcnow() - timedelta(days=3650):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Trade timestamp is outside the accepted range")
    net = req.profit + req.commission + req.swap
    db.add(EaPerformanceTrade(installation_id=inst.id, deal_ticket=req.deal_ticket, position_id=req.position_id,
        symbol=req.symbol, direction=req.direction, volume=req.volume, close_price=req.close_price,
        profit=req.profit, commission=req.commission, swap=req.swap, net_profit=net, currency=req.currency, closed_at=closed_at))
    inst.total_profit_loss_usd += net
    db.commit()
    return {"status": "RECORDED", "deal_ticket": req.deal_ticket, "net_profit": net}

@router.post("/ea/v1/performance/snapshot", status_code=201)
def receive_performance_snapshot(req: PerformanceSnapshotIn, authorization: Optional[str] = Header(default=None), db: Session = Depends(get_db)):
    inst = _authenticate_installation(req.installation_id, authorization, db)
    now = datetime.utcnow()
    captured = datetime.utcfromtimestamp(req.captured_at_epoch)
    if captured > now + timedelta(minutes=5) or captured < now - timedelta(days=3650):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Snapshot timestamp is outside the accepted range")
    latest = db.query(EaPerformanceSnapshot).filter(EaPerformanceSnapshot.installation_id == inst.id).order_by(EaPerformanceSnapshot.captured_at.desc()).first()
    # Avoid excessive writes: one sample per minute is enough for the public curve.
    if latest and (captured - latest.captured_at).total_seconds() < 60:
        return {"status": "THROTTLED", "next_sample_after_seconds": 60}
    db.add(EaPerformanceSnapshot(installation_id=inst.id, balance=req.balance, equity=req.equity, currency=req.currency, captured_at=captured))
    account = db.query(BrokerAccount).filter(BrokerAccount.id == inst.broker_account_id).first()
    if account:
        account.balance = req.balance; account.equity = req.equity; account.currency = req.currency
        account.last_heartbeat = now; account.connection_status = "CONNECTED"
    inst.last_heartbeat = now
    db.commit()
    return {"status": "RECORDED", "captured_at": captured.isoformat()}

@router.get("/api/v1/marketplace/ea-products/{product_id}/performance")
def public_ea_performance(product_id: str, db: Session = Depends(get_db)):
    product = db.query(EaProduct).filter(EaProduct.id == product_id, EaProduct.is_published == True, EaProduct.listing_status == "APPROVED").first()
    if not product:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Published EA not found")
    inst = db.query(EaInstallation).filter(EaInstallation.ea_product_id == product.id, EaInstallation.is_publisher_account == True).first()
    if not inst:
        return {"ea_product_id": product.id, "ea_name": product.name, "data_status": "NO_CONNECTED_OWNER_ACCOUNT",
                "message": "The developer has not connected an MT5 performance account yet.", "trade_count": 0, "equity_curve": []}
    account = db.query(BrokerAccount).filter(BrokerAccount.id == inst.broker_account_id).first()
    trades = db.query(EaPerformanceTrade).filter(EaPerformanceTrade.installation_id == inst.id).order_by(EaPerformanceTrade.closed_at.asc()).all()
    snapshots = db.query(EaPerformanceSnapshot).filter(EaPerformanceSnapshot.installation_id == inst.id).order_by(EaPerformanceSnapshot.captured_at.asc()).all()
    net = sum(t.net_profit for t in trades)
    gross_profit = sum(t.net_profit for t in trades if t.net_profit > 0)
    gross_loss = abs(sum(t.net_profit for t in trades if t.net_profit < 0))
    wins = sum(1 for t in trades if t.net_profit > 0)
    peak = 0.0; max_dd = 0.0
    curve = []
    for snap in snapshots:
        peak = max(peak, snap.equity)
        dd = ((peak - snap.equity) / peak * 100.0) if peak > 0 else 0.0
        max_dd = max(max_dd, dd)
        curve.append({"timestamp": snap.captured_at.isoformat() + "Z", "balance": round(snap.balance, 2), "equity": round(snap.equity, 2)})
    first = snapshots[0] if snapshots else None
    last = snapshots[-1] if snapshots else None
    days = max(0, (last.captured_at - first.captured_at).days) if first and last else 0
    data_status = "BUILDING_TRACK_RECORD" if len(trades) < 10 or days < 7 else "CONNECTED_MT5_DEMO"
    return {"ea_product_id": product.id, "ea_name": product.name, "data_status": data_status,
        "source_label": "Connected owner MT5 account · EA-reported data",
        "account_scope_warning": "Use a dedicated MT5 account running only this EA. Other EAs or manual trades on the same account affect balance and equity snapshots.",
        "environment": account.environment if account else "UNKNOWN", "currency": last.currency if last else (account.currency if account else "USD"),
        "track_record_started_at": first.captured_at.isoformat() + "Z" if first else None,
        "track_record_days": days, "last_updated_at": last.captured_at.isoformat() + "Z" if last else None,
        "closed_trades": len(trades), "winning_trades": wins, "losing_trades": len(trades)-wins,
        "win_rate_pct": round(wins / len(trades) * 100.0, 2) if trades else None,
        "net_profit": round(net, 2),
        "net_profit_pct_of_initial_balance": round(net / first.balance * 100.0, 2) if first and first.balance > 0 else None,
        "gross_profit": round(gross_profit, 2), "gross_loss": round(gross_loss, 2),
        "profit_factor": round(gross_profit / gross_loss, 3) if gross_loss > 0 else (None if not trades else "no_losses_recorded"),
        "max_equity_drawdown_pct": round(max_dd, 2) if snapshots else None,
        "equity_curve": curve,
        "disclaimer": "These figures are reported by the EA running in the developer's connected MT5 terminal. They are not an independently audited broker statement. The net-profit percentage uses the first recorded balance as its denominator and does not adjust for deposits or withdrawals. Commissions, swaps, account currency and terminal connectivity can affect results. Past performance does not predict future results."}
