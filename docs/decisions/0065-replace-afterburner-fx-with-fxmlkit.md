---
nav_order: 65
parent: Decision Records
---
# Replace afterburner.fx with FxmlKit and a JabRef-owned service locator

## Context and Problem Statement

Since [ADR-0055](0055-dependency-injection-approach.md), JavaFX controllers were wired using a [JabRef-maintained fork](https://github.com/JabRef/afterburner.fx) of the abandoned [afterburner.fx](https://github.com/AdamBien/afterburner.fx) framework.
Maintaining the fork (JavaFX version pins in its POM, module metadata patches in the build, releases) is a recurring burden.
Which actively maintained replacement should JabRef use for convention-based FXML loading and for the application-wide service locator?

## Decision Drivers

* No self-maintained fork of an abandoned framework.
* Keep JabRef's established view patterns working unchanged: views are their own FXML controllers (`fx:controller` pointing to the view class, instance pre-existing) and custom controls use `fx:root` — 118 view classes rely on this.
* Keep `jakarta.inject.@Inject` field injection and the `Injector.setModelOrService`/`instantiateModelOrService` service-locator semantics (~70 classes, tests included).
* Keep jablib (the library module) free of GUI-framework dependencies.
* No memory-behavior regressions (views are created per dialog/editor instance).

## Considered Options

* [FxmlKit](https://github.com/dlsc-software-consulting-gmbh/FxmlKit) plus thin JabRef-owned `ViewLoader` and `Injector`
* FxmlKit wholesale (all views migrated to `FxmlView`)
* Keep the afterburner.fx fork
* A full DI framework (e.g. Guice) plus plain `FXMLLoader`

## Decision Outcome

Chosen option: "FxmlKit plus thin JabRef-owned `ViewLoader` and `Injector`".

* `org.jabref.injection.Injector` (jablib, self-contained, ~90 lines): static service locator with afterburner's exact semantics (`instantiateModelOrService` creates and caches singletons on demand, `registerExistingAndInject` resolves `jakarta.inject.@Inject` fields, `instantiatePresenter` creates fresh injected controller instances).
* `org.jabref.gui.util.ViewLoader`/`ViewLoaderResult` (jabgui): drop-in replacement for afterburner's fluent loader on top of `FXMLLoader`, keeping the naming conventions (`AboutDialogView` → `AboutDialog.fxml`, co-located `.css`/`.bss` auto-attached) and the `fx:root`/"view is controller" patterns.
* FxmlKit (jabgui only): wired globally via `FxmlKit.setDiAdapter(new InjectorDiAdapter())` and `FxmlKit.setResourceBundle(Localization.getMessages())`, so newly written views can use FxmlKit's `FxmlView`/`FxmlViewProvider` (and its FXML/CSS hot reload during development) with the same injection and localization sources.

### Consequences

* Good, because the afterburner.fx fork and its build workarounds are gone; jablib no longer depends on any GUI framework.
* Good, because all 118 views keep working without restructuring; the migration is import swaps plus two small classes.
* Good, because new views can adopt FxmlKit natively (hot reload, `@FxmlObject`, lazy `FxmlViewProvider`).
* Bad, because JabRef still owns ~200 lines of view-loading/injection glue (kept deliberately small).

## Pros and Cons of the Options

### FxmlKit wholesale

* Good, because a single framework handles loading, DI, and hot reload.
* Bad, because `FxmlView` extends `StackPane` and instantiates the controller itself: it supports neither `fx:root` custom controls nor pre-existing view instances as controllers. All 118 views (dialogs deriving from `BaseDialog`, field editors deriving from `HBox`, …) would need a view/controller split and new base classes — months of risky rework.
* Bad, because FxmlKit's `LiteDiAdapter` tracks every injected object in a strong-reference identity set; injecting per-dialog view instances through it would leak them (afterburner used weak sets). Its reflection utilities are in a non-exported package and cannot be reused with different tracking.

### Keep the afterburner.fx fork

* Good, because nothing changes.
* Bad, because the maintenance burden (JavaFX pins, metadata patches, releases of the fork) remains indefinitely.

### Full DI framework (e.g. Guice) plus plain FXMLLoader

* Good, because constructor injection becomes possible.
* Bad, because it adds a heavyweight dependency and would still require the same JabRef-owned `ViewLoader` glue for `fx:root` and conventions.
* Bad, because migrating 70 service-locator call sites to real DI is a separate, much larger refactoring (out of scope here).
