# Handoff: KmpInspector in-app debugging UI

## Overview

KmpInspector is a debug-only Compose Multiplatform library. A draggable bubble floats over the host
app; tapping it opens a full-screen inspector with six sections: Network, Database, Background Work
(Android only), Logs, and Crashes & Exceptions. Dismissing returns to the app with the bubble where
it was left.

This bundle specifies the UI for the bubble and for all six sections, at three widths, with empty
states.

## About the design files

`KmpInspector Round 1.dc.html` is a **design reference written in HTML** — an interactive prototype
of the intended look and behaviour. It is not production code and nothing in it should be ported
literally. The task is to **rebuild these screens as Compose Multiplatform composables** in the
existing `:library` module, using Material 3 and the repo's own conventions.

Open the HTML file in a browser to see the real thing: the controls above the frame switch width
(380 / 768 / 1440), platform (Android / iOS / Desktop), populated vs. first-run data, and
annotations. Every list, filter, tab, detail view and editor is live.

## Fidelity

**High fidelity.** Colours, type sizes, weights, spacing, row heights and interaction states are
final and given as exact values below. Reproduce them. Two deliberate exceptions:

- The prototype uses **Cormorant Garamond** for headings and **Lora** for prose because that is the
  design system this was drawn in. In Compose, substitute the M3 type scale's default family for
  those roles (see *Typography*). Do not ship a serif.
- Colour values below are the **dark theme**, which is the primary case. Light theme is not yet
  designed. Wire both through `MaterialTheme.colorScheme` with dynamic color, and read the fixed
  debug palette (status colours, mono text) from a separate object — see *Colour policy*.

## Where this lands in the repo

`library/src/commonMain/kotlin/com/waqas028/kmpinspector/KmpInspector.kt` already has:

- `KmpInspector(enabled, content)` — the wrapping composable
- `DraggableInspectorFab` — drag with clamping, parks bottom-end
- `InspectorScreen` — an empty `Scaffold` with a TopAppBar and a Close TextButton

The bubble spec below **replaces** `DraggableInspectorFab`'s visuals and adds edge snap, resting
transparency and the badge. Everything else fills `InspectorScreen`. Keep the public API
(`KmpInspector(enabled) { }`) unchanged.

Because Compose Multiplatform's material3 does not ship the Material icon set, the prototype's
Material Symbols must be replaced. Either add `compose-material-icons-extended` or keep the repo's
existing approach of drawing glyphs on `Canvas`. Do not introduce a custom icon set with its own
visual language; the icons named per screen below are Material names, used as a specification of
*which* icon, not of *how* to load it.

---

## Colour policy

Three signals encode every status, in this order: **glyph, then literal text, then colour.** Colour
is never the only carrier. This is what makes dynamic color safe.

- **Dynamic color applies to chrome only** — app bar, tab indicator, ripples, selection tints,
  primary actions. Take these from `MaterialTheme.colorScheme`.
- **The debug palette is fixed** and ignores dynamic color, because a status hue derived from the
  user's wallpaper cannot be relied on. Put it in a `DebugPalette` object, not the color scheme.

### Fixed debug palette (dark)

| Token | Hex | Used for |
| --- | --- | --- |
| `bg` | `#191817` | Inspector background |
| `surface` | `#201F1E` | Search field, code blocks, SQL editor |
| `surfaceRaised` | `#232221` | Frozen grid header, cell-edit sheet, bubble fill |
| `surfaceSunken` | `#1E1D1C` | Frozen first grid column, SQL editor strip |
| `line` | `#ECE8E2` @ 13% | Hairline dividers |
| `lineStrong` | `#ECE8E2` @ 20% | Control borders |
| `lineFaint` | `#ECE8E2` @ 7% | Row separators |
| `text` | `#EDE9E4` | Primary text |
| `textDim` | `#A8A29B` | Secondary / metadata (7:1 on `bg`) |
| `textFaint` | `#6E6A64` | Tertiary, disabled, `NULL` |
| `accent` | `#E1AD66` | Selection, active tab, keys, focus |
| `ok` | `#93AD8B` | 2xx, succeeded, string literals |
| `warn` | `#E1AD66` | 4xx, running, warn level |
| `bad` | `#E08C7D` | 5xx / network failure, fatal, error level |
| `neutralState` | `#A8A29B` | 3xx, enqueued |
| `cancelled` | `#6E6A64` | Cancelled work |

