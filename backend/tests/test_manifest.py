import copy
import unittest

from backend.app.ea_dev import manifest as m

KEY = "k" * 40


def base():
    return {
        "schema_version": "1.0", "ea_product_id": "gold-ea", "ea_version": "1.0.0",
        "developer_id": "dev-1",
        "parameters": [
            {"key": "lot_size", "label": "Lot Size", "type": "decimal", "default": 0.01,
             "minimum": 0.01, "maximum": 1.0, "step": 0.01, "unit": "lots",
             "visible_to_customer": True, "customer_editable": True},
            {"key": "secret_edge", "label": "Edge", "type": "integer", "default": 7,
             "visible_to_customer": False, "customer_editable": False},
            {"key": "direction", "label": "Direction", "type": "enum", "default": "BOTH",
             "choices": ["BUY", "SELL", "BOTH"],
             "visible_to_customer": True, "customer_editable": False},
        ],
    }


class ManifestValidation(unittest.TestCase):
    def test_valid(self):
        self.assertEqual(m.validate_manifest(base(), expected_version="1.0.0",
                                             expected_product_ref=("gold-ea",),
                                             expected_developer_id="dev-1"), [])

    def check_bad(self, mutate, text):
        d = base()
        mutate(d)
        errs = m.validate_manifest(d)
        self.assertTrue(any(text in e for e in errs), errs)

    def test_duplicate_key(self):
        self.check_bad(lambda d: d["parameters"].append(copy.deepcopy(d["parameters"][0])), "duplicate key")

    def test_bad_schema(self):
        self.check_bad(lambda d: d.update(schema_version="9.9"), "unsupported schema_version")

    def test_unknown_type(self):
        self.check_bad(lambda d: d["parameters"][0].update(type="float64"), "unsupported type")

    def test_default_out_of_range(self):
        self.check_bad(lambda d: d["parameters"][0].update(default=5), "above maximum")

    def test_step_boundary(self):
        self.check_bad(lambda d: d["parameters"][0].update(default=0.015), "step boundary")

    def test_min_gt_max(self):
        self.check_bad(lambda d: d["parameters"][0].update(minimum=2), "minimum is greater")

    def test_bad_unit(self):
        self.check_bad(lambda d: d["parameters"][0].update(unit="bananas"), "not allowed")

    def test_enum_default_not_choice(self):
        self.check_bad(lambda d: d["parameters"][2].update(default="NONE"), "one of the choices")

    def test_hidden_cannot_be_editable(self):
        self.check_bad(lambda d: d["parameters"][1].update(customer_editable=True), "while hidden")

    def test_unknown_field_rejected(self):
        self.check_bad(lambda d: d["parameters"][0].update(admin_override=True), "unknown fields")

    def test_missing_required(self):
        self.check_bad(lambda d: d["parameters"][0].pop("label"), "missing required")

    def test_bool_is_not_number(self):
        self.check_bad(lambda d: d["parameters"][1].update(default=True), "default")

    def test_version_mismatch(self):
        errs = m.validate_manifest(base(), expected_version="2.0.0")
        self.assertTrue(any("does not match version record" in e for e in errs))

    def test_wrong_developer(self):
        errs = m.validate_manifest(base(), expected_developer_id="someone-else")
        self.assertTrue(any("developer_id" in e for e in errs))

    def test_wrong_product(self):
        errs = m.validate_manifest(base(), expected_product_ref=("other",))
        self.assertTrue(any("ea_product_id" in e for e in errs))


class HashSignCustomer(unittest.TestCase):
    def test_hash_stable_regardless_of_key_order(self):
        a = base()
        b = {k: a[k] for k in reversed(list(a))}
        self.assertEqual(m.manifest_hash(m.canonical_json(a)), m.manifest_hash(m.canonical_json(b)))

    def test_hash_changes_when_edited(self):
        a = base()
        h1 = m.manifest_hash(m.canonical_json(a))
        a["parameters"][0]["maximum"] = 50
        self.assertNotEqual(h1, m.manifest_hash(m.canonical_json(a)))

    def test_signature_roundtrip_and_tamper(self):
        sig = m.sign_release(KEY, "p", "v", "d", "h" * 64, "b" * 64)
        self.assertTrue(m.verify_release(KEY, sig, "p", "v", "d", "h" * 64, "b" * 64))
        self.assertFalse(m.verify_release(KEY, sig, "p", "v", "d", "x" * 64, "b" * 64))
        self.assertFalse(m.verify_release(KEY, sig, "p", "v", "d", "h" * 64, "c" * 64))
        self.assertFalse(m.verify_release("z" * 40, sig, "p", "v", "d", "h" * 64, "b" * 64))
        self.assertFalse(m.verify_release(KEY, "", "p", "v", "d", "h" * 64, "b" * 64))

    def test_short_key_refused(self):
        with self.assertRaises(ValueError):
            m.sign_release("short", "p", "v", "d", "h", "b")

    def test_customer_view_hides_hidden(self):
        keys = [p["key"] for p in m.customer_view(base())]
        self.assertEqual(keys, ["lot_size", "direction"])
        self.assertNotIn("secret_edge", str(m.customer_view(base())))

    def test_customer_values(self):
        d = base()
        self.assertEqual(m.validate_customer_values(d, {"lot_size": 0.5}), [])
        self.assertTrue(m.validate_customer_values(d, {"lot_size": 9}))        # out of range
        self.assertTrue(m.validate_customer_values(d, {"lot_size": 0.015}))    # off step
        self.assertTrue(m.validate_customer_values(d, {"direction": "SELL"}))  # locked
        self.assertTrue(m.validate_customer_values(d, {"secret_edge": 1}))     # hidden
        self.assertTrue(m.validate_customer_values(d, {"nope": 1}))            # unknown

    def test_parse_rejects_garbage(self):
        with self.assertRaises(m.ManifestError):
            m.parse_manifest_bytes(b"\xff\xfe")
        with self.assertRaises(m.ManifestError):
            m.parse_manifest_bytes(b"[1,2]")


if __name__ == "__main__":
    unittest.main()
