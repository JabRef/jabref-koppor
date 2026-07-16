---
parent: Requirements
---
# Search backend

This page collects the requirements on the technology that backs JabRef's search within a library ("search backend"), including the fulltext search of linked PDF files.
"Search backend" here means the **local, single-user** index or DBMS that JabRef creates and manages automatically on the user's machine — not the remote shared SQL database used for group work ([#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3688370074)).

The requirements are phrased backend-neutrally: they must hold for any candidate technology (embedded PostgreSQL, SQLite (FTS5), Lucene, an in-memory grammar walk, ...) and do not presuppose a particular DBMS.
Currently, JabRef ships a GoF bridge (`SearchBackend`) with three implementations: `SqlSearchBackend` (embedded PostgreSQL for bib fields plus Lucene for linked-file fulltext), `InMemorySearchBackend` (default since PR [#15599](https://github.com/JabRef/jabref/pull/15599)), and `NoOpSearchBackend`.

User-facing requirements on the search syntax itself are collected in [Search within a library](search-within-library.md).

## Requirements sources

- Issue [#12708](https://github.com/JabRef/jabref/issues/12708) — the main discussion on which DBMS/index technology should back JabRef's search (and library handling in general).
- PR [#11803](https://github.com/JabRef/jabref/pull/11803) — replaced the Lucene-based bib-field search with an embedded PostgreSQL server; its review threads and the issues it closes encode many behavioral requirements.
- PR [#15385](https://github.com/JabRef/jabref/pull/15385) — JMH benchmark harness for performance measurements of the search backends.
- Issue [#12261](https://github.com/JabRef/jabref/issues/12261) — migration of fulltext (PDF) search away from Lucene; documents the "contains" requirement that troubled Lucene.

Additional evidence is taken from linked issues (e.g., [#10209](https://github.com/JabRef/jabref/issues/10209), [#10490](https://github.com/JabRef/jabref/issues/10490), [#11798](https://github.com/JabRef/jabref/issues/11798), [#11823](https://github.com/JabRef/jabref/issues/11823), [#12190](https://github.com/JabRef/jabref/issues/12190), [#12844](https://github.com/JabRef/jabref/issues/12844), [#14569](https://github.com/JabRef/jabref/issues/14569), [#15111](https://github.com/JabRef/jabref/issues/15111)) and from the current implementation in `jablib`.

## Functional search capabilities

### JabRef query grammar with backend-independent semantics
`req~search.backend.query-grammar~1`

JabRef must expose its own boolean query grammar to users — `AND`/`OR`/`NOT`, parentheses, fielded terms, the operators `=` (contains), `==` (exact), `=~` (regex), their negated and case-sensitive variants (`=!`, `==!`, `=~!`), empty-value tests, and the pseudo-fields `any`/`anyfield` (synonyms), `key`, `anykeyword`, and `entrytype` — and every backend must implement this grammar with identical semantics.
Engine-native query syntax (e.g., Lucene classic syntax) must not be exposed to users.
Space-separated terms must be conjunctive (implicit `AND`), not `OR`, and must parse without exceptions.
Pseudo-field patterns such as `groups != .+` (entries in no group) and `readstatus != .+` must keep working.
Acceptance: the restored v5.x search test suites (`GrammarBasedSearchRuleTest`, `SearchQueryTest`) must pass against every backend.

> Currently, three parallel compilers of the same `Search.g4` ANTLR tree exist (`SearchToSqlVisitor`, `SearchToLuceneVisitor`, `BibEntryMatchVisitor`); no implementation is linked yet.
{: .prompt-note}

Issue: [#11823 (comment)](https://github.com/JabRef/jabref/issues/11823#issuecomment-2385484672), [#11823 (comment)](https://github.com/JabRef/jabref/issues/11823#issuecomment-2373773200)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2415746521), [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1798006641)

### Deterministic substring ("contains") matching
`req~search.backend.substring-search~1`

JabRef must perform deterministic substring matching by default, over both bib fields and (see below) linked-file fulltext: a term must match anywhere inside a field value, regardless of word boundaries or tokenization.
Examples: the query `xyz AND abc` must find an entry containing the single string `xyz-abc`; the query `dorf` must find `Düsseldorf`.
The backend must not require the query to be tokenized identically to the index (the property that made "contains" search hard in Lucene).
Users expect deterministic "contains" results, not relevance-style token-matching surprises.

> The current SQL backend fulfils this for bib fields via a pg_trgm GIN index and `LIKE`/`ILIKE '%term%'` queries; no implementation is linked yet.
{: .prompt-note}

Issue: [#11823](https://github.com/JabRef/jabref/issues/11823), [#11823 (comment)](https://github.com/JabRef/jabref/issues/11823#issuecomment-2386722728), [#12261](https://github.com/JabRef/jabref/issues/12261), [#14569](https://github.com/JabRef/jabref/issues/14569)

### Literal search terms without user-side escaping
`req~search.backend.literal-special-characters~1`

JabRef must let users paste any value — including citation keys with syntax metacharacters such as `Law:2009The`, or group names like `:group/subgroup` containing `:` and `/` — into the search field and find the entry without manual escaping or quoting.
Consequently, user input must be passed to the backend as literal data (escaped or parameterized by JabRef's own grammar layer) and must never be interpreted by the backend's native query parser (Lucene misparsed `Law:2009The` as field query `law:2009The`).

Issue: [#11798](https://github.com/JabRef/jabref/issues/11798), [#11798 (comment)](https://github.com/JabRef/jabref/issues/11798#issuecomment-2363050336), [#11798 (comment)](https://github.com/JabRef/jabref/issues/11798#issuecomment-2363177185), [#11798 (comment)](https://github.com/JabRef/jabref/issues/11798#issuecomment-2363187155)

### Case-insensitive and case-sensitive matching
`req~search.backend.case-sensitivity~1`

JabRef must match case-insensitively by default and must support case-sensitive matching via the `=!`/`==!` operators (the case-sensitivity toggle affects unfielded terms only).
Case-sensitive operators must be honored exactly: `any ==! SEE` must not return entries containing only `See`.
Case-sensitive matching should also be available for linked-file fulltext search.

> The Lucene-based fulltext index cannot deliver case-sensitive search; this is a known gap of the current implementation.
{: .prompt-note}

Issue: [#13048](https://github.com/JabRef/jabref/issues/13048)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1802476377)

### Regular-expression search resolved by the backend
`req~search.backend.regex-search~1`

JabRef must support regular-expression matching via the `=~` operator (case-sensitive and case-insensitive), on any single field and across all fields.
Regex evaluation must be done by the backend (ideally index-supported), not by client-side filtering of all entries — fast regex search was a primary recorded reason for the Lucene-to-Postgres switch.
Regex capability must remain available; users describe it as "priceless".

> Currently fulfilled by the SQL operators `~`/`~*` in `SearchToSqlVisitor` and by Caffeine-cached `java.util.regex` patterns in the in-memory backend; no implementation is linked yet.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2717449913), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3949776169), [#11823 (comment)](https://github.com/JabRef/jabref/issues/11823#issuecomment-2386722728)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1797977534)

### Normalization of diacritics, LaTeX, and transliteration variants
`req~search.backend.term-normalization~1`

JabRef must match a query term against a field value after applying the **same normalization to both**, so that orthographic, diacritic, LaTeX-encoded, and German-transliteration variants of a term are treated as equivalent.
Define a folding function `F(x)`, applied identically at index time and at query time:

1. Unicode NFKC normalization and case-folding (default search is case-insensitive).
2. LaTeX-to-Unicode decoding, so `D\"{u}sseldorf` becomes `Düsseldorf` and a LaTeX-encoded query term never reaches — or crashes — the backend's query parser.
3. Diacritic folding (`ü`→`u`, `é`→`e`, …), which tokenizer-level options already provide (e.g., FTS5 `remove_diacritics`, PostgreSQL `unaccent`, Lucene `ICUFoldingFilter`).
4. A German/transliteration fold map for the digraph and ligature cases diacritic folding does **not** cover: `ü`↔`ue`, `ö`↔`oe`, `ä`↔`ae`, `ß`↔`ss`.

Default matching is then **substring over the folded form**: a query term `t` matches a value `v` iff `F(t)` is a substring of `F(v)`.
This deliberately composes term normalization with `req~search.backend.substring-search~1` — a substring of the *folded* value must match, not only a substring of the literal value.

Acceptance (each must hold, in both directions):

- `dorf` and `sseldorf` find `Düsseldorf` (substring of the literal/Unicode value).
- `dusseldorf`, `duesseldorf`, and `D\"{u}sseldorf` each find `Düsseldorf`, and all four spellings match each other.
- `muller`, `mueller`, and `Müller` match each other; `strasse` finds `Straße`.
- `Kepes` finds `Képes` (fixes [#12685](https://github.com/JabRef/jabref/issues/12685)).

Steps 1–3 are satisfiable at the backend/tokenizer level on every candidate; step 4 (`ue`↔`ü`, `ß`↔`ss`) has no native backend support and must be implemented as application-level folding (e.g., ASCII/German folding equivalent to Lucene's `ASCIIFoldingFilter` from the former `LatexAwareAnalyzer`), applied identically to indexed values and query terms.

> Status: the substring and diacritic axes (steps 1–3) are required and achievable today (an empirical check confirms FTS5 `remove_diacritics` makes `dusseldorf`, `muller`, and `kepes` match their umlaut/accent forms); the German-transliteration axis (step 4, `ue`↔`ü`, `ß`↔`ss`) is required but **not yet achieved** on any backend — the current SQL backend indexes `field_value_literal` and a LaTeX-free `field_value_transformed` column and queries both, which covers the LaTeX axis but not `ue`↔`ü`. No implementation is linked yet.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764437282), [#12261](https://github.com/JabRef/jabref/issues/12261), [#12685](https://github.com/JabRef/jabref/issues/12685), [#9605 (comment)](https://github.com/JabRef/jabref/issues/9605#issuecomment-2361295595)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1802487232)

### Normalization of person names
`req~search.backend.name-normalization~1`

JabRef should normalize person names during indexing so that variant forms of the same name match each other.

> This is an agreed direction without detailed acceptance criteria yet; no implementation is linked.
> The source states only "normalization of names" (alongside term and transliteration normalization) without examples; matching "Doe, John" vs. "John Doe" or abbreviated vs. full first names is one possible interpretation, not a confirmed scope.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764528869)

### Non-Latin scripts and opt-in romanization
`req~search.backend.non-latin-scripts~1`

JabRef must correctly store, index, and exactly match non-ASCII/CJK text in all bib fields and in citation keys (e.g., key `万征2016`, author `万征`, journal `力学学报`); Chinese characters must be searchable as such.
JabRef must never apply automatic Hanzi-to-Latin folding blindly, because identical characters romanize differently across Chinese, Japanese, Vietnamese, and Korean (`出来` → "chu lai" / "de ki" / "xuý lãi" / "cheok rae"); any romanization must be an opt-in preference (default off) with a user-selectable scheme, extensible to other scripts (Japanese, Korean, Cyrillic, Greek, ...).
Language-dependent normalization must be resolvable per entry (e.g., via the BibTeX `language` field), not per library or per OS locale, since one library can mix languages.
Name-part splitting for CJK names must rely on user-provided separators, not on backend-side heuristics (compound surnames such as `诸葛尚如` vs. `朱军` collide under pinyin).
Pinyin search support is an accepted follow-up; transliteration can be implemented backend-independently in Java (e.g., maintained pinyin libraries).

Issue: [#9605](https://github.com/JabRef/jabref/issues/9605), [#9605 (comment)](https://github.com/JabRef/jabref/issues/9605#issuecomment-1879661317), [#9605 (comment)](https://github.com/JabRef/jabref/issues/9605#issuecomment-2029426564), [#9605 (comment)](https://github.com/JabRef/jabref/issues/9605#issuecomment-2029582686), [#9605 (comment)](https://github.com/JabRef/jabref/issues/9605#issuecomment-3502590961)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2415836048)

### Fuzzy search
`req~search.backend.fuzzy-search~1`

JabRef should support fuzzy matching that tolerates typos, e.g., a misspelled journal title should still find the intended entries.

> Aspirational: Lucene's fuzzy "uncertain matches" were consciously given up in PR #11803 in exchange for working exact matching; restoring fuzzy search is a noted, desired improvement, not yet committed.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3949776169)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1788362730)

### Anchored prefix and position-aware matching
`req~search.backend.prefix-and-position~1`

JabRef must support anchored matching: "field begins with ..." on any field, and position-aware matching within multi-valued fields, e.g., matching only the *first* author of the author list.
A simple shorthand (ADS-style `^name` for first-author search) should be expressible in the query syntax, which implies the backend query layer must allow such operators to compile into efficient queries.
The corresponding syntax-level requirements are `req~jabgui.search.syntax.author-first-name~1` and `req~jabgui.search.syntax.citation-key~1` in [Search within a library](search-within-library.md); both are still unimplemented.

Issue: [#10490](https://github.com/JabRef/jabref/issues/10490), [#10490 (comment)](https://github.com/JabRef/jabref/issues/10490#issuecomment-1774156027), [#10490 (comment)](https://github.com/JabRef/jabref/issues/10490#issuecomment-2345176632)

### Exact matching of individual values in multi-value fields
`req~search.backend.multi-value-fields~1`

JabRef must match exact-match queries on multi-value fields (authors, keywords, groups, entry links) against the individual values, which requires the index to store split/normalized values per field.
A search term must match an occurrence in *any* keyword of the keywords field, not only the first keyword in the list.

> The current SQL backend fulfils this via a separate `<table>_split_values` table populated using `AuthorList`/`KeywordList`/groups splitting; no implementation is linked yet.
{: .prompt-note}

Issue: [#11823 (comment)](https://github.com/JabRef/jabref/issues/11823#issuecomment-2373631859)
PR: [#11803](https://github.com/JabRef/jabref/pull/11803)

### Scope of unfielded ("any") search
`req~search.backend.any-field-scope~1`

JabRef must cover all entry fields in unfielded searches, including the keywords field, with one exception: the `groups` field must be excluded from "any contains" queries to avoid false positives from group-membership metadata ([#7996](https://github.com/JabRef/jabref/issues/7996)).

> Currently fulfilled by the SQL clause `main_table.field_name != 'groups'`, repeated in the query builders; no implementation is linked yet.
{: .prompt-note}

Issue: [#11823](https://github.com/JabRef/jabref/issues/11823), [#7996](https://github.com/JabRef/jabref/issues/7996)
PR: [#11542](https://github.com/JabRef/jabref/pull/11542)

### Indexing of derived and resolved values
`req~search.backend.derived-values~1`

JabRef must resolve and index derived values, not only raw field text: date-related fields (`date`, `year`, `month`, `day`), values resolved via crossref/aliases, and the entry type must all be queryable.

> Currently fulfilled by `BibFieldsIndexer` using `getResolvedFieldOrAlias` for date fields and the type header, and `getResolvedFieldOrAliasLatexFree` otherwise; no implementation is linked yet.
{: .prompt-note}

PR: [#11803](https://github.com/JabRef/jabref/pull/11803)

### Fulltext search of linked files
`req~search.backend.fulltext-search~1`

JabRef must search the text content *and* the annotations of linked local PDF files, page-wise, returning page numbers and highlighted snippets per match.
Fulltext queries are addressed via a `SearchQuery` carrying `SearchFlags.FULLTEXT`; only the pseudo-fields `content` and `annotations` are searchable in linked files, and bib-field and fulltext indexes must be queryable jointly in one search.
Acceptance test: open a bib file with linked PDFs, run a fulltext search — terms occurring in the PDF text must produce hits.

> Currently fulfilled by `DocumentReader` (one document per PDF page) and `LinkedFilesSearcher` (page number plus highlighter snippets); no implementation is linked yet.
{: .prompt-note}

Issue: [#12261](https://github.com/JabRef/jabref/issues/12261)
PR: [#8362 (comment)](https://github.com/JabRef/jabref/pull/8362#issuecomment-1001479584), [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1802447479), [#15385](https://github.com/JabRef/jabref/pull/15385/files)

### Substring and regex semantics for fulltext, consistent with field search
`req~search.backend.fulltext-substring-regex~1`

JabRef must apply the same default substring semantics to linked-file fulltext search as to bib-field search: searching `dorf` must return the entry whose attached PDF contains `Düsseldorf`, including Unicode substrings (`üsseldorf` must match `Düsseldorf`), without requiring users to type wildcards only for PDFs.
The fulltext engine must also support regular-expression matching.
The maintainers ruled out implementing this with Lucene wildcards (leading/double wildcards presumed too slow as default); as an interim escape hatch, user-typed wildcards (`*`, `?`, including leading ones) are passed through unescaped (PR [#15241](https://github.com/JabRef/jabref/pull/15241)).

> The current Lucene fulltext index additionally applies English Porter stemming (computer/compute/computations → "comput"); whether stemming is kept alongside substring semantics is undecided.
{: .prompt-note}

Issue: [#14569](https://github.com/JabRef/jabref/issues/14569), [#14569 (comment)](https://github.com/JabRef/jabref/issues/14569#issuecomment-3972802650), [#12261](https://github.com/JabRef/jabref/issues/12261)
PR: [#14574 (discussion)](https://github.com/JabRef/jabref/pull/14574#discussion_r2617521386), [#14574 (discussion)](https://github.com/JabRef/jabref/pull/14574#discussion_r2617240776), [#15241](https://github.com/JabRef/jabref/pull/15241)

### Fulltext indexing must be optional and toggleable
`req~search.backend.fulltext-optional~1`

JabRef must gate fulltext indexing of linked files behind a user preference (`FilePreferences.shouldFulltextIndexLinkedFiles`) and must react to toggling at runtime: disabling removes the fulltext index, enabling triggers re-indexing, and queries consult the fulltext index only when the `FULLTEXT` flag is set.
An opt-out is mandatory because default-on fulltext indexing has made low-end machines unusable.

Issue: [#9491 (comment)](https://github.com/JabRef/jabref/issues/9491#issuecomment-1371476088)
PR: [#15385](https://github.com/JabRef/jabref/pull/15385/files)

### Complete result sets
`req~search.backend.complete-results~1`

JabRef must return complete result sets for any search; results must never be silently capped at an arbitrary hit limit (a hardcoded `maxHits = 5` caused partial fulltext results).

Issue: [#8626](https://github.com/JabRef/jabref/issues/8626)

### Automatic index updates on data changes
`req~search.backend.auto-index-update~1`

JabRef must update every search index automatically when the underlying data changes: entry additions, removals, and single-field changes must be reflected without a manual index rebuild and without a full re-index.
(Technologies without automatic full-text-index maintenance on table changes — DuckDB at evaluation time — fail this requirement.)

> Currently fulfilled by `IndexManager` listening to `FieldChangedEvent`/entries events with incremental, per-entry-coalesced updates; no implementation is linked yet.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329)

### Index lifecycle notifications to the GUI
`req~search.backend.change-notifications~1`

JabRef must notify the GUI when an index becomes ready or changes (events for index started, entries added/updated, removed, index closed), so that displayed search results and search groups can re-evaluate.

> Currently fulfilled by `IndexStartedEvent` and friends posted on the database event bus; no implementation is linked yet.
{: .prompt-note}

PR: [#11803](https://github.com/JabRef/jabref/pull/11803)

### Matching a single entry against a query
`req~search.backend.single-entry-check~1`

JabRef must be able to test one single entry against a query without re-running the full library search; search groups and live entry editing depend on this.

> Currently fulfilled by `SearchBackend.isEntryMatched(entry, query)`; the SQL backend rewrites the query as `(entryid = X) AND (query)`; no implementation is linked yet.
{: .prompt-note}

PR: [#11803](https://github.com/JabRef/jabref/pull/11803)

### Backend-provided match highlighting
`req~search.backend.highlighting~1`

JabRef must provide search-term highlighting (marked-up text and match positions) to the GUI preview for whichever backend is active; the highlighting service must not depend on a *different* backend's infrastructure being available.

> Currently violated: highlighting is implemented as PostgreSQL plpgsql functions (`regexp_mark`, `regexp_positions`), which couples the GUI to the running Postgres server even when the in-memory backend is active.
{: .prompt-note}

PR: [#11803](https://github.com/JabRef/jabref/pull/11803)

### One isolated index per open library
`req~search.backend.per-library-isolation~1`

JabRef must maintain one isolated index per open library, support several libraries open simultaneously (including unsaved ones), and tie the index lifecycle to the library: created when the library is opened, dropped/closed when the library tab is closed.

> Currently fulfilled by one table (unique CUID name) per library in the SQL schema and one `IndexManager` per `LibraryTab`; no implementation is linked yet.
{: .prompt-note}

PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046), [#11542 (discussion)](https://github.com/JabRef/jabref/pull/11542#discussion_r1745632584)

### Relevance scoring and ranking
`req~search.backend.relevance-ranking~1`

JabRef should provide a relevance score per hit (formerly shown as a main-table column) and per-file match indication for linked PDFs.

> Aspirational and contested: deterministic "contains" semantics take precedence; the score-based ranking of the Lucene era was consciously dropped and is recorded as a regression worth restoring.
{: .prompt-note}

PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1788362730), [#11542](https://github.com/JabRef/jabref/pull/11542)

## Performance

### Index-backed query speed on large libraries
`req~search.backend.query-speed~1`

JabRef must answer searches via an index, not via a linear scan over all fields of all entries; fielded quick searches (e.g., by first author or citation key) and default (substring) searches must stay fast enough for search-as-you-type even on large libraries (100k+ entries).
Approaches whose default query path is presumed slow on large libraries (e.g., leading/double wildcards) must be benchmarked before becoming the default.

Issue: [#10490](https://github.com/JabRef/jabref/issues/10490)
PR: [#14574 (discussion)](https://github.com/JabRef/jabref/pull/14574#discussion_r2617521386)

### Debounced, two-path index maintenance
`req~search.backend.debounced-updates~1`

JabRef must debounce and coalesce index updates triggered by typing in the entry editor (suggested 200–300 ms after the last keystroke; the current implementation throttles at 700 ms with per-entry field coalescing), so a continuous editing burst produces one index write instead of one write per keystroke.
The two trigger paths must be handled separately: user-initiated searches execute immediately; edit-triggered index maintenance may be delayed and batched.
Acceptance: editing entries in a real-life library (1k–10k entries) with several search groups and an active search must never pin a CPU core at ~100% for minutes — also on battery-powered laptops.
This requirement is backend-agnostic: switching the DBMS alone does not fix per-keystroke load.

Issue: [#12190](https://github.com/JabRef/jabref/issues/12190), [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-2476285156), [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-2580315024), [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-3684249250), [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-3984738685)

### Bounded re-query cost for search groups
`req~search.backend.bounded-group-requeries~1`

JabRef must not let a single index update fan out into one search re-query per active search group per keystroke; group re-evaluations must be batched/coalesced so that the cost per editing burst stays bounded with N search groups (instead of "1 index update + N search queries" per keystroke).

Issue: [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-2580023014)

### Handling very large libraries
`req~search.backend.large-libraries~1`

JabRef must handle real-world libraries of at least 134,352 entries, with two such libraries (~180k entries total) open concurrently, without excessive memory growth.
Reference measurements to improve upon: +~640 MB heap for 45,974 entries and +~800 MB more for 134,352 entries on top of a 960 MB baseline.
Memory usage must stay roughly constant after a library is loaded (no unbounded growth/leak), and entry-table scrolling must be responsive immediately after load — backend indexing/loading must not starve the UI for minutes.

Issue: [#10209](https://github.com/JabRef/jabref/issues/10209), [#10209 (comment)](https://github.com/JabRef/jabref/issues/10209#issuecomment-1692316102), [#12708](https://github.com/JabRef/jabref/issues/12708)

### Near-real-time index freshness
`req~search.backend.near-real-time~1`

JabRef must reflect recent changes in query results promptly: searches must see not-yet-committed index changes (near-real-time semantics), and a newly attached PDF must be searchable as soon as its indexing finishes — closing and reopening the library must never be necessary.

> The Lucene implementation fulfils the NRT part via a `SearcherManager` refreshed before each query; the attach-freshness defect is recorded in #14569.
{: .prompt-note}

Issue: [#14569](https://github.com/JabRef/jabref/issues/14569)
PR: [#11542](https://github.com/JabRef/jabref/pull/11542)

### Cancellable background indexing with progress and clean shutdown
`req~search.backend.background-indexing~1`

JabRef must run all indexing operations (startup, add, remove, update, rebuild) in cancellable background tasks that report progress to the user; indexing must never block library opening or the UI.
The indexing API must accept a `BackgroundTask` handle (used per file for cancellation checks and progress/message updates), and the indexer must provide a clean, blocking shutdown (`closeAndWait`) so index directories can be safely released and deleted afterwards.

PR: [#11542](https://github.com/JabRef/jabref/pull/11542), [#15385 (comment)](https://github.com/JabRef/jabref/pull/15385#issuecomment-4106224458)

### Lazy loading and out-of-heap entry storage
`req~search.backend.lazy-loading~1`

JabRef should make lazy loading of entries easy, so the UI (dynamically filled `TableView`s) can fetch only the visible rows instead of materializing the whole library, and entry data should be pageable to disk rather than fully resident in the JVM heap (a backend page cache could serve as an out-of-heap entry store, lifting the RAM limit to disk size).
Memory savings should also cover the `.bib` load and save paths (avoid holding all strings in memory during parse/serialize).

> Aspirational: today all entries live as `List<BibEntry>` in the JVM heap; this requirement records the agreed direction of #12708 without committed acceptance criteria.
{: .prompt-note}

Issue: [#12708](https://github.com/JabRef/jabref/issues/12708), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764437282), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2717448258), [#10209 (comment)](https://github.com/JabRef/jabref/issues/10209#issuecomment-2376010585)

### Offloading derived-value computation to the backend
`req~search.backend.derived-value-offloading~1`

JabRef should let the backend host derived-value caches beyond search — parsed author lists (`AuthorList.parse` cache), LaTeX-free field values, `Author.latexFree` — and should execute LaTeX-to-Unicode / Unicode-to-LaTeX conversion and pre-formatted save output backend-side (e.g., views or stored procedures) rather than in Java, to save client memory and CPU.
The scope is deliberately limited: `MainTable` and `EntryEditor` are *not* to be ported wholesale to the backend; an estimated ~50% performance boost is expected from the two cache points alone.

> Aspirational: proposed by the maintainer in #10209/#12708; no implementation exists.
{: .prompt-note}

Issue: [#10209 (comment)](https://github.com/JabRef/jabref/issues/10209#issuecomment-2376534212), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2727673258)

### Streaming index construction and byte-offset retrieval
`req~search.backend.streaming-indexing~1`

JabRef should be able to build its index in a single streaming pass over the `.bib` file without loading the entire file into RAM, and should retrieve a full entry after a search hit via constant-time random access (e.g., byte-offset seek) rather than re-parsing the file.
If byte-offset pointers into the `.bib` file are used, the indexing layer must be UTF-8 multibyte-safe (offsets from UTF-8 byte lengths, not character counts), must detect and preserve the file's line endings (LF vs. CRLF), and should avoid duplicating full entry text in the index (store key + position + length only).
Entry write-back without rewriting the whole file (in-place when the new content fits, append otherwise) is part of the same proposal.

> Aspirational/proposed: this mirrors the byte-offset-indexer prototype by @ungerts; note that a pure pointer index has no native search and still requires a query engine on top.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764523461)
Prototype: [ungerts' gist](https://gist.github.com/ungerts/e347bc3a486833139da2cee5c25df88d)

### Reproducible performance benchmarks
`req~search.backend.benchmarks~1`

JabRef must make backend performance measurable through reproducible JMH benchmarks in `jablib`: at least fulltext query latency (a `@Benchmark` `search()` executing a real query against a built index) and full index rebuild time (remove all documents, re-index all entries).
Benchmark fixtures must resolve independently of the JVM working directory (a relative `src/test/resources/pdfs` path can silently yield an empty index and meaningless results), timed sections must exclude harness overhead (e.g., per-invocation Mockito mock creation), and benchmark setup code must stay small and single-responsibility.

PR: [#15385](https://github.com/JabRef/jabref/pull/15385/files), [#15385 (comment)](https://github.com/JabRef/jabref/pull/15385#issuecomment-4106224458), [#11542 (discussion)](https://github.com/JabRef/jabref/pull/11542#discussion_r1734435518)

## Resource footprint and startup

### Backend start must not delay application startup
`req~search.backend.startup-time~1`

JabRef must keep time-to-first-window at the 5.x level (1–2 s); search-backend initialization must therefore be lazy or sub-second.
Target for the backend's own cold start is tens of milliseconds (comparison figures from the discussion: ~50–150 ms for an in-process engine vs. 500–2000 ms — observed up to 12 s — for the embedded Postgres server, whose startup cost is argued to be "inherent and unfixable").

Issue: [#14970](https://github.com/JabRef/jabref/issues/14970), [#15866](https://github.com/JabRef/jabref/issues/15866), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759)

### Incremental startup indexing
`req~search.backend.incremental-startup~1`

JabRef must update persistent indexes incrementally at startup (e.g., by diffing stored file modification times against the filesystem); a full re-index on every startup or on unrelated library modifications is not acceptable for persistent indexes.
A session-scoped (rebuilt-per-start) bib-field index is tolerable only while its rebuild fits the startup-time and large-library budgets.
Optionally (aspirational), the bib-field index should be persistable across restarts, with a rebuild needed only when the `.bib` file changed outside JabRef (e.g., edited in a text editor).

Issue: [#8420](https://github.com/JabRef/jabref/issues/8420)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1771055857), [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2383232578), [#11542](https://github.com/JabRef/jabref/pull/11542)

### Low resource consumption on end-user machines
`req~search.backend.resource-consumption~1`

JabRef must keep the backend's CPU, memory, and disk consumption suitable for end-user machines (laptops, battery power) — not server-grade resource needs.
An idle backend must not consume measurable CPU in the background, and default-on background indexing must not render low-end machines unusable.

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329), [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-3288112045), [#9491 (comment)](https://github.com/JabRef/jabref/issues/9491#issuecomment-1371476088)

### Small disk footprint
`req~search.backend.disk-footprint~1`

JabRef must keep the backend's contribution to the installation size small; the embedded Postgres binaries added roughly 500 MB of storage, which was noted (half-ironically) as a downside to tolerate, not a precedent.

PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046)

### Zero administration
`req~search.backend.zero-administration~1`

JabRef must not require database-administration tasks — cache configuration, vacuuming to reclaim disk space, server tuning — from either JabRef code or the user; the backend must be a local, automatically managed store with zero user setup ("keeps complexity for the user to a minimum").

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046)

### One backend engine per JabRef instance
`req~search.backend.single-instance-per-process~1`

JabRef must run exactly one backend engine per running JabRef instance, serving all open libraries (e.g., one table or index per library) — not one engine per library or per search group.

Issue: [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-3288112045)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046)

## Deployment and distribution

### Native support for all shipped platforms
`req~search.backend.cross-platform~1`

JabRef must run the backend natively on Windows, macOS, and Linux on both x64 and arm64, including legacy macOS versions.
Missing platform binaries must never cause startup failures (linux-arm64 builds failed with "Missing embedded postgres binaries"), and falling back to x64 emulation with degraded performance (as happened on Apple Silicon) is not acceptable.

Issue: [#14783](https://github.com/JabRef/jabref/issues/14783), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764437282)

### In-process operation
`req~search.backend.in-process~1`

JabRef must run the search backend inside the JVM process; it must not spawn a separate OS process.
Rationale: spawning processes may be forbidden in certain (e.g., institutional) environments, and a child process cannot be cleaned up on SIGKILL; an in-process engine eliminates orphaned processes and external-process startup failures by design.

> Currently violated by the embedded Postgres server, which runs as a separate OS process; the in-memory backend fulfils it.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2738410393), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759), [#12844 (comment)](https://github.com/JabRef/jabref/issues/12844#issuecomment-3984034003)

### No network ports, local-only I/O
`req~search.backend.no-network-ports~1`

JabRef must not open network ports for the search backend and must not introduce security exposure on end-user machines; all index/search I/O must be local (and buffered), with no network or remote calls, so that search works fully offline.

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764523461)

### Compatibility with the distribution toolchain
`req~search.backend.packaging~1`

JabRef must be able to package the backend with jlink/jpackage (and the project's `jbundle.toml`) under JPMS: backend libraries must ship a proper `module-info` (or otherwise not rely on `ServiceLoader` from inside JabRef's merged module — Lucene 9.0 failed with `ServiceConfigurationError ... does not declare uses` until Lucene 9.1 added module info).
Search must work in the packaged binaries, not only from the IDE, and must be verified on Windows and Linux with linked PDFs before release.

PR: [#8362 (comment)](https://github.com/JabRef/jabref/pull/8362#issuecomment-1008170042), [#8362 (comment)](https://github.com/JabRef/jabref/pull/8362#issuecomment-1007988527)
Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759)

### Headless and CLI operation
`req~search.backend.headless-operation~1`

JabRef must provide search in headless deployments: `jabkit` (CLI — including "export matching entries" and the `search` command, and also when shipped as a GraalVM native image) and `jabsrv` (HTTP server) must be able to run searches without GUI-only backend infrastructure; the backend must either be startable on demand from the command line or be substitutable by an in-process implementation.

> Currently, the stand-alone HTTP server and `jabkit` hard-code the in-memory search path and never start the embedded Postgres server.
{: .prompt-note}

PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2415740941)

### Portable, copyable data files
`req~search.backend.portable-data~1`

JabRef should use a backend whose database/index files can be trivially copied and delivered as part of the application (easy initialization, e.g., for pre-built data such as journal abbreviations).

> Contested in scope: pre-loading journal abbreviations was explicitly declared *not* core backend functionality (file timestamps plus background loading suffice), so this requirement must not dominate the technology choice.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3949776169)

## Reliability and robustness

### The .bib file remains the single source of truth
`req~search.backend.source-of-truth~1`

JabRef must treat the `.bib` file as the single, ultimate source of truth; every database or search index is a rebuildable, disposable cache that can be wiped, deleted, or recreated in an arbitrary directory at any time without data loss.
The design must assume that an index written by a newer JabRef cannot be opened by an older JabRef (one-directional format compatibility), and must not rely on persistent entry identity across sessions — entries are keyed by a session-stable `entryId` (ADR 0038), since `BibEntry#hashCode` is not persistent.

> Currently fulfilled: the SQL schema is dropped and recreated on every start, per-library tables are dropped on close, and the Lucene index can be rebuilt from the entries.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1771055857), [#8362 (comment)](https://github.com/JabRef/jabref/pull/8362#issuecomment-1089026074), [#11542](https://github.com/JabRef/jabref/pull/11542)

### No orphaned processes
`req~search.backend.no-orphan-processes~1`

JabRef must leave no OS process running after it terminates — including normal exit, crash, `kill -9`, and Task Manager "End task"; the backend must die with the JVM.
Cleanup must not depend on JVM shutdown hooks, because no code runs on SIGKILL (verified experimentally); any process-watchdog mitigation must work on Windows (the pipe-EOF watchdog approach failed there).
Repeated start/stop cycles (e.g., IDE restarts during development) must not accumulate background processes that consume CPU/memory.

Issue: [#12844](https://github.com/JabRef/jabref/issues/12844), [#12844 (comment)](https://github.com/JabRef/jabref/issues/12844#issuecomment-2762156276), [#12844 (comment)](https://github.com/JabRef/jabref/issues/12844#issuecomment-3985740469), [#12844 (comment)](https://github.com/JabRef/jabref/issues/12844#issuecomment-2761876873)

### Backend failure must not block application startup
`req~search.backend.startup-failure-isolation~1`

JabRef must start successfully even if search-backend initialization fails; search startup must not be a fatal single point of failure for the whole application.
On failure, JabRef degrades to reduced search functionality with a clear error message.

> The current `PostgreServer` catches startup `IOException`s and degrades to null connections; nevertheless, #15111 documents a release where a backend unpack error prevented JabRef from starting at all.
{: .prompt-note}

Issue: [#15111](https://github.com/JabRef/jabref/issues/15111)

### Automatic recovery from stale or corrupted cached state
`req~search.backend.stale-state-recovery~1`

JabRef must detect and repair stale or corrupted cached backend state automatically — unpacked native binaries, index directories, temp files — without requiring the user to delete directories manually (a stale `/tmp/embedded-pg` caused a `FileAlreadyExistsException` that blocked startup until manual deletion).
Backend state must live in a robust application-data location, not in a shared temp directory managed by a testing-oriented library (the current data location was called "very fragile" by the maintainers).
Startup must tolerate version upgrades (leftovers from a previous JabRef or backend version must not break the next start), and this robustness must be independent of the packaging/distribution format (the failure persists in the portable build).

Issue: [#15111](https://github.com/JabRef/jabref/issues/15111), [#15111 (comment)](https://github.com/JabRef/jabref/issues/15111#issuecomment-3902276144), [#15111 (comment)](https://github.com/JabRef/jabref/issues/15111#issuecomment-3902304889), [#15111 (comment)](https://github.com/JabRef/jabref/issues/15111#issuecomment-3902332887)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046)

### Versioned indexes with automatic rebuild
`req~search.backend.index-versioning~1`

JabRef must carry a format/analyzer version with every persistent index; when the stored version does not match the version JabRef uses, the index must be deleted and rebuilt automatically instead of failing at runtime (historically: `CorruptIndexException`/`NoClassDefFoundError` until users deleted the index folder manually).
Index paths must differ between application major versions that are unaware of the versioning scheme, and old version directories must be cleaned up.

> Currently fulfilled by versioned Lucene index directories (`lucene/<VERSION>`, "Incrementing triggers reindexing") and `clearOldSearchIndices`; no implementation is linked yet.
{: .prompt-note}

PR: [#8362 (comment)](https://github.com/JabRef/jabref/pull/8362#issuecomment-1007965373), [#11542 (discussion)](https://github.com/JabRef/jabref/pull/11542#discussion_r1741154424)

### Graceful degradation when the backend becomes unavailable
`req~search.backend.graceful-degradation~1`

JabRef must degrade gracefully if the backend dies or becomes unavailable mid-session (e.g., its process is killed externally): the GUI must stay usable, and the failure must be reported once — not as a storm of hundreds to thousands of identical exceptions per second ("This connection has been closed").
If a persistent index cannot be opened for writing (e.g., locked by another instance), search must fall back to a read-only index rather than failing.

> The read-only fallback exists (`ReadOnlyLinkedFilesIndexer`); the exception-storm behavior is a documented defect of the external-process design.
{: .prompt-note}

Issue: [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-2501284217), [#12190 (comment)](https://github.com/JabRef/jabref/issues/12190#issuecomment-2613964587)

### Race-free concurrent index updates
`req~search.backend.concurrent-writes~1`

JabRef must keep concurrent index writes during entry editing race-free: competing indexing threads must never produce duplicate-key/unique-constraint violations (observed: `duplicate key value violates unique constraint ... (entryid, field_name)=(00229933, publisher)` while editing the publisher field).

Issue: [#12167](https://github.com/JabRef/jabref/issues/12167)

### Robust bulk indexing and reliable rebuild
`req~search.backend.robust-bulk-indexing~1`

JabRef must index large linked-file collections (e.g., 17,000 files) to completion, skipping unreadable or broken files with a log entry instead of aborting the whole run.
A user-triggered index rebuild must reliably work and report progress and completion; stale lock files must never silently block a rebuild (no Lucene `write.lock` "Lock held by this virtual machine" dead-ends).
Users must be able to inspect index health: size, item count, and indexing failures (a 2 GB file collection producing a 500 kB index gave no diagnosis).

Issue: [#13048](https://github.com/JabRef/jabref/issues/13048), [#11374](https://github.com/JabRef/jabref/issues/11374), [#8626](https://github.com/JabRef/jabref/issues/8626)

### Multiple concurrent JabRef instances
`req~search.backend.concurrent-instances~1`

JabRef must keep search functional when two or more JabRef instances run concurrently: each instance gets its own working backend without port, file-lock, or process-ownership conflicts.
JabRef must never kill or adopt server processes it cannot prove it owns (users may legitimately run their own database servers; ports are auto-allocated), which rules out kill-or-reuse-on-restart heuristics.

PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2381330518)
Issue: [#12844 (comment)](https://github.com/JabRef/jabref/issues/12844#issuecomment-3985740469), [#12844 (comment)](https://github.com/JabRef/jabref/issues/12844#issuecomment-2764224806)

### Tolerance of malformed BibTeX
`req~search.backend.malformed-bibtex~1`

JabRef must tolerate malformed BibTeX input (e.g., unbalanced braces) without corrupting the index or crashing indexing; problematic entries are skipped or recovered.

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764523461)

## Licensing and maintainability

### License-compatible, free dependencies
`req~search.backend.license-compatibility~1`

JabRef must only use backend dependencies that are free of charge, open source, and license-compatible with JabRef's MIT distribution, and that may be redistributed in JabRef's installers on all supported platforms without registration or fees.

> Editorially added background constraint: the search-backend discussions themselves (in particular issue [#12708](https://github.com/JabRef/jabref/issues/12708)) do not mention licensing, fees, or redistribution terms. The constraint reflects JabRef's established dependency-licensing practice — JabRef is distributed under the [MIT license](https://github.com/JabRef/jabref/blob/main/LICENSE), and dependencies have repeatedly been selected or excluded on license-compatibility grounds.
{: .prompt-note}

ADR: [ADR 0004 (MariaDB connector chosen over GPL-licensed MySQL connector)](../decisions/0004-use-mariadb-connector.md), [ADR 0056 (OCR engine candidates excluded on license grounds)](../decisions/0056-OCR-engine-selection.md)

### Convergence on a single storage technology
`req~search.backend.single-technology~1`

JabRef should converge on a single (or a minimal number of) storage/search technology: one backend should serve both bib-field search and linked-PDF fulltext search instead of shipping two engines in parallel (currently embedded PostgreSQL + Lucene), and ideally also other persistence needs (journal abbreviations currently use H2 MVStore).

> Contested side argument: choosing the same technology as JabRef's remote shared-library storage ("we also support Postgres for remote storage") was named as an advantage; it presupposes a specific DBMS and is recorded here only as input to the decision.
{: .prompt-note}

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329), [#12261](https://github.com/JabRef/jabref/issues/12261)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046)

### Architectural decision record
`req~search.backend.decision-record~1`

JabRef must document the persistence/search-technology decision as a formal architectural decision record (ADR) that weighs scalability, performance, maintainability, and user-environment constraints, validated by comparisons, prototypes, and benchmarks — not by ad-hoc choice.

Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2746173329), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764530374)

### Pluggable backend behind a stable abstraction
`req~search.backend.pluggable~1`

JabRef must keep the search backend behind a stable abstraction (the `SearchBackend` bridge) so that backends are interchangeable; the active backend must be swappable at runtime via user preference without restarting JabRef, with the newly activated backend re-indexing the current entries on activation.
Shared grammar-semantics tests (see `req~search.backend.query-grammar~1`) must run against every backend implementation.

> Currently fulfilled by `SearchContext` swapping `SqlSearchBackend`/`InMemorySearchBackend`/`NoOpSearchBackend` live on the `usePostgresSearch` preference; no implementation is linked yet.
{: .prompt-note}

Issue: [#12708](https://github.com/JabRef/jabref/issues/12708)

### Query-syntax stability across backend changes
`req~search.backend.query-syntax-stability~1`

JabRef must not invalidate saved search-group queries persisted in users' libraries when the backend changes: either the query syntax stays stable, or an automatic, lossless converter is provided.
Pre-release syntax migrations (such as the temporary Lucene syntax of the 6.0 alphas, where users had to restore backups because no back-migration existed) must not leave permanent migration heuristics in the codebase.

Issue: [#11823 (comment)](https://github.com/JabRef/jabref/issues/11823#issuecomment-2373958933)
PR: [#11803 (discussion)](https://github.com/JabRef/jabref/pull/11803#discussion_r1788305295), [#11542](https://github.com/JabRef/jabref/pull/11542)

### Extensibility beyond search
`req~search.backend.extensibility~1`

JabRef should be able to reuse the backend for other features — e.g., additional tables for AI features such as vector storage ("feel free to create other tables") — and the backend should have the potential to be used by multiple clients.
An immutable/temporal data history (traceable screening decisions, recognizing previously imported entries after they changed) has been proposed as a further opportunity.

> Aspirational: the multi-client and temporal-history items are proposals from the discussion, not agreed commitments.
{: .prompt-note}

PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2366800808)
Issue: [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2764437282), [#12708 (comment)](https://github.com/JabRef/jabref/issues/12708#issuecomment-2724380342)

### Staged, bounded migration process
`req~search.backend.migration-process~1`

JabRef must make the overall backend decision (issue [#12708](https://github.com/JabRef/jabref/issues/12708)) before migrating the fulltext (PDF) search — issue #12261 is explicitly blocked by it.
Migration work should be scoped as bounded contributor projects (roughly 30 days / ~40 person-days per master-level student), and large refactoring PRs touching search should be merged early, with remaining work done in follow-up PRs, to remain mergeable.

> Process constraint rather than a product property; recorded here so the sequencing rationale is not lost.
{: .prompt-note}

Issue: [#12261 (comment)](https://github.com/JabRef/jabref/issues/12261#issuecomment-3958153407), [#12261 (comment)](https://github.com/JabRef/jabref/issues/12261#issuecomment-2843045198)
PR: [#11803 (comment)](https://github.com/JabRef/jabref/pull/11803#issuecomment-2413637979)

<!-- markdownlint-disable-file MD022 -->
