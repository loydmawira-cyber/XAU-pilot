"""Developer-owned EA products, versions, binaries and input manifests.

Ownership is enforced on every route: a product is only visible to the developer
who created it (others, including administrators, get 404). There is deliberately
NO route anywhere that lets a platform admin edit a developer's manifest.
"""
import json
import secrets
from datetime import datetime
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from pydantic import BaseModel, Field
from sqlalchemy import func
from sqlalchemy.orm import Session

from backend.app.config import settings
from backend.app.database import get_db
from backend.app.dependencies import get_current_user
from backend.app.ea_dev import manifest as mf
from backend.app.ea_dev import storage
from backend.app.models import (
    DeveloperProfile, EaBinaryPackage, EaExtractionJob, EaConfigVersion, EaDeveloperAuditLog,
    EaInputManifest, EaInstallation, EaProduct, EaProductVersion, User,
)

router = APIRouter(prefix="/developer", tags=["EA Developer"])

SEMVER = r"^\d+\.\d+\.\d+$"


# ------------------------------------------------------------------ schemas
class DeveloperRegisterRequest(BaseModel):
    display_name: str = Field(min_length=2, max_length=120)
    contact_email: str = Field(min_length=3, max_length=255)


class ProductCreateRequest(BaseModel):
    product_code: str = Field(pattern=r"^[A-Za-z0-9][A-Za-z0-9_-]{2,39}$")
    name: str = Field(min_length=2, max_length=120)
    description: str = Field(min_length=10, max_length=4000)
    strategy_category: str = Field(min_length=2, max_length=60)
    risk_disclosures: str = Field(min_length=10, max_length=4000)
    monthly_price_usd: float = Field(ge=0, le=100000)
    annual_price_usd: float = Field(ge=0, le=1000000)
    lifetime_price_usd: float = Field(ge=0, le=10000000)
    min_deposit_usd: float = Field(default=1000.0, ge=0)


class ProductUpdateRequest(BaseModel):
    name: Optional[str] = Field(default=None, min_length=2, max_length=120)
    description: Optional[str] = Field(default=None, min_length=10, max_length=4000)
    strategy_category: Optional[str] = Field(default=None, min_length=2, max_length=60)
    risk_disclosures: Optional[str] = Field(default=None, min_length=10, max_length=4000)
    monthly_price_usd: Optional[float] = Field(default=None, ge=0, le=100000)
    annual_price_usd: Optional[float] = Field(default=None, ge=0, le=1000000)
    lifetime_price_usd: Optional[float] = Field(default=None, ge=0, le=10000000)


class VersionCreateRequest(BaseModel):
    version: str = Field(pattern=SEMVER)
    release_notes: Optional[str] = Field(default=None, max_length=4000)


# ------------------------------------------------------------------ helpers
def _now() -> datetime:
    return datetime.utcnow()


def _audit(db: Session, user: User, product_id: Optional[str], version_id: Optional[str],
           action: str, details: Optional[Dict[str, Any]] = None) -> None:
    db.add(EaDeveloperAuditLog(
        ea_product_id=product_id, version_id=version_id, actor_user_id=user.id,
        actor_role=user.role, action=action, details=json.dumps(details or {}),
    ))


def _developer(db: Session, user: User) -> DeveloperProfile:
    profile = db.query(DeveloperProfile).filter(DeveloperProfile.user_id == user.id).first()
    if not profile:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Register as an EA developer first")
    if profile.status != "ACTIVE":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Developer account is suspended")
    return profile


def _own_product(db: Session, product_id: str, user: User) -> EaProduct:
    _developer(db, user)
    product = db.query(EaProduct).filter(
        EaProduct.id == product_id, EaProduct.developer_id == user.id).first()
    if not product:  # 404 (not 403) so other developers' product ids cannot be probed
        raise HTTPException(status.HTTP_404_NOT_FOUND, "EA product not found")
    return product


def _own_version(db: Session, product: EaProduct, version_id: str) -> EaProductVersion:
    v = db.query(EaProductVersion).filter(
        EaProductVersion.id == version_id, EaProductVersion.ea_product_id == product.id).first()
    if not v:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "EA version not found")
    return v


