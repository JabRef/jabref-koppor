package org.jabref.logic.openoffice;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jabref.logic.oostyle.InTextCitationType;

/**
 *  How and what is encoded in a mark names.
 *
 *  - pageInfo does not appear here. It is not encoded in the mark name.
 *  - Does not depend on the type of marks (reference mark of bookmark) used.
 */
class Codec52 {
    private static final String BIB_CITATION = "JR_cite";
    private static final Pattern CITE_PATTERN =
        // Pattern.compile(BIB_CITATION + "(\\d*)_(\\d*)_(.*)");
        // itcType is always "1" "2" or "3"
        Pattern.compile(BIB_CITATION + "(\\d*)_([123])_(.*)");

    /**
     * This is what we get back from parsing a refMarkName.
     *
     */
    public static class ParsedMarkName {
        /**  "", "0", "1" ... */
        public final String i;
        /** in-text-citation type */
        public final InTextCitationType itcType;
        /** Citation keys embedded in the reference mark. */
        public final List<String> citationKeys;

        ParsedMarkName(String i, InTextCitationType itcType, List<String> citationKeys) {
            Objects.requireNonNull(i);
            Objects.requireNonNull(citationKeys);
            this.i = i;
            this.itcType = itcType;
            this.citationKeys = citationKeys;
        }
    }

    /*
     * Integer representation was written into the document in
     * JabRef52, must keep it for compatibility.
     */
    public static InTextCitationType InTextCitationTypeFromInt(int i) {
        switch (i) {
        case 1:
            return InTextCitationType.AUTHORYEAR_PAR;
        case 2:
            return InTextCitationType.AUTHORYEAR_INTEXT;
        case 3:
            return InTextCitationType.INVISIBLE_CIT;
        default:
            throw new RuntimeException("Invalid InTextCitationType code");
        }
    }

    public static int InTextCitationTypeToInt(InTextCitationType i) {
        switch (i) {
        case AUTHORYEAR_PAR:
            return 1;
        case AUTHORYEAR_INTEXT:
            return 2;
        case INVISIBLE_CIT:
            return 3;
        default:
            throw new RuntimeException("Invalid InTextCitationType");
        }
    }

    /**
     * Produce a reference mark name for JabRef for the given citation
     * key and itcType that does not yet appear among the reference
     * marks of the document.
     *
     * @param bibtexKey The citation key.
     * @param itcType   Encodes the effect of withText and
     *                  inParenthesis options.
     *
     * The first occurrence of bibtexKey gets no serial number, the
     * second gets 0, the third 1 ...
     *
     * Or the first unused in this series, after removals.
     */
    public static String getUniqueMarkName(Set<String> usedNames,
                                           String bibtexKey,
                                           InTextCitationType itcType)
        throws NoDocumentException {

        // XNameAccess xNamedRefMarks = documentConnection.getReferenceMarks();
        int i = 0;
        int j = InTextCitationTypeToInt(itcType);
        String name = BIB_CITATION + '_' + j + '_' + bibtexKey;
        while (usedNames.contains(name)) {
            name = BIB_CITATION + i + '_' + j + '_' + bibtexKey;
            i++;
        }
        return name;
    }

    /**
     * Parse a JabRef (reference) mark name.
     *
     * @return Optional.empty() on failure.
     *
     */
    public static Optional<ParsedMarkName> parseMarkName(String refMarkName) {

        Matcher citeMatcher = CITE_PATTERN.matcher(refMarkName);
        if (!citeMatcher.find()) {
            return Optional.empty();
        }

        List<String> keys = Arrays.asList(citeMatcher.group(3).split(","));
        String i = citeMatcher.group(1);
        int j = Integer.parseInt(citeMatcher.group(2));
        InTextCitationType itcType = InTextCitationTypeFromInt(j);
        return (Optional.of(new Codec52.ParsedMarkName(i, itcType, keys)));
    }

    /**
     * @return true if name matches the pattern used for JabRef
     * reference mark names.
     */
    public static boolean isJabRefReferenceMarkName(String name) {
        return (CITE_PATTERN.matcher(name).find());
    }

    /**
     * Filter a list of reference mark names by `isJabRefReferenceMarkName`
     *
     * @param names The list to be filtered.
     */
    public static List<String> filterIsJabRefReferenceMarkName(List<String> names) {
        return (names
                .stream()
                .filter(Codec52::isJabRefReferenceMarkName)
                .collect(Collectors.toList()));
    }
}
