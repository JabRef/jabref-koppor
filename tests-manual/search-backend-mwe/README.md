# Search-backend MWEs

Minimal working examples that measure the **unproven axes** of [ADR 0064 — Select search index
backend](../../docs/decisions/0064-search-index-backend.md), so the ADR's gates can move from
*claimed* to *measured*. They cover the candidates evaluated in depth
([SQLite](../../docs/evaluations/search-backend-sqlite.md),
[Lucene](../../docs/evaluations/search-backend-lucene.md),
[embedded PostgreSQL](../../docs/evaluations/search-backend-postgresql.md),
[in-memory](../../docs/evaluations/search-backend-in-memory.md)) and feed back the numbers that
issue [#12708](https://github.com/JabRef/jabref/issues/12708) and its consolidated recommendation
([comment 3947205759](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759)) list as open.

These are **scouting** measurements: fast, throwaway, runnable by anyone via [jbang](https://www.jbang.dev/)
(already an established tool here — see ADR [0046](../../docs/decisions/0046-use-jbang-for-generating-index-files.md)/[0048](../../docs/decisions/0048-jbang-script-modification-for-testing-in-ci.md)).
The **committed** numbers still belong in the [#15385](https://github.com/JabRef/jabref/pull/15385)
JMH harness; an MWE that survives here is promoted there.

## What each MWE proves

| MWE (jbang) | Candidate | ADR gate it closes | Status |
|---|---|---|---|
| `SqliteRegex.java` | SQLite + FTS5 | regex: naive `REGEXP` UDF scan **vs** trigram pre-filter, across literal-extractable and no-literal patterns | **done** |
| `SqliteFulltext.java` | SQLite + FTS5 | PDF-fulltext trigram index absolute size + build time on a real corpus (the ~2.8× / multi-GB worry) | todo |
| `Lucene.java` | Apache Lucene | trigram index size @100k, `RegexpQuery` dialect parity, LaTeX/ICU analyzer throughput | todo |
| `InMemory.java` | in-memory scan | scan latency @10k/100k — the floor every indexed backend must beat | todo |
| `EmbeddedPg.java` | embedded PostgreSQL | the missing 100k reindex / startup / footprint baseline (its evaluation currently has none) | todo |
| `reindex` (all) | all | full reindex time 10k → 100k entries | todo |

The shared pieces (`GenData.java`, `Common.java`) are step 0 and are reused by every MWE so the
results are comparable — the single most important property, and the one the #15385 fixture lacked.

## Run it

Prerequisites: `jbang` (which fetches JDK 25 on demand). No build, no Gradle, no jablib.

```bash
# one candidate, by hand
jbang GenData.java 100000 data/bib-100000.tsv
jbang SqliteRegex.java data/bib-100000.tsv

# the whole suite -> CSV per candidate under out/
bash run-suite.sh 100000
```

Reproducible / resource-capped / arm64 via the pinned Docker runner:

```bash
docker build -t jabref-search-mwe .
docker run --rm -v "$PWD/out:/mwe/out" jabref-search-mwe
docker run --rm --cpus=2 --memory=2g -v "$PWD/out:/mwe/out" jabref-search-mwe   # low-end machine
docker buildx build --platform linux/arm64 -t jabref-search-mwe:arm64 .          # linux-arm64
```

(macOS/Windows comparability is a GitHub Actions matrix running the same jbang scripts; Docker is
linux-only and is here for the pinned/low-end/arm64 runs plus the SIGKILL orphan-process test.)

## CI

[`.github/workflows/search-backend-mwe.yml`](../../.github/workflows/search-backend-mwe.yml) runs the
suite **on demand** (`workflow_dispatch`, with an `entries` input — default 100000) and **on every
change** to `tests-manual/search-backend-mwe/**` (`push`/`pull_request`, with a smaller 20000-entry
set for speed). It sets JBang up the way the rest of the repo does (`jbangdev/setup-jbang`, `~/.jbang`
cache), prints the CSV to the job log, and uploads it as the `search-backend-mwe-results` artifact.
A cross-OS `matrix` (ubuntu/macOS/windows, à la `jabkit-native-smoke-test.yml`) is the natural
extension once the packaging/cross-platform gates need it.

## Output

Every MWE prints the same CSV schema to stdout, so the per-candidate files concatenate into one table:

```
candidate,dataset_size,operation,variant,p50_ms,p95_ms,build_ms,index_bytes,matches,notes
```

`matches` is the result-set size, carried as a correctness check: for each regex the pre-filter and
the naive scan must report the **same** count (the pre-filter only narrows candidates; `REGEXP` still
decides), and `SqliteRegex` flags any mismatch in `notes`.

## Caveats

- **MWE-grade, not JMH.** Simple warmup + small iteration counts via `System.nanoTime()`; expect JIT
  noise. Use for order-of-magnitude and cross-candidate shape, not for three-significant-figure claims.
- **Literal extraction is a heuristic**, not a regex parser (see `Common.longestLiteral`). It is good
  enough to demonstrate the pre-filter mechanism; the production pre-filter would walk the parsed pattern.
- **Synthetic data.** `GenData.java` is seeded and token-varied for realistic query selectivity;
  `scripts/bib-file-generator.py` remains the scale + Unicode-edge (vinculum) generator.

## Dataset shape

`GenData.java` emits an EAV TSV — one `entryId<TAB>field<TAB>value` row per (entry, field), five fields
per entry (`author`, `title`, `journal`, `year`, `keywords`), multi-values kept as one delimited string
(`A and B`, `k1, k2`) to mirror JabRef's `field_value_literal`. So *N* entries ⇒ *5N* searchable rows;
the CSV records both via `dataset_size` and the build log.

## Roadmap

- [x] Step 0: `GenData.java` (deterministic dataset) + `Common.java` (timing, CSV, literal extraction) + Docker runner
- [x] SQLite regex gate — `SqliteRegex.java`
- [ ] Embedded-PostgreSQL 100k baseline — `EmbeddedPg.java` (via `io.zonky.test:embedded-postgres`)
- [ ] Lucene — `Lucene.java` (trigram size, RegexpQuery parity, LaTeX throughput)
- [ ] In-memory floor — `InMemory.java`
- [ ] SQLite PDF-fulltext index size/build on a real corpus — `SqliteFulltext.java`
- [ ] Orphan-process / SIGKILL test (embedded-PG, Docker harness)
- [ ] Wire measured numbers back into the evaluations + ADR (`[claimed in #12708]` → `[MWE-measured]`)
