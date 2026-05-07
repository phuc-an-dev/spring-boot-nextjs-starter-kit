# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

Learning repo for AWS/DevOps practice. A pre-built fullstack starter kit used as the application to deploy through various AWS infrastructure phases (EC2 → Docker Compose → RDS → ECS Fargate). See `LEARNING.md` for the AWS learning roadmap.

## Architecture

**Monorepo with two independent apps:**

- `backend/` — Spring Boot 3.4, Java 21, MySQL, Spring Security (session-based), JPA/Hibernate, QueryDSL, JobRunr (background jobs), AWS S3, Web Push notifications, OAuth2 (GitHub + Google)
- `frontend/` — Next.js 14 (App Router), TypeScript, Mantine UI, Tailwind CSS, Axios (with credentials/XSRF), SWR, React Hook Form + Zod
- `infra/` — Docker Compose for local dev: MySQL 8.4 on port 3306, Mailpit (SMTP) on ports 1025/8025, Caddy reverse proxy on port 8080

**Key cross-cutting concern:** TypeScript types are auto-generated from backend Java classes annotated with `@Client`. The generated file lands at `backend/target/typescript-generator/backend.ts` and is copied to `frontend/models/backend.ts`. The frontend's `lib/httpClient.ts` wraps Axios and instantiates `RestApplicationClient` from that generated file — all API calls go through `restClient`.

**Auth flow:** Session-based (cookie). CSRF is currently disabled (`AbstractHttpConfigurer::disable`) with a TODO for JWT. The frontend sets `withCredentials: true` and `xsrfCookieName: 'XSRF-TOKEN'`. OAuth2 success handler redirects to `app.login-success-url`.

**Backend package layout:**
- `auth/` — SecurityConfiguration, OAuth2 handler, AuthController
- `users/` — User entity, UserService, password reset, email verification, JobRunr jobs for async emails
- `admin/` — Admin-only user management
- `s3/` — File upload service
- `pushNotifications/` — Web Push (VAPID) subscriptions and delivery
- `util/` — `@Client` annotation (marks DTOs/controllers for TS generation), global exception handler, QueryDSL helpers

**Frontend route groups:**
- `(landing)/` — public + authenticated user pages (auth flows, profile)
- `(admin)/admin/` — admin-only pages

## Infrastructure (local dev)

Start infra before running backend:
```bash
cd infra
docker compose up -d
```
Services: MySQL at `localhost:3306`, Mailpit UI at `http://localhost:8025`, Caddy at `http://localhost:8080`.

Backend runs on port 8080 directly (not through Caddy in dev). Spring Docker Compose support is disabled (`spring.docker.compose.enabled=false`).

## Commands

### Backend
```bash
cd backend
./mvnw spring-boot:run          # run with dev profile
./mvnw test                     # run all tests
./mvnw -Dtest=ClassName test    # run single test class
./mvnw compile                  # compile only
```

### Frontend
```bash
cd frontend
npm install
npm run dev                     # dev server on port 3000
npm run build
npm run lint
npm run update-types            # regenerate TS types from backend (run after changing DTOs/controllers)
```

`update-types` runs the Maven `typescript-generator` plugin then copies the output. On Windows it uses `mvnw.cmd`; on Mac/Linux you may need to adjust `generate-types` script to use `./mvnw`.

## Configuration

Backend config lives in `backend/src/main/resources/application.properties`. Key properties to set for a real environment:
- `app.vapid-public-key` / `app.vapid-private-key` — Web Push (VAPID)
- `app.s3.*` — S3/compatible storage credentials
- `spring.security.oauth2.client.registration.github.*` / `.google.*` — OAuth2 keys
- `app.allowed-origins` — CORS (default: `http://localhost:3000`)
- `app.base-url` — backend base URL (default: `http://localhost:8080`)

Frontend requires `NEXT_PUBLIC_BASE_URL` env var pointing to the backend.

## TypeScript Type Generation Notes

Only classes/controllers annotated with `@com.example.backend.util.Client` are included in generation. After adding new DTOs or controller methods, run `npm run update-types` from the `frontend/` directory to keep the generated client in sync.

## Default Admin Account

Seeded on first startup: `admin@email.com` / `Password123` (configurable via `app.admin-user-email` / `app.admin-user-password`).

## Workflow Rules

- **Always write a plan first** before implementing any non-trivial feature. Save to `docs/superpowers/plans/YYYY-MM-DD-<feature>.md`. Use `superpowers:writing-plans` skill.
- **Always TDD**: write failing test → implement → verify green → commit.
- **Commit after each task** in a plan — never batch multiple tasks into one commit.
- **After completing a task group or before continuing to the next phase**, spawn a `superpowers:code-reviewer` agent to review the plan and all completed work. Fix critical issues before proceeding.
- **Update `.gitignore` first** before any task that introduces new secret files, build artifacts, or environment files.
