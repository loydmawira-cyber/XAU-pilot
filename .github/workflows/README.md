# CI/CD: Android APK Build Workflow

This repository includes a production-ready GitHub Actions workflow (`.github/workflows/build_apk.yml`) to automatically test, build, and package Android APKs for **XAU Pilot**.

---

## 🚀 Triggers

The workflow runs on:
1. **Push** to `main` or `master` branches.
2. **Pull Requests** targeting `main` or `master`.
3. **Git Tags** matching `v*` (e.g. `v1.0.0`).
4. **Manual Trigger (`workflow_dispatch`)**:
   - Go to the **Actions** tab in GitHub.
   - Select **Build & Package Android APK** on the left sidebar.
   - Click **Run workflow**.
   - Choose:
     - `build_type`: `debug` (default), `release`, or `both`.
     - `run_tests`: `true` (default) or `false`.

---

## 📦 How to Download Your Generated APK

1. Go to your GitHub repository in your browser.
2. Click on the **Actions** tab at the top.
3. Click on the latest workflow run (e.g., *Build APK (debug)*).
4. Scroll down to the **Artifacts** section at the bottom of the summary page.
5. Click on **`xau-pilot-apk`** to download a ZIP containing the compiled `.apk` file(s).
6. Unzip and transfer the `.apk` to any Android device or emulator to install!

---

## 🔐 Optional Release Keystore Configuration

By default, the workflow packages a **Debug APK** signed with the included development certificate, which installs immediately on all test devices.

To generate a signed **Release APK**, add the following repository secrets under **Settings → Secrets and variables → Actions**:

| Secret Name | Description |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded string of your `.jks` or `.keystore` file (`base64 -w 0 my-release-key.jks`) |
| `RELEASE_STORE_PASSWORD` | Password for the keystore |
| `RELEASE_KEY_ALIAS` | Key alias name |
| `RELEASE_KEY_PASSWORD` | Password for the key alias |

If these secrets are not configured, the workflow automatically generates the standard installable Debug APK so builds never fail.
