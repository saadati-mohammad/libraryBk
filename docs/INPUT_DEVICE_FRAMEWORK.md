# Input Device Framework

This backend framework is the stable contract for configurable form input sources. It is a foundation for the admin UI and scanner-form integration; it does not claim that device enrollment is fully integrated into circulation screens yet.

## Provider extension

Spring beans implementing `InputSourceProvider` are discovered by `InputSourceProviderRegistry`. Each provider declares a stable type key, localized display name, JSON Schema object for a dynamic editor, capability identifiers, and server-side configuration validation. Duplicate keys fail application startup. Add a new provider bean and its validation/tests to extend the registry without editing registry/service control flow.

Initial providers:

| Key | Purpose | Capabilities |
| --- | --- | --- |
| `KEYBOARD_WEDGE` | USB, Bluetooth, or Wi-Fi scanners that type into a focused field | prefix/suffix, Enter/Tab/none terminator |
| `PHONE_CAMERA` | Phone camera/PWA source | environment/user camera, optional torch, paired-session capability |
| `MANUAL` | Keyboard/manual form entry | keyboard |

## API contract

- `GET /api/admin/input-devices/types` returns providers, configuration schemas, and capabilities.
- `GET/POST /api/admin/input-devices`, `GET/PUT /{id}`, `POST /{id}/enable`, `/disable`, `/rotate-token`, `DELETE /{id}`, and `GET /{id}/audit` provide admin-only lifecycle management.
- `POST /api/device-inputs/{id}/heartbeat` accepts a device-specific bearer token and updates last-seen state. The edge filter limits all attempts (including invalid credentials) to 60/minute per remote IP/device path by default; accepted heartbeat writes are additionally spaced at least one second apart per device. These in-process limits do not share counters across instances and do not trust forwarded IP headers.
- Create and rotate return a 256-bit random token once. Only its SHA-256 digest is stored. List/read/audit responses never include the token or digest.
- Delete is a revocation operation: the row and audit history remain queryable, while its token is erased and its enabled flag is cleared.
- Device changes append actor/time/address events with before/after summaries. Config fields named secret, token, credential, password, API key, or private key are redacted before audit persistence.

## Persistence and access

H2 migration `V8` and MySQL migration `V7` create the device registry and append-only application audit table. All management routes require `ADMIN`; heartbeat is the only unauthenticated route and can only prove possession of its high-entropy device token. Existing expiring phone scanner sessions remain compatible.

## Boundaries still to implement

- Connect the existing short-lived phone QR session to a registered `PHONE_CAMERA` device credential and surface its health in the admin UI.
- Per-form/per-user/per-station source policy and fallback behavior.
- Per-form device selection and configuration beyond the existing book-entry choices (manual, HID, camera, and temporary phone session).
- Device scan submission/queue endpoints and rate controls for any future persistent scan API.
- Live USB/Bluetooth/Wi-Fi HID hardware tests, MySQL migration execution, HTTPS camera tests, and multi-instance storage/health behavior.

The current one-time phone pairing flow remains the supported PWA workflow until those integration items are complete.
