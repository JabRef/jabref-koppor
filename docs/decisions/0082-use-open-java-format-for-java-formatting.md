---
nav_order: 82
parent: Decision Records
status: proposed
date: 2026-11-12
---
# Use open-java-format for Java formatting

## Context and Problem Statement

JabRef's Java formatting is defined by an IntelliJ code style (`.idea/codeStyles/Project.xml`).
CI enforces it by running a headless IntelliJ IDEA in a Docker container.
Contributors without IntelliJ cannot reproduce the result, the output changes with the IDE version (IntelliJ 2026.2.1 broke `///` comments and JBang headers), and OpenRewrite output has to be reformatted by the same container.

Which formatter should define JabRef's Java formatting?

## Decision Drivers

* Same output in the IDE, on the command line, in Gradle, and in CI
* Deterministic and idempotent output, no style configuration to maintain
* Close to JabRef's existing style: 4-space indentation, 8-space continuation, long lines allowed
* Current Java syntax (JDK 25), Markdown Javadoc (`///`), JBang scripts
* Fast enough to run after every edit, also by AI agents

## Considered Options

* [open-java-format](https://openjavaformat.dev/)
* [jfmt](https://github.com/Netflix/jfmt) (tried in [jabref-koppor#787](https://github.com/JabRef/jabref-koppor/pull/787))
* [google-java-format](https://github.com/google/google-java-format)
* [palantir-java-format](https://github.com/palantir/palantir-java-format)
* Keep IntelliJ IDEA as the formatter

## Decision Outcome

Chosen option: "open-java-format", because it is the only option that combines JabRef's 4/8 indentation and 120 columns with an IntelliJ plugin, a native binary for CI, and correct handling of JBang directives.
It is a maintained fork of palantir-java-format; Guillaume Laforge's [comparison of Java formatters](https://glaforge.dev/posts/2026/11/09/comparing-java-formatters-why-open-java-format-is-my-new-favorite/) reaches the same conclusion.

### Consequences

* Good, because "Reformat Code" in IntelliJ (with the open-java-format plugin), `./gradlew formatDiff`, and CI produce identical files.
* Good, because CI downloads one checksum-verified native binary (version pinned in `.open-java-format-version`) instead of running an IDE in Docker, and needs no JDK.
* Good, because unused imports are removed and imports are sorted without any configuration.
* Bad, because the initial reformatting touches nearly every Java file: open pull requests have to merge `main` and reformat.
* Bad, because the import layout changes (static imports first, then one ASCII-sorted block), and Checkstyle had to follow.
* Bad, because annotations on fields are put on their own line (`@FXML` above `private Label label;`).
* Bad, because JBang directives (`//DEPS`, ...) are only kept as they are when they come before the first import.
* Bad, because version 21.0.2 crashes on one string concatenation in `BibFileMergerTest` ("Something has gone terribly wrong"); the test now uses a single text block.

### Confirmation

The CI job "format" runs open-java-format on all tracked Java files and fails if a file changes.
Checkstyle accepts open-java-format's output.

## Examples

### A JabRef snippet: IntelliJ (before), jfmt, open-java-format

From `org.jabref.logic.os.OS`.

IntelliJ (current `main`):

```java
import java.util.Objects;

import org.jabref.logic.util.strings.StringUtil;

import com.github.javakeyring.Keyring;
import mslinks.ShellLink;

public static final List<Charset> ENCODINGS = Charset.availableCharsets().values().stream().distinct().toList();

} catch (IOException |
         ShellLinkException e) {
```

jfmt 0.8.4:

```java
import java.util.Objects;

import com.github.javakeyring.Keyring;
import mslinks.ShellLink;
import org.jabref.logic.util.strings.StringUtil;

public static final List<Charset> ENCODINGS = Charset.availableCharsets()
        .values()
        .stream()
        .distinct()
        .toList();

} catch (IOException | ShellLinkException e) {
```

open-java-format 21.0.2:

```java
import com.github.javakeyring.Keyring;
import java.util.Objects;
import mslinks.ShellLink;
import org.jabref.logic.util.strings.StringUtil;

public static final List<Charset> ENCODINGS =
        Charset.availableCharsets().values().stream().distinct().toList();

} catch (IOException | ShellLinkException e) {
```

### Typical JabRef code: google-java-format, open-java-format

Input (formatted by IntelliJ with JabRef's code style):

```java
@FXML private Label titleLabel;

public List<String> titles(List<BibEntry> entries) {
    return entries.stream().filter(entry -> entry.hasField(StandardField.AUTHOR)).map(entry -> entry.getField(TITLE)).flatMap(Optional::stream).toList();
}

public boolean isRelevant(BibEntry entry, String citationKey) {
    return entry.getCitationKey().isPresent() && entry.getCitationKey().get().equals(citationKey) &&
            !entry.getField(StandardField.KEYWORDS).orElse("").contains("ignore");
}
```

google-java-format 2.0.0 (default style: 2-space indentation, 100 columns):

```java
  @FXML private Label titleLabel;

  public List<String> titles(List<BibEntry> entries) {
    return entries.stream()
        .filter(entry -> entry.hasField(StandardField.AUTHOR))
        .map(entry -> entry.getField(TITLE))
        .flatMap(Optional::stream)
        .toList();
  }
```

`--aosp` gives 4/8 indentation, but keeps 100 columns.

open-java-format 21.0.2:

```java
@FXML
private Label titleLabel;

public List<String> titles(List<BibEntry> entries) {
    return entries.stream()
            .filter(entry -> entry.hasField(StandardField.AUTHOR))
            .map(entry -> entry.getField(TITLE))
            .flatMap(Optional::stream)
            .toList();
}

public boolean isRelevant(BibEntry entry, String citationKey) {
    return entry.getCitationKey().isPresent()
            && entry.getCitationKey().get().equals(citationKey)
            && !entry.getField(StandardField.KEYWORDS).orElse("").contains("ignore");
}
```

## Pros and Cons of the Options

### open-java-format

* Good, because 4/8 indentation and 120 columns are close to JabRef's style.
* Good, because of plugins for IntelliJ and Eclipse, a Gradle plugin (`formatDiff`), native binaries, and a GitHub Action.
* Good, because it keeps JBang directives and `$NON-NLS$` markers, which palantir-java-format breaks.
* Good, because it needs no class path: each file is formatted on its own.
* Neutral, because it is a young fork; its output is palantir-java-format's plus bug fixes, so falling back to palantir-java-format is cheap.
* Bad, because Spotless has no built-in step for it (the Gradle plugin adds one).

### jfmt

* Good, because the OpenJDK style keeps more lines as written and has no column limit.
* Bad, because there is no IDE plugin: IntelliJ users have to run an external tool.
* Bad, because it needs JDK 25, seven `--add-exports`, and the compile class path of each source set.
* Bad, because version 0.8.4 had four bugs on JabRef's code base (see [jabref-koppor#787](https://github.com/JabRef/jabref-koppor/pull/787)), including "Formatting did not converge" on `{ /* comment */ }`.
* Bad, because Javadoc is wrapped at 80 columns.

### google-java-format

* Good, because it is the most widely used Java formatter, with plugins for every IDE and build tool.
* Bad, because of 2-space indentation (or 4/8 with `--aosp`) and 100 columns: JabRef's long lines would be wrapped densely.

### palantir-java-format

* Good, because the output is the same as open-java-format's on most code, with a large user base.
* Bad, because it breaks JBang headers (`///usr/bin/env jbang` and `//DEPS` get a space after the slashes) and `$NON-NLS$` comments.
* Bad, because the CLI needs a JVM with `--add-exports`.

### Keep IntelliJ IDEA as the formatter

* Good, because nothing changes for contributors.
* Bad, because CI depends on a headless IDE in a Docker image.
* Bad, because the output depends on the IDE version.
* Bad, because contributors without IntelliJ cannot format locally.

## More Information

This ADR supersedes the trial of jfmt in [jabref-koppor#787](https://github.com/JabRef/jabref-koppor/pull/787).
