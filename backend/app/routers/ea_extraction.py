"""Private API for checksum-bound MT5 extraction jobs.

The Windows worker is an independently deployed service. This API never executes
EX5 binaries and never guesses input definitions.
"""
import hashlib
import json
import secrets
from datetime import datetime, timedelta
from typing import Any, Dict, List

from fastapi import APIRouter, Depends, Header, HTTPException, status
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from backend.app.config import settings
from backend.app.database import get_db
from backend.app.ea_dev import storage
from backend.app.models import EaBinaryPackage, EaExtractionJob, EaProduct, EaProductVersion, User
from backend.app.dependencies import get_current_user

router = APIRouter(tags=["EA extraction worker"])
LEASE_MINUTES = 10
MAX_INPUTS = 200


def _worker(authorization: str | None) -> None:
    if not settings.EA_EXTRACTION_WORKER_TOKEN:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Extraction worker is not configured")
    expected = "Bearer " + settings.EA_EXTRACTION_WORKER_TOKEN
    if not authorization or not secrets.compare_digest(authorization, expected):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid worker credentials")


def _job_view(job: EaExtractionJob) -> Dict[str, Any]:
    return {
        "job_id": job.id, "status": job.status, "binary_sha256": job.binary_sha256,
        "created_at": job.created_at.isoformat() if job.created_at else None,
        "updated_at": job.updated_at.isoformat() if job.updated_at else None,
        "error_code": job.error_code,
        "inputs": json.loads(job.result_json).get("inputs", []) if job.status == "COMPLETE" and job.result_json else None,
    }


class ClaimRequest(BaseModel):
    worker_id: str = Field(min_length=2, max_length=100)


class ExtractionInput(BaseModel):
    key: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z_][A-Za-z0-9_]*$")
    display_name: str = Field(min_length=1, max_length=120)
    type: str = Field(pattern=r"^(bool|int|double|string|enum|unresolved)$")
    default: Any = None
    choices: List[str] = Field(default_factory=list, max_length=100)


class CompleteRequest(BaseModel):
    job_id: str
    lease_token: str = Field(min_length=32, max_length=128)
    binary_sha256: str = Field(pattern=r"^[a-f0-9]{64}$")
    terminal_build: str = Field(min_length=1, max_length=40)
    extractor_version: str = Field(min_length=1, max_length=40)
    inputs: List[ExtractionInput] = Field(max_length=MAX_INPUTS)
    unresolved: List[str] = Field(default_factory=list, max_length=MAX_INPUTS)


@router.post("/internal/ea-extraction/claim")
def claim_job(req: ClaimRequest, authorization: str | None = Header(default=None),
              db: Session = Depends(get_db)):
    _worker(authorization)
    now = datetime.utcnow()
    # Expired leases are safely returned to the queue.
    db.query(EaExtractionJob).filter(
        EaExtractionJob.status == "RUNNING",
        EaExtractionJob.lease_expires_at < now
    ).update({"status": "QUEUED", "lease_token_hash": None, "lease_expires_at": None, "worker_id": None})
    job = db.query(EaExtractionJob).filter(EaExtractionJob.status == "QUEUED")\
        .order_by(EaExtractionJob.created_at.asc()).with_for_update(skip_locked=True).first()
    if not job:
        db.commit()
        return {"job": None}
    token = secrets.token_urlsafe(48)
    job.status = "RUNNING"
    job.worker_id = req.worker_id
    job.lease_token_hash = hashlib.sha256(token.encode()).hexdigest()
    job.lease_expires_at = now + timedelta(minutes=LEASE_MINUTES)
    db.commit()
    return {
        "job_id": job.id, "product_id": job.product_id, "version_id": job.version_id,
        "binary_sha256": job.binary_sha256, "lease_token": token,
        "lease_expires_at": job.lease_expires_at.isoformat(),
        "download_path": f"/internal/ea-extraction/jobs/{job.id}/binary",
    }


@router.get("/internal/ea-extraction/jobs/{job_id}/binary")
def download_job_binary(job_id: str, authorization: str | None = Header(default=None),
                        x_extraction_lease: str | None = Header(default=None),
                        db: Session = Depends(get_db)):
    _worker(authorization)
    job = db.query(EaExtractionJob).filter(EaExtractionJob.id == job_id).first()
    if not job or job.status != "RUNNING" or not job.lease_expires_at or job.lease_expires_at < datetime.utcnow():
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Active extraction job not found")
    if not x_extraction_lease or not secrets.compare_digest(
        hashlib.sha256(x_extraction_lease.encode()).hexdigest(), job.lease_token_hash or ""
    ):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid extraction lease")
    binary = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == job.version_id).first()
    if not binary or binary.sha256 != job.binary_sha256:
        raise HTTPException(status.HTTP_409_CONFLICT, "Uploaded binary no longer matches extraction job")
    if storage.sha256_of(binary.storage_key) != job.binary_sha256:
        raise HTTPException(status.HTTP_409_CONFLICT, "Stored binary checksum mismatch")
    stream = storage.open_binary(binary.storage_key)
    return StreamingResponse(stream, media_type="application/octet-stream",
                             headers={"Content-Disposition": 'attachment; filename="ea-package.ex5"'})


@router.post("/internal/ea-extraction/complete")
def complete_job(req: CompleteRequest, authorization: str | None = Header(default=None),
                 db: Session = Depends(get_db)):
    _worker(authorization)
    job = db.query(EaExtractionJob).filter(EaExtractionJob.id == req.job_id).with_for_update().first()
    if not job or job.status != "RUNNING" or not job.lease_expires_at or job.lease_expires_at < datetime.utcnow():
        raise HTTPException(status.HTTP_409_CONFLICT, "Extraction lease is no longer active")
    supplied_hash = hashlib.sha256(req.lease_token.encode()).hexdigest()
    if not secrets.compare_digest(supplied_hash, job.lease_token_hash or ""):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid extraction lease")
    if not secrets.compare_digest(req.binary_sha256, job.binary_sha256):
        job.status, job.error_code = "FAILED", "BINARY_CHECKSUM_MISMATCH"
        db.commit()
        raise HTTPException(status.HTTP_409_CONFLICT, "Worker inspected a different binary")
    keys = [item.key for item in req.inputs if item.type != "unresolved"]
    if len(keys) != len(set(keys)):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Duplicate input keys returned by worker")
    result = {
        "terminal_build": req.terminal_build, "extractor_version": req.extractor_version,
        "inputs": [item.model_dump() for item in req.inputs],
        "unresolved": req.unresolved,
    }
    job.result_json = json.dumps(result, separators=(",", ":"), ensure_ascii=False)
    job.status = "COMPLETE"
    job.lease_token_hash = None
    job.lease_expires_at = None
    db.commit()
    return {"job_id": job.id, "status": job.status, "review_required": True}


@router.get("/api/v1/developer/ea-products/{product_id}/versions/{version_id}/extraction")
def developer_extraction_status(product_id: str, version_id: str,
                                user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = db.query(EaProduct).filter(EaProduct.id == product_id, EaProduct.developer_id == user.id).first()
    if not product:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "EA product not found")
    version = db.query(EaProductVersion).filter(
        EaProductVersion.id == version_id, EaProductVersion.ea_product_id == product.id).first()
    if not version:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "EA version not found")
    job = db.query(EaExtractionJob).filter(EaExtractionJob.version_id == version.id).first()
    if not job:
        return {"status": "NOT_QUEUED"}
    return _job_view(job)
