# Deep System Audit — Baseline Findings

**Review date:** 2026-10-11 · **Scope:** source, tests and local H2-backed real-browser flows on `feature/system-overhaul`. Findings are ranked by plausible user/safety impact, not by how much code is needed. “Not implemented” is a product gap unless it creates data/security risk. Each actionable finding maps to backlog IDs in `AUDIT_PROGRESS.md`.

## Executive assessment

The current application is a working small-library MVP: guests can discover titles, staff can manage people/books and circulation, members can self-reserve, recovery tokens are designed safely, and current CRUD/scanner workflows pass the checked-in H2 and Playwright tests. It is not yet a complete library platform or a device-management system. It has no tracked physical copies, branch/policy model, fines/renewal, verified member email, session revocation, unified security audit, registered scanner devices, or import preflight. Several gaps become correctness/security issues when the system is exposed to real users or concurrent operators.

## Severity-ranked findings

### AUD-001 — Login and public reset lack abuse throttling (High)

- **Evidence:** all auth routes are public; no application rate-limiter/lockout dependency, filter or gateway contract appears in API source/config. Reset has a per-account 60-second quiet period, but that does not bound guessed-email traffic or parallel requests globally. CI covers response behavior, not brute-force resilience.
- **Scenario/impact:** a public host permits password guessing and high-volume reset email requests. BCrypt increases attacker cost but does not stop request floods or account-targeted lockouts.
- **Action:** add bounded, distributed-ready throttling with a safe local fallback, generic responses, observability, and tests for IP/account buckets and proxy-header trust. Do not permanently lock a victim account on attacker-controlled attempts.
- **Backlog:** T-020.

### AUD-002 — Password reset does not revoke already issued JWTs (High)

- **Evidence:** access JWT lifetime defaults to 86,400 seconds. `PasswordResetService.reset` updates the BCrypt hash and consumes reset tokens, while `JwtAuthenticationFilter` validates signature/expiry and reloads current user enabled state/role but has no password-version/session epoch check. Existing access tokens remain valid until expiry after a password reset.
- **Scenario/impact:** a user resets because a device was stolen; the stolen bearer token still grants access for up to 24 hours. Role/deactivation changes are rechecked, so this gap is specifically credential change/session revocation.
- **Action:** add a server-side token/session generation or revocation mechanism and make password reset/recovery revoke outstanding sessions; tests must prove stale JWT rejection.
- **Backlog:** T-021.

### AUD-003 — Unowned file attachments are readable/deletable by any authenticated user (High)

- **Evidence:** `FileController.requireAttachmentAccess` authorizes only when an attachment has a message ownership row (`Optional.ifPresent`). Upload accepts an optional `messageId`; `FileService.uploadFile` silently leaves the file unattached when the supplied message id is missing/inactive. The controller comment explicitly permits any authenticated caller to read attachments with no owning message. Numeric file IDs can then be enumerated for download/info/delete.
- **Scenario/impact:** authenticated users may access or destroy uploads not attached to a conversation, including orphaned uploads resulting from a stale/invalid message ID.
- **Action:** require a valid owner/conversation at upload or assign uploader ownership; deny unowned access except an explicit admin-only recovery flow. Add cross-principal tests for upload, download, info, list, delete and orphan IDs.
- **Backlog:** T-022.

### AUD-004 — Member self-registration accepts unverified email ownership (High when enabled)

- **Evidence:** registration normalizes/checks email and national ID, then creates a MEMBER account immediately; no verification token/state or confirmation endpoint exists. Production registration is off by default, but turning the provided flag on makes accounts usable before confirming mailbox ownership.
- **Scenario/impact:** typo or false ownership claims capture recovery mail for an address the registrant does not control and can pollute member records. Email uniqueness prevents a second row but does not prove identity.
- **Action:** implement single-use, expiring verification with resend throttling and non-authenticated/unverified account state; enable public production registration only after mail verification is configured. Preserve staff-registered members where policy allows.
- **Backlog:** T-023.

### AUD-005 — Book identity and circulation are title aggregates, not physical-copy inventory (High functional/data integrity)

- **Evidence:** one `Book` row has free-text ISBN-10/13 and integer `copyCount`; no ISBN uniqueness/index-normalization or `BookCopy` table exists. Loan references `book_id`, so no physical barcode, copy condition, branch/shelf assignment per copy, lost/damaged state, or item-level circulation history. Existing pessimistic lock protects aggregate capacity, and its concurrency integration test passes.
- **Scenario/impact:** two books with the same ISBN can be created; checkout/return and stocktaking cannot identify which actual copy moved. Scan input is confined to book title creation. A count can be manually changed while copies are loaned without reconciling which item is missing.
- **Action:** normalize ISBN and prevent accidental duplicates with a migration-safe index/duplicate resolution; introduce copy-level inventory in additive migration and backward-compatible aggregation; add copy barcode, condition, availability and circulation/stocktake flows. Document title-level mode during rollout.
- **Backlog:** T-024, T-025.

