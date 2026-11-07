from fastapi import APIRouter, Depends, HTTPException, status, Header
from sqlalchemy.orm import Session
from datetime import datetime, timedelta
import uuid
import secrets
from sqlalchemy.exc import IntegrityError

from backend.app.database import get_db
from backend.app.models import User, Tenant, RevokedToken, AuditLog
from backend.app.schemas import (
    UserRegisterRequest, UserLoginRequest, TokenRefreshRequest,
    TokenResponse, UserResponse, TenantResponse, SuperAdminBootstrapRequest,
    RestrictedAdminCreateRequest
)
from backend.app.security import (
    hash_password, verify_password, create_access_token,
    create_refresh_token, decode_token, password_needs_rehash
)
from backend.app.dependencies import get_current_user, get_current_tenant, get_token_from_header, require_roles
from backend.app.config import settings

router = APIRouter(prefix="", tags=["Authentication"])


@router.post("/auth/bootstrap-super-admin", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
def bootstrap_super_admin(
    req: SuperAdminBootstrapRequest,
    x_admin_bootstrap_token: str = Header("", alias="X-Admin-Bootstrap-Token"),
    db: Session = Depends(get_db),
):
    """Create the platform's first Super Admin exactly once using a deployment secret."""
    configured_token = settings.SUPER_ADMIN_BOOTSTRAP_TOKEN
    if len(configured_token) < 32:
        raise HTTPException(status_code=503, detail="Super Admin bootstrap is not configured")
    if not secrets.compare_digest(x_admin_bootstrap_token, configured_token):
        raise HTTPException(status_code=403, detail="Invalid bootstrap credential")
    if db.query(User).filter(User.role == "SUPER_ADMINISTRATOR").first():
        raise HTTPException(status_code=409, detail="The initial Super Admin has already been created")

    email = req.email.lower().strip()
    if db.query(User).filter(User.email == email).first():
        raise HTTPException(status_code=409, detail="An account with this email already exists")

    tenant = Tenant(name=f"{req.full_name}'s XAU Pilot Admin", plan="Platform Administration")
    db.add(tenant)
    db.flush()
    user = User(
        tenant_id=tenant.id, email=email, password_hash=hash_password(req.password),
        full_name=req.full_name.strip(), role="SUPER_ADMINISTRATOR", mfa_enabled=False,
    )
    db.add(user)
    db.flush()
    db.add(AuditLog(
        tenant_id=tenant.id, user_id=user.id, action="INITIAL_SUPER_ADMIN_CREATED",
        target_type="USER", target_id=user.id, details='{"method":"one_time_bootstrap"}',
    ))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="The initial Super Admin has already been created")
    db.refresh(user)
    token_data = {"sub": user.id, "tenant_id": tenant.id, "role": user.role}
    return TokenResponse(
        accessToken=create_access_token(token_data), refreshToken=create_refresh_token(token_data),
        tokenType="Bearer", expiresIn=86400,
        user=UserResponse(
            id=user.id, email=user.email, fullName=user.full_name, role=user.role,
            tenantId=user.tenant_id, mfaEnabled=user.mfa_enabled,
            createdAt=user.created_at.strftime("%Y-%m-%d %H:%M:%S"),
        ),
    )


@router.post("/auth/admin-users", response_model=UserResponse, status_code=status.HTTP_201_CREATED)
def create_restricted_admin(
    req: RestrictedAdminCreateRequest,
    current_user: User = Depends(require_roles(["SUPER_ADMINISTRATOR"])),
    db: Session = Depends(get_db),
):
    """Allow the Super Admin to create one restricted operational administrator."""
    if db.query(User).filter(User.role == "ADMINISTRATOR").first():
        raise HTTPException(status_code=409, detail="The restricted administrator account already exists")

    email = req.email.lower().strip()
    if db.query(User).filter(User.email == email).first():
        raise HTTPException(status_code=409, detail="An account with this email already exists")

    admin = User(
        tenant_id=current_user.tenant_id,
        email=email,
        password_hash=hash_password(req.password),
        full_name=req.full_name.strip(),
        role="ADMINISTRATOR",
        mfa_enabled=False,
    )
    db.add(admin)
    db.flush()
    db.add(AuditLog(
        tenant_id=current_user.tenant_id,
        user_id=current_user.id,
        action="RESTRICTED_ADMIN_CREATED",
        target_type="USER",
        target_id=admin.id,
        details='{"role":"ADMINISTRATOR"}',
    ))
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="Could not create the restricted administrator")
    db.refresh(admin)
    return UserResponse(
        id=admin.id, email=admin.email, fullName=admin.full_name, role=admin.role,
        tenantId=admin.tenant_id, mfaEnabled=admin.mfa_enabled,
        createdAt=admin.created_at.strftime("%Y-%m-%d %H:%M:%S"),
    )