All of these meet 4.5:1 against `bg` at body sizes except `textFaint` and `cancelled`, which are
only used for keywords and non-essential metadata that is duplicated elsewhere.

Selection tint: `accent` @ 9% fill plus a 2dp `accent` bar on the leading edge.
Active pill/chip: `accent` @ 14% fill, 1dp `accent` border, `accent` text.
Hover (desktop): `text` @ 7%. Pressed: `text` @ 14%.
Focus: 2dp `accent` ring, 2dp offset — never the platform default.

## Typography

| Role | Prototype | Compose | Size / weight / line-height |
| --- | --- | --- | --- |
| Inspector title | Cormorant Garamond 600 | `headlineSmall` | 25sp, W400 |
| Tab label | Cormorant Garamond | `titleSmall` | 15sp, W400 |
| Section heading in a detail pane | mono 500 | `titleSmall` mono | 13–14sp |
| Body prose (crash message, empty-state copy) | Lora | `bodyMedium` | 13sp, 1.6 line-height |
| **Everything technical** | JetBrains Mono | **JetBrains Mono**, bundled | see below |
| Metadata / timestamps | mono | mono | 10.5–11sp, `textDim` |
| Kicker / section label | mono, 0.1em tracking, uppercase | mono | 10sp, `textFaint` |

Bundle **JetBrains Mono** (400/500/700) as a library resource and use it for every path, method,
status code, header, JSON, SQL, cell value, log line, stack frame, worker name, id, constraint and
timestamp. Mono sizes: 11.5–13sp for values, 12sp/1.7 for JSON and stack frames, 10.5sp for
metadata.

Enable **tabular figures** (`FontFeatureSetting("tnum")`) on every column of numbers — durations,
sizes, timestamps, row counts, attempt counts. Leave prose in proportional figures.

## Spacing, shape, elevation

- Spacing: 4 / 8 / 12 / 16 / 24 / 36 dp.
- Corner radius: 4dp on controls, code blocks and chips-that-are-not-pills; 16dp on pills; 50% on
  the bubble and icon buttons.
- Elevation: borders and tints, not shadows. The only shadow in the whole UI is under the floating
  bubble (`0 4dp 14dp` black @ 30%).

## Touch targets — the hit-slop rule

Every interactive element has a **48dp minimum hit area**, but chips, level letters and toggles
**draw at 32dp** inside it. A toolbar of 48dp-tall pills reads as a wall of buttons; the visual
weight has to stay quiet while the target stays legal.

In Compose: `Modifier.minimumInteractiveComponentSize()` or explicit padding on the `clickable`,
with the visual `Surface`/`Box` sized 32dp inside. Do not solve this by growing the shape.

Compliant sizes to reproduce: list rows 64dp (network) / 68dp (work) / 72dp (crashes) / 52dp
(tables); tabs 48dp; grid cells 48dp; icon buttons 48×48dp; text buttons 48dp tall.

---

# Screen 1 — Floating bubble

**Purpose.** Always-available entry point that must never break the host app.

**Geometry.** 56dp circle. `surfaceRaised` fill, 1dp `accent` border, `accent` 22dp
`travel_explore` glyph.

**Resting state.** Two thirds of the circle sits outside the viewport (translate ±16dp toward the
snapped edge) at **40% opacity**, so it covers ~18dp of the host app instead of 56dp. On touch-down
it returns to full opacity and scales to 1.06.

Contrast note: the resting state is deliberately below 4.5:1. It is decorative and carries no
information — every informational state (unread, crash) is opaque. Contrast requirements are met by
the opaque states.

**Drag and snap.** Free drag, clamped to the window. On release, snap **horizontally only** to the
nearer side (220ms, `cubic-bezier(.2,.8,.2,1)` — use a spring or `FastOutSlowInEasing`); keep the
vertical position exactly where the finger left it. Do not snap to top or bottom edges: those are
where host apps keep their own app bar and bottom nav.

**Badge.** Unread count (new network requests since last opened). 20dp min-width pill, `accent`
fill, `#1B1A19` text, mono 700 11sp, tabular. Positioned top-outer corner relative to the snapped
side. **The badge always renders at full opacity, and a non-zero count forces the bubble itself to
92% opacity.** A translucent number is not a number.

**Crash state.** Fill `#5A2A24`, border and glyph `bad`, badge shows the exception count, and a halo
pulses twice (2.4s, `0 → 9dp` spread, `bad` @ 45% → 0). Three simultaneous signals, so the state
survives greyscale and red-blindness.

