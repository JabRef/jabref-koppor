---
parent: Requirements
---
# Entry Editor

## Entry Editor should show the last entry
`req~entry-editor.keep-showing~1`

The Entry Editor should "always" show a valid entry.

When users search or select a group not containing the entry shown in the Entry Editor, the Entry Editor should keep showing until user select a new entry explicitly.

Needs: impl

## Citations tab should show citation preview on hover
`req~entry-editor.citations.hover-preview~1`

When the user hovers over a citation entry inside the Entry Editor's "Citations" tab, a tooltip containing the entry preview rendered in the current selected style should be displayed.

Needs: impl

## Main tab represents all fields of the entry
`req~entry-editor.main-tab.single-list~1`

The "Main" tab represents the citation key, all required fields of the entry type (even when unset), and every set field of the entry in a single vertically scrolling column: fields covered by the semantic preview are rendered (and edited) there, all remaining fields appear as editor rows with natural heights below the preview. Row order: required fields (entry-type order), set optional fields (important before secondary, each in entry-type order), remaining set fields sorted by name, then fields added by the user that are still empty.

Needs: impl

## Main tab renders the entry as a semantic preview
`req~entry-editor.main-tab.semantic-preview~1`

The top of the "Main" tab renders the entry as a citation-like text (authors, title, venue, volume(number):pages, publisher, year, …) driven by per-entry-type templates with a generic fallback; display text is LaTeX-free and author names render as "First Last and First Last". Required-but-unset fields appear as `{{Field}}` placeholders inside the text (required fields outside the template vocabulary as a trailing placeholder sentence); punctuation around empty slots is suppressed. A header line above shows "@type · citationkey"; clicking the type opens the change-entry-type menu.

Needs: impl

## Preview segments are edited in place
`req~entry-editor.main-tab.in-place-edit~1`

Clicking a field value or placeholder in the semantic preview (or the citation key in the header) opens the field's regular editor in an overlay row directly beneath the preview, with the clicked segment highlighted. Edits write through live (preview, main table, and preview panel follow the typing). Enter or moving the focus elsewhere closes the editor and keeps the value; Esc restores the value the field had when the editor was opened. Jump-to-field opens the in-place editor for preview-covered fields. While the editor is open, the tab does not rebuild its layout (no focus loss).

Needs: impl

## A field never appears twice on the Main tab
`req~entry-editor.main-tab.no-duplication~1`

A field represented in the semantic preview (as value or placeholder, including the citation key in the header) appears nowhere else on the tab: not as an editor row, not as an add-chip in the chip bar or any section. Unset alternatives of an already-satisfied required or-group (e.g. the author field of an editor-only book) get no empty editor row either; the free-form field-name box remains the escape hatch to add them explicitly.

Needs: impl

## Fields are grouped into collapsible sections
`req~entry-editor.main-tab.sections~1`

Identifier fields (DOI, ISBN, ISSN, eprint variants, PMID, MR number), file and link fields (file, URL, URI, urldate), bibliometrics fields (citation count, ICORE ranking), comment fields (comment plus user-specific comment fields), and meta fields (crossref, groups, owner, timestamps, special fields — data about the library entry rather than the paper) are shown in their own always-present, collapsible sections in this order after the main fields. A section is collapsed by default when it contains no shown field and expanded when it contains at least one; a manual expand/collapse by the user survives rebuilds until another entry is opened.

Needs: impl

## Unset optional fields are offered as one-click chips
`req~entry-editor.main-tab.add-chips~1`

The entry type's unset important-optional fields that belong to the main group are offered as one-click "+" chips directly below the main fields; a "Show more" toggle reveals chips for the unset secondary-optional fields ("Show less" hides them again). Clicking a chip shows an empty, focused editor for that field, removes the chip, and keeps the field visible — even while still empty — until another entry is opened.

Needs: impl

## Each section offers chips for its unset member fields
`req~entry-editor.main-tab.section-chips~1`

Every section offers "+" chips for its unset member fields: the identifiers section collects all identifier fields, the files and links and bibliometrics sections their respective fields, the comments section the general comment plus the current user's personal comment field (only when user-specific comment fields are enabled), and the meta section crossref, groups, owner, and the special fields (ranking, priority, read status, quality, relevance, printed). The automatically managed timestamp fields have no chip.

Needs: impl

## Arbitrary fields can be added via a field-name box
`req~entry-editor.main-tab.free-form-add~1`

Below the sections, an editable combo box pre-filled with all known field names plus an "Add" button (Enter works as well) adds an editor for any field name; unknown names create a custom field. Blank input is ignored.

Needs: impl

## The list refreshes on external field changes without disturbing typing
`req~entry-editor.main-tab.live-refresh~1`

When fields of the shown entry are set or unset outside the Main tab (source tab, fetchers, undo), the list updates to reflect the new field set. Typing inside a visible editor never rebuilds the list or steals focus; a visible field whose content is deleted stays visible until another entry is opened.

Needs: impl

<!-- markdownlint-disable-file MD022 -->
