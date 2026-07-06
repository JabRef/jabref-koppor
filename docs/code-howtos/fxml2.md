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
4. **Localized keys**: add `implements LocalizedView` (`org.jabref.gui.l10n`) to the class — required for ANY `%key`
   use, plain or quoted (`StaticResource` throws at runtime otherwise: "requires the root element to implement
   ResourceContextProvider"). In the markup, keys keep JabRef's key=value convention. Syntax: `%Simple key with
   spaces` works as-is, but keys containing apostrophes, commas, **or a trailing period** MUST use the quoted form
   with escaped apostrophes: `text="%'Move DOIs from \'note\' field and \'URL\' field to \'DOI\' field and remove
   http prefix'"`. (In the plain form, the parser unquotes apostrophes, treats commas as argument separators, and
   fails to compile — `processFxml` error `'}' expected` — on a key ending in `.`, since it reads the trailing dot
   as the start of a member-access continuation it can't finish.)
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
   * `fx:id` on a generic control (e.g. `TagsField<X>`): the generated base field is the RAW type — no
     type-argument syntax exists in FXML/2. Lambdas relying on the generic method's inferred type will fail to
     compile against the raw field. Fix by casting once to the parameterized type and using that typed
     reference everywhere instead of the raw inherited field (see `LinkedEntriesEditor`).
   * `%key` ending in a period (`.`) needs the quoted form even without `' , ; { }` — the plain-form parser reads
     a trailing `.` as a member-access continuation and fails with `processFxml` error `'}' expected`.
   * `${viewModel.x}` requires `x` to be backed by a real `xProperty()` — a plain derived getter with no
     matching property fails with `processFxml` error "... is not a valid binding source, required
     javafx.beans.value.ObservableValue". Classic FXML silently accepted the same expression (non-reactively).
     Since ViewModels must not be changed to add a missing property, move that one binding into code-behind:
     give the element an `fx:id`, drop `${...}`, and bind imperatively (e.g. off a sibling property's `.not()`)
     after `initializeComponent()`. See `IdentifierEditor`.
   * A generic class (`class MyEditor<T> extends HBox`) can still be an `fx:subclass`: the generated base is
     simply non-generic (`MyEditorBase extends HBox`, raw fields) and the generic subclass extends it — Java
     allows that. See `OptionEditor`.
   * Classic FXMLLoader's auto-called `@FXML private void initialize()` lifecycle hook (the `Initializable`
     convention) has NO FXML/2 equivalent — nothing calls a method named `initialize()` automatically. Rename it
     to a plain method (drop `@FXML`) and call it explicitly right after `initializeComponent()`. See
     `LinkedFilesEditor`.
   * A family of views sharing `abstract class AbstractXView extends VBox` (for one common method or field) can't
     keep that shared base once converted: the fx:subclass must extend the GENERATED `XViewBase extends VBox`,
     and Java only allows one superclass. Drop the shared abstract class and have each view `implement` the
     interface directly (duplicating the one-liner it provided). See the `automaticfiededitor` tab family, where
     `AbstractAutomaticFieldEditorTabView` (only `getContent() { return this; }`) was removed.
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
