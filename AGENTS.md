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

### Ticket workflow

One ticket = one fresh agent context. Each ticket ships as a branch + PR that references its issue (`Closes #N`): branch → PR → CI green → `/code-review` on the PR diff (Standards + Spec axes) → findings fixed → merge, and the merge decision belongs to the user, never the implementing agent. Direct pushes to main are reserved for docs-only changes.

## Where work is tracked

Spec: issue #1. Tickets: its sub-issues, with native blocked-by dependencies — work any open ticket whose blockers are closed. The UI prototype verdict (stage layout, P0 original palette) is commented on issue #1; prototype source lives on the throwaway branch `prototype/ui` (`ui-prototype/` is `.gitignore`d, not on main).

## Agent skills

### Issue tracker

Issues are tracked as GitHub Issues via the `gh` CLI (repo needs a GitHub remote first). See `docs/agents/issue-tracker.md`.

### Triage labels

Default label vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
