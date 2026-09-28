# Image storage (Cloudflare R2)

Page images live in a **private** Cloudflare R2 bucket. Browsers upload straight to R2 with short-lived presigned URLs, and readers get images through the API's stable address `/api/images/{id}`, which redirects to a 10-minute signed URL. Image bytes never pass through the API. Design: [`specs/005-image-storage`](../specs/005-image-storage/); contract: [`images.openapi.yaml`](../specs/005-image-storage/contracts/images.openapi.yaml).

| What | Where |
|---|---|
| Bucket | `germinawiki-images` (private; location hint `enam`, since R2 has no South America hint) |
| Uploads in progress | `pending/{pageId}/{userId}/{uuid}`, **deleted after 1 day** by a lifecycle rule |
| Recorded images | `images/{uuid}` (the key is stored in `page_images.file_url`) |
| Deleted images' files | queued by a DB trigger (migration V2), removed by a **daily cleanup** at 06:00 UTC |
| Limits | JPEG, PNG, WebP, GIF; up to 5 MB each |
| Free tier | 10 GB storage, 1M writes and 10M reads per month, **no egress fees**; we use a tiny fraction |

---

## One-time setup (account owner)

**Prerequisite:** Wrangler needs **Node 22**:
```bash
fnm install 22 && fnm use 22 && node --version   # v22.x
```

### 1. Cloudflare account and R2 🌐
1. Sign up at https://dash.cloudflare.com/sign-up (free plan).
2. In the dashboard, open **R2 Object Storage** and enable it. Cloudflare may ask for a payment method even though usage stays within the free tier.
3. Copy your **Account ID** from the R2 overview page.

### 2. Bucket, CORS and lifecycle (terminal)
Run from the `ms-germina-wiki/` folder:
```bash
npx wrangler login                                    # opens the browser once
npx wrangler r2 bucket create germinawiki-images --location enam
npx wrangler r2 bucket cors set germinawiki-images --file infra/r2/cors.json --force
npx wrangler r2 bucket lifecycle add germinawiki-images pending-expiry pending/ --expire-days 1
npx wrangler r2 bucket cors list germinawiki-images        # web app origin + localhost:3000; PUT/GET/HEAD
npx wrangler r2 bucket lifecycle list germinawiki-images   # pending/ expires after 1 day
```
Keep the bucket **private**: don't enable the `r2.dev` public URL or a custom domain.

### 3. Bucket-scoped API token 🌐
In **R2 → Manage R2 API Tokens → Create API token**:
- **Permissions:** Object Read & Write
- **Specify bucket(s):** only `germinawiki-images`
- **TTL:** forever (rotate it if it ever leaks)

Copy the **Access Key ID** and **Secret Access Key**. Cloudflare shows the secret only once.

### 4. Secrets (terminal, nothing printed)
```bash
cat >> ~/.config/germinawiki/prod.env <<'EOF'
export R2_ACCOUNT_ID='<account id>'
export R2_BUCKET='germinawiki-images'
export R2_ACCESS_KEY_ID='<access key id>'
export R2_SECRET_ACCESS_KEY='<secret access key>'
EOF
source ~/.config/germinawiki/prod.env
R=Mateus-Mancini/ms-germina-wiki
gh variable set R2_ACCOUNT_ID --env production -R "$R" --body "$R2_ACCOUNT_ID"
gh variable set R2_BUCKET     --env production -R "$R" --body "$R2_BUCKET"
printf %s "$R2_ACCESS_KEY_ID"     | gh secret set R2_ACCESS_KEY_ID     --env production -R "$R"
printf %s "$R2_SECRET_ACCESS_KEY" | gh secret set R2_SECRET_ACCESS_KEY --env production -R "$R"
```
Do this **before merging** feature 005: the release passes these values to the deploy, and it fails (safely, deploying nothing) if they're missing.

### 5. Prove it works against real R2
```bash
source ~/.config/germinawiki/prod.env
APP_STORAGE_ACCOUNT_ID="$R2_ACCOUNT_ID" APP_STORAGE_BUCKET="$R2_BUCKET" \
APP_STORAGE_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" APP_STORAGE_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
  ./mvnw test -Dtest=R2LiveSmokeTest
```
Expect 2 tests, 0 failures. The test checks the CORS preflight for the web app, a presigned upload, `HEAD`, copy, a signed download of the same bytes, and cleanup, using the API's exact client settings. Without these variables the test is skipped (as in CI).

---

## Operations

**Run the cleanup now** (it normally runs daily):
```bash
aws lambda invoke --function-name germinawiki-api:live --cli-binary-format raw-in-base64-out \
  --payload '{"source":"germinawiki.image-cleanup"}' /dev/stdout     # {"deleted":N}
```

**Usage and cost:** Cloudflare dashboard → R2 → `germinawiki-images` → Metrics.

**Rotate the token:** create a new bucket-scoped token, update `prod.env` and the two GitHub secrets, merge any PR (or re-run the latest `release`), then delete the old token.

**Until auth-api lands**, upload and confirm answer `401` by design: identity comes from the signed-in user. Reading images (`GET /api/images/{id}`) is public, like the pages that embed them.
