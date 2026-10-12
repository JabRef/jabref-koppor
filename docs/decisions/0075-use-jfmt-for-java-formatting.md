---
nav_order: 75
parent: Decision Records
status: proposed
date: 2026-10-12
---
# Use jfmt for Java Formatting

## Context and Problem Statement

JabRef's Java formatting was defined by an IntelliJ code style (`.idea/codeStyles/Project.xml`) and enforced in CI by running IntelliJ IDEA headless in a Docker container.
Checkstyle checked a subset of the style (import order, whitespace, braces).
OpenRewrite recipes rewrite code with yet another layout, which then had to be reformatted again.

Contributors without IntelliJ had no way to produce the expected formatting locally.
IntelliJ's formatter is also not deterministic across IDE versions, and the CI check depended on a third-party Docker image.

Which formatter should define JabRef's Java formatting?

## Decision Drivers

* Runnable from the command line and from Gradle, independent of the IDE
* Deterministic and idempotent output
* Close to the existing JabRef style (4-space indentation, 8-space continuation, braces always, no hard line length limit)
* Supports current Java syntax (JDK 25, Markdown Javadoc `///`)

## Considered Options

* [jfmt](https://github.com/Netflix/jfmt)
* [google-java-format](https://github.com/google/google-java-format) (AOSP style)
* [Spotless](https://github.com/diffplug/spotless) with the Eclipse formatter
* [palantir-java-format](https://github.com/palantir/palantir-java-format)
* Keep IntelliJ as the formatter

## Decision Outcome

Chosen option: "jfmt", because it implements a fixed OpenJDK/Sun style that matches JabRef's existing conventions (4/8 indentation, braces around single statements, no fixed line length), it is deterministic, and it runs from Gradle on the JDK already required for the build.

### Consequences

* Good, because `./gradlew jfmt` produces the expected formatting on every machine.
* Good, because the CI check (`./gradlew jfmtCheck`) no longer needs Docker or a headless IDE.
* Good, because unused imports are removed and wildcard imports are expanded with type information (jfmt resolves types via javac).
* Bad, because the initial reformatting touches nearly every Java file. Open pull requests need to be reformatted when merging `main`.
* Bad, because Javadoc prose is wrapped at 80 columns, which differs from the previous unrestricted line length.
* Bad, because there is no IntelliJ plugin. IntelliJ's own formatter is kept close to jfmt's output, but jfmt has to be run (as external tool or Gradle task) before committing.
* Bad, because jfmt is in preview (version 0.8.x) and has rough edges. Known at the time of writing: a block containing only a block comment does not converge, a trailing comma in an array initializer yields `",}"`, and the relocated Markdown parser needs the original `commonmark` jar on the class path to parse HTML entities in `///` comments.

### Confirmation

The CI job "format" runs `./gradlew jfmtCheck`. The task is also part of `./gradlew check`.

## Pros and Cons of the Options

### jfmt

* Good, because the style matches JabRef's conventions (OpenJDK style).
* Good, because it handles `///` Markdown Javadoc.
* Good, because it needs no configuration.
* Bad, because it is in preview and has no IDE integration.
* Bad, because it needs the compile class path for full import normalization.

### google-java-format (AOSP style)

* Good, because mature and widely used, with an IntelliJ plugin.
* Bad, because of the 100-column limit and the resulting dense wrapping of method chains and lambdas.
* Bad, because static imports are placed first.

### Spotless with the Eclipse formatter

* Good, because highly configurable; the existing Eclipse style in `config/` could be reused.
* Bad, because the configuration has to be maintained, and the Eclipse formatter's output is often not idempotent with respect to comments.

### palantir-java-format

* Good, because it is a google-java-format derivative with 120 columns and a more readable lambda layout.
* Bad, because it keeps the 2-space/4-space indentation of google-java-format.

### Keep IntelliJ as the formatter

* Good, because nothing changes for IntelliJ users.
* Bad, because CI depends on a headless IDE in Docker.
* Bad, because the result depends on the IDE version.
