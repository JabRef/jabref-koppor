package org.jabref.logic.biblatex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.annotations.VisibleForTesting;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Finds the fields a biblatex style reads by scanning its field-access macros.
///
/// A biblatex style is a pair of a bibliography style (`.bbx`) and a citation style (`.cbx`); both are scanned when they lie
/// next to each other. Styles pulled in via `\RequireBibliographyStyle` and `\RequireCitationStyle` are scanned too; they are
/// looked up next to the style and, failing that, via `kpsewhich` of an installed TeX distribution. The macros biblatex
/// itself defines (`biblatex.def`) are covered the same way, so without a TeX distribution only the style files are scanned.
@NullMarked
public class BiblatexStyleScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BiblatexStyleScanner.class);

    /// Macros reading a field, a name list or a literal list; the field name is the last brace group
    private static final Pattern FIELD_ACCESS = Pattern.compile(
            "\\\\(?:printfield|printnames|printlist|iffieldundef|ifnameundef|iflistundef|iffieldequalstr|iffieldequals|iffieldint|iffieldnum|iffieldbibstring|ifnamesequal|iflistsequal|thefield|strfield|thelist|thename)(?:\\[[^\\]]*])*\\{([a-z]+)}");
    private static final Pattern DATE_ACCESS = Pattern.compile("\\\\print(url|orig|event)?date(?:extra)?\\b");
    private static final Pattern REQUIRE = Pattern.compile("\\\\Require(Bibliography|Citation)Style\\{([^}]*)}");
    private static final Pattern COMMENT = Pattern.compile("(?<!\\\\)%.*");

    /// Fields biblatex derives from other fields
    private static final Map<String, Set<String>> DERIVED_FIELDS = Map.of(
            "labelname", Set.of("author", "editor", "translator"),
            "labeltitle", Set.of("title"),
            "labelyear", Set.of("date", "year"),
            "labelmonth", Set.of("date", "month"),
            "labelday", Set.of("date", "day"),
            "labeldate", Set.of("date", "year", "month", "day"));

    private final Set<Path> visited = new HashSet<>();
    private final Set<String> fields = new HashSet<>();
    private final Function<String, Optional<Path>> installedFileLookup;

    private BiblatexStyleScanner(Function<String, Optional<Path>> installedFileLookup) {
        this.installedFileLookup = installedFileLookup;
    }

    /// @param styleFile a `.bbx` or `.cbx` file
    /// @return the lower-cased names of the fields the style reads, with derived label fields resolved to their sources
    public static Set<String> scanFields(Path styleFile) throws IOException {
        return scanFields(styleFile, BiblatexStyleScanner::kpsewhich);
    }

    /// @param installedFileLookup finds a file of the TeX distribution by name
    @VisibleForTesting
    static Set<String> scanFields(Path styleFile, Function<String, Optional<Path>> installedFileLookup) throws IOException {
        BiblatexStyleScanner scanner = new BiblatexStyleScanner(installedFileLookup);
        Deque<Path> pending = new ArrayDeque<>();
        pending.add(styleFile);
        siblingStyle(styleFile).ifPresent(pending::add);
        // biblatex loads its own macro definitions before any style; most bibmacros printing fields live there
        installedFileLookup.apply("biblatex.def").ifPresent(pending::add);
        while (!pending.isEmpty()) {
            Path file = pending.poll().toAbsolutePath().normalize();
            if (!scanner.visited.add(file)) {
                continue;
            }
            String source = COMMENT.matcher(Files.readString(file)).replaceAll("");
            scanner.collectFields(source);
            Matcher require = REQUIRE.matcher(source);
            while (require.find()) {
                String extension = "Bibliography".equals(require.group(1)) ? ".bbx" : ".cbx";
                for (String name : require.group(2).split(",")) {
                    scanner.resolve(file.getParent(), name.strip() + extension).ifPresent(pending::add);
                }
            }
        }
        return scanner.fields;
    }

    private void collectFields(String source) {
        Matcher access = FIELD_ACCESS.matcher(source);
        while (access.find()) {
            String name = access.group(1);
            fields.addAll(DERIVED_FIELDS.getOrDefault(name, Set.of(name)));
        }
        Matcher date = DATE_ACCESS.matcher(source);
        while (date.find()) {
            fields.add(Optional.ofNullable(date.group(1)).orElse("") + "date");
        }
    }

    /// The `.cbx` belonging to a `.bbx` and vice versa
    private static Optional<Path> siblingStyle(Path styleFile) {
        String name = styleFile.getFileName().toString();
        String lowerName = name.toLowerCase(Locale.ROOT);
        String base = name.substring(0, name.length() - 4);
        Path sibling = lowerName.endsWith(".bbx") ? styleFile.resolveSibling(base + ".cbx")
                       : lowerName.endsWith(".cbx") ? styleFile.resolveSibling(base + ".bbx")
                       : styleFile;
        return Optional.of(sibling).filter(file -> !file.equals(styleFile) && Files.isRegularFile(file));
    }

    private Optional<Path> resolve(Path directory, String fileName) {
        Path local = directory.resolve(fileName);
        if (Files.isRegularFile(local)) {
            return Optional.of(local);
        }
        Optional<Path> installed = installedFileLookup.apply(fileName);
        if (installed.isEmpty()) {
            LOGGER.info("Required style {} not found, its fields are not known", fileName);
        }
        return installed;
    }

    private static Optional<Path> kpsewhich(String fileName) {
        try {
            Process kpsewhich = new ProcessBuilder("kpsewhich", fileName).redirectErrorStream(true).start();
            String output = new String(kpsewhich.getInputStream().readAllBytes()).strip();
            if (kpsewhich.waitFor(5, TimeUnit.SECONDS) && kpsewhich.exitValue() == 0 && !output.isEmpty()) {
                return Optional.of(Path.of(output.lines().toList().getFirst()));
            }
        } catch (IOException _) {
            LOGGER.debug("kpsewhich is not available, {} is not scanned", fileName);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }
}
