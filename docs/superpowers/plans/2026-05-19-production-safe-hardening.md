# Production-Safe Hardening Plan

> **For agentic workers:** This repo's `AGENTS.md` asks for `superpowers:writing-plans`, but that skill is not available in the current Codex session. This plan follows the existing `docs/superpowers/plans/` format and keeps every task small, verifiable, and separately committable.

**Goal:** Improve the project for AWS/DevOps learning while preserving the currently running production workflow at `https://anphuc.xyz`.

**Current Production Baseline Verified on 2026-05-19:**
- `https://anphuc.xyz` returns `200 OK` through Nginx and serves the Next.js app.
- `http://anphuc.xyz` redirects to `https://anphuc.xyz/`.
- `https://anphuc.xyz/api/auth/csrf` returns `200`.
- `https://anphuc.xyz/api/auth/me` returns `401` for an anonymous request.

**Safety Principle:** Do not change production routing, authentication, Docker deployment, or environment wiring until smoke tests and a rollback/runbook exist. Every task below must preserve the baseline checks above.

---

## File Map

| Action | File | Purpose |
|--------|------|---------|
| Create | `scripts/smoke-prod.sh` | Repeatable production smoke checks for `anphuc.xyz` |
| Create | `docs/production-runbook.md` | Current production topology, deploy verification, rollback notes |
| Create | `.env.example` | Safe template for production/local env variables |
| Modify | `.gitignore` | Ensure local secret files and generated runtime data stay untracked |
| Modify | `backend/pom.xml` | Add Spring Boot Actuator for health checks |
| Modify | `backend/src/main/resources/application.properties` | Expose only safe health endpoint config |
| Modify | `backend/src/main/resources/application-prod.properties` | Production health/env config if needed |
| Create | `.github/workflows/ci.yml` | Non-deploying CI: backend tests, frontend lint/build |
| Modify | `frontend/package.json` | Add cross-platform Unix type generation script without removing Windows script |
| Create | `docs/local-dev.md` | Clarify local ports and local/prod proxy differences |

---

## Task 1: Add Production Smoke Test Script

**Purpose:** Create a fast, repeatable safety net before changing anything else.

**Files:**
- Create: `scripts/smoke-prod.sh`

- [ ] **Step 1: Verify current production behavior manually**

```bash
curl -I -L https://anphuc.xyz
curl -i -sS https://anphuc.xyz/api/auth/csrf | sed -n '1,40p'
curl -i -sS https://anphuc.xyz/api/auth/me | sed -n '1,60p'
curl -I -sS http://anphuc.xyz | sed -n '1,40p'
```

Expected:
- Homepage returns `200`.
- HTTP returns `301` to HTTPS.
- `/api/auth/csrf` returns `200`.
- `/api/auth/me` returns `401` when anonymous.

- [ ] **Step 2: Write `scripts/smoke-prod.sh`**

The script should:
- default `BASE_URL=https://anphuc.xyz`
- allow override with `BASE_URL=https://... scripts/smoke-prod.sh`
- fail fast with non-zero exit code
- print concise PASS/FAIL lines
- check:
  - homepage status is `200`
  - HTTP domain redirects to HTTPS
  - `/api/auth/csrf` is `200`
  - `/api/auth/me` is `401`

- [ ] **Step 3: Run the script**

```bash
chmod +x scripts/smoke-prod.sh
scripts/smoke-prod.sh
```

Expected: all checks pass.

- [ ] **Step 4: Commit**

```bash
git add scripts/smoke-prod.sh
git commit -m "test(prod): add production smoke checks"
```

---

## Task 2: Document Current Production Runbook

**Purpose:** Capture the workflow that is already live before changing it.

**Files:**
- Create: `docs/production-runbook.md`

- [ ] **Step 1: Write the runbook**

Include:
- current domain: `anphuc.xyz`
- production routing: Nginx terminates HTTPS, `/api/` goes to backend, `/` goes to frontend
- expected app ports: backend `8080`, frontend `3000`
- deploy verification command: `scripts/smoke-prod.sh`
- rollback checklist:
  - identify previous Docker image tag
  - restore previous `docker-compose.prod.yml` env/image tag
  - restart compose
  - run smoke test
