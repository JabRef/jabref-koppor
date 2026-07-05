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

- [x] **1. `CitationSegments` + unit tests** → done 2026-07-05, commit c1f4c3890d;
  20 unit tests green (all listed checks), checkstyle clean.
- [x] **2. `SemanticPreviewFlow` read-only + tab integration** → done 2026-07-05,
  commit 10ee1a7414. Verified live (screenshots in build/screenshots/): complete
  article renders "John Doe and Jane Smith. Great Results in Testing. *Journal of
  Tests*, 12(3):45–67, 2020."; sparse article shows {{Journal}}/{{Year}}
  placeholders; thesis expands Files & links; editor-only book shows "(Eds.)" and
  NO empty Author row (unset alternatives of satisfied OrFields groups are
  suppressed; free-form add = escape hatch). Covered fields subtracted from rows,
  section chips, and the optional chip bar. entryeditor tests + checkstyle green.
- [x] **3. Click-to-edit** → done 2026-07-05, commit 5a80a596f4. Design: overlay
  editor row beneath the flow (NOT literal inline swap — editors are HBox
  compounds), segment highlighted (.semantic-preview-editing). Enter/focus-out
  close & keep; Esc restores pre-edit value; placeholder click starts empty.
  Verified live: title/year edits, live write-through into flow+table+preview,
  {{Year}}→2024, Esc XXX→restore. BONUS: found+fixed upstream bug (b5fd8d52c5)
  — WalkthroughKeyBindings consumed EVERY Esc app-wide at scene-filter level.
- [x] **4. Re-render policy** → done 2026-07-05 (same commit): rebuilds deferred
  while overlay open (flow text still follows typing); deferred shown/covered
  check on close; entry switch discards silently; chip clicks close first.
- [x] **5. Citation key + entry type header row** → done 2026-07-05, commit
  e843cb2332: "@type · citationkey" line above the flow; key click → overlay
  CitationKeyEditor (incl. Generate); type click → ChangeEntryTypeMenu popup;
  KEY_FIELD counts as covered (old grid row gone). Verified live incl. type
  change @article→@book (flow re-templates, chips update).
- [x] **6. Focus & navigation** → done 2026-07-05 (same commit):
  `requestFocus(Field)` override routes preview-covered fields to the in-place
  editor. JumpToField dialog live-check pending in step 7.
- [x] **7. Live verification** → done 2026-07-05: segments (article/book/thesis/
  inproceedings), placeholders, in-place edit (Enter/Esc/focus-out), live
  write-through, header key edit + generate button, type change with re-template,
  JumpToField→overlay (title) verified, entry switch state reset, chip add
  (+Month row focused, chip removed). Tests+LocalizationConsistencyTest+
  checkstyle+traceRequirements green. FINDING: Edit>Undo stays greyed for ALL
  entry-editor edits — also for plain upstream rows (verified with Abstract) →
  pre-existing upstream condition, not introduced by concept #2.
- [x] **8. Housekeeping** → done 2026-07-05: CHANGELOG (Added entry for the
  editable semantic preview; Fixed entry for the app-wide Esc bug → PR #13595
  ref), requirements doc extended (semantic-preview / in-place-edit /
  no-duplication reqs + single-list reworded), no new l10n keys needed
  ("Edit"/"Change entry type" existed), traceRequirements green, no dead code.
- [x] **9. Push to `koppor` remote + PR** → done 2026-07-05:
  **<https://github.com/JabRef/jabref-koppor/pull/735>** — screenshots embedded
  via orphan branch `koppor-images-in-place-edit`; body (incl. full CHECKLIST
  walkthrough) kept at `build/pr-body.md`. ALL STEPS DONE. Remaining: await
  review feedback on PR 735; open questions 1–4 documented in the PR body.

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
- 2026-07-05: Step 1 committed (c1f4c3890d). Step 2 committed (10ee1a7414), live
  smoke test done. INFRA notes: (a) /export ran 100% full mid-test → cleared
  ~/.gradle/caches/build-cache-1 (13G, regenerable); (b) csl-styles/csl-locales
  submodules had to be `git submodule update --init`-ed in this worktree, the app
  HANGS at startup without them ("Could not find citation style catalog");
  (c) GUI driving works via AWT-Robot helper (scratchpad/UiDriver.java — click/
  type/key/shot), no xdotool on the box; demo library at scratchpad/demo.bib.
- 2026-07-05: Step 3+4 design decided: NOT literal inline swap (JavaFX field
  editors are HBox compounds — they would wreck the TextFlow line layout);
  instead a shared editor overlay row directly beneath the flow, edited segment
  highlighted via css .semantic-preview-editing. Enter/focus-out = close & keep
  (write-through is live), Esc = restore pre-edit value. While an editor is open,
  rebuilds are DEFERRED (flow still re-renders live for value changes); the
  deferred target/covered check runs on close. Entry switch closes silently.
  Known polish item: Esc-restore bypasses the undo manager (typed edits stay in
  undo history) — revisit before release.
- 2026-07-05 (steps 3+4 done): In-place editing verified live end-to-end.
  DEBUGGING SAGA worth remembering: Esc never reached the overlay row —
  root cause was an UPSTREAM bug (WalkthroughKeyBindings consumes every Esc
  in the main scene even without active walkthrough; my row filter saw all
  keys except ESCAPE). Fixed in commit b5fd8d52c5 — candidate for a separate
  small PR to JabRef main! Second finding: the first Esc after typing may be
  eaten by the controlsfx autocompletion popup (its own window) — standard
  behavior, acceptable. Commits: b5fd8d52c5 (Esc fix), 5a80a596f4 (in-place
  editing). Tests + checkstyle green. NEXT: step 5 (citation key + type
  header row), step 6 (requestFocus routing).
