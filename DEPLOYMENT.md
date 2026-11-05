# Deployment guide

## Vercel static site

Import this GitHub repository into Vercel. In **Project → Settings → Build and Deployment**, use:

- Framework Preset: **Other**
- Root Directory: `.` (repository root)
- Build Command: leave empty
- Output Directory: `docs`
- Install Command: leave empty

The root `vercel.json` already sets `outputDirectory` to `docs`. Redeploy after committing.

Expected deployed paths (relative to the site root):

- `/` — `docs/index.html`
- `/dashboard.html` — `docs/dashboard.html`
- `/report.html` — `docs/report.html`
- `/case-studies/` — `docs/case-studies/index.html`
- `/api.html` — `docs/api.html` (API documentation, not the running Java API)
- `/architecture.html` — `docs/architecture.html`
- `/Fraud_Ring_Investigator.pptx` — slide deck

## GitHub upload

Extract the ZIP and upload its **contents** to the repository root. Do not upload the ZIP itself and do not nest the project inside another `fraud-ring-investigator/` folder. Preserve names and capitalization, especially `docs/Fraud_Ring_Investigator.pptx`.

## Java REST API

The Java API is a separate backend service; Vercel's static `docs` deployment does not run it. Deploy the repository's `Dockerfile` or `deploy/render.yaml` to a Java/container host. The API docs are at `/api.html`; the live API base URL must be configured separately after backend deployment.

All demo case data is synthetic.