### AUD-006 — Circulation rules are fixed/minimal and reservation service has no queue/fairness policy (High product gap)

- **Evidence:** loan duration is a constant 14 days; no renewal, fine/grace/maximum-loan, member suspension/block or branch-specific policy model exists. Reservations are active rows by date but have no queue position; staff can fulfill any active reservation when a copy is available. Expiry scheduling works, and fulfill delegates to the transactional copy-capacity check.
- **Scenario/impact:** staff cannot apply different policy to memberships, branches, holidays or special items; a later reservation can be fulfilled ahead of an earlier one. Overdue status is recorded but no fine or notice is generated.
- **Action:** create configurable circulation policy and explicit reservation ordering/eligibility; implement renewals/fines only with auditable policy and member-visible explanation. Preserve current 14-day behavior as migration default.
- **Backlog:** T-026.

### AUD-007 — ISBN validation/duplicate checks differ by entry path (High data quality)

- **Evidence:** frontend scanner helper validates ISBN checksum before metadata lookup and server relay only accepts valid ISBN-10/13. `Book` entity only requires at least one nonblank ISBN; it does not validate checksum, normalize casing/separators or define unique constraints. Excel import constructs entities directly and does not check duplicate ISBNs, including duplicates within the uploaded file.
- **Scenario/impact:** direct API/import paths can persist malformed or duplicate book identifiers even though the scanner path rejects them; later scanner lookup cannot reliably identify the row.
- **Action:** centralize ISBN normalization/validation for all writes and imports; provide duplicate resolution that allows legitimate editions/records by policy instead of silently discarding them; use additive unique constraints after data cleanup.
- **Backlog:** T-024, T-027.

### AUD-008 — Excel import has no preview/dry-run or row-level recovery (High operational risk)

- **Evidence:** import reads a complete XSSFWorkbook into memory and saves all parsed books at once; people are saved row-by-row inside one transaction. Bad dates and integer cells can be logged and converted to null; malformed active values become inactive; duplicate people are checked against persisted rows but not a staging set of earlier rows in the same workbook. The response does not provide a structured preview/error report and a late constraint failure can reject the transaction without helping the operator correct one row.
- **Scenario/impact:** a large/poorly formed workbook is difficult to correct; operators cannot see duplicates or validation errors before changing the live catalog. Excel ZIP/decompression and request-size limits are not demonstrated in the controller contract.
- **Action:** set bounded upload/row/column limits, validate workbook shape and cell types, stage results, provide dry-run preview with row/field errors, and commit idempotently only after confirmation; add generated large/malformed workbook tests.
- **Backlog:** T-028.

### AUD-009 — Continuous scanner mode can create a book from a single metadata result without review (High data quality)

- **Evidence:** `processNextBatchScan` automatically applies metadata when exactly one provider result exists; `applyMetadata` calls submit while continuous mode is enabled. The providers are explicitly best-effort and may return incomplete/wrong matches. On ambiguous/no match the queue is cleared and scanning stops. Existing E2E proves consecutive entries but not wrong-provider match or recovery after network failure.
- **Scenario/impact:** one incorrect but uniquely returned third-party record may be committed without the librarian reviewing author/title/edition. Pending queue is held in an unbounded in-memory array; a failure clears unprocessed scans, with no undo/retry/review list.
- **Action:** retain batch speed but require a configurable review/confirmation policy (safe default review), bound and visibly show queue, preserve failed items, permit retry/remove/undo, and provide duplicate/unknown feedback. Test provider mis-match and mid-batch failure.
- **Backlog:** T-029.

### AUD-010 — Scanner capability is book-entry-specific and lacks device/input policy (High requested scope)

