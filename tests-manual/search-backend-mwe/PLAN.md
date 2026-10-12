# Search-backend MWE — handover plan

A session-to-session handover for the minimal-working-example benchmark suite that closes the open
gates of [ADR 0064 — Select search index backend](../../docs/decisions/0064-search-index-backend.md).
Read this first, then [`README.md`](README.md) for run details. **Goal of the suite:** turn the ADR's
`[claimed in #12708]` figures into `[MWE-measured]` ones, candidate by candidate.

## Status (branch `add-adr-on-db-backend`, pushed)

- `e9c849b01f` — docs: reconciled ADR 0064 with the #12708 *Lucene-for-search + SQLite-for-storage*
  recommendation (explicit divergence to single-technology SQLite-FTS5 + Zotero-FTS-scaling rebuttal),
  added the "Also considered and rejected" 13-technology table, linked the recommendation comment,
  and noted Citavi/Mendeley + the Zotero-scaling clarification in the SQLite evaluation.
- `8be950ea37` — this MWE suite + the CI workflow `.github/workflows/search-backend-mwe.yml`.

**Done:** step-0 shared harness + the SQLite **regex** gate, compile-checked
(`Common.java`/`GenData.java` compile; `SqliteRegex.java` compiles against a sqlite-jdbc stub).
**Not done:** nothing has actually been *run* yet — no JDK 25 / jbang was available in the authoring
session, so **there are no measured numbers in the repo**. First job below.

## Decisions already locked (do not re-litigate)

