---
title: Select search index backend
nav_order: 64
parent: Decision Records
status: proposed
date: 2026-06-12
decision-makers: JabRef maintainers (devcall)
---
# Select the search index backend

## Context and Problem Statement

JabRef's "search within a library" — including fulltext search of linked PDF files — is backed by a local, single-user index that JabRef creates and manages automatically on the user's machine.
Historically this was [Apache Lucene](https://lucene.apache.org/); PR [#11803](https://github.com/JabRef/jabref/pull/11803) replaced the Lucene-based bib-field search with an *embedded PostgreSQL server* (via the testing-oriented `io.zonky.test` wrapper), while Lucene stayed on for linked-PDF fulltext — so the product ships **two** search engines today, plus H2 MVStore for journal abbreviations.
The embedded-PostgreSQL choice was made without an architectural decision record and has since produced a string of operational failures (orphaned processes [#12844](https://github.com/JabRef/jabref/issues/12844), a release that would not start [#15111](https://github.com/JabRef/jabref/issues/15111), unstartable linux-arm64 builds [#14783](https://github.com/JabRef/jabref/issues/14783), exception storms after process death [#12190](https://github.com/JabRef/jabref/issues/12190)); it has already been demoted to an opt-in, default-off setting in favour of an in-memory backend (PR [#15599](https://github.com/JabRef/jabref/pull/15599)).
Issue [#12708](https://github.com/JabRef/jabref/issues/12708) is the long-running discussion on which DBMS/index technology should back JabRef's search, and the fulltext migration in issue [#12261](https://github.com/JabRef/jabref/issues/12261) is explicitly *blocked* on this decision.
This ADR selects the backend, weighing the requirements collected in [Search backend](../requirements/search-backend.md) (the `req~search.backend.*~1` family) against per-option evaluations and the JMH benchmark harness added in PR [#15385](https://github.com/JabRef/jabref/pull/15385).

## Decision Drivers

The requirements document holds the full, sourced list; the drivers that actually separate the options are:

* **Deterministic substring ("contains") search** — `req~search.backend.substring-search~1`. The single requirement that motivated dropping Lucene as bib-field backend; must be index-backed, not tokenizer-dependent.
* **Backend-resolved, ideally index-supported regex** — `req~search.backend.regex-search~1`. The recorded reason for choosing PostgreSQL ("the DBMS does the regex indexing — not the client").
* **In-process operation, no orphan processes, no network ports** — `req~search.backend.in-process~1`, `req~search.backend.no-orphan-processes~1`, `req~search.backend.no-network-ports~1`. The block that the embedded-PostgreSQL architecture fails by construction.
* **No startup delay** — `req~search.backend.startup-time~1`. Time-to-first-window must stay at the 5.x level (1–2 s); backend cold start in tens of milliseconds.
* **Native support on every shipped platform** — `req~search.backend.cross-platform~1` (Windows/macOS/Linux × x64+arm64).
* **Headless / GraalVM-native-image operation** — `req~search.backend.headless-operation~1` (`jabkit`, including its native image, and `jabsrv`).
* **Fulltext search of linked PDFs** — `req~search.backend.fulltext-search~1`, with substring/regex semantics consistent with field search (`req~search.backend.fulltext-substring-regex~1`).
* **Convergence on a single storage technology** — `req~search.backend.single-technology~1`. One engine for bib fields + PDF fulltext (+ ideally other persistence) instead of three.
* **Small disk footprint** — `req~search.backend.disk-footprint~1`. Embedded PostgreSQL added roughly 500 MB unpacked.
* **Index-backed query speed at scale** — `req~search.backend.query-speed~1` (100k+ entries), with reproducible benchmarks (`req~search.backend.benchmarks~1`).
* **License-compatible, free dependencies** — `req~search.backend.license-compatibility~1` (MIT-compatible, redistributable).
* **`.bib` stays the single source of truth; pluggable behind the `SearchBackend` bridge** — `req~search.backend.source-of-truth~1`, `req~search.backend.pluggable~1`. Any index is a rebuildable cache, and backends are interchangeable.

## Considered Options

* **[SQLite with FTS5](../evaluations/search-backend-sqlite.md)** — in-process single-file SQL engine; FTS5 trigram tokenizer for indexed substring search.
* **[Embedded PostgreSQL (the current backend)](../evaluations/search-backend-postgresql.md)** — server process started per JabRef run via the zonky wrapper; `pg_trgm` for substring/regex.
* **[Apache Lucene](../evaluations/search-backend-lucene.md)** — pure-Java embeddable search library; incumbent for PDF fulltext.
* **[In-memory search (no persistent index)](../evaluations/search-backend-in-memory.md)** — grammar walk over the in-heap entries; the current default.

No other option (H2, DuckDB, …) reached the threshold of being argued in more than one source; DuckDB is noted in the requirements as failing `req~search.backend.auto-index-update~1` (no automatic full-text-index maintenance on table changes) at evaluation time.

## Decision Outcome

Chosen option: **SQLite with FTS5 as the target primary indexed backend, with the in-memory backend retained permanently as the zero-dependency fallback and shared-semantics reference.**
Embedded PostgreSQL is to be retired from the default search path, and Lucene kept *only* as the interim PDF-fulltext engine until the SQLite fulltext path is benchmark-proven.

Rationale, grounded in the evaluations:

* SQLite + FTS5 is the only option that can collapse the three current storage technologies into **one** (`req~search.backend.single-technology~1`) while satisfying the entire deployment/reliability block — `in-process`, `no-orphan-processes`, `no-network-ports`, `startup-time`, `cross-platform`, `headless-operation` (it has out-of-the-box GraalVM native-image support, which PostgreSQL can never offer `jabkit`) — *by construction* rather than by mitigation.
* It keeps the requirement that broke Lucene solved: substring search is a **native, indexed** FTS5 trigram capability, mechanically the same plan `pg_trgm` runs — without an analyzer/tokenizer to mirror.
* It cuts the backend's installer contribution from roughly 500 MB to ≈14 MB (`req~search.backend.disk-footprint~1`), is public-domain licensed (`req~search.backend.license-compatibility~1`), ships a real `module-info`, and slots in behind the existing `SearchBackend` bridge as a fourth implementor (`req~search.backend.pluggable~1`).
* PostgreSQL is functionally the strongest engine but **architecturally non-compliant**: four drivers (`in-process`, `no-orphan-processes`, `no-network-ports`, sub-second `startup-time`) are unreachable at any effort level, and the project has already demoted it (#15599). Keeping it would mean approving a permanently non-compliant default.
* Lucene is the strongest on normalization, fuzzy, ranking, and is the shipped fulltext engine, but it was already tried and reverted as bib-field backend (#11803); its token model does not natively give the deterministic substring / whole-value regex / case-sensitive semantics JabRef's grammar guarantees, and it leaves the storage question to a companion technology (so "single technology" is not achieved). It remains the best **fallback** for fulltext and the option to reconsider if fuzzy/ranking become hard requirements.
* In-memory is an excellent floor (it already ships as default and passes every reliability/footprint requirement) but **definitionally violates** `req~search.backend.query-speed~1` (linear scan) and provides **no fulltext at all** — it cannot be the complete answer, only the fallback beside an indexed primary.

This decision is **proposed**, not accepted: it is explicitly gated on two measurements that the evaluations flag as unproven (see Confirmation and More Information). If either gate fails, the outcome changes (see the revisit triggers).

### Consequences

* Good, because the whole external-process failure class (orphaned processes, stale-temp startup blocks, missing platform binaries, post-kill exception storms) disappears by construction, and search becomes available in the `jabkit` native image.
* Good, because the product can converge toward a single search technology and drop ≈500 MB of embedded-PostgreSQL binaries plus the zonky/JDBC/xz dependencies.
* Good, because deterministic substring search — the property the requirements were written around — stays index-backed, and the GUI highlighter can be decoupled from PostgreSQL (it must be reimplemented in Java regardless, which also fixes `req~search.backend.highlighting~1`).
* Bad, because SQLite has **no native indexed regex**: the naive `REGEXP` UDF is an unindexed scan, and matching `pg_trgm`/Lucene regex performance requires hand-building a trigram pre-filter. A preliminary synthetic benchmark (100k entries) measured the naive UDF at ~350 ms/query versus <1 ms once a ≥3-char literal extracted from the regex drives a trigram pre-filter — so the common case (author names, title words) is cheap, but a regex with no extractable literal (e.g. `^.{4}$`, `\d+`) still falls back to the ~350 ms scan, and a backtracking `java.util.regex` pattern can pin the scan thread (ReDoS — an exposure the in-memory backend already shares). A literal-extraction pre-filter **and** a per-query match timeout are therefore required, and the figures must be reproduced in the #15385 JMH harness before acceptance.
* Bad, because **fuzzy search and relevance ranking** are weaker than Lucene's; if these "should" requirements are upgraded to "must", Lucene (or a Lucene+SQLite hybrid) wins those line items.
* Bad, because PDF-fulltext on FTS5 trigram indexes is unbuilt; a preliminary benchmark measured the trigram index at ~2.8× the indexed-text size on bib fields, which extrapolates to multi-GB indexes over large PDF corpora (the open cost). Lucene must stay until that path is proven on a realistic corpus (e.g. #13048's 17k files), so the "single technology" benefit is deferred, not immediate.
* Bad, because a native SQLite fault crashes the JVM (where out-of-process PostgreSQL would only lose search); SQLite's testing record makes this remote, and `sqlite4j` (Wasm) is a documented hedge.

### Confirmation

* OFT requirement tracing: the `req~search.backend.*~1` requirements are traced to the new `SqliteSearchBackend` via the `traceRequirements` Gradle task (`build/reports/tracing.txt`).
* The new backend must pass the shared grammar-semantics suites (`GrammarBasedSearchRuleTest`, `SearchQueryTest`) that every `SearchBackend` implementor is held to (`req~search.backend.query-grammar~1`).
* The PR [#15385](https://github.com/JabRef/jabref/pull/15385) JMH harness (fixture flaws fixed first) is extended with a SQLite variant and re-run to produce the missing numbers: the two benchmark gates below, plus the 10k–100k-entry full-reindex measurement demanded by #12708. Preliminary synthetic microbenchmarks already exist for the central questions (substring `MATCH` ≈ 0.03 ms; naive regex ≈ 350 ms vs trigram-pre-filtered < 1 ms; trigram index ≈ 2.8× indexed text, built in ~4.6 s at 100k entries) — these are MWE figures that the JMH harness must confirm, not replace.

## Pros and Cons of the Options

### SQLite with FTS5

Evaluation: [search-backend-sqlite.md](../evaluations/search-backend-sqlite.md)

* Good, because indexed substring search (FTS5 trigram), in-process, no ports/processes, GraalVM-native-image-ready, real `module-info`, ≈14 MB, public-domain, native libs for every JabRef platform.
* Good, because it is the only candidate that can be the single technology for bib fields + PDF fulltext + relational storage, and makes a persistent bib-field index (mtime-diffed) cheap.
* Neutral, because writes must be funneled through a single writer (WAL allows concurrent readers); acceptable given the existing 700 ms throttle.
* Bad, because regex has no index support without a hand-built trigram pre-filter, and fuzzy/relevance ranking are not provided natively.
* Bad, because PDF-fulltext index size/build time and regex throughput at 100k entries are unproven and must be benchmarked before an accepted ADR.

### Embedded PostgreSQL (current backend)

Evaluation: [search-backend-postgresql.md](../evaluations/search-backend-postgresql.md)

* Good, because it delivers JabRef's exact semantics (deterministic substring, indexed POSIX regex, case-sensitivity, multi-value matching) with `pg_trgm` and zero analyzer tuning, and is the only credible host for the beyond-search ambitions (out-of-heap storage, server-side caches).
* Good, because it is battle-tested: 18 months of shipped bib-field search and hardening fixes.
* Bad, because `in-process`, `no-orphan-processes`, `no-network-ports`, and sub-second `startup-time` are architecturally unreachable — the documented source of the 6.0 operational failures — and it depends on a wrapper its own author calls "intended for testing".
* Bad, because it is the heaviest option (~500 MB unpacked), cannot run in the `jabkit` native image, and has no benchmark at target scale.

### Apache Lucene

Evaluation: [search-backend-lucene.md](../evaluations/search-backend-lucene.md)

* Good, because pure-Java, in-process, port-free, JPMS-ready, already shipped, and the only candidate with built-in fuzzy matching, BM25 ranking, highlighting, and per-page PDF fulltext; strongest on diacritic/LaTeX normalization.
* Neutral, because deterministic substring needs trigram (`NGramTokenFilter`) fields and case-sensitivity needs dual-case fields — demonstrated at 5k entries but unproven at 100k, and costing index size.
* Bad, because it was already reverted as bib-field backend (#11803); its `RegExp` dialect ≠ Java/POSIX (saved `=~` queries could change meaning), and GraalVM native image is unproven.
* Bad, because it is not a storage answer — it leaves relational/large-library storage to a companion technology, so "single technology" holds only within search scope.

### In-memory search (no persistent index)

Evaluation: [search-backend-in-memory.md](../evaluations/search-backend-in-memory.md)

* Good, because it satisfies every deployment, reliability, and footprint requirement by construction (in-process, zero startup, zero disk, no ports, native-image-ready) and is already the shipped default; perfect deterministic substring on bib fields and the shared-semantics reference.
* Neutral, because adoption effort is zero and hardening (Java highlighting, parity fixes) is small.
* Bad, because it answers every query by a linear scan — `req~search.backend.query-speed~1` forbids exactly this — with latency growing into hundreds of milliseconds at 100k entries.
* Bad, because it provides no fulltext search of linked PDFs and no path to lazy loading / out-of-heap storage, so it cannot stand alone as the backend.

## More Information

Open questions and revisit triggers (this ADR is **proposed**; resolving these decides acceptance):

1. **Regex performance gate — *preliminary: measured.*** A synthetic 100k-entry benchmark put the naive `REGEXP` UDF at ~350 ms/query and a trigram-pre-filtered regex at <1 ms. Remaining work: a literal-extraction pre-filter, a ReDoS match-timeout for no-literal/pathological patterns, and reproduction in the JMH harness. *Trigger:* if the pre-filter cannot be made to cover real query patterns, reconsider PostgreSQL's `pg_trgm` (which does literal-prefiltered regex internally) or a Lucene/SQLite hybrid.
2. **PDF-fulltext index gate — *partially measured.*** The FTS5 trigram index measured ~2.8× the indexed-text size on bib fields; the open question is absolute size/build time over a realistic multi-GB PDF corpus (e.g. the 17k-file library from [#13048](https://github.com/JabRef/jabref/issues/13048)), where `detail='none'` is the main mitigation. *Trigger:* if prohibitive, keep Lucene for fulltext (accepting two technologies) rather than migrating #12261 to SQLite.
3. **Are fuzzy and relevance ranking "should" or "must"?** If `req~search.backend.fuzzy-search~1` / `req~search.backend.relevance-ranking~1` are upgraded to hard requirements, Lucene (or Lucene+SQLite) becomes the stronger choice.
4. **Confirm the original Lucene show-stoppers** from PR #11803 (LoayGhreeb's rationale, still outstanding in #12708) — material to whether PostgreSQL's functional edge is as unique as recorded.
5. **Sequencing.** Per `req~search.backend.migration-process~1`, the backend decision (#12708) must precede the fulltext migration (#12261); migrate bib fields first, PDF fulltext second, scoped as bounded (~40 person-day) contributor projects. The in-memory backend stays the fallback throughout.
6. **Author-centric search/management does not need a graph database.** Author queries — first-author (`req~search.backend.prefix-and-position~1`), individual-author match (`req~search.backend.multi-value-fields~1`), co-authorship, "all papers by X" — are expressible in the chosen relational backend via an `authors` + `entry_authors(position)` schema with recursive CTEs, well within SQLite's scale. A dedicated graph database such as **Neo4j is excluded**: its Community Edition is **GPLv3**, incompatible with JabRef's MIT distribution (the ground that excluded GPL options in [ADR 0004](0004-use-mariadb-connector.md) and [ADR 0056](0056-OCR-engine-selection.md)), and embedding it would re-introduce the external-process, footprint, and multi-technology costs this decision removes (`req~search.backend.in-process~1`, `req~search.backend.single-technology~1`). Author *disambiguation* is a normalization concern (`req~search.backend.name-normalization~1`), not a storage one. If a graph view is ever wanted, an in-memory graph (e.g. JGraphT) over the already-loaded entries, or a property-graph layer on SQLite, keeps it license-clean and in-process.

Related decisions: [ADR 0004 (MariaDB connector over GPL MySQL)](0004-use-mariadb-connector.md) and [ADR 0056 (OCR engines excluded on license grounds)](0056-OCR-engine-selection.md) for the dependency-licensing practice behind `req~search.backend.license-compatibility~1`.
