---
parent: Code Howtos
---
# Converting a view to FXML/2

JabRef is incrementally migrating its views from classic FXML (loaded at runtime via `org.jabref.gui.util.ViewLoader`)
to [FXML/2](https://jfxcore.github.io/fxml-compiler/), which compiles the markup at build time into a Java base class
(scene graph as plain bytecode — no `FXMLLoader`, no reflection). Classic and FXML/2 views coexist; convert view by view.
The already converted `org.jabref.gui.cleanup` family serves as the reference for all three patterns.

## Recipe

1. **Move** the `.fxml` file from `src/main/resources/...` to `src/main/java/...`, next to its Java class
   (same name as the class, e.g. `CleanupMultiFieldPanel.fxml` for `CleanupMultiFieldPanel.java`).
2. **Rewrite the FXML header**: root element is the concrete node type (no `fx:root`, no `fx:controller`) with
   `xmlns="http://javafx.com/javafx" xmlns:fx="http://jfxcore.org/fxml/2.0"` and
   `fx:subclass="org.jabref.gui...YourClass"`.
3. **Rewrite the Java class**: extend the generated `YourClassBase` (instead of `VBox`/`HBox`/...), delete all
   `@FXML` fields (they are inherited from the generated base), replace the `ViewLoader` call with
   `initializeComponent()`. Keep the MVVM shape: create the ViewModel first, then `initializeComponent()`,
   then `bindProperties()`.
4. **Localized keys**: add `implements LocalizedView` (`org.jabref.gui.l10n`) to the class. In the markup, keys
   keep JabRef's key=value convention. Syntax: `%Simple key with spaces` works as-is, but keys containing
   apostrophes or commas MUST use the quoted form with escaped apostrophes:
   `text="%'Move DOIs from \'note\' field and \'URL\' field to \'DOI\' field and remove http prefix'"`.
   (In the plain form, the parser unquotes apostrophes and treats commas as argument separators.)
5. **Dialogs** (`XDialog extends BaseDialog<T>` with a `DialogPane` root): split off a pane class. The FXML becomes
   `XDialogPane.fxml` with `fx:subclass="...XDialogPane"`; the pane code-behind is minimal
   (`initializeComponent()` in the constructor). The dialog class keeps all logic, calls
   `setDialogPane(new XDialogPane())`, and reaches `fx:id` members through the pane's protected fields.
   `ButtonType` children work with named constructor arguments (`text`, `buttonData`) and `fx:constant="CANCEL"`.
   See `CleanupDialog`/`CleanupDialogPane`.
6. **Dialect differences to watch for**:
   * Attributes are named constructor arguments: partial `<Insets top="10" left="20"/>` must list all four sides.
   * `fx:define` + `$id` references (e.g. `ToggleGroup`) work unchanged.
   * The FXML/2 file is a source file — do not reference it as a runtime resource.
7. **Build**: after ADDING a new `.fxml` file, run the first build with `--no-configuration-cache`
   (the FXML plugin scans for markup files at configuration time, so a reused configuration cache does not see
   new files). Subsequent builds are fine. The Gradle daemon needs JDK 24+; this is pinned project-wide in
   `gradle/gradle-daemon-jvm.properties`.
8. **Verify**: module compiles, `LocalizationConsistencyTest` stays green (it extracts `%` keys from FXML/2
   sources textually), and add/extend a TestFX test asserting the scene graph and localized texts
   (see `CleanupMultiFieldPanelTest`).

## Background

* `JabRefResourceContext` resolves `%key` through `Localization.lang(key, args)` — properties files and `%0`
  placeholders are untouched; `MessageFormat` is deliberately not used.
* Once a view is FXML/2, `fx:context` + `${...}` expression bindings can move `bindProperties()` into the markup
  (compile-time checked MVVM bindings). This is intentionally not part of the 1:1 conversion.
* Plan and verified findings: `PLAN-FXML2.md` in the repository root (temporary, while the migration runs).
