import hmac
import secrets
import hashlib
import base64
import json
import time
from datetime import datetime, timedelta
from typing import Optional, Dict, Any
from backend.app.config import settings

_PBKDF2_ITERATIONS = 310_000
_PREFIX = "pbkdf2_sha256"
_LEGACY_SALT = b"xau_salt_secure_2026"  # only used to verify hashes created before per-user salts

def hash_password(password: str) -> str:
    """PBKDF2-HMAC-SHA256 with a random per-user salt. Format: pbkdf2_sha256$iters$salt$hash"""
    salt = secrets.token_bytes(16)
    key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, _PBKDF2_ITERATIONS)
    return f"{_PREFIX}${_PBKDF2_ITERATIONS}${base64.b64encode(salt).decode()}${base64.b64encode(key).decode()}"

def verify_password(plain_password: str, hashed_password: str) -> bool:
    if hashed_password.startswith(_PREFIX + "$"):
        try:
            _, iters, salt_b64, key_b64 = hashed_password.split("$")
            salt, expected = base64.b64decode(salt_b64), base64.b64decode(key_b64)
            actual = hashlib.pbkdf2_hmac("sha256", plain_password.encode("utf-8"), salt, int(iters))
        except Exception:
            return False
        return hmac.compare_digest(actual, expected)
    legacy = base64.b64encode(
        hashlib.pbkdf2_hmac("sha256", plain_password.encode("utf-8"), _LEGACY_SALT, 100000)).decode("utf-8")
    return hmac.compare_digest(legacy, hashed_password)

def password_needs_rehash(hashed_password: str) -> bool:
    return not hashed_password.startswith(_PREFIX + "$")

def hash_ea_token(token: str) -> str:
    """Fast SHA256 for EA authentication keys"""
    return hashlib.sha256(token.encode("utf-8")).hexdigest()

def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("utf-8").rstrip("=")

def _b64url_decode(data: str) -> bytes:
    padding = '=' * (4 - (len(data) % 4))
    return base64.urlsafe_b64decode(data + padding)

def create_access_token(data: Dict[str, Any], expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": int(expire.timestamp()), "type": "access"})

    header = {"alg": "HS256", "typ": "JWT"}
    header_b64 = _b64url_encode(json.dumps(header).encode("utf-8"))
    payload_b64 = _b64url_encode(json.dumps(to_encode).encode("utf-8"))

    signature_input = f"{header_b64}.{payload_b64}".encode("utf-8")
    signature = hmac.new(settings.JWT_SECRET.encode("utf-8"), signature_input, hashlib.sha256).digest()
    sig_b64 = _b64url_encode(signature)

    return f"{header_b64}.{payload_b64}.{sig_b64}"

def create_refresh_token(data: Dict[str, Any], expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    if expires_delta:
        expire = datetime.utcnow() + expires_delta
    else:
        expire = datetime.utcnow() + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    to_encode.update({"exp": int(expire.timestamp()), "type": "refresh"})

    header = {"alg": "HS256", "typ": "JWT"}
    header_b64 = _b64url_encode(json.dumps(header).encode("utf-8"))
    payload_b64 = _b64url_encode(json.dumps(to_encode).encode("utf-8"))

    signature_input = f"{header_b64}.{payload_b64}".encode("utf-8")
    signature = hmac.new(settings.JWT_SECRET.encode("utf-8"), signature_input, hashlib.sha256).digest()
    sig_b64 = _b64url_encode(signature)

    return f"{header_b64}.{payload_b64}.{sig_b64}"

def decode_token(token: str) -> Dict[str, Any]:
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("Invalid JWT format")

    header_b64, payload_b64, sig_b64 = parts
    signature_input = f"{header_b64}.{payload_b64}".encode("utf-8")
    expected_sig = hmac.new(settings.JWT_SECRET.encode("utf-8"), signature_input, hashlib.sha256).digest()

    if not hmac.compare_digest(_b64url_encode(expected_sig), sig_b64):
        raise ValueError("Signature verification failed")

    payload = json.loads(_b64url_decode(payload_b64).decode("utf-8"))
    if "exp" in payload and payload["exp"] < time.time():
        raise ValueError("Token has expired")

    return payload
