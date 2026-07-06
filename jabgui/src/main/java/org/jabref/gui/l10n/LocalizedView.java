package org.jabref.gui.l10n;

import org.jfxcore.markup.resource.ResourceContext;
import org.jfxcore.markup.resource.ResourceContextProvider;

/// Implemented by FXML/2 views whose markup uses localized `%key` resources.
/// The FXML/2 runtime requires the root element (= the code-behind class) to provide the
/// [ResourceContext]; this interface plugs in JabRef's localization for all such views.
public interface LocalizedView extends ResourceContextProvider {

    @Override
    default ResourceContext getResourceContext() {
        return JabRefResourceContext.INSTANCE;
    }
}
