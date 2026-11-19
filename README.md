# YatraPass — Digital Tourist Permit & QR Verification System (Nepal)

A full-stack system for tourist permits to restricted areas:

- tourists register, apply for permits and upload documents;
- administrators review applications and issue digital permits, each with a secure QR code;
- administrators and officers scan the QR code at a checkpoint to verify the permit in real time.

> No real permit areas, rules or fees are included. Development builds seed clearly-labelled **SAMPLE** areas only.
> See [ASSUMPTIONS.md](ASSUMPTIONS.md) for every design decision.

---

## Contents

1. [Architecture](#architecture)
2. [Technologies](#technologies)
3. [Requirements](#requirements)
4. [Quick start (Docker)](#quick-start-docker)
5. [Environment variables](#environment-variables)
6. [Local development](#local-development)
7. [Database](#database)
8. [API documentation](#api-documentation)
9. [Development accounts](#development-accounts)
10. [Testing](#testing)
11. [QR verification workflow](#qr-verification-workflow)
12. [Security overview](#security-overview)
13. [Production deployment notes](#production-deployment-notes)
14. [Project structure](#project-structure)

---

## Architecture

```text
                   Browser (tourist / admin / officer phone)
                                  │  HTTPS
                                  ▼
             ┌────────────── frontend (nginx) ──────────────┐
             │  React SPA  ·  security headers  ·  /api/* ─┐ │
             └─────────────────────────────────────────────┼─┘
                                                           ▼
                              backend (Spring Boot 3, Java 21)
                     JWT auth · REST /api/v1 · Flyway · ZXing QR
                         │                          │
                         ▼                          ▼
                PostgreSQL 16 (volume)     upload storage (volume)
```

The browser talks to a single origin. In production nginx proxies `/api/*` to the backend; in development the Vite dev server does the same.

**Backend layering:** controllers are thin and DTO-only, services hold the business rules, transactions and state machines, and Spring Data repositories do the persistence. JPA entities are never exposed by the API.

**Permit lifecycle** (enforced in the service layer; invalid transitions return HTTP 409):

```text
Application: PENDING → UNDER_REVIEW → APPROVED          Permit: ACTIVE → EXPIRED
                 │            ├──────→ REJECTED                    ├────→ CANCELLED
                 ├──→ REJECTED└──────→ CANCELLED                   └────→ COMPLETED
                 └──→ CANCELLED
```

## Technologies

| Layer | Stack |
|---|---|
| Backend | Java 21, Spring Boot 3.5 (Web, Data JPA/Hibernate 6, Security, Validation, Actuator), JJWT, ZXing, springdoc-openapi, Flyway, PostgreSQL driver, Maven Wrapper |
| Tests | JUnit 5, Mockito, Spring Security Test, MockMvc, Testcontainers (PostgreSQL 16) |
| Frontend | React 19, TypeScript, Vite, React Router 7, Axios, html5-qrcode |
| Infrastructure | Docker, Docker Compose, PostgreSQL 16, nginx (unprivileged) |

## Requirements

- **Docker** with Compose v2. This is all you need to run the full stack.
- For local development without Docker images:
  - JDK 21 or newer. Maven is not needed because the project includes `./mvnw`.
  - Node.js 20.19 or newer.
  - Docker, needed for PostgreSQL and the Testcontainers-based tests.

## Quick start (Docker)

```bash
cp .env.example .env
# Edit .env: set DB_PASSWORD and generate the two keys:
#   JWT_SECRET:               openssl rand -base64 48
#   QR_TOKEN_ENCRYPTION_KEY:  openssl rand -base64 32
# For a local demo also set:
#   SPRING_PROFILES_ACTIVE=dev   (seeds SAMPLE areas, enables Swagger)
#   SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD (>= 12 chars)
#   SEED_OFFICER_EMAIL / SEED_OFFICER_PASSWORD (>= 12 chars)

docker compose up -d --build
```

| Service | URL |
|---|---|
| Web app | http://localhost:8081 |
| Swagger UI (dev profile) | http://localhost:8081/swagger-ui.html |
| Health | http://localhost:8081/api/v1/… via nginx, or `docker compose ps` |
| PostgreSQL | `127.0.0.1:${DB_HOST_PORT}` (loopback only) |

If port 5432 is unavailable on your machine, set `DB_HOST_PORT` (for example `15432`).

Stop the stack with `docker compose down`. Add `-v` to also delete the database and uploads volumes.

## Environment variables

All configuration comes from environment variables, and no secrets are committed. The backend refuses to start if `JWT_SECRET` or `QR_TOKEN_ENCRYPTION_KEY` is missing or too short.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` | | `localhost` / `5432` / `tourist_permit` | PostgreSQL location (compose sets host `postgres`) |
| `DB_USERNAME` / `DB_PASSWORD` | ✔ | – | Database credentials |
| `JWT_SECRET` | ✔ | – | Base64 HMAC key, ≥ 32 bytes decoded |
| `QR_TOKEN_ENCRYPTION_KEY` | ✔ | – | Base64 AES-256 key, exactly 32 bytes decoded |
| `APP_BASE_URL` | | `http://localhost:5173` | Public frontend URL; embedded in QR codes as `<APP_BASE_URL>/verify/<token>` |
| `CORS_ALLOWED_ORIGINS` | | `http://localhost:5173` | Comma-separated allowed origins |
| `FILE_STORAGE_PATH` | | `./data/uploads` | Upload directory (compose: volume `/app/data/uploads`) |
| `MAX_UPLOAD_SIZE` | | `5MB` | Max document size |
| `SPRING_PROFILES_ACTIVE` | | `prod` | `dev` seeds SAMPLE areas and enables Swagger |
| `API_DOCS_ENABLED` | | `false` | Enable OpenAPI/Swagger outside `dev` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | | `PT15M` / `P7D` | Token lifetimes (ISO-8601) |
| `LOGIN_MAX_ATTEMPTS` / `LOGIN_RATE_WINDOW` | | `10` / `PT5M` | Failed-login limit per IP and per account |
| `ALLOW_OVERLAPPING_PERMITS` | | `false` | Allow overlapping permits for the same area |
| `PERMIT_EXPIRY_CRON` | | `0 5 0 * * *` | Expiry job schedule (Asia/Kathmandu) |
| `VERIFICATION_LOCATION_ENABLED` | | `false` | Store scanner latitude/longitude |
| `ROUTING_OSRM_URL` | | `https://router.project-osrm.org` | OSRM-compatible routing service (public demo has usage limits — self-host for production) |
| `ROUTING_ORIGIN_LABEL` / `_LAT` / `_LNG` | | `Kathmandu` / `27.7172` / `85.3240` | Default route start point |
| `ROUTING_TIMEOUT` / `ROUTING_CACHE_TTL` | | `PT10S` / `PT6H` | Routing request timeout and cache lifetime |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` | | – | Create an admin at startup if it does not exist |
| `SEED_OFFICER_EMAIL` / `SEED_OFFICER_PASSWORD` | | – | Create an officer at startup if it does not exist |
| `FRONTEND_PORT` | | `8081` | Host port for the web app (compose) |
| `DB_HOST_PORT` | | `5432` | Host port for PostgreSQL (compose, loopback only) |

## Local development

**1. Database**

```bash
docker compose up -d postgres
```

**2. Backend** (port 8080)

```bash
cd backend
export DB_HOST=localhost DB_PORT=${DB_HOST_PORT:-5432} DB_NAME=tourist_permit \
       DB_USERNAME=tourist_permit DB_PASSWORD='<from .env>' \
       JWT_SECRET='<from .env>' QR_TOKEN_ENCRYPTION_KEY='<from .env>' \
       SPRING_PROFILES_ACTIVE=dev APP_BASE_URL=http://localhost:5173 \
       SEED_ADMIN_EMAIL=admin@dev.local SEED_ADMIN_PASSWORD='<dev-only password>'
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

**3. Frontend** (port 5173; proxies `/api` to `localhost:8080`)

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Browsers only allow camera access on HTTPS or `localhost`. To test the scanner on a phone, serve over HTTPS, for example through a tunnel or a reverse proxy with TLS.

## Database

PostgreSQL schema is managed **only by Flyway** (`backend/src/main/resources/db/migration`). Hibernate is set to `ddl-auto: validate` and never changes the schema.

| Migration | Contents |
|---|---|
| V1 | `users` (roles `TOURIST`/`ADMIN`/`OFFICER`, lower-case unique email) |
| V2 | `tourist_profiles` (1:1 user, unique passport) |
| V3 | `permit_areas` (unique code, max days > 0, fee ≥ 0) |
| V4 | `permit_applications` + `application_number_seq` (status/date checks, indexes) |
| V5 | `permit_documents` (metadata only; files in storage) |
| V6 | `permits` + `permit_number_seq` (unique application, status/date checks) |
| V7 | `verification_tokens` (hash + encrypted token, ≤ 1 ACTIVE per permit via partial unique index) |
| V8 | `permit_verifications` (nullable permit for unknown codes, result checks) |
| V9 | `refresh_tokens` (hashed, revocable) |
| V10 | `audit_logs` (JSONB metadata) |
| V11 | destination photo columns on `permit_areas` (file stored in upload storage) |
| V12 | `road_conditions` (status, POINT/LINE coordinates as JSONB, optional area) |
| V13 | destination pin (`latitude`/`longitude`) on `permit_areas` |

Reference numbers look like `APP-2026-000001` and `PRM-2026-000001`. They come from database sequences and are for display only. They are never used as security tokens.

## API documentation

With the `dev` profile, or `API_DOCS_ENABLED=true`, interactive docs are available at `/swagger-ui.html` and the OpenAPI JSON at `/v3/api-docs`. Use `POST /api/v1/auth/login`, then **Authorize** with the `accessToken`.

| Area | Endpoints (prefix `/api/v1`) | Role |
|---|---|---|
| Auth | `POST auth/register`, `auth/login`, `auth/refresh`, `auth/logout` | public |
| Areas | `GET areas`, `GET areas/{id}`, `GET areas/{id}/photo` | public |
| Profile | `GET/PUT tourists/me` | TOURIST |
| Applications | `POST/GET applications`, `GET applications/{id}`, `POST applications/{id}/cancel`, `POST/GET applications/{id}/documents`, `GET …/documents/{docId}/content`, `DELETE …/documents/{docId}` | TOURIST |
| Permits | `GET permits`, `GET permits/{id}`, `GET permits/{id}/qr[?download=true]` | TOURIST |
| **Verify QR** | `GET permits/verify/{token}[?areaId=&latitude=&longitude=]` | ADMIN, OFFICER |
| Dashboard | `GET admin/dashboard` | ADMIN |
| Tourists | `GET admin/tourists[?q=]`, `GET admin/tourists/{id}`, `PATCH admin/tourists/{id}/status` | ADMIN |
| Review | `GET admin/applications[?q=&status=]`, `GET admin/applications/{id}`, `POST …/{id}/review`, `POST …/{id}/approve`, `POST …/{id}/reject` | ADMIN |
| Documents | `GET admin/documents/{id}/content`, `POST admin/documents/{id}/verify` | ADMIN |
| Permits | `GET admin/permits[?q=&status=]`, `GET admin/permits/expiring?days=`, `GET admin/permits/{id}`, `POST …/{id}/cancel`, `POST …/{id}/complete`, `GET …/{id}/qr`, `POST …/{id}/qr/regenerate`, `POST …/{id}/qr/revoke` | ADMIN |
| Areas | `GET/POST admin/areas`, `GET/PUT admin/areas/{id}`, `PATCH admin/areas/{id}/status`, `PUT/DELETE admin/areas/{id}/photo` (JPEG/PNG/WebP) | ADMIN |
| History | `GET verifications[?result=&permitId=&from=&to=]` | ADMIN |
| Road conditions (map) | `GET road-conditions[?areaId=&includeGeneral=]` | any signed-in user |
| Driving route | `GET areas/{id}/route[?fromLat=&fromLng=]` (OSRM, cached, simplified) | any signed-in user |
| Road conditions (manage) | `GET/POST admin/road-conditions`, `GET/PUT/DELETE admin/road-conditions/{id}` (POINT or LINE geometry) | ADMIN |
| Audit | `GET admin/audit-logs[?action=&entityType=&entityId=]` | ADMIN |

All list endpoints are paginated: `?page=0&size=20&sort=field,desc`. The maximum page size is 100, and `sort` only accepts each endpoint's allow-listed fields. Search (`q`) matches permit number, application number, tourist name, passport number and area.

**Error format** (every error, no stack traces):

```json
{ "timestamp": "2026-10-07T12:00:00Z", "status": 404, "error": "NOT_FOUND",
  "message": "Permit not found", "path": "/api/v1/permits/123" }
```

Status codes: `400` validation, `401` unauthenticated, `403` forbidden, `404` not found or not yours, `409` conflict or invalid state transition, `413` file too large, `415` file type rejected, `422` business rule violated, `429` too many login attempts, `500` unexpected.

## Development accounts

> ⚠️ **DEVELOPMENT ONLY.** No accounts or passwords are hard-coded anywhere.

- **Admin/officer:** set `SEED_ADMIN_*` / `SEED_OFFICER_*` in your local `.env` with your own passwords (≥ 12 characters). They are created at startup only if missing. For example: `admin@dev.local` / `officer@dev.local` with passwords you choose.
- **Tourists:** register through the web app at `/register`.
- **Sample areas:** `SAMPLE-RA`, `SAMPLE-HZ`, `SAMPLE-CA`. Seeded only with `SPRING_PROFILES_ACTIVE=dev`.

## Testing

```bash
cd backend
./mvnw verify            # unit tests (Surefire) + integration tests (Failsafe, Testcontainers PostgreSQL)

cd ../frontend
npm run typecheck && npm run build
```

**End-to-end smoke test** against a running dev stack (`SPRING_PROFILES_ACTIVE=dev`, seed accounts set). It runs the full tourist → admin → officer scenario through nginx and decodes the real QR PNG with ZXing. It needs Python 3, Java, and the ZXing jars in `~/.m2`, which `./mvnw verify` downloads:

```bash
python scripts/e2e/e2e.py .
```

Docker must be running for the integration tests. Current suite: **49 unit + 84 integration tests**, covering:

| Area | Covered |
|---|---|
| Authentication | registration, duplicate email, login, invalid password, unknown user, disabled user, refresh rotation and reuse detection, logout, tampered/expired JWTs, client-supplied role ignored |
| Applications | creation, profile completeness, inactive area, end before start, max-days, unknown area, overlap (application and active permit), cancel, review, reject, invalid transitions |
| Permits | approval → permit, passport required, rejected-cannot-approve, double approval, cancellation, completion, expiry job, ownership isolation, expiring list |
| QR | real PNG decoded with ZXing, only URL in QR, token hashed and encrypted at rest, valid, invalid/malformed, expired (before and after job), cancelled, completed, not-yet-valid, revoked and regenerated, area mismatch, logging of every scan, location opt-in |
| Security | full role × endpoint matrix, officer scope, admin cannot act as tourist or edit identity data, tourist isolation, sort allow-list, LIKE-wildcard escaping, upload magic-byte validation, no stack traces, actuator exposure, security headers |

## QR verification workflow

```text
Admin approves application
  └─ Permit PRM-YYYY-NNNNNN created (ACTIVE)
  └─ token = 32 random bytes (SecureRandom) → base64url (43 chars)
       DB: SHA-256(token)  +  AES-256-GCM(token, aad=permit id)      ← plaintext never stored
  └─ QR (ZXing PNG) = "<APP_BASE_URL>/verify/<token>"                 ← no personal data

Tourist → "My Permits" → shows / downloads QR (served with Cache-Control: no-store)

Officer → /admin/scanner → camera (html5-qrcode) → extracts token
  └─ GET /api/v1/permits/verify/{token}
       1. format valid?           else INVALID_TOKEN
       2. SHA-256 lookup          else INVALID_TOKEN
       3. token revoked?          → TOKEN_REVOKED
       4. permit cancelled?       → PERMIT_CANCELLED
       5. expired / past end?     → PERMIT_EXPIRED
       6. not ACTIVE?             → PERMIT_NOT_ACTIVE
       7. before start date?      → PERMIT_NOT_YET_VALID
       8. checkpoint area given & different? → AREA_MISMATCH
       9. otherwise               → VALID (permit no., name, nationality, area, dates)
  └─ every attempt is written to permit_verifications (+ audit QR_VERIFIED)
```

**Rotating a compromised QR:** on the admin permit page, click **Regenerate QR**. The old token is marked `REVOKED` and immediately verifies as `TOKEN_REVOKED`. The tourist's page shows the new QR.

If anyone scans the QR with an ordinary phone camera, it opens `/verify/<token>`. Signed-in officers and admins see the result there. Everyone else sees a neutral notice and no permit data.

## Security overview

- **Passwords:** BCrypt (cost 12). Login runs in constant time whether or not the user exists. Failed logins are rate-limited per IP and per account.
- **JWT:** HS256, 15-minute access tokens. The user and role are reloaded from the database on every request; client-supplied roles are ignored.
- **Refresh tokens:** opaque, stored as SHA-256 hashes, rotated on every use, with reuse detection and revocation on logout and on account disable.
- **Authorization:** URL rules, plus `@PreAuthorize` on every controller, plus ownership checks in services. Requests for other users' resources return `404` to avoid leaking which IDs exist.
- **Validation:** Bean Validation on all DTOs. Queries are parameterised through JPA. LIKE wildcards are escaped, and sorting uses allow-listed fields only.
- **Uploads:** checks for extension allow-list, declared MIME type, and actual magic bytes (PDF/PNG/JPEG). Size and count are limited. Files are stored under random UUID names with a path-traversal guard. Original filenames are sanitised and used for display only. Downloads require ownership or the ADMIN role and are served as attachments with `no-store`.
- **Headers:** CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, HSTS (backend), and `Permissions-Policy` (camera and geolocation for this origin only).
- **CORS:** explicit origin allow-list only.
- **Logging:** never logs passwords, JWTs, refresh or QR tokens, or passport numbers. Request DTOs override `toString()`, and DB constraint messages are not echoed back to clients.
- **Errors:** centralised in `@RestControllerAdvice`. Stack traces are never returned.
- **Actuator:** only `/actuator/health` is exposed, with no details.

## Production deployment notes

1. **TLS is mandatory.** Terminate HTTPS in front of the frontend container. The camera also requires HTTPS. Set `APP_BASE_URL` to the public HTTPS URL **before** issuing permits, because it is embedded in every QR.
2. **Secrets:** generate unique `JWT_SECRET`, `QR_TOKEN_ENCRYPTION_KEY` and `DB_PASSWORD`, and keep them in a secret manager rather than a plain `.env`.
   - Rotating `JWT_SECRET` logs everyone out.
   - Rotating `QR_TOKEN_ENCRYPTION_KEY` means every permit's QR must be regenerated, so plan a re-issue.
3. **Profiles:** run with `SPRING_PROFILES_ACTIVE=prod` (the default) and `API_DOCS_ENABLED=false`. Never use `dev` in production, because it seeds SAMPLE data.
4. **Bootstrap:** set `SEED_ADMIN_*` for the first start only, then remove it from the environment.
5. **Database:** remove the `ports:` mapping of `postgres` in production, or use a managed PostgreSQL. Back up the database **and** the uploads volume.
6. **Scaling out:** login rate limiting is in-memory, and uploads use the local filesystem. For more than one backend instance, put rate limiting at the proxy or in Redis, and use shared object storage such as S3. Run the expiry job on one instance, or add a scheduler lock such as ShedLock.
7. **Reverse proxy:** the backend trusts `X-Forwarded-*` only from private-network proxies (Tomcat RemoteIpValve defaults). Logged IPs are correct behind the bundled nginx.
8. **Monitoring:** `/actuator/health` backs the container healthcheck. Ship logs to your log platform; audit events are also logged at INFO.

## Project structure

```text
backend/src/main/java/com/example/touristpermit/
├── config/         AppProperties, clock, OpenAPI, bootstrap & dev sample data
├── security/       SecurityConfig, JWT service/filter, rate limiter, CurrentUser
├── auth/           register/login/refresh/logout, refresh tokens
├── user/           User, Role
├── tourist/        profiles (tourist + admin views)
├── area/           permit areas
├── application/    applications + state machine
├── document/       uploads, validation, file storage
├── permit/         permits, lifecycle, expiry job
├── qr/             tokens, encryption, QR rendering, regenerate/revoke
├── verification/   verify endpoint, scan log, history
├── dashboard/      admin statistics
├── audit/          audit trail
└── common/         exceptions, error envelope, paging, utilities

frontend/src/
├── api/            axios client (token refresh) + typed endpoints
├── components/     UI kit, QR image, verification result panel, dialogs
├── hooks/          auth context, async loader, debounce
├── layouts/        app shell (role-aware navigation)
├── pages/          auth/, tourist/, admin/ (incl. ScannerPage), VerifyLandingPage
├── routes/         protected routes, route table
├── services/       token storage
├── types/          DTO types
└── utils/          formatting, errors, QR token extraction
```
