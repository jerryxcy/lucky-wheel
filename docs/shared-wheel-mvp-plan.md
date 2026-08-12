# Shared Wheel MVP plan

Status: agreed planning draft; not yet implemented.

## Outcome

Lucky Wheel keeps its zero-setup Local Wheel and legacy stateless Spin API,
while deployments may explicitly enable Shared Wheels backed by PostgreSQL. A
Shared Wheel has a shareable capability URL. Every browser using that URL sees
the same roster, eligibility, auto-remove setting, latest result, and committed
Spin records.

The implementation is also a learning sequence: each ticket has one primary
Spring Boot topic, remains mergeable with green CI, and ends in a real
two-browser vertical acceptance test.

## Baseline found in the repository

- Java 21 and Spring Boot 3.5 are used with Spring Web and Validation.
- `POST /api/spins` is stateless. The vanilla-JavaScript UI owns roster,
  eligibility, and auto-remove state in `localStorage`.
- The jar serves the static UI and has no front-end build step.
- The existing test suite covers the pure draw domain and the MVC boundary.
- GitHub Actions runs `./mvnw verify` on Temurin 21 and was green when this plan
  was prepared.
- A local `verify` under Java 25 exposed Mockito/Byte Buddy agent-attachment
  incompatibility; the build should fail early unless it runs on Java 21.
- ADR-0001 explicitly names cross-device shared rosters as the trigger for
  revisiting its stateless-only decision.

## Product flow

### Choosing a mode

The UI always exposes these choices instead of expecting a user to discover a
share action without first understanding the product modes:

- `Local Wheel — only in this browser`
- `Create Shared Wheel — saved on server, accessible by link`

`/` opens Local Wheel. Shared controls are offered only when
`GET /api/capabilities` reports that Shared Wheels are enabled. Local Wheel
requires no persistence when Shared mode is disabled. An enabled deployment
with invalid database configuration fails startup instead of silently changing
its advertised capability; a database outage after startup does not change
Local browser state or the legacy stateless Spin contract.

### Creating and opening a Shared Wheel

1. A user prepares a Local Wheel or leaves its roster empty.
2. `Create Shared Wheel` asks for a required Wheel name and explains that the
   current roster, eligibility, and auto-remove setting will be copied.
3. A successful create navigates the same browser tab to
   `/shared-wheels/{wheelId}`. It does not alter the Local Wheel in that
   browser.
4. A failed create leaves the Local Wheel unchanged.
5. On the first view after creation, the Wheel mode sheet opens once and warns
   that there is no account or Shared Wheel list, so the user should copy the
   link or bookmark it with the browser shortcut. Later views keep `Copy link`
   available without repeating the creation reminder; sharing is not a
   blocking step.
6. Opening the full URL in another browser loads the same authoritative state.
   There is no short code or separate join-by-ID UI.

A browser may bookmark a Shared Wheel. The product does not maintain a recent
wheel list. Multiple independent Shared Wheels may exist, and a team lead may
reuse one by changing its roster or re-checking all members.

### Operating a Shared Wheel

Shared Wheel retains Local Wheel feature parity: add and remove members, bulk
import, copy roster, change eligibility, re-check everyone, select the count,
draw an order, configure auto-remove, Spin, skip Reveal, copy the numbered
result, and use English or Traditional Chinese. It additionally shows its
name, share link, live-update connection status, expiry notice, and permanent
delete action.

Shared changes are shown only after the server confirms them. A successful
response replaces the browser's snapshot. A conflict refreshes the snapshot
and asks the user to retry deliberately; unsent text remains available, while
a rejected checkbox change returns to server state.