@router.post("/auth/register", response_model=TokenResponse)
def register(req: UserRegisterRequest, db: Session = Depends(get_db)):
    # Check if user already exists
    existing = db.query(User).filter(User.email == req.email.lower().strip()).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="An account with this email address already exists."
        )

    # Auto-provision dedicated tenant for new organization/customer
    tenant_id = str(uuid.uuid4())
    tenant = Tenant(
        id=tenant_id,
        name=f"{req.full_name}'s Organization",
        plan="Enterprise Gold"
    )
    db.add(tenant)
    db.flush()

    user_id = str(uuid.uuid4())
    user = User(
        id=user_id,
        tenant_id=tenant_id,
        email=req.email.lower().strip(),
        password_hash=hash_password(req.password),
        full_name=req.full_name,
        role="CUSTOMER",
        mfa_enabled=False
    )
    db.add(user)

    # Audit log
    audit = AuditLog(
        tenant_id=tenant_id,
        user_id=user_id,
        action="USER_REGISTERED",
        target_type="USER",
        target_id=user_id,
        details=f'{{"email": "{req.email}"}}'
    )
    db.add(audit)
    db.commit()
    db.refresh(user)

    # Generate JWT pair
    token_data = {"sub": user.id, "tenant_id": tenant_id, "role": user.role}
    access_token = create_access_token(token_data)
    refresh_token = create_refresh_token(token_data)

    user_resp = UserResponse(
        id=user.id,
        email=user.email,
        fullName=user.full_name,
        role=user.role,
        tenantId=user.tenant_id,
        mfaEnabled=user.mfa_enabled,
        createdAt=user.created_at.strftime("%Y-%m-%d %H:%M:%S")
    )

    return TokenResponse(
        accessToken=access_token,
        refreshToken=refresh_token,
        tokenType="Bearer",
        expiresIn=86400,
        user=user_resp
    )

@router.post("/auth/login", response_model=TokenResponse)
def login(req: UserLoginRequest, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == req.email.lower().strip()).first()
    if not user or not verify_password(req.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid email or password. Please check your credentials.",
            headers={"WWW-Authenticate": "Bearer"}
        )

    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Your account has been suspended. Please contact support."
        )

    if password_needs_rehash(user.password_hash):
        user.password_hash = hash_password(req.password)  # upgrade legacy fixed-salt hash

    token_data = {"sub": user.id, "tenant_id": user.tenant_id, "role": user.role}
    access_token = create_access_token(token_data)
    refresh_token = create_refresh_token(token_data)

    # Record audit log
    audit = AuditLog(
        tenant_id=user.tenant_id,
        user_id=user.id,
        action="USER_LOGIN",
        target_type="SESSION",
        target_id=user.id,
        details='{"auth_method": "PASSWORD"}'
    )
    db.add(audit)
    db.commit()

    user_resp = UserResponse(
        id=user.id,
        email=user.email,
        fullName=user.full_name,
        role=user.role,
        tenantId=user.tenant_id,
        mfaEnabled=user.mfa_enabled,
        createdAt=user.created_at.strftime("%Y-%m-%d %H:%M:%S")
    )

    return TokenResponse(
        accessToken=access_token,
        refreshToken=refresh_token,
        tokenType="Bearer",
        expiresIn=86400,
        user=user_resp
    )

@router.post("/auth/refresh", response_model=TokenResponse)
def refresh_token(req: TokenRefreshRequest, db: Session = Depends(get_db)):
    try:
        payload = decode_token(req.refresh_token)
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Invalid refresh token: {str(e)}",
            headers={"WWW-Authenticate": "Bearer"}
        )

    if payload.get("type") != "refresh":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Provided token is not a refresh token"
        )

    # Check revocation
    revoked = db.query(RevokedToken).filter(RevokedToken.token == req.refresh_token).first()
    if revoked:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Refresh token has been revoked",
            headers={"WWW-Authenticate": "Bearer"}
        )

    user_id = payload.get("sub")
    user = db.query(User).filter(User.id == user_id, User.is_active == True).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User no longer active"
        )

    # Revoke old refresh token (refresh token rotation)
    exp = datetime.fromtimestamp(payload.get("exp", int((datetime.utcnow() + timedelta(days=30)).timestamp())))
    db.add(RevokedToken(token=req.refresh_token, token_type="refresh", expires_at=exp))

    token_data = {"sub": user.id, "tenant_id": user.tenant_id, "role": user.role}
    new_access = create_access_token(token_data)
    new_refresh = create_refresh_token(token_data)
    db.commit()

    user_resp = UserResponse(
        id=user.id,
        email=user.email,
        fullName=user.full_name,
        role=user.role,
        tenantId=user.tenant_id,
        mfaEnabled=user.mfa_enabled,
        createdAt=user.created_at.strftime("%Y-%m-%d %H:%M:%S")
    )

    return TokenResponse(
        accessToken=new_access,
        refreshToken=new_refresh,
        tokenType="Bearer",
        expiresIn=86400,
        user=user_resp
    )

@router.post("/auth/logout")
def logout(
    token: str = Depends(get_token_from_header),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    try:
        payload = decode_token(token)
        exp = datetime.fromtimestamp(payload.get("exp", int((datetime.utcnow() + timedelta(hours=24)).timestamp())))
    except Exception:
        exp = datetime.utcnow() + timedelta(hours=24)

    db.add(RevokedToken(token=token, token_type="access", expires_at=exp))
    db.commit()
    return {"status": "SUCCESS", "message": "Logged out successfully"}

@router.get("/users/me", response_model=UserResponse)
def get_current_user_profile(current_user: User = Depends(get_current_user)):
    return UserResponse(
        id=current_user.id,
        email=current_user.email,
        fullName=current_user.full_name,
        role=current_user.role,
        tenantId=current_user.tenant_id,
        mfaEnabled=current_user.mfa_enabled,
        createdAt=current_user.created_at.strftime("%Y-%m-%d %H:%M:%S")
    )

@router.get("/tenants/current", response_model=TenantResponse)
def get_tenant_details(
    tenant: Tenant = Depends(get_current_tenant)
):
    return TenantResponse(
        id=tenant.id,
        name=tenant.name,
        plan=tenant.plan,
        maxAccounts=tenant.max_accounts,
        maxEaInstallations=tenant.max_ea_installations,
        createdAt=tenant.created_at.strftime("%Y-%m-%d %H:%M:%S")
    )
