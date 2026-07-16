# Stateless server — roster lives in the browser

The wheel is used in real work settings (e.g. weekly stand-up on someone's laptop), so the roster must survive server restarts without anyone re-typing names. Instead of persisting server-side, the server holds **no state at all**: the browser keeps the roster in `localStorage` and sends the full member list with every spin request; the API's only job is "given a list of members, return a random pick/order".

## Considered Options

- **H2 + Spring Data JPA** (the original plan in CLAUDE.md) — rejected: adds an entity/repository layer that conflicts with the "as simple as possible, easy to review and explain in interviews" goal, for data that is just a short list of names.
- **Server in-memory `Map`** — rejected: loses the roster on every restart, which is worse than localStorage for the actual usage pattern (laptop restarted daily), while still adding server-side lifecycle to design around.

## Consequences

- There is no `Team` resource on the server; team management is purely a UI concern.
- The randomness/fairness logic is a pure function of the request — trivially unit-testable.
- If shared rosters across devices are ever needed, this decision gets revisited (that's the point at which a DB earns its place).
