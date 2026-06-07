# Jenesis migration spike

Parallel, non-destructive evaluation of switching JabRef's build from **Gradle** to
**[Jenesis](https://github.com/raphw/jenesis)**. Gradle stays the source of truth and is untouched;
nothing in this directory affects the Gradle build.

- **[PLAN.md](PLAN.md)** — the staged migration plan (phases + gates).
- **[NOTES.md](NOTES.md)** — findings, environment, and gotchas as the spike progresses.

## Contents
- `mwe/` — minimal 2-module Jenesis project (Phase 0). Proves the toolchain: `app` requires a local
  `greeter` module plus external `org.slf4j`.
- `modulepatch/` — *(Phase 1, planned)* pre-patch helper that turns JabRef's non-modular dependency
  jars into real JPMS modules.

## Prerequisites
- JDK 26 (Temurin is fine for build/run; Amazon Corretto is needed later for `jpackage`/`jlink`).
- Fetch the vendored Jenesis engine (a git submodule at `jenesis-engine`):
  ```
  git submodule update --init
  ```

## Run the MWE
Run from the project's own directory so Jenesis's caches (`.jenesis/`, `target/`) stay local:
```
cd jenesis-spike/mwe
java ../../jenesis-engine/sources/build/jenesis/Project.java
java ../../jenesis-engine/sources/build/jenesis/Execute.java Ada Lovelace
```
Expected output from `Execute`:
```
Hello, Ada Lovelace, from a Jenesis MWE!
```
(SLF4J prints a "no providers" warning — expected, the MWE bundles no logging backend.)
