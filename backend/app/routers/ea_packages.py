"""Authenticated, entitlement-checked EX5 downloads. No public URLs exist."""
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.responses import StreamingResponse
from sqlalchemy import or_
from sqlalchemy.orm import Session

from backend.app.config import settings
from backend.app.database import get_db
from backend.app.dependencies import get_current_user
from backend.app.ea_dev import manifest as mf
from backend.app.ea_dev import storage
from backend.app.models import (
    EaBinaryPackage, EaLicense, EaPackageDownloadLog, EaProduct, EaProductVersion, User,
)

router = APIRouter(tags=["EA Packages"])


def _deny(db: Session, version_id: str, user: User, reason: str, code: int = status.HTTP_403_FORBIDDEN):
    db.add(EaPackageDownloadLog(version_id=version_id, user_id=user.id, tenant_id=user.tenant_id,
                                allowed=False, reason=reason))
    db.commit()
    raise HTTPException(code, "Download not permitted")


@router.get("/ea-products/{product_id}/versions/{version_id}/download")
def download_package(product_id: str, version_id: str, user: User = Depends(get_current_user),
                     db: Session = Depends(get_db)):
    version = db.query(EaProductVersion).filter(
        EaProductVersion.id == version_id, EaProductVersion.ea_product_id == product_id).first()
    if not version:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Not found")
    product = db.query(EaProduct).filter(EaProduct.id == product_id).first()
    if version.status != "PUBLISHED":
        _deny(db, version.id, user, "version not published")

    is_owner = product.developer_id == user.id
    if not is_owner:
        if product.listing_status != "APPROVED":
            _deny(db, version.id, user, "product not listed")
        now = datetime.utcnow()
        lic = db.query(EaLicense).filter(
            EaLicense.tenant_id == user.tenant_id, EaLicense.ea_product_id == product.id,
            EaLicense.status == "ACTIVE", or_(EaLicense.expires_at.is_(None), EaLicense.expires_at > now),
        ).first()
        if not lic:
            _deny(db, version.id, user, "no active licence")

    binary = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == version.id).first()
    if not binary:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Not found")
    # Integrity: signature must verify and the stored file must still match the signed checksum.
    sig_ok = mf.verify_release(settings.MANIFEST_SIGNING_KEY, version.signature or "", product.id,
                               version.id, product.developer_id or "", version.manifest_hash or "",
                               version.binary_sha256 or "")
    if not sig_ok or storage.sha256_of(binary.storage_key) != version.binary_sha256:
        _deny(db, version.id, user, "integrity check failed", status.HTTP_409_CONFLICT)

    db.add(EaPackageDownloadLog(version_id=version.id, user_id=user.id, tenant_id=user.tenant_id,
                                allowed=True, reason="owner" if is_owner else "licensed"))
    db.commit()
    return StreamingResponse(
        storage.open_binary(binary.storage_key), media_type="application/octet-stream",
        headers={"Content-Disposition": f'attachment; filename="{product.product_code}-{version.version}.ex5"',
                 "X-Content-SHA256": version.binary_sha256, "Cache-Control": "no-store"})
