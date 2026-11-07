from __future__ import annotations

import json
from datetime import datetime
from typing import Any

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from backend.app.config import settings
from backend.app.database import get_db
from backend.app.dependencies import get_current_user
from backend.app.models import EaInstallation, HostingJob, User
from backend.app.hosting import AwsEc2Provider, ProvisionRequest

router = APIRouter(prefix="/hosting", tags=["VPS and MT5 Hosting"])


class ProvisionRequestBody(BaseModel):
    region: str | None = Field(default=None, min_length=3, max_length=60)
    instance_type: str | None = Field(default=None, min_length=2, max_length=40)
    dry_run: bool | None = None


def _provider():
    if settings.HOSTING_PROVIDER == "aws_ec2":
        return AwsEc2Provider()
    raise HTTPException(status_code=500, detail="Unsupported hosting provider")


def _installation_for_user(db: Session, installation_id: str, user: User) -> EaInstallation:
    installation = db.query(EaInstallation).filter(
        EaInstallation.id == installation_id,
        EaInstallation.tenant_id == user.tenant_id,
    ).first()
    if not installation:
        raise HTTPException(status_code=404, detail="EA installation not found")
    return installation


@router.get("/config")
def hosting_config(current_user: User = Depends(get_current_user)):
    return {
        "provider": settings.HOSTING_PROVIDER,
        "dry_run": settings.HOSTING_DRY_RUN,
        "region": settings.AWS_REGION,
        "instance_type": settings.AWS_INSTANCE_TYPE,
        "live_provisioning_enabled": bool(settings.HOSTING_PROVIDER == "aws_ec2" and not settings.HOSTING_DRY_RUN),
        "status_note": "Dry-run requests are explicitly marked SIMULATED and do not create a cloud VM." if settings.HOSTING_DRY_RUN else "Live cloud provisioning is enabled for authorized administrators.",
    }


@router.post("/installations/{installation_id}/provision")
def provision_installation_host(
    installation_id: str,
    body: ProvisionRequestBody | None = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    installation = _installation_for_user(db, installation_id, current_user)
    body = body or ProvisionRequestBody()
    region = body.region or settings.AWS_REGION
    instance_type = body.instance_type or settings.AWS_INSTANCE_TYPE
    dry_run = settings.HOSTING_DRY_RUN if body.dry_run is None else body.dry_run

    if body.dry_run is False and current_user.role not in {"ADMINISTRATOR", "SUPER_ADMINISTRATOR"}:
        raise HTTPException(status_code=403, detail="Only administrators can request billable provisioning")

    job = HostingJob(
        tenant_id=current_user.tenant_id,
        installation_id=installation.id,
        provider=settings.HOSTING_PROVIDER,
        action="PROVISION_HOST",
        status="RUNNING",
        region=region,
        request_payload=json.dumps({"instance_type": instance_type, "dry_run": dry_run}),
        started_at=datetime.utcnow(),
    )
    db.add(job)
    db.flush()

    try:
        host = _provider().provision_host(ProvisionRequest(
            installation_id=installation.id,
            region=region,
            instance_type=instance_type,
            ami_id=settings.AWS_AMI_ID,
            subnet_id=settings.AWS_SUBNET_ID,
            security_group_id=settings.AWS_SECURITY_GROUP_ID,
            iam_instance_profile=settings.AWS_IAM_INSTANCE_PROFILE or None,
            dry_run=dry_run,
        ))
        job.status = host.status
        job.provider_instance_id = host.provider_instance_id
        job.result_payload = json.dumps(host.metadata)
        job.completed_at = datetime.utcnow()
        # The terminal/EA is not ready merely because a host exists.
        installation.runtime_status = "DISCONNECTED"
        installation.vps_instance_id = host.provider_instance_id
        db.commit()
        return {
            "job_id": job.id,
            "installation_id": installation.id,
            "provider": host.provider,
            "provider_instance_id": host.provider_instance_id,
            "status": job.status,
            "next_step": "Install and register the Windows host agent before MT5 activation.",
        }
    except Exception as exc:
        job.status = "FAILED"
        job.error_message = str(exc)[:1000]
        job.completed_at = datetime.utcnow()
        db.commit()
        raise HTTPException(status_code=502, detail="Hosting provider provisioning failed") from exc


@router.get("/installations/{installation_id}/jobs")
def list_installation_hosting_jobs(
    installation_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _installation_for_user(db, installation_id, current_user)
    jobs = db.query(HostingJob).filter(
        HostingJob.installation_id == installation_id,
        HostingJob.tenant_id == current_user.tenant_id,
    ).order_by(HostingJob.created_at.desc()).limit(50).all()
    return [
        {
            "id": job.id,
            "provider": job.provider,
            "action": job.action,
            "status": job.status,
            "region": job.region,
            "provider_instance_id": job.provider_instance_id,
            "error_message": job.error_message,
            "created_at": job.created_at.isoformat() if job.created_at else None,
            "completed_at": job.completed_at.isoformat() if job.completed_at else None,
        }
        for job in jobs
    ]
