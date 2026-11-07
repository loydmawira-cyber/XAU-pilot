"""Platform administration, privacy-controlled user directory and support messaging.

Only SUPER_ADMINISTRATOR can inspect the platform-wide user directory or manage admins.
ADMINISTRATOR and SUPER_ADMINISTRATOR can review EAs and respond to support conversations.
Ordinary users can only access conversations they created.
"""
from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.dependencies import require_roles, get_current_user
from backend.app.models import User, Tenant, AuditLog, SupportConversation, SupportMessage

router = APIRouter(tags=["Platform Administration and Support"])
super_admin = require_roles(["SUPER_ADMINISTRATOR"])
admin_roles = require_roles(["ADMINISTRATOR", "SUPER_ADMINISTRATOR"])


class AdminStatusUpdate(BaseModel):
    is_active: bool


class SupportConversationCreate(BaseModel):
    subject: str = Field(min_length=3, max_length=180)
    message: str = Field(min_length=1, max_length=8000)


class SupportMessageCreate(BaseModel):
    body: str = Field(min_length=1, max_length=8000)


class SupportStatusUpdate(BaseModel):
    status: str = Field(pattern="^(OPEN|CLOSED)$")


def _user_summary(user: User) -> dict:
    """Never serialize password hashes, MFA secrets, tokens or internal credentials."""
    return {
        "id": user.id,
        "full_name": user.full_name,
        "email": user.email,
        "role": user.role,
        "tenant_id": user.tenant_id,
        "is_active": bool(user.is_active),
        "mfa_enabled": bool(user.mfa_enabled),
        "created_at": user.created_at.isoformat() if user.created_at else None,
    }


def _conversation_dict(c: SupportConversation, owner: User) -> dict:
    return {
        "id": c.id,
        "subject": c.subject,
        "status": c.status,
        "owner_user_id": c.owner_user_id,
        "owner_name": owner.full_name,
        "owner_email": owner.email,
        "assigned_admin_id": c.assigned_admin_id,
        "created_at": c.created_at.isoformat() if c.created_at else None,
        "updated_at": c.updated_at.isoformat() if c.updated_at else None,
    }


@router.get("/admin/users")
def platform_user_directory(current: User = Depends(super_admin), db: Session = Depends(get_db)):
    """Platform-wide user information is intentionally Super Admin-only."""
    users = db.query(User).order_by(User.created_at.desc()).limit(1000).all()
    return [_user_summary(u) for u in users]


@router.get("/admin/administrators")
def administrator_directory(current: User = Depends(super_admin), db: Session = Depends(get_db)):
    users = db.query(User).filter(User.role.in_(["ADMINISTRATOR", "SUPPORT_AGENT"])).order_by(User.created_at.desc()).all()
    return [_user_summary(u) for u in users]


@router.patch("/admin/administrators/{user_id}/status")
def update_administrator_status(user_id: str, body: AdminStatusUpdate,
                                current: User = Depends(super_admin), db: Session = Depends(get_db)):
    user = db.query(User).filter(User.id == user_id).first()
    if not user or user.role not in ("ADMINISTRATOR", "SUPPORT_AGENT"):
        raise HTTPException(status_code=404, detail="Administrator not found")
    if user.id == current.id:
        raise HTTPException(status_code=400, detail="You cannot deactivate your own Super Admin account")
    user.is_active = body.is_active
    db.add(AuditLog(tenant_id=current.tenant_id, user_id=current.id,
                    action="ADMIN_ACTIVATED" if body.is_active else "ADMIN_DEACTIVATED",
                    target_type="USER", target_id=user.id,
                    details='{"role":"%s"}' % user.role))
    db.commit()
    return _user_summary(user)


