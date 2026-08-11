# Shared Wheel controls prototype

> PROTOTYPE — throwaway. Do not promote this implementation directly to the
> Spring Boot application's static UI.

## Question

How should the existing stage-style Lucky Wheel expose Local versus Shared
mode, Shared identity, connection status, link, expiry, and deletion without
making the projected wheel feel like an administration screen?

## Variants

- **A — Mode tabs:** persistent two-option header with a Shared status strip.
- **B — Control rail:** explicit left rail owns mode choice and Shared details.
- **C — Stage badge:** projection-first stage with a persistent identity badge
  and an on-demand command sheet.

## Verdict

Selected on 2026-08-11: **C — Stage badge**.

It keeps the projected Wheel clean while the always-visible badge still answers
whether this is Local or Shared and identifies the current Shared Wheel. Shared
link, expiry, rename, roster, and permanent delete stay one click away in the
command sheet. In Local mode, `Create Shared Wheel — saved on server,
accessible by link` remains a persistent callout rather than being hidden in
that sheet.

## Run

From the repository root:

```sh
python3 -m http.server 4173 --directory ui-prototype
```

Open `http://localhost:4173/shared-wheel.html?variant=C`. Use the floating
arrows or keyboard left/right arrows to compare A, B, and C. State and server
interactions are in-memory stubs and reset on reload.

