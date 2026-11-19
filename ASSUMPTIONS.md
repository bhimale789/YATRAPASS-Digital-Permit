# Assumptions & Design Decisions

Decisions made where the specification left room for interpretation.

## Domain

1. **No real permit data.** No real Nepal permit areas, rules or fees are included. The `dev` profile seeds three areas named `Sample …` with codes `SAMPLE-*`, labelled as sample data in their description. Production areas must be entered by an administrator from official sources.
2. **Profile completion before applying.** Registration only asks for name, email and password. Passport number, nationality, date of birth and phone are required before an application can be submitted (HTTP 422 otherwise).
3. **Passport document required for approval.** An application can only be approved once at least one `PASSPORT` document has been uploaded. Other document types (`VISA`, `PHOTO`, `TRAVEL_INSURANCE`, `ITINERARY`, `OTHER`) are optional.
4. **Approval from PENDING.** The lifecycle is `PENDING → UNDER_REVIEW → APPROVED`. If an admin approves a `PENDING` application directly, the system moves it through `UNDER_REVIEW` first in the same transaction, so the lifecycle stays complete. `PENDING → APPROVED` is never stored directly.
5. **Approved application vs. active permit.** Approving an application sets it to `APPROVED` and creates a separate `Permit` record with status `ACTIVE`. Permit statuses (`ACTIVE`, `EXPIRED`, `CANCELLED`, `COMPLETED`) are tracked on the permit. All non-`ACTIVE` permit statuses are terminal.
6. **Validity dates.** A permit is valid from `requested_start_date` to `requested_end_date` inclusive. Calendar dates are evaluated in **Asia/Kathmandu** time.
7. **Overlap rule.** A tourist cannot have an open application (`PENDING`/`UNDER_REVIEW`) or an `ACTIVE` permit for the **same area** with overlapping dates. Overlaps in *different* areas are allowed. Set `ALLOW_OVERLAPPING_PERMITS=true` to disable the rule. The check runs again at approval time to guard against races.
8. **Start dates.** An application's start date cannot be in the past. Approval is refused if the requested end date has already passed.
9. **Expiry.** A scheduled job (`PERMIT_EXPIRY_CRON`, daily 00:05 NPT by default, and also once at startup) moves `ACTIVE` permits whose end date has passed to `EXPIRED`. Verification also checks dates directly, so a permit is never reported valid after its end date, even before the job runs.
10. **Cancellation keeps the QR token.** Cancelling a permit does not revoke its QR token, so a scan reports the more useful `PERMIT_CANCELLED` instead of `TOKEN_REVOKED`. The permit can never verify as valid again because `CANCELLED` is terminal.

## QR verification

11. **Token storage — hash plus encrypted copy.** A QR token is 32 bytes from `SecureRandom` (256 bits), base64url-encoded. The database stores:
    - `SHA-256(token)`, used to look up the token during verification;
    - an AES-256-GCM ciphertext of the token, keyed by `QR_TOKEN_ENCRYPTION_KEY`, with the permit id as associated data. This lets the server re-render the tourist's QR image on demand.

    A database dump alone cannot be used to produce working QR codes. The alternative was to store QR PNG files on disk, which would put plaintext tokens on the filesystem, so it was rejected.
12. **QR content.** The QR encodes only `${APP_BASE_URL}/verify/<token>`. If anyone scans it with an ordinary phone camera, the SPA shows a neutral notice and no permit data. Verification only runs for signed-in `ADMIN`/`OFFICER` users.
13. **Additional verification reasons.** Besides the reasons listed in the spec, two more are returned: `PERMIT_NOT_YET_VALID` (scanned before the start date) and `AREA_MISMATCH` (the scanner selected a checkpoint area and the permit is for a different area). The checkpoint area is optional.
14. **Verification log result.** `permit_verifications.result` is one of `VALID`, `INVALID`, `EXPIRED`, `CANCELLED`. The detailed reason is stored in `failure_reason`. Codes that don't match any permit are logged with `permit_id = NULL`. The scanner sends unrecognised QR content as a placeholder token, so those attempts are logged too.
15. **Safe response content.** Tourist name and nationality appear only in `VALID` responses. Passport number, date of birth, phone and address are never returned. Invalid results for a known permit include the permit number, area and dates to help the officer. A revoked token returns only the reason.
16. **Location.** Scanners may send latitude/longitude, which are stored only when `VERIFICATION_LOCATION_ENABLED=true`. Otherwise they are discarded.
17. **GET with side effects.** The spec asks for `GET /api/v1/permits/verify/{token}`. That endpoint writes a log entry, so the response is sent with `Cache-Control: no-store`.

