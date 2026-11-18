#!/usr/bin/env bash
# Runs the search-backend MWE suite end to end and collects one comparable CSV per candidate.
# Usage: ./run-suite.sh [entryCount=100000]
# Env:   OUT=<dir> (default ./out)
set -euo pipefail
cd "$(dirname "$0")"

N="${1:-100000}"
OUT="${OUT:-out}"
mkdir -p "$OUT" data

echo ">> Generating dataset ($N entries)…" >&2
jbang GenData.java "$N" "data/bib-${N}.tsv"

echo ">> SQLite regex MWE…" >&2
jbang SqliteRegex.java "data/bib-${N}.tsv" | tee "$OUT/sqlite-regex-${N}.csv"

# Candidates still to land (see README roadmap): Lucene, in-memory, embedded-PostgreSQL,
# SQLite PDF-fulltext. Each appends its own CSV here once written.

echo ">> Done. Results in $OUT/" >&2
