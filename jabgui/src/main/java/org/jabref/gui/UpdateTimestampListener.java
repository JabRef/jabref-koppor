package org.jabref.gui;

import com.google.common.eventbus.Subscribe;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.entry.event.EntriesEventSource;
import org.jabref.model.entry.event.EntryChangedEvent;
import org.jabref.model.entry.field.StandardField;

/**
 * Updates the timestamp of changed entries if the feature is enabled
 */
class UpdateTimestampListener {

    private final CliPreferences preferences;

    UpdateTimestampListener(CliPreferences preferences) {
        this.preferences = preferences;
    }

    @Subscribe
    public void listen(EntryChangedEvent event) {
        // The event source needs to be checked, since the timestamp is always updated on every change. The cleanup formatter is an exception to that behavior,
        // since it just should move the contents from the timestamp field to modificationdate or creationdate.
        if (
            preferences.getTimestampPreferences().shouldAddModificationDate()
            && event.getEntriesEventSource()
            != EntriesEventSource.CLEANUP_TIMESTAMP
        ) {
            event
                .getBibEntry()
                .setField(
                    StandardField.MODIFICATIONDATE,
                    preferences.getTimestampPreferences().now()
                );
        }
    }
}
