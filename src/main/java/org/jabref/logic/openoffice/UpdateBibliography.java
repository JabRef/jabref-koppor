package org.jabref.logic.openoffice;

import java.util.Optional;

import org.jabref.logic.oostyle.CitedKeys;
import org.jabref.logic.oostyle.OOBibStyle;
import org.jabref.logic.oostyle.OOFormatBibliography;
import org.jabref.model.oostyle.OOFormattedText;

import com.sun.star.beans.PropertyVetoException;
import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.text.XTextCursor;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextRange;
import com.sun.star.text.XTextSection;

/*
 * Update document: citation marks and bibliography
 */
public class UpdateBibliography {

    private static final String BIB_SECTION_NAME = "JR_bib";
    private static final String BIB_SECTION_END_NAME = "JR_bib_end";

    /**
     * Rebuilds the bibliography.
     */
    static void rebuildBibTextSection(XTextDocument doc,
                                      OOFrontend fr,
                                      CitedKeys bibliography,
                                      OOBibStyle style,
                                      boolean alwaysAddCitedOnPages)
        throws
        NoSuchElementException,
        WrappedTargetException,
        IllegalArgumentException,
        CreationException,
        PropertyVetoException,
        UnknownPropertyException,
        NoDocumentException {

        clearBibTextSectionContent2(doc);

        populateBibTextSection(doc,
                               fr,
                               bibliography,
                               style,
                               alwaysAddCitedOnPages);
    }

    /**
     * Insert a paragraph break and create a text section for the bibliography.
     *
     * Only called from `clearBibTextSectionContent2`
     */
    private static void createBibTextSection2(XTextDocument doc)
        throws
        CreationException,
        IllegalArgumentException {

        // Always creating at the end of the document.
        // Alternatively, we could receive a cursor.
        XTextCursor textCursor = doc.getText().createTextCursor();
        textCursor.gotoEnd(false);
        UnoTextSection.create(doc, BIB_SECTION_NAME, textCursor, false);
    }

    /**
     *  Find and clear the text section BIB_SECTION_NAME to "",
     *  or create it.
     *
     * Only called from: `rebuildBibTextSection`
     *
     */
    private static void clearBibTextSectionContent2(XTextDocument doc)
        throws
        CreationException,
        IllegalArgumentException,
        NoDocumentException,
        WrappedTargetException {

        Optional<XTextRange> sectionRange = UnoTextSection.getAnchor(doc, BIB_SECTION_NAME);
        if (sectionRange.isEmpty()) {
            createBibTextSection2(doc);
            return;
        } else {
            // Clear it
            XTextCursor cursor = doc.getText().createTextCursorByRange(sectionRange.get());
            cursor.setString("");
        }
    }

    /**
     * Only called from: `rebuildBibTextSection`
     *
     * Assumes the section named BIB_SECTION_NAME exists.
     */
    private static void populateBibTextSection(XTextDocument doc,
                                               OOFrontend fr,
                                               CitedKeys bibliography,
                                               OOBibStyle style,
                                               boolean alwaysAddCitedOnPages)
        throws
        CreationException,
        IllegalArgumentException,
        NoDocumentException,
        NoSuchElementException,
        PropertyVetoException,
        UnknownPropertyException,
        WrappedTargetException {

        XTextSection section = (UnoTextSection.getByName(doc, BIB_SECTION_NAME)
                                .orElseThrow(RuntimeException::new));

        XTextCursor cursor = doc.getText().createTextCursorByRange(section.getAnchor());

        // emit the title of the bibliography
        OOFormattedTextIntoOO.removeDirectFormatting(cursor);
        OOFormattedText bibliographyText = OOFormatBibliography.formatBibliography(fr.citationGroups,
                                                                                   bibliography,
                                                                                   style,
                                                                                   alwaysAddCitedOnPages);
        OOFormattedTextIntoOO.write(doc, cursor, bibliographyText);
        cursor.collapseToEnd();

        // remove the inital empty paragraph from the section.
        XTextCursor initialParagraph = doc.getText().createTextCursorByRange(section.getAnchor());
        initialParagraph.collapseToStart();
        initialParagraph.goRight((short) 1, true);
        initialParagraph.setString("");

        UnoBookmark.remove(doc, BIB_SECTION_END_NAME);
        UnoBookmark.create(doc, BIB_SECTION_END_NAME, cursor, true);

        cursor.collapseToEnd();
    }

}