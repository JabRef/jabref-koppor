---
parent: Requirements
---
# Import

## Normalize imported BibTeX keyword delimiters
`req~import.bibtex.keywords.normalize-delimiters~1`

When importing BibTeX entries, JabRef applies the "Normalize keyword delimiters" cleanup (see `req~save.keywords.normalize-delimiters~1`) to every imported entry, so groups, search, and the keyword editor split the field on the library's separator from the start.
The library's separator is the one declared in the library's metadata; if none is declared, it is the accepted delimiter that the library's keyword fields already use most; if the keyword fields contain no delimiter, it is the globally configured keyword separator.
Keyword fields that already use the library's separator are left untouched, so opening a library does not rewrite them.

Delimiter characters that are part of a keyword remain part of that keyword and are escaped when necessary.

Needs: impl, utest

## Imported entries stay locatable in the library
`req~import.entries.sorted-by-id~1`

Entries are kept in the library in the order of their internal ids, regardless of the order in which a batch of imported entries arrives (e.g. after per-entry background duplicate checks). Looking up an entry's position in the library therefore succeeds for every imported entry, so the main table can select and update it.

Needs: impl, utest

## Static group memberships in the pre-3.4 format are warned about
`req~import.library.legacy-group-memberships-warned~1`

Libraries written by JabRef before 3.4 list the members of a static group inside the group instead of in the entries.
JabRef does not convert this format anymore, so these groups show up empty and saving the library removes the memberships.
When such a library is opened, JabRef warns about the loss and recommends a backup and opening and saving the library once with JabRef 5.15, which converts the memberships to the current format.

Needs: impl, utest

## Unresolved merge conflict markers abort the import
`req~import.bibtex.merge-conflict-markers~1`

A BibTeX file that still contains version control conflict markers is rejected with an error naming the line of the first marker, instead of importing an arbitrary side of the conflict or storing the markers inside an entry.
A marker is a line starting with at least seven `<` or `>` characters.
The `=======` and `|||||||` lines of a conflict are not looked for on their own: they always follow a `<<<<<<<` line, and such lines also occur as decorative rules in field values.

Needs: impl, utest

## A library that cannot be read is reported and leaves no tab behind
`req~import.library.unreadable-reported~1`

When a library file cannot be read or parsed at all, JabRef names the file and the reason it failed, instead of failing silently or only logging it.

No library tab is left behind for such a file. The tab that was opened to hold the loading library would otherwise stay as an empty, untitled library, which the user could save over the file that had just failed to load.

A file that parses with warnings is not affected: it still opens, and its warnings are reported separately.

Needs: impl, utest

## Legacy libraries are migrated when opened
`req~import.bibtex.legacy-migrations~1`

Opening a library written by JabRef 2.x/3.x converts its legacy content to the current representation: explicit group memberships stored inside the group tree move to the entries' `groups` field, `__markedentry` markings become groups, and special field values stored in `keywords` move to their own fields.
The keyword separator used for splitting is the library's own, falling back to the configured one.
## Custom entry types of a library are only offered once
`req~import.entry-types.offered-once~1`

When a library declares entry types that differ from the ones stored in the preferences, JabRef offers to store them. This holds both for entry types JabRef does not know and for customizations of entry types JabRef ships.

The definition the user accepts is the one from the library file, and it replaces the stored one. Entry types the user leaves unchecked when confirming the dialog are remembered as declined. Opening the same library again therefore offers nothing, unless the definition in the library or the stored one has changed since. Cancelling the dialog decides nothing, so the entry types are offered again.
## PDF import keeps only authors the document prints
`req~import.pdf.author-confirmed-by-text~1`

When importing a PDF, an author taken from the PDF's document properties is kept only if the text of the leading pages confirms it; otherwise an author list extracted from the document text replaces it.
If no candidate is confirmed, a single unconfirmed person from the document properties is dropped, because office suites store the account name of whoever exported the file there.
An author from bibliographic metadata (an entry with citation key or a known entry type, such as metadata previously written by JabRef or fetched online) is kept even when the text does not confirm it.
If no usable text can be extracted from the leading pages, the author is left unchanged.

Needs: impl, utest

## PDF import extracts only plausible years
`req~import.pdf.plausible-year~1`

When extracting the year from the text of a PDF's first page, JabRef takes only a standalone four-digit number, not attached to letters and not part of a four-digit range (such as a page range), between 1900 and two years after the current year, so postal codes, ISSNs, and page ranges are not imported as the year.
## Rule-based reference extraction reads the biblatex standard styles
`req~import.pdf.references.biblatex~1`
## Rule-based reference extraction reads labelled BibTeX reference lists
`req~import.pdf.references.labelled~1`

The rule-based extraction splits a PDF's reference list into references at their labels, as typeset by BibTeX and biblatex styles: numeric (`[12]`) or alphabetic (`[AL26]`, `[Buc+23]`, `[BSG+ 23]`).
Each entry carries the printed reference in `comment`.

Fields are read for two layouts: IEEE (`J. Knaster et al., “Title”, Nucl. Fusion, vol. 57, …`) and the biblatex standard styles (`Authors. “Title”. In: …`).
There, field values such as the DOI are kept as printed, so faults of the typeset list stay visible.

Reference lists without labels, such as those of author-year styles, are not supported.

Needs: impl, utest

<!-- markdownlint-disable-file MD022 -->
