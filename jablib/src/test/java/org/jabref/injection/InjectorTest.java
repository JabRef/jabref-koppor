package org.jabref.injection;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class InjectorTest {

    static class CachedService {
    }

    static class RegisteredService {
    }

    static class OnDemandService {
    }

    static class Presenter {
        @Inject RegisteredService registeredService;
        @Inject OnDemandService onDemandService;
    }

    @Test
    void instantiateModelOrServiceCachesCreatedInstance() {
        CachedService first = Injector.instantiateModelOrService(CachedService.class);
        assertSame(first, Injector.instantiateModelOrService(CachedService.class));
    }

    @Test
    void registeredInstanceIsReturned() {
        RegisteredService instance = new RegisteredService();
        Injector.setModelOrService(RegisteredService.class, instance);
        assertSame(instance, Injector.instantiateModelOrService(RegisteredService.class));
    }

    @Test
    void injectedFieldsAreResolvedFromRegisteredSingletons() {
        RegisteredService instance = new RegisteredService();
        Injector.setModelOrService(RegisteredService.class, instance);

        Presenter presenter = Injector.registerExistingAndInject(new Presenter());

        assertSame(instance, presenter.registeredService);
    }

    @Test
    void injectedFieldsOfUnregisteredTypesAreCreatedAndCached() {
        Presenter presenter = Injector.registerExistingAndInject(new Presenter());

        assertNotNull(presenter.onDemandService);
        assertSame(presenter.onDemandService, Injector.instantiateModelOrService(OnDemandService.class));
    }

    @Test
    void alreadySetFieldsAreLeftUntouched() {
        RegisteredService existing = new RegisteredService();
        Presenter presenter = new Presenter();
        presenter.registeredService = existing;

        Injector.registerExistingAndInject(presenter);

        assertSame(existing, presenter.registeredService);
    }

    @Test
    void presentersAreFreshInstances() {
        assertNotSame(Injector.instantiatePresenter(Presenter.class), Injector.instantiatePresenter(Presenter.class));
    }
}
