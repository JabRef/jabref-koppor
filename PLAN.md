# JabRef in Debian — packaging plan

Tracking issue: <https://github.com/JabRef/jabref-koppor/issues/135> ·
Debian bug: [#877718](https://bugs.debian.org/877718) ("new upstream version 4.0", 2017) ·
Debian state: `jabref 3.8.2+ds-18` still in sid, removed from testing — <https://tracker.debian.org/pkg/jabref>.

All Debian version numbers below were checked against sid on 2026-08-31 (qa.debian.org/madison).

## Ground rules (why this is hard)

Debian main requires: build entirely from source, on Debian buildds, offline, using only
dependencies that are themselves Debian packages. No downloaded jars, no jitpack, no
prebuilt binaries (the zonky embedded-postgres blobs are out by definition). The version
in a Debian stable release is then frozen for ~2–3 years.

## Build system decision

**Upstream keeps Gradle. Debian ignores it completely.** No upstream build-system switch
is needed — Debian's `debian/rules` *is already a GNU makefile*, and for Java it delegates
to helpers. The friction-least stack on Debian is:

- **debhelper + javahelper (`jh_build`)** for the `jabref` source package itself:
  effectively `javac -cp /usr/share/java/*.jar $(find src -name '*.java')` + `jar`.
  There is nothing for a "real" build system to do — dependency resolution is forbidden
  anyway (all deps are system jars), and per this plan we build **plain classpath, no
  JPMS modules, no jlink/jpackage** (`module-info.java` files are simply excluded from
  compilation). Extra build steps (ANTLR generation, journal-list MV generation) are a
  few explicit commands in `debian/rules`.
- **ant** only as fallback if `debian/rules` outgrows ~50 lines. Well supported
  (`dh --buildsystem=ant`), but adds an XML file for no gain today.
- **maven-debian-helper** not for the app, but it *is* the right tool for the library
  packages we will create (latex-conv, mslinks, unirest, …) whenever upstream ships a pom —
  it installs into `/usr/share/maven-repo` and handles the boilerplate.
- **Gradle is dead on Debian**: sid has gradle 4.4.1 + kotlin 1.3.31. Upgrade work exists
  (<https://salsa.debian.org/jpd/gradle/-/blob/upgrade-to-8.11.1-wip/debian/wip/README.md>,
  sre4ever) but is not our critical path to *wait* on for the CLI — only the GUI needs it
  (via openjfx, see below).

Launch scripts: `/usr/bin/jabkit` / `/usr/bin/jabref` are shell wrappers running
`java -cp ... org.jabref.cli.…/org.jabref.Launcher` with the needed `--add-opens` flags.

## Key facts (checked 2026-08-31)

| Component | Debian sid | JabRef needs | Verdict |
|---|---|---|---|
| JDK | openjdk-25 (25.0.4) | Java 25 | ✅ fine |
| openjfx | **11.0.11** | JavaFX 26 (+incubator modules) | ❌ **GUI blocker** — but a gradle-free build is feasible, see spike below |
| gradle | 4.4.1 | 9.x | ❌ irrelevant if we build with javac |
| lucene | liblucene9-java 9.12.3 | 10.5 | ⚠️ small API port or new lucene10 pkg |
| pdfbox | libpdfbox2-java 2.0.29 | 3.0.8 | ❌ new `pdfbox3` source pkg needed (maven-built, doable) |
| slf4j | 1.7.32 | 2.0.x | ⚠️ compile against 1.7 works; use Debian's binding instead of tinylog |
| antlr4 | 4.9.2 | 4.13 | ❌ 4.9 cannot parse our grammars (`caseInsensitive` needs ≥ 4.10) — antlr4 update in Debian required (verified in PoC) |
| libreoffice UNO | 26.8 | 26.2 | ✅ |
| httpclient5/core5 | 5.6 / 5.4.3 | same | ✅ current |
| postgresql-jdbc | 42.7.13 | same | ✅ current |
| jna | 5.18.1 | 5.19 | ✅ |

Good news vs. the 2017 list in #135: **the Scala chain is gone** — latex2unicode/fastparse/sbt
were replaced by our own `org.jabref:latex-conv`; citeproc-java's jbibtex is packaged
(libjbibtex-java 1.0.20); CSL styles + locales are Debian packages
(`citation-style-language-styles`, `-locales`) we can depend on instead of vendoring the
submodules.

## Strategy: stage the goal

**Stage 1 = `jabkit` (CLI, jablib only).** `jablib` needs `javafx.base` but *no other*
JavaFX module — and `javafx.base` is pure Java (no natives, buildable with javac alone).
The openjfx-11 mega-blocker therefore does not block the CLI: either patch the few
JavaFX-19+ API uses in jablib (`ObservableValue#map`, `Subscription` — 2 files found)
to compile against openjfx 11's javafx-base, or ship a tiny `javafx-base26` jar as its
own trivial source package. Everything else the CLI needs is ~15 small library packages
(below) plus feature-trim patches.

**Stage 2 = `jabref` GUI.** Needs JavaFX 26. Two routes:

- **Route A (primary): a gradle-free `openjfx26` source package.** The feasibility spike
  below shows the Java side of all seven modules JabRef needs compiles with *one javac
  command*. JabRef needs **no javafx.web** (WebKit — explicitly banned in our build since
  html-to-node) and **no javafx.media** (GStreamer) — precisely the two modules that make
  OpenJFX packaging a nightmare. What remains is ~32k LoC of ordinary C (gtk3/GL/freetype/
  pango/libjpeg) behind a Makefile, plus running the in-tree shader compiler. This would
  also unblock every other JavaFX app in Debian — a strong argument for co-maintainers
  (coordinate with ebourg, the openjfx maintainer, before investing).
- **Route B (fallback): wait for Debian's gradle 8/9 → kotlin 2.x → openjfx 26 chain**
  (sre4ever's salsa work). Multi-year horizon, outside our control.

Beyond JavaFX itself: ~20 further JavaFX libraries (fxmisc stack, dlsc stack, ikonli,
mvvmfx, …). Until Stage 2 lands, the official jpackage `.deb`, snap and flathub remain
the delivery channels.

## Gradle-free OpenJFX 26 — feasibility spike (2026-08-31, measured)

Sparse checkout of `openjdk/jfx` at tag `26-ga`, Debian sid container / JDK 25:

- **javafx.base**: 310 files, `javac` → **0 errors** after stubbing
  `com.sun.javafx.runtime.VersionInfo` (6 lines; gradle generates it — just version
  strings).
- **All 7 modules JabRef needs** (base, graphics, controls, fxml, swing,
  jfx.incubator.input, jfx.incubator.richtext): **one `javac --module-source-path … -m …`
  invocation, 0 errors.** (Compiling per-module on the classpath instead yields exactly 20
  errors — all "cannot extend sealed class in different package", i.e. pure JPMS
  mechanics, not missing code. Note: JavaFX itself must be built *as modules*; JabRef on
  top of it can still be classpath.)
- **Generated shader classes are not compile-time references** — Prism loads them
  reflectively at runtime. Generation chain for a *working* runtime: the in-tree JSL
  compiler (`modules/javafx.graphics/src/jslc`, plain Java + an **ANTLR 4** grammar) over
  62 `.jsl` files. No gradle needed; scriptable in `debian/rules`. (Same antlr4 ≥ 4.10
  Debian update as JabRef's grammars need.)
- **Native code, Linux-relevant only**: glass-gtk 11.4k LoC, prism-es2 7.1k, prism-sw
  4.2k, font 7.2k, decora-SSE ~1k, iio ~2k glue (its remaining 31k LoC is a bundled
  libjpeg copy — Debian links the system one anyway). Total ≈ **32k LoC of standard C**,
  pkg-config deps only; a Makefile + `javac -h` JNI-headers job.
- **Not yet proven**: natives Makefile, shader generation run, and a rendering smoke test
  (JavaFX HelloWorld on sid with the hand-built stack). That's the next spike milestone —
  it de-risks Route A completely before any Debian process starts.

**Feature-trim patches for the Debian build** (allowed and normal for Debian; keep as a
small quilt series):

- **AI stack out**: langchain4j, djl (pulls native pytorch!), jvm-openai, jtokkit,
  opennlp — ~30 files in jablib + 3 in jabgui reference them. Best done upstream (see
  below); until then a patch that stubs `AiService` & friends behind "feature disabled".
- **embedded-postgres out**: only 2 files (`module-info.java`, `PostgresServer.java`);
  patch to require a system `postgresql` server at runtime (Recommends:).
- **jabsrv / jabls not built**: drops jersey, grizzly, hk2, lsp4j entirely.
- **tinylog → Debian's slf4j binding** (slf4j-simple or logback).

## Dependency inventory — Stage 1 (jablib + jabkit)

**Measured, not guessed**: a PoC compile of all jablib sources (module-info excluded,
ANTLR sources pre-generated with 4.13) against pure Debian sid jars in a `debian:sid`
container ran on 2026-08-31. Result: **~73 % of jablib compiles as-is** (515 of ~1900
files with errors); every unresolved import is accounted for below (import-site counts
from javac in parentheses).

**Already in Debian, resolves cleanly:** gson, guava (32 vs 33),
commons-{io,lang3,text,csv,logging,compress}, httpcore5, httpclient5, jsoup (1.15 vs 1.23),
snakeyaml 2.5, xz 1.11, postgresql-jdbc, icu4j 77 (drop the `72!!` pin in a patch),
java-diff-utils, java-string-similarity 2.0.0, jbibtex 1.0.20, bcprov 1.80 (vs 1.85),
h2 2.2 (mvstore classes inside h2.jar; verify MV file compat vs 2.4),
libreoffice UNO (libreoffice-java-common; not installed in the PoC, hence its 362 errors),
jna, picocli 4.6, jgit 6.7, lucene9 9.12, slf4j 1.7, javafx-base 11 (from libopenjfx-java),
**jspecify 1.0**, jakarta-annotation-api (2.1 vs 3.0), jakarta-inject-api,
intellij-annotations.

**In Debian, needs major update:** antlr4 (4.9 → ≥ 4.10/4.13, see above), caffeine
(2.6 → 3.2), afterburner.fx (1.7 → JabRef fork 2.0; but see "drop instead" below),
easybind (1.0.3 → JabRef fork 2.3; 9 import sites), pdfbox (2 → 3, as new source package).

**Missing — new packages** (import sites in jablib): unirest-java core+gson (103),
jackson v3 `tools.jackson.*` (36 — Debian only has jackson 2, different coords → new
source package), jool (12), citeproc-java (11), snuggletex de.rototor fork (9),
java-keyring (7), **org.jabref:mslinks** (4), dd-plist (4), cuid (3), e-adr (3,
compile-only), **org.jabref:latex-conv** (2), terminal-text-formatter (2).
Runtime-only (no compile errors, needed in Depends): aalto-xml, tinylog2 (or use
Debian's slf4j binding via patch).

The org.jabref-owned ones are the easiest: we control releases, can ship clean poms and
source tarballs, and can package them ourselves via maven-debian-helper.

**Tiny surface — consider dropping upstream instead of packaging** (each is one or two
files in jablib): flexmark (1 file, `MarkdownFormatter`), afterburner in jablib (1 file,
`ConvertMSCCodesFormatter` — jablib shouldn't need an injection framework), appdirs
(1 file, `Directories` — XDG logic is small), jakarta.ws.rs (2 fetchers, likely just
`UriBuilder`). Velocity is *not yet* a jablib dep in main (1 file; arrives with the
velocity-layouts PR — note: Debian only has velocity 1.7, not engine-core 2.x).

**Patched out (not packaged):** langchain4j+djl+jvm-openai (~130 import sites — matches
the ~30-file estimate), jtokkit, opennlp, embedded-postgres(+binaries),
okhttp/kotlin-stdlib (only reached via AI stack), mvvmfx-validation jitpack pin (GUI;
must die upstream anyway — jitpack is unfetchable for Debian *and* a supply-chain
liability).

**Still to classify:** ~2500 "cannot find symbol" errors = knock-on effects of the
missing packages above + real API deltas (javafx.base 11 vs 26 — `ObservableValue#map`,
`Subscription`; lucene 9 vs 10; pdfbox 2 vs 3; antlr-runtime 4.9 vs 4.13-generated code
— the 24 "does not override" errors; jilt-generated builders; caffeine 2.6). Next PoC
iteration: install/stub the missing jars, re-run, classify what remains.

## Upstream work items (in JabRef, we control these)

1. Make the AI subsystem a separable module / compile-time optional, so Debian (and other
   distros — same request exists from Gentoo/Guix packagers) disables it without patches.
2. Replace the jitpack `com.github.sialcasa.mvvmFX:mvvmfx-validation:f195849ca9` pin with
   a released artifact (vendor into org.jabref if needed).
3. Keep publishing clean poms + source jars for org.jabref libs (latex-conv, mslinks,
   afterburner.fx, easybind, html-to-node) — makes their Debian packaging mechanical.
4. Make embedded-postgres optional at runtime (system-postgres fallback already exists?
   — verify `PostgresServer`).
5. Runtime lookup of CSL styles/locales + journal abbreviation lists from
   `/usr/share/…` paths so Debian packages can provide them (env var or system property
   suffices).

## Step-by-step execution list

- [x] **1. Inventory** dependencies vs Debian sid (this document, 2026-08-31).
- [ ] **2. Sync** this plan to jabref-koppor#135 (link + update the stale 2017 list).
- [ ] **3. PoC build** in a sid container (docker `debian:sid`):
  - [x] Harvest missing packages: javac all jablib sources against Debian sid jars
        (2026-08-31, results in the inventory above — 73 % compiles, missing list exact).
  - [ ] Classify the ~2500 remaining symbol errors into API-delta buckets
        (javafx.base, lucene, pdfbox, antlr-runtime, jilt, caffeine).
  - [ ] Add trim patches + locally-built jars for the missing libs; get a working
        `jabkit --version` / `jabkit convert`. This converts "huge effort" into an
        exact bill of materials.
- [ ] **4. Decide** Stage-1 trim set from PoC results; write the quilt patch series.
- [ ] **5. Package the missing libs** (order above), ITP each; org.jabref libs first.
- [ ] **6. Update** caffeine, afterburner.fx, easybind (coordinate with current Debian
      maintainers; the forks may need new source package names).
- [ ] **7. `jabref` source package** producing `jabkit` (+ `libjablib-java`?):
      javahelper build; ANTLR 4.9 generation; JournalList/Ltwa MV generation via
      `build-support` classes; depend on `citation-style-language-{styles,locales}`;
      abbrv.jabref.org + ltwa CSVs as extra orig-tarball components (uscan multi-component);
      desktop/AppStream assets exist in `flatpak/` for later GUI reuse.
- [ ] **8. Debian process**: take over/retitle #877718 as ITP (jabkit first), repo on
      salsa (java-team), find sponsor — tmancill (Debian Java team) already commented in
      #135; loop in sre4ever.
- [ ] **9. OpenJFX spike, part 2** (can run in parallel with 5–8): Makefile for the
      Linux natives (glass-gtk, prism-es2, prism-sw, font, iio, decora-SSE), run the JSL
      shader compiler, then a rendering smoke test on sid. If green: propose the
      gradle-free `openjfx26` package to debian-java@ / ebourg with the PoC attached.
  - [x] Java-side compile of all 7 modules proven (one javac invocation, 0 errors).
- [ ] **10. Stage 2 (GUI)**: once JavaFX 26 is packagable (route A or B), package the
      JavaFX library stack (fxmisc, dlsc, ikonli, mvvmfx, …); then add the `jabref` GUI
      binary package to the same source package.

## Risks / notes

- **openjfx modernization via gradle (route B) may take years** — that's why Stage 1
  avoids JavaFX and why route A (gradle-free openjfx26) is worth the spike. Route A's
  own risk is per-release maintenance of a parallel build definition (~every 6 months)
  and Debian-maintainer acceptance — hence: talk to ebourg early, with the PoC in hand.
- Debian stable freeze vs. JabRef's "only latest release supported": accept; stable gets
  security patches only. sid/testing users track releases.
- The old `jabref-in-debian.md` (koppor/jabref, `about` branch) is superseded by this file;
  recovered copy: [Software Heritage](https://archive.softwareheritage.org/browse/content/sha1_git:21c21031e18bc87186af7e40d53dd18ebff6c0af/?origin_url=https://github.com/koppor/jabref&path=jabref-in-debian.md&timestamp=2023-03-04T09:13:12Z).
- Gentoo source-build attempt (parked) hit the same walls; share findings across distros.
