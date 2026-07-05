# Plan: Replace afterburner.fx with FxmlKit

Branch: `replace-afterburner`. Goal: drop the JabRef-maintained fork `org.jabref:afterburner.fx:2.0.0`
and use [FxmlKit](https://github.com/dlsc-software-consulting-gmbh/FxmlKit) (`com.dlsc.fxmlkit:fxmlkit:1.5.1`,
module `com.dlsc.fxmlkit`, Apache-2.0, on Maven Central) plus two small JabRef-owned glue classes.

**How to resume:** work through unchecked boxes top-to-bottom. Each phase leaves the repo compilable.
All research in "Findings" is complete and verified — do not redo it.

## Findings (research complete — reference only)

### afterburner usage inventory
- `com.airhacks.afterburner.views.ViewLoader`: 118 files. 117× `ViewLoader.view(this)` (jabgui),
  1× `ViewLoader.view(BibLogSettingsPane.class)` in `jabgui/.../gui/integrity/IntegrityCheckDialog.java`,
  1 static-mock in `jablib/src/test/java/org/jabref/logic/l10n/LocalizationParser.java:188`.
- Fluent calls: `.load(` 124, `.root(this)` 67, `.setAsDialogPane(` 47, `.getView(` 3, `.setAsContent(` 2,
  `.getController(` 1. No usage of `.inject(...)`, `.loadAsync`, `.getViewWithoutRootContainer`.
- `com.airhacks.afterburner.injection.Injector`: 70 files (64 jabgui, 3 jabkit, 3 jablib incl. tests/jmh).
  Only 3 methods used: `instantiateModelOrService` (95×), `setModelOrService` (57×), `registerExistingAndInject` (3×).
  Nothing uses `@PostConstruct`, `forgetAll`, `setInstanceSupplier`, or the `Configurator`.
- `ViewLoaderResult` import: only `IntegrityCheckDialog.java`.
- `ResourceLocator` SPI impls (all just return `Localization.getMessages()`):
  `jabgui/.../gui/util/JabRefResourceLocator.java` (provided in jabgui module-info),
  `jabgui/.../gui/l10n/LocalizationLocator.java` (NOT provided — likely dead),
  `jabsrv-cli/.../http/cli/JabRefResourceLocator.java` (provided), `jabls-cli/.../languageserver/cli/JabRefResourceLocator.java` (not provided).
- `requires afterburner.fx;` in module-info of: jablib(:162), jabgui(:24), jabkit(:13), jabls-cli(:7), jabsrv-cli(:12).
- Build: `versions/build.gradle.kts:134` (`api("org.jabref:afterburner.fx:2.0.0")`) and metadata rule block in
  `build-logic/src/main/kotlin/org.jabref.gradle.base.dependency-rules.gradle.kts:119-128`. No other Gradle refs;
  deps are derived from module-info `requires` (gradlex java-module-dependencies).
- `jabsrv/src/test/java/org/jabref/http/JabSrvArchitectureTest.java:22` forbids package `com.airhacks.afterburner.injection..`.

### Fork semantics that MUST be preserved
- `Injector.instantiateModelOrService(C)`: returns cached singleton; if absent → instantiate, inject `@Inject`
  (jakarta) fields, **cache as singleton**. (FxmlKit's `LiteDiAdapter.getInstance` alone does NOT cache unbound
  creations — the facade must add caching.)
- `ViewLoader.view(this)`: controller factory returns `this` for its own class (after member injection);
  other controller classes (fx:include) are instantiated + injected. Keeps `fx:controller` attribute in FXML.
- FXML resolution: same package, class simple name with `View` suffix stripped; tries lowercase (`aboutdialog.fxml`)
  then CamelCase (`AboutDialog.fxml`). JabRef's 118 FXML files are CamelCase — no renames needed.
- CSS auto-attach: co-located `.bss` preferred over `.css`, same naming convention as FXML.
- `load()` wraps `IOException` in `IllegalStateException`. `ViewLoaderResult.setAsDialogPane(dialog)` requires the
  FXML root to be a `DialogPane` and calls `dialog.setDialogPane(...)`; `setAsContent(pane)` = `dialogPane.setContent(view)`.