- **jbang for all four candidates' functional MWEs; Docker only for the pinned/low-end/arm64 runs and
  the SIGKILL orphan-process test.** Cross-OS (macOS/Windows) is a CI matrix, not Docker. (jbang is the
  repo's blessed tool — ADR [0046](../../docs/decisions/0046-use-jbang-for-generating-index-files.md)/[0048](../../docs/decisions/0048-jbang-script-modification-for-testing-in-ci.md).)
- **SQLite regex is the first gate** — the SQLite evaluation's top risk, and the measurement that
  decides SQLite-FTS5 vs. the Lucene+SQLite fallback.
- **Step 0 is a shared deterministic generator + a uniform CSV row** (`GenData.java` + `Common.java`).
  Comparability across candidates is the whole point — it's exactly what the #15385 fixture lacked.
- **These MWEs are scouting, not the committed numbers.** The committed numbers still go in the
  [#15385](https://github.com/JabRef/jabref/pull/15385) JMH harness; promote an MWE there once it holds.
- **Location:** `tests-manual/search-backend-mwe` (matches the repo's manual-investigation convention).

## TODO

Highest-value first. Each item names the ADR gate it closes and the file to add.

- [ ] **Run the SQLite regex MWE and capture results.** `bash run-suite.sh 10000` and `... 100000`
      (or trigger the CI workflow / `docker run`). Sanity-check that, per query, the trigram-prefilter
      and naive `matches` counts agree (the script flags mismatches in `notes`).
- [ ] **Wire the measured regex numbers back into the docs.** Replace the relevant `[claimed in #12708]`
      / preliminary figures in [`search-backend-sqlite.md`](../../docs/evaluations/search-backend-sqlite.md)
      (the "central weakness" regex section + Risks #1) and ADR 0064's Confirmation/Consequences with
      `[MWE-measured]` values. This is the payoff that lets the ADR move from *proposed* toward *accepted*.
- [ ] **`EmbeddedPg.java` — embedded-PostgreSQL 100k baseline** (`//DEPS io.zonky.test:embedded-postgres`
      + binaries BOM + pgjdbc). Fills the incumbent's missing reindex / startup / footprint numbers — its
      [evaluation](../../docs/evaluations/search-backend-postgresql.md) currently has *none*, so this is
      the baseline everything else is compared against. Gate: `query-speed`, `startup-time`, `incremental-startup`.
- [ ] **`Lucene.java`** (`//DEPS org.apache.lucene:lucene-core`,`-analysis-common`,`-analysis-icu`).
      Trigram index size @100k, `RegexpQuery` dialect-parity vs java.util.regex, LaTeX→ICU analyzer
      throughput. Proves the competing recommendation's strengths. Gate: `regex-search`, `term-normalization`,
      `query-speed`.
- [ ] **`InMemory.java`** — scan latency @10k/100k, the floor every indexed backend must beat. No deps
      (mirror `BibEntryMatchVisitor`'s match kernels). Gate: `query-speed`.
- [ ] **`SqliteFulltext.java`** — FTS5 trigram index *absolute* size + build time on a **real PDF corpus**
      (the ~2.8×-text / multi-GB worry). Needs a corpus wired in (e.g. a sample of the #13048 17k-file set,
      or PDFs under a configurable path). Gate: `fulltext-search`, `disk-footprint`.
- [ ] **Reindex timing 10k→100k** for each backend (add a `reindex` operation to each MWE).
- [ ] **Orphan-process / SIGKILL test** for embedded-PostgreSQL via a small Docker harness (kill the JVM,
      assert no surviving `postgres` child). Gate: `no-orphan-processes` (#12844).
- [ ] **Cross-OS CI matrix** (ubuntu/macOS/windows, à la `jabkit-native-smoke-test.yml`) once the
      packaging/cross-platform gates (SQLite/Lucene native libs on arm64/mac/win) need it.

## Recipe: adding a candidate MWE

1. New jbang file `Xxx.java` with the repo header style: `///usr/bin/env jbang …`, `//JAVA 25`,
   `//RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED` (if JNI), `//DEPS …`, `//SOURCES Common.java`,
   and an instance `void main(String[] args)` (compact source file — see `SqliteRegex.java`).
2. Read the shared dataset via `Common.forEachField(tsv, (id, field, value) -> …)`.
3. Time with `Common.time(warmup, iters, () -> <returns match count>)`; emit `Common.Result` rows
   (`System.out.println(r.toCsv())`) so the CSV concatenates with the others.
4. Append a `jbang Xxx.java …` line to [`run-suite.sh`](run-suite.sh), tick the row in the README matrix,
   and update the roadmap there + the TODO here.

## Gotchas

- **MWE-grade**, not JMH: small warmup + iteration counts via `System.nanoTime()`. For comparable numbers
  use the **Docker runner** (pinned JDK, `--cpus/--memory` caps) or CI, not a dev laptop.
- **Literal extraction** in `Common.longestLiteral` is a heuristic, not a regex parser (skips escapes /
  char-classes / optional chars, ≥3-char threshold for FTS5 trigram). Good enough to show the mechanism;
  the production pre-filter would walk the parsed pattern.
- **`run-suite.sh`**: invoke as `bash run-suite.sh` — the exec bit may be unset on some checkouts.
- **Datasets:** `GenData.java` (seeded, token-varied, selectivity-realistic) is the MWE generator;
  `scripts/bib-file-generator.py` is the separate scale + Unicode-edge (vinculum) generator.
- `data/` and `out/` are git-ignored (regenerated from `GenData.java`).

## Pointers

| Path | Role |
|---|---|
| [`README.md`](README.md) | Run instructions, gate→candidate matrix, CSV schema, roadmap |
| `GenData.java` | Step 0 — deterministic EAV-TSV generator |
| `Common.java` | Step 0 — timing, CSV row, literal extraction, TSV reader |
| `SqliteRegex.java` | SQLite regex gate (naive UDF vs trigram pre-filter) |
| `run-suite.sh` | gen → run → CSV per candidate under `out/` |
| `Dockerfile` | Pinned/low-end/arm64 runner |
| `.github/workflows/search-backend-mwe.yml` | CI: manual (`workflow_dispatch`) + on-change |
| [`../../docs/decisions/0064-search-index-backend.md`](../../docs/decisions/0064-search-index-backend.md) | The ADR these MWEs serve |
| [`../../docs/requirements/search-backend.md`](../../docs/requirements/search-backend.md) | `req~search.backend.*~1` requirements |
| [#12708](https://github.com/JabRef/jabref/issues/12708) · [recommendation comment](https://github.com/JabRef/jabref/issues/12708#issuecomment-3947205759) | Source discussion + the recommendation the ADR diverges from |