@router.get("/support/conversations")
def list_support_conversations(current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    q = db.query(SupportConversation)
    if current.role not in ("ADMINISTRATOR", "SUPER_ADMINISTRATOR", "SUPPORT_AGENT"):
        q = q.filter(SupportConversation.owner_user_id == current.id)
    rows = q.order_by(SupportConversation.updated_at.desc()).limit(200).all()
    result = []
    for c in rows:
        owner = db.query(User).filter(User.id == c.owner_user_id).first()
        if owner:
            result.append(_conversation_dict(c, owner))
    return result


@router.post("/support/conversations", status_code=status.HTTP_201_CREATED)
def create_support_conversation(body: SupportConversationCreate,
                                current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    if current.role in ("ADMINISTRATOR", "SUPER_ADMINISTRATOR", "SUPPORT_AGENT"):
        raise HTTPException(status_code=403, detail="Administrators should reply to an existing owner conversation")
    c = SupportConversation(owner_user_id=current.id, subject=body.subject.strip(), status="OPEN")
    db.add(c)
    db.flush()
    db.add(SupportMessage(conversation_id=c.id, sender_user_id=current.id, body=body.message.strip()))
    db.add(AuditLog(tenant_id=current.tenant_id, user_id=current.id, action="SUPPORT_CONVERSATION_CREATED",
                    target_type="SUPPORT_CONVERSATION", target_id=c.id, details="{}"))
    db.commit()
    db.refresh(c)
    return _conversation_dict(c, current)


@router.get("/support/conversations/{conversation_id}")
def get_support_conversation(conversation_id: str, current: User = Depends(get_current_user),
                             db: Session = Depends(get_db)):
    c = db.query(SupportConversation).filter(SupportConversation.id == conversation_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="Support conversation not found")
    is_admin = current.role in ("ADMINISTRATOR", "SUPER_ADMINISTRATOR", "SUPPORT_AGENT")
    if not is_admin and c.owner_user_id != current.id:
        raise HTTPException(status_code=404, detail="Support conversation not found")
    owner = db.query(User).filter(User.id == c.owner_user_id).first()
    messages = db.query(SupportMessage).filter(SupportMessage.conversation_id == c.id).order_by(SupportMessage.created_at.asc()).all()
    return {
        "conversation": _conversation_dict(c, owner),
        "messages": [{"id": m.id, "sender_user_id": m.sender_user_id,
                      "sender_name": (db.query(User.full_name).filter(User.id == m.sender_user_id).scalar() or "User"),
                      "body": m.body, "created_at": m.created_at.isoformat() if m.created_at else None}
                     for m in messages]
    }


@router.post("/support/conversations/{conversation_id}/messages", status_code=status.HTTP_201_CREATED)
def send_support_message(conversation_id: str, body: SupportMessageCreate,
                         current: User = Depends(get_current_user), db: Session = Depends(get_db)):
    c = db.query(SupportConversation).filter(SupportConversation.id == conversation_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="Support conversation not found")
    is_admin = current.role in ("ADMINISTRATOR", "SUPER_ADMINISTRATOR", "SUPPORT_AGENT")
    if not is_admin and c.owner_user_id != current.id:
        raise HTTPException(status_code=404, detail="Support conversation not found")
    if c.status == "CLOSED":
        raise HTTPException(status_code=409, detail="This support conversation is closed")
    if is_admin and c.assigned_admin_id is None:
        c.assigned_admin_id = current.id
    msg = SupportMessage(conversation_id=c.id, sender_user_id=current.id, body=body.body.strip())
    c.updated_at = datetime.utcnow()
    db.add(msg)
    db.commit()
    db.refresh(msg)
    return {"id": msg.id, "conversation_id": c.id, "sender_user_id": current.id,
            "body": msg.body, "created_at": msg.created_at.isoformat() if msg.created_at else None}


@router.patch("/support/conversations/{conversation_id}/status")
def set_support_status(conversation_id: str, body: SupportStatusUpdate,
                       current: User = Depends(admin_roles), db: Session = Depends(get_db)):
    c = db.query(SupportConversation).filter(SupportConversation.id == conversation_id).first()
    if not c:
        raise HTTPException(status_code=404, detail="Support conversation not found")
    c.status = body.status
    c.updated_at = datetime.utcnow()
    db.commit()
    return {"id": c.id, "status": c.status}
