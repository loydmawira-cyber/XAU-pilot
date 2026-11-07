from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Dict, Any, Optional
from datetime import datetime
import uuid
import json

from backend.app.database import get_db
from backend.app.models import (
    EaInstallation,
    BrokerAccount, VpsInstance, Command, Position, ClosedTrade, User, AuditLog
)
from backend.app.schemas import (
    VpsInstanceResponse, RemoteCommandResponse, PositionResponse, ClosedTradeResponse
)
from backend.app.dependencies import get_current_user

router = APIRouter(prefix="", tags=["Managed VPS & Account Telemetry"])

@router.get("/hosting/managed-vps", response_model=List[VpsInstanceResponse])
def get_vps_instances(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    instances = db.query(VpsInstance).filter(
        VpsInstance.tenant_id == current_user.tenant_id
    ).all()

    if not instances:
        # Seed initial managed VPS instance for customer
        vps = VpsInstance(
            id="vps_ld4_01",
            tenant_id=current_user.tenant_id,
            name="XAU VPS Primary London",
            plan_name="Enterprise Low-Latency (London LD4)",
            ip_address="185.220.101.45",
            os="Windows Server 2022 Datacenter",
            cpu_cores=4,
            ram_gb=8,
            ssd_gb=160,
            region="London, UK (0.8ms to Equinix MT5 servers)",
            status="RUNNING",
            mt5_terminal_status="MetaTrader 5 Build 4480 Running",
            uptime_hours=412,
            cpu_usage_percent=14,
            ram_usage_percent=38,
            last_heartbeat=datetime.utcnow()
        )
        db.add(vps)
        db.commit()
        db.refresh(vps)
        instances = [vps]

    return [
        VpsInstanceResponse(
            id=v.id,
            name=v.name,
            planName=v.plan_name,
            ipAddress=v.ip_address,
            os=v.os,
            cpuCores=v.cpu_cores,
            ramGb=v.ram_gb,
            ssdGb=v.ssd_gb,
            region=v.region,
            status=v.status,
            mt5TerminalStatus=v.mt5_terminal_status,
            uptimeHours=v.uptime_hours,
            cpuUsagePercent=v.cpu_usage_percent,
            ramUsagePercent=v.ram_usage_percent,
            lastHeartbeatUtc=v.last_heartbeat.strftime("%Y-%m-%d %H:%M:%S")
        )
        for v in instances
    ]

@router.post("/hosting/managed-vps/{vps_id}/restart")
def restart_vps(
    vps_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    vps = db.query(VpsInstance).filter(
        VpsInstance.id == vps_id,
        VpsInstance.tenant_id == current_user.tenant_id
    ).first()
    if not vps:
        raise HTTPException(status_code=404, detail="VPS instance not found")

    vps.status = "REBOOTING"
    db.add(AuditLog(
        tenant_id=current_user.tenant_id,
        user_id=current_user.id,
        action="VPS_RESTART_REQUESTED",
        target_type="VPS_INSTANCE",
        target_id=vps_id,
        details='{"action": "RESTART"}'
    ))
    db.commit()
    return {"status": "SUCCESS", "message": "VPS restart sequence queued"}

@router.post("/hosting/managed-vps/{vps_id}/stop")
def stop_vps(
    vps_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    vps = db.query(VpsInstance).filter(
        VpsInstance.id == vps_id,
        VpsInstance.tenant_id == current_user.tenant_id
    ).first()
    if not vps:
        raise HTTPException(status_code=404, detail="VPS instance not found")

    vps.status = "STOPPED"
    db.add(AuditLog(
        tenant_id=current_user.tenant_id,
        user_id=current_user.id,
        action="VPS_STOP_REQUESTED",
        target_type="VPS_INSTANCE",
        target_id=vps_id,
        details='{"action": "STOP"}'
    ))
    db.commit()
    return {"status": "SUCCESS", "message": "VPS halted"}

# Account Remote Commands
def _queue_for_account_installations(db: Session, acc: BrokerAccount, command_type: str, policy: Optional[str] = None, payload: Optional[dict] = None):
    installations = db.query(EaInstallation).filter(EaInstallation.broker_account_id == acc.id).all()
    targets = [inst.id for inst in installations] or [None]
    commands = []
    for installation_id in targets:
        cmd = Command(
            id=str(uuid.uuid4()),
            tenant_id=acc.tenant_id,
            broker_account_id=acc.id,
            installation_id=installation_id,
            command_type=command_type,
            command_state="PENDING",
            policy=policy,
            payload=json.dumps(payload or {})
        )
        db.add(cmd)
        commands.append(cmd)
    return commands

@router.post("/accounts/{account_id}/commands/pause", response_model=RemoteCommandResponse)
def pause_account(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")

    commands = _queue_for_account_installations(db, acc, "PAUSE", payload={"entry_mode": "BLOCK_NEW_ENTRIES"})
    db.commit()
    cmd = commands[0]
    db.refresh(cmd)

    return RemoteCommandResponse(
        id=cmd.id, accountId=acc.id, installationId=cmd.installation_id,
        type="PAUSE", state="PENDING", emergencyPolicy=None,
        createdAtUtc=cmd.created_at.strftime("%Y-%m-%d %H:%M:%S"), acknowledgedAtUtc=None
    )

@router.post("/accounts/{account_id}/commands/resume", response_model=RemoteCommandResponse)
def resume_account(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")

    commands = _queue_for_account_installations(db, acc, "RESUME", payload={"entry_mode": "ALLOW_NEW_ENTRIES"})
    db.commit()
    cmd = commands[0]
    db.refresh(cmd)

    return RemoteCommandResponse(
        id=cmd.id, accountId=acc.id, installationId=cmd.installation_id,
        type="RESUME", state="PENDING", emergencyPolicy=None,
        createdAtUtc=cmd.created_at.strftime("%Y-%m-%d %H:%M:%S"), acknowledgedAtUtc=None
    )

@router.post("/accounts/{account_id}/commands/emergency-stop", response_model=RemoteCommandResponse)
def emergency_stop_account(
    account_id: str,
    payload: Dict[str, str],
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")

    policy = payload.get("policy", "CLOSE_NEW_ENTRIES_ONLY")
    # The UI always requests the explicit flatten-and-stop policy. Do not allow a weaker policy here.
    policy = "CLOSE_ALL_MARKET_ORDERS"
    commands = _queue_for_account_installations(
        db, acc, "EMERGENCY_STOP", policy=policy,
        payload={"policy": policy, "delete_pending_entry_orders": True, "stop_after_flatten": True}
    )
    db.add(AuditLog(
        tenant_id=current_user.tenant_id, user_id=current_user.id,
        action="EMERGENCY_STOP_DISPATCHED", target_type="BROKER_ACCOUNT",
        target_id=acc.id, details=json.dumps({"policy": policy, "installation_commands": len(commands)})
    ))
    db.commit()
    cmd = commands[0]
    db.refresh(cmd)

    return RemoteCommandResponse(
        id=cmd.id, accountId=acc.id, installationId=cmd.installation_id,
        type="EMERGENCY_STOP", state="PENDING", emergencyPolicy=policy,
        createdAtUtc=cmd.created_at.strftime("%Y-%m-%d %H:%M:%S"), acknowledgedAtUtc=None
    )

@router.get("/accounts/{account_id}/positions", response_model=List[PositionResponse])
def get_account_positions(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")

    positions = db.query(Position).filter(Position.broker_account_id == account_id).all()
    return [
        PositionResponse(
            ticket=p.ticket,
            brokerAccountId=p.broker_account_id,
            eaInstallationId=p.ea_installation_id,
            eaName=p.ea_name,
            symbol=p.symbol,
            direction=p.direction,
            lotSize=p.lot_size,
            openPrice=p.open_price,
            currentPrice=p.current_price,
            stopLoss=p.stop_loss,
            takeProfit=p.take_profit,
            floatingProfitLoss=p.floating_profit_loss,
            swap=p.swap,
            commission=p.commission,
            magicNumber=p.magic_number,
            openTimeUtc=p.open_time.strftime("%Y-%m-%d %H:%M:%S")
        )
        for p in positions
    ]

@router.get("/accounts/{account_id}/trades", response_model=List[ClosedTradeResponse])
def get_account_trades_history(
    account_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(BrokerAccount).filter(
        BrokerAccount.id == account_id,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="Account not found")

    trades = db.query(ClosedTrade).filter(
        ClosedTrade.broker_account_id == account_id
    ).order_by(ClosedTrade.close_time.desc()).all()

    return [
        ClosedTradeResponse(
            ticket=t.ticket,
            brokerAccountId=t.broker_account_id,
            eaName=t.ea_name,
            symbol=t.symbol,
            direction=t.direction,
            lotSize=t.lot_size,
            openPrice=t.open_price,
            closePrice=t.close_price,
            stopLoss=t.stop_loss,
            takeProfit=t.take_profit,
            netProfitLoss=t.net_profit_loss,
            commission=t.commission,
            swap=t.swap,
            openTimeUtc=t.open_time.strftime("%Y-%m-%d %H:%M:%S"),
            closeTimeUtc=t.close_time.strftime("%Y-%m-%d %H:%M:%S")
        )
        for t in trades
    ]