def _require_draft(version: EaProductVersion) -> None:
    if version.status != "DRAFT":
        raise HTTPException(status.HTTP_409_CONFLICT,
                            "Published versions are immutable. Create a new version instead.")


def _product_dict(p: EaProduct) -> Dict[str, Any]:
    return {
        "id": p.id, "product_code": p.product_code, "name": p.name, "description": p.description,
        "strategy_category": p.strategy_category, "current_version": p.current_version,
        "listing_status": p.listing_status, "is_published": p.is_published,
        "monthly_price_usd": p.monthly_price_usd, "annual_price_usd": p.annual_price_usd,
        "lifetime_price_usd": p.lifetime_price_usd,
        "created_at": p.created_at.isoformat() if p.created_at else None,
    }


def _version_dict(db: Session, v: EaProductVersion) -> Dict[str, Any]:
    binary = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == v.id).first()
    manifest = db.query(EaInputManifest).filter(EaInputManifest.version_id == v.id).first()
    return {
        "id": v.id, "version": v.version, "status": v.status, "release_notes": v.release_notes,
        "manifest_hash": v.manifest_hash, "binary_sha256": v.binary_sha256,
        "published_at": v.published_at.isoformat() if v.published_at else None,
        "published_by": v.published_by,
        "binary": None if not binary else {
            "filename": binary.original_filename, "sha256": binary.sha256,
            "size_bytes": binary.size_bytes, "scan_status": binary.scan_status},
        "manifest": None if not manifest else {
            "is_valid": manifest.is_valid, "manifest_hash": manifest.manifest_hash,
            "errors": json.loads(manifest.validation_errors)},
    }


def _revalidate(product: EaProduct, version: EaProductVersion, user: User,
                canonical: str) -> List[str]:
    return mf.validate_manifest(
        json.loads(canonical),
        expected_product_ref=(product.id, product.product_code),
        expected_version=version.version,
        expected_developer_id=user.id,
    )


# ------------------------------------------------------------------ registration & products
@router.post("/register", status_code=status.HTTP_201_CREATED)
def register_developer(req: DeveloperRegisterRequest, user: User = Depends(get_current_user),
                       db: Session = Depends(get_db)):
    if db.query(DeveloperProfile).filter(DeveloperProfile.user_id == user.id).first():
        raise HTTPException(status.HTTP_409_CONFLICT, "Already registered as a developer")
    profile = DeveloperProfile(user_id=user.id, display_name=req.display_name.strip(),
                               contact_email=req.contact_email.strip())
    db.add(profile)
    _audit(db, user, None, None, "DEVELOPER_REGISTERED")
    db.commit()
    return {"developer_id": user.id, "display_name": profile.display_name, "status": profile.status}


@router.post("/ea-products", status_code=status.HTTP_201_CREATED)
def create_product(req: ProductCreateRequest, user: User = Depends(get_current_user),
                   db: Session = Depends(get_db)):
    _developer(db, user)
    if db.query(EaProduct).filter(EaProduct.product_code == req.product_code).first():
        raise HTTPException(status.HTTP_409_CONFLICT, "product_code already exists")
    product = EaProduct(
        **req.model_dump(), current_version="0.0.0",
        default_magic_number=800000 + secrets.randbelow(100000),
        developer_id=user.id, listing_status="DRAFT", is_published=False,
    )
    db.add(product)
    db.flush()
    _audit(db, user, product.id, None, "PRODUCT_CREATED", {"product_code": product.product_code})
    db.commit()
    return _product_dict(product)


