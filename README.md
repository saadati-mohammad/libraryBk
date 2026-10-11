# libraryBk — Library Management Backend

Spring Boot 4.1.1 (Java 21) backend for the Library management system. Provides REST APIs
for books, members (persons), loans, reservations, chat/messages with WebSocket (STOMP),
file attachments, and Excel import.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1.1 (Web MVC, Data JPA, WebSocket/STOMP, Security, Validation) |
| Database | H2 in development/tests; persistent MySQL in the production profile |
| Schema | Flyway (`src/main/resources/db/migration/h2` in development/tests; `.../mysql` in production), Hibernate `ddl-auto=validate` |
| Auditing | Hibernate Envers |
| API docs | springdoc-openapi 3.1.1 (Swagger UI at `/swagger-ui.html`) |
| Build | Maven (`./mvnw`) |
| Tests | JUnit 5, Spring Boot integration tests, and H2 |

## Prerequisites

- JDK 21
- No local Maven required — use the bundled wrapper `./mvnw`

## Running locally

```bash
# Development profile (H2 in-memory, H2 console disabled by default)
./mvnw spring-boot:run
```

The API is served on `http://localhost:8079` (override with `SERVER_PORT`). Swagger UI:
`http://localhost:8079/swagger-ui.html`.

The default active profile is `development` (see `application.properties`). Override with:

```bash
SPRING_PROFILES_ACTIVE=production DB_URL='jdbc:mysql://<host>:3306/<database>?useSSL=true&serverTimezone=UTC' DB_USERNAME='<database-user>' DB_PASSWORD='<secret>' JWT_SECRET='<at-least-32-random-bytes>' APP_ADMIN_USERNAME='<operator>' APP_ADMIN_PASSWORD='<secret>' ./mvnw spring-boot:run
```

The production profile requires a persistent MySQL URL and credentials; it cannot fall
back to ephemeral H2. Migrations are validated against H2 in CI. A live MySQL migration
run still requires a configured database service.

> Without network access the wrapper cannot download Maven; use a local Maven installation
> instead (e.g. `mvn -o -B spring-boot:run`).

## Configuration & secrets

Configuration is externalized to environment variables — **no secrets are committed**.
See [`.env.example`](.env.example) for the full list. Key variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `SERVER_PORT` | HTTP port | `8079` |
| `SPRING_PROFILES_ACTIVE` | Active profile | `development` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Datasource | in-memory H2 in development; required MySQL connection in production |
| `JPA_DDL_AUTO` / `JPA_DIALECT` | Hibernate schema handling | `validate`; H2 in development, MySQL in production |
| `FLYWAY_ENABLED` | Run migrations on startup | `true` |
| `JWT_SECRET` | JWT signing secret (≥32 bytes; required in production) | empty |
| `JWT_EXPIRATION` | Token lifetime in seconds | `86400` |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | Operator login for the API (required in production) | `admin` / empty |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:4200,...` |
| `WS_ALLOWED_ORIGINS` | WebSocket allowed origins | `http://localhost:4200,...` |
| `UPLOAD_DIR` | Attachment storage directory | `${user.home}/chat-uploads` |
| `H2_CONSOLE_ENABLED` | Expose the H2 web console | `false` |
| `CLEANUP_ENABLED` / `CLEANUP_CRON` | Incomplete-upload cleanup job | `true` / `0 0 2 * * *` |
| `APP_LICENSE_ENABLED` | Enable the optional machine-bound license gate | `false` |

Profiles:

- `application-development.properties` — H2 console on, SQL debug logging, local CORS.
- `application-production.properties` — persistent MySQL required, H2 console off, quiet logging, and explicit CORS/JWT/admin secrets required.

> **Migrations:** Flyway owns the schema. Development and tests use
> `src/main/resources/db/migration/h2` (V1–V4); production selects
> `src/main/resources/db/migration/mysql` (V1–V3). The provider-specific baselines
> create the initial schema; later migrations remove stale binary audit columns, widen
> file columns, and create member accounts. Hibernate runs with
> `spring.jpa.hibernate.ddl-auto=validate`, so it only checks that the entities match the
> migrated schema — it never alters it. Every schema change must be a new `V<n>__*.sql`
> migration, and an already-applied migration must never be edited (Flyway checksums are
> immutable).

## Testing

```bash
./mvnw test
```

Integration tests boot the real context against an in-memory H2 database with the same
Flyway migrations the application uses (`src/test/resources/application-test.properties`).
The suite also covers metadata provider mapping and the short-lived phone scanner relay.

## Authentication

The API is **default-deny**. Every `/api/**` endpoint requires a bearer token obtained from
`POST /api/auth/login`; only `/api/auth/login`, the Swagger UI, and static resources are
public. The WebSocket/STOMP broker authenticates the CONNECT frame with the same token —
there is no client-supplied identity.

Chat and attachment endpoints are additionally **scoped to the authenticated principal**: the
caller must be the sender or the recipient of the message behind a conversation, message or
attachment, and only a recipient may mark a message read. A denial is `403` with the standard
`ApiResponse` envelope.

```bash
# Obtain a token
curl -s -X POST http://localhost:8079/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'

# Call a protected endpoint
curl -s "http://localhost:8079/api/book?page=0&size=1" -H "Authorization: Bearer <token>"

# Dashboard aggregates for the UI
curl -s http://localhost:8079/api/dashboard/summary -H "Authorization: Bearer <token>"
```

Credentials are supplied via `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD`, BCrypt-hashed in
memory at startup and never persisted. In the `production` profile these (and `JWT_SECRET`)
have **no default** — the application refuses to start if they are unset.

## Book metadata and phone scanner APIs

Staff can search book metadata by ISBN or by title/author through the configured Open
Library and Google Books endpoints. Results are merged and deduplicated on a best-effort
basis; provider catalogs may be unavailable or incomplete. Configure the provider base URLs
and optional Google Books key with `OPEN_LIBRARY_BASE_URL`, `GOOGLE_BOOKS_BASE_URL`, and
`GOOGLE_BOOKS_API_KEY`.

The desktop scanner-session endpoints require staff authentication:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/book/scanner-sessions` | Create a temporary phone pairing session |
| `GET` | `/api/book/scanner-sessions/{id}/scans?afterSequence=0` | Poll scans waiting for the desktop form |
| `DELETE` | `/api/book/scanner-sessions/{id}` | Close a session |

The phone submits a valid ISBN to `POST /api/phone-scanner/sessions/{id}/scans` using the
session pairing token. This endpoint is public only in the sense that the phone does not
need a staff JWT: the random, high-entropy token authorizes that single short-lived session.
Sessions expire after 10 minutes, keep a bounded in-memory scan queue, suppress rapid
duplicate reads, and are invalidated when the service restarts. Do not log or share the
pairing URL. The phone PWA camera requires HTTPS; keyboard-emulating USB/Bluetooth scanners
can enter ISBNs without camera permission.

## Notes on behaviour preserved from earlier versions

- The optional **license gate** (`KeyPromptValidator`) previously ran unconditionally on
  startup and called `System.exit()` when no interactive key was supplied, which prevented
  the app from starting in any headless environment. It is now **opt-in** via
  `app.license.enabled=true` and reads the key from `APP_LICENSE_KEY`.
- CORS is configured centrally (`CorsConfig`); per-controller `@CrossOrigin("*")` wildcards
  were removed because they overrode the central policy.
