# Godot-style dock layout prototype

Goal: a JabRef main window laid out like the Godot editor: dock tabs on the left, the library
in the middle, the entry editor as a dock on the right. Flat "dock" tab styling and borderless,
filled checkboxes. Prototype on top of `main`, PR on jabref-koppor.

## Design (decided)

- **Left dock** = plain JavaFX `TabPane` in `SidePane`, one `Tab` per visible `SidePaneType`
  (icon graphic + title). Tabs are closable (close = remove from
  `StateManager.getVisibleSidePaneComponents()`) and drag-reorderable
  (`TabDragPolicy.REORDER`, reorder = update `SidePanePreferences.preferredPositions`).
  No library (DockFX & co. are unmaintained; a TabPane is all Godot does visually).
- The header row of `SidePaneComponent` (title label + up/down/close buttons) goes away; the
  groups toolbar buttons (filter / invert / union-intersection) move into the tab content top.
- **Right dock** = `EntryEditor` as the third item of the horizontal `SplitPane` in
  `JabRefFrame` (`[sidePane | libraries | entryEditor]`); the vertical split is dropped.
  Divider positions: left divider keeps `MAIN_WINDOW_SIDEPANE_WIDTH`; the editor divider
  reuses `MAIN_WINDOW_EDITOR_HEIGHT` (renamed getter to "editor divider position", same key,
  value is a ratio either way — default changed to ~0.6).
- **Theme**: style class `dock` on the dock `TabPane`s (left side pane, entry editor tabs):
  flat tab strip, selected tab merges with the content background, no borders; checkboxes:
  filled `-color-bg-tertiary` box, no border, accent when selected. Rules live in
  `jabref-theme.css` (JabRef theme) + token-only rules in `internal/jabref-base.css`.

## Steps

- [x] 1. PLAN.md committed + branch pushed to koppor
- [x] 2. SidePane → TabPane of docks (SidePane, SidePaneViewModel, SidePaneComponent,
        GroupsSidePaneComponent; SidePaneViewModelTest adapted)
- [x] 3. Entry editor to the right (JabRefFrame, CoreGuiPreferences, JabRefGUI)
- [x] 4. CSS: `.dock` tab styling + borderless checkboxes
- [ ] 5. Compile + `:jabgui:test --tests '*SidePane*'` + Xvfb screenshot
- [ ] 6. Draft PR on JabRef/jabref-koppor with screenshot
- [ ] 7. Follow-ups (not in this PR): layout preference classic/docked, closable editor dock
        via View menu, remember selected dock tab

## Notes / state

- 2026-09-10: steps 1-4 done, `:jabgui:test --tests 'org.jabref.gui.sidepane.*'`, checkstyle and
  LocalizationConsistencyTest green. l10n keys "Hide panel"/"Move panel up/down" removed (unused).
- Dock CSS lives in `internal/jabref-base.css` (token-only, applies to every theme); the checkbox
  restyle only in `jabref-theme.css` (theme decision). Entry editor tabs get the `dock` class in
  `EntryEditor.fxml`; their selected tab merges with `-color-bg-secondary`, side pane docks with
  `-color-bg-sidepane`.
- Walkthrough still finds the groups pane: id `groups-side-pane` sits on the tab content node.
