package org.jabref.logic.oostyle;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jabref.model.oostyle.Citation;
import org.jabref.model.oostyle.CitationGroup;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.CitationGroups;
import org.jabref.model.oostyle.ListUtil;
import org.jabref.model.oostyle.OOFormattedText;

class OOProcessNumericMarkers {

    /**
     * Produce citation markers for the case of numbered citations
     * with bibliography sorted by first appearance in the text.
     *
     * @param cgs
     * @param style
     *
     * @return Numbered citation markers for each CitationGroupID.
     *         Numbering is according to first appearance.
     *         Assumes global order and local order ae already applied.
     *
     */
    static Map<CitationGroupID, OOFormattedText>
    produceCitationMarkersForIsNumberEntriesIsSortByPosition(CitationGroups cgs, OOBibStyle style) {

        assert style.isNumberEntries();
        assert style.isSortByPosition();

        cgs.createNumberedBibliographySortedInOrderOfAppearance();

        final int minGroupingCount = style.getMinimumGroupingCount();

        Map<CitationGroupID, OOFormattedText> citMarkers = new HashMap<>();

        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {
            List<Citation> cits = cg.getCitationsInLocalOrder();
            citMarkers.put(cg.cgid,
                           style.getNumCitationMarker(ListUtil.map(cits, Citation::getNumberOrThrow),
                                                      minGroupingCount,
                                                      ListUtil.map(cits, Citation::getPageInfo)));
        }

        return citMarkers;
    }

    /**
     * Produce citation markers for the case of numbered citations
     * when the bibliography is not sorted by position.
     */
    static Map<CitationGroupID, OOFormattedText>
    produceCitationMarkersForIsNumberEntriesNotSortByPosition(CitationGroups cgs, OOBibStyle style) {
        assert style.isNumberEntries();
        assert !style.isSortByPosition();

        cgs.createNumberedBibliographySortedByComparator(OOProcess.AUTHOR_YEAR_TITLE_COMPARATOR);

        final int minGroupingCount = style.getMinimumGroupingCount();

        Map<CitationGroupID, OOFormattedText> citMarkers = new HashMap<>();

        for (CitationGroup cg : cgs.getCitationGroupsInGlobalOrder()) {
            List<Citation> cits = cg.getCitationsInLocalOrder();
            citMarkers.put(cg.cgid,
                           style.getNumCitationMarker(ListUtil.map(cits, Citation::getNumberOrThrow),
                                                      minGroupingCount,
                                                      ListUtil.map(cits, Citation::getPageInfo)));
        }

        return citMarkers;
    }

    static Map<CitationGroupID, OOFormattedText>
    produceCitationMarkers(CitationGroups cgs, OOBibStyle style) {
        if (style.isSortByPosition()) {
            return produceCitationMarkersForIsNumberEntriesIsSortByPosition(cgs, style);
        } else {
            return produceCitationMarkersForIsNumberEntriesNotSortByPosition(cgs, style);
        }
    }
}