The selected UI direction is projection-first **Stage badge**: an always-visible
badge in the upper-left identifies Local versus Shared mode and, in Shared
mode, the current Wheel name and live connection state. Activating it opens a
command sheet containing mode choice, share link, expiry, rename, roster, and
permanent deletion. Local users create a Shared Wheel through the Shared option
in this command sheet; the stage does not show a second creation callout. This
direction came from the three-variant throwaway Shared Wheel UI prototype, with
the badge retained as the single production entry point. Production code must
reimplement the direction rather than promote prototype code.

`Live updates connected` and `Reconnecting` describe the SSE connection, not
whether REST is usable. Initial page load, reconnect, window focus, SSE
notification, and conflict recovery all converge on the same authoritative
GET-and-version-deduplication path.

### Reveal and later updates

Only a newly received committed-Spin event starts Reveal. Loading, refreshing,
or reconnecting displays the latest result complete without replaying it.

During Reveal, the browser freezes the committed Spin record being played and
coalesces ordinary invalidations into one refresh after the animation. If a
newer Spin then exists, it shows that result complete with a notice instead of
replaying every missed animation. A wheel-deleted event interrupts Reveal
immediately. The server does not store a revealing/spinning state or wait for
client animation acknowledgements.

## Domain rules

- A Shared Wheel has a UUID v4 identity and a required, mutable, non-unique
  display name. The trimmed name is 1 to 80 characters.
- A roster contains at most 100 members in a stable order.
- A member name is trimmed, case-sensitive, unique within its wheel, and at
  most 80 characters. Member rename is not an MVP operation; remove and add is
  sufficient. Internal persistence identifiers are not exposed in the API.
- `eligible: true` means the member participates in the next Spin. An
  ineligible member remains on the roster.
- A Shared Wheel may have an empty roster, but a Spin requires a count from 1
  through the current number of eligible members.
- Auto-remove is authoritative Shared Wheel state. When enabled, a successful
  Spin makes every member in that draw order ineligible in the same transaction.
- A Spin record is immutable. It stores its UUID, UTC occurrence time, the
  complete eligible-member snapshot, full ordered draw result, and whether
  auto-remove applied. Requested count is derivable from draw-order length.
- Spin records cannot be edited, individually deleted, or cleared by re-checking
  members. Deleting the Shared Wheel removes all of them.
- The current API exposes the latest Spin in the wheel snapshot and a Spin by
  ID. It does not expose a Spin-record list, search, pagination, or history UI.
- Server timestamps use `Instant`, PostgreSQL `timestamptz`, and ISO-8601 UTC
  values ending in `Z`. The browser renders them in the user's local timezone.

## Concurrency and transaction contract

One optimistic version covers the complete Shared Wheel aggregate. Every
mutation supplies the version it expects. Every successful mutation that keeps
the wheel advances the root version, including every Spin. Hard delete checks
the expected version and then removes the root rather than preserving a new
version. Two concurrent commands based on the same version cannot both commit.

The aggregate update replaces name, auto-remove, ordered members, and
eligibility in one PUT. A PUT with the current expected version and an
identical representation is a no-op: it does not advance version, extend
retention, or publish an event. A stale but otherwise identical PUT still
conflicts.

A Shared Spin application-service method owns one transaction:

1. Load the Shared Wheel aggregate.
2. Confirm it has not expired and verify the expected version.
3. Validate count against the current eligible members.
4. Decide the complete draw order.
5. Persist the immutable Spin record.
6. Apply auto-remove eligibility changes when configured.
7. Set the latest result, advance the root version, and extend retention.
8. Flush and commit.
9. Publish an SSE notification only after commit.

Failure at any step persists no Spin record, partial eligibility update, or
event. PostgreSQL's default `READ COMMITTED` isolation and aggregate optimistic
locking are used; there is no locks table, serializable retry loop, automatic
conflict merge, idempotency key, or automatic command retry. After an ambiguous
network failure, the client GETs current state rather than guessing whether to
repeat the command.

Database uniqueness, foreign keys, checks, and non-null constraints complement
the version; they do not replace it. In particular, member or Spin child
changes must explicitly advance the Shared Wheel root instead of assuming JPA
will dirty its version automatically.

