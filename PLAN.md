# Plan: Entry editor "Main" tab as an editable semantic preview (in-place editing)

Issue: <https://github.com/JabRef/jabref/issues/12711>
Based on: <https://github.com/JabRef/jabref-koppor/pull/731> (concept #1: single scrollable
field list; checked out at `../jabref`, branch `new-entry-editor`) — but this branch starts
fresh on `main` and explores concept #2: **in-place editing inside a semantic preview**.

Branch: `new-entry-editor-in-place-edit` (worktree `jabref-worktree-1`, based on `main`).

**2026-07-05: concept #1 was MERGED into origin/main as PR #16166 ("New entry
editor")** — AllFieldsTab, FieldListSections, FieldsEditorTab hooks, classic-tab
sunset are all upstream now (byte-identical to `../jabref` except a trivial `==`
in FieldListSections). This branch therefore *evolves the merged AllFieldsTab*
instead of porting anything. Note: main also got #16161 (JabRefGuiPreferences →
PreferenceBindings), so prefs plumbing details in older notes may be stale.

## Target UX (concept #2, Oliver 2026-07-05)

- **Tab lineup as in PR #731**: one field tab **"Main"** replaces the classic category tabs
  (Required/Optional/Optional2/Deprecated/Other/Comments); feature tabs unchanged.
- **Tab "Main" shows a *semantic preview*** of the entry (citation-like rendering:
  authors, title, venue, volume/pages, year, …) instead of a key–value grid.
- **In-place editing**: clicking a field's text inside the preview swaps that segment for
  the real field editor (`FieldEditorFX`); commit on Enter / focus loss, cancel on Esc.
- **Required-but-unset fields render as `{{Field}}` placeholders** inside the preview,
  clickable and editable the same way.
- **Buttons (chips) below the preview** for adding fields.
- **Fields not represented in the preview** are added/shown **grouped as in #731**
  (collapsible sections: Identifiers / Files & links / Bibliometrics / Comments / Meta,
  each with add-chips; free-form add row at the bottom).
- **No field duplication**: a field rendered in the preview never appears again in the
  groups below — *the preview takes precedence*; the grouped part is only the fallback
  for fields the semantic template does not cover (goal: reduce cognitive load by never
  showing the same information twice).

## Architecture (facts established 2026-07-05 on main)

- The existing preview is **HTML in a WebView**: `PreviewLayout.generatePreview(...)`
  returns a flat HTML `String` rendered via `WebEngine.loadContent` (PreviewViewer).
  No structure survives → **not editable**. The semantic preview must be a new
  **node-based JavaFX component** (TextFlow with per-field segment nodes) driven
  directly by `BibEntry` (+ `AuthorList.parse(...)` for structured author display).
- **Field editors bind two-way automatically**: `FieldEditors.getForField(field, …)`
  → `editor.bindToEntry(entry)`; typing calls `entry.setField` (with undo edit),
  external `setField` updates the control (caret-preserving). An inline-swapped editor
  therefore needs no extra commit logic for the value itself.
- `FieldsEditorTab` hooks from #731 (`layoutEditors(...)`, `stretchContentToTabHeight()`,
  `getEditorContent()`) let a subclass fully own the layout while inheriting editor
  creation (`createLabelAndEditor` → `editors` map), preview-SplitPane, focus
  (`requestFocus(Field)`), and content-driven visibility.
- Portable #731 substrate (from `../jabref`, diff vs merge-base 36f4faafcb):
  `EntryEditorTabModel` (BuiltIn.ALL_FIELDS "Main", classic tabs + customized-tab model
  deleted), `EntryEditorTabFactory`, `EntryEditorPreferences`/`JabRefGuiPreferences`
  (pref key `showAllFieldsTab`, SHOW_* keys of deleted tabs dropped, 2 obsolete
  migrations removed), `FieldListSections` (+test), `FieldsEditorTab` hooks,
  `AllFieldsTab` (will be reshaped), jabref-theme.css, l10n keys, prefs UI reduction,
  CHANGELOG, `docs/requirements/entry-editor.md`.
