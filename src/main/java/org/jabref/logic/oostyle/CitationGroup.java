package org.jabref.logic.oostyle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jabref.logic.openoffice.StorageBase;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.oostyle.CitationGroupID;
import org.jabref.model.oostyle.InTextCitationType;
import org.jabref.model.oostyle.OOFormattedText;

/*
 * A CitationGroup describes a group of citations.
 */
public class CitationGroup {

    /*
     * Identifies this citation group.
     */
    public CitationGroupID cgid;

    /*
     * Identifies location in the document for the backend.
     * Could be moved to the backend (which is currently stateless),
     * and use cgid to identify.
     */
    public StorageBase.NamedRange cgRangeStorage;


    /*
     * The core data, stored in the document:
     * The type of citation and citations in storage order.
     */
    public InTextCitationType citationType;
    public List<Citation> citationsInStorageOrder;

    /*
     * Extra data added during processing:
     */

    /*
     * Indices into citations: citations[localOrder[i]] provides ith
     * citation according to the currently imposed local order for
     * presentation.
     */
    public List<Integer> localOrder;

    /*
     * A name of a reference mark to link to by formatCitedOnPages.
     * May be empty, if Backend does not use reference marks.
     *
     * produceCitationMarkers might want fill it to support
     * cross-references to citation groups from the bibliography.
     */
    private Optional<String> referenceMarkNameForLinking;

    /*
     * "Cited on pages" uses this to sort the cross-references.
     */
    private Optional<Integer> indexInGlobalOrder;

    public CitationGroup(CitationGroupID cgid,
                         StorageBase.NamedRange cgRangeStorage,
                         InTextCitationType citationType,
                         List<Citation> citationsInStorageOrder,
                         Optional<String> referenceMarkNameForLinking) {
        this.cgid = cgid;
        this.cgRangeStorage = cgRangeStorage;
        this.citationType = citationType;
        this.citationsInStorageOrder = citationsInStorageOrder;
        this.localOrder = makeIndices(citationsInStorageOrder.size());
        this.referenceMarkNameForLinking = referenceMarkNameForLinking;
        this.indexInGlobalOrder = Optional.empty();
    }

    public int numberOfCitations() {
        return citationsInStorageOrder.size();
    }

    public void setIndexInGlobalOrder(Optional<Integer> i) {
        this.indexInGlobalOrder = i;
    }

    public Optional<Integer> getIndexInGlobalOrder() {
        return this.indexInGlobalOrder;
    }

    public Optional<String> getReferenceMarkNameForLinking() {
        return referenceMarkNameForLinking;
    }

    public void setReferenceMarkNameForLinking(Optional<String> referenceMarkNameForLinking) {
        this.referenceMarkNameForLinking = referenceMarkNameForLinking;
    }

    /** Integers 0..(n-1) */
    static List<Integer> makeIndices(int n) {
        return Stream.iterate(0, i -> i + 1).limit(n).collect(Collectors.toList());
    }

    public List<Citation> getCitationsInLocalOrder() {
        List<Citation> res = new ArrayList<>(citationsInStorageOrder.size());
        for (int i : localOrder) {
            res.add(citationsInStorageOrder.get(i));
        }
        return res;
    }

    /*
     * Values of the number fields of the citations according to
     * localOrder.
     */
    public List<Integer> getCitationNumbersInLocalOrder() {
        List<Citation> cits = getCitationsInLocalOrder();
        return (cits.stream()
                .map(cit -> cit.number.orElseThrow(RuntimeException::new))
                .collect(Collectors.toList()));
    }

    /*
     * Helper class for imposeLocalOrderByComparator: a citation
     * paired with its storage index.
     */
    class CitationAndIndex implements CitationSort.ComparableCitation {
        Citation c;
        int i;

        CitationAndIndex(Citation c, int i) {
            this.c = c;
            this.i = i;
        }

        @Override
        public String getCitationKey() {
            return c.getCitationKey();
        }

        @Override
        public Optional<BibEntry> getBibEntry() {
            return c.getBibEntry();
        }

        @Override
        public Optional<OOFormattedText> getPageInfo() {
            return c.pageInfo;
        }
    }

    /**
     * Sort citations for presentation within a CitationGroup.
     */
    void imposeLocalOrderByComparator(Comparator<BibEntry> entryComparator) {

        // Pair citations with their storage index in citations
        final int nCitations = citationsInStorageOrder.size();
        List<CitationAndIndex> cks = new ArrayList<>(nCitations);
        for (int i = 0; i < nCitations; i++) {
            Citation c = citationsInStorageOrder.get(i);
            cks.add(new CitationAndIndex(c, i));
        }

        // Sort the list
        cks.sort(new CitationSort.CitationComparator(entryComparator, true));

        // Copy ordered storage indices to localOrder
        List<Integer> o = new ArrayList<>(nCitations);
        for (CitationAndIndex ck : cks) {
            o.add(ck.i);
        }
        this.localOrder = o;
    }
} // class CitationGroup