- **Evidence:** scanner flows exist only in `BookComponent` and `/phone-scan`; there is no persistent device entity, owner/station, provider registry, capability/config schema, token rotation, last-seen, audit, enable/revoke UI or per-form input selection. Pairing is a 10-minute in-memory session with bounded 50-scan queue and 1.2-second repeated-value suppression; strong random session secret and constant-time comparison are present.
- **Scenario/impact:** an operator cannot register/revoke/identify a desk scanner or choose a scanner for loan/return/stocktake. Server restart drops pairing and a lost phone cannot be individually revoked once the pairing link is exposed.
- **Action:** implement API-first pluggable provider registry and registered phone credentials/pairing; add role-scoped management, form policy/fallback and migrations. Keep current short-lived relay compatible until UI migrates.
- **Backlog:** T-010, T-011, T-012.
- **Implementation status (2026-10-11):** additive H2 V8/MySQL V7 schema, provider registry, administrator API, hashed/rotatable device tokens, audit events, heartbeat throttling, and `/input-devices` management UI now exist and have H2/Playwright coverage. This closes baseline registration and administrator CRUD findings only; persistent phone QR pairing, form-level input policies, configured hardware and MySQL execution remain open.

### AUD-011 — Scanner UX lacks safe batch recovery and camera controls (Medium-high)

- **Evidence:** continuous book scan has a private batch queue, local camera rear-facing preference, EAN-13/UPC-A/Code-128 decode hints, visual status and duplicate suppression. Phone flow supports manual/HID, camera and optional vibration. There is no visible queue/review/undo, audio option, torch/zoom/device switch, offline sync or form-level source choice. Phone relay/API accepts only valid ISBN values, not generic QR/other inventory codes.
- **Scenario/impact:** on camera permission denial the UI gives guidance/fallback, but on unstable network or metadata/provider errors unprocessed entries are discarded; operators cannot correct a mistaken scan before/after batch persistence. Camera features vary by browser and require manual test.
- **Action:** add bounded scan queue with review/undo/retry and status, optional accessible sound/haptics, supported camera track controls and permission/network tests; distinguish ISBN ingestion from future generic barcode identifiers.
- **Backlog:** T-029, T-030.

### AUD-012 — No unified administrative/security audit trail (High governance gap)

- **Evidence:** Envers audits selected `Book`, `Person`, `BookLoan`, `Reservation`, `Message` changes; binary payloads and staff/member accounts/reset tokens are excluded. No application actor/action/source/IP before/after event service, searchable audit screen/export or retention configuration exists. `StaffAccount` timestamps do not capture who changed the account.
- **Scenario/impact:** after an account is deactivated, permission changed, a book copied count changed, or a record is removed, an administrator cannot reliably answer who did it, when, or restore the previous value from a user-facing audit workflow.
- **Action:** design append-only structured audit events for sensitive actions, safe field redaction, integrity/retention/export policy and admin-only query UI; avoid claiming cryptographic tamper proof without external immutable storage/key ownership.
- **Backlog:** T-031.

### AUD-013 — User deletion semantics are inconsistent with retained-record intent (High data retention ambiguity)

- **Evidence:** book/person deletion is reversible active-flag deactivation and retains linked loan history; staff deactivation retains account row. `MessageService.deleteMessage` deactivates the message but also sets its message body to the empty string. File delete physically removes both disk file and database row. Reset token cleanup physically deletes expired/used tokens. These are distinct retention policies.
- **Scenario/impact:** a deleted message cannot be inspected/recovered despite retaining its row; an attachment deletion cannot be undone. That may be correct for privacy, but it conflicts with a blanket “deleted data stays accessible” expectation unless lifecycle is clearly scoped.
- **Action:** define record-class retention policy, permissions, privacy erasure/retention precedence, and user-visible restore path. Preserve audit history where justified; do not retain personal content indefinitely without a policy.
- **Backlog:** T-032.

### AUD-014 — Authentication/admin center is coarse and lacks session/security operations (High product/security gap)

- **Evidence:** password hashes use BCrypt; reset uses a random 256-bit token whose SHA-256 hash is stored, one-use/20-minute, generic response, and rate-limits a same-account reset to one/minute. Backend reloads enabled account/role on each API request and prevents removing the final admin. However, staff roles are enum-only ADMIN/LIBRARIAN; no fine-grained permissions, session listing/revocation, login history, email verification, MFA, effective-permission preview, failed-login dashboard or searchable audit center is present.
- **Scenario/impact:** operators cannot revoke one stolen token or understand account history, and cannot give a staff member least privilege below Librarian. In-memory browser logout only removes the local JWT.
- **Action:** deliver measured backend-enforced session/version revocation and audit first; then permission groups/templates and security UI. Treat TOTP/remember-device only after recovery and threat model tests. Avoid unsafe impersonation.
- **Backlog:** T-021, T-031, T-033.

### AUD-015 — Search and discovery are substring-based with no Persian normalization/facets (Medium product gap)

