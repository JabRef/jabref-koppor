# Switch JabRef build system to Jenesis — staged migration plan

## Context

JabRef currently builds with **Gradle (Kotlin DSL)** across 9 subprojects (`jablib`,
`jabkit`, `jabgui`, `jabsrv`, `jabsrv-cli`, `jabls`, `jabls-cli`, `test-support`,
`versions`). The goal is to switch to **Jenesis** (https://github.com/raphw/jenesis),
a Java-native, plugin-free build tool that treats `module-info.java` as a first-class
input. JabRef is already fully modular (it uses the GradleX `javaModules` plugins that
derive dependencies from `requires` directives), so the *modeling* is well aligned with
Jenesis.

- **Intermediate goal:** a working `jabkit` (CLI). Then `jabsrv`, then `jabgui`.
- **Approach:** start with an MWE, then `jablib` first.
- **Final goal:** Maven publishing works. IntelliJ integration is a **separate follow-up**
  once everything builds.

**Why this is non-trivial:** Jenesis is young (created 2024-10, ~45★, Apache-2.0,
by Rafael Winterhalter / Byte Buddy author) and *plugin-free by design*. JabRef's Gradle
build leans heavily on plugins that Jenesis has no turnkey equivalent for — most critically
`org.gradlex.extra-java-module-info`, which patches ~150 non-modular dependency jars into
real JPMS modules. That gap is the make-or-break risk and is validated first.

The working tree is on `switch-to-jenesis`, which now includes the `update-to-jdk26` merge:
the build targets **Java 26** (`build-logic/.../feature.compile.gradle.kts`: `release = 26`,
vendor Amazon).

## Decisions (confirmed with user)

1. **Both build systems run in parallel; eventually drop Gradle.** This is a migration, not a
   throwaway experiment. Gradle stays the working source of truth and is left untouched; Jenesis
   is built up alongside it (under `build/jenesis/` + `jenesis-spike/`). Keeping the two in
   parallel adds **no real overhead** — separate files, no shared state, Gradle keeps building the
   whole time. Once Jenesis reaches parity, Gradle is removed. Each phase ends in a GATE used as a
   progress checkpoint (proven → continue; blocked → fix or reassess — not abandon).
2. **Pre-patch helper as fallback** for the `extra-java-module-info` gap: if Jenesis can't
   natively patch non-modular jars into JPMS modules, add a build step that ASM-patches them
   (reusing GradleX logic or a standalone tool) and feed Jenesis already-modular jars.

## What Jenesis is (facts grounded from repo + demos — items marked ⚠ to confirm in Phase 0)

Run model: `java build/jenesis/Project.java [goal]` where goal ∈ `build` (compile/jar/test),
`pin` (resolve + record versions/checksums), `stage` (materialize Maven repo layout), `export`
(publish to local repo). Build sources ship under `build/jenesis/` (curl bootstrap / git
submodule / SDKMAN).

- **Modules** = directories containing `module-info.java`; dependencies = `requires`
  directives. Multi-module is auto-discovered by scanning the tree (demo-04).
- **Versions** live as per-module Javadoc pins: `@jenesis.pin <module> <version> SHA-256/<hex>`,
  auto-filled by the `pin` goal. **There is no central BOM/version-catalog.** ⚠ This is the
  opposite of JabRef's single `versions/` java-platform.
- **Layout**: demos use a flat layout (`module-info.java` + sources directly in the module
  dir). JabRef uses Maven layout (`src/main/java/module-info.java`). `MODULAR_TO_MAVEN` is the
  default and is documented to read `module-info.java`; ⚠ confirm it reads `src/main/java/...`
  or whether a layout/property is needed (demo-12 `module-layout`).
- **Executable** (demo-06): `@jenesis.main <class>` + `@jenesis.release <N>` Javadoc tags;
  app-image via `-Djenesis.java.jpackage=app-image` (needs a JDK with `jmods`); fat bundle via
  `-Djenesis.java.bundle=true`.
- **Publishing** (demo-22): `stage` emits `stage/maven` (jar + pom + sources + javadoc).
  POM metadata comes from `project.properties` (url, license.*, developer.*, scm.*) plus
  module-info Javadoc (name/description). **Signing + upload is delegated to JReleaser**, not
  Jenesis ("credentials and keys never enter the build").
- **Coordinate derivation**: group = first two dotted segments of the module name, artifact =
  full module name. So `org.jabref.jablib` → `org.jabref : org.jabref.jablib`. ⚠ JabRef
  publishes `org.jabref : jablib` today → an artifactId override is required (custom assembler,
  demo-13/17).
- **Customization**: no plugins. Extra steps (ANTLR, code generators, resource filtering) must
  become custom Java build steps via a custom assembler/`BuildExecutor` (demos 13, 17–19).
- **IDE**: no native IntelliJ integration documented; the build is plain Java the IDE can index.

Sources: https://github.com/raphw/jenesis , https://jenesis.build , and demo READMEs
(`demo/demo-04-java-modular-multi`, `-06-...-executable`, `-13-custom-assembler`,
`-16-external-module`, `-22-publishing`).

## The central risk — `extra-java-module-info`

`build-logic/src/main/kotlin/org.jabref.gradle.base.dependency-rules.gradle.kts` (~570 lines)
encodes metadata Jenesis currently has no expression for:

- ~150 `module("group:artifact", "module.name")` mappings turning **non-modular jars** into
  named JPMS modules, many with synthesized descriptors (`exportAllPackages()`, `requires(...)`,
  `requiresTransitive(...)`, `uses(...)`, `opens(...)`).
- `mergeJar(...)` — e.g. `dev.langchain4j:langchain4j-core` merges **6** sibling jars into one
  module.
- `patchRealModule()`, `preserveExisting()`, `overrideModuleName()` (e.g. Scala `fastparse_2.13`).
- Per-OS native variants for JavaFX (`javafx-*` → `win`/`linux`/`mac`(+`aarch64`) classifiers).
- JitPack variants (`com.github.sialcasa.mvvmFX:mvvmfx-validation`).
- POM dependency surgery (`removeDependency`/`addApiDependency`/`addRuntimeOnlyDependency`).
- `failOnAutomaticModules = true` — JabRef forbids automatic modules, which is also a hard
  requirement for `jlink`/`jpackage` (jlink refuses automatic modules). So the app-image goals
  **cannot** rely on automatic-module fallback; every jar must be a real module.

**Validation is front-loaded** (Phase 1). If Jenesis can't do this natively, the **pre-patch
helper** (Decision 2) produces already-modular jars from this exact catalog.

## Mismatch map (Gradle feature → Jenesis path → risk)

| JabRef Gradle feature | Where | Jenesis path | Risk |
|---|---|---|---|
| Modular multi-project | `settings.gradle.kts` `javaModules` | native (module dirs) | Low (layout ⚠) |
| Central dependency versions | `versions/build.gradle.kts` (java-platform BOM) | per-module `@jenesis.pin` (auto via `pin`), or custom shared version source | Medium (lose single BOM) |
| Module→coordinate map + non-modular jar patching | `dependency-rules.gradle.kts` (`extra-java-module-info`) | none built-in → **pre-patch helper** | **HIGH (make/break)** |
| Dependency conflict/patch rules | `jvmDependencyConflicts.patch{}` | re-express in helper / pinned closure | Medium |
| ANTLR grammar gen | `jablib` `generateGrammarSource` | custom build step (run antlr) | High |
| 3 code generators (journal MV, CSL catalog, LTWA MV) | `build-support/src/main/java/*.java` via JBang | custom build step / pre-gen | High |
| Resource filtering (`build.properties` + ~12 API keys, version, year, maintainers) | `jablib` `processResources` `expand{}` | custom step / pre-gen | Medium |
| App image (jlink/jpackage, 5 OS targets) | `targets.gradle.kts` `javaModulePackaging` | `@jenesis.main` + `-Djenesis.java.jpackage` (needs jmods JDK) | Medium |
| Maven publish + sign + sources/javadoc | `jablib` `mavenPublishing` (vanniktech) | `stage` + JReleaser; artifactId override | Medium |
| errorprone / NullAway | root + `jablib` | drop initially, or custom javac args | Low (defer) |
| checkstyle / modernizer / openrewrite | `build-logic` + root | run as separate external tools | Low (out of build) |
| jmh benchmarks | `jablib` | defer | Low |
| CycloneDX SBOM / OpenFastTrace | root `build.gradle.kts` | external / JReleaser / defer | Low |
| JitPack + JabRef-published deps (`afterburner.fx`, `easybind`, `mslinks`) | repos + coordinates | configure repos; pins | Medium |

## Repo layout (non-destructive, parallel)

```
switch-to-jenesis (branch)
  build.gradle.kts, build-logic/, settings.gradle.kts   <- UNTOUCHED (Gradle keeps working)
  build/jenesis/                                        <- vendored Jenesis (submodule/curl)
  jenesis-spike/
    mwe/            greeter + app (Phase 0)
    modulepatch/    pre-patch helper + generated patched-jar repo (Phase 1)
  project.properties                                    <- publishing metadata (Phase 5)
  jenesis.properties                                    <- project-level Jenesis options
```

Module source dirs (`jablib/src/main/java/...`, etc.) are reused in place; Jenesis is pointed at
them. Nothing under `jablib/`, `jabkit/`, etc. is moved unless Phase 0 proves a layout move is
unavoidable (then we symlink/copy in the spike, not rewrite the tree).

## Phases & gates

**Per-app-component packaging pattern (Phases 4, 6, 7).** Every runnable component goes through
three explicit, separately-checkable steps, each its own mini-gate:
1. **build** — compile + modular jar (on the module path).
2. **package binary** — primary: `jpackage` **app-image** (`-Djenesis.java.jpackage=app-image`)
   on a `jmods` JDK (Corretto 26), carrying the jlink options from `base.targets.gradle.kts`
   (`--compress zip-6`, `--no-header-files`, `--no-man-pages`, `--bind-services`). This is the
   same `jpackage` mechanism JabRef already uses (GradleX `java-module-packaging`), so it should
   work for **jabkit, jabsrv, and jabgui**. Alternative if Jenesis's jpackage wiring is missing:
   **JReleaser `assemble`** (it has `jpackage` / `jlink` / `native-image` assemblers) fed from
   the Jenesis `bundle`/`stage` output.
3. **smoke** — CLI components: `--help` exits 0 and prints usage. GUI: launches a window.

### Phase 0 — Environment + vendored Jenesis + MWE  *(prove the toolchain on this machine)*
- Vendor Jenesis under `build/jenesis/`; pick install method (git submodule preferred for
  reproducibility).
- JDK: the build targets **Java 26 (Amazon)**. Local **Temurin 26.0.1** matches the language
  level → fine for compile/run (Phases 0–3). It lacks `jmods` and bundles no separable JavaFX,
  so app-image phases need **Amazon Corretto 26** (jmods, no bundled JavaFX) — per the comment
  in `feature.compile.gradle.kts`.
- Build a 2-module toy in `jenesis-spike/mwe/` (`demo.greeter` lib + `demo.app` exe requiring it
  + one external dep, e.g. `org.slf4j`). Exercise `build`, `pin`, run via `Execute`/`@jenesis.main`.
- **Confirm the ⚠ items**: does Jenesis read `src/main/java/module-info.java` layout? exact pin
  tag syntax? `pin` resolving an external module name → Maven coordinate? Windows + JDK 26
  behavior?
- **GATE 0:** MWE compiles, pins, and runs; layout + CLI semantics documented. Else: fix the
  tooling/layout and continue. Gradle is unaffected throughout.

### Phase 1 — `jablib-lite` + the jar-patching verdict  *(make/break)*
- Run the **existing Gradle build once** to populate generated artifacts
  (`jablib/build/generated/**`: ANTLR sources + journal-list.mv + citation-style-catalog.json +
  ltwa-list.mv + filtered `build.properties`). This decouples "can Jenesis compile + resolve
  jablib's deps" from "can Jenesis run the codegen" (Phase 2).
- Point Jenesis at `jablib/src/main/java` + the pre-generated sources/resources; pin the full
  dependency closure.
- **Test the central risk**: can Jenesis produce real JPMS modules for jablib's non-modular
  deps (the `dependency-rules.gradle.kts` catalog)? Try native first.
  - If **no**: build the **pre-patch helper** in `jenesis-spike/modulepatch/` — a small tool
    that consumes the same coordinate→module-name + synthesized-descriptor + `mergeJar` catalog,
    ASM-patches jars, and writes a local repo of modular jars that Jenesis consumes.
- **GATE 1:** `jablib` modular jar builds under Jenesis with a real (no automatic) module graph —
  natively or via the pre-patch helper. The decisive feasibility checkpoint; Gradle keeps working
  regardless.

### Phase 2 — Native codegen as custom Jenesis steps
- Re-implement as custom assembler steps (demo-13/17–19), removing the Phase-1 pre-generation:
  1. **ANTLR** grammar generation (`jablib` `generateGrammarSource`).
  2. The 3 generators in `build-support/src/main/java/` (`JournalListMvGenerator`,
     `CitationStyleCatalogGenerator`, `LtwaListMvGenerator`) — compile as a build module + run,
     or invoke via JBang as a pre-step.
  3. **Resource filtering** of `build.properties` (version, year, maintainers, ~12 API-key env
     vars) — currently `jablib/build.gradle.kts` `processResources { expand{} }`.
- **GATE 2:** clean `jablib` build from source under Jenesis, no Gradle pre-step.

### Phase 3 — `jablib` parity + tests
- Diff Jenesis `jablib` jar vs Gradle jar (module descriptor, contents, resources). Get tests
  compiling/running (`test-support` module + JUnit/Mockito/ArchUnit pins) — full green can be
  deferred; the bar is "builds + representative tests run".
- **GATE 3:** jar parity acceptable; representative tests run.

### Phase 4 — `jabkit`  *(INTERMEDIATE GOAL)*  — build → package → `--help`
- **4.1 build:** module `org.jabref.jabkit`, `requires org.jabref.jablib`; add
  `@jenesis.main org.jabref.toolkit.JabKitLauncher` (from `jabkit/build.gradle.kts`
  `application{}`). Verify via `Execute` with JabKit's JVM args (`-Xlog:disable`,
  `--enable-native-access=...`, GC + compact-object-headers flags).
