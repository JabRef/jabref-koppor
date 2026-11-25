---
parent: Evaluations
---
# SQLite with FTS5 as search backend

This document evaluates **SQLite with its FTS5 full-text extension** (accessed via the [xerial `sqlite-jdbc`](https://github.com/xerial/sqlite-jdbc) driver) as the backend for JabRef's search within a library, including fulltext search of linked PDF files.
It assesses the option against the requirements in [Search backend](../requirements/search-backend.md) (`req~search.backend.*~1`) and unpacks the arguments from issue [#12708](https://github.com/JabRef/jabref/issues/12708).

Throughout, facts are labeled: **[JabRef-measured]** (numbers measured in JabRef issues/PRs), **[cited]** (external sources, linked), **[claimed in #12708]** (numbers posted in the discussion, not independently reproduced), and **[inference]** (this document's own reasoning).

## Verdict

SQLite + FTS5 is the only candidate that could realistically collapse JabRef's current three storage technologies (embedded PostgreSQL, Lucene, H2 MVStore) into **one**, while structurally eliminating the entire external-process failure class (orphaned processes [#12844](https://github.com/JabRef/jabref/issues/12844), unpack startup failures [#15111](https://github.com/JabRef/jabref/issues/15111), missing platform binaries [#14783](https://github.com/JabRef/jabref/issues/14783)) and cutting the backend's installer contribution from roughly 500 MB to roughly 14 MB.
Substring ("contains") search — the requirement that broke Lucene and motivated the Postgres switch — is a *native, indexed* FTS5 capability via the trigram tokenizer.
The two honest weaknesses are: **regular-expression search has no index support in SQLite** (it degrades to a per-row Java callback unless JabRef hand-builds a trigram pre-filter, i.e., re-implements what pg_trgm gives Postgres for free), and **fuzzy search/relevance ranking are weaker than Lucene's**.
Requirement coverage is approximately: 53 yes, 6 partial, 1 no, 2 process-level (see table).
Recommendation-quality summary: a strong, low-risk choice for the bib-field index and a *plausible but unproven* choice for PDF fulltext; the regex mechanism and the fulltext path must be prototyped and benchmarked before an ADR commits to it.

## Technology overview

[SQLite](https://sqlite.org/) is an in-process, serverless, single-file SQL database engine written in C; it runs inside the host process and performs only local file I/O.
[FTS5](https://sqlite.org/fts5.html) is its built-in full-text search engine: a virtual-table module providing an inverted index, boolean `MATCH` queries, prefix queries, `bm25()` ranking, and `highlight()`/`snippet()` auxiliary functions.
Four tokenizers ship built-in: `unicode61` (default; Unicode-aware, optional diacritic removal), `ascii`, `porter` (stemming wrapper), and — decisive for JabRef — `trigram`, which indexes every contiguous 3-character sequence and thereby supports **indexed substring matching** and indexed `LIKE`/`GLOB`, the same idea as PostgreSQL's pg_trgm ([FTS5 docs](https://sqlite.org/fts5.html); trigram support added in SQLite 3.34.0, 2020-12-01, [release log](https://sqlite.org/releaselog/3_34_0.html)).

How JabRef would embed it:

- **Driver**: `org.xerial:sqlite-jdbc` (JDBC over JNI; the de-facto standard, used by virtually every JVM desktop app shipping SQLite). The bundled SQLite is compiled with `SQLITE_ENABLE_FTS5` ([Makefile](https://github.com/xerial/sqlite-jdbc/blob/master/Makefile)), so FTS5 works out of the box.
- **Schema**: a 1:1 port of the current Postgres design (which is plain SQL): one main entity–attribute–value table per open library (`entryid`, `field_name`, `field_value_literal`, `field_value_transformed`) plus a `_split_values` table, exactly as `BibFieldsIndexer` creates today — but in SQLite the *whole database is one file* (or an in-memory database for session-scoped indexes).
- **Substring index**: an FTS5 virtual table using `tokenize = 'trigram'` over the value columns (external-content table pointing at the main table, kept in sync by triggers or by the same targeted `INSERT`/`UPDATE`/`DELETE` statements `BibFieldsIndexer` already issues).
- **Regex**: a custom `REGEXP` SQL function registered from Java (`org.sqlite.Function`); SQLite defines the `REGEXP` operator syntax but ships **no** default implementation ([expression docs](https://sqlite.org/lang_expr.html): "No regexp() user function is defined by default").
- **PDF fulltext**: a second FTS5 table, one row per PDF page (`path`, `page`, `content`, `annotations`, `mtime`) populated from the existing PDFBox `DocumentReader`; `snippet()`/`highlight()` produce the marked-up excerpts the GUI shows today.
- **Query compilation**: a SQLite dialect of `SearchToSqlVisitor`; the CTE/`INTERSECT`/`UNION` structure it generates is standard SQL that SQLite executes unchanged — only the operator layer (`ILIKE`, `~`, `~*`, `ON CONFLICT`) needs porting (SQLite has `LIKE`, `GLOB`, `MATCH`, `REGEXP`-via-UDF, and upsert `ON CONFLICT` clauses).

## Requirement coverage

| Requirement (short title) | ID | Verdict | Notes |
|---|---|---|---|
| Query grammar, backend-independent semantics | `req~search.backend.query-grammar~1` | yes | SQLite dialect of `SearchToSqlVisitor`; CTE/`INTERSECT`/`UNION` ports as-is |
| Deterministic substring matching | `req~search.backend.substring-search~1` | yes | FTS5 trigram index; <3-char terms fall back to scan (see prose) |
| Literal terms without user escaping | `req~search.backend.literal-special-characters~1` | yes | JDBC prepared statements; JabRef builds `MATCH` strings, user never sees them |
| Case-insensitive + case-sensitive matching | `req~search.backend.case-sensitivity~1` | yes | trigram `case_sensitive` option; case-sensitive verify-filter on folded index |
| Backend-resolved regex search | `req~search.backend.regex-search~1` | **partial** | Java `REGEXP` UDF = unindexed row scan; trigram pre-filter must be hand-built |
| Diacritic/LaTeX/transliteration normalization | `req~search.backend.term-normalization~1` | partial | app-level two-column scheme ports 1:1; trigram `remove_diacritics` helps; `ue`↔`ü` stays app work (as for all candidates) |
| Person-name normalization | `req~search.backend.name-normalization~1` | partial | app-level (`AuthorList`), backend-neutral |
| Non-Latin scripts, opt-in romanization | `req~search.backend.non-latin-scripts~1` | yes | UTF-8 exact storage/match; trigram covers CJK ≥3 chars; 1–2-char CJK terms unindexed |
| Fuzzy search | `req~search.backend.fuzzy-search~1` | **no** | no native fuzzy matching; `spellfix1` not in xerial build; would be app-level |
| Anchored prefix / position-aware matching | `req~search.backend.prefix-and-position~1` | yes | `LIKE 'term%'`; position column in split-values table |
| Exact match in multi-value fields | `req~search.backend.multi-value-fields~1` | yes | `_split_values` table design ports unchanged |
| Scope of "any" search | `req~search.backend.any-field-scope~1` | yes | `WHERE field_name != 'groups'` identical |
| Derived/resolved value indexing | `req~search.backend.derived-values~1` | yes | done in Java by the indexer, backend-neutral |
| Fulltext search of linked files | `req~search.backend.fulltext-search~1` | yes | FTS5 page-per-row table; `snippet()`/`highlight()`; must be built |
| Fulltext substring + regex semantics | `req~search.backend.fulltext-substring-regex~1` | **partial** | substring: trigram, consistent with fields; regex over PDF text: unindexed UDF scan, unproven at scale |
| Fulltext indexing optional/toggleable | `req~search.backend.fulltext-optional~1` | yes | preference gate stays app-level; `DROP TABLE` on disable |
| Complete result sets | `req~search.backend.complete-results~1` | yes | SQL result sets, no hit caps |
| Automatic index updates | `req~search.backend.auto-index-update~1` | yes | targeted DML or external-content triggers (unlike DuckDB's stale FTS) |
| Index lifecycle notifications | `req~search.backend.change-notifications~1` | yes | `IndexManager` events, backend-neutral |
| Single-entry match check | `req~search.backend.single-entry-check~1` | yes | same `(entryid = ?) AND (query)` rewrite |
| Backend-provided highlighting | `req~search.backend.highlighting~1` | yes | re-implement `regexp_mark`/`regexp_positions` in Java; removes GUI→Postgres coupling |
| One isolated index per library | `req~search.backend.per-library-isolation~1` | yes | per-library tables in one DB, or one DB file per library (`ATTACH` limit 125 in xerial build) |
| Relevance scoring | `req~search.backend.relevance-ranking~1` | **partial** | `bm25()` exists but is meaningless over trigram tokens; needs a second word-token index |
| Index-backed query speed | `req~search.backend.query-speed~1` | yes | **[cited]** 10–30 ms trigram queries on 18.2 M rows; 100k entries is small for SQLite |
| Debounced two-path updates | `req~search.backend.debounced-updates~1` | yes | existing 700 ms throttler retained; explicitly backend-agnostic |
| Bounded group re-queries | `req~search.backend.bounded-group-requeries~1` | yes | app-level batching, backend-agnostic |
| Very large libraries (≥134k entries) | `req~search.backend.large-libraries~1` | yes | disk-paged B-tree, ~8 MB default page cache; backend is not the heap problem |
| Near-real-time freshness | `req~search.backend.near-real-time~1` | yes | committed writes immediately visible; no NRT/reopen machinery needed |
| Cancellable background indexing | `req~search.backend.background-indexing~1` | yes | `BackgroundTask` orchestration stays app-level; single-writer discipline needed |
| Lazy loading / out-of-heap storage | `req~search.backend.lazy-loading~1` | yes | SQLite's strongest card: rowid random access, pages on disk |
| Derived-value offloading to backend | `req~search.backend.derived-value-offloading~1` | **partial** | cache tables yes; no stored procedures — computation stays in Java (UDFs run in-process anyway) |
| Streaming indexing / byte-offset retrieval | `req~search.backend.streaming-indexing~1` | yes | compatible with the BibTeXIndexer proposal; offsets storable as columns |
| Reproducible JMH benchmarks | `req~search.backend.benchmarks~1` | yes | `sqlite-jdbc` trivially usable from the existing JMH harness |
| Backend start must not delay startup | `req~search.backend.startup-time~1` | yes | open-a-file cost; ~50–150 ms cold **[claimed in #12708]** vs observed ~12 s Postgres path **[JabRef-measured]** |
| Incremental startup indexing | `req~search.backend.incremental-startup~1` | yes | persistent single-file DB enables persistent bib index + mtime diffing (improvement over drop-schema-per-start) |
| Low resource consumption | `req~search.backend.resource-consumption~1` | yes | idle = zero CPU; no background workers, no vacuum daemon |
| Small disk footprint | `req~search.backend.disk-footprint~1` | yes | ~13.9 MB all-platform jar (smaller per-platform classifiers) vs ~500 MB Postgres **[cited / JabRef-stated]** |
| Zero administration | `req~search.backend.zero-administration~1` | yes | no server tuning; index is a rebuildable cache, so even `VACUUM` is avoidable |
| One engine per JabRef instance | `req~search.backend.single-instance-per-process~1` | yes | one connection (pool) per process serving all libraries |
| Native on win/mac/linux × x64/arm64 | `req~search.backend.cross-platform~1` | yes | verified native libs: Windows x86/x64/armv7/aarch64, macOS x64/aarch64, Linux x64/aarch64/musl **[cited]** |
| In-process operation | `req~search.backend.in-process~1` | yes | JNI inside the JVM; no child process, by design |
| No network ports | `req~search.backend.no-network-ports~1` | yes | file I/O only; fully offline |
| jlink/jpackage/JPMS packaging | `req~search.backend.packaging~1` | yes | real `module-info` (`org.xerial.sqlitejdbc`) since 2022; Java 24 needs `--enable-native-access` (flag list already exists) |
| Headless + GraalVM native image | `req~search.backend.headless-operation~1` | yes | native-image support out of the box since 3.40.1.0 **[cited]**; usable from jabkit/jabsrv |
| Portable, copyable data files | `req~search.backend.portable-data~1` | yes | single `.db` file; cross-platform bit-identical format **[cited]** |
| .bib stays single source of truth | `req~search.backend.source-of-truth~1` | yes | DB file is a disposable cache; delete + rebuild any time |
| No orphaned processes | `req~search.backend.no-orphan-processes~1` | yes | dies with the JVM; SIGKILL-safe by design |
| Startup-failure isolation | `req~search.backend.startup-failure-isolation~1` | yes | init failures are catchable Java exceptions; degrade to in-memory backend |
| Stale-state recovery | `req~search.backend.stale-state-recovery~1` | partial | native-lib extraction to temp dir is a (much smaller) cousin of #15111; mitigations exist (see prose) |
| Versioned indexes, auto rebuild | `req~search.backend.index-versioning~1` | yes | `PRAGMA user_version` stamp; on mismatch delete file + rebuild |
| Graceful degradation | `req~search.backend.graceful-degradation~1` | yes | no separate process to die; `SQLITE_BUSY`/read-only fallbacks are local error handling |
| Race-free concurrent writes | `req~search.backend.concurrent-writes~1` | yes | single writer + `ON CONFLICT` upsert removes the #12167 race class; needs a serialized writer |
| Robust bulk indexing, inspectable health | `req~search.backend.robust-bulk-indexing~1` | yes | per-file skip stays app-level; no lock-file dead ends (WAL auto-recovers); health queryable via SQL/`dbstat` |
| Multiple concurrent JabRef instances | `req~search.backend.concurrent-instances~1` | yes | per-instance session DB, or shared persistent file via WAL multi-process (same host) |
| Tolerance of malformed BibTeX | `req~search.backend.malformed-bibtex~1` | yes | parser-level concern, backend-neutral |
| License-compatible, free | `req~search.backend.license-compatibility~1` | yes | SQLite public domain; driver Apache-2.0/BSD-2 **[cited]** |
| Convergence on a single technology | `req~search.backend.single-technology~1` | yes | the unique selling point: one engine for bib fields + PDF fulltext + abbreviations is plausible |
| Formal ADR | `req~search.backend.decision-record~1` | yes (process) | this evaluation is input to that ADR |
| Pluggable behind `SearchBackend` | `req~search.backend.pluggable~1` | yes | new bridge implementor; runtime-swappable like the existing three |
| Query-syntax stability | `req~search.backend.query-syntax-stability~1` | yes | user grammar untouched; only the internal compiler changes |
| Extensibility beyond search | `req~search.backend.extensibility~1` | yes | extra tables ("feel free to create other tables"); vector ext. noted in ADR-0037; multi-client same-host only |
| Staged, bounded migration | `req~search.backend.migration-process~1` | yes (process) | naturally stageable: bib fields first, PDF fulltext second |

### Substring search and query speed (`substring-search`, `query-speed`)

The trigram tokenizer makes substring matching an *index lookup*: every 3-character sequence of a field value becomes a token, and a query for `dorf` is answered by intersecting the posting lists of `dor` and `orf` and verifying candidates — mechanically the same plan pg_trgm executes with its GIN index.
The [FTS5 docs](https://sqlite.org/fts5.html) additionally state that trigram tables accelerate ordinary `LIKE`/`GLOB` patterns (unless `remove_diacritics` is enabled), so the existing `LIKE '%term%'` query shapes from `SearchToSqlVisitor` could even survive nearly verbatim.
**[cited]** An external benchmark on 18.2 million rows measured 1.75 s for an unindexed substring `LIKE`, and 10–30 ms with a trigram FTS5 table ([andrewmara.com](https://andrewmara.com/blog/faster-sqlite-like-queries-using-fts5-trigram-indexes)) — this is the very number quoted in #12708.
**[inference]** A 100k-entry bib library produces on the order of a few million (entry, field) rows — two orders of magnitude below that benchmark — so query latency is not a concern.
Two real caveats: (1) terms shorter than 3 Unicode characters "do not match any rows" in trigram full-text queries ([FTS5 docs](https://sqlite.org/fts5.html)), so 1–2-character searches must fall back to an unindexed `LIKE`/`instr` scan — at 100k entries an in-C linear scan is still fast **[inference]**, but it must be implemented as an explicit fallback; (2) the trigram index is large — the cited benchmark added 2.4 GiB of index to a 1.3 GiB table (1.5 GiB with `detail='none'`), i.e., roughly 1–2× the indexed text. For bib fields (tens of MB) this is irrelevant; for PDF fulltext of multi-GB collections it is a sizing question (see Risks).

### Case sensitivity (`case-sensitivity`)

The trigram tokenizer takes a `case_sensitive` option (default 0 = case-folded).
The clean mechanism is one case-folded trigram index serving both modes: case-insensitive queries use `MATCH` directly; case-sensitive queries (`=!`, `==!`) use the folded index to obtain candidates, then verify with a case-sensitive comparison (`GLOB`, `instr`) — a standard verify-filter, and the same trick the in-memory backend's semantics define.
Note that SQLite's *core* `LIKE` is case-insensitive only for ASCII; full Unicode folding must come from the FTS5 path or from JabRef's transformed column, since the xerial build does not include the ICU extension **[inference from the build flags](https://github.com/xerial/sqlite-jdbc/blob/master/Makefile)**.
Unlike the Lucene fulltext index ("Lucene doesn't support case-sensitive searches", PR #11803), case-sensitive fulltext becomes possible.

### Regular expressions (`regex-search`, `fulltext-substring-regex`) — the central weakness

SQLite has **no native regex**: the `REGEXP` operator exists in the grammar but no `regexp()` function is defined by default ([docs](https://sqlite.org/lang_expr.html)).
JabRef would register a Java implementation via `org.sqlite.Function` (supported by sqlite-jdbc), compiling to cached `java.util.regex` patterns — functionally complete, semantically identical to the in-memory backend.
But this executes the regex **per row with a JNI→Java callback and no index support**: on a 100k-entry library that is a few million callback invocations per regex query **[inference]**.
This is precisely the capability for which Postgres was chosen ("the DBMS does the regex indexing and resolving — and not the client", koppor in [#12708](https://github.com/JabRef/jabref/issues/12708#issuecomment-2717449913)).
A pg_trgm-equivalent *can* be hand-built — extract required literal substrings from the regex, pre-filter candidates through the trigram index, run the Java regex only on survivors (the same architecture pg_trgm and Lucene's `RegexpQuery`+NGram use) — but it is genuine engineering work that no library provides for SQLite, and it must be benchmarked.
Until then the requirement's letter ("regex evaluation must be done by the backend, ideally index-supported") is met only half-way: evaluation happens inside the query engine, but as an unindexed scan.
For PDF fulltext the same applies with larger inputs, hence **partial** for `fulltext-substring-regex` as well (substring over PDFs, in contrast, is fully covered by the trigram index, fixing the [#14569](https://github.com/JabRef/jabref/issues/14569) inconsistency between field and fulltext semantics).

### Normalization (`term-normalization`, `name-normalization`, `non-latin-scripts`)

Issue [#12261](https://github.com/JabRef/jabref/issues/12261) already records that variant unification (`Düsseldorf`/`Duesseldorf`/`D\"{u}sseldorf`/`Dusseldorf`) "is as hard in Postgres as it is in Lucene" — it is application-level work for *every* candidate.
SQLite changes nothing about the proven mechanism: `BibFieldsIndexer` keeps writing both `field_value_literal` and `field_value_transformed` (LaTeX→Unicode), and queries keep testing both columns.
FTS5 contributes one extra: the trigram tokenizer's `remove_diacritics` option folds `Képes`→`Kepes` *in the index*, covering [#12685](https://github.com/JabRef/jabref/issues/12685) without an extra column — at the price of losing the indexed-`LIKE` side path (the `MATCH` path remains).
`ue`↔`ü` equivalence and name normalization remain Java-side folding at indexing time, exactly as the requirement permits.
The cautionary tale that "Zotero has failed to solve this for 10+ years on SQLite" (#12708) is real but is an argument about *application* analyzer pipelines, not about the storage engine: JabRef's two-column transform already goes beyond what Zotero does **[inference]**.
CJK: UTF-8 text is stored and matched exactly; trigram tokens are Unicode-character-based, so `力学学报` (4 chars) is indexed; 2-character terms — common for Chinese names — hit the <3-char fallback scan; romanization stays an opt-in Java feature as required.

### Fuzzy search and ranking (`fuzzy-search`, `relevance-ranking`)

FTS5 has no Levenshtein/fuzzy matching; SQLite's `spellfix1` extension exists but is not compiled into sqlite-jdbc and would need separate distribution.
Lucene's `FuzzyQuery` is clearly stronger here; under SQLite, fuzzy search would be an application-level feature (e.g., Java edit-distance filtering of candidate terms) — hence **no** for the backend itself (the requirement is a "should").
`bm25()` provides ranking, but over trigram tokens its scores are not meaningful relevance; a usable score column would need a second, word-tokenized (`unicode61`/`porter`) FTS5 index over the same content — feasible (FTS5 supports multiple indexes over external content) but extra index size and sync work — hence **partial**.

### PDF fulltext (`fulltext-search`, `fulltext-optional`, `complete-results`)

The mechanism is a direct translation of the Lucene design: one FTS5 row per PDF page (the Lucene index also stores one document per page), columns for path/page/mtime, text from the existing PDFBox `DocumentReader`.
`snippet(ft, col, '<mark>', '</mark>', '…', 64)` and `highlight()` give marked-up excerpts ([FTS5 docs](https://sqlite.org/fts5.html)); page numbers are ordinary columns; incremental updates diff stored `mtime` values exactly as `DefaultLinkedFilesIndexer` does today.
Because results are plain SQL result sets, the `maxHits=5`-style silent truncation of [#8626](https://github.com/JabRef/jabref/issues/8626) cannot recur structurally.
None of this exists yet — it is the larger half of the migration (see Migration effort).

### Highlighting (`highlighting~1`)

Today the GUI `Highlighter` sends `SELECT regexp_mark(?, ?)` to the Postgres server — the hidden coupling that forces `Launcher` to start Postgres even when the in-memory backend is active.
Under this option the two plpgsql helpers become plain Java methods (or registered SQLite UDFs); either way the GUI stops depending on any server, and the in-memory backend can share the same implementation — this option *fixes* the currently violated requirement rather than merely matching it.

### Concurrency, instances, persistence (`concurrent-writes`, `concurrent-instances`, `incremental-startup`, `per-library-isolation`)

SQLite allows exactly one writer at a time per database; in WAL mode readers and the writer proceed concurrently ([WAL docs](https://sqlite.org/wal.html)).
JabRef's indexer currently uses up to five concurrent writer threads against Postgres (and produced duplicate-key races, [#12167](https://github.com/JabRef/jabref/issues/12167)); under SQLite, writes must be funneled through one writer connection or queue — **[inference]** an acceptable constraint, since per-update work is milliseconds and the 700 ms throttler already serializes bursts; `INSERT ... ON CONFLICT` upserts remove the race class outright.
Multiple JabRef instances: the simplest design gives each instance its own session-scoped DB file (status-quo semantics, zero conflicts); if the bib-field index is made persistent (an explicit improvement enabled by this option — no more full re-index per start), WAL supports multiple same-host processes on one file, degrading to `SQLITE_BUSY`-aware retries — better than the Lucene path, where a second instance silently drops to a read-only fulltext index.
Per-library isolation maps to per-library tables (as today, CUID-named) or per-library files; the xerial build allows up to 125 `ATTACH`ed databases.

### Stale state and packaging-adjacent risks (`stale-state-recovery`, `startup-failure-isolation`)

`sqlite-jdbc` extracts its ~few-MB native library to the OS temp directory on first load ([README](https://github.com/xerial/sqlite-jdbc)).
That is the same *category* of failure as the Postgres `/tmp/embedded-pg` breakage in #15111 — but orders of magnitude smaller surface (one versioned file vs. a full DBMS distribution with symlinked ICU libraries), and it is controllable: `org.sqlite.lib.path`/`org.sqlite.tmpdir` can point at JabRef's own app-data directory, and GraalVM builds can link the library statically at build time.
A load failure surfaces as a catchable Java exception, so JabRef degrades to the in-memory backend instead of failing startup — hence **partial**, with known mitigations, rather than yes.
Index versioning is straightforward: stamp the schema/tokenizer version into `PRAGMA user_version`, delete and rebuild the file on mismatch — trivially satisfiable because the index is a disposable cache.

### Lazy loading and offloading (`lazy-loading`, `derived-value-offloading`)

SQLite is the candidate best aligned with the out-of-heap ambitions of #12708/#10209: it is *designed* as an application file format for desktop programs ([appropriate uses](https://sqlite.org/whentouse.html)), pages data from disk through a small cache (~8 MB default **[claimed in #12708]**), offers rowid random access for lazily filled `TableView`s, and the same file could host derived-value cache tables (`AuthorList` parses, LaTeX-free values).
What it cannot do is host *logic*: there are no stored procedures or server-side views with computation, so LaTeX↔Unicode conversion stays in Java — registered UDFs execute in the JVM anyway, which still saves memory (values stored out-of-heap) but not CPU — hence **partial** for offloading.

## Java integration and packaging

- **Driver**: `org.xerial:sqlite-jdbc`, currently 3.53.2.0 (bundling SQLite 3.53.2; the first three version components track the SQLite version). Dual-licensed Apache-2.0 / BSD-2-Clause ([README](https://github.com/xerial/sqlite-jdbc)).
- **JPMS**: ships a real `module-info` — `module org.xerial.sqlitejdbc { requires transitive java.sql; ... provides java.sql.Driver with org.sqlite.JDBC; }` ([source](https://github.com/xerial/sqlite-jdbc/blob/master/src/main/java9/module-info.java)); the auto-module/jlink problem was fixed in 2022 ([issue #790](https://github.com/xerial/sqlite-jdbc/issues/790), closed 2022-11-11, label "released"). This clears the bar that Lucene 9.0 famously failed (PR [#8362](https://github.com/JabRef/jabref/pull/8362)). `jablib/module-info.java` gains `requires org.xerial.sqlitejdbc;` — and, after the full migration, *drops* `embedded.postgres`, two Postgres binary modules, `org.postgresql.jdbc`, and `org.tukaani.xz`.
- **Java 24 native access**: JNI from a named module triggers the Java 24 restricted-native-access warning ([issue #1289](https://github.com/xerial/sqlite-jdbc/issues/1289)); the fix is appending `org.xerial.sqlitejdbc` to the `--enable-native-access=` list that JabRef already maintains in `jabgui/build.gradle.kts` and `jablib/build.gradle.kts` (one-line change, verified in this repo).
- **GraalVM native image**: supported "out of the box starting from version 3.40.1.0", with bundled reachability metadata; the native library can be exported at build time via `org.sqlite.lib.exportPath` for faster startup ([README](https://github.com/xerial/sqlite-jdbc)). This makes index-backed search available to the `jabkit` native image — something the Postgres backend can never offer (jabkit's `reachability-metadata.json` today contains no postgres/lucene entries because jabkit hard-codes the in-memory path).
- **Platform matrix** (native libraries verified in the repository): Windows x86/x86_64/armv7/**aarch64**; macOS x86_64/**aarch64**; Linux glibc x86/x86_64/arm/**aarch64**/riscv64/ppc64 plus musl variants; FreeBSD; Android ([repo tree](https://github.com/xerial/sqlite-jdbc/tree/master/src/main/resources/org/sqlite/native), [README](https://github.com/xerial/sqlite-jdbc)). Every JabRef target — including the linux-arm64 combination that shipped broken with Postgres ([#14783](https://github.com/JabRef/jabref/issues/14783)) and Apple Silicon (which fell back to emulated x64 Postgres) — is covered natively. A pure-Java fallback exists for unsupported platforms.
- **Size**: the all-platform jar is 13.9 MB; since 3.53.0.0 per-platform classifier jars are published, so jpackage installers can ship only their own platform's native library ([Maven Central](https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc), [releases](https://github.com/xerial/sqlite-jdbc/releases)). Net installer effect after removing Postgres: **[inference]** roughly −35 MB compressed / −500 MB unpacked storage (#11803's "only 500 MB additional storage" remark), +≤14 MB.
- **Alternative integration path**: [`io.roastedroot:sqlite4j`](https://github.com/roastedroot/sqlite4j) is a pure-Java port of sqlite-jdbc that runs SQLite compiled to WebAssembly via the Chicory runtime — zero native code, sandboxed, GraalVM-friendly ([Quarkus blog](https://quarkus.io/blog/sqlite4j-pure-java-sqlite/), [InfoQ](https://www.infoq.com/articles/sqlite-java-integration-webassembly/)). **[inference]** Not the primary recommendation (younger project, JNI is faster), but a credible hedge if JNI-in-JPMS friction or native-crash concerns materialize.

## Operational footprint

- **Startup**: opening a SQLite database is opening a file; cold-start figures discussed in #12708 are 50–150 ms **[claimed in #12708]**, plus a one-time native-library extraction. Compare the embedded-Postgres path: ~12 s before "Postgres server started" in a real startup log **[JabRef-measured, #14970]**.
- **RAM**: page cache defaults to ~2 MB–8 MB per database (tunable via `PRAGMA cache_size`); #12708 estimates 10–30 MB total at 100k+ entries vs. 100–300 MB for embedded Postgres **[claimed in #12708, not independently verified]**. There are no background worker processes.
- **Disk**: index file sized by content; bib-field EAV + trigram index for a 100k-entry library is estimated in the tens-to-low-hundreds of MB **[inference; must be measured]**. The library binaries are ≤14 MB.
- **Processes/ports/sockets**: none. No listener, no localhost socket, nothing for antivirus/firewall heuristics to flag — eliminating a *theoretical* objection to Postgres (no field reports of firewall blocks exist, but spawning a server from temp dirs is exactly the pattern endpoint-protection products dislike **[inference]**).
- **Crash behavior**: the engine dies with the JVM; transactions are atomic and the database self-recovers from the WAL on next open ([WAL docs](https://sqlite.org/wal.html)). No orphaned processes after `kill -9` (the #12844 class disappears by construction), no exception storms from a dead server connection (the #12190-follow-up class disappears: there is no connection to lose).
- **Multiple instances**: session-scoped per-process DB files conflict-free by construction; shared persistent files possible via WAL on the same host (WAL does not work over network filesystems — acceptable, since the index is a local cache).
- **Idle**: zero CPU; SQLite executes only when called.

## Performance evidence

**JabRef-measured (none of it is SQLite — that is itself a finding):**

- PR [#15385](https://github.com/JabRef/jabref/pull/15385) contributes the JMH benchmark *harness* (`search()` and `index()` over the Lucene linked-files path) but **zero measured numbers**, and its fixture is a single ~243 KB PDF with known fidelity caveats (CWD-dependent path, mock allocation inside the timed loop). It defines the vehicle a SQLite prototype must plug into, nothing more.
- Comparison context from the Postgres incumbent: initial indexing of 6.5k entries ≈ 8% CPU, single updates ≤ 0.3% (#12190); ~12 s to "Postgres server started" in a slow-startup log (#14970); postgres process pinned at 99.7% CPU under per-keystroke indexing before the debounce fix (#12190) — root cause backend-agnostic.
- JMH numbers posted in #12708 for the *Lucene* candidate (trigram substring 33k–42k ops/s; `RegexpQuery` ~6,300 ops/s on 5k entries) set the bar a SQLite prototype should be benchmarked against. **[JabRef-measured, for Lucene]**

**External:**

- 18.2 M rows of short strings: unindexed substring `LIKE` 1.75 s → 10–30 ms with an FTS5 trigram table (50–100×); index cost: +2.4 GiB on a 1.3 GiB table, +1.5 GiB with `detail='none'`; index build ~191 s / ~145 s ([andrewmara.com](https://andrewmara.com/blog/faster-sqlite-like-queries-using-fts5-trigram-indexes)).
- 1.73 M patent records: `LIKE` queries of 10–30 s replaced by FTS5 at ~3 ms ([PatentLLM blog](https://media.patentllm.org/blog/ai/nemotron-fts5-patent-speedup)).
- SQLite generally: handles databases up to TB scale with no administration; write transactions take milliseconds ([whentouse.html](https://sqlite.org/whentouse.html)).

**Inference:** JabRef's bib-field workload (≤ ~2 M EAV rows for the largest known real library, 134k entries) is 10× below the external benchmarks' scale; substring query latency will be low single-digit milliseconds. The unproven paths are (a) regex throughput via the Java UDF at 100k entries, (b) trigram index size/build time over multi-GB PDF text, (c) end-to-end reindex time for 10k–100k entries — all three are exactly the open benchmark items #12708 lists.

## Licensing and distribution

- **SQLite**: public domain — "Anyone is free to copy, modify, publish, use, compile, sell, or distribute … for any purpose, commercial or non-commercial" ([copyright page](https://sqlite.org/copyright.html)). No attribution, registration, or fees; unconditionally compatible with JabRef's MIT distribution.
- **sqlite-jdbc**: Apache-2.0 (with a BSD-2-Clause portion) ([repo](https://github.com/xerial/sqlite-jdbc)) — both already common in JabRef's dependency set.
- Redistribution inside installers on all platforms is unrestricted; per-platform classifier jars keep each installer minimal. `req~search.backend.license-compatibility~1` is met without reservation.

## Maintenance and ecosystem

- **SQLite itself**: developed continuously since 2000; the developers commit to supporting it **through 2050** with a frozen, cross-platform on-disk format ([lts.html](https://sqlite.org/lts.html)); the US Library of Congress lists the format as recommended for digital preservation. Testing rigor is exceptional: 100% MC/DC branch coverage, four independent harnesses, millions of fuzz mutations daily ([testing.html](https://sqlite.org/testing.html)). Caveat: SQLite is "open-source, not open-contribution" — a small fixed team funded via the SQLite Consortium; JabRef could not upstream fixes, only report bugs ([copyright.html](https://sqlite.org/copyright.html)). **[inference]** For a dependency this stable, that is a tolerable bus-factor trade; the public-domain code could be forked by anyone in extremis.
- **sqlite-jdbc**: roughly monthly releases tracking each SQLite release (3.51.0.0 Nov 2025 … 3.53.2.0 Jun 2026, [releases](https://github.com/xerial/sqlite-jdbc/releases)); self-described as "maintained, but not being actively developed" — i.e., stable plumbing, not a moving target. Single-maintainer-led (Taro L. Saito) with broad community use; the Quarkus/Chicory `sqlite4j` fork demonstrates the ecosystem has redundancy.
- **Ecosystem fit**: SQLite is the storage layer of the major desktop reference managers — Zotero (`zotero.sqlite`, "the database containing the majority of your data", [Zotero docs](https://www.zotero.org/support/zotero_data)), Citavi, and Mendeley Desktop — and was the SQL candidate shortlisted in JabRef's own ADR-0010. Zotero's documented scaling pain past ~30k items is in its *word-tokenized* full-text cache, not in SQLite's relational core or in a trigram index (see Performance evidence). JDBC means the team's existing SQL code, tests (`SearchQuerySQLConversionTest`), and habits transfer; no new query paradigm to learn (unlike Lucene analyzers).

## Migration effort

The `SearchBackend` bridge (verified: `jablib/src/main/java/org/jabref/logic/search/SearchBackend.java`, swapped live by `SearchContext`) means no GUI- or consumer-side rework: a `SqliteSearchBackend` is a fourth implementor. Concrete work items:

1. **Dependency + packaging** (S): add `org.xerial:sqlite-jdbc` (per-platform classifiers), `requires org.xerial.sqlitejdbc`, extend `--enable-native-access`, verify jpackage installers on win/mac/linux × x64/arm64 and the jabkit native image.
2. **Bib-field indexer port** (S): `BibFieldsIndexer` schema and DML are plain SQL; replace Postgres `ON CONFLICT` syntax (SQLite has it), create the FTS5 trigram table + sync.
3. **Query-compiler dialect** (M): a `SearchToSqliteVisitor` mapping `ILIKE`→trigram `MATCH`/`LIKE`, `~`/`~*`→`REGEXP` UDF, keeping the CTE structure; must pass `GrammarBasedSearchRuleTest`/`SearchQueryTest` semantics suites. The <3-char fallback and case-sensitive verify-filter live here.
4. **Highlighting in Java** (S): replace `regexp_mark`/`regexp_positions` SQL calls in `jabgui/.../search/Highlighter.java` with a Java implementation (benefits all backends; removes the unconditional Postgres start in `Launcher`).
5. **Indexed regex pre-filter** (M, optional but recommended): literal-extraction + trigram candidate pre-filter to approach pg_trgm performance; ship the naive UDF first, benchmark, then decide.
6. **PDF fulltext on FTS5** (M–L, second stage per `req~search.backend.migration-process~1`): page-per-row table, snippets, mtime-incremental updates, `PRAGMA user_version` versioning, preference gating — replaces `DefaultLinkedFilesIndexer`/`LinkedFilesSearcher` and finally retires Lucene (and with it the dual-stack inconsistency of #14569).
7. **Benchmarks** (S): SQLite variants of the #15385 JMH harness; the 10k–100k reindex measurement demanded in #12708.

**Estimate: M overall for the bib-field backend (items 1–5), L for the complete consolidation including PDF fulltext (items 1–7).**
Justification: items 1–4 are a dialect port of code that already exists twice (SQL and in-memory compilers prove the grammar layer is backend-neutral); the maintainers sized the comparable Lucene→Postgres fulltext migration at ~40 person-days per master student (#12261), and item 6 is of that order. The risk-bearing items are 5 and 6; everything else is mechanical.

## Risks and open questions

1. **Regex performance is unproven** (top risk). The naive `REGEXP` UDF is an unindexed scan; whether that is acceptable at 100k entries — and whether a hand-built trigram pre-filter reaches pg_trgm/Lucene-NGram levels — must be benchmarked *before* the ADR. This was the recorded reason for choosing Postgres; regressing it would repeat the #11803 churn in reverse.
2. **PDF-fulltext index size and build time.** Trigram indexes cost ~1–2× the indexed text **[cited]**; a 2 GB PDF corpus could mean several GB of index. Mitigations (`detail='none'`, indexing transformed text only) trade away snippet quality. Needs measurement against the same corpus sizes that broke Lucene indexing (#13048: 17k files).
3. **Sub-3-character and short-CJK queries** silently leave the indexed path. Functionally correct via fallback scan, but the fallback must be written, tested, and benchmarked; 2-character Chinese author names are a realistic workload (#9605).
4. **Fuzzy search and relevance ranking** are not provided; if the team upgrades these "should" requirements to "must", Lucene (or a hybrid) wins those line items.
5. **JNI crash blast radius**: a native SQLite fault crashes the JVM, where out-of-process Postgres would merely lose search and pure-Java Lucene would throw. SQLite's testing record makes this remote **[cited: testing.html]**; `sqlite4j` (Wasm) is the documented hedge.
6. **Single-writer discipline**: the current five-thread indexing pipeline must be re-architected onto one writer queue; otherwise `SQLITE_BUSY` errors replace the old duplicate-key races instead of fixing them.
7. **Driver governance**: sqlite-jdbc is maintenance-mode with a concentrated maintainer; low risk (stable API, forks exist) but worth recording in the ADR.
8. **Open question — one technology or two?** The competing #12708 recommendation (Lucene for all search + SQLite for relational storage) keeps two technologies but gets analyzer-grade normalization, fuzzy, and ranking "for free". SQLite-only is simpler operationally and in packaging; Lucene+SQLite is stronger linguistically. The decisive inputs are the regex benchmark (item 1) and whether `req~search.backend.fuzzy-search~1`/`relevance-ranking~1` stay "should".
9. **Open question — persistent vs. session-scoped bib index.** SQLite makes a persistent bib-field index cheap (single file + `user_version`), which would fix the rebuild-on-every-start cost — but requires a `.bib`-changed-on-disk detection story before it can be enabled.

## Sources

JabRef repository and tracker (verified in-repo where stated): requirements doc `docs/requirements/search-backend.md`; `SearchBackend.java`, `SearchContext.java`, `PostgreServer.java`, `BibFieldsIndexer.java`, `SearchToSqlVisitor.java`, `Highlighter.java`, `module-info.java`, `versions/build.gradle.kts`, `jabkit .../reachability-metadata.json`; issues/PRs [#12708](https://github.com/JabRef/jabref/issues/12708), [#11803](https://github.com/JabRef/jabref/pull/11803), [#12261](https://github.com/JabRef/jabref/issues/12261), [#15385](https://github.com/JabRef/jabref/pull/15385), [#11542](https://github.com/JabRef/jabref/pull/11542), [#8362](https://github.com/JabRef/jabref/pull/8362), [#10209](https://github.com/JabRef/jabref/issues/10209), [#10490](https://github.com/JabRef/jabref/issues/10490), [#11798](https://github.com/JabRef/jabref/issues/11798), [#11823](https://github.com/JabRef/jabref/issues/11823), [#12167](https://github.com/JabRef/jabref/issues/12167), [#12190](https://github.com/JabRef/jabref/issues/12190), [#12685](https://github.com/JabRef/jabref/issues/12685), [#12844](https://github.com/JabRef/jabref/issues/12844), [#13048](https://github.com/JabRef/jabref/issues/13048), [#14569](https://github.com/JabRef/jabref/issues/14569), [#14783](https://github.com/JabRef/jabref/issues/14783), [#14970](https://github.com/JabRef/jabref/issues/14970), [#15111](https://github.com/JabRef/jabref/issues/15111), [#8626](https://github.com/JabRef/jabref/issues/8626), [#9605](https://github.com/JabRef/jabref/issues/9605), [ungerts' BibTeXIndexer gist](https://gist.github.com/ungerts/e347bc3a486833139da2cee5c25df88d).

External:

- <https://sqlite.org/fts5.html> — FTS5 tokenizers (incl. trigram options, 3-char minimum, indexed LIKE/GLOB), `highlight()`/`snippet()`/`bm25()`, external-content tables, custom-tokenizer C API
- <https://sqlite.org/releaselog/3_34_0.html> — FTS5 trigram support, released 2020-12-01
- <https://sqlite.org/lang_expr.html> — `REGEXP` operator has no default implementation
- <https://sqlite.org/wal.html> — WAL crash safety, single writer, same-host multi-process, no network filesystems
- <https://sqlite.org/whentouse.html> — desktop application file format, single-writer suitability
- <https://sqlite.org/copyright.html> — public domain; open-source, not open-contribution
- <https://sqlite.org/lts.html> — support through 2050, stable cross-platform file format
- <https://sqlite.org/testing.html> — 100% MC/DC coverage, test harness scale
- <https://github.com/xerial/sqlite-jdbc> — license, platform matrix, native-library loading, GraalVM native-image support since 3.40.1.0, version scheme
- <https://github.com/xerial/sqlite-jdbc/blob/master/src/main/java9/module-info.java> — explicit JPMS module `org.xerial.sqlitejdbc`
- <https://github.com/xerial/sqlite-jdbc/issues/790> — module-info/jlink issue, closed "released" 2022-11-11
- <https://github.com/xerial/sqlite-jdbc/issues/1289> — Java 24 native-access warning
- <https://github.com/xerial/sqlite-jdbc/blob/master/Makefile> — compile flags (`SQLITE_ENABLE_FTS5`, `SQLITE_MAX_ATTACHED=125`, `SQLITE_ENABLE_DBSTAT_VTAB`, no ICU)
- <https://github.com/xerial/sqlite-jdbc/releases> — release cadence, per-platform classifier jars since 3.53.0.0
- <https://central.sonatype.com/artifact/org.xerial/sqlite-jdbc> — artifact sizes (13.9 MB all-platform jar)
- <https://andrewmara.com/blog/faster-sqlite-like-queries-using-fts5-trigram-indexes> — 18.2 M-row trigram benchmark (10–30 ms; index size overhead)
- <https://media.patentllm.org/blog/ai/nemotron-fts5-patent-speedup> — 1.73 M-record FTS5 case study
- <https://github.com/roastedroot/sqlite4j>, <https://quarkus.io/blog/sqlite4j-pure-java-sqlite/>, <https://www.infoq.com/articles/sqlite-java-integration-webassembly/> — pure-Java (Wasm/Chicory) SQLite alternative
- <https://www.zotero.org/support/zotero_data> — Zotero stores its data in `zotero.sqlite`

<!-- markdownlint-disable-file MD013 -->
