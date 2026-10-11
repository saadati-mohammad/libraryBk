# Full-System Overhaul Progress

This file is the durable source of truth for the coordinated `LibraryUI` and `libraryBk` overhaul. Re-read it and both `git log`s at the start of every phase/continuation.

## Phase status

| Phase | Status | Exit evidence |
| --- | --- | --- |
| 0 — Discovery & baseline | DONE | Repository maps, architecture/API/role inventory, clean install/build/lint/test baseline, `SYSTEM_MAP.md` in both repos; evidence on 2026-10-11 |
| 1 — Deep system audit | DONE | `docs/AUDIT_REPORT.md` ranks 18 findings and maps all actions to T-020–T-037; source and H2-backed browser flows reviewed |
| 2 — Competitor research | DONE | Sourced `COMPETITOR_ANALYSIS.md`, prioritized gap list and implementation tasks |
| 3 — User stories | IN-PROGRESS | Complete role-based stories mapped to screen/API/permission/tests; critical gaps implemented |
| 4 — Device/input framework | TODO | Extensible registered devices, secure pairing, form input policy, continuous scan UX, automated tests |
| 5 — Access control/auth center | TODO | Backend-enforced policy, sessions/audit/security improvements prioritized by evidence, tested admin UX |
| 6 — Further improvements | TODO | Measured performance/reliability/accessibility/operations improvements, justified scope decisions |
| 7 — Verification & handoff | TODO | Full test matrix, final walkthrough, updated READMEs, changelog, handoff, pushed branches and PRs |

## Task backlog

Status values: `TODO`, `IN-PROGRESS`, `DONE`, `OUT-OF-SCOPE` (with reason). Do not mark a task done without recorded evidence.

| ID | Repo | Task | Status | Evidence / notes |
| --- | --- | --- | --- | --- |
| T-001 | both | Confirm clean starting trees, remotes, history, and create dedicated overhaul branches | DONE | `feature/system-overhaul` created from `master` in both repos; initial trees clean; last commits UI `90c4b40`, API `f6be881` |
| T-002 | both | Map frontend/backend architecture, routes, endpoints, models, persistence, security, CI and tests | DONE | `docs/SYSTEM_MAP.md` in both repos; source, routes, security matchers, entities, migrations, tests and CI reviewed |
| T-003 | both | Install dependencies and run baseline build, lint, unit and integration suites | DONE | Baseline 2026-10-11: UI `npm ci --omit=peer`, lint/typecheck, Vitest (28 files/158 tests), production build, audit (0 vulnerabilities), full E2E (73 pass/1 expected viewport-conditional skip); API clean verify 152 tests. Latest API after changes: full H2 `clean verify` 159 tests green. API has no configured lint plugin/script. |
| T-004 | both | Create Phase 0 system map with Mermaid architecture/ERD, API inventory, role matrix, user-facing feature list | DONE | Verified Mermaid maps and inventories in both repo docs |
| T-005 | both | Audit all user workflows, validations, error/loading/empty states, concurrency, persistence and test coverage | DONE | `docs/AUDIT_REPORT.md`: 18 ranked findings, verified-as-is safeguards, scenario impacts and action mappings; baseline H2/Playwright evidence recorded |
| T-006 | both | Research required open-source, commercial, regional and adjacent competitors with dated primary sources | DONE | Produce sourced `COMPETITOR_ANALYSIS.md`; separate verified facts from inference |
| T-007 | both | Prioritize competitor gaps by user impact, risk, effort and implement selected high-value gaps | IN-PROGRESS | Current priority/gap reasoning is documented; implementation remains tracked by T-020 onward |
| T-008 | both | Write exhaustive Admin/Librarian/Member/Guest user stories with acceptance, edge cases and screen/API/permission/test mapping | IN-PROGRESS | No unsupported behavior represented as implemented |
| T-009 | both | Design shared device/input-source contracts and compatibility/migration strategy | TODO | Backend contract and additive migrations first; document extension interface |
| T-010 | API | Implement device registry, configurable HID profiles, secure mobile pairing/token lifecycle, authorization/rate controls/audit | TODO | Schema, API validation, migration, tests; preserve existing scanner contract during transition |
| T-011 | UI | Implement device/input management and schema-driven config UX | TODO | Accessible CRUD, enable/disable, test flow, health/last-seen, revoke, permissions |
| T-012 | both | Integrate per-form/per-user/per-station input policy and useful fallbacks | TODO | Manual/HID/phone/specific-device/multiple allowed sources; API policy enforcement where needed |
| T-013 | both | Build resilient scan workflow: batch queue/review/undo, duplicate/unknown feedback, symbologies, camera controls, offline strategy | TODO | Measure and test keyboard, camera, mobile, permission-denied and network-loss cases |
| T-014 | both | Review authentication, credential/session lifecycle, authorization completeness, CORS/CSRF/XSS/headers/throttling and recovery | TODO | Record threat model and evidence before adding mechanisms |
| T-015 | API | Implement selected auth hardening and comprehensive endpoint authorization tests | TODO | No secrets; additive/reversible migrations; secure defaults |
| T-016 | UI | Implement justified access-control/security center and effective-permission UX | TODO | Avoid displaying unimplemented security controls; backend remains authoritative |
| T-017 | both | Evaluate performance, data integrity, observability, accessibility, Persian/RTL, imports, backups and deployment workflow | TODO | Implement measurable useful work; record justified exclusions |
| T-018 | both | Run final builds, lint, unit/integration/E2E, scenario walkthrough and update docs/changelog/handoff | TODO | Capture exact evidence and limitations |
| T-019 | both | Push each atomic task-group commit and open coordinated GitHub PRs | TODO | No force push; do not merge without authority and green CI |
| T-020 | API | Add safe login and reset-request throttling | TODO | Bounded IP/account buckets; generic responses; proxy trust and tests |
| T-021 | API | Revoke issued credentials after password reset | TODO | Password/session generation check and stale-token tests |
| T-022 | API | Fail closed for unowned attachment access and invalid message association | DONE | API now requires an active message for upload, denies missing/orphan ownership; `mvnw.cmd -B clean verify` passed 154 tests incl. orphan info/delete denial and upload-without-message rejection (2026-10-11) |
| T-023 | API | Verify self-registered email before account activation | TODO | Expiring single-use flow, resend controls, configurable mail delivery |
| T-024 | API | Normalize and validate ISBN across every write path | TODO | Audit existing duplicates before additive uniqueness enforcement |
| T-025 | both | Add backward-compatible physical copy inventory and circulation | TODO | Includes per-copy barcode, condition and migration path from aggregate counts |
| T-026 | both | Configure circulation rules and reservation queue eligibility | TODO | Preserve current 14-day default; policy behavior must be explainable/tested |
| T-027 | API | Make catalog import duplicate-aware | TODO | Duplicates within workbook and existing catalog; safe resolution feedback |
| T-028 | both | Add bounded import preview, row validation and recoverable commit | TODO | Dry run, row/field errors, file and row limits, idempotency |
| T-029 | UI | Make continuous scan batches reviewable and recoverable | TODO | Safe default review, bounded queue, retry/remove/undo, no silent loss/commit |
| T-030 | both | Improve scanner feedback, camera controls and network/permission recovery | TODO | Feature-detect optional controls; generic codes separated from ISBN validation |
| T-031 | both | Add structured actor-attributed audit trail and admin search | TODO | Redact secrets/personal fields; define integrity/retention/export boundaries |
| T-032 | both | Define soft-delete, recovery, retention and privacy-erasure behavior | TODO | Consistent record-class policy and tests; do not retain sensitive content blindly |
| T-033 | both | Add backend-enforced account/session security operations | TODO | Version/revoke, history, least privilege and admin operations by validated scope |
| T-034 | both | Add Persian-normalized catalog search and useful safe facets | IN-PROGRESS | Backend public search normalizes Yeh/Kaf variants, ZWNJ/direction marks, and Persian/Arabic digits in query and SQL fields; H2 integration + unit tests pass in full `clean verify` (159 tests). Frontend facets/relevance ranking and query/index performance review remain |
| T-035 | API | Improve production-like verification and CI quality gates | TODO | MySQL migration/locking, static analysis, query/load and operational checks |
| T-036 | API | Use explicit business timezone and injectable clock for circulation | TODO | Boundary tests; preserve existing behavior in configured default zone |
| T-037 | both | Document metadata-source coverage and regional provider extension | TODO | Do not claim exhaustive world coverage; record source attribution and gaps |

