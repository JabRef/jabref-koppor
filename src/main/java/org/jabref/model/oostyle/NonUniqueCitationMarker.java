package org.jabref.model.oostyle;

/**
 * What should createCitationMarker do if it discovers that
 * uniqueLetters provided are not sufficient for unique presentation?
 */
public enum NonUniqueCitationMarker {
    /** Give an insufficient representation anyway.  */
    FORGIVEN,
    /** Throw a RuntimeException */
    THROWS
}

