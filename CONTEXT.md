# Lucky Wheel

A spin-wheel decision tool for real workplace use: pick who's on-call, or draw an order (who chooses seats first). The server is stateless; the roster lives in the browser.

## Language

**Roster**:
The list of member names a spin draws from. Kept in the browser, sent with every spin request.
_Avoid_: team, group, list

**Member**:
One name on the roster.
_Avoid_: player, participant, user

**Eligible**:
A member currently checked to take part in spins. Unchecked members stay on the roster but are excluded (used both for "picked last week" and "on leave today"). One action re-checks everyone when a rotation completes.
_Avoid_: active, enabled, available

**Spin**:
One draw: given a roster and a count, produces the draw order. The whole result is decided at once, server-side.
_Avoid_: roll, draw request

**Draw order**:
The result of a spin — the picked members, in the order they were drawn. count=1 picks one member; count=roster size orders everyone.
_Avoid_: winners, result list

**Reveal**:
The front-end animation that plays back an already-decided draw order one pick at a time (the wheel spins once per pick, removing each picked member from the wheel). Skippable — skipping jumps straight to showing the full draw order.
_Avoid_: animation (when meaning the playback concept)
