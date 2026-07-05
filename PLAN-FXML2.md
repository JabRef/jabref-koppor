# Plan: Migrate views to FXML/2 (jfxcore/fxml-compiler)

Branch: `fxml2-spike` (based on `replace-afterburner`). Goal decided by Oliver: instead of keeping the
JabRef-owned `ViewLoader` long-term (or filing an FxmlKit feature request), migrate views to
[FXML/2](https://jfxcore.github.io/fxml-compiler/) — FXML compiled to Java classes at build time.
The `replace-afterburner` branch (koppor PR [#733](https://github.com/JabRef/jabref-koppor/pull/733)) stays as
step 0: it removes afterburner and establishes `org.jabref.injection.Injector`, and its `ViewLoader` remains the
runtime bridge for **not-yet-converted** views during this (long, view-by-view) migration.

**How to resume:** findings below are verified (2026-07-05); work through unchecked boxes.

## Findings (verified)

### FXML/2 model

- `.fxml` compiles at build time to a **generated Java base class** (e.g. `MyControlBase`) that `extends` the
  root element type (VBox, DialogPane, …). The hand-written **code-behind** class declares
  `fx:subclass="fq.ClassName"` in the FXML root and `extends MyControlBase`; its constructor MUST call
  `initializeComponent()` (generated) — after initializing state the scene graph needs, before touching it.
- `fx:id` elements become fields of the generated base — code-behind accesses them directly, **no `@FXML`**.
- **`fx:controller` and `fx:root` do not exist.** `fx:subclass` covers JabRef's "view is its own control" pattern.
- Without `fx:subclass`, the FXML alone compiles to a standalone class (usable for controller-less views).
- Root element namespaces (mandatory, or the compiler skips the file):
  `xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"`.
- FXML/2 files belong in the **source tree** (`src/main/java/...`, same package as code-behind), NOT resources;
  they are not deployed.
- Embedded markup alternative: `@ComponentView("""...""")` annotation (needs annotation processing enabled +
  the `jfxcore/markup` library).
- Localization: `%key` shorthand exists = `StaticResource` markup extension (from the **`jfxcore/markup` runtime
  library**, separate dependency); resolved via a `ResourceContext` provided by a `ResourceContextProvider` —
  this is where `Localization.getMessages()` docks. OPEN: exact wiring API + whether JabRef's keys-with-spaces
  work in `%...` shorthand or need `{StaticResource '...'}` quoting. Also `formatArguments` replaces JabRef's
  `%0` placeholders story in FXML.
- Compile-time typed bindings (`{fx:bind ...}`, `${...}` observe syntax) — future win for MVVM wiring; NOT needed
  for a 1:1 migration (keep imperative wiring in code-behind).
- Tooling: Gradle plugin `org.jfxcore.fxmlplugin` 0.14.0 (adds `processFxml` per source set, generates Java
  sources → javac compiles them; JPMS-friendly). IntelliJ plugin exists ("FXML/2 for JavaFX"). **No Scene Builder
  support** — team decision needed. No Maven plugin.
- Maturity: fxml-compiler 0.14.0 published 2026-06-12, active; org.jfxcore's `javafx-*` fork artifacts are dead
  (2022) and NOT needed — the compiler works against the project's own (stock) JavaFX. 9 GitHub stars = bus-factor
  risk; mitigations: generated code is plain Java, and classic-FXML fallback (our ViewLoader) keeps working.

### How JabRef's patterns map

| Today | FXML/2 |
| --- | --- |
| fx:root custom control, `ViewLoader.view(this).root(this).load()` (67 views) | root element + `fx:subclass`; class `extends <Name>Base`; ctor calls `initializeComponent()` |
| Dialog: `XView extends BaseDialog<T>` + FXML root `<DialogPane>` + `setAsDialogPane` (47) | **split needed**: DialogPane becomes its own FXML/2 class (`XDialogPane extends XDialogPaneBase`), `XView extends BaseDialog` does `setDialogPane(new XDialogPane(...))`; alternatively restructure dialog to extend the pane |
| `fx:include` (preferences tabs) | just instantiate the compiled class as an element |
| `ViewLoader.view(Class).load()` + `getController()` (IntegrityCheckDialog) | direct `new BibLogSettingsPane(...)` |
| `@Inject` fields filled during load | ctor: `Injector.registerExistingAndInject(this);` **before** `initializeComponent()` (locator from step 0 survives; ViewLoader/FxmlKit eventually retire) |
| `@FXML` fields | deleted — inherited from generated base |
| `%key` in FXML | `%key` via StaticResource + JabRef `ResourceContextProvider` (OPEN, see above) |
| `LocalizationParser` FXMLLoader-loads every `.fxml` under `src/main` | must SKIP FXML/2 files (namespace `jfxcore.org/fxml/2.0`) and later extract their keys by parsing instead |

### Risks / open questions (resolve via spike)

1. Gradle plugin vs JabRef's build-logic (gradlex module plugins, ErrorProne/NullAway, checkstyle, Java 25/JavaFX 26).
2. Generated-source location + module-info interplay; does the plugin add a runtime dependency (markup lib) and
   does it need a `requires`?
3. Localization wiring (`ResourceContextProvider`) + keys with spaces + `%0` placeholders.
4. Checkstyle/rewrite/NullAway on code-behind classes extending generated types.
5. RTL/pseudo-classes/styleclass parity; `.bss`/CSS auto-attach (ViewLoader did this — FXML/2 views must add
   stylesheets in markup or code-behind).

## Phase 1 — Spike (one fx:root view, no l10n keys)

- [x] Apply `id("org.jfxcore.fxmlplugin") version "0.14.0"` to `jabgui/build.gradle.kts`. TWO issues found+solved:
  1. The FXML compiler loads JavaFX classes inside the **Gradle daemon JVM** → daemon must run on JDK 24+
     (JavaFX 26 = class-file 68). This box defaults to Java 21: run with
     `-Dorg.gradle.java.home=$HOME/.gradle/jdks/amazon_com_inc_-25-amd64-linux.2` (or set
     `gradle/gradle-daemon-jvm.properties` project-wide — decide before Phase 2; also an upstream issue candidate).
  2. The plugin scans `sourceSet.allSource` incl. **resources** → tries to compile all 117 classic FXMLs (they
     carry the javafx.com namespace) and fails on `fx:controller`. Workaround in jabgui/build.gradle.kts:
     `ProcessFxmlTask.fxmlSourceInfo` filtered to `src/*/java` dirs. Upstream issue candidate (skip files with
     classic `xmlns:fx="http://javafx.com/fxml/1"`, or include/exclude config).
- [x] Convert `org.jabref.gui.cleanup.CleanupSingleFieldPanel`:
  - move+rewrite FXML to `jabgui/src/main/java/org/jabref/gui/cleanup/CleanupSingleFieldPanel.fxml`
    (namespaces, `fx:subclass`, drop `fx:root`/`fx:controller`, dedupe the double `<padding>`)
  - code-behind: `extends CleanupSingleFieldPanelBase`, drop `@FXML` field + ViewLoader call, ctor calls
    `initializeComponent()` then `bindProperties()`
- [x] `LocalizationParser.getLanguageKeysInFxmlFile`: skip files containing `http://jfxcore.org/fxml/2.0`.
- [x] `./gradlew :jabgui:compileJava` + `:jablib:test --tests "*LocalizationConsistencyTest"` (DISPLAY=:10.0) — green.
- [x] Runtime check: new TestFX test `CleanupSingleFieldPanelTest` instantiates the panel — scene graph built by generated `initializeComponent()` (verified via javap: plain `new FieldFormatterCleanupsPanel()` bytecode, zero reflection), bindings work. Green with DISPLAY=:10.0.
- [x] Verdict: **GO**. Build friction is real but manageable (daemon JDK + source-scan filter, both recorded
  above); generated code is clean plain Java (stub) + rewritten bytecode; checkstyle/ErrorProne/NullAway untouched
  by generated sources; classic and FXML/2 views coexist. Biggest open items for Phase 2: localization wiring
  (`ResourceContextProvider` + markup runtime lib), dialog-pane split pattern, daemon-JVM decision for all
  devs/CI, and the two upstream issues.

## Phase 2 — Foundations (after GO)

- [ ] Add `jfxcore/markup` runtime lib; implement JabRef `ResourceContextProvider` on `Localization.getMessages()`;
  prove `%key` (incl. spaces + format args) on one localized view.
- [ ] Solve stylesheet attachment convention for converted views (markup `stylesheets` or base-class helper).
- [ ] Extend `LocalizationParser` to EXTRACT keys from FXML/2 files (XML parse for `%`/StaticResource) so l10n
  consistency checks cover converted views again.
- [ ] Decide dialog pattern (pane-class split) and convert one dialog end-to-end as the template.
- [ ] Document the conversion recipe in `docs/code-howtos/` + ADR amending ADR-0065.

## Phase 3 — Incremental conversion (many PRs)

- [ ] Field editors (fx:root family, ~20 views) → then panels/tabs → then dialogs (47) → preferences tabs.
- [ ] Each conversion deletes its ViewLoader usage; when usages hit 0: delete `ViewLoader`/`ViewLoaderResult`,
  drop FxmlKit wiring if unused, update ADR/CHANGELOG.
