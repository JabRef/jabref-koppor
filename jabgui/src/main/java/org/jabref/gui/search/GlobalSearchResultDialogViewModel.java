package org.jabref.gui.search;

import com.tobiasdiez.easybind.EasyBind;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jabref.logic.search.SearchPreferences;
import org.jabref.model.database.BibDatabaseContext;

public class GlobalSearchResultDialogViewModel {

    private final BibDatabaseContext searchDatabaseContext =
        new BibDatabaseContext();
    private final BooleanProperty keepOnTop = new SimpleBooleanProperty();

    public GlobalSearchResultDialogViewModel(
        SearchPreferences searchPreferences
    ) {
        keepOnTop.set(searchPreferences.shouldKeepWindowOnTop());

        EasyBind.subscribe(
            this.keepOnTop,
            searchPreferences::setKeepWindowOnTop
        );
    }

    public BibDatabaseContext getSearchDatabaseContext() {
        return searchDatabaseContext;
    }

    public BooleanProperty keepOnTop() {
        return this.keepOnTop;
    }
}
