# Jenesis migration — spike notes

## Environment
- OS: Windows 11
- Build JDK: Temurin 26.0.1 (JabRef targets Java 26; Amazon Corretto needed later for jmods/jpackage)
- Jenesis: vendored as a git submodule at `jenesis-engine` (pinned commit). Engine sources live at
  `jenesis-engine/sources/build/jenesis/`.
- jbang 0.138, git 2.52.

## How to run
Run each project from its own directory so Jenesis's caches (`.jenesis/`, `target/`) stay local
(and gitignored):

```
cd jenesis-spike/mwe
java ../../jenesis-engine/sources/build/jenesis/Project.java          # build
java ../../jenesis-engine/sources/build/jenesis/Project.java pin      # pin versions + checksums
java ../../jenesis-engine/sources/build/jenesis/Execute.java Ada      # run @jenesis.main
```

Avoid `-Djenesis.project.root=… ` from the repo root: Jenesis writes a `.jenesis/` overlay cache into
the *current* directory, which would land at the repo root. `cd` into the project instead.

## Phase 0 results — GATE 0 PASS
- Engine compiles + runs on Windows + JDK 26.
- Upstream `demo-04-java-modular-multi` (modular multi + slf4j + JUnit): build + test green in ~25 s.
- MWE (`jenesis-spike/mwe`, flat layout, `app` → `greeter` + `org.slf4j`): build ~10–15 s; `Execute`
  prints `Hello, Ada Lovelace, from a Jenesis MWE!`. `org.slf4j` resolved from the Jenesis module
  overlay; the intra-project `app → greeter` dependency resolves with no manual wiring.
- `pin` rewrote `app/module-info.java` with `@jenesis.pin org.slf4j 2.1.0-alpha1 SHA-256/<hex>` — the
  version/checksum mechanism works. (Write the version explicitly before `pin` to avoid the overlay's
  latest alpha.)

## Findings that shape Phase 1

### 1. Layout: `module-info.java` must sit ONE level under the project root
JabRef's `src/main/java/module-info.java` layout **fails**. Jenesis keys each module by its directory
path relative to the project root, so `greeter/src/main/java` becomes the module key
`module-greeter%2Fsrc%2Fmain%2Fjava`, which is then **double-encoded on Windows** (`%252F`):

```
java.nio.file.NoSuchFileException:
  target\build\modules\compose\group\output\groups\module-app%252Fsrc%252Fmain%252Fjava.properties
```

`demo-04` works because module-info is at `greeter/module-info.java` (a single path segment).

- **Impact:** `jablib/src/main/java/module-info.java` (and every other JabRef module) will not build
  unmodified.
- **Options for Phase 1, cheapest first:**
  - (a) Set the project root to the module's own `src/main/java` dir, so `module-info.java` is at the
    root — sources stay in place; build modules individually and wire inter-module deps via the local
    Jenesis module repo (`~/.jenesis`).
  - (b) Generate or symlink a flat module dir next to the real sources.
  - (c) Upstream fix: stop double-encoding and/or allow a path-independent module key.
  - Investigate (a) first.

### 2. Stock layouts do NOT reproduce `extra-java-module-info`
From the Jenesis README: `MODULAR_TO_MAVEN` maps each `requires` → Maven coordinate via the Jenesis
overlay, reads **no** `module-info.class`, and runs on the **classpath** (not jlink-provable). The pure
`MODULAR` layout resolves by module name against `~/.jenesis` (+ overlay) and **is** module-path
provable, but cannot resolve automatic / non-modular jars.

JabRef patches ~150 non-modular jars into real JPMS modules (`build-logic/.../dependency-rules.gradle.kts`).
Neither stock layout does this. **Plan:** a pre-patch helper (`jenesis-spike/modulepatch/`) ASM-patches
those jars into named modules and publishes them to the local `~/.jenesis` repo, then jablib builds with
the `MODULAR` layout. This is the Phase 1 make/break.

## Tooling gotchas
- The Bash git-guard hooks (`.claude/hooks/*.py`) resolve relative to the current dir and wedge if the
  Bash tool's CWD becomes a repo subdir. Run Jenesis via the **PowerShell tool** (the Bash-only hooks
  don't apply) or keep the Bash CWD at the repo root.
- `build/` and `**/build/` are gitignored in JabRef, so the engine cannot be tracked at `build/jenesis`.
  Hence the submodule at `jenesis-engine` (tracked) plus an optional generated `build/jenesis` (ignored).
- `jenesis-engine` is itself a Jenesis project (root `pom.xml` + `build.jenesis` module). When running
  Jenesis at the JabRef root later, exclude it from module discovery (e.g. a `.jenesis.skip` marker, or
  build scoped to specific modules) so the engine isn't treated as a project module.

## Next (Phase 1)
- Try layout option (a) on one small module (e.g. `test-support`, or a stripped `jablib`) with the
  project root set to its `src/main/java`.
- Stand up the pre-patch helper skeleton in `jenesis-spike/modulepatch/`.
