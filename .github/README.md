# GitHub Actions Workflows

> **Prerequisite:** Ensure `gradlew` and `gradle/wrapper/` exist (run `gradle wrapper` if needed).

## CI (`ci.yml`)

Runs on every push and pull request to `main`/`master`:

- **Lint** – Android lint
- **Test** – Unit tests
- **Build** – Debug APK

Version on CI: `1.0.{run_number}` (e.g. `1.0.42`).

Artifacts: Debug APK is uploaded for each run.

---

## Release (`release.yml`)

Runs when you push a tag matching `v*` (e.g. `v1.0.0`, `v2.1.3`):

```bash
git tag v1.0.0
git push origin v1.0.0
```

- Builds release AAB and APK
- Creates a GitHub Release with both artifacts
- Version is taken from the tag

### Signing (optional)

Use your own keystore by adding these secrets:

- `RELEASE_KEYSTORE` – Base64-encoded `.jks` file
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Decode and use in the workflow (see workflow comments for details).

---

## GitHub Pages (`pages.yml`)

Deploys a minimal project page on push to `main`/`master`.

Enable in **Settings → Pages → Build and deployment → Source: GitHub Actions**.
