"""Platform marketplace review. Admins decide whether a product is LISTED; they have no
ability to read-write, add, relax or unlock a developer's manifest parameters."""
import json
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.dependencies import require_roles
from backend.app.models import EaDeveloperAuditLog, EaProduct, EaProductVersion, User

router = APIRouter(prefix="/admin/ea-review", tags=["EA Marketplace Review"])
admin_only = require_roles(["ADMINISTRATOR", "SUPER_ADMINISTRATOR"])


class ReviewNote(BaseModel):
    note: Optional[str] = Field(default=None, max_length=1000)


def _product(db: Session, product_id: str) -> EaProduct:
    p = db.query(EaProduct).filter(EaProduct.id == product_id, EaProduct.developer_id.isnot(None)).first()
    if not p:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Developer product not found")
    return p


def _log(db: Session, admin: User, product: EaProduct, action: str, note: Optional[str]) -> None:
    db.add(EaDeveloperAuditLog(ea_product_id=product.id, actor_user_id=admin.id, actor_role=admin.role,
                               action=action, details=json.dumps({"note": note})))


@router.get("/pending")
def pending(admin: User = Depends(admin_only), db: Session = Depends(get_db)):
    rows = db.query(EaProduct).filter(EaProduct.listing_status == "PENDING_REVIEW").all()
    out = []
    for p in rows:
        v = db.query(EaProductVersion).filter(EaProductVersion.ea_product_id == p.id,
                                              EaProductVersion.status == "PUBLISHED")\
            .order_by(EaProductVersion.published_at.desc()).first()
        out.append({"id": p.id, "name": p.name, "product_code": p.product_code,
                    "version": v.version if v else None,
                    "manifest_hash": v.manifest_hash if v else None,
                    "binary_sha256": v.binary_sha256 if v else None})
    return out


@router.post("/{product_id}/approve")
def approve(product_id: str, body: ReviewNote = ReviewNote(), admin: User = Depends(admin_only),
            db: Session = Depends(get_db)):
    p = _product(db, product_id)
    if p.listing_status != "PENDING_REVIEW":
        raise HTTPException(status.HTTP_409_CONFLICT, f"Cannot approve from {p.listing_status}")
    p.listing_status, p.is_published = "APPROVED", True
    _log(db, admin, p, "LISTING_APPROVED", body.note)
    db.commit()
    return {"id": p.id, "listing_status": p.listing_status}


@router.post("/{product_id}/reject")
def reject(product_id: str, body: ReviewNote = ReviewNote(), admin: User = Depends(admin_only),
           db: Session = Depends(get_db)):
    p = _product(db, product_id)
    if p.listing_status != "PENDING_REVIEW":
        raise HTTPException(status.HTTP_409_CONFLICT, f"Cannot reject from {p.listing_status}")
    p.listing_status, p.is_published = "REJECTED", False
    _log(db, admin, p, "LISTING_REJECTED", body.note)
    db.commit()
    return {"id": p.id, "listing_status": p.listing_status}


@router.post("/{product_id}/suspend")
def suspend(product_id: str, body: ReviewNote = ReviewNote(), admin: User = Depends(admin_only),
            db: Session = Depends(get_db)):
    p = _product(db, product_id)
    if p.listing_status == "RETIRED":
        raise HTTPException(status.HTTP_409_CONFLICT, "Product is retired")
    p.listing_status, p.is_published = "SUSPENDED", False
    _log(db, admin, p, "LISTING_SUSPENDED_BY_PLATFORM", body.note)
    db.commit()
    return {"id": p.id, "listing_status": p.listing_status}