- **4.2 package:** `jpackage` app-image per the pattern above, plus
  `--add-modules jdk.incubator.vector` (jabkit-specific). JReleaser fallback if needed.
- **4.3 smoke:** `jabkit --help` exits 0; bonus `jabkit --debug check consistency empty.bib`
  (mirrors `runJabKitPortableSmokeTest`).
- **GATE 4:** packaged `jabkit` binary prints `--help`. **Intermediate goal reached.**

### Phase 5 — Maven publishing of `jablib`  *(FINAL GOAL: publishing)*
- Author `project.properties` (url, MIT license, JabRef developers, scm) mirroring the
  `mavenPublishing` POM in `jablib/build.gradle.kts`.
- Override coordinates to `org.jabref:jablib` (custom assembler) instead of the default
  `org.jabref:org.jabref.jablib`.
- `stage` → verify `stage/maven` has jar + pom + sources + javadoc; wire **JReleaser** for
  signing + Maven Central upload (deploy to a local/test repo first).
- **GATE 5:** a signed, correctly-coordinated `jablib` bundle stages and deploys to a test repo.

### Phase 6 — `jabsrv`  *(replaces the old IntelliJ phase)*  — build → package → `--help`
- **6.1 build:** server pair mirroring jablib/jabkit — **`org.jabref.jabsrv`** (library) +
  **`org.jabref.jabsrv.cli`** (executable). Add `@jenesis.main org.jabref.http.server.cli.ServerCli`
  (from `jabsrv-cli/build.gradle.kts`) with its JVM args (`--enable-native-access=...`,
  compact-object-headers, string-dedup). Wire the **picocli-codegen** annotation processor
  (`info.picocli.codegen`, from `mainModuleInfo`) into the Jenesis compile step.
  New dependency surface beyond jabkit (extends the Phase-1 patch catalog): the Jersey / Grizzly /
  HK2 injection stack (`org.glassfish.jersey.*`, `org.glassfish.grizzly.*`, `org.glassfish.hk2.*`,
  `aopalliance`, `osgi.resource.locator`), gson, Jackson, **plus JavaFX base/graphics/controls +
  afterburner.fx** (the CAYW feature) — JavaFX native-variant patching is first exercised here.
