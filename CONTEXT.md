# Lucky Wheel

A spin-wheel decision tool for real workplace use: pick who's on-call, or draw an order (who chooses seats first).

## Language

**Roster**:
The collection of members managed together for spins.
_Avoid_: team, group, list

**Member**:
One name on the roster.
_Avoid_: player, participant, user

**Local Wheel**:
A wheel whose roster and eligibility belong to one browser.
_Avoid_: private wheel, offline wheel

**Shared Wheel**:
A wheel whose roster, eligibility, settings, and spin records are shared by every browser using its URL. It supports the same roster and spin operations as a Local Wheel, plus a wheel name, shareable link, live-sync status, expiry notice, and permanent deletion. Anyone holding the URL may view and operate it.
_Avoid_: team, room, session

**Wheel name**:
A human-readable label for a Shared Wheel's purpose. It may change and need not be unique; the wheel ID, not its name, establishes identity.
_Avoid_: team name, wheel ID

**Eligible**:
A member currently checked to take part in spins. Unchecked members stay on the roster but are excluded (used both for "picked last week" and "on leave today"). One action re-checks everyone when a rotation completes.
_Avoid_: active, enabled, available

**Auto-remove**:
A wheel setting that makes members picked by a spin not eligible for future spins.
_Avoid_: skip winners, remove winners

**Spin**:
A draw from the eligible members that produces a draw order of the requested size. The whole result is decided before the reveal begins.
_Avoid_: roll, draw request

**Draw order**:
The result of a spin — the picked members, in the order they were drawn. count=1 picks one member; count=roster size orders everyone.
_Avoid_: winners, result list

**Spin record**:
The immutable record of a completed Shared Wheel spin. It preserves when the spin occurred, its eligible members, its draw order, and whether auto-remove applied.
_Avoid_: history entry, audit log, activity

**Reveal**:
The front-end animation that plays back an already-decided draw order one pick at a time (the wheel spins once per pick, removing each picked member from the wheel). Skippable — skipping jumps straight to showing the full draw order.
_Avoid_: animation (when meaning the playback concept)
