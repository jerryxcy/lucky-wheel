# Lucky Wheel UI prototype

> PROTOTYPE — throwaway. This directory is not production code and must not be
> copied directly into the Spring Boot application.

## Question

Which information hierarchy should the single-page Lucky Wheel use in a team
meeting?

- **A — 工作台:** roster, wheel, and draw order stay visible together.
- **B — 舞台:** the wheel and reveal dominate; roster management moves into a
  drawer.
- **C — 清單流:** roster, settings, and spin become a guided vertical flow.

Round 1 decision (2026-07-16): **B — 舞台** was selected for further design
exploration.

## Run

From the repository root:

```sh
python3 -m http.server 4173 --directory ui-prototype
```

Open <http://localhost:4173/>. Use the floating arrows or the keyboard left and
right arrow keys to switch variants. Each variant also has a shareable URL:

- <http://localhost:4173/?variant=A>
- <http://localhost:4173/?variant=B>
- <http://localhost:4173/?variant=C>

The prototype keeps state only in memory and stubs `POST /api/spins` with the
same `{ members, count } -> { drawOrder }` contract. Reloading resets it.

## Round 2 — palette study

Open `round2-styles.html` and compare:

- `?variant=P0` — 原版・基準
- `?variant=P1` — 漆夜・朱金
- `?variant=P2` — 煙燻・寶石
- `?variant=P3` — 礦物・霧面

For example: <http://localhost:4173/round2-styles.html?variant=P0>.

Round 2 decision (2026-07-16): **P0 — 原版色票** was selected. Compared with
the low-chroma alternatives, P0 keeps the strongest balance of segment
legibility, meeting-room energy, and a clear visual focus on the wheel.

## Captured design direction

The production rewrite should use the **B stage layout with P0's original
palette**. Before production implementation, record this verdict on GitHub
issue #1. Per the prototype workflow, the complete prototype should eventually
live only on a throwaway branch; main should retain the validated decision and
the production rewrite.
