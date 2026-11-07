"""End-to-end API tests (FastAPI TestClient + SQLite).
Run from the repo root after `pip install -r backend/requirements.txt httpx`:
    python -m unittest backend.tests.test_developer_api -v
"""
import io
import json
import os
import tempfile
import unittest

_TMP = tempfile.mkdtemp()
os.environ["DATABASE_URL"] = f"sqlite:///{_TMP}/test.db"
os.environ["EA_STORAGE_DIR"] = f"{_TMP}/packages"
os.environ["MANIFEST_SIGNING_KEY"] = "s" * 48
os.environ["JWT_SECRET"] = "j" * 48
os.environ["SUPER_ADMIN_BOOTSTRAP_TOKEN"] = "bootstrap-test-token-" + ("x" * 40)

from fastapi.testclient import TestClient  # noqa: E402

from backend.app.database import Base, SessionLocal, engine  # noqa: E402
from backend.app.main import app  # noqa: E402
from backend.app.models import EaLicense, EaBinaryPackage, EaExtractionJob, User  # noqa: E402

API = "/api/v1"
EX5 = b"\x7fEX5-binary-bytes" * 8


def manifest(product_id, dev_id, version="1.0.0"):
    return {
        "schema_version": "1.0", "ea_product_id": product_id, "ea_version": version,
        "developer_id": dev_id,
        "parameters": [
            {"key": "lot_size", "label": "Lot Size", "type": "decimal", "default": 0.01,
             "minimum": 0.01, "maximum": 1.0, "step": 0.01, "unit": "lots",
             "visible_to_customer": True, "customer_editable": True},
            {"key": "hidden_edge", "label": "Edge", "type": "integer", "default": 3,
             "visible_to_customer": False, "customer_editable": False},
        ],
    }


class DeveloperApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        Base.metadata.drop_all(bind=engine)
        Base.metadata.create_all(bind=engine)
        cls.c = TestClient(app)

    def user(self, email, role=None):
        r = self.c.post(f"{API}/auth/register",
                        json={"email": email, "password": "Passw0rd!x", "full_name": email.split("@")[0]})
        self.assertEqual(r.status_code, 200, r.text)
        body = r.json()
        if role:
            with SessionLocal() as db:
                db.query(User).filter(User.id == body["user"]["id"]).update({"role": role})
                db.commit()
            body = self.c.post(f"{API}/auth/login", json={"email": email, "password": "Passw0rd!x"}).json()
        return {"id": body["user"]["id"], "tenant": body["user"]["tenantId"],
                "h": {"Authorization": f"Bearer {body['accessToken']}"}}

    def developer(self, email):
        u = self.user(email)
        r = self.c.post(f"{API}/developer/register", headers=u["h"],
                        json={"display_name": "Dev " + email, "contact_email": email})
        self.assertEqual(r.status_code, 201, r.text)
        return u

    def product(self, dev, code):
        r = self.c.post(f"{API}/developer/ea-products", headers=dev["h"], json={
            "product_code": code, "name": "Gold " + code, "description": "A gold scalper EA for XAUUSD.",
            "strategy_category": "Scalper", "risk_disclosures": "Trading is risky; test on demo first.",
            "monthly_price_usd": 49, "annual_price_usd": 490, "lifetime_price_usd": 1200})
        self.assertEqual(r.status_code, 201, r.text)
        return r.json()

    def version(self, dev, pid, ver="1.0.0"):
        r = self.c.post(f"{API}/developer/ea-products/{pid}/versions", headers=dev["h"], json={"version": ver})
        self.assertEqual(r.status_code, 201, r.text)
        return r.json()["id"]

    def upload(self, dev, pid, vid, man):
        base = f"{API}/developer/ea-products/{pid}/versions/{vid}"
        r1 = self.c.post(f"{base}/upload-binary", headers=dev["h"], files={"file": ("gold.ex5", io.BytesIO(EX5))})
        r2 = self.c.post(f"{base}/upload-manifest", headers=dev["h"],
                         files={"file": ("m.json", io.BytesIO(json.dumps(man).encode()))})
        # Unit-test fixture: simulate a configured scanner returning CLEAN.
        # Production publication still requires an actual scanner result.
        if r1.status_code == 200:
            with SessionLocal() as db:
                db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == vid).update({"scan_status": "CLEAN"})
                db.commit()
        return r1, r2

    def published(self, dev, code):
        p = self.product(dev, code)
        vid = self.version(dev, p["id"])
        r1, r2 = self.upload(dev, p["id"], vid, manifest(p["id"], dev["id"]))
        self.assertEqual(r1.status_code, 200, r1.text)
        self.assertTrue(r2.json()["manifest"]["is_valid"], r2.text)
        # Fixture: simulate a completed extraction for tests that need a published EA.
        with SessionLocal() as db:
            binary = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == vid).one()
            job = db.query(EaExtractionJob).filter(EaExtractionJob.version_id == vid).one()
            job.status = "COMPLETE"
            job.binary_sha256 = binary.sha256
            job.result_json = json.dumps({"inputs": [], "unresolved": []})
            db.commit()
        r = self.c.post(f"{API}/developer/ea-products/{p['id']}/versions/{vid}/publish", headers=dev["h"])
        self.assertEqual(r.status_code, 200, r.text)
        return p, vid

    def test_00_first_super_admin_bootstrap_is_one_time_and_secure(self):
        url = f"{API}/auth/bootstrap-super-admin"
        payload = {"email": "owner@xaupilot.test", "password": "A-Strong-Admin-Password-2026!", "full_name": "Platform Owner"}
        self.assertEqual(self.c.post(url, json=payload).status_code, 403)
        self.assertEqual(self.c.post(url, json=payload, headers={"X-Admin-Bootstrap-Token": "wrong"}).status_code, 403)
        created = self.c.post(url, json=payload, headers={"X-Admin-Bootstrap-Token": os.environ["SUPER_ADMIN_BOOTSTRAP_TOKEN"]})
        self.assertEqual(created.status_code, 201, created.text)
        self.assertEqual(created.json()["user"]["role"], "SUPER_ADMINISTRATOR")
        again = self.c.post(url, json=payload, headers={"X-Admin-Bootstrap-Token": os.environ["SUPER_ADMIN_BOOTSTRAP_TOKEN"]})
        self.assertEqual(again.status_code, 409)
        ordinary = self.c.post(f"{API}/auth/register", json={"email": "ordinary@xaupilot.test", "password": "Passw0rd!x", "full_name": "Ordinary User"})
        self.assertEqual(ordinary.status_code, 200)
        self.assertEqual(ordinary.json()["user"]["role"], "CUSTOMER")

    def test_01_super_admin_can_create_multiple_restricted_admins(self):
        login = self.c.post(f"{API}/auth/login", json={
            "email": "owner@xaupilot.test", "password": "A-Strong-Admin-Password-2026!"
        })
        self.assertEqual(login.status_code, 200, login.text)
        super_headers = {"Authorization": f"Bearer {login.json()['accessToken']}"}
        payload = {"email": "operations@xaupilot.test", "password": "Another-Strong-Password-2026!", "full_name": "Operations Admin"}
        created = self.c.post(f"{API}/auth/admin-users", headers=super_headers, json=payload)
        self.assertEqual(created.status_code, 201, created.text)
        self.assertEqual(created.json()["role"], "ADMINISTRATOR")
        second = self.c.post(f"{API}/auth/admin-users", headers=super_headers, json={**payload, "email": "second@xaupilot.test"})
        self.assertEqual(second.status_code, 201, second.text)
        self.assertEqual(second.json()["role"], "ADMINISTRATOR")
        admin_login = self.c.post(f"{API}/auth/login", json={"email": payload["email"], "password": payload["password"]})
        self.assertEqual(admin_login.status_code, 200, admin_login.text)
        admin_headers = {"Authorization": f"Bearer {admin_login.json()['accessToken']}"}
        forbidden = self.c.post(f"{API}/auth/admin-users", headers=admin_headers, json={**payload, "email": "third@xaupilot.test"})
        self.assertEqual(forbidden.status_code, 403, forbidden.text)
        customer = self.c.post(f"{API}/auth/register", json={"email": "normal@xaupilot.test", "password": "Passw0rd!x", "full_name": "Normal Customer"})
        self.assertEqual(customer.status_code, 200)
        self.assertEqual(customer.json()["user"]["role"], "CUSTOMER")

    # ---- ownership / roles
    def test_developer_cannot_touch_another_developers_product(self):
        a, b = self.developer("a1@x.com"), self.developer("b1@x.com")
        p = self.product(a, "OWNA1")
        self.assertEqual(self.c.get(f"{API}/developer/ea-products/{p['id']}", headers=b["h"]).status_code, 404)
        r = self.c.post(f"{API}/developer/ea-products/{p['id']}/versions", headers=b["h"], json={"version": "1.0.0"})
        self.assertEqual(r.status_code, 404)

    def test_customer_cannot_use_developer_endpoints(self):
        cust = self.user("cust1@x.com")
        self.assertEqual(self.c.get(f"{API}/developer/ea-products", headers=cust["h"]).status_code, 403)
        self.assertEqual(self.c.post(f"{API}/developer/ea-products/x/versions/y/upload-binary", headers=cust["h"],
                                     files={"file": ("a.ex5", io.BytesIO(EX5))}).status_code, 403)

    def test_unauthenticated_rejected(self):
        self.assertEqual(self.c.get(f"{API}/developer/ea-products").status_code, 401)

    def test_admin_cannot_edit_developer_manifest(self):
        dev, admin = self.developer("d2@x.com"), self.user("adm2@x.com", role="ADMINISTRATOR")
        p = self.product(dev, "ADM02")
        vid = self.version(dev, p["id"])
        base = f"{API}/developer/ea-products/{p['id']}/versions/{vid}"
        r = self.c.post(f"{base}/upload-manifest", headers=admin["h"],
                        files={"file": ("m.json", io.BytesIO(b"{}"))})
        self.assertIn(r.status_code, (403, 404))
        self.assertEqual(self.c.post(f"{base}/publish", headers=admin["h"]).status_code, 403)

    # ---- manifest + immutability
    def test_invalid_manifest_cannot_publish(self):
        dev = self.developer("d3@x.com")
        p = self.product(dev, "INV03")
        vid = self.version(dev, p["id"])
        bad = manifest(p["id"], dev["id"])
        bad["parameters"][0]["default"] = 99
        r1, r2 = self.upload(dev, p["id"], vid, bad)
        self.assertFalse(r2.json()["manifest"]["is_valid"])
        r = self.c.post(f"{API}/developer/ea-products/{p['id']}/versions/{vid}/publish", headers=dev["h"])
        self.assertEqual(r.status_code, 422)

    def test_manifest_for_other_developer_rejected(self):
        dev = self.developer("d4@x.com")
        p = self.product(dev, "OTH04")
        vid = self.version(dev, p["id"])
        _, r2 = self.upload(dev, p["id"], vid, manifest(p["id"], "someone-else"))
        self.assertFalse(r2.json()["manifest"]["is_valid"])

    def test_published_version_is_immutable(self):
        dev = self.developer("d5@x.com")
        p, vid = self.published(dev, "IMM05")
        r1, r2 = self.upload(dev, p["id"], vid, manifest(p["id"], dev["id"]))
        self.assertEqual(r1.status_code, 409)
        self.assertEqual(r2.status_code, 409)
        again = self.c.post(f"{API}/developer/ea-products/{p['id']}/versions/{vid}/publish", headers=dev["h"])
        self.assertEqual(again.status_code, 409)

    def test_new_version_keeps_old_manifest(self):
        dev = self.developer("d6@x.com")
        p, v1 = self.published(dev, "OLD06")
        v2 = self.version(dev, p["id"], "1.1.0")
        m2 = manifest(p["id"], dev["id"], "1.1.0")
        m2["parameters"].append({"key": "max_trades", "label": "Max Trades", "type": "integer", "default": 2,
                                 "visible_to_customer": True, "customer_editable": True})
        self.upload(dev, p["id"], v2, m2)
        prev = self.c.get(f"{API}/developer/ea-products/{p['id']}/versions/{v1}/preview", headers=dev["h"]).json()
        self.assertEqual([x["key"] for x in prev["parameters"]], ["lot_size"])

    def test_preview_hides_hidden_parameters(self):
        dev = self.developer("d7@x.com")
        p, vid = self.published(dev, "PRV07")
        prev = self.c.get(f"{API}/developer/ea-products/{p['id']}/versions/{vid}/preview", headers=dev["h"]).json()
        self.assertNotIn("hidden_edge", json.dumps(prev))

    def test_extraction_prepares_hidden_developer_review_and_binds_schema_to_binary(self):
        dev = self.developer("extract-review@xaupilot.test")
        product = self.product(dev, "EXTRVW")
        version_id = self.version(dev, product["id"])
        base = f"{API}/developer/ea-products/{product['id']}/versions/{version_id}"
        upload = self.c.post(f"{base}/upload-binary", headers=dev["h"],
                             files={"file": ("review.ex5", io.BytesIO(EX5))})
        self.assertEqual(upload.status_code, 200, upload.text)
        with SessionLocal() as db:
            binary = db.query(EaBinaryPackage).filter(EaBinaryPackage.version_id == version_id).one()
            binary.scan_status = "CLEAN"
            job = db.query(EaExtractionJob).filter(EaExtractionJob.version_id == version_id).one()
            job.status = "COMPLETE"
            job.binary_sha256 = binary.sha256
            job.result_json = json.dumps({"inputs": [
                {"key": "lots", "display_name": "Lots", "type": "double", "default": 0.01, "choices": []}
            ], "unresolved": []})
            db.commit()

        prepared = self.c.post(f"{base}/extraction/prepare-manifest", headers=dev["h"])
        self.assertEqual(prepared.status_code, 200, prepared.text)
        self.assertEqual(prepared.json()["status"], "REVIEW_REQUIRED")
        param = prepared.json()["parameters"][0]
        self.assertFalse(param["visible_to_customer"])
        self.assertFalse(param["customer_editable"])
        reviewed = {
            "schema_version": "1.0", "ea_product_id": product["id"], "ea_version": "1.0.0",
            "developer_id": dev["id"],
            "parameters": [{**param, "visible_to_customer": True, "customer_editable": True,
                            "minimum": 0.01, "maximum": 1.0, "step": 0.01, "requires_restart": True}],
        }
        saved = self.c.put(f"{base}/review-manifest", headers=dev["h"], json=reviewed)
        self.assertEqual(saved.status_code, 200, saved.text)
        self.assertEqual(saved.json()["status"], "REVIEW_SAVED")
        preview = self.c.get(f"{base}/preview", headers=dev["h"])
        self.assertEqual(preview.status_code, 200, preview.text)
        self.assertEqual(preview.json()["parameters"][0]["key"], "lots")

        changed_default = {**reviewed, "parameters": [{**reviewed["parameters"][0], "default": 0.02}]}
        rejected = self.c.put(f"{base}/review-manifest", headers=dev["h"], json=changed_default)
        self.assertEqual(rejected.status_code, 422, rejected.text)

    def test_reupload_invalidates_previous_manifest_and_extraction(self):
        dev = self.developer("reupload-review@xaupilot.test")
        product = self.product(dev, "REUPRV")
        version_id = self.version(dev, product["id"])
        _, manifest_response = self.upload(dev, product["id"], version_id, manifest(product["id"], dev["id"]))
        self.assertTrue(manifest_response.json()["manifest"]["is_valid"])
        base = f"{API}/developer/ea-products/{product['id']}/versions/{version_id}"
        uploaded_again = self.c.post(f"{base}/upload-binary", headers=dev["h"],
                                     files={"file": ("replacement.ex5", io.BytesIO(EX5 + b"new"))})
        self.assertEqual(uploaded_again.status_code, 200, uploaded_again.text)
        versions = self.c.get(f"{API}/developer/ea-products/{product['id']}/versions", headers=dev["h"])
        version = next(item for item in versions.json() if item["id"] == version_id)
        self.assertIsNone(version["manifest"])

    def test_binary_validation(self):
        dev = self.developer("d8@x.com")
        p = self.product(dev, "BIN08")
        vid = self.version(dev, p["id"])
        base = f"{API}/developer/ea-products/{p['id']}/versions/{vid}"
        r = self.c.post(f"{base}/upload-binary", headers=dev["h"], files={"file": ("gold.mq5", io.BytesIO(EX5))})
        self.assertEqual(r.status_code, 422)
        r = self.c.post(f"{base}/upload-binary", headers=dev["h"],
                        files={"file": ("gold.ex5", io.BytesIO(b"#property copyright"))})
        self.assertEqual(r.status_code, 422)

    # ---- review, downloads
    def test_admin_review_cannot_change_manifest_but_can_list(self):
        dev, admin = self.developer("d9@x.com"), self.user("adm9@x.com", role="ADMINISTRATOR")
        p, vid = self.published(dev, "REV09")
        self.c.post(f"{API}/developer/ea-products/{p['id']}/submit-for-review", headers=dev["h"])
        self.assertEqual(self.c.post(f"{API}/admin/ea-review/{p['id']}/approve", headers=dev["h"]).status_code, 403)
        self.assertEqual(self.c.post(f"{API}/admin/ea-review/{p['id']}/approve", headers=admin["h"]).status_code, 200)

    def test_owner_mt5_performance_feed_is_authenticated_and_public_aggregate_only(self):
        import time
        from backend.app.models import EaProduct
        dev = self.developer("perf-owner@xaupilot.test")
        product = self.product(dev, "PERF001")
        with SessionLocal() as db:
            db.query(EaProduct).filter(EaProduct.id == product["id"]).update({"is_published": True, "listing_status": "APPROVED"})
            db.commit()
        connect = self.c.post(f"{API}/developer/ea-products/{product['id']}/performance-account", headers=dev["h"], json={
            "label": "Owner Live MT5", "broker_name": "Live Broker", "server_name": "Live-Server",
            "account_number": "12345678", "environment": "LIVE", "magic_number": 880001,
            "symbol": "XAUUSD", "timeframe": "M5"})
        self.assertEqual(connect.status_code, 201, connect.text)
        body = connect.json()
        self.assertEqual(body["account_number_masked"], "****5678")
        self.assertTrue(body["ea_bridge_token"])
        self.assertEqual(self.c.post("/ea/v1/performance/snapshot", json={"installation_id": body["installation_id"], "balance": 1000, "equity": 1000, "captured_at_epoch": int(time.time())}).status_code, 401)
        token_headers = {"Authorization": "Bearer " + body["ea_bridge_token"]}
        now = int(time.time())
        snapshot = {"installation_id": body["installation_id"], "balance": 1000, "equity": 995,
                    "currency": "USD", "captured_at_epoch": now}
        stored = self.c.post("/ea/v1/performance/snapshot", headers=token_headers, json=snapshot)
        self.assertEqual(stored.status_code, 201, stored.text)
        trade = {"installation_id": body["installation_id"], "deal_ticket": 90001, "position_id": 70001,
                 "symbol": "XAUUSD", "direction": "BUY", "volume": 0.01, "close_price": 2350.5,
                 "profit": 12.0, "commission": -0.5, "swap": -0.1, "currency": "USD", "closed_at_epoch": now}
        saved = self.c.post("/ea/v1/performance/trade", headers=token_headers, json=trade)
        self.assertEqual(saved.status_code, 200, saved.text)
        duplicate = self.c.post("/ea/v1/performance/trade", headers=token_headers, json=trade)
        self.assertEqual(duplicate.status_code, 200, duplicate.text)
        public = self.c.get(f"{API}/marketplace/ea-products/{product['id']}/performance")
        self.assertEqual(public.status_code, 200, public.text)
        self.assertEqual(public.json()["closed_trades"], 1)
        self.assertAlmostEqual(public.json()["net_profit"], 11.4, places=2)
        self.assertNotIn("ea_bridge_token", public.text)
        self.assertNotIn("12345678", public.text)

    def test_download_requires_licence(self):
        dev, cust = self.developer("d10@x.com"), self.user("c10@x.com")
        admin = self.user("adm10@x.com", role="ADMINISTRATOR")
        p, vid = self.published(dev, "DL010")
        self.c.post(f"{API}/developer/ea-products/{p['id']}/submit-for-review", headers=dev["h"])
        self.c.post(f"{API}/admin/ea-review/{p['id']}/approve", headers=admin["h"])
        url = f"{API}/ea-products/{p['id']}/versions/{vid}/download"
        self.assertEqual(self.c.get(url, headers=cust["h"]).status_code, 403)
        with SessionLocal() as db:
            db.add(EaLicense(tenant_id=cust["tenant"], ea_product_id=p["id"], license_key_hash="h",
                             license_key_masked="XXXX", license_type="LIFETIME", status="ACTIVE"))
            db.commit()
        ok = self.c.get(url, headers=cust["h"])
        self.assertEqual(ok.status_code, 200)
        self.assertEqual(ok.content, EX5)
        self.assertEqual(self.c.get(url).status_code, 401)


if __name__ == "__main__":
    unittest.main()