- operational checks:
  - `docker ps`
  - `docker logs`
  - Nginx config test
  - certificate status

- [ ] **Step 2: Cross-check against existing docs**

```bash
sed -n '1,220p' docs/aws-setup-guide.md
sed -n '1,220p' docs/devops-flows.md
sed -n '1,220p' infra/nginx/nginx.conf
```

Expected: the runbook does not contradict the production Nginx topology.

- [ ] **Step 3: Run smoke test again**

```bash
scripts/smoke-prod.sh
```

- [ ] **Step 4: Commit**

```bash
git add docs/production-runbook.md
git commit -m "docs(prod): document production runbook"
```

---

## Task 3: Protect Secrets and Runtime Files

**Purpose:** Improve safety without changing runtime behavior.

**Files:**
- Create: `.env.example`
- Modify: `.gitignore`

- [ ] **Step 1: Inspect current ignore rules**

```bash
sed -n '1,220p' .gitignore
git status --short
```

- [ ] **Step 2: Update `.gitignore` first**

Ensure these are ignored:
- `.env`
- `.env.*`
- `!.env.example`
- `.env.prod`
- `.env.local`
- `infra/data/`
- `.DS_Store`
- local IDE folders if not intentionally tracked

Do not remove existing ignore rules.

- [ ] **Step 3: Add `.env.example`**

Include placeholders only:
- AWS/ECR account and region
- backend DB variables
- S3 variables
- OAuth variables
- VAPID variables
- SMTP variables
- admin seed variables
- `NEXT_PUBLIC_BASE_URL`

Do not include real secrets.

- [ ] **Step 4: Verify no real secret values were introduced**

```bash
rg -n "secret|password|access-key|client-secret|AWS_SECRET|GITHUB_OAUTH" .env.example .gitignore
```

Expected: only placeholder names or empty/example values.

- [ ] **Step 5: Run smoke test**

```bash
scripts/smoke-prod.sh
```

- [ ] **Step 6: Commit**

```bash
git add .gitignore .env.example
git commit -m "chore(config): add safe env template and ignore runtime secrets"
```

---

## Task 4: Add Backend Health Check

