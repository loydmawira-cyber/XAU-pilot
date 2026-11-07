from fastapi import APIRouter, Depends, HTTPException, status, Header
from sqlalchemy.orm import Session
from typing import Dict, Any, Optional
from datetime import datetime
import json
import secrets
from backend.app.security import hash_ea_token

from backend.app.database import get_db
from backend.app.models import (
    EaInstallation, BrokerAccount, EaLicense, Command,
    EaConfigVersion, Position, ClosedTrade, AuditLog, User
)
from backend.app.schemas import (
    EaHeartbeatRequest, LicenseValidateRequest, LicenseValidateResponse,
    CommandAckRequest, ConfigAckRequest, TradeEventRequest, PositionsSnapshotRequest
)

router = APIRouter(prefix="", tags=["MQL5 EA Bridge Protocol"])

def _authorized_installation(installation_id: str, authorization: Optional[str], db: Session) -> EaInstallation:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing EA bearer token")
    token = authorization.split(" ", 1)[1].strip()
    inst = db.query(EaInstallation).filter(EaInstallation.id == installation_id).first()
    if not inst or not secrets.compare_digest(inst.auth_token_hash, hash_ea_token(token)):
        raise HTTPException(status_code=401, detail="EA authentication failed")
    return inst

def _authorized_command(command: Command, authorization: Optional[str], db: Session) -> EaInstallation:
    candidates = db.query(EaInstallation).filter(
        (EaInstallation.id == command.installation_id) if command.installation_id else
        (EaInstallation.broker_account_id == command.broker_account_id)
    ).all()
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing EA bearer token")
    token_hash = hash_ea_token(authorization.split(" ", 1)[1].strip())
    for inst in candidates:
        if secrets.compare_digest(inst.auth_token_hash, token_hash):
            return inst
    raise HTTPException(status_code=401, detail="EA authentication failed for command")