- **Evidence:** public search applies case-folded `LIKE` across title, author, publisher, subject and ISBN; category is exact subject equality. No Persian Arabic Yeh/Kaf normalization, zero-width handling, typo tolerance, stemming, relevance ranking, full-text index, facets or richer collection filters are evident.
- **Scenario/impact:** equivalent Persian spellings can return different results and a large catalog will require broad scans; guests cannot refine by language, author, availability, publication year or location. Do not expose private shelf coordinates in public results.
- **Action:** add normalized search field/index and safe facets with query/performance tests; preserve existing simple query semantics and ensure public DTO remains privacy-safe.
- **Backlog:** T-034.

### AUD-016 — API-level performance/reliability validation is incomplete (Medium operational risk)

- **Evidence:** H2 tests prove main workflows and one aggregate loan concurrency case, but no live MySQL migration run is available. Maven CI has no lint/static analysis task and OWASP dependency-check is `continue-on-error`. Startup logs show `spring.jpa.open-in-view` default enabled. No query-budget/N+1 test, load test, backup/restore drill or structured health/metrics coverage is established here.
- **Scenario/impact:** H2 compatibility does not guarantee MySQL DDL/locking behavior; API latency may scale poorly as list/import sizes grow; dependency scan can fail without failing CI.
- **Action:** provision a supported MySQL CI service/testcontainer, add static analysis and actionable security gate, disable open-in-view after service-fetch audit, add query/load/backup restore evidence and production health/metrics.
- **Backlog:** T-035.

### AUD-017 — Circulation dates use server-local clock and policies are not timezone-explicit (Medium correctness)

- **Evidence:** loan/reservation business logic and scheduled expiry use `LocalDate.now()`; app schedules do not establish an explicit business zone. UI converts/display ISO dates in Persian calendar. No library timezone configuration or time-boundary tests across timezone changes exist.
- **Scenario/impact:** hosts configured in different zones may disagree on due/expiry/overdue date, especially around midnight; a future multi-branch system needs branch-local policy.
- **Action:** configure one explicit library business zone now, use injectable `Clock` and test boundary behavior; evolve to branch zone only with branch model.
- **Backlog:** T-036.

### AUD-018 — “All books in the world,” Persian catalog coverage and copies are not guaranteed (Medium expectation gap)

- **Evidence:** Open Library and Google Books are external, best-effort providers; no direct Iranian national/institutional bibliographic source or coverage SLA was verified. Search/book entry can be manual. Metadata is not guaranteed and single-result continuous mode can create without review (AUD-009).
- **Scenario/impact:** staff may assume an empty result means a book does not exist or may accept wrong transliteration/edition data.
- **Action:** communicate source attribution/coverage and fallback; prioritize configurable regional catalog providers and reviewed metadata import rather than claiming global completeness.
- **Backlog:** T-037.

## Verified working as-is (retain and protect with regression tests)

1. API defaults to authenticated access; public catalog is GET-only and uses a restricted projection; staff/member API roles are not trusted from UI guards.
2. Member self-service resolves the person from authenticated username and scopes loans/reservations to that member.
3. Staff account status/role is reloaded per request; disabled accounts do not retain API access; last-active-admin protection is tested.
4. Book checkout locks the aggregate book row, checks active member/book and capacity in one transaction; the H2 concurrent-capacity integration test passes.
5. Reservation fulfillment is transactional, requires ACTIVE state and delegates to the same checkout capacity rule; member cancellation verifies ownership after row lock.
6. Book/person deactivation retains primary records/history; public results filter inactive books.
7. Reset passwords are BCrypt-hashed; reset tokens are random, single-use, short-lived, and only a SHA-256 digest is stored; reset request response does not disclose account existence.
8. Phone pairing secret is high entropy, compared constant-time, session expiry is 10 minutes, pending queue is bounded to 50 and server only accepts valid ISBNs; pairing remains in-memory.
9. Frontend unit suite (158 tests), backend unit/integration suite (152 tests), and actual Chromium desktop/mobile E2E (73 pass, one viewport-conditional desktop skip whose mobile counterpart passes) are green at baseline.
10. UI routes have responsive/contrast/axe tests, keyboard-wedge consecutive book entry, Jalali date input/calendar, and phone scan manual/HID/camera paths.

## Scope boundary

“Best-in-class” is not a single achievable binary state. This report separates hard correctness/security issues from large product capabilities. Phase 2 will compare competitors and value/effort; Phase 3 will map accepted stories; phases 4–6 will ship and verify a coherent, backward-compatible subset. Hardware protocols, national metadata subscriptions, real SMS, and production security properties require provider/device/deployment access and will be stated explicitly if not verifiable here.
