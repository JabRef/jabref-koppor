package org.jabref.gui.util;

import org.jabref.injection.Injector;

import com.dlsc.fxmlkit.di.DiAdapter;
import org.jspecify.annotations.NullMarked;

/// Bridges FxmlKit's dependency injection to JabRef's [Injector] service locator.
///
/// Registered via `FxmlKit.setDiAdapter(new InjectorDiAdapter())` at application start, so
/// that views built on FxmlKit's `FxmlView`/`FxmlViewProvider` get their controllers created
/// and `@Inject`-annotated fields resolved from the same singletons as the rest of the GUI.
///
/// Controllers are created freshly per [#getInstance(Class)] call and are never cached —
/// a controller (and its scene graph) must not be shared between two view instances.
@NullMarked
public class InjectorDiAdapter implements DiAdapter {

    @Override
    public <T> T getInstance(Class<T> type) {
        return Injector.instantiatePresenter(type);
    }

    @Override
    public void injectMembers(Object target) {
        Injector.registerExistingAndInject(target);
    }
}
