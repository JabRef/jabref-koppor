package org.jabref.logic.openoffice;

import java.util.Optional;

import com.sun.star.text.XFootnote;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextRangeCompare;

public class UnoTextRange {

    private UnoTextRange() { }

    /**
     *  If original is in a footnote, return a range containing
     *  the corresponding footnote marker.
     *
     *  Returns Optional.empty if not in a footnote.
     */
    public static Optional<XTextRange> getFootnoteMarkRange(XTextRange original) {
        XFootnote footer = UnoCast.unoQI(XFootnote.class, original.getText());
        if (footer != null) {
            // If we are inside a footnote,
            // find the linking footnote marker:
            // The footnote's anchor gives the correct position in the text:
            return Optional.ofNullable(footer.getAnchor());
        }
        return Optional.empty();
    }

    /**
     * Test if two XTextRange values are comparable (i.e. they share
     * the same getText()).
     */
    public static boolean comparables(XTextRange a, XTextRange b) {
        return a.getText() == b.getText();
    }

    /**
     * @return follows java conventions
     *
     * 1 if  (a &gt; b); (-1) if (a &lt; b)
     */
    public static int compareStarts(XTextRange a, XTextRange b) {
        if (!comparables(a, b)) {
            throw new RuntimeException("compareStarts: got incomparable regions");
        }
        final XTextRangeCompare compare = UnoCast.unoQI(XTextRangeCompare.class, a.getText());
        return (-1) * compare.compareRegionStarts(a, b);
    }

    /**
     * @return follows java conventions
     *
     * 1 if  (a &gt; b); (-1) if (a &lt; b)
     */
    public static int compareEnds(XTextRange a, XTextRange b) {
        if (!comparables(a, b)) {
            throw new RuntimeException("compareEnds: got incomparable regions");
        }
        final XTextRangeCompare compare = UnoCast.unoQI(XTextRangeCompare.class, a.getText());
        return (-1) * compare.compareRegionEnds(a, b);
    }

    public static int compareStartsThenEnds(XTextRange a, XTextRange b) {
        if (!comparables(a, b)) {
            throw new RuntimeException("compareStartsThenEnds: got incomparable regions");
        }
        final XTextRangeCompare compare = UnoCast.unoQI(XTextRangeCompare.class, a.getText());
        int res = (-1) * compare.compareRegionStarts(a, b);
        if (res != 0) {
            return res;
        }
        return (-1) * compare.compareRegionEnds(a, b);
    }

}
