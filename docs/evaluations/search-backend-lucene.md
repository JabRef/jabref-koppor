---
parent: Evaluations
---
# Apache Lucene as search backend

This document evaluates **Apache Lucene** as the single backend for JabRef's search within a library — both bib-field search and fulltext search of linked PDF files.
It unpacks the compressed arguments from issue [#12708](https://github.com/JabRef/jabref/issues/12708) against the requirements in [Search backend](../requirements/search-backend.md).

Throughout this document, claims are labeled as **[repo]** (verified in this repository), **[JabRef-measured]** (numbers produced in JabRef's context, e.g., JMH runs reported in #12708), **[cited]** (external source, URL given), or **[inference]** (the author's reasoning).

## Verdict

Lucene is the strongest candidate on every *operational* requirement: it is pure Java, in-process, port-free, license-clean, JPMS-ready, already shipped in every JabRef distribution, and it eliminates the entire external-process failure class of the embedded PostgreSQL backend (orphaned processes, unpack failures, startup cost, platform-binary matrix) *by design*.
It is also the only candidate with built-in fuzzy matching, relevance scoring, highlighting, and per-page PDF fulltext — three of which JabRef lost or never had under Postgres.
The honest case against it is equally specific: Lucene's token-oriented model does not natively provide the deterministic substring, whole-value regex, and case-sensitive semantics that JabRef's grammar guarantees and that caused Lucene's removal as bib-field backend in PR [#11803](https://github.com/JabRef/jabref/pull/11803).
All of these gaps have known analyzer-level mechanisms (trigram fields, untokenized keyword fields, dual-case fields), but they were *asserted with small-scale benchmarks, not proven at 100k-entry/17k-PDF scale*, and they cost index size.
Net assessment: Lucene **passes or can pass** the large majority of requirements; the residual risk is concentrated in three benchmarkable questions (NGram index size at scale, whole-value regex semantics parity, fulltext substring cost) and one packaging question (GraalVM native image for `jabkit`).

## Technology overview

Apache Lucene is "a high-performance, full-featured search engine library written entirely in Java" maintained as an Apache Software Foundation top-level project; it is the indexing core underneath Elasticsearch, OpenSearch, and Apache Solr ([lucene.apache.org](https://lucene.apache.org/core/), [cited]).
It is an embeddable *library*, not a server: the application opens a `Directory` (on disk or in memory), writes `Document`s through an `IndexWriter`, and queries through an `IndexSearcher`; everything runs inside the host JVM.

JabRef already embeds Lucene 10.4.0 today **[repo]**:

- Dependency declarations: `lucene-core`, `lucene-analysis-common`, `lucene-highlighter`, `lucene-queries`, `lucene-queryparser` (`versions/build.gradle.kts`, `val lucene = "10.4.0"`).
- Linked-PDF fulltext indexing: `jablib/src/main/java/org/jabref/logic/search/sqlbased/indexing/DefaultLinkedFilesIndexer.java` maintains a persistent per-library `FSDirectory` index (one Lucene document per PDF page, produced by `DocumentReader`), updated incrementally via stored file-modification times, with near-real-time visibility through a `SearcherManager`.
- Query side: `LinkedFilesSearcher` (`MultiFieldQueryParser` over `content`/`annotations`, leading wildcards allowed, `Integer.MAX_VALUE` hits) and `SearchToLuceneVisitor` compiling JabRef's ANTLR grammar to Lucene syntax.
- A `uses org.apache.lucene.codecs.lucene104.Lucene104Codec` declaration and `--enable-native-access=...,org.apache.lucene.core` in the build — i.e., JPMS service wiring and FFM access are already solved for packaged builds.

"Lucene as search backend" would mean: add a fourth `SearchBackend` implementor (`LuceneSearchBackend`) that indexes bib fields into a second Lucene index per library (alongside the existing PDF index), with a purpose-built analyzer chain — LaTeX→Unicode `MappingCharFilter`, `ICUTokenizer`, `ICUFoldingFilter`, plus trigram (`NGramTokenFilter(3,3)`) and untokenized "keyword" field variants — and a visitor that compiles the ANTLR query tree into programmatic `Query` objects (never user-visible Lucene syntax).
The embedded PostgreSQL server and its six platform-binary artifacts would be removed from the search path.

## Requirement coverage

| Requirement (short title) | ID | Verdict | Notes |
|---|---|---|---|
| Query grammar, backend-independent semantics | `req~search.backend.query-grammar~1` | yes | Compile ANTLR tree to programmatic `Query` objects; `SearchToLuceneVisitor` exists [repo]; acceptance = v5.x test suites |
| Deterministic substring matching | `req~search.backend.substring-search~1` | partial | Trigram fields (`NGramTokenFilter(3,3)`) = pg_trgm-equivalent; JMH-backed at 5k entries; index-size at 100k unbenchmarked |
| Literal terms without escaping | `req~search.backend.literal-special-characters~1` | yes | Programmatic `TermQuery` construction; never route user input through the classic `QueryParser` (the #11798 failure mode) |
| Case-insensitive + case-sensitive matching | `req~search.backend.case-sensitivity~1` | partial | Dual-field indexing (lowercased + original-case); analyzer-determined, not a Lucene limitation; costs index size; unimplemented |
| Backend-resolved regex search | `req~search.backend.regex-search~1` | partial | `RegexpQuery` DFA over untokenized whole-value fields; Lucene `RegExp` dialect ≠ Java/POSIX regex — semantics parity must be tested |
| Diacritic/LaTeX/transliteration normalization | `req~search.backend.term-normalization~1` | yes | `MappingCharFilter` + `ICUFoldingFilter` + `ASCIIFoldingFilter`; Lucene's strongest area; identical chain at index and query time |
| Person-name normalization | `req~search.backend.name-normalization~1` | partial | Custom `TokenFilter`s feasible; requirement scope itself unconfirmed |
| Non-Latin scripts, opt-in romanization | `req~search.backend.non-latin-scripts~1` | yes | Unicode-native; `ICUTokenizer` segments CJK; folding is opt-in per analyzer; per-field/per-language analyzers via `PerFieldAnalyzerWrapper` |
| Fuzzy search | `req~search.backend.fuzzy-search~1` | yes | `FuzzyQuery` (Levenshtein automaton, edit distance 1–2) built in; only grammar wiring missing; unique among candidates |
| Anchored prefix / position-aware matching | `req~search.backend.prefix-and-position~1` | yes | `PrefixQuery`/anchored `RegexpQuery` on untokenized fields; first-author via dedicated field from the existing `AuthorList` split |
| Exact match in multi-value fields | `req~search.backend.multi-value-fields~1` | yes | Repeated document fields (one per author/keyword); `TermQuery` on untokenized instances |
| Any-field scope incl. keywords, excl. groups | `req~search.backend.any-field-scope~1` | yes | Catch-all field or `BooleanQuery` fan-out excluding `groups`; application-controlled |
| Indexing of derived/resolved values | `req~search.backend.derived-values~1` | yes | Backend-agnostic: indexer feeds resolved values (dates, crossref, entry type) into documents, as `BibFieldsIndexer` does today |
| Fulltext search of linked files | `req~search.backend.fulltext-search~1` | yes | **Incumbent, shipped**: page-wise documents, page numbers, highlighter snippets [repo] |
| Fulltext substring + regex semantics | `req~search.backend.fulltext-substring-regex~1` | partial | Needs trigram/analyzer rework of the PDF index; index-size blow-up on book-length PDFs unbenchmarked; today only manual wildcards (PR #15241) |
| Fulltext indexing optional/toggleable | `req~search.backend.fulltext-optional~1` | yes | Shipped: `shouldFulltextIndexLinkedFiles` gate [repo] |
| Complete result sets | `req~search.backend.complete-results~1` | yes | `Integer.MAX_VALUE` hits requested today [repo]; the historic `maxHits = 5` was application code, not Lucene |
| Automatic index updates | `req~search.backend.auto-index-update~1` | yes | `updateDocument`/`deleteDocuments` incremental; NRT visibility; shipped for PDFs, proven for bib fields in PR #11542 |
| Index lifecycle notifications | `req~search.backend.change-notifications~1` | yes | Backend-agnostic: `IndexManager` event-bus posts exist [repo] |
| Single-entry match check | `req~search.backend.single-entry-check~1` | yes | `entryId` filter clause, or `MemoryIndex` (purpose-built single-document matching) |
| Backend-provided highlighting | `req~search.backend.highlighting~1` | yes | `lucene-highlighter` already used for PDF snippets; would *fix* the current GUI→Postgres `regexp_mark` coupling |
| One isolated index per open library | `req~search.backend.per-library-isolation~1` | yes | One `Directory` per library (shipped for PDFs); `ByteBuffersDirectory` for unsaved libraries |
| Relevance scoring/ranking | `req~search.backend.relevance-ranking~1` | yes | BM25 scoring native; restores the match-score column lost in #11803 |
| Index-backed query speed at 100k+ | `req~search.backend.query-speed~1` | partial | Inverted index + trigram fields; JabRef JMH numbers only up to 5k entries; external evidence at Wikipedia scale |
| Debounced index maintenance | `req~search.backend.debounced-updates~1` | yes | Backend-agnostic; 700 ms `DelayTaskThrottler` exists [repo]; Lucene NRT updates are cheap (sub-ms claim) |
| Bounded search-group re-queries | `req~search.backend.bounded-group-requeries~1` | yes | Application-level batching; backend-agnostic |
| Very large libraries (134k+, 2 open) | `req~search.backend.large-libraries~1` | partial | Index is disk-based/mmap, low heap; but Lucene alone does not move `BibEntry` objects out of the heap |
| Near-real-time freshness | `req~search.backend.near-real-time~1` | yes | `SearcherManager` NRT shipped [repo]; nightly NRT benchmarks exist upstream |
| Cancellable background indexing | `req~search.backend.background-indexing~1` | yes | Shipped: `BackgroundTask` integration, `closeAndWait` [repo] |
| Lazy loading / out-of-heap entries | `req~search.backend.lazy-loading~1` | partial | Stored fields/doc values can back lazy views, but Lucene is not a relational system of record; #12708 pairs it with SQLite |
| Derived-value computation offloading | `req~search.backend.derived-value-offloading~1` | partial | Can *store* derived values (LaTeX-free columns equivalent); cannot *compute* them backend-side (no stored procedures/views) |
| Streaming indexing / byte-offset retrieval | `req~search.backend.streaming-indexing~1` | yes | Document-at-a-time streaming; compatible with the byte-offset indexer (store key+offset+length as fields) |
| Reproducible JMH benchmarks | `req~search.backend.benchmarks~1` | yes | Lucene is the only backend with a JMH harness today (PR #15385), with known fidelity caveats |
| No startup delay | `req~search.backend.startup-time~1` | yes | Library call, no process spawn/unpack; opening an index is milliseconds-scale [inference, supported by #12708 figures] |
| Incremental startup indexing | `req~search.backend.incremental-startup~1` | yes | Shipped for PDFs (mtime diff); persistent bib-field index possible (Lucene indexes are durable) |
| Low resource consumption | `req~search.backend.resource-consumption~1` | yes | No idle daemon; CPU only during indexing bursts; PDF-extraction cost (#9491) is PDFBox, backend-agnostic |
| Small disk footprint | `req~search.backend.disk-footprint~1` | yes | ~7.6 MB of jars (measured) vs. ~85 MB Postgres binary jars / ~500 MB unpacked; NGram fields inflate *index* size (open item) |
| Zero administration | `req~search.backend.zero-administration~1` | yes | Automatic segment merging; no vacuum/tuning; `forceMerge` optional |
| One engine per JabRef instance | `req~search.backend.single-instance-per-process~1` | yes | One in-JVM engine, one `Directory` per library |
| Native on win/mac/linux, x64+arm64 | `req~search.backend.cross-platform~1` | yes | Pure Java — no platform binaries to ship, miss, or emulate; runs wherever JDK 21+ runs |
| In-process operation | `req~search.backend.in-process~1` | yes | The headline argument: a library, not a process |
| No network ports | `req~search.backend.no-network-ports~1` | yes | File I/O only; fully offline |
| jlink/jpackage/JPMS packaging | `req~search.backend.packaging~1` | yes | Proper modules since 9.1; shipped in all JabRef jlink distributions today [repo]; PR #8362 is the proof and the cautionary tale |
| Headless and CLI operation | `req~search.backend.headless-operation~1` | partial | JVM-mode `jabkit`/`jabsrv`: fine. GraalVM native image: needs metadata/substitutions or keeping the in-memory backend in `jabkit` |
| Portable, copyable data files | `req~search.backend.portable-data~1` | yes | A closed index directory is plain files; copyable/shippable |
| .bib as single source of truth | `req~search.backend.source-of-truth~1` | yes | Shipped model: index = rebuildable cache; versioned directories |
| No orphaned processes | `req~search.backend.no-orphan-processes~1` | yes | Nothing survives the JVM; eliminates #12844 by design |
| Startup-failure isolation | `req~search.backend.startup-failure-isolation~1` | yes | In-process exceptions are catchable; worst case: delete + rebuild index; no binary-unpack step (#15111 class eliminated) |
| Stale-state recovery | `req~search.backend.stale-state-recovery~1` | yes | Versioned index dirs + delete-and-rebuild shipped [repo]; OS releases native file locks on process death |
| Versioned indexes, automatic rebuild | `req~search.backend.index-versioning~1` | yes | Shipped: `lucene/<VERSION>` dirs + `clearOldSearchIndices` [repo]; matches Lucene's one-way major-version compatibility |
| Graceful degradation | `req~search.backend.graceful-degradation~1` | yes | No separate process to die; `ReadOnlyLinkedFilesIndexer` fallback exists [repo]; no connection-storm failure mode |
| Race-free concurrent writes | `req~search.backend.concurrent-writes~1` | yes | Single `IndexWriter` serializes; `updateDocument` is atomic delete+add; no unique-constraint races |
| Robust bulk indexing, index health | `req~search.backend.robust-bulk-indexing~1` | partial | History of app-level lifecycle bugs on Lucene's watch (#11374 lock, #8626 silent caps, #13048 abort); engineering needed regardless of backend |
| Multiple concurrent JabRef instances | `req~search.backend.concurrent-instances~1` | partial | On-disk index is single-writer; second instance degrades to read-only (shipped behavior); no port conflicts |
| Tolerance of malformed BibTeX | `req~search.backend.malformed-bibtex~1` | yes | Parsing precedes indexing; backend-agnostic |
| License-compatible, free | `req~search.backend.license-compatibility~1` | yes | Apache-2.0; already redistributed in JabRef installers today |
| Single storage technology | `req~search.backend.single-technology~1` | partial | One engine for *all search* (bib + PDF), removing Postgres; relational needs (abbreviations, metadata) still want SQLite → two technologies |
| Architectural decision record | `req~search.backend.decision-record~1` | yes | Process requirement; this evaluation is an input to the ADR |
| Pluggable behind `SearchBackend` | `req~search.backend.pluggable~1` | yes | Bridge exists [repo]; `LuceneSearchBackend` becomes the fourth implementor |
| Query-syntax stability | `req~search.backend.query-syntax-stability~1` | yes | JabRef grammar stays the user syntax; conditional on *not* repeating #11542's exposure of Lucene syntax |
| Extensibility beyond search | `req~search.backend.extensibility~1` | partial | No relational tables; but native KNN vector search (HNSW, quantized formats in 10.4) is a concrete AI/RAG asset |
| Staged, bounded migration | `req~search.backend.migration-process~1` | yes | Incumbent for PDFs → migration is incremental, not big-bang; fits ~40-person-day project scoping |

### Prose on the non-trivial verdicts

**Query grammar (`query-grammar`, yes) and literal terms (`literal-special-characters`, yes).**
The 2024 failure (#11798: `Law:2009The` misparsed as field query `law:2009The`; #11823: space-means-OR) happened because PR #11542 exposed Lucene's *classic query parser syntax* to users.
The requirement explicitly forbids that, and nothing forces it: a Lucene backend can construct `BooleanQuery`/`TermQuery`/`RegexpQuery` objects directly from JabRef's ANTLR tree, so user input is always literal data and `AND`-conjunction, negation, and parentheses are implemented by the visitor, not by Lucene's parser.
Today's `SearchToLuceneVisitor` still emits classic-syntax *strings* (escaped via `QueryParser.escape`) for the fulltext path [repo] — that is the piece to replace, and the v5.x grammar test suites are the acceptance gate.

**Substring search (`substring-search`, partial) — the requirement that killed Lucene in 2024.**
Lucene's inverted index matches *tokens*, so "query must be tokenized like the index" (#12261) made `contains` semantics fragile, and leading/double wildcards were ruled out as default because unindexed leading wildcards degenerate to term-dictionary scans.
The mechanism that fixes this is the same one Postgres uses: index character trigrams.
A field analyzed with `NGramTokenFilter(3,3)` ([Javadoc](https://lucene.apache.org/core/10_4_0/analysis/common/org/apache/lucene/analysis/ngram/NGramTokenFilter.html), [cited]) lets any substring query be answered as a conjunction of trigram lookups — conceptually identical to pg_trgm's GIN index.
[JabRef-measured]: 33,000–42,000 substring ops/s on 100–5,000 entries, ~6× faster than a regex fallback ([#12708 comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3952949233)).
What is *not* yet measured is the trigram index size and rebuild time at 100k entries — explicitly listed as an open benchmark in #12708.
Verdict "partial" because the mechanism is demonstrated but unproven at JabRef's target scale.

**Regex search (`regex-search`, partial).**
Lucene's `RegexpQuery` compiles the pattern to a deterministic automaton intersected with the term dictionary, "enumerated in an intelligent way, to avoid comparisons" ([Javadoc](https://lucene.apache.org/core/10_4_0/core/org/apache/lucene/search/RegexpQuery.html), [cited]).
Two consequences matter.
First, semantics: a `RegexpQuery` matches *one indexed term entirely*. To reproduce Postgres's `~`/`~*` (regex anywhere in the whole field value, across spaces), the field value must additionally be indexed as a single untokenized term ("keyword field"); the regex then runs over whole values, and an unanchored search becomes `.*pattern.*`.
This also *helps* `prefix-and-position`: against whole-value terms, `RegexpQuery` is implicitly anchored, so "field begins with" is the cheap case — inverting the #10490 complaint that `^` anchors were unsupported.
Second, dialect: Lucene's `RegExp` class implements its own operator set (no backreferences, no lookaround, optional extensions), which is *not* java.util.regex or POSIX.
Saved search-group regexes written against the Postgres/Java flavor may behave differently; parity must be tested query-corpus-wide, or regex evaluation must fall back to trigram-prefiltered Java-regex verification (trigram index narrows candidates, `java.util.regex` confirms — exactly pg_trgm's strategy).
[JabRef-measured]: `RegexpQuery` `.*comput.*` ≈ 6,300 ops/s on 5,000 entries ([#12708 comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3952949233)).
koppor's recorded skepticism ("Lucene is not good in both of this", [#12708 comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3949776169)) stands until the parity tests exist; the original #11803 show-stoppers ("RegEx performance and wrong matches") are attributed in #12708 to analyzer-level choices (EdgeNGram prefix-only tokenization, one-way folding), and LoayGhreeb's confirmation is still outstanding.

**Case sensitivity (`case-sensitivity`, partial).**
"Lucene doesn't support case-sensitive searches" (#11803 review) is true only of an analyzer chain containing `LowerCaseFilter`.
The mechanism is dual-field indexing: each searchable value is indexed twice (folded and original-case); `=!`/`==!` route to the case-preserving field.
This is standard Lucene practice but roughly doubles the term volume for affected fields and is not implemented anywhere in JabRef today — including the PDF index, where the requirement notes the gap.

**Term normalization (`term-normalization`, yes) — Lucene's strongest card.**
The analyzer pipeline is exactly the tool for "Düsseldorf = Duesseldorf = Dusseldorf = `D\"{u}sseldorf`": a `MappingCharFilter` rewrites LaTeX escapes to Unicode before tokenization; `ICUFoldingFilter` applies Unicode accent/diacritic/case folding to NFKC ([Javadoc](https://lucene.apache.org/core/10_4_0/analysis/icu/org/apache/lucene/analysis/icu/ICUFoldingFilter.html), [cited]); a `GermanNormalizationFilter`-style step folds `ue`→`u` for the transliteration variant.
Because the same chain runs at query time, `Kepes` finds `Képes` (#12685) without the query parser ever seeing LaTeX.
#12261 correctly notes this is "as hard in Postgres as in Lucene" as an *application* problem — but Lucene ships the building blocks (JabRef's old `LatexAwareAnalyzer` already reused them, and koppor proposed reusing `ASCIIFoldingFilter` in [#9605](https://github.com/JabRef/jabref/issues/9605)), whereas SQL backends need the folding re-implemented in Java or in custom tokenizers.
Note `lucene-analysis-icu` adds a dependency on ICU4J — which JabRef already ships (icu4j 72.0.1, ~14 MB, measured in the Gradle cache) [repo]; version alignment with Lucene's required ICU version must be checked.

**Fuzzy search (`fuzzy-search`, yes) and relevance ranking (`relevance-ranking`, yes).**
`FuzzyQuery` ([Javadoc](https://lucene.apache.org/core/10_4_0/core/org/apache/lucene/search/FuzzyQuery.html), [cited]) builds a Levenshtein automaton (edit distance ≤ 2) and intersects it with the term dictionary — no candidate SQL backend has an indexed equivalent without extensions.
BM25 scoring restores the "uncertain matches" column whose loss koppor called "a bit a pity" in #11803.
Both need grammar/UI wiring; neither needs new backend machinery.

**Fulltext substring/regex (`fulltext-substring-regex`, partial).**
The shipped PDF index uses `EnglishAnalyzer` (Porter stemming) [repo], which is why `dorf` does not find `Düsseldorf` in PDFs (#14569); the merged mitigation (PR #15241) only passes user wildcards through.
The Lucene fix is the same trigram/ICU analyzer as for bib fields — but PDF content is megabytes per file, and trigram-indexing book-length text multiplies index size; LoayGhreeb explicitly said substring over linked files "should not be implemented with Lucene [wildcards]" without benchmarks.
[Inference]: this is the single largest unquantified cost of the Lucene option; the decision needs an index-size/latency benchmark on a realistic PDF corpus (e.g., the 17,000-file library from #13048).

**Single-entry check (`single-entry-check`, yes).**
Two mechanisms: a `BooleanQuery` with an `entryId` filter clause (mirror of the SQL rewrite), or Lucene's `MemoryIndex` ([Javadoc](https://lucene.apache.org/core/10_4_0/memory/org/apache/lucene/index/memory/MemoryIndex.html), [cited]), which is purpose-built for matching one document against many queries — a good fit for search-group evaluation during entry editing.

**Concurrent instances (`concurrent-instances`, partial).**
A Lucene index directory has exactly one writer (`write.lock`).
The shipped behavior — second instance opens the PDF index read-only via `ReadOnlyLinkedFilesIndexer` [repo] — satisfies the "no port/process conflicts" core of the requirement but leaves the second instance unable to update the shared index.
Postgres sidesteps this with per-instance servers at the price of per-instance processes; an in-process design can mitigate via per-instance index copies or single-writer hand-off, both engineering work.
[Inference]: acceptable degradation, not full parity.

**Large libraries and lazy loading (`large-libraries`/`lazy-loading`/`derived-value-offloading`, partial).**
Lucene's index lives on disk and is accessed via mmap with modest heap needs, so *search* scales; but #12708's RAM-ceiling goal (entries paged out of the JVM heap, derived-value caches in the backend) is a *storage* problem.
Lucene can store derived values (e.g., LaTeX-free text) as stored fields/doc values but cannot compute them backend-side; the #12708 recommendation therefore pairs Lucene with SQLite as system of record.
Choosing Lucene answers the search question, not the storage question — by design, not by defect.

## Java integration and packaging

- **Library, not driver.** Plain Maven Central artifacts; no JNI, no native libraries, no socket protocol. JabRef calls Lucene classes directly (as `DefaultLinkedFilesIndexer` does today) [repo].
- **JPMS.** Since 9.1, "Lucene JARs are now proper Java modules, with module descriptors and dependency information" ([release notes 9.1](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=199538057), [cited]). JabRef's `module-info.java` already `requires` the four Lucene modules and declares `uses ...Lucene104Codec` for the ServiceLoader-based codec discovery [repo]. PR [#8362](https://github.com/JabRef/jabref/pull/8362) is the historical proof that pre-9.1 Lucene broke jlink builds (`ServiceConfigurationError`) and that since 9.1 it works — and the standing lesson that packaged-binary testing on real installers is mandatory.
- **Java version.** Lucene 10.x requires Java 21+ ([system requirements](https://lucene.apache.org/core/10_0_0/SYSTEM_REQUIREMENTS.html), [cited]); JabRef builds on Java 24 [repo]. Lucene 11 is planned to require Java 25 ([apache/lucene#14229](https://github.com/apache/lucene/issues/14229), [cited]) — compatible with JabRef's aggressive JDK adoption.
- **FFM/native access.** Lucene 10's `MMapDirectory` uses the Foreign Function & Memory API; JabRef already passes `--enable-native-access=...,org.apache.lucene.core` [repo].
- **GraalVM native image.** The open question. `jabkit`'s native image today contains *no* Lucene reachability metadata because its `search` command uses the in-memory backend [repo]. Making Lucene mandatory in `jabkit` would require work: Lucene's `AttributeFactory` uses method handles and `MMapDirectory` needs substitutions or fallback to a heap/`NIOFSDirectory`, as documented by Gunnar Morling's native-image Lucene build ([morling.dev](https://www.morling.dev/blog/how-i-built-a-serverless-search-for-my-blog/), [cited], Lucene 8.x era) and by ongoing native-image failures with Lucene-backed stacks ([oracle/graal#8526](https://github.com/oracle/graal/issues/8526), [cited]). No first-class Lucene native-image support exists upstream. Mitigation: keep `jabkit` on the in-memory backend (status quo), or invest in metadata/substitutions for Lucene 10. [Inference]: budget this as its own work item; do not assume it for free.
- **jpackage/installers.** Already shipped in every jlink distribution (jabgui, jabkit app-image, jabsrv-cli, jabls-cli) via jablib's `module-info` [repo].
- **Platform matrix.** Pure Java: Windows/macOS/Linux on x64 and arm64 are covered by the JVM itself. The #14783 failure class (missing linux-arm64 Postgres binaries; macOS-arm64 x64-emulation fallback) cannot occur.
- **Size impact.** Measured jar sizes (10.4.0, Gradle cache): `lucene-core` 4.6 MB, `analysis-common` 1.7 MB, `queries` 0.5 MB, `queryparser` 0.4 MB, `highlighter` 0.3 MB ≈ **7.6 MB total**; adding `lucene-analysis-icu` costs a few hundred KB plus the already-shipped ICU4J. For comparison, each zonky Postgres binary jar is ~14–15 MB (six artifacts ≈ 85–90 MB in dependencies; ~500 MB unpacked installation was quoted in #11803). Dropping Postgres while keeping Lucene is a clear net installer win.

## Operational footprint

- **Startup.** No process spawn, no binary unpack, no `initdb`. Opening an existing `FSDirectory` index and creating an `IndexWriter` are file operations; #12708's comparison places in-process engines at ~50–150 ms cold start vs. 500–2000 ms (observed up to 12 s in #14970) for embedded Postgres [cited from discussion]. The bib-field index can additionally be made *persistent*, turning per-start full re-indexing (today's Postgres `DROP SCHEMA` design) into an mtime check — directly serving `req~search.backend.incremental-startup~1`.
- **RAM.** Index structures are mmap-backed off-heap; JVM heap holds writer buffers (configurable, default tens of MB) and per-query transient state. No resident daemon memory (Postgres-era measurements: ~35–56 MB RSS per server process plus shared buffers, #12190/#12167).
- **Disk.** Index size depends on analyzers: plain inverted index over bib fields is small (a 6.0-era in-memory rebuild was instant enough to ship); trigram and dual-case fields multiply term volume — the headline open benchmark. Installer footprint shrinks by the Postgres binaries.
- **Ports/processes/sockets.** None. Search works fully offline; nothing for institutional environments to forbid. The antivirus/firewall surface of spawning an unpacked server binary from a temp directory disappears; Lucene only ever does file I/O in JabRef's own data directory. (No antivirus field reports exist against embedded Postgres either — the gain is the removal of an attack/false-positive surface, not a fix of observed incidents.)
- **Crash behavior.** The index dies and recovers with the JVM. Lucene writes are transactional at commit points; a crash mid-write loses at most uncommitted changes, and the index remains readable (worst case: delete + rebuild, which the versioned-cache design already supports). `kill -9` leaves no orphan (#12844 class), no stale `/tmp` unpack state (#15111 class). The known sharp edge is the `write.lock` file: within one JVM, double-opening is an application bug (#11374); across crashed processes, OS-native locks are released on process death.
- **Multiple instances.** No port allocation, no per-instance servers. Shared on-disk index degrades the second instance to read-only (shipped `ReadOnlyLinkedFilesIndexer`) — see requirement prose above.
- **Idle behavior.** Zero background CPU; merges run only when the application writes. The #12190 keystroke-storm incident was an unthrottled event pipeline, "backend-agnostic" per the analysis in that issue; debouncing (700 ms throttler) is already in place [repo].

## Performance evidence

**JabRef-measured** (JMH, posted in [#12708](https://github.com/JabRef/jabref/issues/12708#issuecomment-3952949233); synthetic bib entries; small scale):

- Trigram substring queries (`NGramTokenFilter(3,3)`): 33,000–42,000 ops/s across 100–5,000 entries; ~6× faster than the regex fallback.
- `RegexpQuery` `.*comput.*` on 5,000 entries: ~6,300 ops/s (sub-millisecond per query).
- Claimed NRT characteristics: sub-millisecond `updateDocument()`, low-single-digit-ms reader reopen ([#12708 comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759)).

**JabRef benchmark infrastructure** (PR [#15385](https://github.com/JabRef/jabref/pull/15385)): contributes JMH `search()`/`index()` entry points for the Lucene linked-files path in `jablib/src/jmh/.../Benchmarks.java` — *it contains no measured numbers*, and its fixture (one ~243 KB PDF, CWD-relative path, Mockito mock inside the timed loop) must be fixed before its output can inform this decision. It does make Lucene the only backend with a measurement vehicle today.

**External** (clearly not JabRef workloads):

- Apache Lucene nightly benchmarks index the full English Wikipedia export nightly and track GB/hour indexing throughput, query latencies, and NRT reopen latency (reader reopened once per second under a concurrent update stream) — evidence that the engine's headroom is orders of magnitude above JabRef's 100k-entry scale ([benchmarks.mikemccandless.com](https://benchmarks.mikemccandless.com/), [indexing](https://benchmarks.mikemccandless.com/indexing.html), [NRT](https://benchmarks.mikemccandless.com/nrt.html), [cited]).
- Lucene 10.4.0 reports 10–15% query-throughput improvement (up to 35% for some query classes) from larger postings block sizes and SIMD usage ([core news](https://lucene.apache.org/core/corenews.html), [cited]).

**What is missing**: trigram-index size and full-reindex time at 10k–100k entries; LaTeX `CharFilter` throughput; substring-over-PDF-fulltext cost. All three are recorded open items in #12708, and `req~search.backend.query-speed~1` explicitly demands benchmarks before presumed-slow defaults ship.

## Licensing and distribution

Lucene is licensed under the Apache License 2.0 ([apache.org](https://www.apache.org/licenses/LICENSE-2.0), [cited]) — free, OSI-approved, redistributable without registration or fees, and permissively compatible with inclusion in JabRef's MIT-licensed distribution (Apache-2.0 obligations: keep license text and NOTICE attributions in the installers).
This satisfies `req~search.backend.license-compatibility~1` not just in theory: JabRef has shipped Lucene in its installers since the 5.x fulltext feature, so the licensing path is exercised practice [repo].
No platform binary redistribution questions arise (pure Java).

## Maintenance and ecosystem

- **Project health.** Apache top-level project with PMC governance. Release cadence is steady: 10.2.2 (June 2025), 10.3.0 (Sep 2025), 10.3.1 (Oct 2025), 10.3.2 (Nov 2025), 10.4.0 (Feb 2026), with parallel 9.12.x maintenance releases ([core news](https://lucene.apache.org/core/corenews.html), [cited]).
- **Bus factor.** Effectively the lowest-risk option available: Lucene underpins Elasticsearch, OpenSearch, and Solr, so multiple companies (Elastic, Amazon, others) fund full-time committers; abandonment risk is negligible compared to single-maintainer wrappers like zonky embedded-postgres. [Inference from project structure; cited project list at [lucene.apache.org](https://lucene.apache.org/), [cited]]
- **Java ecosystem fit.** Native JPMS modules, pure-Java, tracks modern JDKs aggressively (10.x → JDK 21, 11.x → JDK 25 planned, [apache/lucene#14229](https://github.com/apache/lucene/issues/14229)) — matching JabRef's own JDK policy.
- **Upgrade discipline required.** Index formats are read-compatible only with the previous major version via `backward-codecs`, and never forward-compatible ([backward-codecs README](https://github.com/apache/lucene/blob/main/lucene/backward-codecs/README.md), [cited]). JabRef's versioned-index-directory + auto-rebuild scheme [repo] absorbs this, but every Lucene major upgrade forces a user-visible re-index.
- **In-house expertise.** JabRef has run Lucene in production since 5.x, including two GSoC generations of search work; the failure modes (analyzers, locks, versioning) are institutionally known.

## Migration effort

The `SearchBackend` bridge (`jablib/src/main/java/org/jabref/logic/search/SearchBackend.java`) makes this an additive change: build a `LuceneSearchBackend` implementor, prove it against the shared grammar tests, flip the default, then remove the SQL backend. Concretely:

1. **Bib-field analyzer chain** (new): LaTeX→Unicode `MappingCharFilter` (from JabRef's existing LaTeX-to-Unicode tables), `ICUTokenizer`, `ICUFoldingFilter`, plus per-field variants: trigram field, untokenized whole-value field, original-case field. This is the technical heart and the part #11542 got wrong.
2. **Query compiler** (rewrite): replace string-emitting `SearchToLuceneVisitor` with a visitor producing programmatic `Query` trees covering all grammar operators, including `groups != .+`-style pseudo-field tests and the `groups`-exclusion in any-field search. Acceptance: `GrammarBasedSearchRuleTest`/`SearchQueryTest` semantics, currently encoded in `SearchQuerySQLConversionTest`.
3. **Bib-field indexer** (port): re-target `BibFieldsIndexer`'s existing value extraction (resolved fields, `AuthorList`/`KeywordList` splitting, crossref) from SQL rows to Lucene documents; optionally persistent with mtime-based invalidation.
4. **Highlighter decoupling** (required regardless of backend): replace the GUI's raw-SQL calls to Postgres `regexp_mark`/`regexp_positions` with `lucene-highlighter`/Java-side highlighting — this also un-blocks removing the unconditional Postgres start in `Launcher` [repo].
5. **PDF fulltext** (incremental): keep the shipped indexer; later switch its analyzer to the new chain for substring/case parity (benchmark-gated; index version bump triggers rebuild).
6. **Cleanup**: remove `PostgreServer`, the zonky dependency, six binary artifacts, `org.tukaani.xz`, the JDBC driver from the search path; update `module-info`, installers, and docs.
7. **Out of scope but adjacent**: `jabkit` native-image metadata if Lucene becomes mandatory there (today's in-memory path can remain).

**Estimate: M** (roughly 2–3 of the ~40-person-day projects from #12261's calibration).
Justification: not S, because the analyzer design, semantics-parity test work, regex-dialect handling, and the highlighter decoupling are genuinely new engineering with benchmark gates; not L, because the bridge, the PDF indexer, index versioning, event plumbing, background-task integration, and team Lucene experience already exist — the change replaces one implementor and deletes a heavyweight subsystem rather than restructuring the architecture.

## Risks and open questions

1. **Repeating 2024.** Lucene was already tried as bib-field backend and reverted (#11803). The thesis that the show-stoppers were analyzer-level, not engine-level, is plausible and benchmark-supported but remains *unconfirmed by the original author* (LoayGhreeb's rationale is still requested in #12708). The mitigation is the acceptance gate: the full v5.x semantics test suite against the new backend before any default flip.
2. **Trigram index size at scale.** Unmeasured for 100k-entry libraries and for PDF fulltext; could erode the disk-footprint advantage. Open benchmark item in #12708.
3. **Regex dialect parity.** Lucene `RegExp` ≠ Java/POSIX regex; saved search groups using `=~` could silently change meaning. Needs a documented dialect decision (native `RegexpQuery` vs. trigram-prefiltered Java-regex verification) and tests.
4. **GraalVM native image.** Lucene in `jabkit`'s native image is unproven and historically needs substitutions; either keep the in-memory backend there (accepting feature asymmetry: no fulltext in native `jabkit`) or budget the metadata work.
5. **Single-writer index vs. multiple instances.** Read-only degradation for the second instance is acceptable today but is a real functional gap vs. per-instance Postgres servers.
6. **Index-lifecycle robustness.** Lucene's 5.x field history (stale `write.lock` dead-ends, silently incomplete indexes, aborted bulk runs) shows that robustness is application engineering, not an engine property; an index-health UI (`req~search.backend.robust-bulk-indexing~1`) must be part of the plan.
7. **Not a storage answer.** Lucene leaves the RAM-ceiling/lazy-loading/relational-storage goals of #12708 to a companion technology (SQLite per the converged recommendation) — choosing Lucene for search is only half of the architecture decision, and "single technology" is then satisfied only within the search scope.
8. **Major-version re-index churn.** Every Lucene major upgrade invalidates indexes (one-way compatibility); with large PDF collections, the automatic rebuild is hours of background work for users. Mitigation: stay on a major as long as practical; schedule upgrades with releases.

## Sources

- Requirements: `docs/requirements/search-backend.md` (this repo)
- JabRef issue [#12708 — Use a database as a backend](https://github.com/JabRef/jabref/issues/12708), incl. [evaluation comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759) and [JMH benchmark comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3952949233)
- JabRef PR [#11803 — Searching with Postgres](https://github.com/JabRef/jabref/pull/11803); PR [#11542 — Lucene search](https://github.com/JabRef/jabref/pull/11542); PR [#15385 — JMH benchmark harness](https://github.com/JabRef/jabref/pull/15385); PR [#8362 — Lucene 9.x JPMS upgrade](https://github.com/JabRef/jabref/pull/8362); PR [#15241](https://github.com/JabRef/jabref/pull/15241); PR [#15599 — InMemorySearch](https://github.com/JabRef/jabref/pull/15599)
- JabRef issues [#12261](https://github.com/JabRef/jabref/issues/12261), [#14569](https://github.com/JabRef/jabref/issues/14569), [#12685](https://github.com/JabRef/jabref/issues/12685), [#11823](https://github.com/JabRef/jabref/issues/11823), [#11798](https://github.com/JabRef/jabref/issues/11798), [#10490](https://github.com/JabRef/jabref/issues/10490), [#12190](https://github.com/JabRef/jabref/issues/12190), [#12844](https://github.com/JabRef/jabref/issues/12844), [#15111](https://github.com/JabRef/jabref/issues/15111), [#14783](https://github.com/JabRef/jabref/issues/14783), [#13048](https://github.com/JabRef/jabref/issues/13048), [#11374](https://github.com/JabRef/jabref/issues/11374), [#8626](https://github.com/JabRef/jabref/issues/8626), [#9491](https://github.com/JabRef/jabref/issues/9491), [#9605](https://github.com/JabRef/jabref/issues/9605), [#10209](https://github.com/JabRef/jabref/issues/10209)
- Apache Lucene: [project home](https://lucene.apache.org/), [core](https://lucene.apache.org/core/), [core news / releases](https://lucene.apache.org/core/corenews.html), [10.0 system requirements](https://lucene.apache.org/core/10_0_0/SYSTEM_REQUIREMENTS.html), [release notes 9.1 (JPMS)](https://cwiki.apache.org/confluence/pages/viewpage.action?pageId=199538057), [release notes 10.0.0](https://cwiki.apache.org/confluence/display/LUCENE/Release+Notes+10.0.0), [backward-codecs README](https://github.com/apache/lucene/blob/main/lucene/backward-codecs/README.md), [apache/lucene#14229 (Java 25 for Lucene 11)](https://github.com/apache/lucene/issues/14229)
- Lucene Javadoc (10.4.0): [RegexpQuery](https://lucene.apache.org/core/10_4_0/core/org/apache/lucene/search/RegexpQuery.html), [FuzzyQuery](https://lucene.apache.org/core/10_4_0/core/org/apache/lucene/search/FuzzyQuery.html), [NGramTokenFilter](https://lucene.apache.org/core/10_4_0/analysis/common/org/apache/lucene/analysis/ngram/NGramTokenFilter.html), [ICUFoldingFilter](https://lucene.apache.org/core/10_4_0/analysis/icu/org/apache/lucene/analysis/icu/ICUFoldingFilter.html), [MemoryIndex](https://lucene.apache.org/core/10_4_0/memory/org/apache/lucene/index/memory/MemoryIndex.html)
- Benchmarks: [Lucene nightly benchmarks](https://benchmarks.mikemccandless.com/), [indexing](https://benchmarks.mikemccandless.com/indexing.html), [NRT latency](https://benchmarks.mikemccandless.com/nrt.html)
- GraalVM native image and Lucene: [Gunnar Morling — serverless search](https://www.morling.dev/blog/how-i-built-a-serverless-search-for-my-blog/), [oracle/graal#8526](https://github.com/oracle/graal/issues/8526), [Lucene 8.4.1 substitutions gist](https://gist.github.com/TronPaul/008bc72238d0adcfbbcd2c8ea0219eb0)
- Licensing: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- Repository evidence (paths relative to repo root): `jablib/src/main/java/org/jabref/logic/search/SearchBackend.java`, `.../sqlbased/indexing/DefaultLinkedFilesIndexer.java`, `.../sqlbased/retrieval/LinkedFilesSearcher.java`, `.../query/SearchToLuceneVisitor.java`, `.../sqlbased/PostgreServer.java`, `jablib/src/main/java/module-info.java`, `versions/build.gradle.kts`, `jablib/build.gradle.kts`, `jabkit/build.gradle.kts`, `jabkit/src/main/resources/META-INF/native-image/org.jabref/jabkit/reachability-metadata.json`

<!-- markdownlint-disable-file MD013 -->