## HTTP API

The original `POST /api/spins` contract and its legacy `{ "message": "..." }`
error body remain unchanged.

### Capability and browser routes

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/capabilities` | Always available; reports `sharedWheels` without querying the database |
| GET | `/` | Local Wheel UI |
| GET | `/shared-wheels/{wheelId}` | Same static UI bootstrapped into Shared mode; supports direct navigation |

An unknown, expired, or deleted Shared Wheel displays an unavailable error and
does not silently fall back to Local Wheel.

### Shared resources

| Method | Path | Success |
| --- | --- | --- |
| POST | `/api/shared-wheels` | `201 Created`, `Location` header, complete wheel snapshot |
| GET | `/api/shared-wheels/{wheelId}` | `200 OK`, complete wheel snapshot |
| PUT | `/api/shared-wheels/{wheelId}` | `200 OK`, complete wheel snapshot, including a no-op PUT |
| POST | `/api/shared-wheels/{wheelId}/spins` | `200 OK`, complete post-Spin wheel snapshot |
| GET | `/api/shared-wheels/{wheelId}/spins/{spinId}` | `200 OK`, one immutable Spin record |
| GET | `/api/shared-wheels/{wheelId}/events` | `text/event-stream` invalidation notifications |
| DELETE | `/api/shared-wheels/{wheelId}?expectedVersion={version}` | `204 No Content` after complete hard deletion |

Create accepts `name`, `autoRemove`, and the ordered `members`. Aggregate PUT
accepts those fields plus `expectedVersion`. Spin accepts `expectedVersion` and
`count`. PUT never accepts latest-Spin or Spin-record data.

A complete Shared Wheel snapshot has this semantic shape:

```json
{
  "id": "UUID",
  "name": "On-call rotation",
  "version": 13,
  "autoRemove": true,
  "members": [
    { "name": "Alice", "eligible": false },
    { "name": "Bob", "eligible": true }
  ],
  "latestSpin": {
    "id": "UUID",
    "occurredAt": "2026-08-11T02:30:00Z",
    "eligibleMembers": ["Alice", "Bob"],
    "drawOrder": ["Alice"],
    "autoRemoveApplied": true
  },
  "expiresAt": "2026-09-10T02:30:00Z"
}
```

`latestSpin` is `null` before the first Spin. `expiresAt` is `null` when the
deployment has disabled automatic inactivity retention.

### Errors

New Shared APIs use RFC 9457 `application/problem+json` with stable problem
types. Known types are translated by the browser; unknown types fall back to
the server's English `detail`. User-provided names are never translated.

- `400 Bad Request`: malformed JSON, invalid UUID or field, blank/long names,
  member duplication/limit violations, invalid count, or invalid/missing
  expected version.
- `404 Not Found`: missing wheel or Spin record. Never-existing, manually
  deleted, and expired wheels are deliberately indistinguishable.
- `409 Conflict`: stale expected version, with a `currentVersion` extension.
- `500 Internal Server Error`: unexpected failure without exception, SQL, or
  stack details.

Field validation may use an `errors` extension. The MVP does not introduce a
separate `422 Unprocessable Content` branch.

### SSE semantics

State-change notifications identify the committed wheel version; Spin
notifications also identify the Spin UUID. A terminal deletion notification
states that no authoritative version remains. Update, committed-Spin, and
deletion notifications are published after commit. They are invalidations
rather than authoritative state payloads, so the client GETs the current
snapshot unless deletion has made that unnecessary.

Delivery is intentionally best effort and in-process for a single application
instance. There is no outbox, guaranteed replay, WebSocket, Kafka, or
cross-instance event distribution. Native reconnect plus later GETs repairs a
missed notification.

## Persistence and runtime

- The same jar runs both modes. Shared mode is explicitly disabled by default.
- Default Local startup requires no DataSource, migration, Docker, or database.
- When Shared mode is enabled, valid PostgreSQL configuration and successful
  Flyway migrations are mandatory; startup fails rather than silently falling
  back to Local-only mode.
- Development Shared mode uses Docker Compose PostgreSQL with a named volume.
- Integration tests use disposable Testcontainers PostgreSQL. H2 is not used.
- Production supplies an external PostgreSQL connection.
- Flyway SQL files are the sole schema source in all environments. Hibernate
  uses `ddl-auto=validate` and never creates or updates production schema.
- Current member/eligibility state is normalized relational data. Immutable
  eligible-member and draw-order snapshots use PostgreSQL `jsonb`.
- Spring Data JPA provides repositories and Hibernate is the JPA provider.
  Application services, not controllers or repositories, own transaction
  boundaries.
- Database values and API timestamps follow UTC. Tests inject a `Clock` rather
  than relying on wall time.

## Expiry and deletion

Automatic inactivity retention is a deployment-level feature that is enabled
by default with an expiry 30 days after the last successful mutation. Its
enabled flag and duration are configurable independently of manual deletion.
When disabled, wheels do not expire automatically and expose `expiresAt` as
null. GET, open SSE connections, reconnect, and window focus do not extend
retention; an identical no-op PUT does not extend it.

`expiresAt` is the authoritative access deadline. Once reached, every operation
treats the wheel as missing even if rows remain until the next cleanup run. A
successful mutation before the deadline calculates a new expiry. Scheduled
cleanup hard-deletes the wheel, members, and every Spin record, then publishes
a deletion notification after commit.

Any link holder may also permanently delete the entire Shared Wheel after an
explicit confirmation, whether or not automatic retention is enabled. DELETE
checks the expected version and immediately hard-deletes the wheel, members,
and all Spin records in that transaction. Its 204 response means the old URL is
already unavailable; the post-commit event then updates connected browsers.
There is no restore or tombstone.

## MVP exclusions

- Accounts, OAuth, owners, hosts, administrators, viewers, or separate secrets
- Shared Wheel dashboard, listing, recent-wheel list, search, short join codes,
  duplication, archive, or restore
- Member rename as a distinct operation
- Full Spin-history UI, list endpoint, filtering, pagination, undo, or deletion
  of individual records
- Server-side Reveal state, synchronized animation frames, or replay on refresh
- Idempotency keys, automatic write retries, intent-specific mutation APIs, or
  automatic conflict merging
- H2, React, a front-end build tool, WebSocket, transactional outbox, Kafka,
  microservices, horizontal event distribution, or AI features
- Public-SaaS abuse controls, quotas, and rate limiting

## Verification strategy

- Keep all legacy domain and MVC tests passing.
- `*Test` runs in Maven Surefire's `test` phase and requires only Java 21.
- `*IT` runs in Failsafe's `integration-test`/`verify` phases and may require
  Docker and a browser.
- `mvn package` is JDK-only and produces the complete jar.
- `mvn verify` runs PostgreSQL and browser integration tests. CI prepares the
  required Playwright browser and executes this command on every PR.
- Repository and HTTP integration tests use real PostgreSQL with the same
  Flyway migrations as development and production.
- At least one random-port full-stack concurrency test sends two Spins with the
  same expected version. Exactly one succeeds, one receives 409, and the
  database contains exactly one Spin record, the correct eligibility state,
  and one aggregate version advance.
- The final two-browser Playwright scenario starts the real application with
  Shared mode, Flyway, and Testcontainers PostgreSQL. Browser A creates a
  Shared Wheel from Local state; Browser B opens the link and changes
  eligibility; A updates through SSE; B Spins; both display the same complete
  draw order and auto-remove state; refresh preserves the server state. No
  repository, HTTP, SSE, or browser layer is mocked.

## Implementation tickets

Every ticket must preserve zero-setup Local mode and green CI. Each PR updates
README or operational documentation for the behavior it actually ships.

The approved work is tracked as six vertical tracer bullets under
[specification issue #24](https://github.com/jerryxcy/lucky-wheel/issues/24).
The GitHub issues contain the complete acceptance criteria; this section records
their intended learning boundary and the user-visible capability added by each
slice.

### 1. [#25 — Preserve zero-setup Local Wheel with optional Shared infrastructure](https://github.com/jerryxcy/lucky-wheel/issues/25)

Primary learning topic: Spring Boot conditional configuration and test
lifecycle separation.

Blocked by: none.

Delivers the Shared capability switch, Java 21 build guard, Surefire/Failsafe
test split, optional PostgreSQL/Flyway/JPA/Testcontainers infrastructure, and a
real-database smoke path while keeping default Local startup database-free and
the legacy Spin API unchanged.

### 2. [#26 — Create and reopen a named Shared Wheel](https://github.com/jerryxcy/lucky-wheel/issues/26)

Primary learning topic: Flyway-owned schemas and Spring Data JPA aggregate
mapping.

Blocked by: #25.

Delivers the first end-to-end Shared flow: a Local user names and creates a
Shared Wheel, receives its capability URL, and can reopen the complete
server-side snapshot in one browser. It includes the initial normalized schema,
immutable Spin snapshot storage shape, validation, Problem Details, deep links,
and the selected Stage badge UI.

### 3. [#27 — Edit Shared Wheel state without lost updates](https://github.com/jerryxcy/lucky-wheel/issues/27)

Primary learning topic: aggregate versions and optimistic locking.

Blocked by: #26.

Delivers full-aggregate edits for name, ordered roster, eligibility, and
auto-remove. Every accepted mutation uses one aggregate version; stale writes
return 409 with the current version, identical current-version writes are
no-ops, and Shared controls wait for the server response before presenting the
new state as committed.

### 4. [#28 — Synchronize committed Shared Wheel edits live](https://github.com/jerryxcy/lucky-wheel/issues/28)

Primary learning topic: transaction-bound events and Spring MVC Server-Sent
Events lifecycle.

Blocked by: #27.

Delivers best-effort after-commit invalidations so two browsers converge through
authoritative GET snapshots. It covers rollback silence, reconnect/focus
recovery, version deduplication, connection status, and a two-browser edit
integration path without WebSocket or an outbox.

### 5. [#29 — Commit and Reveal a Shared Spin atomically](https://github.com/jerryxcy/lucky-wheel/issues/29)

Primary learning topic: transaction boundaries, commit timing, and concurrent
integration testing.

Blocked by: #28.

Delivers the complete Shared Spin transaction: validate the expected version,
choose and persist the full draw order, save the immutable Spin record, apply
auto-remove, update latest result, advance version and retention, then notify
after commit. It also delivers reveal behavior and the required real-PostgreSQL
concurrency test proving two same-version Spins produce exactly one success,
one conflict, and no partial state.

### 6. [#30 — Expire or permanently delete a complete Shared Wheel](https://github.com/jerryxcy/lucky-wheel/issues/30)

Primary learning topic: Spring scheduling, injected clocks, and aggregate
lifecycle deletion.

Blocked by: #29.

Delivers configurable inactivity retention, authoritative expiry, scheduled
hard cleanup, and immediate version-checked manual deletion of the entire
aggregate. Its final two-browser test exercises the real application, Flyway,
PostgreSQL, live editing, Shared Spin, refresh persistence, and deletion as the
MVP's vertical acceptance test.

## Dependency order

The native GitHub dependency chain is:

`#25 → #26 → #27 → #28 → #29 → #30`

All six are native sub-issues of #24, and each arrow is represented by a native
blocked-by relationship. Only #25 is currently unblocked. Each ticket must land
through its own branch and PR with CI green before work begins on the next
slice; the merge decision remains with the user.
