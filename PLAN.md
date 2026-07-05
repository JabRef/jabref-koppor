# Plan: Entry editor "Main" tab as an editable semantic preview (in-place editing)

Issue: <https://github.com/JabRef/jabref/issues/12711> (concept #2)
Predecessor: concept #1 (single scroll list) — designed in
<https://github.com/JabRef/jabref-koppor/pull/731>, **merged into main as #16166**.
This branch evolves the merged `AllFieldsTab` into an editable semantic preview.

Branch: `new-entry-editor-in-place-edit` (worktree `jabref-worktree-1`, on origin/main).

## Target UX (Oliver 2026-07-05)

- Tab "Main" shows a **semantic preview** (citation-like rendering: authors, title,
  venue, volume/pages, year, …) instead of the key–value list.
- **In-place editing**: click a field's text in the preview → the real field editor
  (`FieldEditorFX`) opens for it; commit on Enter / focus loss, cancel on Esc.
- Required-but-unset fields render as **`{{Field}}` placeholders** in the preview,
  clickable/editable the same way.
- **Chips below the preview** for adding fields; fields not represented in the preview
  stay **grouped as today** (Identifiers / Files & links / Bibliometrics / Comments /
  Meta sections + free-form add row — all already upstream via #16166).
- **No duplication**: a field rendered in the preview never appears again below —
  the preview takes precedence; the grouped part is only the fallback for fields the
  template does not cover (reduce cognitive load).

## Architecture facts (verified 2026-07-05)

- Existing preview = HTML in a WebView (flat string) → **not editable**; the semantic
  preview is a new node-based component (TextFlow) driven by `BibEntry` directly
  (`AuthorList.parse` for names, `LatexToUnicodeAdapter.format` for display text).
- Field editors two-way bind automatically: `FieldEditors.getForField(...)` +
  `bindToEntry(entry)` — typing writes `entry.setField` (with undo), external changes
  update the control. No extra commit logic needed for in-place editors.
- Upstream `AllFieldsTab` (keep the name — open q. 3) already provides: editor map +
  labels via `FieldsEditorTab`, natural-height scroll layout hooks, sections + chips
  (`FieldListSections`), free-form add, live refresh via entry event bus
  (`@Subscribe FieldChangedEvent`, rebuild only when shown set changes),
  `requestFocus(Field)`, preview SplitPane.
- Note: #16161 migrated JabRefGuiPreferences to PreferenceBindings — don't rely on
  older notes about prefs plumbing.

## Design

1. **`CitationSegments`** (plain Java, unit-testable) — (BibEntryType?, BibEntry) →
   ordered tokens: `TextToken` (punctuation) | `FieldToken(field, displayText, style)`
   | `PlaceholderToken(field)`. Templates: article / book-family / part-of-collection
   ("In …") / thesis-or-report / fallback; slots resolve alias candidates (first set,
   else first unmet-required → placeholder): author|editor, year|date,
   journal|journaltitle, school|institution. Volume/number/pages composite
   "12(3):45–67". Punctuation suppressed when a slot emits nothing. Required fields
   outside the vocabulary → trailing placeholder sentence. `coveredFields()` =
   all rendered fields = the "no duplication" source of truth.
2. **`SemanticPreviewFlow`** (JavaFX) — TextFlow over the tokens; field/placeholder
   segments clickable (hover affordance); click opens the bound editor **in place**
   (inline in the flow if it cooperates; otherwise an editor row directly beneath the
   flow with the segment highlighted — decide in step 3). While an editor is open,
   flow re-render is suppressed; re-render on close/external changes.
3. **`AllFieldsTab` layout** (single scrolling column, as upstream): citation key +
   entry type header → semantic preview flow → add-chip bar (uncovered optional
   fields; "Show more") → "More fields" rows (set MAIN-section fields not covered:
   abstract, keywords, …) → sections with chips → free-form add row.
   Covered fields are subtracted from every chip list and row list.

## Steps (check off as done; commit after each step)

- [ ] **1. `CitationSegments` + unit tests** (class written, needs: compile, tests,
  checkstyle, commit). *Check: article/book/inproceedings/thesis/fallback rendering,
  placeholders, punctuation suppression, alias slots, coveredFields, latex→unicode,
  pages en-dash, length cap, trailing required placeholders.*
- [ ] **2. `SemanticPreviewFlow` read-only + tab integration**: TextFlow + CSS
  (value/placeholder/punctuation, italic style, hover); mount atop `AllFieldsTab`;
  subtract covered fields from chips/rows. *Check: compile; GUI smoke on
  DISPLAY=:10.0, screenshots.*
- [ ] **3. Click-to-edit**: open bound editor for clicked segment (inline swap or
  editor-row-under-flow); Enter/focus-out close; Esc restores pre-edit value;
  placeholder click starts empty.
- [ ] **4. Re-render policy**: suppress while editing; re-render on close + external
  changes (Source tab, fetchers, undo); entry switch resets. *Check 3+4: value lands
  in entry (Source tab); no focus loss mid-typing.*
- [ ] **5. Citation key + entry type header row**, click-to-edit (CitationKeyEditor
  incl. generate button).
- [ ] **6. Focus & navigation**: `requestFocus(Field)` (JumpToField) routes covered
  fields to the in-place editor; Tab order sensible.
- [ ] **7. Live verification** on DISPLAY=:10.0: full manual pass (each segment type,
  placeholders, chips, sections, free-form add, undo/redo, entry switch, type
  change), screenshots; entryeditor tests + LocalizationConsistencyTest + checkstyle.
- [ ] **8. Housekeeping**: CHANGELOG, l10n keys, requirements doc
  (docs/requirements/entry-editor.md) extended for concept #2, dead code check.
- [ ] **9. Push to `koppor` remote + PR** at github.com/JabRef/jabref-koppor for
  internal inspection (like #731). Keep PLAN.md + incremental commits (no squash).

## Open questions (ask Oliver / decide before the relevant step)

1. Classic side-by-side PreviewPanel on the Main tab: keep (interim) or hide —
   semantic preview + CSL preview is double preview?
2. Semantic preview style: fixed hand-rolled template (interim) vs derived from the
   user's preview/CSL style (hard: CSL output is opaque HTML)?
3. Class name stays `AllFieldsTab` (upstream name, minimal diff) — rename only if
   reviewers ask.
4. Header row style: "@article · citationkey" line (interim) or plain rows?
5. Abstract/keywords in the preview flow? Interim: no — rows below.
6. Known quirk: a required-outside-vocabulary field (e.g. biblatex `url` for
   @online) is a trailing placeholder while unset but moves to its section once
   set. Acceptable? (Documented in CitationSegments javadoc.)

## Build / verify commands

```bash
./gradlew :jabgui:compileJava          # fast compile check
./gradlew :jabgui:run                  # manual smoke test (DISPLAY=:10.0)
./gradlew :jabgui:test --tests "org.jabref.gui.entryeditor.*"
./gradlew :jabgui:checkstyleMain :jabgui:checkstyleTest
```

PROCESS: commit after each step; push to github.com/JabRef/jabref-koppor and PR
there; keep PLAN.md + incremental commits; DISPLAY=:10.0 available for GUI/TestFX
(restart gradle daemon once so forked JVMs inherit it).

## Status log (update when stopping!)

- 2026-07-05: Plan written; architecture explored (see facts above). Concept #1
  merged upstream (#16166) mid-work → branch reset onto origin/main, PLAN.md kept;
  obsolete port/rename commits dropped.
- 2026-07-05: `CitationSegments.java` written (templates, slots, punctuation,
  trailing placeholders, coveredFields). NEXT: compile it, write
  `CitationSegmentsTest`, checkstyle, commit (= finish step 1).
