package org.jabref.model.oostyle;

import java.util.Optional;

/**
 * This is what we need for numeric citation markers.
 */
public interface CitationMarkerNumericEntry {

    String getCitationKey();

    /**
     * @return Optional.empty() for unresolved
     */
    Optional<Integer> getNumber();

    Optional<OOText> getPageInfo();
}
