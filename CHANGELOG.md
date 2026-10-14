# Changelog — libraryBk

All notable changes to the libraryBk Spring Boot backend are documented here.
Entries cover the coordinated `feature/system-overhaul` branch (2026-10-11 → 2026-10-14).

## [Unreleased] — feature/system-overhaul

### Added

- **Safe device-profile delivery in effective-policy responses** (`T-010 T-012`): the
  `effectivePolicy` endpoint now includes a `deviceProfiles` list containing only the resolved
  config (prefix, suffix, terminator, minimumLength) for explicitly selected, enabled, unrevoked
  devices compatible with the requesting owner and station. Token hashes and internal fields are
  excluded. H2 integration proves `prefix`/`suffix`/`terminator` delivery and excludes sensitive
  fields.
- **Device registry and lifecycle API** (`T-010`): admin CRUD for input devices, configurable HID
  profiles, secure mobile pairing/token lifecycle, enable/disable/revoke/token-rotation, bearer
  heartbeat and audit events. Scanner rate/session caps and cross-staff isolation H2-tested.
- **Registered phone-camera authentication** (`T-010`): PHONE_CAMERA tokens authenticate scans,
  bind sessions to device/staff owner, update last-seen and are revocable by admin.
- **Form input policies** (`T-012`): global/personal/station policies with device availability and
  ownership validation persisted and served to frontend.
- **Relevance-ranked public catalog search** (`T-034`): API ranks exact normalized title,
  title-prefix, author/subject match then stable ID. Persian normalization verified in H2 integration.
- **Per-IP auth rate limiting** (`T-020`): in-process throttle for login (20/15 min) and reset
  (5/hour) with configurable CI overrides.
- **Auth version / JWT revocation on password reset** (`T-021`): additive `auth_version` column;
  JWT filter re-checks version so stolen tokens are invalidated after a reset.
- **Attachment ownership enforcement** (`T-022`): upload requires an active message; orphan/unowned
  attachment access returns 401/403 for non-admin callers.
- **Duplicate-aware catalog import** (`T-027`): ISBN-normalized batch deduplication; skips existing
  records without overwriting; returns skipped row numbers.
- **Bounded XLSX import preview** (`T-028`): configurable size (`UPLOAD_MAX_FILE_SIZE`, default
  10 MiB) and row-count (`IMPORT_MAX_ROWS`, 5000) limits; no-write preview endpoints for books and
  members with row-level validation and duplicate reporting.
- **Business timezone + injectable clock** (`T-036`): `APP_BUSINESS_ZONE` configures the shared
  `Clock` used across loans, reservations and member expiry. Fixed-clock Tehran boundary tests pass.
- **Anonymous denial test matrix** (`T-015`): integration test enumerates 20+ non-public REST
  handlers and proves every one returns 401 for unauthenticated requests.
- **Password-reset pessimistic lock** (`T-015`): `FOR UPDATE` on reset token redemption prevents
  race-condition double-use.
- **ISBN-10/13 cross-format loan lookup** (`T-012`): checkout and return accept ISBN-10 or ISBN-13
  and normalize before matching.
- **Persian ISBN provider validation** (`T-037`): documented coverage gap; live smoke test recorded
  in `docs/METADATA_SOURCES.md`.
- **WebSocket identity and message ownership enforcement** (`T-014`): broker-topic SEND, recipient
  read access and sender-only edit/delete verified by regression tests.

### Fixed

- ISBN checksum validation and digit normalization (ASCII/Persian/Arabic-Indic) applied to all
  create/update/import write paths. [T-024]
- Scanner session count bounded; expired pairing sessions cleaned up. [T-010]
- Password reset redemption serialized to prevent double-use under concurrent requests. [T-015]
- Message edit/delete restricted to sender; file stats only visible to owners. [T-014]

### Infrastructure / CI

- `mvnw -B clean verify` covers H2 Flyway migrations, unit and integration tests (196 tests).
- OWASP dependency scan runs with `continue-on-error: true` (advisory-only gate).
- Surefire test reports uploaded as CI artifacts.
- Backend CI triggers on `feature/system-overhaul` pushes.

### Verified baselines (2026-10-14)

| Check | Result |
|---|---|
| `mvnw -B verify` | ✓ 196 tests, 0 failures, 0 errors, BUILD SUCCESS |
| Flyway H2 migrations | ✓ V1–V9 applied cleanly |
| MySQL migrations | Maintained in parallel (not run in CI; verified by migration parity review) |

### Deferred / Out-of-scope

- **T-023** — Email verification for self-registered members
- **T-025** — Per-copy physical inventory (requires migration/reconciliation)
- **T-026** — Configurable circulation rules (loan duration, renewals, fines)
- **T-031** — Structured actor-attributed audit trail
- **T-032** — Soft-delete / retention / privacy-erasure policy
- **T-033** — Session management / revocation center API
- **T-035** — MySQL CI gate and production-load verification
- **T-037** — Verified Iranian ISBN from licensed/documented provider

---

*Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).*