**Tap.** Clears the unread count and opens the inspector. The bubble hides while the inspector is
open (already the case in `KmpInspector.kt`).

# Screen 2 — Inspector shell

Full-screen `Surface` over the host app.

**Header.** Title "KmpInspector" (`headlineSmall`). Under it, one mono 11sp `textDim` session line:
`com.example.shop · debug · Android 14 · 04:12 captured` — package, variant, OS, capture uptime.
This is what a developer checks their bug report against, and it makes screenshots
self-documenting. A 48dp `close` icon button sits at the trailing edge.

**Search.** Full-width 48dp field, `surface` fill, 1dp `lineStrong`, 4dp radius, leading `search`
icon, mono 13sp input. Placeholder names the active section: `Search network…`, `Search logs…`.

Search is **scoped to the active tab**, and says so. One field meaning seven different things is
worse than a per-section filter; a cross-section search would need result grouping and a screen of
its own. Filtering is live, no submit — every dataset here is local.

**Tabs.** Scrollable single row, 48dp tall, icon + label, active tab in `accent` with a 2dp
underline. Order: Network (`swap_vert`), Database (`table_chart`), Background Work
(`work_history`, **Android only** — absent, not disabled, elsewhere), Logs (`subject`), Crashes
(`error`, with a 5dp `bad` dot when unread crashes exist).

Scroll, do not wrap or collapse into a "more" menu: six sections never fit 380dp, and a menu hides
exactly the section you need. **Selecting a tab scrolls it into view** so the active underline is
never off-screen (`ScrollableTabRow` does this).

**Responsive.** One body, three arrangements:

| Width | Master–detail |
| --- | --- |
| 380dp phone | Single pane. Detail replaces the list; a back arrow returns. |
| 768dp tablet | 300dp list pane, hairline divider, detail fills the rest. Both visible. |
| 1440dp desktop | 420dp list pane, detail fills the rest. Back arrow suppressed. |

At ≥768dp the back arrow disappears and selecting a row updates the detail pane in place. Logs is
always a single full-width pane — it has no detail view.

# Screen 3 — Network

## List

Two lines per row, six facts, 64dp tall. One line cannot hold method, path, status, duration, size
and time at 380dp without truncating all of them, so line one **identifies** the call and line two
**measures** it.

- **Line 1:** status mark (18dp circle, 1dp border in the status tone, glyph inside) · status code
  (mono 500 12.5sp, tone-coloured, tabular) · method (mono 500 11sp, `textDim`, 0.04em tracking) ·
  path (mono 12.5sp, `text`, **head-truncated with a leading ellipsis** — the tail is the part that
  differs; budget ~26 chars at 380dp, 30 at 768, 48 at 1440).
- **Line 2:** duration (52dp column) · a 2dp duration bar (`min(ms/1500, 1)` of the width; `warn`
  above 800ms, otherwise `text` @ 35%) · response size (52dp, trailing) · timestamp (64dp,
  trailing). All mono 11sp `textDim` tabular.

**Status encoding.** Glyph + code + colour: `✓` 2xx `ok`, `↻` 3xx `neutralState`, `!` 4xx `warn`,
`✕` 5xx `bad`, `✕` `ERR` `bad` for a transport failure (show the exception name in the detail).

**Filter chips.** Pinned row above the list: `All · 8`, `Errors · 3`, `Slow`, `Writes`. Errors-only
is the most-used view in any network log — one tap, permanently visible, count on the chip. 32dp
pills in 48dp targets.

**Footer.** `6 of 8 requests · capture buffer 200`.

## Detail

**Header.** Method, status mark, code and reason phrase on one mono line; then the full URL, which
**wraps** across lines and never scrolls sideways; then one tabular metadata line:
`↑ 184 B  ↓ 18.4 kB  ·  142 ms  ·  h2  ·  12:04:31.882`. These are the numbers you paste into a
bug report.

**Tab row.** Request / Response / Headers, mono 12.5sp, 48dp, active in `accent` with a 2dp
underline. **Copy as cURL is peer to the tabs, not buried in a menu** — it is the most-used action
in a network inspector. At ≥768dp it is a labelled 48dp button; at 380dp it collapses to a 48dp
icon-only button (`content_copy`). Tapping it copies and reveals the exact command in a sticky
strip at the bottom of the pane, so the developer can see what went to the clipboard.