**Purpose:** Add a stable health endpoint for Docker, Nginx, ALB, ECS, and future CloudWatch checks.

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/resources/application-prod.properties`

- [ ] **Step 1: Verify health endpoint is not available yet**

```bash
curl -i -sS https://anphuc.xyz/actuator/health | sed -n '1,40p'
```

Expected: likely `401`, `404`, or proxied frontend response. Record actual result in commit notes.

- [ ] **Step 2: Add `spring-boot-starter-actuator`**

Add the dependency to `backend/pom.xml`.

- [ ] **Step 3: Expose only health/info config**

In backend config, keep exposure minimal:

```properties
management.endpoints.web.exposure.include=health,info
management.endpoint.health.probes.enabled=true
management.health.mail.enabled=false
```

If DB health causes local/prod false negatives during boot, document it before disabling any contributor.

- [ ] **Step 4: Ensure security permits health only**

If `/actuator/health` is blocked by Spring Security, update `SecurityConfiguration` to permit only `GET /actuator/health` and optionally `/actuator/health/**`.

Do not permit all actuator endpoints.

- [ ] **Step 5: Test backend locally**

```bash
cd backend
./mvnw test
./mvnw compile
```

- [ ] **Step 6: Run production smoke test**

```bash
scripts/smoke-prod.sh
```

Expected: existing production is unchanged until deployment occurs.

- [ ] **Step 7: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.properties backend/src/main/resources/application-prod.properties backend/src/main/java/com/example/backend/auth/SecurityConfiguration.java
git commit -m "feat(ops): add backend health endpoint"
```

---

## Task 5: Add Non-Deploying CI

**Purpose:** Validate code before future AWS deploy automation, without touching production.

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Check existing workflows**

```bash
find .github -maxdepth 3 -type f -print
```

- [ ] **Step 2: Create CI workflow**

Workflow requirements:
- trigger on pull request and push to `master`
- backend job:
  - setup Java 21
  - cache Maven dependencies
  - run `cd backend && ./mvnw test`
- frontend job:
  - setup Node 20
  - run `cd frontend && npm ci`
  - run `npm run lint`
  - run `NEXT_PUBLIC_BASE_URL=https://anphuc.xyz npm run build`
- no AWS credentials
- no deployment steps

- [ ] **Step 3: Run equivalent commands locally**

```bash
cd backend && ./mvnw test
cd ../frontend && npm ci && npm run lint && NEXT_PUBLIC_BASE_URL=https://anphuc.xyz npm run build
```

- [ ] **Step 4: Run production smoke test**

```bash
scripts/smoke-prod.sh
```

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add non-deploying build and test workflow"
```

---

## Task 6: Add Cross-Platform Type Generation Script

**Purpose:** Make generated TypeScript client workflow work on macOS/Linux without breaking Windows users.

**Files:**
- Modify: `frontend/package.json`

- [ ] **Step 1: Verify current script behavior**

```bash
cd frontend
npm run generate-types
```

Expected on macOS/Linux: likely fails because it uses `mvnw.cmd`.

- [ ] **Step 2: Add Unix-specific scripts**

Keep existing Windows script intact. Add:

```json
"generate-types:unix": "cd ../backend && ./mvnw compile && ./mvnw typescript-generator:generate",
"update-types:unix": "npm run generate-types:unix && npm run copy-types"
```

Optionally rename the current script later in a separate task after verifying developer workflow.

- [ ] **Step 3: Run Unix update**

```bash
cd frontend
npm run update-types:unix
```

Expected: `frontend/models/backend.ts` is regenerated.

- [ ] **Step 4: Check generated diff**

```bash
git diff -- frontend/package.json frontend/models/backend.ts
```

Expected: only package script changes unless backend API has changed.

- [ ] **Step 5: Run smoke test**

```bash
scripts/smoke-prod.sh
```

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/models/backend.ts
git commit -m "chore(frontend): add Unix type generation script"
```

---

## Task 7: Document Local Development Ports

**Purpose:** Remove confusion between local Caddy, local backend, and production Nginx without changing production.

**Files:**
- Create: `docs/local-dev.md`
- Optionally Modify: `README.md`

- [ ] **Step 1: Document local services**

Include:
- MySQL: host `localhost`, port `3307`
- Mailpit SMTP: `1025`
- Mailpit UI: `8025`
- Backend direct: `8080`
- Frontend direct: `3000`
- Caddy local reverse proxy behavior and any known mismatch

- [ ] **Step 2: Add clear warning**

Document that production uses Nginx in `infra/nginx/nginx.conf`; local Caddy config is not the production source of truth.

- [ ] **Step 3: Link from README**

Add one short link to `docs/local-dev.md`.

- [ ] **Step 4: Run smoke test**

```bash
scripts/smoke-prod.sh
```

- [ ] **Step 5: Commit**

```bash
git add docs/local-dev.md README.md
git commit -m "docs(local): clarify development ports and proxy setup"
```

---

## Task 8: Review Gate Before Riskier Changes

**Purpose:** Stop before changing auth, production deployment, Docker routing, or secrets wiring.

- [ ] **Step 1: Run all verification**

```bash
scripts/smoke-prod.sh
cd backend && ./mvnw test
cd ../frontend && npm run lint && NEXT_PUBLIC_BASE_URL=https://anphuc.xyz npm run build
```

- [ ] **Step 2: Review completed work**

Per `AGENTS.md`, spawn or request a code review agent for completed work before continuing to riskier phases. If subagents are unavailable, perform a code-review pass focused on:
- production workflow regressions
- CI false positives/false negatives
- accidental secret exposure
- actuator endpoint overexposure
- docs contradicting current deployment

- [ ] **Step 3: Decide next phase**

Only after this gate, choose one:
- CI/CD deploy automation
- safer production env wiring
- CSRF/session hardening
- Docker Compose production cleanup
- CloudWatch/observability

Do not combine these into one task.

---

## Out of Scope for This Plan

These are valuable but intentionally deferred because they can break the live workflow:
- enabling CSRF in production
- replacing session auth with JWT
- changing Nginx routing
- changing production Docker Compose service names or ports
- rotating production secrets
- automatic production deployment from GitHub Actions
- moving static assets to CloudFront
- ECS Fargate migration