@router.get("/ea-products")
def list_products(user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    _developer(db, user)
    rows = db.query(EaProduct).filter(EaProduct.developer_id == user.id).order_by(EaProduct.created_at.desc()).all()
    return [_product_dict(p) for p in rows]


@router.get("/ea-products/{product_id}")
def get_product(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return _product_dict(_own_product(db, product_id, user))


@router.put("/ea-products/{product_id}")
def update_product(product_id: str, req: ProductUpdateRequest, user: User = Depends(get_current_user),
                   db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    if product.listing_status == "RETIRED":
        raise HTTPException(status.HTTP_409_CONFLICT, "Retired products cannot be edited")
    changes = req.model_dump(exclude_unset=True, exclude_none=True)
    for k, v in changes.items():
        setattr(product, k, v)
    _audit(db, user, product.id, None, "PRODUCT_UPDATED", {"fields": sorted(changes)})
    db.commit()
    return _product_dict(product)


@router.post("/ea-products/{product_id}/submit-for-review")
def submit_for_review(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    if product.listing_status not in ("DRAFT", "REJECTED"):
        raise HTTPException(status.HTTP_409_CONFLICT, f"Cannot submit from {product.listing_status}")
    has_release = db.query(EaProductVersion).filter(
        EaProductVersion.ea_product_id == product.id, EaProductVersion.status == "PUBLISHED").first()
    if not has_release:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Publish at least one version first")
    product.listing_status = "PENDING_REVIEW"
    _audit(db, user, product.id, None, "SUBMITTED_FOR_REVIEW")
    db.commit()
    return _product_dict(product)


@router.post("/ea-products/{product_id}/suspend")
def suspend_product(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    if product.listing_status in ("RETIRED", "SUSPENDED"):
        raise HTTPException(status.HTTP_409_CONFLICT, f"Already {product.listing_status}")
    product.listing_status, product.is_published = "SUSPENDED", False
    _audit(db, user, product.id, None, "PRODUCT_SUSPENDED_BY_DEVELOPER")
    db.commit()
    return _product_dict(product)


@router.post("/ea-products/{product_id}/retire")
def retire_product(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    if product.listing_status == "RETIRED":
        raise HTTPException(status.HTTP_409_CONFLICT, "Already retired")
    product.listing_status, product.is_published = "RETIRED", False
    db.query(EaProductVersion).filter(
        EaProductVersion.ea_product_id == product.id, EaProductVersion.status == "PUBLISHED"
    ).update({"status": "RETIRED"})
    _audit(db, user, product.id, None, "PRODUCT_RETIRED")
    db.commit()
    return _product_dict(product)


# ------------------------------------------------------------------ versions
@router.post("/ea-products/{product_id}/versions", status_code=status.HTTP_201_CREATED)
def create_version(product_id: str, req: VersionCreateRequest, user: User = Depends(get_current_user),
                   db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    if product.listing_status == "RETIRED":
        raise HTTPException(status.HTTP_409_CONFLICT, "Retired products cannot get new versions")
    if db.query(EaProductVersion).filter(
            EaProductVersion.ea_product_id == product.id, EaProductVersion.version == req.version).first():
        raise HTTPException(status.HTTP_409_CONFLICT, "That version already exists")
    v = EaProductVersion(ea_product_id=product.id, version=req.version,
                         release_notes=req.release_notes, created_by=user.id)
    db.add(v)
    db.flush()
    _audit(db, user, product.id, v.id, "VERSION_CREATED", {"version": v.version})
    db.commit()
    return _version_dict(db, v)


@router.get("/ea-products/{product_id}/versions")
def list_versions(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    rows = db.query(EaProductVersion).filter(EaProductVersion.ea_product_id == product.id)\
        .order_by(EaProductVersion.created_at.desc()).all()
    return [_version_dict(db, v) for v in rows]


@router.post("/ea-products/{product_id}/versions/{version_id}/upload-binary")
def upload_binary(product_id: str, version_id: str, file: UploadFile = File(...),
                  user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    version = _own_version(db, product, version_id)
    _require_draft(version)
    try:
        stored = storage.save_ex5(product.id, version.id, file.filename or "", file.file)
    except storage.StorageError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, str(exc))
    scan_status, scan_detail = storage.scan_binary(stored.storage_key)
    if scan_status == "INFECTED":
        storage.delete_binary(stored.storage_key)
        _audit(db, user, product.id, version.id, "BINARY_REJECTED_MALWARE", {"detail": scan_detail})
        db.commit()
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "File failed malware scan")
    existing = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == version.id).first()
    if existing:
        storage.delete_binary(existing.storage_key)
        db.delete(existing)
        db.flush()
    db.add(EaBinaryPackage(
        version_id=version.id, storage_key=stored.storage_key,
        original_filename=(file.filename or "")[:200], sha256=stored.sha256,
        size_bytes=stored.size_bytes, scan_status=scan_status, uploaded_by=user.id))
    # Queue MT5 inspection automatically; the separate Windows worker claims this job.
    # Re-uploading replaces the prior draft binary and its prior extraction result.
    existing_job = db.query(EaExtractionJob).filter(EaExtractionJob.version_id == version.id).first()
    if existing_job:
        db.delete(existing_job)
        db.flush()
    db.add(EaExtractionJob(
        product_id=product.id, version_id=version.id, binary_sha256=stored.sha256, status="QUEUED"))
    _audit(db, user, product.id, version.id, "BINARY_UPLOADED",
           {"sha256": stored.sha256, "size": stored.size_bytes, "scan": scan_status,
            "extraction": "QUEUED"})
    db.commit()
    return _version_dict(db, version)


@router.post("/ea-products/{product_id}/versions/{version_id}/upload-manifest")
def upload_manifest(product_id: str, version_id: str, file: UploadFile = File(...),
                    user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    version = _own_version(db, product, version_id)
    _require_draft(version)
    raw = file.file.read(mf.MAX_MANIFEST_BYTES + 1)
    try:
        data = mf.parse_manifest_bytes(raw)
    except mf.ManifestError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, exc.errors)
    errors = mf.validate_manifest(
        data, expected_product_ref=(product.id, product.product_code),
        expected_version=version.version, expected_developer_id=user.id)
    canonical = mf.canonical_json(data)
    row = db.query(EaInputManifest).filter(EaInputManifest.version_id == version.id).first()
    if row is None:
        row = EaInputManifest(version_id=version.id, uploaded_by=user.id)
        db.add(row)
    row.schema_version = str(data.get("schema_version", ""))[:10]
    row.canonical_json = canonical
    row.manifest_hash = mf.manifest_hash(canonical)
    row.validation_errors = json.dumps(errors)
    row.is_valid = not errors
    row.uploaded_by, row.uploaded_at = user.id, _now()
    _audit(db, user, product.id, version.id, "MANIFEST_UPLOADED",
           {"hash": row.manifest_hash, "valid": row.is_valid, "error_count": len(errors)})
    db.commit()
    return _version_dict(db, version)


@router.post("/ea-products/{product_id}/versions/{version_id}/validate")
def validate_version(product_id: str, version_id: str, user: User = Depends(get_current_user),
                     db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    version = _own_version(db, product, version_id)
    row = db.query(EaInputManifest).filter(EaInputManifest.version_id == version.id).first()
    if not row:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Upload a manifest first")
    errors = _revalidate(product, version, user, row.canonical_json)
    if version.status == "DRAFT":
        row.validation_errors, row.is_valid = json.dumps(errors), not errors
        db.commit()
    return {"valid": not errors, "errors": errors, "manifest_hash": row.manifest_hash}


@router.get("/ea-products/{product_id}/versions/{version_id}/preview")
def preview_customer_settings(product_id: str, version_id: str, user: User = Depends(get_current_user),
                              db: Session = Depends(get_db)):
    """Exactly what a customer will be shown for this version (hidden parameters omitted)."""
    product = _own_product(db, product_id, user)
    version = _own_version(db, product, version_id)
    row = db.query(EaInputManifest).filter(EaInputManifest.version_id == version.id).first()
    if not row:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Upload a manifest first")
    params = mf.customer_view(json.loads(row.canonical_json))
    editable = [p for p in params if p["customer_editable"]]
    return {
        "parameters": params,
        "message": None if editable else
        "The developer has not enabled customer-adjustable settings for this EA.",
    }


@router.post("/ea-products/{product_id}/versions/{version_id}/publish")
def publish_version(product_id: str, version_id: str, user: User = Depends(get_current_user),
                    db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    version = _own_version(db, product, version_id)
    _require_draft(version)
    if product.listing_status in ("RETIRED", "SUSPENDED"):
        raise HTTPException(status.HTTP_409_CONFLICT, f"Product is {product.listing_status}")
    binary = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == version.id).first()
    row = db.query(EaInputManifest).filter(EaInputManifest.version_id == version.id).first()
    problems = []
    if not binary:
        problems.append("upload the EX5 binary")
    elif binary.scan_status != "CLEAN":
        problems.append(f"binary scan must be CLEAN before publication (current status: {binary.scan_status})")
    if not row:
        problems.append("upload the input manifest")
    if problems:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, {"must": problems})
    errors = _revalidate(product, version, user, row.canonical_json)
    if errors:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, {"manifest_errors": errors})
    if storage.sha256_of(binary.storage_key) != binary.sha256:
        raise HTTPException(status.HTTP_409_CONFLICT, "Stored binary does not match its checksum")
    if len(settings.MANIFEST_SIGNING_KEY) < 32:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE,
                            "Manifest signing key is not configured on the server")
    m_hash = mf.manifest_hash(row.canonical_json)
    version.manifest_hash, version.binary_sha256 = m_hash, binary.sha256
    version.signature = mf.sign_release(
        settings.MANIFEST_SIGNING_KEY, product.id, version.id, user.id, m_hash, binary.sha256)
    version.published_by, version.published_at, version.status = user.id, _now(), "PUBLISHED"
    product.current_version = version.version
    _audit(db, user, product.id, version.id, "VERSION_PUBLISHED",
           {"version": version.version, "manifest_hash": m_hash, "binary_sha256": binary.sha256})
    db.commit()
    return _version_dict(db, version)


# ------------------------------------------------------------------ analytics (no customer identities)
@router.get("/ea-products/{product_id}/analytics")
def analytics(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    by_state = dict(db.query(EaInstallation.runtime_status, func.count(EaInstallation.id))
                    .filter(EaInstallation.ea_product_id == product.id)
                    .group_by(EaInstallation.runtime_status).all())
    return {"installations_total": sum(by_state.values()), "installations_by_runtime_status": by_state,
            "versions": db.query(func.count(EaProductVersion.id))
            .filter(EaProductVersion.ea_product_id == product.id).scalar()}


@router.get("/ea-products/{product_id}/installations")
def installations(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    rows = db.query(EaInstallation).filter(EaInstallation.ea_product_id == product.id)\
        .order_by(EaInstallation.created_at.desc()).limit(200).all()
    # Anonymised: no tenant, account, broker, token or parameter values are exposed.
    return [{"installation_ref": i.id[:8], "symbol": i.symbol, "timeframe": i.timeframe,
             "runtime_status": i.runtime_status, "config_status": i.config_status,
             "created_at": i.created_at.isoformat() if i.created_at else None} for i in rows]


@router.get("/ea-products/{product_id}/configuration-activity")
def configuration_activity(product_id: str, user: User = Depends(get_current_user),
                           db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    rows = db.query(EaConfigVersion).join(EaInstallation, EaInstallation.id == EaConfigVersion.installation_id)\
        .filter(EaInstallation.ea_product_id == product.id)\
        .order_by(EaConfigVersion.submitted_at.desc()).limit(200).all()
    return [{"installation_ref": r.installation_id[:8], "config_version": r.version, "status": r.status,
             "submitted_at": r.submitted_at.isoformat() if r.submitted_at else None,
             "applied_at": r.applied_at.isoformat() if r.applied_at else None} for r in rows]


@router.get("/ea-products/{product_id}/audit")
def audit_history(product_id: str, user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    product = _own_product(db, product_id, user)
    rows = db.query(EaDeveloperAuditLog).filter(EaDeveloperAuditLog.ea_product_id == product.id)\
        .order_by(EaDeveloperAuditLog.created_at.desc()).limit(200).all()
    return [{"action": r.action, "actor_role": r.actor_role, "version_id": r.version_id,
             "details": json.loads(r.details), "created_at": r.created_at.isoformat()} for r in rows]
