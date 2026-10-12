///usr/bin/env jbang "$0" "$@" ; exit $?
//DESCRIPTION Deterministic synthetic bibliographic dataset for the search-backend MWEs (ADR 0064 / #12708).
//DESCRIPTION Emits an EAV TSV: one "entryId<TAB>field<TAB>value" row per (entry, field).
//DESCRIPTION Usage: jbang GenData.java [count=100000] [outFile=data/bib-<count>.tsv]
//JAVA 25

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.BufferedWriter;
import java.util.Random;

// Pools chosen so the regex/substring query set has *meaningful* selectivity (neither 0% nor 100%):
// the "Smith" family exercises anchored prefixes, "Müller" the umlaut/normalisation case, and the
// "comput*" title words the infix-literal case. Everything is seeded, so runs are reproducible.
// Relationship to scripts/bib-file-generator.py: that script is the scale + Unicode-edge generator
// (100k templated entries, vinculum U+0304); this one trades templating for token variety and is
// Python-free so the Docker runner stays pure-JVM.

String[] firstNames = {
        "Alice", "Bob", "Carol", "David", "Erin", "Frank", "Grace", "Henrik",
        "Ingrid", "Jamal", "Kira", "Liam", "Mei", "Noah", "Olga", "Priya"
};

String[] surnames = {
        "Smith", "Smithson", "Smithfield", "Smithers", "Smitherman", "Smythe",
        "Schmidt", "Müller", "Muller", "Miller", "Meyer", "Garcia", "Johnson",
        "Williams", "Brown", "Jones", "Davis", "Nguyen", "Wang", "Li", "Zhang",
        "Kim", "Tanaka", "Rossi", "Kowalski", "Andersson", "Okafor", "Silva",
        "Ivanov", "Cohen"
};

String[] titleWords = {
        "computational", "computing", "computer", "system", "systems", "analysis",
        "network", "networks", "learning", "model", "models", "data", "algorithm",
        "distributed", "quantum", "neural", "optimization", "framework", "evaluation",
        "approach", "method", "design", "architecture", "performance", "scalable",
        "robust", "adaptive", "dynamic", "efficient", "empirical"
};

String[] journals = {
        "Journal of Computing", "Transactions on Systems", "Nature", "Science",
        "IEEE Software", "ACM Computing Surveys", "Data and Knowledge", "Machine Learning",
        "Information Sciences", "Software Practice and Experience", "Algorithmica",
        "Computational Linguistics"
};

String[] keywords = {
        "search", "indexing", "database", "retrieval", "fulltext", "regex",
        "normalization", "benchmark", "sqlite", "lucene", "trigram", "postgres",
        "performance", "evaluation", "latex", "unicode"
};

void main(String[] args) throws Exception {
    int n = args.length > 0 ? Integer.parseInt(args[0].replace("_", "")) : 100_000;
    Path out = Path.of(args.length > 1 ? args[1] : "data/bib-" + n + ".tsv");
    if (out.getParent() != null) {
        Files.createDirectories(out.getParent());
    }

    Random rnd = new Random(42);  // fixed seed -> byte-for-byte reproducible dataset
    try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
        for (int i = 1; i <= n; i++) {
            String id = "e" + i;

            int authorCount = 1 + rnd.nextInt(4);
            StringBuilder authors = new StringBuilder();
            for (int a = 0; a < authorCount; a++) {
                if (a > 0) {
                    authors.append(" and ");
                }
                authors.append(pick(firstNames, rnd)).append(' ').append(pick(surnames, rnd));
            }
            write(w, id, "author", authors.toString());

            int titleLen = 5 + rnd.nextInt(8);
            StringBuilder title = new StringBuilder();
            for (int t = 0; t < titleLen; t++) {
                if (t > 0) {
                    title.append(' ');
                }
                title.append(pick(titleWords, rnd));
            }
            write(w, id, "title", capitalize(title.toString()));

            write(w, id, "journal", pick(journals, rnd));
            write(w, id, "year", Integer.toString(1950 + rnd.nextInt(76)));

            int kwCount = 1 + rnd.nextInt(5);
            StringBuilder kw = new StringBuilder();
            for (int k = 0; k < kwCount; k++) {
                if (k > 0) {
                    kw.append(", ");
                }
                kw.append(pick(keywords, rnd));
            }
            write(w, id, "keywords", kw.toString());
        }
    }
    System.err.println("Wrote " + n + " entries (" + (n * 5L) + " field rows) to " + out);
}

String pick(String[] pool, Random rnd) {
    return pool[rnd.nextInt(pool.length)];
}

String capitalize(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
}

void write(BufferedWriter w, String id, String field, String value) throws Exception {
    String safe = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    w.write(id);
    w.write('\t');
    w.write(field);
    w.write('\t');
    w.write(safe);
    w.write('\n');
}
