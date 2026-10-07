# libraryBk — Library Management Backend

Spring Boot 3.4 (Java 21) backend for the Library management system. Provides REST APIs
for books, members (persons), loans, reservations, chat/messages with WebSocket (STOMP),
file attachments, and Excel import.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.4.2 (Web, Data JPA, WebSocket/STOMP, Validation, Mail) |
| Database | H2 (default, in-memory); MySQL supported via config |
| Auditing | Hibernate Envers |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Build | Maven (`./mvnw`) |

## Prerequisites

- JDK 21
- No local Maven required — use the bundled wrapper `./mvnw`

## Running locally

```bash
# Development profile (H2 in-memory, H2 console enabled, debug SQL)
./mvnw spring-boot:run
```

The API is served on `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

The default active profile is `development` (see `application.properties`). Override with:

```bash
SPRING_PROFILES_ACTIVE=production ./mvnw spring-boot:run
```

## Configuration & secrets

Configuration is externalized to environment variables — **no secrets are committed**.
See [`.env.example`](.env.example) for the full list. Key variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | Datasource | in-memory H2 |
| `JWT_SECRET` | Signing secret (required in production) | empty |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins | `http://localhost:4200,...` |
| `WS_ALLOWED_ORIGINS` | WebSocket allowed origins | `http://localhost:4200,...` |
| `MAIL_*` | SMTP settings | placeholders |
| `UPLOAD_DIR` | Attachment storage directory | `${user.home}/chat-uploads` |
| `APP_LICENSE_ENABLED` | Enable the optional machine-bound license gate | `false` |

Profiles:

- `application-development.properties` — H2 console on, SQL debug logging, local CORS.
- `application-production.properties` — H2 console off, quiet logging, no default CORS/secret.

> **Migrations:** the project currently relies on Hibernate `ddl-auto` (default `update`).
> Introduce Flyway/Liquibase and set `JPA_DDL_AUTO=validate` before going to production with
> data you care about.

## Testing

```bash
./mvnw test
```

## Notes on behaviour preserved from earlier versions

- The optional **license gate** (`KeyPromptValidator`) previously ran unconditionally on
  startup and called `System.exit()` when no interactive key was supplied, which prevented
  the app from starting in any headless environment. It is now **opt-in** via
  `app.license.enabled=true` and reads the key from `APP_LICENSE_KEY`.
- CORS is configured centrally (`CorsConfig`); per-controller `@CrossOrigin("*")` wildcards
  were removed because they overrode the central policy.
