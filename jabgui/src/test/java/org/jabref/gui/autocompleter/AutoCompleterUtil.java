package org.jabref.gui.autocompleter;

import org.controlsfx.control.textfield.AutoCompletionBinding.ISuggestionRequest;

public class AutoCompleterUtil {
    public static ISuggestionRequest getRequest(String text) {
        return new ISuggestionRequest() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public String getUserText() {
                return text;
            }
        };
    }
}