- Resource bundle: always `Localization.getMessages()` (that's all the ResourceLocator SPI ever did).

### FxmlKit facts
- `DiAdapter` iface: `<T> T getInstance(Class<T>)` + `void injectMembers(Object)` (idempotent).
  `LiteDiAdapter`: `bindInstance(Class,T)`, `isBound(Class)`; recognizes `@Inject` from **jakarta.inject**, javax.inject
  and Guice (README claiming javax-only is outdated) → JabRef's 82 `@Inject` files stay untouched.
  `injectMembers` only fills null fields; tracks injected objects.
- `FxmlView<C> extends StackPane` (eager), `FxmlViewProvider<C>` (lazy, not a Node). **No public fx:root support,
  no "existing instance as controller" support** (`FxmlKitLoader` is in a non-exported `internal` package) →
  JabRef's dominant patterns cannot use `FxmlView` directly → we keep a thin JabRef-owned `ViewLoader`.
- `FxmlKit.setDiAdapter/getDiAdapter`, `setResourceBundle/getResourceBundle`, `enableDevelopmentMode()` (FXML/CSS
  hot reload), `-Dfxmlkit.devmode=true`.
- POM declares javafx-controls/javafx-fxml as `provided` → Gradle metadata lacks them → need `addApiDependency`
  rules (module-info says `requires transitive javafx.controls, javafx.fxml`).
- Module name `com.dlsc.fxmlkit` should auto-map to `com.dlsc.fxmlkit:fxmlkit` (same pattern as `com.dlsc.gemsfx`).

### Architecture decision (REVISED during implementation — this is what is built)
`LiteDiAdapter` was ruled out for the service locator: its idempotency set `injectedObjects` holds STRONG
references to every injected object → would leak every per-dialog view instance (afterburner used weak sets);
its reflection helper `InjectionUtils` is in the non-exported `di.internal` package so it cannot be reused with
different tracking. Also, `ConferenceRepository` (`@Inject`-ed in `ICORERankingEditor`, never registered) proves
afterburner's create-and-cache fallback is exercised and must be kept.
- New `org.jabref.injection.Injector` (jablib, new exported package, SELF-CONTAINED, no FxmlKit dep — jablib
  sheds its GUI-framework dependency entirely): `setModelOrService`, `instantiateModelOrService` (create+cache via
  no-arg ctor; registers BEFORE field injection so cycles terminate), `registerExistingAndInject` (jakarta
  `@Inject` fields, only-if-null, no tracking → no leak), `instantiatePresenter` (fresh instance per call, for
  FXML controllers). jablib already has `requires transitive jakarta.inject`.
- New `org.jabref.gui.util.ViewLoader` + `ViewLoaderResult` (jabgui): same fluent API as the fork, built on plain
  `FXMLLoader`; controller factory: `view(this)` returns the given instance (after `registerExistingAndInject`),
  other controller types via `Injector.instantiatePresenter` (fresh, never cached — nodes can't be reused across
  scenes). Bundle from `Localization.getMessages()` directly. All 118 imports swap.
- New `org.jabref.gui.util.InjectorDiAdapter implements com.dlsc.fxmlkit.di.DiAdapter` (jabgui): bridges FxmlKit
  to the Injector (`getInstance` → `instantiatePresenter`-fresh, `injectMembers` → `registerExistingAndInject`).
  Wired at GUI startup: `FxmlKit.setDiAdapter(new InjectorDiAdapter())` +
  `FxmlKit.setResourceBundle(Localization.getMessages())` so future views can extend `FxmlView`/use hot reload.
- FxmlKit dependency ONLY in jabgui (`requires com.dlsc.fxmlkit`). jablib/jabkit/jab*s-cli: no FxmlKit.
- ResourceLocator SPI impls + `provides` clauses deleted (obsolete).
- Memory-semantics note: fork cached services in a `WeakHashMap` keyed by Class; new locator uses a strong
  `ConcurrentHashMap` — services are app-lifetime singletons, acceptable.

## Phase 1 — Build wiring

- [x] `versions/build.gradle.kts:134`: replace `api("org.jabref:afterburner.fx:2.0.0")` with `api("com.dlsc.fxmlkit:fxmlkit:1.5.1")`.
- [x] `build-logic/.../org.jabref.gradle.base.dependency-rules.gradle.kts`: replace the `module("org.jabref:afterburner.fx") {...}` block with:
  ```kotlin
  module("com.dlsc.fxmlkit:fxmlkit") {
      // POM declares javafx-* as provided, but module-info has 'requires transitive' on them
      addApiDependency("org.openjfx:javafx-fxml")
      addApiDependency("org.openjfx:javafx-controls")
  }
  ```
- [x] Contingency (NOT needed — module auto-mapped, :jablib:compileJava passed): if the build later fails to map module `com.dlsc.fxmlkit` → GA coordinates, add a
  `moduleNameToGA`/mapping entry the same way other odd modules are mapped (grep build-logic for how e.g.
  `unirest.modules.gson` is handled).

## Phase 2 — Injector facade in jablib

- [x] Create `jablib/src/main/java/org/jabref/injection/Injector.java` — DONE as the self-contained locator
  described in "Architecture decision" above (see the file itself; no FxmlKit imports).
- [x] `jablib/src/main/java/module-info.java`: `requires afterburner.fx;` removed; `exports org.jabref.injection;`
  added. (No new `requires` needed — jakarta.inject already present.)
- [x] Swap the one jablib main import (`ConvertMSCCodesFormatter`).
- [x] Compile check: `./gradlew :jablib:compileJava`.

## Phase 3 — Mechanical Injector import swap (all modules, main + test + jmh)

- [x] Swap every remaining `import com.airhacks.afterburner.injection.Injector;` →
  `import org.jabref.injection.Injector;`:
  ```bash
  grep -rl 'com\.airhacks\.afterburner\.injection\.Injector' --include='*.java' . \
    | xargs sed -i 's/com\.airhacks\.afterburner\.injection\.Injector/org.jabref.injection.Injector/g'
  ```
- [x] jabkit `module-info.java`: remove `requires afterburner.fx;` (Injector now comes from jablib's exported package).
- [x] `jabsrv/src/test/java/org/jabref/http/JabSrvArchitectureTest.java`: change forbidden package
  `com.airhacks.afterburner.injection..` → `org.jabref.injection..`; reword the comment/`because(...)` text.
- [x] Check jabgui architecture tests for afterburner references — none found.
- [x] Compile: `./gradlew :jabkit:compileJava` OK. (:jablib:compileTestJava deferred to Phase 5 — LocalizationParser still references afterburner ViewLoader.)

## Phase 4 — ViewLoader shim in jabgui

- [x] Create `jabgui/src/main/java/org/jabref/gui/util/ViewLoader.java` — port of the fork's ViewLoader
  (see "Fork semantics" above; fork source: github.com/JabRef/afterburner.fx, Apache-2.0):
  - `static ViewLoader view(Class<?>)` / `static ViewLoader view(Object controller)` (= view(class) + controller(obj))
  - `controller(Object)`: controllerFactory returns the given instance for its own class (after
    `Injector.registerExistingAndInject(instance)`), else `Injector.instantiatePresenter(type)` (fx:include support)
  - default controllerFactory (for `view(Class)`): `Injector.instantiatePresenter(type)`
  - `root(Object)` → `fxmlLoader.setRoot(root)`
  - `load()` → sets `fxmlLoader.setResources(Localization.getMessages())`, loads (IOException →
    IllegalStateException), attaches co-located .bss/.css, returns `ViewLoaderResult`
  - FXML/CSS name resolution: strip `View` suffix, try lowercase then CamelCase (FXML mandatory, CSS optional)
- [x] Create `jabgui/src/main/java/org/jabref/gui/util/InjectorDiAdapter.java` implementing
  `com.dlsc.fxmlkit.di.DiAdapter` (`getInstance` → `Injector.instantiatePresenter`, `injectMembers` →
  `Injector.registerExistingAndInject`).
- [x] Create `jabgui/src/main/java/org/jabref/gui/util/ViewLoaderResult.java` — same API as fork:
  `getView()`, `getController()`, `setAsDialogPane(Dialog<?>)`, `setAsContent(DialogPane)`. (Skip
  `getViewWithoutRootContainer`/`getResourceBundle` — unused.)
- [x] Import swap in jabgui (done repo-wide; CAUTION: the sed also rewrites doc comments containing the literal — fixed by hand in the two new files):
  ```bash
  grep -rl 'com\.airhacks\.afterburner\.views\.ViewLoader\b' --include='*.java' jabgui \
    | xargs sed -i 's/com\.airhacks\.afterburner\.views\.ViewLoader/org.jabref.gui.util.ViewLoader/g'
  grep -rl 'com\.airhacks\.afterburner\.views\.ViewLoaderResult' --include='*.java' jabgui \
    | xargs sed -i 's/com\.airhacks\.afterburner\.views\.ViewLoaderResult/org.jabref.gui.util.ViewLoaderResult/g'
  ```
  Note: files in `org.jabref.gui.util` itself then have a same-package import — remove those redundant imports if any.
- [x] Wire FxmlKit globals in `jabgui/src/main/java/org/jabref/gui/JabRefGUI.java` (done in start(), before initialize()) (in `start()`/init, near the
  existing `Injector.setModelOrService` bootstrap):
  ```java
  FxmlKit.setDiAdapter(new InjectorDiAdapter());
  FxmlKit.setResourceBundle(Localization.getMessages());
  ```
- [x] Delete `jabgui/src/main/java/org/jabref/gui/util/JabRefResourceLocator.java` and
  `jabgui/src/main/java/org/jabref/gui/l10n/LocalizationLocator.java` (verify no references first:
  `grep -rn "JabRefResourceLocator\|LocalizationLocator" jabgui`).
- [x] `jabgui/src/main/java/module-info.java`: remove `requires afterburner.fx;` (line ~24) and the
  `provides com.airhacks.afterburner.views.ResourceLocator with ...;` clause (lines ~25-26); add `requires com.dlsc.fxmlkit;`.
- [x] Compile: `./gradlew :jabgui:compileJava`.

## Phase 5 — Remaining modules & test utilities

- [x] `jabsrv-cli`: delete `src/main/java/org/jabref/http/cli/JabRefResourceLocator.java`; in module-info remove
  `requires afterburner.fx;` and the `provides ... ResourceLocator` clause.
- [x] `jabls-cli`: delete `src/main/java/org/jabref/languageserver/cli/JabRefResourceLocator.java`; remove
  `requires afterburner.fx;` from module-info.
- [x] (DONE: removed the vestigial MockedStatic + unused imports; plain FXMLLoader static-load kept) `jablib/src/test/java/org/jabref/logic/l10n/LocalizationParser.java:186-190`: currently static-mocks
  afterburner's ViewLoader before parsing FXMLs. Read the surrounding code and rework — afterburner is gone, and
  jabgui's new ViewLoader is not visible from jablib tests. Likely fix: mock/stub nothing and use a plain
  `FXMLLoader` with a no-op controller factory, since the mock only existed to suppress controller creation.
- [x] Compile everything: all listed compile tasks pass.

## Phase 6 — Docs & cleanup

- [x] `grep -rni "airhacks\|afterburner" --include='*.java' --include='*.kts' --include='*.toml' .` → must be empty
  (except possibly docs/changelog prose).
- [x] Check docs/text references: updated `docs/code-howtos/index.md` (DI section), `ImporterPreferences` comment, `.jbang/JabSrvLauncher.java` (removed afterburner DEPS, reworded JavaFX pin comment). `external-libraries.md` needs no change (libraries listed via generated SBOM). `grep -rni afterburner docs/ *.md` — update
  `external-libraries.md` (replace afterburner.fx entry with FxmlKit, Apache-2.0) and anything else found.
- [x] `CHANGELOG.md`: added entry under Changed — TODO fill in PR link when the PR exists under "Changed": replaced afterburner.fx fork with FxmlKit for FXML loading and DI.
- [x] ADR created: `docs/decisions/0065-replace-afterburner-fx-with-fxmlkit.md` documenting the FxmlKit choice (follow existing MADR template numbering).

## Phase 7 — Verification

- [x] `./gradlew :jablib:test --tests "org.jabref.logic.l10n.LocalizationConsistencyTest"` — PASSED.
- [x] `./gradlew :jabsrv:test --tests "org.jabref.http.JabSrvArchitectureTest"` and `:jabgui:test --tests "*ArchitectureTest*"` — PASSED. Also added+ran `jablib/src/test/java/org/jabref/injection/InjectorTest.java` (create-and-cache, @Inject resolution, fresh presenters) — PASSED.
- [x] Broader test run: `./gradlew :jablib:test :jabgui:test` — jablib green. jabgui: one REAL regression found and
  fixed (JabGuiArchitectureTest: new ViewLoader needs `@AllowedToUseClassGetResource` — added, test green now).
  Remaining jabgui failures are NOT regressions: 23 suites fail with "Unable to open DISPLAY" (TestFX needs an X
  server; this machine is headless — they fail before any JabRef code runs), and KeyBindingViewModelTest +
  JournalAbbreviationRepositoryTest fail IDENTICALLY on the pre-migration tree (verified via git stash baseline
  run) — pre-existing, environment-dependent.
- [ ] GUI smoke test: `./gradlew :jabgui:run` — open About dialog (DialogPane path), entry editor
  (fx:root field editors path), preferences (fx:include path), Integrity check dialog (view(Class) path).
- [x] Commit + push to koppor fork + draft PR there (user requested). PLAN.md included in the draft on purpose (working state); remove it and fill in the CHANGELOG PR link (marked TODO) before the real JabRef/jabref PR.

## Stretch (separate PR material, not required)

- [ ] Enable FxmlKit hot reload in dev builds (`FxmlKit.enableDevelopmentMode()` guarded by build/debug flag, or
  document `-Dfxmlkit.devmode=true`).
- [ ] Consider `FxmlView`-based pattern for newly written views (view/controller split); document in CONTRIBUTING/devdocs.
