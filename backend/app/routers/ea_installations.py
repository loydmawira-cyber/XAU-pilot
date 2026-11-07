from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Dict, Any
from datetime import datetime
import json
import uuid
import hashlib

from backend.app.database import get_db
from backend.app.models import (
    EaInstallation, EaProduct, BrokerAccount, AccountRiskProfile,
    EaConfigVersion, EaPreset, User, AuditLog
)
from backend.app.schemas import (
    EaInstallationCreate, EaInstallationResponse, ParameterDefinitionSchema,
    ConfigAuditEntrySchema, EaPresetSchema
)
from backend.app.dependencies import get_current_user
from backend.app.ea_dev import manifest as ea_manifest
from backend.app.config import settings
from backend.app.models import EaProductVersion, EaInputManifest

router = APIRouter(prefix="", tags=["EA Installations & Remote Inputs"])

# Standard parameter definitions catalog
PARAM_DEFINITIONS: Dict[str, List[ParameterDefinitionSchema]] = {
    "ea_gold_pro": [
        ParameterDefinitionSchema(key="lot_size", displayName="Fixed Lot Size", description="Trade volume per order", dataType="DECIMAL", defaultValue="0.10", minValue=0.01, maxValue=10.0, step=0.01, unit="lots", category="Money Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="risk_percent", displayName="Risk Percentage", description="Equity risk % per trade", dataType="DECIMAL", defaultValue="1.5", minValue=0.1, maxValue=5.0, step=0.1, unit="%", category="Money Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="stop_loss_points", displayName="Stop Loss", description="Protective stop in points (10pts = 1pip)", dataType="INTEGER", defaultValue="120", minValue=20.0, maxValue=1000.0, step=10.0, unit="pts", category="Risk & Stop Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="take_profit_points", displayName="Take Profit", description="Target profit in points", dataType="INTEGER", defaultValue="240", minValue=20.0, maxValue=2000.0, step=10.0, unit="pts", category="Risk & Stop Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="max_open_trades", displayName="Max Open Trades", description="Concurrent position limit", dataType="INTEGER", defaultValue="3", minValue=1.0, maxValue=10.0, step=1.0, unit="trades", category="Money Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="trading_direction", displayName="Trading Direction", description="Direction filter", dataType="ENUM", defaultValue="BOTH", category="Execution", visibility="STANDARD", enumOptions=["BOTH", "BUY_ONLY", "SELL_ONLY"]),
        ParameterDefinitionSchema(key="trailing_stop_enabled", displayName="Trailing Stop", description="Profit trailing", dataType="BOOLEAN", defaultValue="true", category="Trailing Stop", visibility="STANDARD"),
        ParameterDefinitionSchema(key="break_even_enabled", displayName="Break-Even Trigger", description="Move SL to entry", dataType="BOOLEAN", defaultValue="true", category="Trailing Stop", visibility="STANDARD"),
        ParameterDefinitionSchema(key="max_spread_points", displayName="Max Spread Allowed", description="Spread filter", dataType="INTEGER", defaultValue="35", minValue=10.0, maxValue=100.0, step=5.0, unit="pts", category="Safety Filters", visibility="STANDARD"),
        ParameterDefinitionSchema(key="trading_session", displayName="Trading Sessions", description="Time window", dataType="ENUM", defaultValue="LONDON_NY", category="Execution", visibility="STANDARD", enumOptions=["ALL_DAY", "LONDON_ONLY", "NY_ONLY", "LONDON_NY"]),
        # Advanced Parameters
        ParameterDefinitionSchema(key="trailing_start_points", displayName="Trailing Activation", description="Profit points before trailing activates", dataType="INTEGER", defaultValue="60", minValue=10.0, maxValue=500.0, step=10.0, unit="pts", category="Trailing Stop", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="trailing_step_points", displayName="Trailing Step", description="Step increment for trail", dataType="INTEGER", defaultValue="20", minValue=5.0, maxValue=100.0, step=5.0, unit="pts", category="Trailing Stop", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="break_even_trigger", displayName="Break-Even Points", description="Points to move SL to BE", dataType="INTEGER", defaultValue="50", minValue=10.0, maxValue=300.0, step=5.0, unit="pts", category="Trailing Stop", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="atr_period", displayName="ATR Volatility Period", description="Lookback bars", dataType="INTEGER", defaultValue="14", minValue=5.0, maxValue=50.0, step=1.0, unit="bars", category="Indicator Settings", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="rsi_period", displayName="RSI Momentum Period", description="RSI lookback", dataType="INTEGER", defaultValue="14", minValue=2.0, maxValue=30.0, step=1.0, unit="bars", category="Indicator Settings", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="rsi_overbought", displayName="RSI Overbought Filter", description="Threshold suppressing buys", dataType="INTEGER", defaultValue="70", minValue=50.0, maxValue=95.0, step=1.0, unit="level", category="Indicator Settings", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="rsi_oversold", displayName="RSI Oversold Filter", description="Threshold suppressing sells", dataType="INTEGER", defaultValue="30", minValue=5.0, maxValue=50.0, step=1.0, unit="level", category="Indicator Settings", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="magic_number", displayName="EA Magic Number", description="Order identifier", dataType="INTEGER", defaultValue="108821", minValue=100000.0, maxValue=999999.0, step=1.0, unit="", category="Routing & Identity", visibility="ADVANCED", requiresRestart=True)
    ],
    "ea_gold_scalper": [
        ParameterDefinitionSchema(key="lot_size", displayName="Fixed Lot Size", description="Order volume", dataType="DECIMAL", defaultValue="0.05", minValue=0.01, maxValue=5.0, step=0.01, unit="lots", category="Money Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="risk_percent", displayName="Risk Percentage", description="Equity risk %", dataType="DECIMAL", defaultValue="1.0", minValue=0.1, maxValue=3.0, step=0.1, unit="%", category="Money Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="stop_loss_points", displayName="Stop Loss", description="Points for scalp SL", dataType="INTEGER", defaultValue="60", minValue=15.0, maxValue=300.0, step=5.0, unit="pts", category="Risk & Stop Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="take_profit_points", displayName="Take Profit", description="Points for scalp TP", dataType="INTEGER", defaultValue="90", minValue=20.0, maxValue=500.0, step=5.0, unit="pts", category="Risk & Stop Management", visibility="STANDARD"),
        ParameterDefinitionSchema(key="max_spread_points", displayName="Max Spread Allowed", description="Max spread", dataType="INTEGER", defaultValue="28", minValue=10.0, maxValue=50.0, step=1.0, unit="pts", category="Safety Filters", visibility="STANDARD"),
        ParameterDefinitionSchema(key="trailing_stop_enabled", displayName="Trailing Stop", description="Enable trailing", dataType="BOOLEAN", defaultValue="true", category="Trailing Stop", visibility="STANDARD"),
        ParameterDefinitionSchema(key="trailing_start_points", displayName="Trailing Activation", description="Profit before trailing", dataType="INTEGER", defaultValue="30", minValue=10.0, maxValue=100.0, step=5.0, unit="pts", category="Trailing Stop", visibility="ADVANCED"),
        ParameterDefinitionSchema(key="magic_number", displayName="EA Magic Number", description="Order routing number", dataType="INTEGER", defaultValue="209934", minValue=100000.0, maxValue=999999.0, step=1.0, unit="", category="Routing & Identity", visibility="ADVANCED", requiresRestart=True)
    ]
}

@router.get("/ea-installations", response_model=List[EaInstallationResponse])
def list_installations(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    installations = db.query(EaInstallation).filter(
        EaInstallation.tenant_id == current_user.tenant_id
    ).all()

    resp = []
    for inst in installations:
        try:
            curr_vals = json.loads(inst.current_values) if inst.current_values else {}
        except Exception:
            curr_vals = {}
        resp.append(
            EaInstallationResponse(
                id=inst.id,
                tenantId=inst.tenant_id,
                eaProductId=inst.ea_product_id,
                eaProductName=inst.ea_product.name if inst.ea_product else "XAU Expert",
                brokerAccountId=inst.broker_account_id,
                brokerAccountLabel=inst.broker_account.label if inst.broker_account else "MT5 Account",
                magicNumber=inst.magic_number,
                symbol=inst.symbol,
                timeframe=inst.timeframe,
                runtimeStatus=inst.runtime_status,
                currentConfigVersion=inst.current_config_version,
                pendingConfigVersion=inst.pending_config_version,
                configStatus=inst.config_status,
                currentValues=curr_vals,
                lastHeartbeatUtc=inst.last_heartbeat.strftime("%Y-%m-%d %H:%M:%S") if inst.last_heartbeat else None,
                hostingType=inst.hosting_type,
                vpsInstanceId=inst.vps_instance_id,
                openPositionsCount=inst.open_positions_count,
                totalProfitLossUsd=inst.total_profit_loss_usd
            )
        )
    return resp

@router.post("/ea-installations", response_model=EaInstallationResponse)
def create_installation(
    req: EaInstallationCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # Verify account ownership
    account = db.query(BrokerAccount).filter(
        BrokerAccount.id == req.brokerAccountId,
        BrokerAccount.tenant_id == current_user.tenant_id
    ).first()
    if not account:
        raise HTTPException(status_code=404, detail="Broker account not found")

    ea = db.query(EaProduct).filter(EaProduct.id == req.eaProductId).first()
    if not ea:
        raise HTTPException(status_code=404, detail="EA product not found")

    # Generate initial default values
    defaults = {}
    defs = PARAM_DEFINITIONS.get(ea.id, [])
    for d in defs:
        defaults[d.key] = d.defaultValue
    defaults["magic_number"] = str(req.magicNumber)

    raw_token = f"xau_inst_{uuid.uuid4().hex}"
    token_hash = hashlib.sha256(raw_token.encode("utf-8")).hexdigest()

    inst_id = str(uuid.uuid4())
    installation = EaInstallation(
        id=inst_id,
        tenant_id=current_user.tenant_id,
        broker_account_id=account.id,
        ea_product_id=ea.id,
        magic_number=req.magicNumber,
        symbol="XAUUSD",
        timeframe=req.timeframe,
        runtime_status="RUNNING",
        current_config_version=1,
        config_status="APPLIED",
        current_values=json.dumps(defaults),
        auth_token_hash=token_hash,
        hosting_type=account.hosting_type,
        open_positions_count=0,
        total_profit_loss_usd=0.0
    )
    db.add(installation)

    account.assigned_ea_count += 1
    db.commit()
    db.refresh(installation)

    return EaInstallationResponse(
        id=installation.id,
        tenantId=installation.tenant_id,
        eaProductId=installation.ea_product_id,
        eaProductName=ea.name,
        brokerAccountId=installation.broker_account_id,
        brokerAccountLabel=account.label,
        magicNumber=installation.magic_number,
        symbol=installation.symbol,
        timeframe=installation.timeframe,
        runtimeStatus=installation.runtime_status,
        currentConfigVersion=installation.current_config_version,
        configStatus=installation.config_status,
        currentValues=defaults,
        lastHeartbeatUtc=None,
        hostingType=installation.hosting_type,
        openPositionsCount=0,
        totalProfitLossUsd=0.0
    )

@router.get("/ea-installations/{installation_id}/inputs", response_model=List[ParameterDefinitionSchema])
def get_installation_inputs(installation_id: str, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    """Return only developer-approved visible inputs from the current published version."""
    inst = db.query(EaInstallation).filter(EaInstallation.id == installation_id, EaInstallation.tenant_id == current_user.tenant_id).first()
    if not inst: raise HTTPException(status_code=404, detail="Installation not found")
    product = db.query(EaProduct).filter(EaProduct.id == inst.ea_product_id).first()
    if not product: raise HTTPException(status_code=404, detail="EA product not found")
    version = db.query(EaProductVersion).filter(EaProductVersion.ea_product_id == product.id, EaProductVersion.version == product.current_version, EaProductVersion.status == "PUBLISHED").first()
    if not version: return []
    row = db.query(EaInputManifest).filter(EaInputManifest.version_id == version.id, EaInputManifest.is_valid.is_(True)).first()
    if not row or not version.manifest_hash or not version.signature: raise HTTPException(status_code=409, detail="Published EA manifest is unavailable")
    actual_hash = ea_manifest.manifest_hash(row.canonical_json)
    if actual_hash != version.manifest_hash or actual_hash != row.manifest_hash: raise HTTPException(status_code=409, detail="EA manifest integrity check failed")
    if not ea_manifest.verify_release(settings.MANIFEST_SIGNING_KEY, version.signature, product.id, version.id, product.developer_id or "", version.manifest_hash, version.binary_sha256 or ""):
        raise HTTPException(status_code=409, detail="EA manifest signature verification failed")
    try:
        raw = ea_manifest.customer_view(json.loads(row.canonical_json))
        type_map = {"decimal":"DECIMAL", "integer":"INTEGER", "boolean":"BOOLEAN", "enum":"ENUM", "string":"STRING"}
        return [ParameterDefinitionSchema(key=x["key"], displayName=x["label"], description=x.get("description", ""), dataType=type_map[x["type"]], defaultValue=str(x["default"]), minValue=x.get("minimum"), maxValue=x.get("maximum"), step=x.get("step"), unit=x.get("unit", ""), category=x.get("category", "General"), visibility="STANDARD", enumOptions=[str(c.get("value")) if isinstance(c, dict) else str(c) for c in x.get("choices", [])] or None, requiresRestart=bool(x.get("requires_restart", False))) for x in raw]
    except (ValueError, KeyError, TypeError, json.JSONDecodeError):
        raise HTTPException(status_code=409, detail="Published EA manifest is invalid")

@router.get("/ea-installations/{installation_id}/configuration")
def get_installation_configuration(
    installation_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    inst = db.query(EaInstallation).filter(
        EaInstallation.id == installation_id,
        EaInstallation.tenant_id == current_user.tenant_id
    ).first()
    if not inst:
        raise HTTPException(status_code=404, detail="Installation not found")

    try:
        applied_vals = json.loads(inst.current_values) if inst.current_values else {}
    except Exception:
        applied_vals = {}

    try:
        pending_vals = json.loads(inst.pending_values) if inst.pending_values else None
    except Exception:
        pending_vals = None

    return {
        "installation_id": inst.id,
        "current_version": inst.current_config_version,
        "pending_version": inst.pending_config_version,
        "config_status": inst.config_status,
        "applied_values": applied_vals,
        "pending_values": pending_vals
    }

@router.put("/ea-installations/{installation_id}/configuration", response_model=ConfigAuditEntrySchema)
def update_installation_configuration(
    installation_id: str,
    new_values: Dict[str, str],
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    inst = db.query(EaInstallation).filter(
        EaInstallation.id == installation_id,
        EaInstallation.tenant_id == current_user.tenant_id
    ).first()
    if not inst:
        raise HTTPException(status_code=404, detail="Installation not found")

    # Enforce developer-owned permissions using the authoritative published manifest.
    product = db.query(EaProduct).filter(EaProduct.id == inst.ea_product_id).first()
    version = db.query(EaProductVersion).filter(EaProductVersion.ea_product_id == inst.ea_product_id, EaProductVersion.version == (product.current_version if product else None), EaProductVersion.status == "PUBLISHED").first() if product else None
    row = db.query(EaInputManifest).filter(EaInputManifest.version_id == (version.id if version else None), EaInputManifest.is_valid.is_(True)).first() if version else None
    if not product or not version or not row or not version.signature: raise HTTPException(status_code=409, detail="No verified published developer manifest is available")
    manifest_hash_value = ea_manifest.manifest_hash(row.canonical_json)
    if manifest_hash_value != version.manifest_hash or manifest_hash_value != row.manifest_hash: raise HTTPException(status_code=409, detail="EA manifest integrity check failed")
    if not ea_manifest.verify_release(settings.MANIFEST_SIGNING_KEY, version.signature, product.id, version.id, product.developer_id or "", version.manifest_hash or "", version.binary_sha256 or ""):
        raise HTTPException(status_code=409, detail="EA manifest signature verification failed")
    try:
        manifest_data = json.loads(row.canonical_json)
        definitions = {x["key"]: x for x in manifest_data.get("parameters", [])}
        typed_values = {}
        for key, value in new_values.items():
            definition = definitions.get(key)
            if not definition or definition.get("visible_to_customer") is not True or definition.get("customer_editable") is not True:
                raise HTTPException(status_code=403, detail=f"{key}: this setting is not customer-editable")
            kind = definition["type"]
            if kind == "integer": typed_values[key] = int(value)
            elif kind == "decimal": typed_values[key] = float(value)
            elif kind == "boolean":
                if str(value).lower() not in ("true", "false"): raise ValueError(f"{key}: expected true or false")
                typed_values[key] = str(value).lower() == "true"
            else: typed_values[key] = str(value)
        errors = ea_manifest.validate_customer_values(manifest_data, typed_values)
        if errors: raise HTTPException(status_code=422, detail={"configuration_errors": errors})
    except HTTPException: raise
    except (ValueError, TypeError, KeyError): raise HTTPException(status_code=422, detail="One or more setting values have invalid types")

    # Fetch Account Risk Profile for server-side risk validation
    account = db.query(BrokerAccount).filter(BrokerAccount.id == inst.broker_account_id).first()
    risk = db.query(AccountRiskProfile).filter(AccountRiskProfile.account_id == account.id).first() if account else None

    # Check Lot Size
    if "lot_size" in new_values:
        try:
            lot = float(new_values["lot_size"])
            max_allowed = risk.max_lot_size_per_trade if risk else 0.50
            if lot > max_allowed:
                reason = f"Requested lot size {lot} exceeds Account Risk Rule limit of {max_allowed} lots"
                audit = EaConfigVersion(
                    installation_id=inst.id,
                    version=inst.current_config_version + 1,
                    parameter_key="lot_size",
                    old_value=json.loads(inst.current_values).get("lot_size", ""),
                    new_value=str(lot),
                    config_values=json.dumps(new_values),
                    status="REJECTED",
                    submitted_by=current_user.id,
                    rejection_reason=reason
                )
                db.add(audit)
                db.commit()
                raise HTTPException(status_code=400, detail=reason)
        except ValueError:
            raise HTTPException(status_code=400, detail="Invalid lot size format")

    # Separation of Desired State vs Applied State
    next_version = inst.current_config_version + 1
    inst.pending_config_version = next_version
    inst.pending_values = json.dumps(new_values)
    inst.config_status = "PENDING"

    audit_entry = EaConfigVersion(
        installation_id=inst.id,
        version=next_version,
        parameter_key="all",
        old_value=inst.current_values,
        new_value=json.dumps(new_values),
        config_values=json.dumps(new_values),
        status="PENDING",
        submitted_by=current_user.id
    )
    db.add(audit_entry)
    db.commit()
    db.refresh(audit_entry)

    return ConfigAuditEntrySchema(
        version=next_version,
        parameterKey="all",
        oldValue="previous_version",
        newValue="queued_version",
        submittedAtUtc=audit_entry.submitted_at.strftime("%Y-%m-%d %H:%M:%S"),
        appliedAtUtc=None,
        status="PENDING",
        rejectionReason=None
    )

@router.get("/ea-installations/{installation_id}/configuration/history", response_model=List[ConfigAuditEntrySchema])
def get_configuration_history(
    installation_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    inst = db.query(EaInstallation).filter(
        EaInstallation.id == installation_id,
        EaInstallation.tenant_id == current_user.tenant_id
    ).first()
    if not inst:
        raise HTTPException(status_code=404, detail="Installation not found")

    history = db.query(EaConfigVersion).filter(
        EaConfigVersion.installation_id == installation_id
    ).order_by(EaConfigVersion.version.desc()).all()

    return [
        ConfigAuditEntrySchema(
            version=h.version,
            parameterKey=h.parameter_key,
            oldValue=h.old_value or "",
            newValue=h.new_value or "",
            submittedAtUtc=h.submitted_at.strftime("%Y-%m-%d %H:%M:%S"),
            appliedAtUtc=h.applied_at.strftime("%Y-%m-%d %H:%M:%S") if h.applied_at else None,
            status=h.status,
            rejectionReason=h.rejection_reason
        )
        for h in history
    ]

@router.get("/ea-installations/{installation_id}/presets", response_model=List[EaPresetSchema])
def get_presets(
    installation_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    inst = db.query(EaInstallation).filter(
        EaInstallation.id == installation_id,
        EaInstallation.tenant_id == current_user.tenant_id
    ).first()
    if not inst:
        raise HTTPException(status_code=404, detail="Installation not found")

    presets = db.query(EaPreset).filter(
        (EaPreset.ea_product_id == inst.ea_product_id) | (EaPreset.is_system_default == True)
    ).all()

    return [
        EaPresetSchema(
            id=p.id,
            eaProductId=p.ea_product_id,
            name=p.name,
            description=p.description,
            values=json.loads(p.values) if p.values else {},
            isSystemDefault=p.is_system_default
        )
        for p in presets
    ]

@router.post("/ea-installations/{installation_id}/presets", response_model=EaPresetSchema)
def save_preset(
    installation_id: str,
    preset_data: EaPresetSchema,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    inst = db.query(EaInstallation).filter(
        EaInstallation.id == installation_id,
        EaInstallation.tenant_id == current_user.tenant_id
    ).first()
    if not inst:
        raise HTTPException(status_code=404, detail="Installation not found")

    preset = EaPreset(
        id=str(uuid.uuid4()),
        ea_product_id=inst.ea_product_id,
        name=preset_data.name,
        description=preset_data.description,
        values=json.dumps(preset_data.values),
        is_system_default=False
    )
    db.add(preset)
    db.commit()
    db.refresh(preset)

    return EaPresetSchema(
        id=preset.id,
        eaProductId=preset.ea_product_id,
        name=preset.name,
        description=preset.description,
        values=preset_data.values,
        isSystemDefault=False
    )

@router.post("/ea-installations/{installation_id}/presets/{preset_id}/apply", response_model=ConfigAuditEntrySchema)
def apply_preset(
    installation_id: str,
    preset_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    preset = db.query(EaPreset).filter(EaPreset.id == preset_id).first()
    if not preset:
        raise HTTPException(status_code=404, detail="Preset not found")

    values = json.loads(preset.values) if preset.values else {}
    return update_installation_configuration(installation_id, values, current_user, db)