@router.post("/heartbeat")
def ea_heartbeat(
    req: EaHeartbeatRequest,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    inst = _authorized_installation(req.installation_id, authorization, db)

    now = datetime.utcnow()
    inst.last_heartbeat = now
    inst.runtime_status = req.status

    # Update real broker account balance and equity confirmed by terminal
    account = db.query(BrokerAccount).filter(BrokerAccount.id == inst.broker_account_id).first()
    if account:
        account.balance = req.balance
        account.equity = req.equity
        account.free_margin = req.free_margin
        account.used_margin = max(0.0, req.equity - req.free_margin)
        account.margin_level_percent = (req.equity / account.used_margin * 100.0) if account.used_margin > 0 else 0.0
        account.last_heartbeat = now
        account.connection_status = "CONNECTED"

    # Count pending commands
    pending_cmds = db.query(Command).filter(
        ((Command.installation_id == inst.id) | (Command.broker_account_id == inst.broker_account_id)),
        Command.command_state == "PENDING"
    ).count()

    db.commit()

    return {
        "status": "ACKNOWLEDGED",
        "pending_commands_count": pending_cmds,
        "has_pending_config": (inst.config_status == "PENDING"),
        "pending_config_version": inst.pending_config_version,
        "server_time": int(now.timestamp())
    }

@router.post("/license/validate", response_model=LicenseValidateResponse)
def validate_license(
    req: LicenseValidateRequest,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    try:
        inst = _authorized_installation(req.installation_id, authorization, db)
    except HTTPException as exc:
        if exc.status_code == 404:
            return LicenseValidateResponse(valid=False, license_status="REVOKED")
        raise

    # Publisher performance installs are owned by the EA developer; they do not consume a customer licence.
    if inst.is_publisher_account and inst.ea_product and inst.ea_product.developer_id:
        owner = db.query(User).filter_by(id=inst.ea_product.developer_id).first()
        if owner and owner.tenant_id == inst.tenant_id:
            return LicenseValidateResponse(valid=True, license_status="ACTIVE", entitled_until="PUBLISHER_TRACKING")

    # Check active license for tenant and product
    lic = db.query(EaLicense).filter(
        EaLicense.tenant_id == inst.tenant_id,
        EaLicense.ea_product_id == inst.ea_product_id,
        EaLicense.status == "ACTIVE"
    ).first()

    if not lic:
        return LicenseValidateResponse(valid=False, license_status="UNLICENSED")

    if lic.expires_at and lic.expires_at < datetime.utcnow():
        lic.status = "EXPIRED"
        db.commit()
        return LicenseValidateResponse(valid=False, license_status="EXPIRED")

    return LicenseValidateResponse(
        valid=True,
        license_status="ACTIVE",
        entitled_until=lic.expires_at.strftime("%Y-%m-%d %H:%M:%S") if lic.expires_at else "LIFETIME"
    )

@router.get("/commands/next")
def get_next_command(
    installation_id: str,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    inst = _authorized_installation(installation_id, authorization, db)

    cmd = db.query(Command).filter(
        ((Command.installation_id == inst.id) | (Command.broker_account_id == inst.broker_account_id)),
        Command.command_state == "PENDING"
    ).order_by(Command.created_at.asc()).first()

    if not cmd:
        return {"command": None}

    cmd.command_state = "DELIVERED"
    db.commit()

    return {
        "command": {
            "id": cmd.id,
            "type": cmd.command_type,
            "policy": cmd.policy,
            "payload": json.loads(cmd.payload) if cmd.payload else {}
        }
    }

@router.post("/commands/{command_id}/ack")
def acknowledge_command(
    command_id: str,
    req: CommandAckRequest,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    cmd = db.query(Command).filter(Command.id == command_id).first()
    if not cmd:
        raise HTTPException(status_code=404, detail="Command not found")
    _authorized_command(cmd, authorization, db)

    cmd.command_state = req.status # APPLIED or REJECTED
    cmd.acknowledged_at = datetime.utcnow()
    db.commit()

    return {"status": "RECORDED", "command_id": command_id, "state": cmd.command_state}

@router.get("/configuration/next")
def get_pending_configuration(
    installation_id: str,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    inst = _authorized_installation(installation_id, authorization, db)

    if inst.config_status != "PENDING" or not inst.pending_config_version:
        return {"has_update": False, "version": inst.current_config_version}

    try:
        vals = json.loads(inst.pending_values) if inst.pending_values else {}
    except Exception:
        vals = {}

    return {
        "has_update": True,
        "version": inst.pending_config_version,
        "values": vals
    }

@router.post("/configuration/ack")
def acknowledge_configuration(
    req: ConfigAckRequest,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    inst = _authorized_installation(req.installation_id, authorization, db)

    version_row = db.query(EaConfigVersion).filter(
        EaConfigVersion.installation_id == inst.id,
        EaConfigVersion.version == req.version
    ).first()

    now = datetime.utcnow()

    if req.status == "APPLIED":
        inst.current_config_version = req.version
        inst.pending_config_version = None
        inst.config_status = "APPLIED"
        inst.current_values = json.dumps(req.applied_values)
        inst.pending_values = None

        if version_row:
            version_row.status = "APPLIED"
            version_row.applied_at = now

        audit = AuditLog(
            tenant_id=inst.tenant_id,
            action="EA_CONFIG_CONFIRMED",
            target_type="EA_INSTALLATION",
            target_id=inst.id,
            details=f'{{"version": {req.version}, "status": "APPLIED"}}'
        )
        db.add(audit)
    else:
        inst.pending_config_version = None
        inst.config_status = "REJECTED"
        inst.pending_values = None

        if version_row:
            version_row.status = "REJECTED"
            version_row.rejection_reason = req.rejection_reason or "EA rejected configuration"

    db.commit()

    return {"status": "SUCCESS", "current_version": inst.current_config_version}

@router.post("/positions/snapshot")
def receive_positions_snapshot(
    req: PositionsSnapshotRequest,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    inst = _authorized_installation(req.installation_id, authorization, db)

    # Clear outdated positions for this installation
    db.query(Position).filter(Position.ea_installation_id == inst.id).delete()

    for p in req.positions:
        pos = Position(
            ticket=p.get("ticket"),
            broker_account_id=inst.broker_account_id,
            ea_installation_id=inst.id,
            ea_name=inst.ea_product.name if inst.ea_product else "XAU Expert",
            symbol=p.get("symbol", "XAUUSD"),
            direction=p.get("direction", "BUY"),
            lot_size=float(p.get("lot_size", 0.01)),
            open_price=float(p.get("open_price", 0.0)),
            current_price=float(p.get("current_price", 0.0)),
            stop_loss=float(p.get("stop_loss")) if p.get("stop_loss") else None,
            take_profit=float(p.get("take_profit")) if p.get("take_profit") else None,
            floating_profit_loss=float(p.get("floating_pl", 0.0)),
            swap=float(p.get("swap", 0.0)),
            commission=float(p.get("commission", 0.0)),
            magic_number=inst.magic_number
        )
        db.add(pos)

    inst.open_positions_count = len(req.positions)
    db.commit()

    return {"status": "STORED", "count": len(req.positions)}

@router.post("/trades/event")
def receive_trade_event(
    req: TradeEventRequest,
    authorization: Optional[str] = Header(default=None),
    db: Session = Depends(get_db)
):
    inst = _authorized_installation(req.installation_id, authorization, db)

    # If it is a deal closed with profit
    if req.profit != 0:
        inst.total_profit_loss_usd += req.profit

    db.commit()
    return {"status": "RECORDED", "ticket": req.ticket}