**JSON viewer.** Pretty-printed, collapsible per node.

- Row heights: 36dp for a branch (collapsible, whole row is the target), 24dp for a leaf. Indent
  14dp per depth level. Caret `▸`/`▾` in a 14dp gutter.
- Collapsed branches keep their child count: `{ … 4 keys }`, `[ … 2 items ]` — often all you needed.
- Colours: keys `accent`, strings `ok`, numbers and booleans `accent`, `null` `textFaint` italic,
  punctuation `textFaint`.
- Arrays collapse past the first element by default.
- `collapse all` / `expand all` buttons plus a content-type line: `application/json · 18.4 kB · gzip`.
- Long values wrap (`word-break: break-all` equivalent); no horizontal scroll anywhere.

**Headers tab.** Two groups, Request and Response, each with a mono 10sp `accent` uppercase heading.
Rows are a 34%/66% two-column grid, key `textDim`, value `text`, both wrapping, 1dp `lineFaint`
separators.

# Screen 4 — Database

## Table list

52dp rows: `table_chart` icon, table name (mono 13sp), row count (mono 11.5sp, tabular, trailing,
`textFaint` when zero), `chevron_right`. Header strip above it: `app.db · SQLDelight · 2.1 MB` and a
48dp `SQL` button (`terminal` icon, `accent` outline).

## Data grid

**Both the header row and the first column freeze.** Scrolling right is useless if you lose which
row you are on, so the primary key stays pinned while the rest slides.

- Header: `surfaceRaised`, sticky, 8dp padding, column name (`text`, mono 500 11sp) with its **type
  underneath in 10sp `textFaint`** — `INTEGER PK`, `TEXT NULL`, `BLOB`. The type decides how a value
  should be read.
- Cells: 48dp tall, mono 12sp, tabular, single line, clipped with an ellipsis, `lineFaint` bottom
  border. First column has `surfaceSunken` fill and a `line` trailing border.
- Selected cell: `accent` @ 16% fill and a 1dp `accent` inset border.

**The three awkward value types, each with its own treatment:**

| Value | Renders as |
| --- | --- |
| `NULL` | the word `NULL`, `textFaint`, italic — never an empty cell |
| `BLOB` | a bordered chip, `BLOB · 12.4 kB` — type and size, never mojibake |
| Empty string | an em dash, `textFaint` — visibly distinct from `NULL` |
| Very long text | clipped to one line with an ellipsis; full value in the editor |

**Cell editor.** Tapping a cell opens a **sheet pinned to the bottom of the pane**, not an inline
field: a phone keyboard would cover the grid. It shows the cell's address
(`order_items · note · row 1 · TEXT`), a multi-line mono text area with the full value, and
`Update row` / `Set NULL` buttons plus the warning `Writes go straight to the device database.`
Query results are read-only and say so.

**SQL editor.** A `surfaceSunken` strip above the grid: mono 12sp/1.6 multi-line input,
`Run` button, and a status line — `Read-only. SELECT and WITH only.` before running, `ok · 4 rows ·
6 ms` after, or the error in `bad`. **Results render in the same grid component** — one presentation
for browsing and querying means one thing to build and one thing to learn. On error the previous
result stays on screen.

# Screen 5 — Background Work (Android only)

## List

68dp rows, three lines: **state badge first** (glyph + word, 20dp, 1dp border in the state tone,
mono 500 9.5sp, 0.06em tracking) with the attempt count opposite it; then the worker name (mono 500
13sp); then last and next run as a two-line tabular pair in mono 10.5sp `textFaint`.

State first because the question is almost always "did it run, and when does it run again". Attempts
sit opposite the badge because they only matter when something failed.

| State | Glyph | Tone |
| --- | --- | --- |
| Enqueued | `○` | `neutralState` |
| Running | `◔` | `warn` |
| Succeeded | `✓` | `ok` |
| Failed | `✕` | `bad` |
| Cancelled | `⊘` | `cancelled` |

Header strip: `WorkManager 2.10 · 5 jobs`.

## Detail

Worker name, state badge, then `id 9f2c-4d81 · tag "sync" · attempt 3`. Below, four labelled blocks
in read order, each headed by a mono 10sp `textFaint` uppercase label:

1. **SCHEDULE** — `last` and `next` as a label/value pair, tabular. Next run states the reason:
   `12:34:26 (backoff, exponential 30s)`, `18:00:00 (periodic, 6h)`, `— (running)`.