- Entry event bus (`entry.registerListener` + `@Subscribe FieldChangedEvent`) is the
  live-refresh mechanism (see #731 `AllFieldsTab.listen`).
- LaTeX → display text: use `LatexToUnicodeAdapter`/`LatexToUnicodeFormatter` (check
  exact class at impl time) for segment text rendering.

## Design: semantic preview component

1. **`CitationSegments` (plain Java, unit-testable, no JavaFX)** — turns
   (BibEntryType, BibEntry) into an ordered list of render tokens:
   `TextToken(text)` | `FieldToken(field, displayText, style)` |
   `PlaceholderToken(field)` (required + unset).
   Per-type templates (article, book, inproceedings/incollection, thesis, misc fallback)
   over a canonical vocabulary: author/editor, title, journal/booktitle, volume, number,
   pages, publisher, school/institution, edition, year/date, …; punctuation as
   TextTokens between them, emitted only when both neighbors render.
   Required fields of the type not in the template vocabulary → appended as trailing
   placeholders. Exposes `coveredFields()` = fields rendered (set-in-vocabulary ∪
   required) — the "no duplication" source of truth for the tab.
2. **`SemanticPreviewFlow` (JavaFX)** — TextFlow rendering the tokens; FieldTokens and
   PlaceholderTokens are clickable (hover underline/highlight); click swaps the segment
   for the bound field editor node **inline** (TextFlow accepts arbitrary Nodes);
   Esc/Enter/focus-out swaps back to text. Multiline/heavy editors (weight > 1, e.g.
   file) are not in the template vocabulary, so inline stays single-line-friendly.
   While an inline editor is open, flow re-rendering is suppressed (value write-through
   is live anyway); re-render happens on editor close and on external field changes.
   (Fallback if inline swap fights TextFlow layout: editor row directly beneath the
   flow with the edited segment highlighted — decide during step 4.)
3. **Main tab layout** (single scrolling column, natural heights, as #731):
   citation key row (entry type + key, editable) → semantic preview flow →
   add-chip bar (unset important-optional fields *not* covered by the preview
   vocabulary; "Show more" for secondary) → "More fields" rows (set MAIN-section
   fields not covered by preview: abstract, keywords, custom fields, …) →
   collapsible sections (Identifiers / Files & links / Bibliometrics / Comments /
   Meta) with their chips → free-form add row.
   Preview-covered fields are subtracted from every chip list and row list.

## Steps (check off as done; commit after each step)

### Phase 0 — Baseline

- [x] **1. Baseline = origin/main.** ~~Port #731 diff~~ OBSOLETE: concept #1 merged
  upstream as #16166. Branch rebased onto origin/main (6c67e6f99b); only the
  PLAN.md commit kept. The earlier port commit (ebf393e0c8) and the
  AllFieldsTab→MainTab rename (092f8bab66) were dropped — **class keeps the
  upstream name `AllFieldsTab`** for a minimal PR diff (open question 3 closed).

### Phase 1 — Semantic preview, read-only

- [ ] **2. `CitationSegments`**: token model + per-type templates + fallback +
  `coveredFields()`. LaTeX-to-unicode + author display via `AuthorList`.
  *Check: unit tests (article/book/inproceedings/misc; placeholder emission;
  punctuation suppression; coveredFields).*
- [ ] **3. `SemanticPreviewFlow` read-only + tab integration**: render tokens in a
  TextFlow (CSS: value vs placeholder vs punctuation); mount at top of the Main tab
  (replacing the #731 main grid); subtract covered fields from all chip lists and
  row lists ("More fields" rows for uncovered set MAIN fields).
  *Check: compile; GUI smoke test on DISPLAY=:10.0 with screenshots.*

### Phase 2 — In-place editing

- [ ] **4. Click-to-edit**: segment click swaps in the bound editor inline; Enter /
  focus-out closes (value already written through); Esc restores the pre-edit value
  (single undoable change semantics — check UndoableFieldChange behavior).
  Placeholder click = same flow starting empty.
- [ ] **5. Re-render policy**: entry event bus subscription; suppress re-render while
  an inline editor is open; re-render on close + on external changes (Source tab,
  fetchers, undo); entry switch resets state (cf. #731 `userAddedFields` pattern).
  *Check for 4+5: type in inline editor → value lands in entry (Source tab);
  external edit re-renders; no focus loss mid-typing.*

### Phase 3 — Integration & polish

- [ ] **6. Citation key row**: entry type + citation key line above the preview,
  click-to-edit (CitationKeyEditor with generate button as the swap-in editor).
- [ ] **7. Focus & navigation**: `requestFocus(Field)` (JumpToField, focus key binding)
  must open/focus the right inline editor or section editor; Tab order sensible.
- [ ] **8. Live verification** on DISPLAY=:10.0: full manual pass (edit each segment
  type, placeholders, chips, sections, free-form add, undo/redo, entry switch,
  entry-type change), screenshots for the PR; entryeditor tests +
  LocalizationConsistencyTest + checkstyle.
- [ ] **9. Housekeeping**: CHANGELOG entry (reword for concept #2), l10n keys
  complete, requirements doc updated for the new concept, dead code check.

### Phase 4 — Ship for internal inspection

- [ ] **10. Push branch to `koppor` remote** (`github.com/JabRef/jabref-koppor`) and
  **open PR there** (design may be controversial → internal first, like #731).
  Keep PLAN.md and all incremental commits (no squash).

## Open questions (ask Oliver / decide before the relevant step)

1. Does the classic side-by-side PreviewPanel stay visible on the Main tab?
   (Semantic preview + CSL preview side by side = duplication; but the CSL preview
   is the *real* citation style. Interim: keep, user can hide via divider.)
2. Semantic preview style: one fixed hand-rolled template (interim: yes) or derived
   from the user's selected preview/CSL style (hard: CSL output is opaque)?
3. Rename `AllFieldsTab` → `MainTab`? → **RESOLVED: no** — the class landed in
   main via #16166 under that name; keep it, reshape in place (minimal diff).
4. Citation key + entry type rendering: header line "@article{key," style or plain
   row? (Interim: header line with both parts clickable.)
5. Abstract/keywords in the preview flow? (Interim: no — they stay as rows below;
   citation previews don't show them, and long text breaks the flow.)

## Build / verify commands

```bash
./gradlew :jabgui:compileJava          # fast compile check
./gradlew :jabgui:run                  # manual smoke test (DISPLAY=:10.0)
./gradlew :jabgui:test --tests "org.jabref.gui.entryeditor.*"
./gradlew :jabgui:checkstyleMain
```

PROCESS (from #731, confirmed): commit after each step; push to
`github.com/JabRef/jabref-koppor` and PR there; keep PLAN.md + incremental commits;
DISPLAY=:10.0 available for GUI runs/TestFX (restart gradle daemon once so forked
JVMs inherit it).

## Status log (update when stopping!)

- 2026-07-05: Plan written. Architecture explored (preview = WebView HTML, not
  editable → new TextFlow component; field editors two-way bind automatically;
  #731 substrate portable). Design for `CitationSegments` / `SemanticPreviewFlow`
  fixed (see Design section). No implementation steps done yet.
- 2026-07-05: Oliver: concept #1 merged into origin/main (#16166) → branch reset
  onto origin/main + PLAN.md cherry-picked; port/rename commits dropped as
  obsolete. Baseline verified upstream-identical to the explored `../jabref`
  state. Next: step 2 (`CitationSegments`).