## Security

18. **Token transport.** Access tokens are short-lived (15 min) HS256 JWTs, kept only in memory by the SPA. Refresh tokens are opaque random values, stored hashed on the server, rotated on every use, and revoked on logout. Presenting a revoked refresh token revokes all of that user's sessions (theft detection). The SPA keeps the refresh token in `localStorage` so sessions survive a page reload. The strict CSP (`script-src 'self'`) reduces the XSS risk this introduces. If you need stronger isolation, move the refresh token to an `HttpOnly; Secure; SameSite=Strict` cookie and add CSRF protection to `/auth/refresh`.
19. **Roles from the database.** On every request the user is reloaded by id from the JWT subject. The role and enabled flag come from the database, never from the token or the client, so disabling an account takes effect immediately.
20. **Staff accounts.** `ADMIN`/`OFFICER` accounts cannot be self-registered. They are created at startup from the `SEED_*` environment variables, which have no defaults and require passwords of at least 12 characters. There is currently no UI for staff user management.
21. **Officer scope.** `OFFICER` can only use the scanner and verification endpoint. Verification history, the dashboard and all other admin functions are `ADMIN`-only.
22. **Admins cannot edit tourist identity data.** Admins can view full tourist details and enable/disable accounts. Only the tourist can change their own profile.
23. **Rate limiting.** Failed logins are limited per IP and per account (default 10 per 5 minutes) by an in-memory limiter. That is enough for a single instance. Multi-instance deployments should use a shared limiter, for example at the reverse proxy or backed by Redis.
24. **File storage.** Uploads go to the local filesystem under `FILE_STORAGE_PATH`, a Docker volume. Multi-instance deployments should replace `FileStorageService` with an object-store implementation.
25. **Sorting.** Paged endpoints accept `sort` only for an explicit allow-list of properties. This prevents ordering-based inference of sensitive joined fields.

## Technical

26. **Java version.** The project targets Java 21 (`maven.compiler.release=21`) and the Docker image runs on Temurin 21. Local development also works with newer JDKs. This project was verified with JDK 26.
27. **Lombok not used.** The spec marks Lombok as optional. Plain Java (records for DTOs) avoids annotation-processor compatibility issues with new JDKs.
28. **Tests use real PostgreSQL.** Integration tests run against PostgreSQL 16 via Testcontainers, so Flyway migrations and SQL are exercised on the production engine. They require a running Docker daemon.
29. **Extra tables.** Beyond the spec's list, migrations add `refresh_tokens` (V9) and `audit_logs` (V10).

## Maps & routes

30. **Maps use OpenStreetMap** (Leaflet), which needs no API key. Map tiles are the only external resource the browser loads, and the CSP is widened for `tile.openstreetmap.org` images only.
31. **Driving routes are computed by OSRM through the backend.** The browser never calls the routing service directly. Results are cached for 6 hours (500 entries), and the full geometry is simplified to within 15 m before it is sent to the browser. The default `https://router.project-osrm.org` is a public demo server with usage limits, so production should self-host OSRM and set `ROUTING_OSRM_URL`.
32. **Route start point.** Routes start at **Kathmandu** (27.7172, 85.3240) by default, because most visitors arrive there. Tourists can choose "Use my location" instead. That location is used only for that request and is neither stored nor logged.
33. **Routes follow drivable roads only.** Trekking sections beyond the road head are not shown. A destination's route endpoint is the pin an admin places on the Areas page. Destinations without a pin show road conditions only.