2. **CONSTRAINTS** — 24dp bordered chips reading like the enum values they are:
   `NETWORK: CONNECTED`, `BATTERY_NOT_LOW`, `IDLE`, `CHARGING`, `STORAGE_NOT_LOW`.
3. **INPUT DATA** / **OUTPUT DATA** — key–value tables, 38%/62%, mono 11.5sp, values wrapping.
   **Omit the output block entirely when a job has produced none** rather than showing it empty.
4. **Failure reason** (or **Cancellation reason**) — a block with a 2dp leading rule in `bad`
   (`textDim` for cancellations) and the full reason in mono 11.5sp/1.7, e.g. `HTTP 500 from
   /v2/recommendations after 3 attempts. Result.retry() returned; exponential backoff 30s → 60s →
   120s.`

# Screen 6 — Logs

Single full-width pane, chronological.

**Level filter is a floor, not five checkboxes.** Five independent toggles is five taps to answer
"show me warnings and worse". A row labelled `MIN` holds `V D I W E` as 32dp letter buttons in 48dp
targets; tapping `W` means W and E, and letters below the floor grey to `textFaint` so the exclusion
is visible. **Letters, not colours, name the level.**

**Tag filter** is a native dropdown (`select` / `ExposedDropdownMenu`), 48dp, because tag lists grow
past what chips can hold. Default `All tags`.

**Auto-scroll toggle** is a pill reading **`Tailing`** or **`Paused`** with a `pause`/`play_arrow`
icon — a word, not an ambiguous icon. It is **pinned outside the scrolling filter row** so it is
reachable at 380dp without discovering a sideways scroll. Live tailing is a primary feature; it must
not be the least discoverable control on the screen.

**Rows.** Metadata above, message below, nothing sideways:

- Line 1, mono 10.5sp tabular: level chip (18dp square, 3dp radius, 1dp border in the level tone,
  the letter inside) · timestamp `textDim` · tag, tone-coloured, 0.04em tracking.
- Line 2: the message, mono 12sp/1.65, wrapping freely with `overflow-wrap: anywhere`. Verbose lines
  set in `textDim`, everything else in `text`.

Level tones: E `bad`, W `warn`, I `text`, D and V `textDim`.

**Footer.** `12 of 15 lines · ring buffer 2,000 · live` (or `· paused at 12:04:31`).

Search filters the message and tag and **does not stop tailing**.

# Screen 7 — Crashes & Exceptions

## List

**Fatal and non-fatal are one time-ordered list with a filter, not two lists** — the order in time
is what tells you a handled exception preceded the crash. Filter chips: `All · 3`, `Fatal · 1`,
`Non-fatal · 2`.

72dp rows, four lines:

1. A badge — `✕ FATAL` in `bad` or `! CAUGHT` in `warn`, 20dp, 1dp border, mono 500 9.5sp, 0.08em
   tracking — then, trailing, a `×7` repeat chip (`text` @ 10% fill, mono 500 10sp, tabular) and the
   timestamp.
2. Exception type, mono 500 13sp `text`, wrapping.
3. Message, **body face** 12sp `textDim`, wrapping — it is prose and reads faster that way.
4. Origin, mono 10.5sp `textFaint`: `CheckoutViewModel.kt:118`.

## Detail

**Header.** `FATAL · app terminated` / `NON-FATAL · caught` in the tone, then the exception type in
mono 14sp, then the message in the body face, then
`main thread · 12:04:39.201 · 7 occurrences`.

**Actions, at the top** where you reach after reading: `Copy trace` (`accent` outline, 48dp),
`Share` (`ios_share`, outline), and `Hide framework frames` / `Show framework frames`.

**Caused by**, when present, gets its own bordered block above the trace in `bad` — the root cause
is usually the answer: `Caused by: NumberFormatException: For input string: "12,900"`.

**Stack trace.** Header `STACK TRACE` with `2 app · 3 framework`.

**App frames are pulled left and marked; framework frames are indented and dimmed.**

| | App frame | Framework frame |
| --- | --- | --- |
| Leading rule | 2dp `accent` | 1dp `line` |
| Fill | `accent` @ 6% | none |
| Indent | 0 | 10dp |
| Text | `text` | `textDim` |

Three signals again — rule, tint, brightness — so the frames you can act on are findable in a
greyscale screenshot. Frames are mono 11.5sp/1.6 and **wrap**; they never scroll horizontally. The
framework toggle hides them entirely for a short, readable trace.

