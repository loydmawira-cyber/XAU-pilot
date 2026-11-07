"""EA input-definition manifest: strict validation, canonical hashing, signing.

Pure standard library so it can be unit tested without the web stack.
The developer owns every definition in a manifest. Nothing in this module
(or anywhere else in the platform) adds, edits or unlocks parameters.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import re
from decimal import Decimal, InvalidOperation
from typing import Any, Dict, List, Optional, Tuple

SUPPORTED_SCHEMA_VERSIONS = {"1.0"}
SUPPORTED_TYPES = {"integer", "decimal", "boolean", "string", "enum"}
ALLOWED_UNITS = {
    "", "lots", "points", "pips", "percent", "usd", "seconds", "minutes",
    "hours", "bars", "trades", "multiplier",
}
MAX_PARAMETERS = 200
MAX_MANIFEST_BYTES = 256 * 1024

KEY_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]{0,63}$")
SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+$")

TOP_LEVEL_FIELDS = {"schema_version", "ea_product_id", "ea_version", "developer_id", "parameters"}
REQUIRED_TOP_LEVEL = TOP_LEVEL_FIELDS
PARAM_FIELDS = {
    "key", "label", "description", "type", "default", "minimum", "maximum", "step",
    "unit", "category", "choices", "max_length", "visible_to_customer",
    "customer_editable", "requires_restart", "requires_no_open_positions",
}
REQUIRED_PARAM_FIELDS = {"key", "label", "type", "default", "visible_to_customer", "customer_editable"}
BOOL_FLAGS = ("visible_to_customer", "customer_editable", "requires_restart", "requires_no_open_positions")


class ManifestError(ValueError):
    def __init__(self, errors: List[str]):
        super().__init__("; ".join(errors))
        self.errors = errors


def _dec(value: Any) -> Optional[Decimal]:
    if isinstance(value, bool) or not isinstance(value, (int, float, str, Decimal)):
        return None
    try:
        d = Decimal(str(value))
    except InvalidOperation:
        return None
    return d if d.is_finite() else None


def _is_integral(d: Decimal) -> bool:
    return d == d.to_integral_value()


def _choice_values(param: Dict[str, Any]) -> List[str]:
    out = []
    for c in param.get("choices") or []:
        out.append(c["value"] if isinstance(c, dict) else c)
    return out


def validate_manifest(
    manifest: Any,
    *,
    expected_product_ref: Optional[Tuple[str, ...]] = None,
    expected_version: Optional[str] = None,
    expected_developer_id: Optional[str] = None,
) -> List[str]:
    """Return a list of human-readable errors; empty list means valid."""
    errors: List[str] = []
    if not isinstance(manifest, dict):
        return ["manifest must be a JSON object"]

    unknown = set(manifest) - TOP_LEVEL_FIELDS
    if unknown:
        errors.append(f"unknown top-level fields: {sorted(unknown)}")
    missing = REQUIRED_TOP_LEVEL - set(manifest)
    if missing:
        errors.append(f"missing required fields: {sorted(missing)}")
        return errors

    if manifest["schema_version"] not in SUPPORTED_SCHEMA_VERSIONS:
        errors.append(f"unsupported schema_version {manifest['schema_version']!r}")

    version = manifest["ea_version"]
    if not isinstance(version, str) or not SEMVER_RE.match(version):
        errors.append("ea_version must look like 1.0.0")
    elif expected_version is not None and version != expected_version:
        errors.append(f"manifest ea_version {version!r} does not match version record {expected_version!r}")

    if not isinstance(manifest["ea_product_id"], str) or not manifest["ea_product_id"]:
        errors.append("ea_product_id must be a non-empty string")
    elif expected_product_ref and manifest["ea_product_id"] not in expected_product_ref:
        errors.append("manifest ea_product_id does not match this product")

    if not isinstance(manifest["developer_id"], str) or not manifest["developer_id"]:
        errors.append("developer_id must be a non-empty string")
    elif expected_developer_id and manifest["developer_id"] != expected_developer_id:
        errors.append("manifest developer_id does not match the authenticated developer")

    params = manifest["parameters"]
    if not isinstance(params, list):
        errors.append("parameters must be a list")
        return errors
    if len(params) > MAX_PARAMETERS:
        errors.append(f"at most {MAX_PARAMETERS} parameters are allowed")

    seen = set()
    for i, p in enumerate(params):
        errors.extend(_validate_parameter(p, i, seen))
    return errors


def _validate_parameter(p: Any, i: int, seen: set) -> List[str]:
    where = f"parameters[{i}]"
    if not isinstance(p, dict):
        return [f"{where} must be an object"]
    errs: List[str] = []
    key = p.get("key")
    if isinstance(key, str):
        where = f"parameters[{i}] ({key})"

    unknown = set(p) - PARAM_FIELDS
    if unknown:
        errs.append(f"{where}: unknown fields {sorted(unknown)}")
    missing = REQUIRED_PARAM_FIELDS - set(p)
    if missing:
        errs.append(f"{where}: missing required fields {sorted(missing)}")
        return errs

    if not isinstance(key, str) or not KEY_RE.match(key):
        errs.append(f"{where}: key must match {KEY_RE.pattern}")
    elif key in seen:
        errs.append(f"{where}: duplicate key")
    else:
        seen.add(key)

    label = p["label"]
    if not isinstance(label, str) or not label.strip() or len(label) > 80:
        errs.append(f"{where}: label must be 1-80 characters")
    for opt, limit in (("description", 500), ("category", 40)):
        v = p.get(opt)
        if v is not None and (not isinstance(v, str) or len(v) > limit):
            errs.append(f"{where}: {opt} must be a string up to {limit} characters")

    for flag in BOOL_FLAGS:
        if flag in p and not isinstance(p[flag], bool):
            errs.append(f"{where}: {flag} must be true or false")
    if p.get("customer_editable") is True and p.get("visible_to_customer") is not True:
        errs.append(f"{where}: a parameter cannot be customer_editable while hidden")

    unit = p.get("unit", "")
    if unit is None:
        unit = ""
    if unit not in ALLOWED_UNITS:
        errs.append(f"{where}: unit {unit!r} is not allowed")

    ptype = p["type"]
    if ptype not in SUPPORTED_TYPES:
        errs.append(f"{where}: unsupported type {ptype!r}")
        return errs

    default = p["default"]
    if ptype in ("integer", "decimal"):
        errs.extend(_validate_numeric(p, where, ptype, default))
    elif ptype == "boolean":
        if not isinstance(default, bool):
            errs.append(f"{where}: default must be true or false")
        for f in ("minimum", "maximum", "step", "choices", "max_length"):
            if f in p:
                errs.append(f"{where}: {f} is not valid for boolean")
    elif ptype == "string":
        if not isinstance(default, str):
            errs.append(f"{where}: default must be a string")
        ml = p.get("max_length", 200)
        if isinstance(ml, bool) or not isinstance(ml, int) or not 1 <= ml <= 1000:
            errs.append(f"{where}: max_length must be an integer from 1 to 1000")
        elif isinstance(default, str) and len(default) > ml:
            errs.append(f"{where}: default exceeds max_length")
        for f in ("minimum", "maximum", "step", "choices"):
            if f in p:
                errs.append(f"{where}: {f} is not valid for string")
    elif ptype == "enum":
        errs.extend(_validate_enum(p, where, default))
    return errs


def _validate_numeric(p: Dict[str, Any], where: str, ptype: str, default: Any) -> List[str]:
    errs: List[str] = []
    for f in ("choices", "max_length"):
        if f in p:
            errs.append(f"{where}: {f} is not valid for {ptype}")
    d = _dec(default)
    if d is None or isinstance(default, str):
        return errs + [f"{where}: default must be a number"]
    if ptype == "integer" and not _is_integral(d):
        errs.append(f"{where}: default must be a whole number")
    lo = hi = step = None
    for name in ("minimum", "maximum", "step"):
        if name in p:
            v = _dec(p[name])
            if v is None or isinstance(p[name], str):
                errs.append(f"{where}: {name} must be a number")
            else:
                if ptype == "integer" and not _is_integral(v):
                    errs.append(f"{where}: {name} must be a whole number")
                if name == "minimum":
                    lo = v
                elif name == "maximum":
                    hi = v
                else:
                    step = v
    if step is not None and step <= 0:
        errs.append(f"{where}: step must be greater than 0")
        step = None
    if lo is not None and hi is not None and lo > hi:
        errs.append(f"{where}: minimum is greater than maximum")
    if lo is not None and d < lo:
        errs.append(f"{where}: default is below minimum")
    if hi is not None and d > hi:
        errs.append(f"{where}: default is above maximum")
    if step is not None:
        base = lo if lo is not None else Decimal(0)
        if (d - base) % step != 0:
            errs.append(f"{where}: default does not fall on a step boundary")
    return errs


def _validate_enum(p: Dict[str, Any], where: str, default: Any) -> List[str]:
    errs: List[str] = []
    for f in ("minimum", "maximum", "step", "max_length"):
        if f in p:
            errs.append(f"{where}: {f} is not valid for enum")
    choices = p.get("choices")
    if not isinstance(choices, list) or not choices:
        return errs + [f"{where}: enum requires a non-empty choices list"]
    values = []
    for c in choices:
        if isinstance(c, str) and c:
            values.append(c)
        elif (isinstance(c, dict) and set(c) <= {"value", "label"} and "value" in c
              and isinstance(c["value"], str) and c["value"]
              and isinstance(c.get("label", ""), str)):
            values.append(c["value"])
        else:
            errs.append(f"{where}: each choice must be a string or {{value, label}}")
    if len(values) != len(set(values)):
        errs.append(f"{where}: duplicate enum choices")
    if not isinstance(default, str) or default not in values:
        errs.append(f"{where}: default must be one of the choices")
    return errs


# ---------------------------------------------------------------- hashing / signing

def canonical_json(manifest: Dict[str, Any]) -> str:
    return json.dumps(manifest, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def manifest_hash(canonical: str) -> str:
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def parse_manifest_bytes(raw: bytes) -> Dict[str, Any]:
    if len(raw) > MAX_MANIFEST_BYTES:
        raise ManifestError([f"manifest larger than {MAX_MANIFEST_BYTES} bytes"])
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ManifestError([f"manifest is not valid UTF-8 JSON: {exc}"])
    if not isinstance(data, dict):
        raise ManifestError(["manifest must be a JSON object"])
    return data


def _signing_payload(product_id: str, version_id: str, developer_id: str,
                     m_hash: str, binary_sha256: str) -> bytes:
    return "|".join(["xaupilot.manifest.v1", product_id, version_id, developer_id,
                     m_hash, binary_sha256]).encode("utf-8")


def sign_release(key: str, product_id: str, version_id: str, developer_id: str,
                 m_hash: str, binary_sha256: str) -> str:
    if not key or len(key) < 32:
        raise ValueError("MANIFEST_SIGNING_KEY must be set to at least 32 characters")
    return hmac.new(key.encode("utf-8"),
                    _signing_payload(product_id, version_id, developer_id, m_hash, binary_sha256),
                    hashlib.sha256).hexdigest()


def verify_release(key: str, signature: str, product_id: str, version_id: str,
                   developer_id: str, m_hash: str, binary_sha256: str) -> bool:
    try:
        expected = sign_release(key, product_id, version_id, developer_id, m_hash, binary_sha256)
    except ValueError:
        return False
    return hmac.compare_digest(expected, signature or "")


# ---------------------------------------------------------------- customer projection

def customer_view(manifest: Dict[str, Any]) -> List[Dict[str, Any]]:
    """Only parameters the developer made visible. Hidden ones never leave the server."""
    out = []
    for p in manifest.get("parameters", []):
        if p.get("visible_to_customer") is not True:
            continue
        item = {k: v for k, v in p.items() if k not in ("visible_to_customer",)}
        item["customer_editable"] = p.get("customer_editable") is True
        out.append(item)
    return out


def validate_customer_values(manifest: Dict[str, Any], values: Dict[str, Any]) -> List[str]:
    """Check a customer's submitted values against the authoritative manifest."""
    errors: List[str] = []
    by_key = {p["key"]: p for p in manifest.get("parameters", [])}
    for key, val in (values or {}).items():
        p = by_key.get(key)
        if p is None or p.get("visible_to_customer") is not True or p.get("customer_editable") is not True:
            errors.append(f"{key}: not an editable setting for this EA")
            continue
        probe = dict(p)
        probe["default"] = val
        errs = _validate_parameter(probe, 0, set())
        errors.extend(e.replace("default", "value") for e in errs)
    return errors