## Decisions

| Decision | Why |
| --- | --- |
| Keep two coordinated `feature/system-overhaul` branches, one per repository | Repositories are separate and contract changes must ship API-first; preserve `master` history and make review possible. |
| Treat all production-grade feature claims as unverified until source and tests demonstrate them | Prior documentation can drift; Phase 0 maps implementation and establishes a measurable baseline. |
| Prefer additive schema/API evolution and preserve the existing phone scanner route until consumers migrate | Avoid breaking deployed frontend/API combinations. |
| If a requested mechanism (for example 2FA, RFID, IP/device identity or “tamper-proof” audit) cannot be made safe/meaningful with available identity/deployment context, document a bounded secure design and deliver the highest-value verifiable subset instead of faking capability | Security properties depend on deployment and hardware, not just UI toggles. |
| Unowned file attachments must be private by default; orphan recovery should require explicit privileged ownership | Current authenticated-only fallback permits cross-user access by numeric ID. |
| A unique external ISBN metadata match is not sufficient authority to auto-create a catalog record in a batch | Metadata providers can be incomplete or wrong; retain a review/confirmation boundary. |
| Migrate existing aggregate book counts into explicit copies only after inspecting legacy loans and defining reconciliation | Inventing per-copy loan allocation risks corrupting circulation history. |

## Known risks / constraints

- No backend lint plugin or lint command is configured in `pom.xml`; use compile/test and consider adding an agreed static-analysis gate after Phase 1.
- The requested scope spans an entire library platform and cannot be declared complete merely by producing documents; each claim needs implementation evidence.
- Production MySQL, SMTP, HTTPS camera access, mobile LAN pairing and real HID devices may require external services/devices unavailable in this environment.
- Competitor feature depth and regional product details require current sourced research; marketing pages alone may not establish actual behavior.
- Opening PRs is possible after all changes are committed/pushed; merging is explicitly not assumed.

## Next action

Phase 2 source review and gap prioritization are documented in `docs/COMPETITOR_ANALYSIS.md`. Phase 3 story traceability is in `docs/USER_STORIES.md`; implement uncovered critical security/data-integrity stories and add proof before declaring phase done.
