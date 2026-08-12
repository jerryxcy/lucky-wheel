# Lucky Wheel

[![CI](https://github.com/jerryxcy/lucky-wheel/actions/workflows/ci.yml/badge.svg)](https://github.com/jerryxcy/lucky-wheel/actions/workflows/ci.yml)

A spin-wheel decision tool for teams. Build a roster, spin the wheel, and let
it pick who's on-call this week — or draw a full order for who goes first.
Local Wheel runs from a single `java -jar`: no database, no build step, and no
setup beyond a JDK. Optional Shared Wheel mode stores a named roster on the
server and gives it a capability URL that another browser can open, without
changing the zero-setup default.

<p align="center">
  <img src="docs/images/wheel.png" alt="The Lucky Wheel with seven members ready to spin" width="480">
</p>

## Why it exists

Deciding "who's on-call" or "what order do we go in" is a small, recurring
source of friction. Names in a hat are unceremonious and easy to dispute; a
random-number site makes you re-type everyone every time. Lucky Wheel remembers
your team, makes the draw visibly fair, and turns it into a small moment of fun
in the stand-up.

## Quick start

Requires **Java 21 or later**. Either grab the pre-built jar or build from source.

**Download the released jar** (no build tools needed):

```bash
# with the GitHub CLI
gh release download v1.0.0 --repo jerryxcy/lucky-wheel --pattern '*.jar'

# …or with curl
curl -LO https://github.com/jerryxcy/lucky-wheel/releases/download/v1.0.0/lucky-wheel-1.0.0.jar

java -jar lucky-wheel-1.0.0.jar
```

**Or build from source** (from the repo root):

```bash
./mvnw package
java -jar target/lucky-wheel-1.0.0.jar
```

Open <http://localhost:8080>. Colleagues on the same network can open it at your
machine's LAN address — handy for projecting the wheel in a meeting. The UI is
bilingual (EN / 中) — it picks a language from your browser on first visit, and
the toggle in the top bar switches it any time.

## Using it

- **Roster** — add members one at a time, or paste a whole list (newline- or
  comma-separated; duplicates are dropped). Copy the roster back out to move it
  between machines. The roster lives in your browser, so it's still there next
  week.
- **Eligible** — a checkbox per member decides who's in the next spin. Uncheck
  whoever was picked last week or is on leave today; one button re-checks
  everyone when a rotation completes.
- **Spin** — choose how many to pick (one, or a full-team order), then spin. The
  wheel reveals the result one pick at a time; **skip** jumps straight to the
  final order.
- **Auto-remove** — flip this on before spinning and picked members drop out of
  the next draw automatically, so a weekly rotation needs no bookkeeping.
- **Shared Wheel** — when the server has Shared mode enabled, choose **Create
  Shared Wheel — saved on server, accessible by link**, give the wheel a name,
  and copy its URL from the Shared badge. Opening or refreshing that URL loads
  the same ordered roster, eligibility, and auto-remove setting from the
  server. Shared editing and spinning are delivered by the next tickets; this
  first vertical slice deliberately keeps those controls read-only.

## API

The original stateless spin endpoint remains unchanged. When Shared mode is
enabled, the application also exposes capability discovery and Shared Wheel
creation/read endpoints.

**`POST /api/spins`**

```jsonc
// request — members is the list to draw from; count is how many to pick
{ "members": ["Ava", "Ben", "Chloe"], "count": 2 }

// 200 — drawOrder is the picked members, in the order drawn
{ "drawOrder": ["Chloe", "Ava"] }
```

`count = 1` picks one member; `count = members.length` orders the whole list.

Bad input returns **400** with a human-readable message — an empty roster, a
`count` outside `1..members.length`, blank names, or duplicate names (after
trimming):

```bash
curl -s localhost:8080/api/spins \
  -H 'Content-Type: application/json' \
  -d '{"members":["Ava","Ava"],"count":1}'
# {"message":"Member names must be unique (after trimming whitespace)."}
```

**`GET /api/capabilities`** is always available and does not touch the
database:

```json
{ "sharedWheels": false }
```

**`POST /api/shared-wheels`** creates a named Shared Wheel from a complete
Local snapshot. **`GET /api/shared-wheels/{wheelId}`** reopens the authoritative
snapshot. Both endpoints exist only when Shared mode is enabled.

```json
{
  "name": "On-call rotation",
  "autoRemove": true,
  "members": [
    { "name": "Alice", "eligible": false },
    { "name": "Bob", "eligible": true }
  ]
}
```

A successful create returns **201**, an API `Location` header, and the complete
snapshot. The snapshot includes `id`, optimistic-lock `version`, the ordered
`members`, nullable `latestSpin`, and nullable `expiresAt`. Shared API failures
use RFC 9457 Problem Details (`application/problem+json`); the legacy spin
endpoint keeps its original error format.

## Design notes

- **Local stays stateless and zero-setup.** By default there is no DataSource,
  migration, or server-side roster — the roster is kept in the browser's
  `localStorage` and sent in full with every spin. The endpoint's only job is
  turning a member list into a draw order. This keeps Local Wheel trivial and
  restart-safe, at the cost of rosters being per-browser. The trade-off and its
  original alternatives are recorded in
  [ADR-0001](docs/adr/0001-stateless-server-roster-in-browser.md).
- **Shared infrastructure is explicit.** Setting
  `LUCKY_WHEEL_SHARED_ENABLED=true` activates PostgreSQL, Flyway, and JPA.
  Startup fails if PostgreSQL is unavailable or a migration fails; it never
  silently falls back to Local-only behavior. Flyway owns schema changes and
  Hibernate validates them. H2 is not used.
- **The draw is a pure function.** Picking is `(members, count, RandomGenerator)
  → drawOrder` — a Fisher-Yates shuffle behind an injectable random source. That
  makes every property of a fair draw deterministically testable: exact count,
  no duplicates, a subset of the input, reproducibility under a fixed seed, a
  full permutation when `count` equals the roster size, and a large-sample
  uniformity check.
- **Tests cross the real boundaries.** The pure function uses deterministic
  unit tests, the legacy HTTP contract uses `MockMvc`, Shared persistence and
  migrations run against disposable PostgreSQL, and Playwright exercises the
  create/copy/open/refresh journey in a real browser.
- **Reveal is playback, not decision.** The API returns the whole draw order in
  one call; the wheel animation just replays that already-decided result, and
  skipping it changes nothing about the outcome.
- **No build step.** The UI is static HTML + vanilla JS served from the jar —
  no bundler, no `node_modules`. `java -jar` is the complete artifact.

## Shared Wheel development

To enable Shared Wheel locally, start PostgreSQL from the repository root:

```bash
docker compose up -d postgres
docker compose ps
```

Then start the application with Shared infrastructure enabled:

```bash
LUCKY_WHEEL_SHARED_ENABLED=true \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/lucky_wheel \
SPRING_DATASOURCE_USERNAME=lucky_wheel \
SPRING_DATASOURCE_PASSWORD=lucky_wheel \
./mvnw spring-boot:run
```

Stop the container while preserving the named
`lucky-wheel-postgres-data` volume:

```bash
docker compose down
```

To permanently remove the development database as well:

```bash
docker compose down --volumes
```

## Build and test

Both commands require Java 21 or later. Maven Enforcer stops the build during
`validate` when an older Java version is active. The compiled application still
targets Java 21.

Install the browser runtime once before the integration suite:

```bash
./mvnw -q exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.classpathScope=test \
  -Dexec.args="install chromium"
```

```bash
# Fast tests and the executable jar; Docker is not required
./mvnw package

# Fast tests plus PostgreSQL and real-browser integration tests
./mvnw verify
```

Surefire owns `*Test`; Failsafe owns `*IT`. Integration tests use disposable
PostgreSQL containers and the same Flyway migrations as development and
production. They therefore require a running Docker daemon and the Playwright
Chromium runtime. CI installs Chromium with its Linux system dependencies before
running `./mvnw verify`.

## Tech

Java 21 · Spring Boot 3.5 · Spring Web + Validation · optional Spring Data JPA,
Flyway, and PostgreSQL · Testcontainers · no H2 or Lombok · vanilla JS +
`<canvas>` · Playwright · Maven wrapper · GitHub Actions CI.

## License

[MIT](LICENSE) © 2026 Jerry Wang