- **6.2 package:** `jabsrv` app-image per the pattern above (JReleaser fallback).
- **6.3 smoke:** `jabsrv --help` exits 0; bonus: start the server and hit one endpoint (confirms
  Jersey/HK2 service population on the module path).
- **GATE 6:** packaged `jabsrv` binary prints `--help` (and ideally serves a request).

### Phase 7 — `jabgui`  — build → package → launch
- **7.1 build:** the GUI module (full JavaFX: controls/fxml/web/swing/media + ControlsFX,
  RichTextFX, MVVMFX, GemsFX, Ikonli, …), plus all per-OS native JavaFX variants. Largest
  dependency + packaging surface; depends on everything proven in Phases 1–6.
- **7.2 package:** `jabgui` app-image per the pattern above (the real installer targets in
  `targets.gradle.kts` are out of scope for the spike; app-image is the bar). JReleaser fallback.
- **7.3 launch:** start the GUI; the smoke is a window that opens. **→ SIGNAL THE USER HERE** so
  they can try the GUI interactively (per request).
- **GATE 7:** `jabgui` launches from a Jenesis build (ideally from the packaged app-image), and
  the user is notified to try it.

### After everything builds (separate follow-ups)
- **IntelliJ integration** — no native importer; evaluate + document importing modules as plain
  Java (source roots + module path) with Run Configurations / External Tools invoking
  `java build/jenesis/Project.java <goal>`; verify edit→build→run→debug. (Pulled out of the
  numbered phases at the user's request — do once the modules build.)
- **Remaining modules** `jabls` / `jabls-cli` (LSP server) under Jenesis.
- **Retire Gradle** — remove `build-logic/`, the `*.gradle.kts` files, and `settings.gradle.kts`
  only after full parity across all modules.

## First concrete artifacts (Phase 0 MWE)

`jenesis-spike/mwe/greeter/src/main/java/module-info.java`
```java
module demo.greeter { exports sample.greeter; }
```
`jenesis-spike/mwe/app/src/main/java/module-info.java`
```java
/**
 * @jenesis.main sample.app.App
 * @jenesis.release 26
 * @jenesis.pin org.slf4j 2.0.18 SHA-256/<filled-by-pin>
 */
module demo.app {
    requires demo.greeter;
    requires org.slf4j;
}
```
Commands to validate (exact flags confirmed in Phase 0):
```
java build/jenesis/Project.java pin     # fill checksums
java build/jenesis/Project.java         # build + test
java build/jenesis/Execute.java         # run main
```

## Verification

- **Per phase:** the GATE criterion above is the test. Capture each command + output in
  `jenesis-spike/NOTES.md`.
- **jablib (Phase 3):** compare Jenesis jar against the Gradle jar
  (`jablib/build/libs/*.jar`) — module descriptor (`jar --describe-module`), entry list,
  filtered `build.properties`.
- **App components (Phases 4/6/7):** each verified by its 3 steps — build, packaged binary
  exists, smoke passes. CLI smoke = packaged `jabkit --help` / `jabsrv --help` exits 0 (bonus:
  `jabkit --debug check consistency empty.bib`, mirroring `runJabKitPortableSmokeTest`; `jabsrv`
  serving one request). **GUI smoke = window launches → notify the user to try it.**
- **Publishing (Phase 5):** `stage`, inspect `stage/maven`, JReleaser deploy to a local/test
  repo; assert coordinates `org.jabref:jablib` and presence of sources + javadoc jars.
- **Non-destructive guarantee:** `./gradlew :jabkit:run` (and a normal Gradle build) must keep
  working unchanged throughout.

## Open notes / things to confirm early

- **Jenesis CLI/tag specifics** (pin syntax, layout reading of `src/main/java`, app-image
  property names) were partly read via a summarizing fetch — treat as ⚠ until Phase 0 confirms.
- **JDK**: target is **Java 26 (Amazon)**. Local Temurin 26 is fine for compile/run (Phases 0–3);
  use **Amazon Corretto 26** (jmods, no bundled JavaFX) for app-image (Phases 4, 6, 7).
- **Version single-source**: decide in Phase 1 whether to accept per-module pins or build a
  shared version source mirroring `versions/build.gradle.kts` (a custom assembler can read one
  properties file and inject pins).
- **Scala/JitPack/native-variant deps** are the nastiest entries for the pre-patch helper —
  cover `fastparse_2.13`, `langchain4j-core` (mergeJar×6), `javafx-*` native classifiers, and
  `mvvmfx-validation` (jitpack) as explicit helper test cases.
