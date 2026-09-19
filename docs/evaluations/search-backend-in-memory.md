---
parent: Evaluations
---
# In-memory search (no persistent index) as search backend

## Verdict

The in-memory backend — a direct walk of the `Search.g4` parse tree over the `List<BibEntry>` already held in the JVM heap, with no index, no storage, and no third-party engine — is JabRef's *shipped default* since PR [#15599](https://github.com/JabRef/jabref/pull/15599).
It is the only option that satisfies **every** deployment, reliability, and footprint requirement *by construction*: in-process, zero startup cost, zero disk, no processes, no ports, no native binaries, trivially GraalVM- and jlink-compatible, and immune to every orphan-process/stale-state/index-corruption failure class documented in the issue tracker.
It is equally the only option that *definitionally violates* the central performance requirement: it answers every query by a linear scan over all fields of all entries (`req~search.backend.query-speed~1` explicitly forbids this), provides **no fulltext search of linked PDFs at all**, and offers no path toward lazy loading or out-of-heap storage (`req~search.backend.lazy-loading~1`), which is the other half of issue [#12708](https://github.com/JabRef/jabref/issues/12708).
Conclusion offered to the ADR: in-memory search is an excellent *floor* — the guaranteed-correct, guaranteed-everywhere fallback and semantics reference that should be kept behind the `SearchBackend` bridge permanently — but it is not a complete *answer* to the backend question, because fulltext search and indexed query speed at 100k+ entries require an index by definition.

Requirement scorecard (62 requirement IDs assessed): **34 yes, 13 partial, 14 no, 1 unknown**. The "no" verdicts cluster exactly where an index is the point: fulltext, normalization, query speed at scale, lazy loading, and backend-hosted caches.

## Technology overview

"In-memory search" is not a DBMS or a library; it is the *absence* of one. The pieces (all already in the repository):

- **`InMemorySearchBackend`** (`jablib/src/main/java/org/jabref/logic/search/inmemory/InMemorySearchBackend.java`) — the GoF-bridge Implementor. All index-lifecycle methods (`addToIndex`, `removeFromIndex`, `updateEntry`, `rebuildFullTextIndex`, `close`) are documented no-ops, because there is no index to maintain.
- **`InMemoryLibrarySearcher`** (same package) — streams `databaseContext.getDatabase().getEntries()` and filters with the visitor below. If a query carries `SearchFlags.FULLTEXT`, it logs a warning and silently degrades to metadata-only matching.
- **`BibEntryMatchVisitor`** — an ANTLR `SearchBaseVisitor<Boolean>` that evaluates the same `Search.g4` grammar tree the SQL backend compiles to SQL, directly against one `BibEntry`: `=`/`!=` via (lower-cased) `String.contains`, `==` via `String.equals`/`equalsIgnoreCase`, `=~` via `java.util.regex` with a static Caffeine-cached pattern map, pseudo-fields `any`/`anyfield`/`key`/`entrytype`/`anykeyword`, `groups` excluded from any-field scans. Operator semantics deliberately mirror `SearchToSqlVisitor`.
- **Shared semantics tests** — `LibrarySearcherTestCases` (jablib test sources) defines implementation-agnostic cases that every `LibrarySearcher` must pass; `InMemoryLibrarySearcherTest` runs them.

How JabRef "embeds" it: there is nothing to embed. The only dependencies exercised are the ANTLR 4 runtime (required by the search grammar for *every* backend) and Caffeine (already a jablib dependency, used here only to cache compiled `Pattern` objects). Consumers today: the GUI default path (`LibraryTab.createSearchContext`), the stand-alone HTTP server (`jabsrv`, which hard-codes it: "Stand-alone HTTP server must never use the Postgres backend"), the `jabkit search` command (including the GraalVM native image), and `jabsrv`'s CAYW endpoint.

One coupling must be named up front because it negates part of this option's benefits *as currently shipped*: `jabgui`'s `Launcher` still constructs `PostgreServer` unconditionally (Launcher.java, ~line 95), and `Highlighter` (jabgui) obtains preview-highlighting via the PostgreSQL functions `regexp_mark`/`regexp_positions` over JDBC — even when the in-memory backend is active. The in-memory option only realizes its zero-footprint promise once highlighting is reimplemented in Java and the unconditional Postgres start is removed.

## Requirement coverage

| Requirement (short title) | ID | Verdict | Notes |
|---|---|---|---|
| Query grammar, backend-independent semantics | `req~search.backend.query-grammar~1` | yes | Same `Search.g4` tree, walked directly; semantics parity with SQL visitor by construction; shared test cases exist |
| Substring ("contains") matching | `req~search.backend.substring-search~1` | partial | Bib fields: perfect (`String.contains`, no tokenizer exists to disagree with). Linked-file fulltext: not available at all |
| Literal terms without escaping | `req~search.backend.literal-special-characters~1` | yes | No native query parser exists that metacharacters could leak into; `Law:2009The` is just a Java string |
| Case-(in)sensitive matching | `req~search.backend.case-sensitivity~1` | yes | `=!`/`==!` map to exact `String` comparisons; honored exactly. Fulltext clause moot (no fulltext) |
| Regex resolved by the backend | `req~search.backend.regex-search~1` | partial | Full `java.util.regex` semantics incl. anchors; but evaluation *is* client-side filtering of all entries, which the requirement explicitly rules out; no index support |
| Term normalization (diacritics/LaTeX) | `req~search.backend.term-normalization~1` | no | Matches raw field values only; no LaTeX-free transformed value is consulted (SQL backend indexes both). Implementable in Java, at per-query CPU cost or via a cache that is an index in disguise |
| Person-name normalization | `req~search.backend.name-normalization~1` | no | Nothing implemented; same Java-side path as above would apply |
| Non-Latin scripts, opt-in romanization | `req~search.backend.non-latin-scripts~1` | yes | UTF-16 `String` comparison matches CJK exactly; no blind folding occurs (which is what the requirement demands); romanization would be backend-independent Java work |
| Fuzzy search | `req~search.backend.fuzzy-search~1` | no | No edit-distance matching; brute-force Levenshtein per entry per query is possible but unindexed |
| Anchored prefix / position-aware matching | `req~search.backend.prefix-and-position~1` | partial | `=~ "^Smith"` works today (java.util.regex supports `^`, unlike Lucene per #10490); first-author shorthand unimplemented but easy — the visitor has full `AuthorList` object access; no index acceleration |
| Exact match in multi-value fields | `req~search.backend.multi-value-fields~1` | partial | `anykeyword` splits via `KeywordList`; `author == Smith` compares the *whole* field value instead of split authors (SQL backend uses a `_split_values` table) — semantics divergence |
| Any-field scope (groups excluded) | `req~search.backend.any-field-scope~1` | yes | `matchAnyField` iterates all fields and skips `groups` |
| Derived/resolved values | `req~search.backend.derived-values~1` | partial | `entrytype` queryable; date aliases (`year` vs `date`) and crossref-resolved values are **not** matched — visitor reads only fields literally present on the entry |
| Fulltext search of linked files | `req~search.backend.fulltext-search~1` | no | By design: logs "In-memory searcher does not support FULLTEXT search" and degrades to metadata-only |
| Fulltext substring/regex semantics | `req~search.backend.fulltext-substring-regex~1` | no | Moot — no fulltext engine exists |
| Fulltext optional/toggleable | `req~search.backend.fulltext-optional~1` | partial | Opt-out trivially satisfied; enabling the preference does nothing (no re-indexing can be triggered) |
| Complete result sets | `req~search.backend.complete-results~1` | yes | Full scan returns every match; no hit caps possible by construction |
| Automatic index updates | `req~search.backend.auto-index-update~1` | yes | Strongest property of this option: there is no index to go stale; every query sees the live entry objects |
| Index lifecycle notifications | `req~search.backend.change-notifications~1` | partial | Emits no `IndexStartedEvent` etc.; met in spirit (results are never stale) but not by the letter — GUI re-evaluation must key off entry-change events instead |
| Single-entry check | `req~search.backend.single-entry-check~1` | yes | `isEntryMatched` walks one entry, O(fields); cheaper than any SQL round trip |
| Backend-provided highlighting | `req~search.backend.highlighting~1` | no | Provides none; GUI `Highlighter` currently calls PostgreSQL `regexp_mark`/`regexp_positions` even when in-memory is active — the documented violation. Fixable in Java via `Matcher` positions |
| Per-library isolation | `req~search.backend.per-library-isolation~1` | yes | One stateless backend object per `LibraryTab`; unsaved libraries fine; only shared state is the static regex cache (harmless) |
| Relevance scoring | `req~search.backend.relevance-ranking~1` | no | Boolean membership only; a single marker `SearchResult` per hit |
| Index-backed query speed (100k+) | `req~search.backend.query-speed~1` | **no** | Definitional violation: the requirement forbids "a linear scan over all fields of all entries", which is exactly what this backend is. See performance evidence |
| Debounced index maintenance | `req~search.backend.debounced-updates~1` | yes | Vacuously: index updates are no-ops, the per-keystroke-write failure mode (#12190) cannot occur; re-*query* throttling lives outside the backend |
| Bounded group re-queries | `req~search.backend.bounded-group-requeries~1` | partial | Backend-agnostic orchestration concern; per-group full re-search costs O(entries) here vs. an indexed lookup elsewhere, so unbatched fan-out hurts this backend most |
| Very large libraries (134k+, two open) | `req~search.backend.large-libraries~1` | partial | Adds *zero* index memory (best in class) but does nothing to relieve the in-heap `BibDatabase` that caused #10209; query latency and per-query GC garbage grow linearly |
| Near-real-time freshness | `req~search.backend.near-real-time~1` | yes | Better than NRT: zero staleness, always — for metadata; the attached-PDF clause is moot |
| Cancellable background indexing | `req~search.backend.background-indexing~1` | yes | Vacuously: no indexing operations exist; nothing can block library opening |
| Lazy loading / out-of-heap storage | `req~search.backend.lazy-loading~1` | **no** | Structurally incompatible: requires the full `List<BibEntry>` materialized in heap; offers no page cache, no disk paging |
| Derived-value offloading | `req~search.backend.derived-value-offloading~1` | no | No backend store exists to host `AuthorList`/LaTeX-free caches or save-path formatting |
| Streaming indexing / byte-offset retrieval | `req~search.backend.streaming-indexing~1` | no | No index at all; moreover the scan needs materialized entries, the opposite of a byte-offset pointer model |
| Reproducible JMH benchmarks | `req~search.backend.benchmarks~1` | partial | jablib JMH harness exists (PR #15385), but its `search()`/`index()` benchmarks exercise only the Lucene linked-files path; no in-memory search benchmark is wired up yet |
| No startup delay | `req~search.backend.startup-time~1` | yes | Zero backend initialization. Caveat: today's GUI still starts Postgres unconditionally for the Highlighter, which erases this benefit until decoupled |
| Incremental startup indexing | `req~search.backend.incremental-startup~1` | yes | Vacuously: nothing to rebuild, ever |
| Low resource consumption | `req~search.backend.resource-consumption~1` | yes | Idle cost is exactly zero; no background work exists. Query-time CPU at scale is covered under `query-speed` |
| Small disk footprint | `req~search.backend.disk-footprint~1` | yes | Zero bytes — vs. ~13–32 MB *per platform* of compressed embedded-Postgres binaries measured in the Gradle cache, and the "~500 MB" unpacked figure noted in PR #11803 |
| Zero administration | `req~search.backend.zero-administration~1` | yes | Nothing exists to vacuum, tune, or configure |
| One engine per JabRef instance | `req~search.backend.single-instance-per-process~1` | yes | There is no engine; per-library searcher objects are plain Java objects, not engine instances — meets the requirement's intent (no per-library server processes) |
| Native on all platforms (x64+arm64) | `req~search.backend.cross-platform~1` | yes | Pure Java; runs wherever the JVM runs; the #14783 missing-binaries failure class cannot exist |
| In-process operation | `req~search.backend.in-process~1` | yes | Definitionally; the requirement's reference fulfilment |
| No network ports | `req~search.backend.no-network-ports~1` | yes | No I/O of any kind |
| jlink/jpackage/JPMS packaging | `req~search.backend.packaging~1` | yes | Ships today as the default in packaged builds; ANTLR runtime and Caffeine are existing `module-info` entries; no ServiceLoader use |
| Headless and CLI operation | `req~search.backend.headless-operation~1` | yes | `jabkit search` and stand-alone `jabsrv` already hard-wire it; jabkit's GraalVM `reachability-metadata.json` needs zero lucene/postgres entries |
| Portable, copyable data files | `req~search.backend.portable-data~1` | partial | No data files exist — nothing to copy, but also nothing with which to deliver pre-built data (a contested, non-core "should") |
| .bib as single source of truth | `req~search.backend.source-of-truth~1` | yes | Purest fulfilment: no cache exists that could diverge, version-skew, or need rebuilding |
| No orphaned processes | `req~search.backend.no-orphan-processes~1` | yes | By design; eliminates the #12844 failure class entirely (the issue itself predicts an in-process solution "would eliminate orphan processes by design") |
| Startup-failure isolation | `req~search.backend.startup-failure-isolation~1` | yes | There is no backend startup that could fail (#15111-class failures impossible) |
| Stale-state recovery | `req~search.backend.stale-state-recovery~1` | yes | No cached state, no unpacked binaries, no temp directories |
| Index versioning + auto rebuild | `req~search.backend.index-versioning~1` | yes | Vacuously: no persistent index, no format version, no migration |
| Graceful degradation | `req~search.backend.graceful-degradation~1` | yes | Cannot "die mid-session"; no connection to lose, no exception storms ("This connection has been closed" ×1000 impossible) |
| Race-free concurrent writes | `req~search.backend.concurrent-writes~1` | yes | No index writes exist; #12167-class duplicate-key races impossible (but see open question on scanning a live entry list) |
| Robust bulk indexing | `req~search.backend.robust-bulk-indexing~1` | no | Moot in the worst way: 17k linked files are never indexed because no fulltext indexing exists |
| Multiple concurrent instances | `req~search.backend.concurrent-instances~1` | yes | Zero shared state between JabRef instances; no ports, no lock files |
| Malformed BibTeX tolerance | `req~search.backend.malformed-bibtex~1` | yes | Searches whatever the importer produced; no separate index to corrupt |
| License-compatible dependencies | `req~search.backend.license-compatibility~1` | yes | Zero new dependencies; ANTLR 4 is BSD-licensed, Caffeine is Apache-2.0 — both already shipped |
| Single storage technology | `req~search.backend.single-technology~1` | partial | Eliminates Postgres but cannot absorb PDF fulltext, so it forces either keeping Lucene alongside or dropping fulltext as a feature |
| Architectural decision record | `req~search.backend.decision-record~1` | unknown | Process requirement, backend-neutral; this evaluation is input to that ADR |
| Pluggable behind stable abstraction | `req~search.backend.pluggable~1` | yes | Is one of the three bridge implementors; live preference swap works today |
| Query-syntax stability | `req~search.backend.query-syntax-stability~1` | yes | Identical user-facing grammar; saved search groups keep working unchanged |
| Extensibility beyond search | `req~search.backend.extensibility~1` | no | No tables, no storage, no multi-client potential, no vector/temporal options |
| Staged, bounded migration | `req~search.backend.migration-process~1` | yes | Process requirement; this option requires near-zero migration (it already ships as default) |

### Mechanisms behind the non-trivial verdicts

**Substring, literal terms, case sensitivity, non-Latin scripts (yes for bib fields).** The mechanism is the *absence of an analyzer*: `value.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))` matches any byte-for-byte substring anywhere in a field value. There is no tokenizer whose query-side mirror could drift (the exact failure that sank Lucene in [#12261](https://github.com/JabRef/jabref/issues/12261): "the query must be tokenized using the same tokenizer as during indexing"), no metacharacter set (so `Law:2009The` from [#11798](https://github.com/JabRef/jabref/issues/11798) needs no escaping), and `xyz AND abc` finds `xyz-abc` ([#11823](https://github.com/JabRef/jabref/issues/11823)) because both terms are substrings of the raw value. CJK works because Java `String` comparison is Unicode-exact and nothing folds anything ([#9605](https://github.com/JabRef/jabref/issues/9605)'s "no blind romanization" is satisfied for free).

**Regex (partial).** `=~` compiles to `java.util.regex.Pattern` (Caffeine-cached across the scan, since one visitor is created per entry) and uses `Matcher.find()`, so anchors (`^`, `$`), lookarounds, and the full Java regex dialect work — strictly richer than POSIX `~`/`~*` in Postgres, and it covers the `^first-author` anchoring gap that Lucene could not express ([#10490](https://github.com/JabRef/jabref/issues/10490)). The "partial" is architectural: `req~search.backend.regex-search~1` demands backend/index-resolved regex "not … client-side filtering of all entries" — fast regex via pg_trgm-style index support was a primary recorded reason for the Lucene→Postgres switch, and this backend cannot offer any index support. There is also a robustness wrinkle: `java.util.regex` is a backtracking engine, so a pathological user pattern (nested quantifiers) can exhibit catastrophic backtracking and stall the scan thread (see OWASP ReDoS); Postgres's regex engine and Lucene's `RegexpQuery` DFA are immune to this class. Invalid patterns are handled (caught `PatternSyntaxException` → no match).

**Term/name normalization (no).** The SQL backend indexes a `field_value_transformed` (LaTeX→Unicode) column next to the literal and queries both; the in-memory visitor consults only `entry.getField(...)` raw values, so `Düsseldorf` is *not* found by `Dusseldorf`, nor a LaTeX-encoded `D\"{u}sseldorf` by either ([#12685](https://github.com/JabRef/jabref/issues/12685)). The fix would be Java-side folding (e.g., the `LatexToUnicodeFormatter` already benchmarked in jablib, plus ASCII folding) applied to *both* value and term at match time — every comparison then pays the conversion cost, or the converted values are cached per entry, at which point one is building an in-memory index and the "no index" simplicity argument starts to dissolve. This is an honest structural weakness, not an oversight.

**Derived values and multi-value exact match (partial).** `BibFieldsIndexer` (SQL path) calls `getResolvedFieldOrAlias` for date fields and `getResolvedFieldOrAliasLatexFree` otherwise, and populates a `_split_values` table from `AuthorList`/`KeywordList`. `BibEntryMatchVisitor` does neither: `year = 2016` misses an entry that only has `date = 2016-07`, crossref-inherited fields are invisible, and `author == Smith` fails against `Smith and Jones`. All of this is *fixable in plain Java* — the same `BibEntry` helper methods are one call away, and the visitor has richer object-model access than any SQL row — but it is unimplemented today and each fix adds per-query CPU.

**Fulltext (no, three requirements).** There is no PDF text extraction, no fulltext store, no page-wise snippets. `SearchFlags.FULLTEXT` is logged and ignored. Any deployment that keeps fulltext search as a feature must pair this backend with a fulltext engine (today: Lucene's `DefaultLinkedFilesIndexer`/`LinkedFilesSearcher` inside `SqlSearchBackend`) — which caps the achievable score on `req~search.backend.single-technology~1` at "partial".

**Query speed and large libraries (no / partial).** Every search and every group re-evaluation is O(entries × fields × value-length). The case-insensitive path additionally allocates a lowercase copy of every scanned field value *per query* — at 134k entries that is on the order of the library's whole text volume in transient garbage per keystroke. Measured magnitudes below.

**Reliability block (yes across the board).** The entire reliability section of the requirements document was reverse-engineered from embedded-Postgres and Lucene field failures: orphaned processes ([#12844](https://github.com/JabRef/jabref/issues/12844)), startup-blocking stale `/tmp/embedded-pg` state ([#15111](https://github.com/JabRef/jabref/issues/15111)), exception storms after process death ([#12190](https://github.com/JabRef/jabref/issues/12190)), write.lock dead-ends ([#11374](https://github.com/JabRef/jabref/issues/11374)), duplicate-key races ([#12167](https://github.com/JabRef/jabref/issues/12167)), index-format version crashes (PR [#8362](https://github.com/JabRef/jabref/pull/8362)). The in-memory backend passes all of them by having no state, no process, and no persistence whose failure could be observed. This is genuine merit, not a technicality — but note these are *vacuous* satisfactions: any future in-process indexed backend (SQLite, Lucene-in-process) eliminates the process-class failures too, while still owning index-lifecycle risk.

## Java integration and packaging

- **Driver/library:** none. The implementation is ~3 classes of plain Java in `jablib`. Dependencies touched: ANTLR 4 runtime (generated `SearchParser`/`SearchBaseVisitor` — needed by every backend, since the user-facing grammar is non-negotiable per `req~search.backend.query-grammar~1`) and Caffeine 3.x for the regex `Pattern` cache.
- **JPMS:** no new modules. `org.antlr.antlr4.runtime` and `com.github.benmanes.caffeine` are already in `jablib/src/main/java/module-info.java`. By contrast, the *current* module graph hard-requires `embedded.postgres` (+ per-arch binary modules + `org.tukaani.xz`) and four Lucene modules into every jlink distribution even though the default backend uses none of them.
- **GraalVM native image:** proven in production. `jabkit search` runs `InMemoryLibrarySearcher` inside the native image; `jabkit/src/main/resources/META-INF/native-image/org.jabref/jabkit/reachability-metadata.json` contains zero Lucene/Postgres entries. This matches GraalVM's documented model: plain, non-reflective Java needs no reachability metadata.
- **jpackage/jlink:** ships today as the default backend of the packaged GUI — packaging compatibility is demonstrated, not predicted.
- **Platform matrix:** identical to the JVM's: Windows/macOS/Linux, x64 and arm64, including legacy macOS. There are no platform binaries that could be missing (#14783) or emulated.
- **Installer size impact:** ±0 MB for the option itself. The *potential* saving if it allowed dropping the SQL stack: the embedded-Postgres binary jars measure 13.5–31.6 MB per platform (xz-compressed; measured in the local Gradle cache for BOM 18.4.0), unpacking to the "~500 MB" storage remarked in PR [#11803](https://github.com/JabRef/jabref/pull/11803#issuecomment-2367741046); the Lucene modules in use total ~7 MB. As long as fulltext search stays, Lucene's ~7 MB remains.

## Operational footprint

- **Startup:** zero. No process spawn, no binary unpack, no initdb, no schema creation. Compare: ~12 s elapsed before "Postgres server started" in the [#14970](https://github.com/JabRef/jabref/issues/14970) startup log. *Caveat (implementation, not option):* `Launcher` still starts `PostgreServer` unconditionally for the Highlighter, so today's users do not yet see this benefit.
- **RAM:** no resident overhead beyond the `BibEntry` objects JabRef holds anyway. Transient: the case-insensitive scan lower-cases every field value per query (allocation ≈ library text volume per query; short-lived, GC-collected). No off-heap, no page cache — which is also why it cannot help #10209's heap problem (1.6–2.4 GB with 45k/134k-entry libraries open).
- **Disk:** zero bytes; nothing under AppData, nothing in `/tmp`.
- **Ports/processes/sockets:** none. Nothing for a firewall to ask about, nothing for institutional no-child-process policies to forbid, nothing for antivirus to scan or quarantine (the tracker shows no AV/firewall reports even against embedded Postgres, but the architectural exposure simply does not exist here).
- **After crash / kill -9:** nothing survives, because nothing exists outside the JVM. No cleanup, no PID files, no watchdogs (the Windows-failing pipe-EOF watchdog of #12844 is unnecessary).
- **Multiple simultaneous instances:** trivially safe — no shared files, no locks, no port allocation. (Contrast: Lucene's on-disk PDF index degrades the second instance to read-only; each Postgres instance spawns another server process.)
- **Idle behavior:** exactly zero CPU; cost is incurred only while a query or group re-evaluation runs.

## Performance evidence

**JabRef-measured (PR [#15385](https://github.com/JabRef/jabref/pull/15385)):** none for this backend. That PR contributes JMH *harness* code only — `search()`/`index()` benchmarks for the **Lucene linked-files** path against a single ~243 KB PDF — and contains no measured numbers at all for any backend; its fixture flaws (CWD-relative PDF path, Mockito mock creation inside the timed loop) are themselves recorded as `req~search.backend.benchmarks~1`. No JMH benchmark for `InMemoryLibrarySearcher` exists yet (verified in `jablib/src/jmh/java/org/jabref/benchmarks/Benchmarks.java`). This is a gap this evaluation cannot fill with authoritative numbers.

**Informal measurement made for this evaluation** (synthetic, single dev machine — Linux x64, OpenJDK 21, `-Xmx2g`; standalone program mirroring `BibEntryMatchVisitor`'s match kernels: per-value `toLowerCase().contains()` and pre-compiled `Matcher.find()`; 8 fields/entry incl. an 80-word abstract; 10 repetitions after warmup):

| Library size | Total field text | Contains scan | Regex scan |
|---|---|---|---|
| 10,000 entries | ~11 MB | ~12 ms | ~21 ms |
| 100,000 entries | ~109 MB | ~135 ms | ~202 ms |

Treat these as **lower bounds**: the real implementation additionally allocates one ANTLR visitor per entry and walks the parse tree per entry, accesses fields through `Optional`/stream plumbing, and splits keywords — a small multiple of these times is plausible. Interpretation (inference, not measurement): at ≤10k entries the scan is comfortably inside search-as-you-type budgets; at 100k entries, ~0.15–0.5+ s per query × (1 query + N group re-evaluations) per keystroke approaches or exceeds interactive budgets and produces ~100 MB of transient garbage per scan.

**JabRef-adjacent numbers from the decision thread ([#12708](https://github.com/JabRef/jabref/issues/12708), comparison points, not in-memory measurements):** Lucene JMH microbenchmarks reported 33k–42k ops/s for trigram substring queries and ~6,300 ops/s for `.*comput.*` regex on 5,000 entries — i.e., an indexed engine answers in sub-millisecond time what the scan does in milliseconds-to-hundreds-of-milliseconds, with the gap growing linearly in library size. Postgres field data (#12190): initial indexing of 6.5k entries ~8% CPU; per-update <0.3% — the embedded-DB CPU incident was an unthrottled-pipeline bug, not query cost.

**External evidence:** there is no third-party engine to benchmark. The relevant external fact is qualitative: `java.util.regex` is a backtracking NFA engine, so worst-case matching time is super-linear for pathological patterns (OWASP ReDoS); engines with DFA construction (Lucene `RegexpQuery`, RE2-class engines) give linear-time guarantees the scan cannot.

## Licensing and distribution

The option adds no dependencies, so the licensing position is inherited and clean:

- ANTLR 4 (grammar runtime): BSD license ([antlr.org/license.html](https://www.antlr.org/license.html)) — MIT-compatible; required by all backends anyway.
- Caffeine (regex cache): Apache-2.0 ([github.com/ben-manes/caffeine](https://github.com/ben-manes/caffeine)) — MIT-compatible; already a jablib dependency.

No registration, fees, or redistribution constraints; nothing platform-specific to redistribute. `req~search.backend.license-compatibility~1` is satisfied trivially — and an in-memory-only distribution would *remove* the PostgreSQL-licensed binaries and Apache-2.0 Lucene jars from the licensing surface rather than add anything.

## Maintenance and ecosystem

- **Bus factor / project health:** the "ecosystem" is the JDK plus two of the healthiest libraries in the Java world. ANTLR 4's latest release is 4.13.2 (Aug 2024; mature, slow-cadence project — [releases](https://github.com/antlr/antlr4/releases)); Caffeine released 3.2.4 in May 2026 and is actively maintained ([repo](https://github.com/ben-manes/caffeine)). Neither is a search-specific risk: if either stagnated, the backend would be unaffected (Caffeine is replaceable by a `ConcurrentHashMap` in an afternoon).
- **Code ownership:** all logic is first-party JabRef code (~3 classes + shared tests). No upstream release can break index formats, change analyzer behavior, or force re-index migrations. The flip side: every capability gap (normalization, fuzzy, fulltext) must be built and maintained by JabRef itself, with no upstream community contributing tokenizers, codecs, or fixes.
- **Fit:** maximal — it is plain Java in the project's own style, testable with plain JUnit, debuggable without any infrastructure.

## Migration effort

**Effort to adopt: none.** It is already merged (PR [#15599](https://github.com/JabRef/jabref/pull/15599), 2026-05-18), shipped, and the default (`usePostgresSearch=false` in `SearchPreferences`). `jabkit`, stand-alone `jabsrv`, and the GUI default path already run on it.

**Effort to make it a *complete* answer for its scope** (i.e., close the parity gaps that are fixable without an index), estimated **S–M**:

1. Java-side highlighting (replace `Highlighter`'s SQL calls to `regexp_mark`/`regexp_positions` with `java.util.regex.Matcher` positions) and removal of the unconditional `PostgreServer` start in `Launcher` — **S**; prerequisite for *any* non-Postgres future, and the single change that actually realizes the zero-footprint promise.
2. Parity fixes in `BibEntryMatchVisitor`: date-alias/crossref resolution via `getResolvedFieldOrAlias*`, split-value exact matching via `AuthorList`/`KeywordList`, optional LaTeX-free/ASCII folding for term normalization — **S–M**; pure Java, well-tested helper methods exist, but folding needs a per-entry cache to stay fast.
3. JMH benchmark for `InMemoryLibrarySearcher` at 1k/10k/100k entries (fills the `benchmarks~1` gap and validates the scan-budget inference above) — **S**.

**What it can never deliver regardless of effort:** PDF fulltext search, index-backed query speed at 100k+ entries, lazy loading/out-of-heap storage, backend-hosted derived-value caches, relevance ranking with acceptable cost. Closing those means adding an indexed engine next to it — at which point the in-memory backend's role is "reference semantics + guaranteed fallback", not "the backend".

**Overall S/M/L verdict: S** for adoption (done) and hardening; **not applicable** for the gaps, which are out of scope for this technology by definition rather than expensive.

## Risks and open questions

1. **Scaling cliff is silent.** No user-visible signal exists when a library crosses the size where scan latency degrades typing/group responsiveness; unlike Postgres-era bugs, it would manifest as diffuse sluggishness. Needs the JMH benchmark plus a documented size guidance (inference: comfortable ≤10k, suspect ≥50–100k).
2. **Fulltext silently degrades.** A FULLTEXT-flagged query logs a warning and returns metadata-only results — users may believe their PDFs contain no matches (`req~search.backend.complete-results~1` is honored for metadata but the UX resembles silent incompleteness). The GUI should surface "fulltext unavailable with this backend".
3. **Concurrent modification during scan.** `getMatches` streams the live entry list; mutation from another thread (imports, fetchers) during a scan could throw `ConcurrentModificationException` or yield torn results. Not observed in the tracker yet; worth a targeted test. (Own inference from code reading.)
4. **ReDoS via user regex.** A pathological `=~` pattern can pin the search thread through catastrophic backtracking (OWASP). Mitigations: match timeout via watchdog, or bounded-input checks. Postgres/Lucene-DFA backends did not share this exposure.
5. **Normalization economics.** Term/name normalization done per-comparison is O(scan) extra CPU; done with caching it re-creates an index. If #12685-class expectations are firm requirements, this option's "no index" premise erodes.
6. **Hidden Postgres coupling.** Until `Highlighter`/`Launcher` are decoupled, every claimed footprint advantage of the default backend is fictional in the shipped GUI — users still pay process spawn, binary unpack, and orphan-process risk for highlighting only.
7. **Open question for the ADR:** is in-memory the *floor* (kept as fallback/reference next to an indexed primary — the role it plays well) or the *default* long-term? The answer hinges on the unresolved #12708 decision for fulltext and large-library storage, by which #12261 is explicitly blocked.

## Sources

Repository (verified at commit b29f1291ec, 2026-06-12): `jablib/src/main/java/org/jabref/logic/search/inmemory/{InMemorySearchBackend,InMemoryLibrarySearcher,BibEntryMatchVisitor}.java`, `jablib/src/main/java/org/jabref/logic/search/{SearchBackend,SearchContext,SearchPreferences}.java`, `jablib/src/main/java/org/jabref/logic/search/sqlbased/PostgreServer.java`, `jablib/src/main/java/org/jabref/logic/search/sqlbased/indexing/BibFieldsIndexer.java`, `jabgui/src/main/java/org/jabref/{Launcher,gui/search/Highlighter,gui/LibraryTab}.java`, `jabkit/src/main/java/org/jabref/toolkit/commands/Search.java`, `jabkit/src/main/resources/META-INF/native-image/org.jabref/jabkit/reachability-metadata.json`, `jablib/src/jmh/java/org/jabref/benchmarks/Benchmarks.java`, `jablib/src/main/java/module-info.java`, `CHANGELOG.md`.

- Requirements: [docs/requirements/search-backend.md](../requirements/search-backend.md)
- <https://github.com/JabRef/jabref/issues/12708> — backend decision discussion (drivers, evaluation, JMH comparison numbers)
- <https://github.com/JabRef/jabref/pull/11803> — Lucene→Postgres switch; "~500 MB" disk remark
- <https://github.com/JabRef/jabref/pull/15599> — "Add InMemorySearch"; in-memory becomes default
- <https://github.com/JabRef/jabref/pull/15385> — JMH harness (no measured numbers)
- <https://github.com/JabRef/jabref/issues/12261> — fulltext migration; Lucene "contains" tokenization problem
- <https://github.com/JabRef/jabref/issues/12844>, <https://github.com/JabRef/jabref/issues/15111>, <https://github.com/JabRef/jabref/issues/12190>, <https://github.com/JabRef/jabref/issues/12167>, <https://github.com/JabRef/jabref/issues/14783>, <https://github.com/JabRef/jabref/issues/14970>, <https://github.com/JabRef/jabref/issues/15866> — embedded-Postgres operational failures
- <https://github.com/JabRef/jabref/issues/11823>, <https://github.com/JabRef/jabref/issues/11798>, <https://github.com/JabRef/jabref/issues/10490>, <https://github.com/JabRef/jabref/issues/14569>, <https://github.com/JabRef/jabref/issues/12685>, <https://github.com/JabRef/jabref/issues/9605>, <https://github.com/JabRef/jabref/issues/8626>, <https://github.com/JabRef/jabref/issues/13048>, <https://github.com/JabRef/jabref/issues/11374>, <https://github.com/JabRef/jabref/issues/9491> — search-semantics and index-lifecycle evidence
- <https://github.com/JabRef/jabref/issues/10209> — large-library memory measurements
- <https://github.com/JabRef/jabref/pull/8362> — Lucene/JPMS packaging history
- <https://gist.github.com/ungerts/e347bc3a486833139da2cee5c25df88d> — byte-offset indexer prototype (streaming-indexing requirement)
- <https://www.antlr.org/license.html> — ANTLR BSD license
- <https://github.com/antlr/antlr4/releases> — ANTLR 4.13.2 (Aug 2024)
- <https://github.com/ben-manes/caffeine> — Caffeine Apache-2.0, v3.2.4 (May 2026)
- <https://owasp.org/www-community/attacks/Regular_expression_Denial_of_Service_-_ReDoS> — backtracking-regex ReDoS
- <https://www.graalvm.org/latest/reference-manual/native-image/metadata/> — native image: plain Java needs no reachability metadata

<!-- markdownlint-disable-file MD013 -->
