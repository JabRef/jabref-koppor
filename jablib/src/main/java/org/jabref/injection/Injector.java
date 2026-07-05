package org.jabref.injection;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Inject;

/// Static service locator for JabRef's application-wide singletons.
///
/// Replacement for afterburner.fx's `com.airhacks.afterburner.injection.Injector`,
/// keeping its semantics:
///
/// - [#instantiateModelOrService(Class)] returns the registered singleton, lazily creating
///   (and caching) one via the no-arg constructor if none is registered yet.
/// - [#registerExistingAndInject(Object)] resolves `jakarta.inject.@Inject` fields of an
///   existing instance from this locator; referenced services are created on demand.
/// - [#instantiatePresenter(Class)] creates a *fresh*, injected instance per call
///   (used for FXML controllers, which must not be singletons).
///
/// In the GUI, FXML controller injection is bridged to FxmlKit via a `DiAdapter`
/// backed by this class.
public class Injector {

    private static final Map<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private Injector() {
    }

    /// Registers the given instance as the application-wide singleton for the given class.
    public static <T> void setModelOrService(Class<T> clazz, T instance) {
        SERVICES.put(clazz, instance);
    }

    /// Returns the singleton registered for the given class. If none is registered, the class
    /// is instantiated via its no-arg constructor, cached as the singleton, and its `@Inject`
    /// fields are resolved.
    public static <T> T instantiateModelOrService(Class<T> clazz) {
        Object service = SERVICES.get(clazz);
        if (service == null) {
            SERVICES.putIfAbsent(clazz, instantiate(clazz));
            service = SERVICES.get(clazz);
            // Registering before injecting lets cyclic @Inject references terminate
            registerExistingAndInject(service);
        }
        return clazz.cast(service);
    }

    /// Resolves the `@Inject` fields of an already existing instance. Fields that are already
    /// set are left untouched. The instance itself is not registered as a singleton.
    public static <T> T registerExistingAndInject(T instance) {
        Class<?> clazz = instance.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injectField(field, instance);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return instance;
    }

    /// Creates a fresh instance of the given class via its no-arg constructor and resolves its
    /// `@Inject` fields. In contrast to [#instantiateModelOrService(Class)], nothing is cached:
    /// every call returns a new instance.
    public static <T> T instantiatePresenter(Class<T> clazz) {
        return registerExistingAndInject(instantiate(clazz));
    }

    private static void injectField(Field field, Object instance) {
        try {
            field.setAccessible(true);
            if (field.get(instance) == null) {
                field.set(instance, instantiateModelOrService(field.getType()));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot inject field %s of %s".formatted(field.getName(), instance.getClass().getName()), e);
        }
    }

    private static <T> T instantiate(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot instantiate " + clazz.getName(), e);
        }
    }
}
