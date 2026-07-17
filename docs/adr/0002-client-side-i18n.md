# Client-side i18n — inline JS string table, no server changes

The UI needed a language toggle between English and Traditional Chinese
(zh-Hant), so an English-reading visitor isn't met with a Chinese-only page.
Given ADR-0001's stateless server and the "static HTML + vanilla JS, zero
build step" architecture (CLAUDE.md), the UI is rendered entirely
client-side, in the browser, from files served as-is out of the jar — so
whatever renders the strings has to run there too. Every visible string
(the ~17 static strings in `index.html` and the ~10 runtime strings in
`app.js`, including the `<canvas>` "roster is empty" text, which isn't in
the DOM at all) now lives in a `{ en: {...}, "zh-Hant": {...} }` object keyed
by id, looked up through a `t(key, params)` function. HTML marks its slots
with `data-i18n="key"` (plus `data-i18n-placeholder` / `data-i18n-aria-label`
for attributes); JS calls `t()` directly wherever it builds a string,
including for the canvas redraw. The chosen language is persisted in
`localStorage`, sibling to the roster and auto-remove keys, so an explicit
choice survives reload and a new tab the same way the roster does. Member
names are user data and never pass through `t()`.

## Considered Options

- **Spring `MessageSource` + `.properties` bundles** — rejected: the server
  is stateless and never renders HTML (ADR-0001); `MessageSource` only helps
  if something server-side is producing the markup. Wiring it in would mean
  either switching to Thymeleaf server-side rendering or adding an i18n API
  endpoint for the client to query — both contradict "zero build step,
  `java -jar` is the complete artifact" and pull the server into a concern
  it doesn't otherwise have.
- **A fetched JSON translations file** — rejected: the string table is tiny
  (two languages, ~30 keys), so a separate `GET /i18n/en.json` request buys
  nothing but an extra round trip and a first-paint flash (English markup
  briefly visible before the fetched language overwrites it, or vice versa)
  while it loads.
- **Inline JS string table** (chosen) — a plain object shipped inside
  `app.js`, already on the page before first paint. No extra request, no
  flash, no server involvement, and adding a third language later is just
  another key in the same object.

## Consequences

- The server's error messages (400 responses) stay English-only, unaffected
  by this work — they're a separate, nearly unreachable path from normal UI
  flow, and translating them is explicitly out of scope.
- Every UI string, including the ones drawn on `<canvas>` (which have no DOM
  node for `data-i18n` to target), must be read through `t()` rather than
  hardcoded — a redraw or a new notice that skips this will silently ship
  untranslated.
- The string table lives in the same file as the logic that uses it; for a
  page this size that's a feature (one file to grep), but it would need
  splitting out if the vocabulary or the language count grew substantially.
