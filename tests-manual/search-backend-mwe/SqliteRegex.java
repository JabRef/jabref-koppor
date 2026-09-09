///usr/bin/env jbang "$0" "$@" ; exit $?
//DESCRIPTION SQLite + FTS5 trigram MWE for ADR 0064: regex via the naive REGEXP UDF (full scan)
//DESCRIPTION vs. a literal-extraction trigram pre-filter. Closes the SQLite evaluation's top risk
//DESCRIPTION (regex throughput). Usage: jbang SqliteRegex.java [data/bib-100000.tsv]
//JAVA 25
//RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED
//DEPS org.xerial:sqlite-jdbc:3.53.2.0
//SOURCES Common.java

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.sqlite.Function;

// The query set is chosen to span the full range of literal-extractability, because that -- not raw
// engine speed -- is what decides the regex gate:
//   .*comput.*  infix literal  -> "comput"  (good pre-filter)
//   Smith       bare literal    -> "Smith"   (good pre-filter)
//   ^Smit       anchored prefix -> "Smit"    (good pre-filter, case-sensitive verify on top)
//   M[üu]ller   literal after a class -> "ller" (pre-filter narrows, REGEXP verifies the class)
//   [0-9]{4}    year-like       -> NONE      (no >=3-char literal -> falls back to full scan)
//   ^.{4}$      fixed length    -> NONE      (the pathological no-literal case the ADR calls out)
String[] queries = {".*comput.*", "Smith", "^Smit", "M[üu]ller", "[0-9]{4}", "^.{4}$"};

void main(String[] args) throws Exception {
    Path tsv = Path.of(args.length > 0 ? args[0] : "data/bib-100000.tsv");
    if (!Files.exists(tsv)) {
        System.err.println("Dataset not found: " + tsv + "  (run GenData.java first)");
        System.exit(2);
    }
    int datasetSize = Common.datasetSizeFromName(tsv);

    Path db = Files.createTempFile("sqlite-mwe-", ".db");
    Files.deleteIfExists(db);   // let SQLite create it fresh
    try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db)) {
        registerRegexp(c);
        long buildMs = build(c, tsv);
        long indexBytes = Files.size(db);
        System.err.println("Built index in " + buildMs + " ms; db file " + indexBytes + " bytes");

        System.out.println(Common.Result.csvHeader());
        for (String q : queries) {
            Optional<String> literal = Common.longestLiteral(q);

            Common.Timing naive = Common.time(2, 10, () -> countNaive(c, q));
            emit(new Common.Result("sqlite", datasetSize, "regex-query", "naive:" + q,
                    naive.p50ms(), naive.p95ms(), buildMs, indexBytes, naive.matches(),
                    "full REGEXP UDF scan over all field rows"));

            if (literal.isPresent()) {
                String lit = literal.get();
                Common.Timing pre = Common.time(2, 10, () -> countPrefiltered(c, lit, q));
                String note = "literal='" + lit + "'"
                        + (pre.matches() == naive.matches() ? "" : " MISMATCH vs naive=" + naive.matches());
                emit(new Common.Result("sqlite", datasetSize, "regex-query", "trigram-prefilter:" + q,
                        pre.p50ms(), pre.p95ms(), buildMs, indexBytes, pre.matches(), note));
            } else {
                emit(new Common.Result("sqlite", datasetSize, "regex-query", "trigram-prefilter:" + q,
                        -1, -1, buildMs, indexBytes, -1,
                        "no >=3-char literal -> no pre-filter possible; cost = naive scan above"));
            }
        }
    } finally {
        Files.deleteIfExists(db);
        Files.deleteIfExists(Path.of(db + "-journal"));
    }
}

void emit(Common.Result r) {
    System.out.println(r.toCsv());
}

/// Build the EAV table and a trigram FTS5 index over the values; return build time in ms.
long build(Connection c, Path tsv) throws Exception {
    long t0 = System.nanoTime();
    try (Statement s = c.createStatement()) {
        s.execute("PRAGMA synchronous=OFF");     // throwaway benchmark DB; durability irrelevant
        s.execute("PRAGMA temp_store=MEMORY");
        s.execute("CREATE TABLE field (name TEXT, value TEXT)");
    }
    c.setAutoCommit(false);
    try (PreparedStatement ps = c.prepareStatement("INSERT INTO field(name, value) VALUES (?, ?)")) {
        int[] batched = {0};
        Common.forEachField(tsv, (id, name, value) -> {
            try {
                ps.setString(1, name);
                ps.setString(2, value);
                ps.addBatch();
                if (++batched[0] % 10_000 == 0) {
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        ps.executeBatch();
    }
    c.commit();
    c.setAutoCommit(true);
    try (Statement s = c.createStatement()) {
        // External-content trigram index; 'rebuild' populates it from the content table in one pass.
        s.execute("CREATE VIRTUAL TABLE field_fts USING fts5(value, content='field', tokenize='trigram')");
        s.execute("INSERT INTO field_fts(field_fts) VALUES('rebuild')");
        s.execute("ANALYZE");
    }
    return (System.nanoTime() - t0) / 1_000_000;
}

/// Naive path: REGEXP runs as a per-row Java callback over every field value -- no index.
long countNaive(Connection c, String regex) {
    try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM field WHERE value REGEXP ?")) {
        ps.setString(1, regex);
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}

/// Pre-filtered path: the trigram index returns substring candidates, REGEXP verifies the survivors.
/// This is the same architecture pg_trgm and Lucene's RegexpQuery+NGram use.
long countPrefiltered(Connection c, String literal, String regex) {
    String sql = "SELECT count(*) FROM field_fts f JOIN field d ON d.rowid = f.rowid "
            + "WHERE f MATCH ? AND d.value REGEXP ?";
    try (PreparedStatement ps = c.prepareStatement(sql)) {
        ps.setString(1, '"' + literal.replace("\"", "\"\"") + '"');   // phrase-quote for the trigram MATCH
        ps.setString(2, regex);
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    } catch (SQLException e) {
        throw new RuntimeException(e);
    }
}

/// Register a `regexp(pattern, value)` UDF backing the `value REGEXP pattern` operator,
/// compiling to cached java.util.regex patterns -- semantically identical to the in-memory backend.
void registerRegexp(Connection c) throws SQLException {
    Map<String, Pattern> cache = new HashMap<>();
    Function.create(c, "REGEXP", new Function() {
        @Override
        protected void xFunc() throws SQLException {
            String pattern = value_text(0);
            String text = value_text(1);
            if (pattern == null || text == null) {
                result(0);
                return;
            }
            Pattern p = cache.computeIfAbsent(pattern, Pattern::compile);
            result(p.matcher(text).find() ? 1 : 0);
        }
    });
}
