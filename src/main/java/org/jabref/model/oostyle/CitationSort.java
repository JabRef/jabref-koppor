package org.jabref.model.oostyle;

import java.util.Comparator;
import java.util.Optional;

import org.jabref.model.entry.BibEntry;

public class CitationSort {

    public interface ComparableCitation {

        public String getCitationKey();

        public Optional<BibEntry> getBibEntry();

        public Optional<OOFormattedText> getPageInfo();
    }

    public static class CitationComparator implements Comparator<ComparableCitation> {

        Comparator<BibEntry> entryComparator;
        boolean unresolvedComesFirst;

        CitationComparator(Comparator<BibEntry> entryComparator,
                           boolean unresolvedComesFirst) {
            this.entryComparator = entryComparator;
            this.unresolvedComesFirst = unresolvedComesFirst;
        }

        public int compare(ComparableCitation a, ComparableCitation b) {
            Optional<BibEntry> abe = a.getBibEntry();
            Optional<BibEntry> bbe = b.getBibEntry();
            final int mul = unresolvedComesFirst ? (+1) : (-1);

            int res = 0;
            if (abe.isEmpty() && bbe.isEmpty()) {
                // Both are unresolved: compare them by citation key.
                String ack = a.getCitationKey();
                String bck = b.getCitationKey();
                res = ack.compareTo(bck);
            } else if (abe.isEmpty()) {
                return -mul;
            } else if (bbe.isEmpty()) {
                return mul;
            } else {
                // Proper comparison of entries
                res = entryComparator.compare(abe.get(),
                                              bbe.get());
            }
            // Also consider pageInfo
            if (res == 0) {
                CitationSort.comparePageInfo(a.getPageInfo().orElse(null),
                                             b.getPageInfo().orElse(null));
            }
            return res;
        }
    }

    public static String regularizePageInfoToString(OOFormattedText p) {
        if (p == null) {
            return null;
        }
        String pt = OOFormattedText.toString(p).trim();
        return (pt.equals("") ? null : pt);
    }

    /**
     * Defines sort order for pageInfo strings.
     *
     * null comes before non-null
     */
    public static int comparePageInfo(OOFormattedText a, OOFormattedText b) {

        String aa = regularizePageInfoToString(a);
        String bb = regularizePageInfoToString(b);
        if (aa == null && bb == null) {
            return 0;
        }
        if (aa == null) {
            return -1;
        }
        if (bb == null) {
            return +1;
        }
        return aa.compareTo(bb);
    }
}