Classify a frame as "app" by matching the host app's package prefix; everything else (androidx,
kotlinx, android, io.ktor, platform frames) is framework.

# Screen 8 — Empty and error states

Every list gets a first-run state, centred, ~34ch measure, and **every one contains the fix** — an
empty list that only says "nothing here" wastes the one moment the developer is looking for
instructions. Structure: 40dp `textFaint` icon → `headlineSmall` title → one `bodyMedium` `textDim`
sentence → a mono code block on `surface` with the actual setup lines → a `Copy snippet` action.

| Section | Title | Snippet |
| --- | --- | --- |
| Network | No requests captured | `HttpClient { install(InspectorPlugin) }` |
| Logs | Nothing logged yet | `InspectorLog.i("CartStore", message)` |
| Crashes | No crashes this session (icon `check_circle` in `ok`) | `Inspector.recordNonFatal(e)` |
| Background Work | No work scheduled | `WorkManager.getInstance(ctx).enqueue(syncRequest)` |

Crashes and Background Work add a closing mono 11sp `textFaint` note: `Buffer survives process death
· cleared on uninstall` and `Android only — the tab is absent on iOS and desktop`.

Error states (a failed DB read, a bad SQL statement) report **in place, above the content**, and
never replace a result the developer is still reading.

---

## State

Hoist into one `InspectorState` (or a `ViewModel` per section — the shell only needs the first two):

| State | Type | Notes |
| --- | --- | --- |
| `open` | Boolean | already in `KmpInspector.kt` |
| `bubbleOffset`, `dragging`, `snappedEdge` | Offset / Boolean / enum | vertical position persists, horizontal snaps |
| `unreadCount`, `hasCrash` | Int / Boolean | drive badge and crash state; cleared on open |
| `tab` | enum | tab list is platform-dependent |
| `query` | String | **reset when the tab changes** — it is scoped |
| `selectedRequest`, `detailTab`, `collapsedJsonPaths`, `curlVisible` | Int? / enum / Set&lt;String&gt; / Boolean | |
| `selectedTable`, `sqlOpen`, `sqlText`, `sqlResult`, `sqlError`, `editingCell`, `cellDraft` | | selecting a table clears any query result |
| `logMinLevel`, `logTag`, `tailing` | Int / String / Boolean | |
| `crashFilter`, `selectedCrash`, `hideFrameworkFrames` | | |
| `selectedWork` | Int? | |

Selection state is per section and clears on tab change. At ≥768dp a null selection shows a
"select a request" placeholder in the detail pane rather than an empty one.

Capture buffers: network 200 requests, logs 2,000 lines (ring), crashes persisted across process
death.

## Accessibility checklist

- 48dp minimum hit area everywhere, via hit-slop — verify with the accessibility scanner.
- 4.5:1 minimum for all text on `bg`; `textFaint` only for keywords duplicated elsewhere.
- No colour-only status encoding anywhere: glyph + text + colour, in that order of priority.
- `contentDescription` on every icon-only control (the bubble already has one).
- Focus order follows reading order; focus ring is the 2dp `accent` outline, never the default.
- Nothing scrolls horizontally except the data grid and the tab strip — both deliberate.

## Requirements that conflict, and how they were resolved

Carry these into the implementation; they are decisions, not oversights.

1. **Bubble opacity vs. 4.5:1.** A 40%-opacity control cannot meet 4.5:1. The resting state is
   decorative and carries no information; unread and crash states are opaque, and the badge never
   dims. Contrast rules apply to the opaque states.
2. **Dynamic color vs. no colour-only status.** If tonal palettes come from the wallpaper, no status
   hue is guaranteed. Colour is the third signal after glyph and literal code, drawn from a fixed
   palette; dynamic color is confined to chrome.
3. **Global search across six sections.** Scoped to the active tab, and the placeholder says so. A
   genuine cross-section search needs result grouping and its own screen.

## Assets

No image assets. Icons are Material Symbols, named per screen. One bundled font family: JetBrains
Mono (400/500/700).

## Files

- `KmpInspector Round 1.dc.html` — the interactive design reference. Open in a browser; use the
  controls above the frame to switch width, platform, populated/first-run data and annotations. The
  right-hand column carries the layout rationale for whatever view is on screen.
- Target for the implementation:
  `library/src/commonMain/kotlin/com/waqas028/kmpinspector/` (`KmpInspector.kt` today).
