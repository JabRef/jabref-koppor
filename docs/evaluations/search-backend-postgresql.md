---
parent: Evaluations
---
# PostgreSQL (embedded server; current backend) as search backend

## Verdict

Embedded PostgreSQL is functionally the strongest search engine among the candidates: it is the only option that delivers JabRef's exact search semantics (deterministic substring, POSIX regex, case-sensitivity, multi-value field matching) **with index acceleration and zero analyzer tuning**, it has carried production bib-field search for ~18 months, and its SQL/stored-procedure surface uniquely serves the aspirational requirements (derived-value offloading, out-of-heap entry storage, extensibility).
However, it **architecturally fails** a block of hard deployment and reliability requirements — `in-process`, `no-orphan-processes`, `no-network-ports`, `startup-time`, `stale-state-recovery`, `disk-footprint` — and these failures are inherent to running a separate OS process unpacked from a testing-oriented wrapper, not bugs that can be fixed.
Choosing this option means formally waiving or rewriting those requirements; on the requirements as written, embedded PostgreSQL cannot be made compliant.

The strongest honest case **for**: every functional search requirement that has actually bitten users (#11823, #11798, #10490, #14569) is solved or solvable with stock SQL plus `pg_trgm`, with no analyzer engineering, and the engine doubles as the only credible host for the project's beyond-search ambitions (out-of-heap storage, server-side derived-value caches, shared-library tech alignment).
The strongest honest case **against**: the entire operational failure record of the 6.0 line that is attributable to the search backend — orphaned processes (#12844), a release that would not start (#15111), unstartable linux-arm64 builds (#14783), exception storms after process death (#12190) — stems from the process-spawning architecture itself, the project has already demoted the backend to an opt-in "Experimental search (Postgres)" default-off setting (PR #15599), and its functional edge is contested by benchmarked in-process alternatives in #12708.

## Technology overview

PostgreSQL is a client–server relational DBMS written in C, released under the permissive [PostgreSQL License](https://www.postgresql.org/about/licence/).
JabRef embeds it via [io.zonky.test:embedded-postgres](https://github.com/zonkyio/embedded-postgres) (Apache-2.0), a Java wrapper that, on every JabRef GUI start ([`Launcher.java` line ~95](../../jabgui/src/main/java/org/jabref/Launcher.java), unconditional and synchronous):

1. extracts xz-compressed platform-specific PostgreSQL binaries from a jar (provided by [zonkyio/embedded-postgres-binaries](https://github.com/zonkyio/embedded-postgres-binaries), BOM `18.4.0` = PostgreSQL 18.4) into a shared temp directory (`/tmp/embedded-pg/PG-<hash>`),
2. runs `initdb` and spawns a `postgres` server **process** listening on an auto-detected free localhost TCP port,
3. connects via the standard PostgreSQL JDBC driver (`org.postgresql:postgresql` 42.7.11).

[`PostgreServer`](../../jablib/src/main/java/org/jabref/logic/search/sqlbased/PostgreServer.java) then installs the `pg_trgm` extension, drops and recreates the `bib_fields` schema (the index is **session-scoped**, rebuilt from scratch every run), and installs two `plpgsql` helper functions (`regexp_mark`, `regexp_positions`) used by the GUI preview highlighter.
Per open library, [`BibFieldsIndexer`](../../jablib/src/main/java/org/jabref/logic/search/sqlbased/indexing/BibFieldsIndexer.java) creates two tables (random CUID names): a main entity–attribute–value table (`entryid`, `field_name`, `field_value_literal`, `field_value_transformed` — the latter LaTeX-to-Unicode converted) with a GIN trigram index (`gin_trgm_ops`) over both value columns, and a `_split_values` table holding individually split authors/keywords/groups/entry-links.
`SearchToSqlVisitor` compiles the ANTLR `Search.g4` query tree into CTE chains using `LIKE`/`ILIKE` (substring) and `~`/`~*` (POSIX regex).

In the consolidated end state of this option, the linked-PDF fulltext search would **also** move from Lucene to PostgreSQL (issue [#12261](https://github.com/JabRef/jabref/issues/12261), currently blocked by the backend decision), e.g. one row per PDF page with trigram and/or [`tsvector`](https://www.postgresql.org/docs/current/textsearch.html) indexes.
The verdicts below assess that end state; notes flag where today's shipping state (Lucene still doing fulltext) differs.

## Requirement coverage

| Requirement (short title) | ID | Verdict | Notes |
|---|---|---|---|
| Query grammar, backend-independent semantics | `req~search.backend.query-grammar~1` | yes | Implemented and shipped (`SearchToSqlVisitor`); v5.x semantics restored by PR #11803 |
| Deterministic substring matching | `req~search.backend.substring-search~1` | yes | `LIKE/ILIKE '%term%'` + pg_trgm GIN index; the reference implementation of this requirement |
| Literal terms without escaping | `req~search.backend.literal-special-characters~1` | yes | Prepared statements; no native query parser exposed |
| Case-(in)sensitive matching | `req~search.backend.case-sensitivity~1` | yes | `LIKE` vs `ILIKE`, `~` vs `~*`; fulltext case-sensitivity only after #12261 migration |
| Backend-resolved regex search | `req~search.backend.regex-search~1` | yes | POSIX `~`/`~*`, trigram-index accelerated; the recorded reason for choosing Postgres |
| Diacritic/LaTeX/transliteration normalization | `req~search.backend.term-normalization~1` | partial | Dual literal+transformed columns shipped; `ue`↔`ü` equivalence missing; `unaccent` extension available as mechanism |
| Person-name normalization | `req~search.backend.name-normalization~1` | partial | Application-level (`AuthorList` split into `_split_values`); backend-neutral, not yet specified |
| Non-Latin scripts, opt-in romanization | `req~search.backend.non-latin-scripts~1` | yes | UTF-8 native; CJK exact match confirmed working in #11803 review; romanization is app-level anyway |
| Fuzzy search | `req~search.backend.fuzzy-search~1` | partial | pg_trgm `similarity()`/`%` operator exists, index-supported; not wired into the grammar |
| Anchored prefix / position-aware matching | `req~search.backend.prefix-and-position~1` | partial | `LIKE 'term%'`/`~ '^term'` index-supported; first-author position needs a position column in `_split_values` (not built) |
| Exact match in multi-value fields | `req~search.backend.multi-value-fields~1` | yes | `_split_values` table, shipped |
| Any-field scope incl. keywords, excl. groups | `req~search.backend.any-field-scope~1` | yes | `field_name != 'groups'` clause, shipped |
| Derived/resolved value indexing | `req~search.backend.derived-values~1` | yes | `BibFieldsIndexer` indexes resolved date/crossref/type values, shipped |
| Fulltext search of linked files | `req~search.backend.fulltext-search~1` | partial | Today done by Lucene; Postgres mechanism (page-per-row, `ts_headline` snippets) designed but unbuilt (#12261 blocked) |
| Fulltext substring/regex consistency | `req~search.backend.fulltext-substring-regex~1` | partial | Would become consistent-by-construction after migration (same trigram operators); today inconsistent (Lucene) |
| Fulltext indexing optional/toggleable | `req~search.backend.fulltext-optional~1` | yes | Preference gate exists; backend-agnostic |
| Complete result sets | `req~search.backend.complete-results~1` | yes | SQL returns all rows; no hit cap |
| Automatic index updates | `req~search.backend.auto-index-update~1` | yes | Plain tables + indexes update transactionally; event-driven incremental updates shipped |
| Index lifecycle notifications | `req~search.backend.change-notifications~1` | yes | `IndexStartedEvent` etc. on event bus; backend-agnostic, shipped |
| Single-entry match check | `req~search.backend.single-entry-check~1` | yes | Query rewrite `(entryid = X) AND (query)`, shipped |
| Backend-provided highlighting | `req~search.backend.highlighting~1` | yes | `regexp_mark`/`regexp_positions` plpgsql functions; self-consistent if Postgres *is* the backend (today they leak into other backends) |
| One isolated index per library | `req~search.backend.per-library-isolation~1` | yes | Per-library CUID tables, dropped on close, shipped |
| Relevance scoring | `req~search.backend.relevance-ranking~1` | partial | `ts_rank`/`similarity()` available as mechanisms; consciously dropped in #11803, not rebuilt |
| Index-backed query speed at 100k entries | `req~search.backend.query-speed~1` | yes | GIN trigram index, no linear scan; no 100k-entry benchmark exists yet |
| Debounced index maintenance | `req~search.backend.debounced-updates~1` | yes | 700 ms `DelayTaskThrottler` (PR #15289/#15501); backend-agnostic; reporter confirmed fix |
| Bounded search-group re-queries | `req~search.backend.bounded-group-requeries~1` | partial | Debounce landed; the 1-update→N-group-queries fan-out is orchestration-level, unresolved for all backends |
| Very large libraries (134k+ entries) | `req~search.backend.large-libraries~1` | partial | DB pages to disk, but entries still live in JVM heap; Postgres adds its own 100–300 MB process (cited claim); full reindex/start at 134k unmeasured |
| Near-real-time freshness | `req~search.backend.near-real-time~1` | yes | Committed writes immediately visible to queries; no NRT machinery needed |
| Cancellable background indexing | `req~search.backend.background-indexing~1` | yes | `BackgroundTask` integration shipped |
| Lazy loading / out-of-heap storage | `req~search.backend.lazy-loading~1` | partial | Strong capability (server page cache, MVCC); aspirational, unimplemented |
| Derived-value offloading to backend | `req~search.backend.derived-value-offloading~1` | partial | Unique strength: plpgsql stored procedures/views could host LaTeX↔Unicode and caches; unimplemented |
| Streaming indexing / byte-offset retrieval | `req~search.backend.streaming-indexing~1` | partial | Backend-neutral proposal; `COPY` bulk load would help; nothing built |
| Reproducible JMH benchmarks | `req~search.backend.benchmarks~1` | no | PR #15385 harness covers only the Lucene fulltext path; no Postgres benchmark exists |
| No startup delay | `req~search.backend.startup-time~1` | no | Unconditional synchronous start; 500–2000 ms typical (cited), ~12 s observed (#14970); unpack+initdb+spawn is inherent |
| Incremental startup indexing | `req~search.backend.incremental-startup~1` | no | Schema dropped each start → full reindex of every library every launch; persistence possible (`setDataDirectory`) but unused and fragile |
| Low resource consumption | `req~search.backend.resource-consumption~1` | partial | Post-fix steady state fine (≤0.3 % CPU/update, measured); but a server-grade process with background workers always runs |
| Small disk footprint | `req~search.backend.disk-footprint~1` | no | 14–33 MB compressed binaries per platform (measured) + unpacked binaries + data dir; heaviest candidate by far |
| Zero administration | `req~search.backend.zero-administration~1` | partial | zonky automates initdb/start/stop; but #15111 forced users to delete `/tmp/embedded-pg` manually; vacuum/tuning concerns raised |
| One engine per JabRef instance | `req~search.backend.single-instance-per-process~1` | yes | One server, one table-pair per library, shipped |
| Native on win/mac/linux × x64/arm64 | `req~search.backend.cross-platform~1` | partial | All six targets exist; field failures: missing linux-arm64 binaries (#14783), macOS-arm x64-emulation fallback, hardened-runtime breakage on older macOS |
| In-process operation | `req~search.backend.in-process~1` | no | Definitional fail: separate OS process; explicitly recorded as violated |
| No network ports | `req~search.backend.no-network-ports~1` | no | Listens on a localhost TCP port (auto-allocated); local-only, but a port nonetheless |
| jlink/jpackage/JPMS packaging | `req~search.backend.packaging~1` | partial | Ships today in jpackage builds, but neither zonky nor pgjdbc has real `module-info` (JabRef patches them); GraalVM native image unsupported |
| Headless and CLI operation | `req~search.backend.headless-operation~1` | partial | jabkit/jabsrv deliberately bypass Postgres (in-memory hard-coded); spawning a server per CLI call is impractical; no native-image story |
| Portable, copyable data files | `req~search.backend.portable-data~1` | no | Data directory tied to server version/instance; not a copyable file |
| .bib remains source of truth | `req~search.backend.source-of-truth~1` | yes | Drop-and-rebuild-per-session index is the purest disposable cache |
| No orphaned processes | `req~search.backend.no-orphan-processes~1` | no | #12844: SIGKILL leaves orphans; shutdown hooks provably insufficient; Windows watchdog failed; "unfixable" in-JVM |
| Startup-failure isolation | `req~search.backend.startup-failure-isolation~1` | partial | `PostgreServer` catches `IOException` and degrades; yet #15111 documents a release where unpack failure blocked startup entirely |
| Stale-state auto-recovery | `req~search.backend.stale-state-recovery~1` | no | Stale `/tmp/embedded-pg` → `FileAlreadyExistsException`, manual deletion required; location called "very fragile" by maintainers |
| Versioned indexes, auto-rebuild | `req~search.backend.index-versioning~1` | partial | Session-scoped index makes versioning moot today; persistent data dirs would face PostgreSQL major-version (`pg_upgrade`) incompatibility |
| Graceful degradation mid-session | `req~search.backend.graceful-degradation~1` | no | Killing the server floods 100s–1000s identical `PSQLException`s (#12190); failure storm is a documented defect |
| Race-free concurrent writes | `req~search.backend.concurrent-writes~1` | yes | Fixed via `ON CONFLICT` upserts (#12373); MVCC transactionality is a genuine strength |
| Robust bulk indexing, inspectable health | `req~search.backend.robust-bulk-indexing~1` | partial | Bib-field bulk indexing robust; the 17k-file fulltext robustness (#13048) would have to be rebuilt on the Postgres side |
| Multiple concurrent JabRef instances | `req~search.backend.concurrent-instances~1` | yes | Free-port auto-detection per instance, proven; cost: one full server per instance |
| Tolerance of malformed BibTeX | `req~search.backend.malformed-bibtex~1` | yes | Backend-neutral; indexer consumes already-parsed entries |
| License-compatible, free dependencies | `req~search.backend.license-compatibility~1` | yes | PostgreSQL License (BSD-like), zonky Apache-2.0, pgjdbc BSD-2-Clause, xz-java 0BSD |
| Single storage technology | `req~search.backend.single-technology~1` | partial | After #12261 it would be one *search* engine, and the same tech as remote-library sharing; H2 MVStore remains; today it is one of three technologies |
| Architectural decision record | `req~search.backend.decision-record~1` | no | The 2024 Postgres choice was made without an ADR (recorded in #12708); this evaluation feeds the missing ADR |
| Pluggable behind `SearchBackend` | `req~search.backend.pluggable~1` | yes | `SqlSearchBackend` implements the bridge; runtime swap shipped (#15599) |
| Query-syntax stability | `req~search.backend.query-syntax-stability~1` | yes | Staying on Postgres preserves the v5.x syntax by definition; no migration of saved groups |
| Extensibility beyond search | `req~search.backend.extensibility~1` | yes | Strongest candidate: extra tables, stored procedures, views, potential multi-client; but pgvector is *not* in the zonky bundles |
| Staged, bounded migration process | `req~search.backend.migration-process~1` | yes | Process requirement; #12261 already scoped at ~40 person-days, blocked on the decision |

### Notes on non-trivial verdicts

**Battle-tested grammar coverage (`query-grammar`, `literal-special-characters`, `multi-value-fields`, `any-field-scope`, `derived-values`, `single-entry-check`, `concurrent-writes`).**
These verdicts are "yes" not on paper but in shipped code: PR #11803 was explicitly accepted *because* it fixed the user-visible Lucene regressions (#11823 `xyz AND abc` vs `xyz-abc`, space-means-OR, keywords-only-first-match; #11798 `Law:2009The` misparsed as a field query), and the subsequent 18 months produced a long tail of hardening fixes (#12373 unique-constraint upserts, #13981 duplicate keys, #15241, #15747/#15719 freshness).
The mechanism in each case is plain SQL: prepared statements keep user terms literal; the `_split_values` table (populated by `AuthorList`/`KeywordList` splitting) makes `author = Smith` match one author among many; `field_name != 'groups'` implements the any-field exclusion; `getResolvedFieldOrAlias*` feeds resolved dates/crossref/type into the index; `(entryid = X) AND (query)` re-runs the compiled query for single-entry group tests.
Any replacement backend must re-earn this maturity; that is a real, if soft, argument for the incumbent.

**Substring, regex, case-sensitivity (`substring-search`, `regex-search`, `case-sensitivity`) — the core strength.**
The mechanism is `pg_trgm`: a GIN index over trigrams of both value columns that accelerates `LIKE`, `ILIKE`, `~`, `~*` and `=` queries — the [official documentation](https://www.postgresql.org/docs/current/pgtrgm.html) states "These index types also support trigram-based index searches for `LIKE`, `ILIKE`, `~`, `~*` and `=` queries."
This gives deterministic "contains" semantics with no tokenizer to mirror (the property that broke Lucene per [#12261](https://github.com/JabRef/jabref/issues/12261)), index-backed POSIX regex resolved *inside* the DBMS (the recorded motivation for PR [#11803](https://github.com/JabRef/jabref/pull/11803)), and exact case-sensitivity by operator choice.
No other candidate provides all three natively; Lucene needs custom NGram analyzers and `RegexpQuery`, SQLite needs a user-defined `REGEXP` function plus an FTS5 trigram tokenizer.

**Term normalization (`term-normalization`) — partial everywhere.**
Postgres does not solve `Düsseldorf` = `Duesseldorf` = `D\"{u}sseldorf` natively; neither does any candidate (koppor: "as hard in Postgres as it is in Lucene", #12261).
The shipped mechanism — indexing `field_value_literal` *and* a LaTeX-to-Unicode `field_value_transformed` column and querying both — solves the LaTeX axis.
The diacritic axis (`Kepes` → `Képes`) could use the contrib [`unaccent`](https://www.postgresql.org/docs/current/unaccent.html) extension in an additional folded column (inference: contrib modules are bundled, since `pg_trgm` — also contrib — works in the zonky binaries; to be verified).
The `ue`↔`ü` orthographic axis needs application-level folding regardless of backend.

**Fuzzy search (`fuzzy-search`).**
pg_trgm's `similarity()`, `word_similarity()` and the `%` operator give index-supported typo tolerance — arguably closer to "misspelled journal title still matches" than Lucene's edit-distance `FuzzyQuery`, and a capability SQLite lacks entirely.
Nothing is wired into the search grammar yet; verdict *partial* on implementation, *strong* on mechanism.

**Fulltext of linked PDFs (`fulltext-search`, `fulltext-substring-regex`) — the consolidation bet.**
Today Lucene does this; Postgres doing it is a design (#12261: one row per PDF page; trigram index for substring/regex; [`ts_headline`](https://www.postgresql.org/docs/current/textsearch-controls.html) or the existing `regexp_mark` function for snippets).
The upside is *consistency by construction*: the same operators serve fields and fulltext, fixing the semantic split of [#14569](https://github.com/JabRef/jabref/issues/14569).
The risks: trigram GIN indexes over megabytes of PDF text per library are unbenchmarked (index size and build time), and page-wise incremental re-indexing keyed on file mtimes — which Lucene already has — must be rebuilt.
Stemming (Lucene's `EnglishAnalyzer` today) would be lost unless `tsvector` columns are added in parallel.

**Startup, process lifecycle, stale state (`startup-time`, `in-process`, `no-orphan-processes`, `no-network-ports`, `stale-state-recovery`, `graceful-degradation`) — the architectural fail block.**
These are not implementation bugs.
A separate OS process *cannot* be cleaned up on SIGKILL (verified experimentally in [#12844](https://github.com/JabRef/jabref/issues/12844); the pipe-EOF watchdog failed on Windows); binary unpacking *can* hit stale shared-temp state ([#15111](https://github.com/JabRef/jabref/issues/15111), maintainer: the issue "surely will persist"); `initdb` + process spawn *is* hundreds of milliseconds to seconds; a server *must* listen on a socket (zonky uses localhost TCP).
The discussion in #12708 calls this failure class "inherent to external-process architecture" and "unfixable", and the field record (orphans, startup failures, exception storms after killing the process) supports that.
A mitigating fact from the issue sweep: **no** antivirus/firewall blocking or port-conflict reports exist in the tracker to date; the port concern is so far architectural, not observed (though zonky issue [#66](https://github.com/zonkyio/embedded-postgres/issues/66) shows PostgreSQL refusing to start for Windows administrator accounts — "Execution of PostgreSQL by a user with administrative permissions is not permitted" — a real institutional-environment hazard).

**Cross-platform (`cross-platform`).**
The [zonky binaries project](https://github.com/zonkyio/embedded-postgres-binaries) covers Darwin/Windows/Linux/Alpine on amd64/arm64 and more; JabRef ships six artifacts.
But the matrix has burned JabRef before: linux-arm64 builds once shipped *without* binaries (startup failure, [#14783](https://github.com/JabRef/jabref/issues/14783)); macOS-arm64 silently ran x64 binaries under Rosetta; older macOS hardened-runtime rules broke binary loading upstream ([binaries#21](https://github.com/zonkyio/embedded-postgres-binaries/issues/21)).
Every new OS hardening change is a new risk surface that pure-Java or single-native-lib candidates do not have.

**Beyond-search potential (`lazy-loading`, `derived-value-offloading`, `extensibility`, `large-libraries`) — unique upside, all unimplemented.**
PostgreSQL is the only candidate that can plausibly serve the #12708 long-term vision in one technology: a server-side page cache that holds entry data out of the JVM heap (lifting the 134k-entry RAM ceiling of #10209), `plpgsql` stored procedures and views hosting LaTeX↔Unicode conversion, `AuthorList` parse caches and pre-formatted save output (koppor estimated ~50 % gain from the two cache points alone), additional tables for AI features, and true multi-client MVCC access.
SQLite can store the data but cannot run server-side procedures; Lucene is not a relational store at all.
Two honest caveats: nothing of this is built, so it is potential rather than coverage; and the `large-libraries` verdict stays *partial* because today's search backend does not touch the actual bottleneck (entries as `List<BibEntry>` in heap) — it merely would not stand in the way of fixing it.
Note also that `pgvector` (mentioned for AI/RAG in ADR-0037 contexts) is a third-party extension **not** included in the zonky binary bundles; using it would require maintaining custom binary builds (inference from the bundles' vanilla-PostgreSQL build process).

**Benchmarks (`benchmarks`) — a process gap that cuts against the incumbent.**
PR [#15385](https://github.com/JabRef/jabref/pull/15385) added JMH entry points only for the *Lucene* linked-files path (`search()`, `index()`), with reviewer-flagged fidelity problems (CWD-dependent fixture path, mock allocation inside the timed loop) and a one-PDF workload.
There is no JMH benchmark of `BibFieldsIndexer`/`BibFieldsSearcher` at all, so the option with the heaviest footprint is also the one with the least reproducible performance evidence — the per-start full-reindex cost at 100k entries, the figure most likely to decide this option's fate, has never been measured.

**Headless operation (`headless-operation`).**
The follow-up question was raised in the #11803 review itself ("for our Cli application, we need to fire up postgres on some cases").
Today both stand-alone jabsrv and jabkit hard-code the in-memory path; jabsrv even documents "Stand-alone HTTP server must never use the Postgres backend."
Under this option that split becomes permanent: a per-invocation CLI cannot amortize a 0.5–2 s server start plus full reindex, and the native-image build cannot carry the binaries.
The consequence is two coexisting search implementations with subtly different capabilities (no fulltext, no trigram acceleration in headless mode) — in tension with `single-technology` even in the option's best case.

**Incremental startup (`incremental-startup`).**
The schema is dropped and recreated every launch, so every open library is fully re-indexed on every start.
At 1k–10k entries this is tolerable; at the 134k-entry requirement scale it is unmeasured.
Persistence across runs is technically available (`setDataDirectory` + `setCleanDataDirectory(false)`, confirmed in the #11803 review) but would bind JabRef to PostgreSQL's on-disk format, which is **not** compatible across major versions (annual majors; upgrade requires `pg_upgrade` or dump/reload per the [versioning policy](https://www.postgresql.org/support/versioning/)) — making true index persistence costly to maintain.

## Java integration and packaging

- **Libraries:** `io.zonky.test:embedded-postgres` 2.2.2 (wrapper, 60 kB) + `io.zonky.test.postgres:embedded-postgres-binaries-*` (BOM 18.4.0, six platform jars) + `org.postgresql:postgresql` 42.7.11 (JDBC, 1.1 MB) + `org.tukaani:xz` 1.11 (binary unpacking). All declared in `versions/build.gradle.kts`.
- **JPMS:** none of the three main jars ships a real `module-info`; JabRef patches them into named modules (`embedded.postgres`, `org.postgresql.jdbc`, plus per-platform binary modules) in `build-logic/src/main/kotlin/org.jabref.gradle.base.dependency-rules.gradle.kts`. This works, but each upstream bump risks re-patching — contrast with Lucene, which has first-class JPMS since 9.1 (PR [#8362](https://github.com/JabRef/jabref/pull/8362)).
- **jlink/jpackage:** proven — every shipped 6.0 build packages the stack. Because `jablib`'s `module-info` hard-requires `embedded.postgres` (+ binary modules), **all** distributions (jabgui, jabkit app-image, jabsrv-cli, jabls-cli) carry the binaries even where they are never started (issue [#15963](https://github.com/JabRef/jabref/issues/15963) floats splitting the Postgres backend into a separate module).
- **GraalVM native image:** effectively unsupported. jabkit's `reachability-metadata.json` contains zero Postgres (or Lucene) entries; jabkit hard-codes the in-memory searcher. The [GraalVM reachability-metadata repository](https://github.com/oracle/graalvm-reachability-metadata/tree/master/metadata/org.postgresql/postgresql) covers the JDBC driver, so *talking to* Postgres from a native image is feasible — but the zonky wrapper (classpath-resource binary extraction, process management) has no native-image support anywhere, and embedding tens of MB of server binaries as image resources would defeat the point of a slim CLI. Under this option, native-image jabkit permanently needs a different (in-memory) search path.
- **Platform matrix / size impact** (jar sizes measured in the local Gradle cache, version 18.4.0): linux-amd64 15.2 MB, linux-arm64v8 14.2 MB, linux-amd64-alpine 14.9 MB, windows-amd64 25.3 MB, darwin-amd64 33.1 MB, darwin-arm64v8 33.1 MB — compressed; binaries unpack at runtime into the temp cache. Per-platform installers therefore grow by ~15–35 MB compressed versus a Postgres-free build (the "~500 MB additional storage" remark in #11803 was half-ironic and refers to a generous total-runtime view, not the jar payload).

## Operational footprint

- **Startup:** unconditional, synchronous server start before the GUI appears (`Launcher.java`). Cited comparison figures: 500–2000 ms cold start (claim from the #12708 evaluation); a #14970 Windows log shows ~12 s elapsed before "Postgres server started, connection port: 58685". First-ever start additionally pays binary extraction.
- **RAM:** the postgres process itself measured at ~56 MB RSS / ~235 MB VIRT in #12190; the #12708 evaluation claims ~100–300 MB at 100k+ entries (shared buffers, WAL, background workers) vs ~10–30 MB for SQLite — a cited claim, not a JabRef measurement.
- **Disk:** per-platform binaries (above) + unpacked binaries in the shared temp cache + a data directory per instance (session-scoped today). Index tables are dropped on library close and the schema on restart, so no vacuum-driven growth in practice.
- **Processes/ports/sockets:** one `postgres` server process (visible as several OS-level postgres workers) per JabRef instance, listening on one auto-allocated localhost TCP port. No port-5432 conflicts possible; no antivirus/firewall field reports so far — but the process model is exactly what "may be forbidden in certain environments" (#12708) targets, and Windows administrator accounts cannot run PostgreSQL at all (zonky [#66](https://github.com/zonkyio/embedded-postgres/issues/66)).
- **After a crash:** SIGKILL/Task-Manager kill orphans the server ([#12844](https://github.com/JabRef/jabref/issues/12844): 6 orphaned instances after 5–6 dev restarts, pinned to one CPU, causing system lag); stale temp-cache state can block the *next* startup ([#15111](https://github.com/JabRef/jabref/issues/15111)). If the server dies mid-session, the GUI floods identical `PSQLException`s (#12190).
- **Multiple instances:** each instance auto-detects a free port — proven to work, at the cost of N full server processes.

## Performance evidence

**JabRef-measured (field reports and maintainer measurements):**

- Initial indexing of a 6.5k-entry library: ~8 % CPU; single insert/update: ≤0.3 % CPU (LoayGhreeb, #12190) — raw per-operation cost was never the bottleneck.
- Pre-debounce pathology (#12190): postgres pinned at 99.7 % CPU for minutes (7:35 min accumulated) under 1k+ entries + search groups + typing; root cause was the un-throttled event pipeline (one DELETE+INSERT plus N group re-queries per keystroke), fixed backend-agnostically by debouncing (PR #15289); reporter confirmed resolution.
- Startup: ~12 s to "Postgres server started" in a #14970 Windows log; JabRef 6.0 window in 8–10 s vs 1–2 s for 5.15 (#15866; only partly attributable to Postgres).
- Process footprint: ~56 MB RSS / ~235 MB VIRT for the postgres process (#12190 `top` samples).
- Binary jar sizes: 14.2–33.1 MB per platform (measured in this repo's dependency cache).
- PR [#15385](https://github.com/JabRef/jabref/pull/15385) contributes **no numbers**: it is a JMH harness for the *Lucene* fulltext path only (one ~243 kB PDF fixture, with known fidelity caveats). **No Postgres-side JMH benchmark exists**, so `req~search.backend.benchmarks~1` is unmet for this option and no 100k-entry reindex/query measurement is on record.

**External / cited claims (not JabRef measurements):**

- #12708 evaluation comparison: embedded Postgres ~100–300 MB RAM at 100k+ entries vs SQLite ~10–30 MB; cold start 500–2000 ms vs 50–150 ms. These are the discussion's planning numbers, derived from general PostgreSQL behavior, not benchmarked inside JabRef.
- [pg_trgm documentation](https://www.postgresql.org/docs/current/pgtrgm.html): GIN/GiST trigram indexes accelerate `LIKE`/`ILIKE`/`~`/`~*` — capability statement, no latency figures.
- Competing Lucene JMH numbers from #12708 (trigram substring 33k–42k ops/s; `RegexpQuery` ~6.3k ops/s on 5k entries) show the alternative is also fast; no head-to-head Postgres-vs-Lucene benchmark on identical data exists.

## Licensing and distribution

All components are permissive and MIT-compatible (`req~search.backend.license-compatibility~1`: yes):

- PostgreSQL server: [PostgreSQL License](https://www.postgresql.org/about/licence/) ("similar to the BSD or MIT licenses"), with a perpetuity commitment.
- zonky wrapper and binaries: [Apache-2.0](https://github.com/zonkyio/embedded-postgres).
- JDBC driver: [BSD-2-Clause](https://github.com/pgjdbc/pgjdbc).
- xz-java: 0BSD (1.10+).

Redistribution in installers on all platforms is free of registration or fees. No copyleft obligations arise.

## Maintenance and ecosystem

- **PostgreSQL itself:** exemplary health — annual major releases, quarterly minors, 5-year support per major ([versioning policy](https://www.postgresql.org/support/versioning/)); one of the largest open-source communities; effectively zero bus-factor risk.
- **The critical link is zonky, not Postgres.** [zonkyio/embedded-postgres](https://github.com/zonkyio/embedded-postgres) is a small community fork (~470 stars) whose README frames it for *unit testing* ("allowing you to unit test with a 'real' Postgres"); JabRef's maintainer openly stated the wrapper "is intended for testing" and the data location is "very fragile" (#11803). It is maintained (v2.2.2, 2026-03; binaries 18.4.0, 2026-05) but has a small contributor base — a genuine bus-factor risk for the exact component JabRef depends on for lifecycle management. Production-grade desktop embedding is off-label use with no upstream support promise.
- **Java ecosystem fit:** JDBC is the most mature DB integration in Java; however neither wrapper nor driver ships JPMS metadata (JabRef patches), and the architecture is at odds with the Java-desktop deployment story (jlink minimal runtimes, native images, single-process apps).

## Migration effort

The `SearchBackend` bridge means *keeping* PostgreSQL costs nothing structurally — `SqlSearchBackend` exists and works. What "choosing this option" concretely requires:

1. **Write the missing ADR** (`req~search.backend.decision-record~1`) and re-promote Postgres from "Experimental search (Postgres)" opt-in back to default — trivial code, significant decision weight, since #15599 demoted it. (S)
2. **Migrate PDF fulltext from Lucene to Postgres** (#12261): page-per-row schema, trigram/tsvector indexes, snippet generation (`ts_headline`/`regexp_mark`), mtime-based incremental indexing, robustness for 17k-file collections, plus benchmark validation. koppor sized it at ~40 person-days for a master-level contributor. (M)
3. **Pay down the operational debt that the requirements demand and the architecture resists:** lazy/async server start; PID-file orphan cleanup (#12844 — best effort only, the requirement as written stays unmet); robust stale-state recovery away from `/tmp/embedded-pg` (#15111 — likely needs replacing or forking the zonky wrapper, since the maintainer expects the failure to persist); single-report degradation instead of exception storms; decoupling the GUI `Highlighter` (or accepting the coupling as the design). Each item fights the external-process model rather than completing it. (L, open-ended)
4. **Headless/native-image story:** accept a permanent split — jabkit native image and stand-alone jabsrv keep the in-memory backend — which institutionalizes two search semantics in the product. (S to accept, impossible to fully unify)

**Overall estimate: M for feature consolidation, L for requirement compliance — with the caveat that items `in-process`, `no-orphan-processes`, `no-network-ports`, and sub-second cold start are not achievable at any effort level.** By contrast, the *exit* migration this option avoids (SQLite or Lucene backend) was scoped in #12708 as a dialect port of `SearchToSqlVisitor`/`BibFieldsIndexer` plus highlighting re-implementation — also roughly M — so "migration avoided" is not a decisive argument for staying.

## Risks and open questions

- **Unfixable requirement violations:** four requirements (`in-process`, `no-orphan-processes`, `no-network-ports`, `startup-time` at the tens-of-ms target) are architecturally unreachable. Selecting this option requires explicitly re-scoping them; otherwise the ADR would approve a permanently non-compliant backend.
- **Testing-tool dependency in production:** the zonky wrapper is off-label for desktop shipping; its temp-dir cache design has already caused a release-blocking failure (#15111) and its small maintainer base may not prioritize JabRef-class desktop concerns (signed binaries for future macOS hardening, Windows admin accounts, antivirus quirks).
- **Unbenchmarked at target scale:** no measurement exists for full reindex time or query latency at 100k–134k entries, nor for trigram-indexing whole PDF corpora. The session-scoped rebuild could blow the startup budget exactly where the backend is supposed to shine (`req~search.backend.large-libraries~1`).
- **PostgreSQL major-version churn:** annual majors with incompatible data directories make persistent indexes (the fix for `incremental-startup`) expensive; staying session-scoped instead keeps the startup cost.
- **Institutional environments:** process-spawn prohibitions and the Windows "no administrator" rule are plausible hard blockers for a subset of users that no code change can address.
- **Open questions:** (a) Would the maintainers accept shipping the Postgres backend as an optional separate module/distribution (#15963), converting most footprint "no" verdicts into "n/a for default installs"? (b) Is `unaccent` actually present in the zonky binary bundles (needs a one-line test)? (c) What are the original Lucene show-stoppers from PR #11803 per LoayGhreeb — still unconfirmed, and material to whether Postgres's functional edge is as unique as recorded? (d) Can `pg_trgm` fuzzy operators be wired into the grammar cheaply enough to claim `fuzzy-search` as a differentiator?

## Sources

- Requirements: [docs/requirements/search-backend.md](../requirements/search-backend.md)
- JabRef discussions and code: [#12708](https://github.com/JabRef/jabref/issues/12708), [PR #11803](https://github.com/JabRef/jabref/pull/11803), [#12261](https://github.com/JabRef/jabref/issues/12261), [PR #15385](https://github.com/JabRef/jabref/pull/15385), [#12190](https://github.com/JabRef/jabref/issues/12190), [#12844](https://github.com/JabRef/jabref/issues/12844), [#15111](https://github.com/JabRef/jabref/issues/15111), [#14783](https://github.com/JabRef/jabref/issues/14783), [#14569](https://github.com/JabRef/jabref/issues/14569), [#14970](https://github.com/JabRef/jabref/issues/14970), [#15866](https://github.com/JabRef/jabref/issues/15866), [#15963](https://github.com/JabRef/jabref/issues/15963), [#10209](https://github.com/JabRef/jabref/issues/10209), [#10490](https://github.com/JabRef/jabref/issues/10490), [#11823](https://github.com/JabRef/jabref/issues/11823), [#11798](https://github.com/JabRef/jabref/issues/11798), [#9605](https://github.com/JabRef/jabref/issues/9605), [PR #8362](https://github.com/JabRef/jabref/pull/8362), [PR #15599](https://github.com/JabRef/jabref/pull/15599), [PR #15289](https://github.com/JabRef/jabref/pull/15289), [PR #12373](https://github.com/JabRef/jabref/pull/12373)
- Repository state: `jablib/src/main/java/org/jabref/logic/search/**`, `jabgui/src/main/java/org/jabref/Launcher.java`, `jabgui/src/main/java/org/jabref/gui/search/Highlighter.java`, `versions/build.gradle.kts`, `build-logic/src/main/kotlin/org.jabref.gradle.base.dependency-rules.gradle.kts`, `jabkit/src/main/resources/META-INF/native-image/org.jabref/jabkit/reachability-metadata.json`
- zonky embedded-postgres: <https://github.com/zonkyio/embedded-postgres> (purpose, license, platforms, releases), <https://github.com/zonkyio/embedded-postgres/issues/66> (Windows administrator restriction), <https://github.com/zonkyio/embedded-postgres/issues/146> (initialization failures)
- zonky binaries: <https://github.com/zonkyio/embedded-postgres-binaries> (platform/architecture matrix, ~10 MB bundles, 18.4.0), <https://github.com/zonkyio/embedded-postgres-binaries/issues/21> (macOS hardened runtime)
- PostgreSQL: <https://www.postgresql.org/about/licence/> (license), <https://www.postgresql.org/support/versioning/> (release cadence, 5-year support), <https://www.postgresql.org/docs/current/pgtrgm.html> (pg_trgm index support for LIKE/ILIKE/regex, similarity functions), <https://www.postgresql.org/docs/current/textsearch.html> and <https://www.postgresql.org/docs/current/textsearch-controls.html> (tsvector, ts_headline), <https://www.postgresql.org/docs/current/unaccent.html> (unaccent extension)
- JDBC driver: <https://github.com/pgjdbc/pgjdbc> (BSD-2-Clause)
- GraalVM: <https://github.com/oracle/graalvm-reachability-metadata/tree/master/metadata/org.postgresql/postgresql> (driver metadata exists; no zonky support)

<!-- markdownlint-disable-file MD013 -->
