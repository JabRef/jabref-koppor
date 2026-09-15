package org.jabref.gui.util;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import org.jabref.architecture.AllowedToUseClassGetResource;
import org.jabref.injection.Injector;
import org.jabref.logic.l10n.Localization;

import org.jspecify.annotations.NullMarked;

/// Convention-based FXML loader, a drop-in replacement for afterburner.fx's
/// `ViewLoader` (same fluent API and conventions).
///
/// Conventions: the FXML file lives in the same package as the view class and is named after it
/// with a trailing `View` suffix stripped (`AboutDialogView` → `AboutDialog.fxml`, lowercase
/// variant `aboutdialog.fxml` also supported). A co-located `.bss`/`.css` file with the same
/// base name is attached automatically.
///
/// Controllers referenced via `fx:controller` are created through
/// [Injector#instantiatePresenter(Class)] — a fresh instance per load with `@Inject` fields
/// resolved from the service locator. With [#view(Object)], the given (already existing)
/// instance is used as the controller for its own class instead.
///
/// Note: this loader intentionally does not use FxmlKit's `FxmlView` (which wraps views in a
/// `StackPane`), because JabRef's views are their own controllers and often their own
/// `fx:root`, which FxmlKit does not support.
@AllowedToUseClassGetResource("FXML files and stylesheets have to be located and attached by URL")
@NullMarked
public class ViewLoader {

    private static final String VIEW_SUFFIX = "View";
    private static final String FXML_FILE_ENDING = ".fxml";
    private static final String CSS_FILE_ENDING = ".css";
    private static final String BSS_FILE_ENDING = ".bss";

    private final Class<?> clazz;
    private final FXMLLoader fxmlLoader;

    private ViewLoader(Class<?> clazz) {
        this.clazz = clazz;
        URL fxmlUrl = findResourceUrl(FXML_FILE_ENDING).orElseThrow(
                () -> new IllegalStateException("Cannot find FXML file for view " + clazz.getName()));
        this.fxmlLoader = new FXMLLoader(fxmlUrl);
        fxmlLoader.setControllerFactory(Injector::instantiatePresenter);
    }

    /// Prepares loading of the FXML file belonging to the given view class.
    /// The FXML (and other resources) are not yet loaded.
    public static ViewLoader view(Class<?> clazz) {
        return new ViewLoader(clazz);
    }

    /// Prepares loading of the FXML file belonging to the given view object.
    /// The given object is used as the controller of its own class.
    /// The FXML (and other resources) are not yet loaded.
    public static ViewLoader view(Object controller) {
        return view(controller.getClass()).controller(controller);
    }

    /// Uses the given existing instance as the controller.
    ///
    /// We keep `fxmlLoader.setControllerFactory` (instead of `setController`) so that the
    /// `fx:controller` attribute stays allowed in the FXML file and IDE support is kept.
    /// Controllers of other classes (e.g. from `fx:include`) are still freshly instantiated.
    public ViewLoader controller(Object controller) {
        fxmlLoader.setControllerFactory(type -> {
            if (type == controller.getClass()) {
                return Injector.registerExistingAndInject(controller);
            }
            return Injector.instantiatePresenter(type);
        });
        return this;
    }

    /// Sets the root of the object hierarchy, used as the value of the `fx:root` tag.
    /// Must be called prior to [#load()] when the FXML uses `fx:root`.
    public ViewLoader root(Object root) {
        fxmlLoader.setRoot(root);
        return this;
    }

    /// Synchronously loads the FXML file and associated resources.
    ///
    /// @return a composite object giving access to the loaded root node and initialized controller
    /// @throws IllegalStateException if an exception occurred during loading and parsing of the FXML file
    public ViewLoaderResult load() {
        fxmlLoader.setResources(Localization.getMessages());
        try {
            fxmlLoader.load();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load FXML view of " + clazz.getName(), ex);
        }

        Parent parent = fxmlLoader.getRoot();
        addStyleSheetIfAvailable(parent);
        return new ViewLoaderResult(parent, fxmlLoader.getController());
    }

    private void addStyleSheetIfAvailable(Parent parent) {
        // .bss files are binary encoded css files which JavaFX produces
        findResourceUrl(BSS_FILE_ENDING)
                .or(() -> findResourceUrl(CSS_FILE_ENDING))
                .map(URL::toExternalForm)
                .filter(uri -> !parent.getStylesheets().contains(uri))
                .ifPresent(uri -> parent.getStylesheets().add(uri));
    }

    /// Resolves a co-located resource named after the view class ("View" suffix stripped),
    /// trying the lowercase variant first, then the camel-case variant.
    private Optional<URL> findResourceUrl(String ending) {
        String baseName = clazz.getSimpleName();
        if (baseName.endsWith(VIEW_SUFFIX)) {
            baseName = baseName.substring(0, baseName.lastIndexOf(VIEW_SUFFIX));
        }
        String lowerCaseName = baseName.toLowerCase(Locale.ROOT) + ending;
        String camelCaseName = baseName + ending;
        return Optional.ofNullable(clazz.getResource(lowerCaseName))
                       .or(() -> Optional.ofNullable(clazz.getResource(camelCaseName)));
    }
}
