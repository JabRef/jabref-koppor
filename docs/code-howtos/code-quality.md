---
parent: Code Howtos
---
# Code Quality

## Formatting

Java sources are formatted with [jfmt](https://github.com/Netflix/jfmt), a formatter for modern Java built on google-java-format that follows the [Code Conventions for the Java Programming Language](https://www.oracle.com/java/technologies/javase/codeconventions-introduction.html).
jfmt has exactly one style and no configuration. The main properties are:

* 4 spaces for block indentation, 8 spaces for continuation lines.
* No maximum line length. Lines are broken at syntactic boundaries (method chains, argument lists, binary operators) only when the construct is "long enough" structurally.
* Javadoc prose is wrapped at 80 columns. Line and block comments are left as they are.
* Braces are added around single-statement `if`, `else`, `for`, `while`, and `do` bodies.
* Imports are ordered `java.*` and `javax.*` first, then all other imports in one block, then static imports. Unused imports are removed and wildcard imports are expanded.

Run it with

```bash
./gradlew jfmt
```

The CI job "format" runs `./gradlew jfmtCheck` and fails when any file would be rewritten. The job uploads the diff as a patch artifact.
`jfmtCheck` is also part of `./gradlew check`.

The Gradle integration lives in `build-logic/src/main/kotlin/org.jabref.gradle.check.jfmt.gradle.kts`.
It passes the compile class path of each source set to jfmt, because jfmt resolves the types referenced in declarations before it touches imports.
When the class path is incomplete, jfmt still formats, but skips the type-aware import normalization.

### IntelliJ

jfmt has no IntelliJ plugin. The project code style in `.idea/codeStyles/Project.xml` is kept close to jfmt's output (indentation, import layout), so that typing and "Reformat code" stay compatible, but it is not the source of truth.
Set up an [external tool](https://www.jetbrains.com/help/idea/configuring-third-party-tools.html) that formats the current file, as described in [Step 3 of the IntelliJ setup](../getting-into-the-code/guidelines-for-setting-up-a-local-workspace/intellij-13-code-style.md#format-with-jfmt).

### OpenRewrite

OpenRewrite (see below) rewrites code with its own layout. Run `./gradlew jfmt` after `./gradlew rewriteRun`.

## Code style checkers

JabRef has three code style checkers in place:

* [Checkstyle](https://checkstyle.sourceforge.io/) for basic checks, such as wrong import order. The checkstyle rules that overlap with formatting are aligned with jfmt's output.
* [Gradle Modernizer Plugin](https://github.com/andygoossens/gradle-modernizer-plugin#gradle-modernizer-plugin) for Java library usage checks.
  It ensures that "modern" Java concepts are used (e.g., [one should use `Deque` instead of `Stack`](https://stackoverflow.com/a/73021741/873282)).
* [OpenRewrite](https://docs.openrewrite.org/) for advanced rules. OpenRewrite can also automatically fix issues.
  JabRef's CI toolchain does NOT automatically rewrite the source code, but checks whether OpenRewrite would rewrite something.
  As developer, one can execute `./gradlew rewriteRun` to fix the issues.
  Gradle itself has to run on JDK 25 for this task: OpenRewrite parses with the JVM running Gradle, not with the toolchain, and both older and newer JDKs misparse the sources.
  Note that [JabRef is available on the Moderne platform](https://app.moderne.io/organizations/JabRef/jabref?branch=main&origin=github.com), too.

In case a check fails, [the CI](https://github.com/JabRef/jabref/blob/main/.github/workflows/tests.yml#L24C6-L24C6) automatically adds a comment on the pull request.

## Monitoring

We monitor the general source code quality at three places:

* [codacy](https://www.codacy.com) is a hosted service to monitor code quality. It thereby combines the results of available open source code quality checkers such as [Checkstyle](https://checkstyle.sourceforge.io) or [PMD](https://pmd.github.io). The code quality analysis for JabRef is available at [https://app.codacy.com/gh/JabRef/jabref/dashboard](https://app.codacy.com/gh/JabRef/jabref/dashboard), especially the [list of open issues](https://app.codacy.com/gh/JabRef/jabref/issues/index). In case a rule feels wrong, it is most likely a PMD rule.
* [codecov](https://codecov.io) is a solution to check code coverage of test cases. The code coverage metrics for JabRef are available at [https://codecov.io/github/JabRef/jabref](https://codecov.io/github/JabRef/jabref).
* [Teamscale](https://www.cqse.eu/en/teamscale/overview/) is a popular German product analyzing code quality. The analysis results are available at <https://demo.teamscale.com/findings.html#/jabref/?>.

## Up to date dependencies

We believe that updated dependencies are a sign of maintained code and thus an indicator of good quality.
We use [GitHub's dependabot](https://docs.github.com/en/code-security/getting-started/dependabot-quickstart-guide) to keep our versions up to date.

Moreover, we try to test JabRef with the latest Java Development Kit (JDK) builds.
Our results can be seen at the [Quality Outreach page](https://wiki.openjdk.org/display/quality/Quality+Outreach).

## Statistics

* [![Test Status](https://github.com/JabRef/jabref/workflows/Tests/badge.svg)](https://github.com/JabRef/jabref/actions?query=workflow%3ATests)
* [![codecov.io](https://codecov.io/github/JabRef/jabref/coverage.svg?branch=master)](https://codecov.io/github/JabRef/jabref?branch=main)

## Background literature

We strongly recommend reading following two books on code quality:

* [Java by Comparison](http://java.by-comparison.com) is a book by three JabRef developers which focuses on code improvements close to single statements. It is fast to read and one gains much information from each recommendation discussed in the book.
* [Effective Java](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/) is the standard book for advanced Java programming. Did you know that `enum` is the [recommended way to enforce a singleton instance of a class](https://learning.oreilly.com/library/view/effective-java-3rd/9780134686097/ch2.xhtml#lev3)? Did you know that one should [refer to objects by their interfaces](https://learning.oreilly.com/library/view/effective-java-3rd/9780134686097/ch9.xhtml#lev64)?

The principles we follow to ensure high code quality in JabRef is stated at our [Development Strategy](../getting-into-the-code/development-strategy.md).
