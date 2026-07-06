package org.jabref.gui.l10n;

import org.jabref.logic.l10n.Localization;

import org.jfxcore.markup.resource.ResourceContext;
import org.jspecify.annotations.NullMarked;

/// Resolves resource keys of FXML/2 markup (`%key` / `StaticResource`) through JabRef's
/// [Localization], keeping JabRef's `key=value` convention: the key is the English text,
/// and formatting uses JabRef's `%0`-style placeholders (not `java.text.MessageFormat`).
@NullMarked
public class JabRefResourceContext implements ResourceContext {

    public static final JabRefResourceContext INSTANCE = new JabRefResourceContext();

    private JabRefResourceContext() {
    }

    @Override
    public String getString(String key, Object... args) {
        // the FXML/2 runtime passes null when the markup declares no formatArguments
        Object[] params = args == null ? new Object[0] : args;
        return Localization.lang(key, params);
    }

    @Override
    public <T> T getObject(String key, Class<T> type) {
        if (type.isAssignableFrom(String.class)) {
            return type.cast(Localization.lang(key));
        }
        throw new IllegalArgumentException("JabRef localization only provides strings, but %s was requested for key '%s'".formatted(type.getName(), key));
    }
}
