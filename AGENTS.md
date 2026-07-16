# Lucky Wheel

## Project Goal

A spin-wheel decision tool: teams use it to randomly pick a member for on-call/duty rotation, or to draw an order (e.g. who picks first).

- Create a roster, add member names
- Spin the wheel to randomly pick a member
- Possible future extensions: draw history, avoiding repeat picks, WebSocket live wheel animation

## Technical Plan

- Java 21 + Spring Boot 3.x (generated via Spring Initializr)
- Dependencies: Spring Web, Validation (**no** Data JPA / H2 / Lombok — see ADR-0001)
- **Server is fully stateless**: no DB, roster lives in browser localStorage; single endpoint `POST /api/spins`
- UI: static HTML + vanilla JS under `src/main/resources/static`, zero build step, `java -jar` is the complete artifact
- Priorities: tests (unit tests for the random-draw logic), README quality, clean API design
- Domain vocabulary in `CONTEXT.md` (Roster / Member / Eligible / Spin / Draw order / Reveal)

## Development Workflow

Uses the mattpocock/skills workflow (installed in `.agents/skills/`; Codex reads this directory directly, and it's symlinked to `.claude/skills/` for Claude Code):

1. `/setup-matt-pocock-skills` — bootstrap (domain docs, issue tracker) ✅ done
2. `/domain-modeling` — define domain concepts (Team, Member, Wheel/Spin, draw history)
3. `/to-spec` → `/to-tickets` — spec and ticket breakdown
4. `/tdd` or `/implement` — implementation
5. `/code-review`, `/grill-me` — design review

## Current Status (2026-07-16)

- GitHub repo (private, will go public later): https://github.com/jerryxcy/lucky-wheel
- `/grill-with-docs` ✅: architecture settled (stateless server, ADR-0001), glossary (CONTEXT.md), MVP scope
- `/to-spec` ✅: MVP spec in GitHub Issue #1 (label: ready-for-agent)
- `/prototype` ✅: settled on the stage layout with the P0 original palette; verdict commented on Issue #1; prototype source lives on throwaway branch `prototype/ui` (`ui-prototype/` is `.gitignore`d, not on main)
- Spring Boot skeleton ✅: Java 21, Boot 3.5.14, Maven wrapper, Web + Validation; namespace `io.github.jerryxcy.luckywheel`; `./mvnw clean test` green (context smoke test only so far)
- Next: `/to-tickets` → `/tdd`
- Minor follow-ups: LICENSE + empty pom placeholders (url/licenses/developers/scm), `spring.application.name` to kebab-case, GitHub Actions CI, README

## Agent skills

### Issue tracker

Issues are tracked as GitHub Issues via the `gh` CLI (repo needs a GitHub remote first). See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
