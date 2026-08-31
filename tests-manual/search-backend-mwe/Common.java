import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

/// Shared helpers for the search-backend MWEs (ADR 0064 / issue #12708).
///
/// Plain named class (no `main`); pulled into each MWE via `//SOURCES Common.java`,
/// so every candidate emits the *same* CSV schema and is timed the *same* way.
/// These are deliberately MWE-grade; the committed numbers live in the #15385 JMH harness.
class Common {

    /// One row of the uniform CSV every MWE prints. `-1` means "not applicable".
    record Result(
            String candidate,    // sqlite | lucene | in-memory | embedded-pg
            int datasetSize,     // number of entries (not field rows)
            String operation,    // regex-query | substring-query | reindex | ...
            String variant,      // naive | trigram-prefilter | <query> | ...
            double p50ms,
            double p95ms,
            long buildMs,
            long indexBytes,
            long matches,        // result-set size, as a correctness sanity check
            String notes) {

        static String csvHeader() {
            return "candidate,dataset_size,operation,variant,p50_ms,p95_ms,build_ms,index_bytes,matches,notes";
        }

        String toCsv() {
            return String.join(",",
                    candidate,
                    Integer.toString(datasetSize),
                    csv(operation),
                    csv(variant),
                    fmt(p50ms), fmt(p95ms),
                    Long.toString(buildMs),
                    Long.toString(indexBytes),
                    Long.toString(matches),
                    csv(notes));
        }

        private static String fmt(double d) {
            return d < 0 ? "" : String.format(Locale.ROOT, "%.3f", d);
        }

        private static String csv(String s) {
            if (s == null) {
                return "";
            }
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0) {
                return '"' + s.replace("\"", "\"\"") + '"';
            }
            return s;
        }
    }

    record Timing(double p50ms, double p95ms, long matches) {}

    /// Run `body` `warmup` times (discarded) then `iters` times (timed); report p50/p95 in ms.
    /// `body` returns a match count so callers can assert the two strategies agree.
    static Timing time(int warmup, int iters, LongSupplier body) {
        for (int i = 0; i < warmup; i++) {
            body.getAsLong();
        }
        long[] ns = new long[iters];
        long matches = 0;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            matches = body.getAsLong();
            ns[i] = System.nanoTime() - t0;
        }
        Arrays.sort(ns);
        double p50 = ns[iters / 2] / 1_000_000.0;
        double p95 = ns[(int) Math.min(iters - 1, Math.round(iters * 0.95))] / 1_000_000.0;
        return new Timing(p50, p95, matches);
    }

    /// MWE-grade literal extraction: the longest run of non-meta characters in `regex`
    /// that is guaranteed to appear verbatim in any match, so it can drive an FTS5 trigram
    /// pre-filter. Returns empty when no run of >= 3 chars qualifies (the regex then has to
    /// fall back to a full REGEXP scan -- exactly the honest weakness the ADR records).
    ///
    /// Heuristic, not a regex parser: it skips escapes (`\d`), ignores character-class
    /// contents (`[a-z]`), and drops a character made optional by a trailing `?`/`*`.
    /// FTS5 trigram needs >= 3 characters, hence the threshold. The production pre-filter
    /// would walk the parsed pattern; this is enough to measure the mechanism.
    static Optional<String> longestLiteral(String regex) {
        StringBuilder run = new StringBuilder();
        String best = "";
        boolean inClass = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }
            char next = i + 1 < regex.length() ? regex.charAt(i + 1) : '\0';
            if (c == '\\') {                 // escape -> ends the run, skip the escaped char
                best = longer(best, run);
                i++;
            } else if (c == '[') {           // character class -> ends the run, skip contents
                best = longer(best, run);
                inClass = true;
            } else if (".(){}*+?|^$".indexOf(c) >= 0) {   // metacharacter -> ends the run
                best = longer(best, run);
            } else if (next == '?' || next == '*') {       // optional char -> not guaranteed present
                best = longer(best, run);
            } else {
                run.append(c);
            }
        }
        best = longer(best, run);
        return best.length() >= 3 ? Optional.of(best) : Optional.empty();
    }

    private static String longer(String best, StringBuilder run) {
        String r = run.toString();
        run.setLength(0);
        return r.length() > best.length() ? r : best;
    }

    /// Stream (entryId, field, value) rows from a generated EAV TSV (see GenData.java).
    static void forEachField(Path tsv, FieldConsumer consumer) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(tsv, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                int t1 = line.indexOf('\t');
                int t2 = line.indexOf('\t', t1 + 1);
                if (t1 < 0 || t2 < 0) {
                    continue;
                }
                consumer.accept(line.substring(0, t1), line.substring(t1 + 1, t2), line.substring(t2 + 1));
            }
        }
    }

    @FunctionalInterface
    interface FieldConsumer {
        void accept(String entryId, String field, String value);
    }

    /// Parse the entry count out of a `bib-<N>.tsv` file name; -1 if not encoded.
    static int datasetSizeFromName(Path tsv) {
        var m = java.util.regex.Pattern.compile("bib-(\\d+)").matcher(tsv.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
